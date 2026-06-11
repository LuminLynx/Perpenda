package com.perpenda.model

data class UnitManifestEntry(
    val id: String,
    val slug: String,
    val title: String,
    val position: Int,
    val status: String,
    /** Unit ids that must be completed before this unit unlocks. Empty = no gate. */
    val prereqUnitIds: List<String> = emptyList()
)

data class Path(
    val id: String,
    val slug: String,
    val title: String,
    val description: String,
    val units: List<UnitManifestEntry>
)

data class UnitSource(
    val id: Long,
    val url: String,
    val title: String,
    val date: String,
    val primarySource: Boolean
)

data class CalibrationTag(
    val id: Long,
    val claim: String,
    val tier: String
)

data class DecisionPrompt(
    val id: Long,
    val promptMd: String
)

data class RubricCriterion(
    val id: Long,
    val position: Int,
    val text: String
)

data class Rubric(
    val id: Long,
    val version: Int,
    val criteria: List<RubricCriterion>
)

data class UnitDetail(
    val id: String,
    val pathId: String,
    val slug: String,
    val position: Int,
    val title: String,
    val definition: String,
    val tradeOffFraming: String,
    val biteMd: String,
    val depthMd: String,
    val prereqUnitIds: List<String>,
    val status: String,
    val sources: List<UnitSource>,
    val calibrationTags: List<CalibrationTag>,
    val decisionPrompt: DecisionPrompt?,
    val rubric: Rubric?
)

data class CompletionRecord(
    val id: Long,
    val userId: String,
    val pathId: String,
    val unitId: String,
    val completedAt: String
)

/**
 * One per-criterion grade returned by the F4 grader. Mirrors the
 * backend `grades` table + the inline `answer_quote` echoed in the
 * grade endpoint response (the column doesn't exist on `grades`
 * yet — see backend/app/repositories/grade_repository.py).
 */
data class Grade(
    val id: Long,
    val criterionId: Long,
    val met: Boolean,
    val confidence: Double,
    val rationale: String,
    val flagged: Boolean,
    /** Verbatim span the grader quoted from the user's answer. Empty when met=false. */
    val answerQuote: String
)

data class GradeResult(
    /**
     * Null when the answer didn't clear the completion bar (more than one
     * criterion Not met, or flagged) — grades still return as calibration
     * but no completion was recorded server-side.
     */
    val completion: CompletionRecord?,
    val grades: List<Grade>,
    /** True when the grader flagged the answer for review (T2-B). */
    val flagged: Boolean,
    /** True when this submission completed the unit (T2 amendment). */
    val completed: Boolean = true
)

/**
 * One spaced-review entry due for the user (F5 / Loop step 6).
 * Mirrors the backend `_map_due_row` shape from
 * GET /api/v1/review-schedule. `lastReviewedAt` is null until the
 * unit has been reviewed at least once.
 */
data class ReviewDue(
    val unitId: String,
    val slug: String,
    val title: String,
    val dueAt: String,
    val intervalDays: Int,
    val lastReviewedAt: String?
)
