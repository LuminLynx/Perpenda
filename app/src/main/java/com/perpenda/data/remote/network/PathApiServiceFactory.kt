package com.perpenda.data.remote.network

import com.perpenda.data.remote.api.PathApiException
import com.perpenda.data.remote.api.PathApiService
import com.perpenda.model.CalibrationTag
import com.perpenda.model.CompletionRecord
import com.perpenda.model.DecisionPrompt
import com.perpenda.model.Grade
import com.perpenda.model.GradeResult
import com.perpenda.model.Path
import com.perpenda.model.ReviewDue
import com.perpenda.model.Rubric
import com.perpenda.model.RubricCriterion
import com.perpenda.model.UnitDetail
import com.perpenda.model.UnitManifestEntry
import com.perpenda.model.UnitSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object PathApiServiceFactory {
    fun create(
        config: ApiConfig,
        tokenProvider: () -> String?
    ): PathApiService = HttpPathApiService(config, tokenProvider)
}

private class HttpPathApiService(
    private val config: ApiConfig,
    private val tokenProvider: () -> String?
) : PathApiService {

    override suspend fun getPath(pathId: String): Path = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(pathId, Charsets.UTF_8.name())
        val envelope = request("GET", "api/v1/paths/$encodedId", payload = null)
        parsePath(envelope.requireData())
    }

    override suspend fun getUnit(unitId: String): UnitDetail = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(unitId, Charsets.UTF_8.name())
        val envelope = request("GET", "api/v1/units/$encodedId", payload = null)
        parseUnit(envelope.requireData())
    }

    override suspend fun listCompletions(): List<CompletionRecord> = withContext(Dispatchers.IO) {
        val envelope = request("GET", "api/v1/completions", payload = null)
        val array = envelope.optJSONArray("data") ?: JSONArray()
        array.map(::parseCompletion)
    }

    override suspend fun submitGrade(unitId: String, answer: String): GradeResult = withContext(Dispatchers.IO) {
        val encodedId = URLEncoder.encode(unitId, Charsets.UTF_8.name())
        val payload = JSONObject().put("answer", answer)
        val envelope = request("POST", "api/v1/units/$encodedId/grade", payload = payload)
        val data = envelope.requireData()
        val completion = data.optJSONObject("completion")?.let(::parseCompletion)
        // Derive "completed" from the presence of a completion object when
        // the flag is absent (pre-T2-amendment backends omit it and always
        // completed). Reading the flag only when explicitly present avoids
        // optBoolean's footgun of treating a wrong-typed value as the
        // default, and avoids turning a valid below-bar result (completion:
        // null) into a hard parse error.
        val completed = if (data.has("completed") && !data.isNull("completed")) {
            data.optBoolean("completed", completion != null)
        } else {
            completion != null
        }
        val gradesArray = data.optJSONArray("grades") ?: JSONArray()
        val quotesArray = data.optJSONArray("answerQuotes") ?: JSONArray()
        val quoteByCriterion: Map<Long, String> = buildMap {
            for (i in 0 until quotesArray.length()) {
                val q = quotesArray.getJSONObject(i)
                put(q.optLong("criterionId"), q.optString("quote"))
            }
        }
        val grades = gradesArray.map { item ->
            val criterionId = item.optLong("criterionId")
            Grade(
                id = item.optLong("id"),
                criterionId = criterionId,
                met = item.optBoolean("met"),
                confidence = item.optDouble("confidence", 0.0),
                rationale = item.optString("rationale"),
                flagged = item.optBoolean("flagged"),
                answerQuote = quoteByCriterion[criterionId].orEmpty()
            )
        }
        GradeResult(
            completion = completion,
            grades = grades,
            flagged = data.optBoolean("flagged"),
            completed = completed
        )
    }

    override suspend fun listDueReviews(): List<ReviewDue> = withContext(Dispatchers.IO) {
        val envelope = request("GET", "api/v1/review-schedule", payload = null)
        val array = envelope.optJSONArray("data") ?: JSONArray()
        array.map(::parseReviewDue)
    }

    override suspend fun markReviewed(unitId: String) {
        withContext(Dispatchers.IO) {
            val encodedId = URLEncoder.encode(unitId, Charsets.UTF_8.name())
            // POST with no body; the server takes no payload. A 404/409
            // (never completed / not yet due) surfaces as PathApiException
            // and is treated best-effort by the caller.
            request("POST", "api/v1/review-schedule/$encodedId/reviewed", payload = null)
        }
    }

    private fun request(method: String, path: String, payload: JSONObject?): JSONObject {
        val requestUrl = URL(config.baseUrl.trimEnd('/') + "/" + path)
        val connection = (requestUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = config.connectTimeoutMillis.toInt()
            readTimeout = config.readTimeoutMillis.toInt()
            setRequestProperty("Accept", "application/json")
            tokenProvider()?.takeIf { it.isNotBlank() }?.let { token ->
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        return try {
            if (payload != null) {
                connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            }
            val responseCode = connection.responseCode
            val responseText = when {
                responseCode in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                else -> connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            parseEnvelope(responseText, responseCode)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEnvelope(responseText: String, responseCode: Int): JSONObject {
        val envelope = runCatching { JSONObject(responseText) }
            .getOrElse {
                throw PathApiException(
                    message = "Unexpected server response (HTTP $responseCode).",
                    statusCode = responseCode
                )
            }

        val errorObject = envelope.optJSONObject("error")
        if (errorObject != null && !errorObject.isNull("code")) {
            throw PathApiException(
                message = errorObject.optString("message", "Request failed."),
                code = errorObject.optString("code", "UNKNOWN_ERROR"),
                statusCode = responseCode
            )
        }

        if (responseCode !in 200..299) {
            throw PathApiException(
                message = "Server error (HTTP $responseCode).",
                statusCode = responseCode
            )
        }
        return envelope
    }

    private fun JSONObject.requireData(): JSONObject {
        return optJSONObject("data")
            ?: throw PathApiException("Response was empty.")
    }
}

private fun parsePath(data: JSONObject): Path {
    val unitsArray = data.optJSONArray("units") ?: JSONArray()
    val units = buildList {
        for (i in 0 until unitsArray.length()) {
            add(parseUnitManifestEntry(unitsArray.getJSONObject(i)))
        }
    }
    return Path(
        id = data.optString("id"),
        slug = data.optString("slug"),
        title = data.optString("title"),
        description = data.optString("description"),
        units = units.sortedBy { it.position }
    )
}

private fun parseUnitManifestEntry(item: JSONObject): UnitManifestEntry {
    val prereqArray = item.optJSONArray("prereqUnitIds") ?: JSONArray()
    val prereqs = buildList {
        for (i in 0 until prereqArray.length()) add(prereqArray.optString(i))
    }.filter { it.isNotBlank() }
    return UnitManifestEntry(
        id = item.optString("id"),
        slug = item.optString("slug"),
        title = item.optString("title"),
        position = item.optInt("position", 0),
        status = item.optString("status"),
        prereqUnitIds = prereqs
    )
}

private fun parseUnit(data: JSONObject): UnitDetail {
    val sources = (data.optJSONArray("sources") ?: JSONArray()).map(::parseSource)
    val calibrationTags = (data.optJSONArray("calibrationTags") ?: JSONArray()).map(::parseCalibrationTag)
    val prereqArray = data.optJSONArray("prereqUnitIds") ?: JSONArray()
    val prereqs = buildList {
        for (i in 0 until prereqArray.length()) add(prereqArray.optString(i))
    }.filter { it.isNotBlank() }

    val prompt = data.optJSONObject("decisionPrompt")?.let {
        DecisionPrompt(
            id = it.optLong("id"),
            promptMd = it.optString("promptMd")
        )
    }
    val rubric = data.optJSONObject("rubric")?.let { rubricObj ->
        val criteriaArray = rubricObj.optJSONArray("criteria") ?: JSONArray()
        Rubric(
            id = rubricObj.optLong("id"),
            version = rubricObj.optInt("version", 1),
            criteria = criteriaArray.map { item ->
                RubricCriterion(
                    id = item.optLong("id"),
                    position = item.optInt("position", 0),
                    text = item.optString("text")
                )
            }.sortedBy { it.position }
        )
    }

    return UnitDetail(
        id = data.optString("id"),
        pathId = data.optString("pathId"),
        slug = data.optString("slug"),
        position = data.optInt("position", 0),
        title = data.optString("title"),
        definition = data.optString("definition"),
        tradeOffFraming = data.optString("tradeOffFraming"),
        biteMd = data.optString("biteMd"),
        depthMd = data.optString("depthMd"),
        prereqUnitIds = prereqs,
        status = data.optString("status"),
        sources = sources,
        calibrationTags = calibrationTags,
        decisionPrompt = prompt,
        rubric = rubric
    )
}

private fun parseSource(item: JSONObject): UnitSource = UnitSource(
    id = item.optLong("id"),
    url = item.optString("url"),
    title = item.optString("title"),
    date = item.optString("date"),
    primarySource = item.optBoolean("primarySource", false)
)

private fun parseCalibrationTag(item: JSONObject): CalibrationTag = CalibrationTag(
    id = item.optLong("id"),
    claim = item.optString("claim"),
    tier = item.optString("tier")
)

private fun parseCompletion(data: JSONObject): CompletionRecord = CompletionRecord(
    id = data.optLong("id"),
    userId = data.optString("userId"),
    pathId = data.optString("pathId"),
    unitId = data.optString("unitId"),
    completedAt = data.optString("completedAt")
)

private fun parseReviewDue(item: JSONObject): ReviewDue = ReviewDue(
    unitId = item.optString("unitId"),
    slug = item.optString("slug"),
    title = item.optString("title"),
    dueAt = item.optString("dueAt"),
    intervalDays = item.optInt("intervalDays", 0),
    lastReviewedAt = if (item.isNull("lastReviewedAt")) null else item.optString("lastReviewedAt")
)

private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> = buildList {
    for (i in 0 until length()) add(transform(getJSONObject(i)))
}
