package com.perpenda.data.repository

import com.perpenda.data.remote.api.PathApiService
import com.perpenda.model.GradeResult
import com.perpenda.model.Path
import com.perpenda.model.ReviewDue
import com.perpenda.model.UnitDetail

interface PathRepository {
    suspend fun getPath(pathId: String): Path
    suspend fun getUnit(unitId: String): UnitDetail

    /**
     * F4 — submit the user's open-ended decision-prompt answer for grading.
     * The server records a completion + persists per-criterion grades on
     * success; the local completion cache is updated so path home reflects
     * the new state without an extra round trip.
     */
    suspend fun submitGrade(unitId: String, answer: String): GradeResult

    /**
     * Pull the authenticated user's completion list from the server and
     * replace the local cache with it. Used to seed the cache after sign-in
     * or on a fresh install so completion state syncs across devices for
     * the same account.
     */
    suspend fun syncCompletedUnits()

    /**
     * F5 / D5 — spaced reviews currently due for the user. Surfaced
     * alongside (never gating) the next new unit on path home; the
     * ViewModel calls this best-effort and a failure must not block
     * the rest of the screen.
     */
    suspend fun listDueReviews(): List<ReviewDue>

    /**
     * F5 / D6 — mark a due review done (advances the spaced-review
     * ladder server-side). Best-effort: the server gates it
     * (404 never-completed / 409 not-yet-due) and the caller
     * swallows failures.
     */
    suspend fun markReviewed(unitId: String)
}

class ApiPathRepository(
    private val pathApiService: PathApiService,
    private val completionCache: CompletionCache
) : PathRepository {

    override suspend fun getPath(pathId: String): Path = pathApiService.getPath(pathId)

    override suspend fun getUnit(unitId: String): UnitDetail = pathApiService.getUnit(unitId)

    override suspend fun submitGrade(unitId: String, answer: String): GradeResult {
        val result = pathApiService.submitGrade(unitId, answer)
        // Only a completing grade updates local progress — a below-the-bar
        // submission returns calibration only (completion == null).
        result.completion?.let { completionCache.add(it.unitId) }
        return result
    }

    override suspend fun syncCompletedUnits() {
        val records = pathApiService.listCompletions()
        completionCache.replaceAll(records.map { it.unitId }.toSet())
    }

    override suspend fun listDueReviews(): List<ReviewDue> =
        pathApiService.listDueReviews()

    override suspend fun markReviewed(unitId: String) =
        pathApiService.markReviewed(unitId)
}
