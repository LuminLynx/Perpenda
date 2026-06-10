package com.perpenda.data.repository

import com.perpenda.data.auth.AuthApiService
import com.perpenda.data.auth.TokenStorage
import com.perpenda.model.AuthSession
import com.perpenda.model.SignupResult
import com.perpenda.model.User
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression tests for the clear ordering in logout()/deleteAccount():
 * the completion cache is keyed by the stored userId, so it must be
 * cleared BEFORE token storage — otherwise the cache entry survives
 * forever, breaking the "delete your account and all your data" promise.
 */
class ApiAuthRepositoryTest {

    @Test
    fun `deleteAccount clears completion cache while userId is still readable`() = runTest {
        val storage = FakeTokenStorage(token = "jwt", userId = "user-1")
        val cache = RecordingCache { storage.getUserId() }
        val repository = ApiAuthRepository(FakeAuthApiService(), storage, cache)

        repository.deleteAccount()

        assertEquals(listOf("user-1"), cache.clearedForUserIds)
        assertNull(storage.getToken())
    }

    @Test
    fun `logout clears completion cache while userId is still readable`() = runTest {
        val storage = FakeTokenStorage(token = "jwt", userId = "user-1")
        val cache = RecordingCache { storage.getUserId() }
        val repository = ApiAuthRepository(FakeAuthApiService(), storage, cache)

        repository.logout()

        assertEquals(listOf("user-1"), cache.clearedForUserIds)
        assertNull(storage.getToken())
    }

    @Test
    fun `deleteAccount without a session touches neither backend nor cache`() = runTest {
        val storage = FakeTokenStorage(token = null, userId = null)
        val api = FakeAuthApiService()
        val cache = RecordingCache { storage.getUserId() }
        val repository = ApiAuthRepository(api, storage, cache)

        repository.deleteAccount()

        assertEquals(0, api.deleteCalls)
        assertEquals(emptyList<String?>(), cache.clearedForUserIds)
    }

    /** Records the userId visible at the moment clear() runs. */
    private class RecordingCache(
        private val userIdProvider: () -> String?
    ) : CompletionCache {
        val clearedForUserIds = mutableListOf<String?>()

        override fun completedUnitIds(): Set<String> = emptySet()
        override fun add(unitId: String) = Unit
        override fun replaceAll(unitIds: Set<String>) = Unit
        override fun clear() {
            clearedForUserIds += userIdProvider()
        }
    }

    private class FakeTokenStorage(
        private var token: String?,
        private var userId: String?
    ) : TokenStorage {
        private var email: String? = null
        private var displayName: String? = null

        override fun saveToken(token: String) { this.token = token }
        override fun getToken(): String? = token
        override fun saveDisplayName(displayName: String?) { this.displayName = displayName }
        override fun getDisplayName(): String? = displayName
        override fun saveEmail(email: String?) { this.email = email }
        override fun getEmail(): String? = email
        override fun saveUserId(userId: String?) { this.userId = userId }
        override fun getUserId(): String? = userId
        override fun clear() {
            token = null
            userId = null
            email = null
            displayName = null
        }
    }

    private class FakeAuthApiService : AuthApiService {
        var deleteCalls = 0

        override suspend fun signup(email: String, password: String, displayName: String): SignupResult =
            SignupResult.Session(AuthSession(token = "jwt", user = User("user-1", email, displayName)))

        override suspend fun login(email: String, password: String) =
            AuthSession(token = "jwt", user = User("user-1", email, "Name"))

        override suspend fun verifyEmail(email: String, code: String) =
            AuthSession(token = "jwt", user = User("user-1", email, "Name"))

        override suspend fun resendVerification(email: String) = Unit

        override suspend fun requestPasswordReset(email: String) = Unit

        override suspend fun resetPassword(email: String, code: String, newPassword: String) =
            AuthSession(token = "jwt", user = User("user-1", email, "Name"))

        override suspend fun fetchMe(token: String) = User("user-1", "a@b.com", "Name")

        override suspend fun deleteAccount(token: String) {
            deleteCalls += 1
        }
    }
}
