package com.perpenda.viewmodel

import com.perpenda.data.remote.api.PathApiException
import com.perpenda.data.repository.CompletionCache
import com.perpenda.data.repository.PathRepository
import com.perpenda.model.CompletionRecord
import com.perpenda.model.Grade
import com.perpenda.model.GradeResult
import com.perpenda.model.Path
import com.perpenda.model.UnitDetail
import com.perpenda.model.UnitManifestEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnitReaderViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `loaded state exposes the unit`() = runTest(dispatcher) {
        val viewModel = newViewModel(
            repo = FakeRepo(unit = sampleUnit("u-1")),
            cache = FakeCache()
        )
        advanceUntilIdle()

        val state = viewModel.uiState as UnitReaderUiState.Loaded
        assertEquals("u-1", state.unit.id)
        assertFalse(state.depthExpanded)
        assertFalse(state.isCompleted)
        assertEquals("", state.answerDraft)
        assertNull(state.gradeResult)
    }

    @Test
    fun `loaded state reads completion from cache so prior-session completions show as complete`() = runTest(dispatcher) {
        val viewModel = newViewModel(
            repo = FakeRepo(unit = sampleUnit("u-1")),
            cache = FakeCache(initial = setOf("u-1"))
        )
        advanceUntilIdle()

        val state = viewModel.uiState as UnitReaderUiState.Loaded
        assertTrue("isCompleted must reflect the local cache on load", state.isCompleted)
    }

    @Test
    fun `toggleDepth flips the disclosure`() = runTest(dispatcher) {
        val viewModel = newViewModel(repo = FakeRepo(unit = sampleUnit("u-1")), cache = FakeCache())
        advanceUntilIdle()

        viewModel.toggleDepth()
        assertTrue((viewModel.uiState as UnitReaderUiState.Loaded).depthExpanded)
        viewModel.toggleDepth()
        assertFalse((viewModel.uiState as UnitReaderUiState.Loaded).depthExpanded)
    }

    @Test
    fun `onAnswerChanged updates the draft and clears prior failure`() = runTest(dispatcher) {
        val viewModel = newViewModel(repo = FakeRepo(unit = sampleUnit("u-1")), cache = FakeCache())
        advanceUntilIdle()

        viewModel.onAnswerChanged("draft text")
        val state = viewModel.uiState as UnitReaderUiState.Loaded
        assertEquals("draft text", state.answerDraft)
        assertNull(state.submitFailure)
    }

    @Test
    fun `submitAnswer no-ops on a blank draft`() = runTest(dispatcher) {
        val repo = FakeRepo(unit = sampleUnit("u-1"))
        val viewModel = newViewModel(repo = repo, cache = FakeCache())
        advanceUntilIdle()

        viewModel.onAnswerChanged("   ")
        viewModel.submitAnswer()
        advanceUntilIdle()

        val state = viewModel.uiState as UnitReaderUiState.Loaded
        assertEquals(0, repo.submitGradeCalls)
        assertFalse(state.submitInProgress)
        assertNull(state.gradeResult)
    }

    @Test
    fun `submitAnswer populates gradeResult and flips isCompleted`() = runTest(dispatcher) {
        val repo = FakeRepo(unit = sampleUnit("u-1"))
        val viewModel = newViewModel(repo = repo, cache = FakeCache())
        advanceUntilIdle()

        viewModel.onAnswerChanged("a real answer")
        viewModel.submitAnswer()
        advanceUntilIdle()

        val state = viewModel.uiState as UnitReaderUiState.Loaded
        assertNotNull(state.gradeResult)
        assertEquals(2, state.gradeResult!!.grades.size)
        assertFalse(state.gradeResult!!.flagged)
        assertTrue(state.isCompleted)
        assertFalse(state.submitInProgress)
        assertNull(state.submitFailure)
        assertEquals(1, repo.submitGradeCalls)
    }

    @Test
    fun `401 on submit surfaces a session-expired failure (no gradeResult)`() = runTest(dispatcher) {
        val repo = FakeRepo(
            unit = sampleUnit("u-1"),
            submitGradeError = PathApiException("expired", statusCode = 401)
        )
        val viewModel = newViewModel(repo = repo, cache = FakeCache())
        advanceUntilIdle()

        viewModel.onAnswerChanged("answer")
        viewModel.submitAnswer()
        advanceUntilIdle()

        val state = viewModel.uiState as UnitReaderUiState.Loaded
        assertNull(state.gradeResult)
        assertNotNull(state.submitFailure)
        assertTrue(state.submitFailure!!.contains("session", ignoreCase = true))
        assertFalse(state.isCompleted)
    }

    @Test
    fun `submitAnswer merges into latest state, preserving in-flight toggles`() = runTest(dispatcher) {
        // Reviewer regression: the prior implementation captured `current`
        // before launching the network call and rebuilt state from that
        // stale snapshot. A user toggling depth or trade-off framing
        // during the 3–8s grader wait would have those toggles silently
        // overwritten. The fix merges results into the latest state.
        val gate = CompletableDeferred<GradeResult>()
        val repo = FakeRepo(unit = sampleUnit("u-1"), submitGradeGate = gate)
        val viewModel = newViewModel(repo = repo, cache = FakeCache())
        advanceUntilIdle()

        viewModel.onAnswerChanged("a real answer")
        viewModel.submitAnswer()
        advanceUntilIdle()
        // submitInProgress=true while we wait on the gate
        assertTrue((viewModel.uiState as UnitReaderUiState.Loaded).submitInProgress)

        // User toggles depth while the grader is still working
        viewModel.toggleDepth()
        assertTrue((viewModel.uiState as UnitReaderUiState.Loaded).depthExpanded)

        // Grader finally returns
        gate.complete(
            GradeResult(
                completion = CompletionRecord(1L, "u", "p", "u-1", "now"),
                grades = listOf(
                    Grade(1L, 11L, met = true, confidence = 0.9, rationale = "ok", flagged = false, answerQuote = "x")
                ),
                flagged = false
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState as UnitReaderUiState.Loaded
        assertTrue("depth toggle made during in-flight submit must be preserved", state.depthExpanded)
        assertNotNull(state.gradeResult)
        assertFalse(state.submitInProgress)
    }

    @Test
    fun `error state surfaces authExpired on initial 401`() = runTest(dispatcher) {
        val repo = FakeRepo(
            getUnitError = PathApiException("expired", statusCode = 401)
        )
        val viewModel = newViewModel(repo = repo, cache = FakeCache())
        advanceUntilIdle()

        val state = viewModel.uiState as UnitReaderUiState.Error
        assertTrue(state.authExpired)
    }

    @Test
    fun `401 emits a single AuthExpired event (one-shot)`() = runTest(dispatcher) {
        val viewModel = newViewModel(
            repo = FakeRepo(getUnitError = PathApiException("expired", statusCode = 401)),
            cache = FakeCache()
        )
        advanceUntilIdle()

        val first = withTimeoutOrNull(1_000) { viewModel.events.first() }
        assertNotNull("expected one AuthExpired event", first)

        advanceUntilIdle()
        val second = withTimeoutOrNull(50) { viewModel.events.first() }
        assertNull("AuthExpired must not re-emit while state lingers", second)
    }

    @Test
    fun `grade success resolves the next unit for the one-tap shortcut`() = runTest(dispatcher) {
        val path = Path(
            id = "llm-systems-for-pms",
            slug = "llm-systems-for-pms",
            title = "LLM Systems for PMs",
            description = "desc",
            units = listOf(
                UnitManifestEntry("u-1", "a", "Tokenization", 1, "published"),
                UnitManifestEntry(
                    "u-2", "b", "Context Window", 2, "published",
                    prereqUnitIds = listOf("u-1")
                )
            )
        )
        // The cache already holds u-1, standing in for the completion the
        // real repository records on submitGrade.
        val viewModel = newViewModel(
            repo = FakeRepo(unit = sampleUnit("u-1"), path = path),
            cache = FakeCache(initial = setOf("u-1"))
        )
        advanceUntilIdle()

        viewModel.onAnswerChanged("tokens, not characters")
        viewModel.submitAnswer()
        advanceUntilIdle()

        val state = viewModel.uiState as UnitReaderUiState.Loaded
        assertNotNull(state.gradeResult)
        assertEquals("u-2", state.nextUnit?.id)
        assertEquals("Context Window", state.nextUnit?.title)
    }

    @Test
    fun `next unit resolution failure leaves the shortcut absent`() = runTest(dispatcher) {
        // FakeRepo without a path stub throws from getPath — the best-effort
        // resolution must swallow it and leave nextUnit null.
        val viewModel = newViewModel(
            repo = FakeRepo(unit = sampleUnit("u-1")),
            cache = FakeCache()
        )
        advanceUntilIdle()

        viewModel.onAnswerChanged("an answer")
        viewModel.submitAnswer()
        advanceUntilIdle()

        val state = viewModel.uiState as UnitReaderUiState.Loaded
        assertNotNull(state.gradeResult)
        assertNull(state.nextUnit)
    }

    private fun newViewModel(
        repo: PathRepository,
        cache: CompletionCache,
        unitId: String = "u-1"
    ): UnitReaderViewModel = UnitReaderViewModel(repo, cache, unitId)

    private fun sampleUnit(id: String): UnitDetail = UnitDetail(
        id = id,
        pathId = "llm-systems-for-pms",
        slug = "tokenization",
        position = 1,
        title = "Tokenization",
        definition = "def",
        tradeOffFraming = "framing",
        biteMd = "bite",
        depthMd = "depth",
        prereqUnitIds = emptyList(),
        status = "draft",
        sources = emptyList(),
        calibrationTags = emptyList(),
        decisionPrompt = null,
        rubric = null
    )
}

private class FakeRepo(
    private val unit: UnitDetail? = null,
    private val getUnitError: Throwable? = null,
    private val submitGradeError: Throwable? = null,
    /** Optional path manifest for the post-grade next-unit resolution. */
    private val path: Path? = null,
    /**
     * Optional gate the test resolves manually, so the fake suspends in
     * `submitGrade` until the test signals. Used to verify in-flight
     * state-merge behavior.
     */
    private val submitGradeGate: CompletableDeferred<GradeResult>? = null
) : PathRepository {
    var submitGradeCalls = 0
        private set

    override suspend fun getPath(pathId: String): Path = path ?: error("not used")

    override suspend fun getUnit(unitId: String): UnitDetail {
        getUnitError?.let { throw it }
        return unit ?: error("no unit stub set")
    }

    override suspend fun submitGrade(unitId: String, answer: String): GradeResult {
        submitGradeCalls++
        submitGradeError?.let { throw it }
        submitGradeGate?.let { return it.await() }
        return GradeResult(
            completion = CompletionRecord(1L, "u", "p", unitId, "now"),
            grades = listOf(
                Grade(1L, 11L, met = true, confidence = 0.9, rationale = "ok", flagged = false, answerQuote = "x"),
                Grade(2L, 12L, met = false, confidence = 0.85, rationale = "missed", flagged = false, answerQuote = "")
            ),
            flagged = false
        )
    }

    override suspend fun syncCompletedUnits() { /* no-op for these tests */ }

    override suspend fun listDueReviews(): List<com.perpenda.model.ReviewDue> =
        emptyList()

    override suspend fun markReviewed(unitId: String) { /* no-op for these tests */ }
}

private class FakeCache(initial: Set<String> = emptySet()) : CompletionCache {
    private val store = initial.toMutableSet()
    override fun completedUnitIds(): Set<String> = store.toSet()
    override fun add(unitId: String) { store.add(unitId) }
    override fun replaceAll(unitIds: Set<String>) {
        store.clear()
        store.addAll(unitIds)
    }
    override fun clear() { store.clear() }
}
