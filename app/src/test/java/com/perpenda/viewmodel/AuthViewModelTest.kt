package com.perpenda.viewmodel

import com.perpenda.data.auth.AuthApiException
import com.perpenda.data.repository.AuthRepository
import com.perpenda.model.AuthSession
import com.perpenda.model.SignupResult
import com.perpenda.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `signup with verification-required backend shows the code step`() = runTest(dispatcher) {
        val repo = FakeAuthRepo(signupResult = SignupResult.VerificationRequired("ada@example.com"))
        val viewModel = newSignupViewModel(repo)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("ada@example.com", viewModel.uiState.pendingVerificationEmail)
        assertFalse(viewModel.uiState.justAuthenticated)
        assertEquals("", viewModel.uiState.password)
        assertNotNull(viewModel.uiState.infoMessage)
    }

    @Test
    fun `signup with legacy backend authenticates directly`() = runTest(dispatcher) {
        val repo = FakeAuthRepo(signupResult = SignupResult.Session(session("ada@example.com")))
        val viewModel = newSignupViewModel(repo)

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.justAuthenticated)
        assertNull(viewModel.uiState.pendingVerificationEmail)
        assertEquals("", viewModel.uiState.password)
    }

    @Test
    fun `verify success authenticates and leaves the code step`() = runTest(dispatcher) {
        val repo = FakeAuthRepo(signupResult = SignupResult.VerificationRequired("ada@example.com"))
        val viewModel = newSignupViewModel(repo)
        viewModel.submit()
        advanceUntilIdle()

        viewModel.onVerificationCodeChanged("123456")
        viewModel.submitVerificationCode()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.justAuthenticated)
        assertNull(viewModel.uiState.pendingVerificationEmail)
        assertEquals(listOf("ada@example.com" to "123456"), repo.verifyCalls)
    }

    @Test
    fun `wrong code surfaces INVALID_CODE message and stays on the step`() = runTest(dispatcher) {
        val repo = FakeAuthRepo(
            signupResult = SignupResult.VerificationRequired("ada@example.com"),
            verifyError = AuthApiException("nope", code = "INVALID_CODE", statusCode = 400)
        )
        val viewModel = newSignupViewModel(repo)
        viewModel.submit()
        advanceUntilIdle()

        viewModel.onVerificationCodeChanged("000000")
        viewModel.submitVerificationCode()
        advanceUntilIdle()

        assertEquals("ada@example.com", viewModel.uiState.pendingVerificationEmail)
        assertFalse(viewModel.uiState.justAuthenticated)
        assertEquals("That code didn't work. Check it and try again.", viewModel.uiState.errorMessage)
    }

    @Test
    fun `short code is rejected locally without an API call`() = runTest(dispatcher) {
        val repo = FakeAuthRepo(signupResult = SignupResult.VerificationRequired("ada@example.com"))
        val viewModel = newSignupViewModel(repo)
        viewModel.submit()
        advanceUntilIdle()

        viewModel.onVerificationCodeChanged("123")
        viewModel.submitVerificationCode()
        advanceUntilIdle()

        assertTrue(repo.verifyCalls.isEmpty())
        assertNotNull(viewModel.uiState.errorMessage)
    }

    @Test
    fun `code input keeps digits only and caps at six`() = runTest(dispatcher) {
        val repo = FakeAuthRepo(signupResult = SignupResult.VerificationRequired("ada@example.com"))
        val viewModel = newSignupViewModel(repo)

        viewModel.onVerificationCodeChanged("12a3-456789")

        assertEquals("123456", viewModel.uiState.verificationCode)
    }

    @Test
    fun `login against unverified account routes to code step and auto-resends`() = runTest(dispatcher) {
        val repo = FakeAuthRepo(
            loginError = AuthApiException("verify first", code = "EMAIL_NOT_VERIFIED", statusCode = 403)
        )
        val viewModel = AuthViewModel(repo)
        viewModel.onEmailChanged("ada@example.com")
        viewModel.onPasswordChanged("password123")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("ada@example.com", viewModel.uiState.pendingVerificationEmail)
        assertEquals(listOf("ada@example.com"), repo.resendCalls)
        assertNull(viewModel.uiState.errorMessage)
    }

    @Test
    fun `resend reports a fresh code was sent`() = runTest(dispatcher) {
        val repo = FakeAuthRepo(signupResult = SignupResult.VerificationRequired("ada@example.com"))
        val viewModel = newSignupViewModel(repo)
        viewModel.submit()
        advanceUntilIdle()

        viewModel.resendVerificationCode()
        advanceUntilIdle()

        assertEquals(listOf("ada@example.com"), repo.resendCalls)
        assertEquals("New code sent to ada@example.com.", viewModel.uiState.infoMessage)
    }

    @Test
    fun `cancel returns to the form and clears the code`() = runTest(dispatcher) {
        val repo = FakeAuthRepo(signupResult = SignupResult.VerificationRequired("ada@example.com"))
        val viewModel = newSignupViewModel(repo)
        viewModel.submit()
        advanceUntilIdle()
        viewModel.onVerificationCodeChanged("123456")

        viewModel.cancelVerification()

        assertNull(viewModel.uiState.pendingVerificationEmail)
        assertEquals("", viewModel.uiState.verificationCode)
    }

    private fun newSignupViewModel(repo: AuthRepository): AuthViewModel {
        val viewModel = AuthViewModel(repo)
        viewModel.setMode(AuthMode.Signup)
        viewModel.onEmailChanged("ada@example.com")
        viewModel.onPasswordChanged("password123")
        viewModel.onDisplayNameChanged("Ada")
        return viewModel
    }

    private fun session(email: String) = AuthSession(
        token = "jwt",
        user = User(id = "u-1", email = email, displayName = "Ada")
    )

    private class FakeAuthRepo(
        private val signupResult: SignupResult? = null,
        private val verifyError: AuthApiException? = null,
        private val loginError: AuthApiException? = null
    ) : AuthRepository {
        val verifyCalls = mutableListOf<Pair<String, String>>()
        val resendCalls = mutableListOf<String>()

        override suspend fun signup(email: String, password: String, displayName: String): SignupResult =
            signupResult ?: error("signup not stubbed")

        override suspend fun login(email: String, password: String): AuthSession {
            loginError?.let { throw it }
            return AuthSession(token = "jwt", user = User("u-1", email, "Ada"))
        }

        override suspend fun verifyEmail(email: String, code: String): AuthSession {
            verifyCalls += email to code
            verifyError?.let { throw it }
            return AuthSession(token = "jwt", user = User("u-1", email, "Ada"))
        }

        override suspend fun resendVerification(email: String) {
            resendCalls += email
        }

        override suspend fun refreshSession(): User? = null
        override fun currentUser(): User? = null
        override fun isLoggedIn(): Boolean = false
        override fun token(): String? = null
        override fun logout() = Unit
        override suspend fun deleteAccount() = Unit
    }
}
