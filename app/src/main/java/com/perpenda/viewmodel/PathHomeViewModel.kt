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
import com.perpenda.model.Path
import com.perpenda.model.ReviewDue
import com.perpenda.model.UnitManifestEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

const val CANONICAL_PATH_ID: String = "llm-systems-for-pms"

/**
 * How a unit renders on the path and whether it's openable.
 *
 * - DONE: completed; stays openable (spaced review re-surfaces it).
 * - CURRENT: the first unlocked, not-done unit — the "you are here".
 * - AVAILABLE: unlocked and not done, but not the immediate next.
 * - LOCKED: a prerequisite is not yet completed; not openable.
 *
 * A completed unit is never LOCKED (it could only have been completed
 * with its prereqs met), so DONE takes precedence over the gate.
 */
enum class UnitGateState { DONE, CURRENT, AVAILABLE, LOCKED }

/**
 * Map each unit to its gate state from the completed set + prereqs.
 * Pure and order-independent; `CURRENT` is the lowest-position unit
 * that is unlocked and not done. With backward-pointing prereqs (every
 * unit's prereqs are earlier units), a `CURRENT` always exists while
 * any unit is incomplete, so the "Continue" target is never null until
 * the path is fully done.
 */
fun computeGateStates(
    units: List<UnitManifestEntry>,
    completed: Set<String>
): Map<String, UnitGateState> {
    fun unlocked(unit: UnitManifestEntry): Boolean =
        unit.prereqUnitIds.all { it in completed }

    val currentId = units
        .sortedBy { it.position }
        .firstOrNull { it.id !in completed && unlocked(it) }
        ?.id

    return units.associate { unit ->
        unit.id to when {
            unit.id in completed -> UnitGateState.DONE
            !unlocked(unit) -> UnitGateState.LOCKED
            unit.id == currentId -> UnitGateState.CURRENT
            else -> UnitGateState.AVAILABLE
        }
    }
}

sealed interface PathHomeUiState {
    object Loading : PathHomeUiState
    data class Error(val message: String, val authExpired: Boolean = false) : PathHomeUiState
    data class Loaded(
        val path: Path,
        val completedUnitIds: Set<String>,
        val nextUnit: UnitManifestEntry?,
        /** Per-unit gate state (id -> state), driving node rendering and tap-ability. */
        val unitStates: Map<String, UnitGateState> = emptyMap(),
        /**
         * True only when every unit is completed — genuine path completion.
         * Distinct from `nextUnit == null` because nothing is currently
         * unlocked (a blocked state from inconsistent prereq data), so the
         * UI never shows completion messaging to a stuck learner.
         */
        val pathComplete: Boolean = false,
        /**
         * F5 / D4 — spaced reviews due, surfaced *alongside* the next
         * new unit. Optional and never a gate: a failed review fetch
         * leaves this empty and never blocks `nextUnit` or `Loaded`.
         */
        val reviewsDue: List<ReviewDue> = emptyList()
    ) : PathHomeUiState
}

/** One-shot UI event emitted at most once per occurrence (Channel-backed). */
sealed interface PathHomeEvent {
    object AuthExpired : PathHomeEvent

    /**
     * A unit transitioned to DONE between two successive loads — i.e. the
     * user just completed it (typically returning from the unit reader).
     * `unlockedTitle` is the newly-current unit, null when the path is done.
     */
    data class UnitCompleted(
        val completedTitle: String,
        val unlockedTitle: String?,
        val pathComplete: Boolean
    ) : PathHomeEvent
}

class PathHomeViewModel(
    private val pathRepository: PathRepository,
    private val completionCache: CompletionCache,
    private val pathId: String = CANONICAL_PATH_ID
) : ViewModel() {

    var uiState: PathHomeUiState by mutableStateOf(PathHomeUiState.Loading)
        private set

    private val _events = Channel<PathHomeEvent>(Channel.BUFFERED)
    val events: Flow<PathHomeEvent> = _events.receiveAsFlow()

    fun load() {
        // Refresh silently when content is already on screen: returning
        // from the unit reader re-loads on every RESUME, and flashing a
        // full-screen spinner over 99%-unchanged data reads as jank. The
        // spinner is reserved for the first load (nothing to show yet).
        val previous = uiState as? PathHomeUiState.Loaded
        if (previous == null) {
            uiState = PathHomeUiState.Loading
        }
        viewModelScope.launch {
            uiState = try {
                // Best-effort: pull completion state from the server before
                // we read the local cache, so completion syncs across devices
                // for the same account. Failures (offline, 401, server down)
                // are intentionally swallowed — we fall through to whatever
                // is already cached locally, which is the v1 baseline.
                runCatching { pathRepository.syncCompletedUnits() }
                    .onFailure { if (it is CancellationException) throw it }

                val path = pathRepository.getPath(pathId)
                val completed = completionCache.completedUnitIds()
                val states = computeGateStates(path.units, completed)
                // F5 / D4: reviews are alongside, never a gate. Fetched
                // best-effort *after* the path/completed state that the
                // screen actually depends on; a failure here yields an
                // empty list and never degrades Loaded or nextUnit.
                val reviewsDue = runCatching { pathRepository.listDueReviews() }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrDefault(emptyList())
                val nextUnit = path.units.firstOrNull { states[it.id] == UnitGateState.CURRENT }
                val pathComplete = path.units.all { it.id in completed }

                // A unit newly DONE since the previous Loaded state means
                // the user just completed it — surface the moment once.
                if (previous != null) {
                    val newlyCompleted = path.units.firstOrNull {
                        it.id in completed && it.id !in previous.completedUnitIds
                    }
                    if (newlyCompleted != null) {
                        _events.send(
                            PathHomeEvent.UnitCompleted(
                                completedTitle = newlyCompleted.title,
                                unlockedTitle = nextUnit?.title,
                                pathComplete = pathComplete
                            )
                        )
                    }
                }

                PathHomeUiState.Loaded(
                    path = path,
                    completedUnitIds = completed,
                    nextUnit = nextUnit,
                    unitStates = states,
                    pathComplete = pathComplete,
                    reviewsDue = reviewsDue
                )
            } catch (error: PathApiException) {
                if (error.statusCode == 401) {
                    _events.send(PathHomeEvent.AuthExpired)
                    // Auth expiry must surface even over stale content —
                    // the screen routes to sign-in from this state.
                    PathHomeUiState.Error(
                        message = error.message.ifBlank { "Couldn't load the path." },
                        authExpired = true
                    )
                } else if (previous != null) {
                    // Transient server trouble must not replace a perfectly
                    // good screen with a full-screen error; keep the stale
                    // content. The next resume retries anyway.
                    previous
                } else {
                    PathHomeUiState.Error(
                        message = error.message.ifBlank { "Couldn't load the path." }
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                previous ?: PathHomeUiState.Error(message = "Network error. Pull to retry.")
            }
        }
    }

    /**
     * F5 / D6 — the user tapped a due review. Mark it reviewed
     * server-side (best-effort: the server gates 404/409 and we
     * swallow failures) and optimistically drop it from the
     * surfaced list so it disappears immediately; it would also
     * fall off the next load() since due_at advances. Navigation
     * to the unit reader (D2 — a review is a re-surface, not a
     * re-grade, so it reuses the existing unit route) is handled
     * by the screen.
     */
    fun markReviewed(unitId: String) {
        val current = uiState
        if (current is PathHomeUiState.Loaded) {
            uiState = current.copy(
                reviewsDue = current.reviewsDue.filterNot { it.unitId == unitId }
            )
        }
        viewModelScope.launch {
            runCatching { pathRepository.markReviewed(unitId) }
        }
    }

    /** Re-evaluate "next unit" using the current cache; cheap, no network. */
    fun refreshFromCache() {
        val current = uiState
        if (current !is PathHomeUiState.Loaded) return
        val completed = completionCache.completedUnitIds()
        val states = computeGateStates(current.path.units, completed)
        uiState = current.copy(
            completedUnitIds = completed,
            nextUnit = current.path.units.firstOrNull { states[it.id] == UnitGateState.CURRENT },
            unitStates = states,
            pathComplete = current.path.units.all { it.id in completed }
        )
    }

    companion object {
        fun factory(
            pathRepository: PathRepository,
            completionCache: CompletionCache,
            pathId: String = CANONICAL_PATH_ID
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PathHomeViewModel(pathRepository, completionCache, pathId) as T
            }
        }
    }
}
