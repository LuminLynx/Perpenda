package com.perpenda.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.perpenda.data.remote.api.PathApiException
import com.perpenda.data.repository.CompletionCache
import com.perpenda.data.repository.PathRepository
import com.perpenda.model.GradeResult
import com.perpenda.model.UnitDetail
import com.perpenda.model.UnitManifestEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface UnitReaderUiState {
    object Loading : UnitReaderUiState
    data class Error(val message: String, val authExpired: Boolean = false) : UnitReaderUiState
    data class Loaded(
        val unit: UnitDetail,
        val tradeOffExpanded: Boolean = false,
        val depthExpanded: Boolean = false,
        /** Current value of the open-ended decision-prompt answer field (F3). */
        val answerDraft: String = "",
        /** True while a grade submission is in flight. */
        val submitInProgress: Boolean = false,
        /** Non-null when the most recent grade submission failed. */
        val submitFailure: String? = null,
        /** Non-null after a successful grade submission. Drives the F4 grade UI. */
        val gradeResult: GradeResult? = null,
        /**
         * True if this unit is already completed for the current user — either
         * because the user just submitted an answer (which records a completion
         * server-side under T2), or because the local CompletionCache already
         * had the unit id when the screen loaded.
         */
        val isCompleted: Boolean = false,
        /**
         * The unit unlocked by this completion, resolved best-effort after a
         * successful grade. Drives the "Next · {title}" action on the grade
         * results, closing the loop without a detour through path home.
         */
        val nextUnit: UnitManifestEntry? = null
    ) : UnitReaderUiState
}

sealed interface UnitReaderEvent {
    object AuthExpired : UnitReaderEvent
}

class UnitReaderViewModel(
    private val pathRepository: PathRepository,
    private val completionCache: CompletionCache,
    private val unitId: String
) : ViewModel() {

    var uiState: UnitReaderUiState by mutableStateOf(UnitReaderUiState.Loading)
        private set

    private val _events = Channel<UnitReaderEvent>(Channel.BUFFERED)
    val events: Flow<UnitReaderEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        uiState = UnitReaderUiState.Loading
        viewModelScope.launch {
            uiState = try {
                val unit = pathRepository.getUnit(unitId)
                UnitReaderUiState.Loaded(
                    unit = unit,
                    isCompleted = unit.id in completionCache.completedUnitIds()
                )
            } catch (error: PathApiException) {
                if (error.statusCode == 401) {
                    _events.send(UnitReaderEvent.AuthExpired)
                }
                UnitReaderUiState.Error(
                    message = error.message.ifBlank { "Couldn't load this unit." },
                    authExpired = error.statusCode == 401
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                UnitReaderUiState.Error(message = "Network error. Pull to retry.")
            }
        }
    }

    fun toggleTradeOff() {
        val current = uiState
        if (current is UnitReaderUiState.Loaded) {
            uiState = current.copy(tradeOffExpanded = !current.tradeOffExpanded)
        }
    }

    fun toggleDepth() {
        val current = uiState
        if (current is UnitReaderUiState.Loaded) {
            uiState = current.copy(depthExpanded = !current.depthExpanded)
        }
    }

    fun onAnswerChanged(answer: String) {
        val current = uiState
        if (current is UnitReaderUiState.Loaded) {
            uiState = current.copy(answerDraft = answer, submitFailure = null)
        }
    }

    /**
     * F4 — submit the user's open-ended answer to the grader. On success
     * the per-criterion grade payload populates `gradeResult` and
     * `isCompleted` flips to true (grading IS completion under T2).
     * Re-submitting replaces the prior grade output.
     *
     * The grader call takes 3–8 seconds. While it's in flight the user
     * can keep interacting (toggle trade-off / depth disclosures, edit
     * the draft, etc.). When the response arrives we merge the result
     * into the **latest** uiState rather than overwriting from the
     * snapshot taken before the call, so concurrent toggles aren't
     * silently undone.
     */
    fun submitAnswer() {
        val current = uiState
        if (current !is UnitReaderUiState.Loaded || current.submitInProgress) return
        val answer = current.answerDraft.trim()
        if (answer.isBlank()) return

        val unitId = current.unit.id
        uiState = current.copy(submitInProgress = true, submitFailure = null)

        viewModelScope.launch {
            val outcome: Result<com.perpenda.model.GradeResult> = runCatching {
                pathRepository.submitGrade(unitId, answer)
            }.onFailure { if (it is CancellationException) throw it }
            val pathError = outcome.exceptionOrNull() as? PathApiException
            if (pathError?.statusCode == 401) {
                _events.send(UnitReaderEvent.AuthExpired)
            }

            // Merge into the LATEST state, not the captured snapshot. If
            // the screen has navigated away or reloaded mid-flight we
            // drop the result silently — the user has moved on.
            val latest = uiState
            if (latest !is UnitReaderUiState.Loaded) return@launch

            uiState = outcome.fold(
                onSuccess = { result ->
                    latest.copy(
                        submitInProgress = false,
                        submitFailure = null,
                        gradeResult = result,
                        isCompleted = true,
                        // Resolved asynchronously below; null until then.
                        nextUnit = null
                    )
                },
                onFailure = { error ->
                    latest.copy(
                        submitInProgress = false,
                        submitFailure = mapSubmitFailure(error)
                    )
                }
            )

            // Best-effort: resolve the unit this completion unlocked so the
            // grade results can offer "Next · {title}". A failure just means
            // no next-unit shortcut — the pinned button on path home remains.
            if (outcome.isSuccess) {
                val resolved = runCatching {
                    val current = uiState as? UnitReaderUiState.Loaded ?: return@launch
                    val path = pathRepository.getPath(current.unit.pathId)
                    val states = computeGateStates(path.units, completionCache.completedUnitIds())
                    path.units.firstOrNull { states[it.id] == UnitGateState.CURRENT }
                }.onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
                val afterFetch = uiState
                if (resolved != null && afterFetch is UnitReaderUiState.Loaded) {
                    uiState = afterFetch.copy(nextUnit = resolved)
                }
            }
        }
    }

    private fun mapSubmitFailure(error: Throwable): String {
        return when {
            error is PathApiException && error.statusCode == 401 ->
                "Your session expired. Sign in again to submit your answer."
            error is PathApiException && error.statusCode == 409 ->
                "This unit doesn't have a graded decision prompt yet."
            error is PathApiException && error.statusCode == 502 ->
                "The grader is temporarily unavailable. Try again in a moment."
            error is PathApiException ->
                error.message.ifBlank { "Couldn't grade your answer." }
            else -> "Network error. Try again."
        }
    }

    companion object {
        fun factory(
            pathRepository: PathRepository,
            completionCache: CompletionCache,
            unitId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UnitReaderViewModel(pathRepository, completionCache, unitId) as T
            }
        }
    }
}
