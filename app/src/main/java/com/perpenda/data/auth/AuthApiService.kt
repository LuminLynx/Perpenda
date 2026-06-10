package com.perpenda.data.auth

import com.perpenda.data.remote.network.ApiConfig
import com.perpenda.model.AuthSession
import com.perpenda.model.SignupResult
import com.perpenda.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

interface AuthApiService {
    suspend fun signup(email: String, password: String, displayName: String): SignupResult
    suspend fun login(email: String, password: String): AuthSession
    suspend fun verifyEmail(email: String, code: String): AuthSession
    suspend fun resendVerification(email: String)
    suspend fun requestPasswordReset(email: String)
    suspend fun resetPassword(email: String, code: String, newPassword: String): AuthSession
    suspend fun fetchMe(token: String): User
    suspend fun deleteAccount(token: String)
}

class AuthApiException(
    override val message: String,
    val code: String? = null,
    val statusCode: Int? = null
) : RuntimeException(message)

object AuthApiServiceFactory {
    fun create(config: ApiConfig): AuthApiService = HttpAuthApiService(config)
}

private class HttpAuthApiService(
    private val config: ApiConfig
) : AuthApiService {

    override suspend fun signup(
        email: String,
        password: String,
        displayName: String
    ): SignupResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("displayName", displayName)
        val envelope = request("POST", "api/v1/auth/signup", payload, token = null)
        val data = envelope.optJSONObject("data")
            ?: throw AuthApiException("Auth response was empty.")
        // EMAIL_VERIFICATION_REQUIRED backends return 201 with no token;
        // the session arrives later from the verify-email call.
        if (data.optBoolean("verificationRequired", false)) {
            SignupResult.VerificationRequired(email = email)
        } else {
            SignupResult.Session(parseAuthSession(envelope))
        }
    }

    override suspend fun login(email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email)
            .put("password", password)
        val envelope = request("POST", "api/v1/auth/login", payload, token = null)
        parseAuthSession(envelope)
    }

    override suspend fun verifyEmail(email: String, code: String): AuthSession = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email)
            .put("code", code)
        val envelope = request("POST", "api/v1/auth/verify-email", payload, token = null)
        parseAuthSession(envelope)
    }

    override suspend fun resendVerification(email: String) {
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("email", email)
            request("POST", "api/v1/auth/resend-verification", payload, token = null)
        }
    }

    override suspend fun requestPasswordReset(email: String) {
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("email", email)
            request("POST", "api/v1/auth/request-password-reset", payload, token = null)
        }
    }

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String
    ): AuthSession = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email)
            .put("code", code)
            .put("newPassword", newPassword)
        val envelope = request("POST", "api/v1/auth/reset-password", payload, token = null)
        parseAuthSession(envelope)
    }

    override suspend fun fetchMe(token: String): User = withContext(Dispatchers.IO) {
        val envelope = request("GET", "api/v1/auth/me", payload = null, token = token)
        val data = envelope.optJSONObject("data")
            ?: throw AuthApiException("Auth response was empty.")
        User(
            id = data.optString("id"),
            email = data.optString("email"),
            displayName = data.optString("displayName")
        )
    }

    override suspend fun deleteAccount(token: String) {
        withContext(Dispatchers.IO) {
            request("DELETE", "api/v1/auth/me", payload = null, token = token)
        }
    }

    private fun parseAuthSession(envelope: JSONObject): AuthSession {
        val data = envelope.optJSONObject("data")
            ?: throw AuthApiException("Auth response was empty.")
        val userObj = data.optJSONObject("user")
            ?: throw AuthApiException("Auth response missing user object.")
        val token = data.optString("token").takeIf { it.isNotBlank() }
            ?: throw AuthApiException("Auth response missing token.")
        return AuthSession(
            token = token,
            user = User(
                id = userObj.optString("id"),
                email = userObj.optString("email"),
                displayName = userObj.optString("displayName")
            )
        )
    }

    private fun request(
        method: String,
        path: String,
        payload: JSONObject?,
        token: String?
    ): JSONObject {
        val requestUrl = URL(config.baseUrl.trimEnd('/') + "/" + path)
        val connection = (requestUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = config.connectTimeoutMillis.toInt()
            readTimeout = config.readTimeoutMillis.toInt()
            setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) {
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
                throw AuthApiException(
                    message = "Unexpected server response (HTTP $responseCode).",
                    statusCode = responseCode
                )
            }

        val errorObject = envelope.optJSONObject("error")
        if (errorObject != null && !errorObject.isNull("code")) {
            throw AuthApiException(
                message = errorObject.optString("message", "Request failed."),
                code = errorObject.optString("code", "UNKNOWN_ERROR"),
                statusCode = responseCode
            )
        }

        if (responseCode !in 200..299) {
            throw AuthApiException(
                message = "Server error (HTTP $responseCode).",
                statusCode = responseCode
            )
        }
        return envelope
    }
}
