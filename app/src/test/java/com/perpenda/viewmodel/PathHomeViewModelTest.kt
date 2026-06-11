package com.perpenda.viewmodel

import com.perpenda.data.remote.api.PathApiException
import com.perpenda.data.repository.CompletionCache
import com.perpenda.data.repository.PathRepository
import com.perpenda.model.CompletionRecord
import com.perpenda.model.Path
import com.perpenda.model.ReviewDue
import com.perpenda.model.UnitDetail
import com.perpenda.model.UnitManifestEntry
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PathHomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `next unit is the lowest-position uncompleted unit`() = runTest(dispatcher) {
        val path = samplePath(
            UnitManifestEntry("u-1", "tokenization", "Tokenization", 1, "draft"),
            UnitManifestEntry("u-2", "context-windows", "Context Windows", 2, "draft"),
            UnitManifestEntry("u-3", "latency", "Latency", 3, "draft")
        )
        val viewModel = PathHomeViewModel(
            pathRepository = FakePathRepository(path = path),
            completionCache = FakeCompletionCache(initial = setOf("u-1"))
        )

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState as PathHomeUiState.Loaded
        assertEquals("u-2", state.nextUnit?.id)
        assertTrue(state.completedUnitIds.contains("u-1"))
    }

    @Test
    fun `nextUnit is null when every unit is completed`() = runTest(dispatcher) {
        val path = samplePath(
            UnitManifestEntry("u-1", "a", "A", 1, "draft"),
            UnitManifestEntry("u-2", "b", "B", 2, "draft")
        )
        val viewModel = PathHomeViewModel(
            pathRepository = FakePathRepository(path = path),
            completionCache = FakeCompletionCache(initial = setOf("u-1", "u-2"))
        )

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState as PathHomeUiState.Loaded
        assertNull(state.nextUnit)
    }

    @Test
    fun `error state surfaces with authExpired flag on 401`() = runTest(dispatcher) {
        val viewModel = PathHomeViewModel(
            pathRepository = FakePathRepository(
                error = PathApiException("expired", code = "TOKEN_EXPIRED", statusCode = 401)
            ),
            completionCache = FakeCompletionCache()
        )

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState as PathHomeUiState.Error
        assertTrue(state.authExpired)
    }

    @Test
    fun `401 emits a single AuthExpired event (no re-emit while state lingers)`() = runTest(dispatcher) {
        val viewModel = PathHomeViewModel(
            pathRepository = FakePathRepository(
                error = PathApiException("expired", statusCode = 401)
            ),
            completionCache = FakeCompletionCache()
        )

        viewModel.load()
        advanceUntilIdle()
        val first = withTimeoutOrNull(1_000) { viewModel.events.first() }
        assertNotNull("expected one AuthExpired event", first)

        // No second event should arrive while the screen sits on the same Error state.
        advanceUntilIdle()
        val second = withTimeoutOrNull(50) { viewModel.events.first() }
        assertNull("AuthExpired must be one-shot, not re-emitted from stale state", second)
    }

    @Test
    fun `load syncs server completions into the local cache before computing nextUnit`() = runTest(dispatcher) {
        // Cache starts empty (e.g. fresh install on a new device); the
        // server reports the user already completed u-1. After load we
        // expect u-1 to appear in the cache so "Continue" advances to u-2.
        val path = samplePath(
            UnitManifestEntry("u-1", "a", "A", 1, "draft"),
            UnitManifestEntry("u-2", "b", "B", 2, "draft")
        )
        val cache = FakeCompletionCache()
        val repository = FakePathRepository(
            path = path,
            cacheToSeed = cache,
            syncedUnitIds = setOf("u-1")
        )
        val viewModel = PathHomeViewModel(repository, cache)

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState as PathHomeUiState.Loaded
        assertTrue("u-1" in state.completedUnitIds)
        assertEquals("u-2", state.nextUnit?.id)
        assertEquals(1, repository.syncCalls)
    }

    @Test
    fun `sync failure does not break load (best-effort)`() = runTest(dispatcher) {
        // If the sync call fails (offline, transient 500, etc.) the load
        // must still succeed using whatever is already cached locally.
        val path = samplePath(
            UnitManifestEntry("u-1", "a", "A", 1, "draft")
        )
        val cache = FakeCompletionCache(initial = emptySet())
        val repository = FakePathRepository(
            path = path,
            syncError = PathApiException("offline", statusCode = null)
        )
        val viewModel = PathHomeViewModel(repository, cache)

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState as PathHomeUiState.Loaded
        assertEquals("u-1", state.nextUnit?.id)
        assertEquals(1, repository.syncCalls)
        assertEquals(1, repository.getPathCalls)
    }

    @Test
    fun `refreshFromCache recomputes nextUnit without re-loading`() = runTest(dispatcher) {
        val path = samplePath(
            UnitManifestEntry("u-1", "a", "A", 1, "draft"),
            UnitManifestEntry("u-2", "b", "B", 2, "draft")
        )
        val cache = FakeCompletionCache()
        val repository = FakePathRepository(path = path)
        val viewModel = PathHomeViewModel(repository, cache)
        viewModel.load()
        advanceUntilIdle()
        assertEquals("u-1", (viewModel.uiState as PathHomeUiState.Loaded).nextUnit?.id)

        cache.add("u-1")
        viewModel.refreshFromCache()
        assertEquals("u-2", (viewModel.uiState as PathHomeUiState.Loaded).nextUnit?.id)
        assertEquals("expected exactly one network call", 1, repository.getPathCalls)
    }

    @Test
    fun `reviews due surface alongside nextUnit`() = runTest(dispatcher) {
        val path = samplePath(
            UnitManifestEntry("u-1", "a", "A", 1, "published"),
            UnitManifestEntry("u-2", "b", "B", 2, "published")
        )
        val repository = FakePathRepository(
            path = path,
            reviewsDue = listOf(
                ReviewDue("u-1", "a", "A", "2026-05-18T00:00:00+00:00", 3, null)
            )
        )
        val viewModel = PathHomeViewModel(repository, FakeCompletionCache(setOf("u-1")))

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState as PathHomeUiState.Loaded
        assertEquals("u-2", state.nextUnit?.id)          // spine intact
        assertEquals(1, state.reviewsDue.size)            // alongside
        assertEquals("u-1", state.reviewsDue.first().unitId)
        assertEquals(1, repository.listDueReviewsCalls)
    }

    @Test
    fun `failed review fetch never gates the screen (best-effort)`() = runTest(dispatcher) {
        val path = samplePath(UnitManifestEntry("u-1", "a", "A", 1, "published"))
        val repository = FakePathRepository(
            path = path,
            reviewsError = PathApiException("offline", statusCode = null)
        )
        val viewModel = PathHomeViewModel(repository, FakeCompletionCache())

        viewModel.load()
        advanceUntilIdle()

        // D4: a review-fetch failure must still yield Loaded with the
        // next unit intact and an empty reviews list — never Error.
        val state = viewModel.uiState as PathHomeUiState.Loaded
        assertEquals("u-1", state.nextUnit?.id)
        assertTrue(state.reviewsDue.isEmpty())
    }

    @Test
    fun `markReviewed optimistically drops the row and calls the repo`() = runTest(dispatcher) {
        val path = samplePath(UnitManifestEntry("u-2", "b", "B", 2, "published"))
        val repository = FakePathRepository(
            path = path,
            reviewsDue = listOf(
                ReviewDue("u-1", "a", "A", "2026-05-18T00:00:00+00:00", 3, null)
            )
        )
        val viewModel = PathHomeViewModel(repository, FakeCompletionCache(setOf("u-1")))
        viewModel.load()
        advanceUntilIdle()
        assertEquals(1, (viewModel.uiState as PathHomeUiState.Loaded).reviewsDue.size)

        viewModel.markReviewed("u-1")
        advanceUntilIdle()

        val state = viewModel.uiState as PathHomeUiState.Loaded
        assertTrue("review row removed optimistically", state.reviewsDue.isEmpty())
        assertEquals(listOf("u-1"), repository.markedReviewedUnitIds)
        assertEquals("u-2", state.nextUnit?.id)  // spine untouched
    }

    @Test
    fun `gate locks units whose prereqs are not yet completed`() {
        val units = listOf(
            UnitManifestEntry("u-1", "a", "A", 1, "published"),
            UnitManifestEntry("u-2", "b", "B", 2, "published", prereqUnitIds = listOf("u-1")),
            UnitManifestEntry("u-3", "c", "C", 3, "published", prereqUnitIds = listOf("u-2"))
        )

        val states = computeGateStates(units, completed = emptySet())

        assertEquals(UnitGateState.CURRENT, states["u-1"])
        assertEquals(UnitGateState.LOCKED, states["u-2"])
        assertEquals(UnitGateState.LOCKED, states["u-3"])
    }

    @Test
    fun `completing a prereq unlocks the next unit as CURRENT`() {
        val units = listOf(
            UnitManifestEntry("u-1", "a", "A", 1, "published"),
            UnitManifestEntry("u-2", "b", "B", 2, "published", prereqUnitIds = listOf("u-1")),
            UnitManifestEntry("u-3", "c", "C", 3, "published", prereqUnitIds = listOf("u-2"))
        )

        val states = computeGateStates(units, completed = setOf("u-1"))

        assertEquals(UnitGateState.DONE, states["u-1"])
        assertEquals(UnitGateState.CURRENT, states["u-2"])
        assertEquals(UnitGateState.LOCKED, states["u-3"])
    }

    @Test
    fun `a unit with multiple prereqs stays locked until all are met`() {
        val units = listOf(
            UnitManifestEntry("u-1", "a", "A", 1, "published"),
            UnitManifestEntry("u-2", "b", "B", 2, "published"),
            UnitManifestEntry("u-4", "d", "D", 4, "published", prereqUnitIds = listOf("u-1", "u-2"))
        )

        assertEquals(UnitGateState.LOCKED, computeGateStates(units, setOf("u-1"))["u-4"])
        assertEquals(UnitGateState.CURRENT, computeGateStates(units, setOf("u-1", "u-2"))["u-4"])
    }

    @Test
    fun `completed unit is never locked even if a prereq is missing`() {
        // Defensive: DONE takes precedence over the gate.
        val units = listOf(
            UnitManifestEntry("u-2", "b", "B", 2, "published", prereqUnitIds = listOf("u-1"))
        )

        assertEquals(UnitGateState.DONE, computeGateStates(units, setOf("u-2"))["u-2"])
    }

    @Test
    fun `nextUnit is the unlocked frontier and locked units are gated in state`() = runTest(dispatcher) {
        val path = samplePath(
            UnitManifestEntry("u-1", "a", "A", 1, "published"),
            UnitManifestEntry("u-2", "b", "B", 2, "published", prereqUnitIds = listOf("u-1"))
        )
        val viewModel = PathHomeViewModel(
            FakePathRepository(path = path),
            FakeCompletionCache(initial = emptySet())
        )

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState as PathHomeUiState.Loaded
        assertEquals("u-1", state.nextUnit?.id)
        assertEquals(UnitGateState.CURRENT, state.unitStates["u-1"])
        assertEquals(UnitGateState.LOCKED, state.unitStates["u-2"])
    }

    @Test
    fun `all units completed reports pathComplete with no next unit`() = runTest(dispatcher) {
        val path = samplePath(
            UnitManifestEntry("u-1", "a", "A", 1, "published"),
            UnitManifestEntry("u-2", "b", "B", 2, "published", prereqUnitIds = listOf("u-1"))
        )
        val viewModel = PathHomeViewModel(
            FakePathRepository(path = path),
            FakeCompletionCache(initial = setOf("u-1", "u-2"))
        )

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState as PathHomeUiState.Loaded
        assertNull(state.nextUnit)
        assertTrue(state.pathComplete)
    }

    @Test
    fun `a blocked path (nothing unlocked, not all done) is not reported complete`() = runTest(dispatcher) {
        // Inconsistent prereq data: u-1 depends on a unit that never exists,
        // so nothing is unlockable. nextUnit is null, but the path is NOT
        // complete — the UI must not show completion messaging.
        val path = samplePath(
            UnitManifestEntry("u-1", "a", "A", 1, "published", prereqUnitIds = listOf("missing"))
        )
        val viewModel = PathHomeViewModel(
            FakePathRepository(path = path),
            FakeCompletionCache(initial = emptySet())
        )

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState as PathHomeUiState.Loaded
        assertNull(state.nextUnit)
        assertEquals(false, state.pathComplete)
        assertEquals(UnitGateState.LOCKED, state.unitStates["u-1"])
    }

    @Test
    fun `resume refresh keeps Loaded on screen instead of flashing Loading`() = runTest(dispatcher) {
        val path = samplePath(UnitManifestEntry("u-1", "a", "A", 1, "published"))
        val viewModel = PathHomeViewModel(FakePathRepository(path = path), FakeCompletionCache())
        viewModel.load()
        advanceUntilIdle()
        assertTrue(viewModel.uiState is PathHomeUiState.Loaded)

        viewModel.load()
        // Before the refresh lands, the screen must still show content.
        assertTrue(
            "expected Loaded during silent refresh, got ${viewModel.uiState}",
            viewModel.uiState is PathHomeUiState.Loaded
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState is PathHomeUiState.Loaded)
    }

    @Test
    fun `refresh failure keeps stale Loaded content`() = runTest(dispatcher) {
        val path = samplePath(UnitManifestEntry("u-1", "a", "A", 1, "published"))
        val repository = FakePathRepository(path = path)
        val viewModel = PathHomeViewModel(repository, FakeCompletionCache())
        viewModel.load()
        advanceUntilIdle()

        repository.getPathError = RuntimeException("server down")
        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState
        assertTrue("stale Loaded must survive a failed refresh, got $state", state is PathHomeUiState.Loaded)
        assertEquals("A", (state as PathHomeUiState.Loaded).path.units.first().title)
    }

    @Test
    fun `first load failure still shows the error state`() = runTest(dispatcher) {
        val repository = FakePathRepository(path = null, error = RuntimeException("down"))
        val viewModel = PathHomeViewModel(repository, FakeCompletionCache())
        viewModel.load()
        advanceUntilIdle()
        assertTrue(viewModel.uiState is PathHomeUiState.Error)
    }

    @Test
    fun `unit newly completed between loads emits UnitCompleted with the unlocked title`() =
        runTest(dispatcher) {
            val path = samplePath(
                UnitManifestEntry("u-1", "a", "A", 1, "published"),
                UnitManifestEntry("u-2", "b", "B", 2, "published", prereqUnitIds = listOf("u-1"))
            )
            val cache = FakeCompletionCache()
            val viewModel = PathHomeViewModel(FakePathRepository(path = path), cache)
            viewModel.load()
            advanceUntilIdle()

            cache.add("u-1") // the unit reader recorded a completion
            viewModel.load()
            advanceUntilIdle()

            val event = withTimeoutOrNull(1_000) { viewModel.events.first() }
            assertTrue("expected UnitCompleted, got $event", event is PathHomeEvent.UnitCompleted)
            event as PathHomeEvent.UnitCompleted
            assertEquals("A", event.completedTitle)
            assertEquals("B", event.unlockedTitle)
            assertEquals(false, event.pathComplete)
        }

    @Test
    fun `unchanged completion state emits no UnitCompleted on refresh`() = runTest(dispatcher) {
        val path = samplePath(UnitManifestEntry("u-1", "a", "A", 1, "published"))
        val viewModel = PathHomeViewModel(
            FakePathRepository(path = path),
            FakeCompletionCache(initial = setOf("u-1"))
        )
        viewModel.load()
        advanceUntilIdle()
        viewModel.load()
        advanceUntilIdle()

        val event = withTimeoutOrNull(50) { viewModel.events.first() }
        assertNull("no completion delta — no event expected", event)
    }

    private fun samplePath(vararg units: UnitManifestEntry): Path = Path(
        id = "llm-systems-for-pms",
        slug = "llm-systems-for-pms",
        title = "LLM Systems for PMs",
        description = "desc",
        units = units.toList()
    )
}

private class FakePathRepository(
    private val path: Path? = null,
    private val unit: UnitDetail? = null,
    private val error: Throwable? = null,
    private val cacheToSeed: FakeCompletionCache? = null,
    private val syncedUnitIds: Set<String> = emptySet(),
    private val syncError: Throwable? = null,
    private val reviewsDue: List<ReviewDue> = emptyList(),
    private val reviewsError: Throwable? = null
) : PathRepository {
    var getPathCalls = 0
        private set
    var syncCalls = 0
        private set
    var listDueReviewsCalls = 0
        private set
    val markedReviewedUnitIds = mutableListOf<String>()

    /** Settable mid-test: makes the NEXT getPath fail (refresh-failure cases). */
    var getPathError: Throwable? = null

    override suspend fun getPath(pathId: String): Path {
        getPathCalls++
        getPathError?.let { throw it }
        error?.let { throw it }
        return path ?: error("no path stub set")
    }

    override suspend fun getUnit(unitId: String): UnitDetail {
        error?.let { throw it }
        return unit ?: error("no unit stub set")
    }

    override suspend fun markComplete(unitId: String): CompletionRecord {
        error?.let { throw it }
        return CompletionRecord(1L, "u", "p", unitId, "now")
    }

    override suspend fun submitGrade(unitId: String, answer: String): com.perpenda.model.GradeResult =
        error("not used in PathHomeViewModelTest")

    override suspend fun syncCompletedUnits() {
        syncCalls++
        syncError?.let { throw it }
        cacheToSeed?.replaceAll(syncedUnitIds)
    }

    override suspend fun listDueReviews(): List<ReviewDue> {
        listDueReviewsCalls++
        reviewsError?.let { throw it }
        return reviewsDue
    }

    override suspend fun markReviewed(unitId: String) {
        markedReviewedUnitIds.add(unitId)
    }
}

private class FakeCompletionCache(initial: Set<String> = emptySet()) : CompletionCache {
    private val store = initial.toMutableSet()
    override fun completedUnitIds(): Set<String> = store.toSet()
    override fun add(unitId: String) { store.add(unitId) }
    override fun replaceAll(unitIds: Set<String>) {
        store.clear()
        store.addAll(unitIds)
    }
    override fun clear() { store.clear() }
}
