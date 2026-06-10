package com.perpenda.data.repository

import com.perpenda.data.auth.AuthApiException
import com.perpenda.data.auth.AuthApiService
import com.perpenda.data.auth.TokenStorage
import com.perpenda.model.AuthSession
import com.perpenda.model.SignupResult
import com.perpenda.model.User

interface AuthRepository {
    suspend fun signup(email: String, password: String, displayName: String): SignupResult
    suspend fun login(email: String, password: String): AuthSession
    suspend fun verifyEmail(email: String, code: String): AuthSession
    suspend fun resendVerification(email: String)
    suspend fun requestPasswordReset(email: String)
    suspend fun resetPassword(email: String, code: String, newPassword: String): AuthSession
    suspend fun refreshSession(): User?
    fun currentUser(): User?
    fun isLoggedIn(): Boolean
    fun token(): String?
    fun logout()
    suspend fun deleteAccount()
}

class ApiAuthRepository(
    private val authApiService: AuthApiService,
    private val tokenStorage: TokenStorage,
    private val completionCache: CompletionCache
) : AuthRepository {

    override suspend fun signup(
        email: String,
        password: String,
        displayName: String
    ): SignupResult {
        val result = authApiService.signup(email, password, displayName)
        // Only a real session is persisted; a verification-pending account
        // has no token yet (it arrives from verifyEmail).
        if (result is SignupResult.Session) {
            persist(result.session)
        }
        return result
    }

    override suspend fun login(email: String, password: String): AuthSession {
        val session = authApiService.login(email, password)
        persist(session)
        return session
    }

    override suspend fun verifyEmail(email: String, code: String): AuthSession {
        val session = authApiService.verifyEmail(email, code)
        persist(session)
        return session
    }

    override suspend fun resendVerification(email: String) {
        authApiService.resendVerification(email)
    }

    override suspend fun requestPasswordReset(email: String) {
        authApiService.requestPasswordReset(email)
    }

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String
    ): AuthSession {
        val session = authApiService.resetPassword(email, code, newPassword)
        persist(session)
        return session
    }

    override suspend fun refreshSession(): User? {
        val token = tokenStorage.getToken() ?: return null
        return try {
            val user = authApiService.fetchMe(token)
            tokenStorage.saveDisplayName(user.displayName)
            tokenStorage.saveEmail(user.email)
            tokenStorage.saveUserId(user.id)
            user
        } catch (error: AuthApiException) {
            if (error.statusCode == 401) {
                tokenStorage.clear()
            }
            null
        }
    }

    override fun currentUser(): User? {
        val id = tokenStorage.getUserId() ?: return null
        val email = tokenStorage.getEmail() ?: return null
        val displayName = tokenStorage.getDisplayName() ?: return null
        return User(id = id, email = email, displayName = displayName)
    }

    override fun isLoggedIn(): Boolean = tokenStorage.getToken() != null

    override fun token(): String? = tokenStorage.getToken()

    override fun logout() {
        // Clear the completion cache BEFORE token storage: the cache key is
        // derived from the stored userId, which tokenStorage.clear() erases.
        completionCache.clear()
        tokenStorage.clear()
    }

    override suspend fun deleteAccount() {
        val token = tokenStorage.getToken() ?: return
        authApiService.deleteAccount(token)
        // Same ordering constraint as logout(): the cache needs the stored
        // userId to find its entry, so it must be cleared first — otherwise
        // the deleted account's progress stays in (backed-up) SharedPreferences,
        // breaking the "delete your account and all your data" promise.
        completionCache.clear()
        tokenStorage.clear()
    }

    private fun persist(session: AuthSession) {
        tokenStorage.saveToken(session.token)
        tokenStorage.saveDisplayName(session.user.displayName)
        tokenStorage.saveEmail(session.user.email)
        tokenStorage.saveUserId(session.user.id)
    }
}
