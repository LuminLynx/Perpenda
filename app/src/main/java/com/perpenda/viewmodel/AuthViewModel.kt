package com.perpenda.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.perpenda.data.auth.AuthApiException
import com.perpenda.data.repository.AuthRepository
import com.perpenda.model.SignupResult
import com.perpenda.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class AuthMode { Login, Signup }

data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val signedInUser: User? = null,
    val justAuthenticated: Boolean = false,
    /** Non-null = the verify-code step is showing for this address. */
    val pendingVerificationEmail: String? = null,
    /** Non-null = the password-reset step is showing for this address. */
    val pendingResetEmail: String? = null,
    val verificationCode: String = "",
    val infoMessage: String? = null,
    /** One-shot success notice shown (as a toast) when navigating into the
     * app after verify/reset — plain login/signup stays silent as before. */
    val postAuthNotice: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    var uiState by mutableStateOf(
        AuthUiState(signedInUser = authRepository.currentUser())
    )
        private set

    fun setMode(mode: AuthMode) {
        uiState = uiState.copy(mode = mode, errorMessage = null)
    }

    fun onEmailChanged(email: String) {
        uiState = uiState.copy(email = email, errorMessage = null)
    }

    fun onPasswordChanged(password: String) {
        uiState = uiState.copy(password = password, errorMessage = null)
    }

    fun onDisplayNameChanged(displayName: String) {
        uiState = uiState.copy(displayName = displayName, errorMessage = null)
    }

    fun submit() {
        val email = uiState.email.trim()
        val password = uiState.password
        val displayName = uiState.displayName.trim()
        val mode = uiState.mode

        val validationError = when {
            email.isBlank() -> "Email is required."
            !email.contains("@") || !email.contains(".") -> "Enter a valid email address."
            password.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters."
            mode == AuthMode.Signup && displayName.length < MIN_DISPLAY_NAME_LENGTH -> {
                "Display name must be at least $MIN_DISPLAY_NAME_LENGTH characters."
            }
            else -> null
        }
        if (validationError != null) {
            uiState = uiState.copy(errorMessage = validationError)
            return
        }

        uiState = uiState.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            uiState = try {
                when (mode) {
                    AuthMode.Login -> {
                        val session = authRepository.login(email, password)
                        uiState.copy(
                            isSubmitting = false,
                            signedInUser = session.user,
                            justAuthenticated = true,
                            errorMessage = null,
                            password = ""
                        )
                    }
                    AuthMode.Signup -> {
                        when (val result = authRepository.signup(email, password, displayName)) {
                            is SignupResult.Session -> uiState.copy(
                                isSubmitting = false,
                                signedInUser = result.session.user,
                                justAuthenticated = true,
                                errorMessage = null,
                                password = ""
                            )
                            is SignupResult.VerificationRequired -> uiState.copy(
                                isSubmitting = false,
                                errorMessage = null,
                                password = "",
                                pendingVerificationEmail = result.email,
                                verificationCode = "",
                                infoMessage = "We emailed a 6-digit code to ${result.email}."
                            )
                        }
                    }
                }
            } catch (error: AuthApiException) {
                if (error.code == "EMAIL_NOT_VERIFIED") {
                    // Login with the right password but an unconfirmed email:
                    // route to the code step and request a fresh code — any
                    // earlier one has likely expired by now.
                    resendCode(email, silent = true)
                    uiState.copy(
                        isSubmitting = false,
                        errorMessage = null,
                        pendingVerificationEmail = email,
                        verificationCode = "",
                        infoMessage = "Confirm your email to sign in. We've sent a fresh code to $email."
                    )
                } else {
                    uiState.copy(isSubmitting = false, errorMessage = mapErrorMessage(error))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                uiState.copy(isSubmitting = false, errorMessage = "Network error. Please try again.")
            }
        }
    }

    fun onVerificationCodeChanged(code: String) {
        // Digits only, never longer than the 6 the server issues.
        val cleaned = code.filter(Char::isDigit).take(VERIFICATION_CODE_LENGTH)
        uiState = uiState.copy(verificationCode = cleaned, errorMessage = null)
    }

    fun submitVerificationCode() {
        val email = uiState.pendingVerificationEmail ?: return
        val code = uiState.verificationCode
        if (code.length < VERIFICATION_CODE_LENGTH) {
            uiState = uiState.copy(errorMessage = "Enter the 6-digit code from the email.")
            return
        }

        uiState = uiState.copy(isSubmitting = true, errorMessage = null, infoMessage = null)
        viewModelScope.launch {
            uiState = try {
                val session = authRepository.verifyEmail(email, code)
                uiState.copy(
                    isSubmitting = false,
                    signedInUser = session.user,
                    justAuthenticated = true,
                    errorMessage = null,
                    pendingVerificationEmail = null,
                    verificationCode = "",
                    password = "",
                    postAuthNotice = "Email confirmed — your account is ready."
                )
            } catch (error: AuthApiException) {
                uiState.copy(isSubmitting = false, errorMessage = mapErrorMessage(error))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                uiState.copy(isSubmitting = false, errorMessage = "Network error. Please try again.")
            }
        }
    }

    fun resendVerificationCode() {
        val email = uiState.pendingVerificationEmail ?: return
        uiState = uiState.copy(errorMessage = null, infoMessage = null)
        resendCode(email, silent = false)
    }

    /** Leave the code step and return to the login/signup form. */
    fun cancelVerification() {
        uiState = uiState.copy(
            pendingVerificationEmail = null,
            pendingResetEmail = null,
            verificationCode = "",
            errorMessage = null,
            infoMessage = null
        )
    }

    /** "Forgot password?" — request a reset code for the entered email. */
    fun startPasswordReset() {
        val email = uiState.email.trim()
        if (email.isBlank() || !email.contains("@") || !email.contains(".")) {
            uiState = uiState.copy(errorMessage = "Enter your account's email address first.")
            return
        }

        uiState = uiState.copy(isSubmitting = true, errorMessage = null, infoMessage = null)
        viewModelScope.launch {
            uiState = try {
                authRepository.requestPasswordReset(email)
                uiState.copy(
                    isSubmitting = false,
                    pendingResetEmail = email,
                    verificationCode = "",
                    password = "",
                    // The server answers "sent" for any address (so accounts
                    // can't be probed); phrase the message the same way.
                    infoMessage = "If $email has an account, a 6-digit reset code is on its way."
                )
            } catch (error: AuthApiException) {
                uiState.copy(isSubmitting = false, errorMessage = mapErrorMessage(error))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                uiState.copy(isSubmitting = false, errorMessage = "Network error. Please try again.")
            }
        }
    }

    /** Submit the reset code + new password (held in the password field). */
    fun submitPasswordReset() {
        val email = uiState.pendingResetEmail ?: return
        val code = uiState.verificationCode
        val newPassword = uiState.password
        val validationError = when {
            code.length < VERIFICATION_CODE_LENGTH -> "Enter the 6-digit code from the email."
            newPassword.length < MIN_PASSWORD_LENGTH ->
                "Password must be at least $MIN_PASSWORD_LENGTH characters."
            else -> null
        }
        if (validationError != null) {
            uiState = uiState.copy(errorMessage = validationError)
            return
        }

        uiState = uiState.copy(isSubmitting = true, errorMessage = null, infoMessage = null)
        viewModelScope.launch {
            uiState = try {
                val session = authRepository.resetPassword(email, code, newPassword)
                uiState.copy(
                    isSubmitting = false,
                    signedInUser = session.user,
                    justAuthenticated = true,
                    errorMessage = null,
                    pendingResetEmail = null,
                    verificationCode = "",
                    password = "",
                    postAuthNotice = "Password updated — you're signed in."
                )
            } catch (error: AuthApiException) {
                uiState.copy(isSubmitting = false, errorMessage = mapErrorMessage(error))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                uiState.copy(isSubmitting = false, errorMessage = "Network error. Please try again.")
            }
        }
    }

    fun resendResetCode() {
        val email = uiState.pendingResetEmail ?: return
        uiState = uiState.copy(errorMessage = null, infoMessage = null)
        viewModelScope.launch {
            try {
                authRepository.requestPasswordReset(email)
                uiState = uiState.copy(infoMessage = "New code sent to $email.")
            } catch (error: AuthApiException) {
                uiState = uiState.copy(errorMessage = mapErrorMessage(error))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                uiState = uiState.copy(errorMessage = "Network error. Please try again.")
            }
        }
    }

    private fun resendCode(email: String, silent: Boolean) {
        viewModelScope.launch {
            try {
                authRepository.resendVerification(email)
                if (!silent) {
                    uiState = uiState.copy(infoMessage = "New code sent to $email.")
                }
            } catch (error: AuthApiException) {
                if (!silent) {
                    uiState = uiState.copy(errorMessage = mapErrorMessage(error))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!silent) {
                    uiState = uiState.copy(errorMessage = "Network error. Please try again.")
                }
            }
        }
    }

    fun acknowledgeNavigation() {
        if (uiState.justAuthenticated) {
            uiState = uiState.copy(justAuthenticated = false, postAuthNotice = null)
        }
    }

    fun logout() {
        authRepository.logout()
        uiState = AuthUiState(mode = AuthMode.Login)
    }

    fun refreshSignedInUser() {
        uiState = uiState.copy(signedInUser = authRepository.currentUser())
    }

    private fun mapErrorMessage(error: AuthApiException): String {
        return when (error.code) {
            "EMAIL_TAKEN" -> "An account with this email already exists."
            "INVALID_CREDENTIALS" -> "Invalid email or password."
            "WEAK_PASSWORD" -> "Password must be at least $MIN_PASSWORD_LENGTH characters."
            "INVALID_EMAIL" -> "Enter a valid email address."
            "INVALID_DISPLAY_NAME" -> "Display name must be 2-50 characters."
            "INVALID_CODE" -> "That code didn't work. Check it and try again."
            "CODE_EXPIRED" -> "That code expired. Tap \"Resend code\" for a new one."
            "RATE_LIMITED" -> "Too many attempts. Wait a bit and try again."
            else -> error.message.ifBlank { "Something went wrong. Please try again." }
        }
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MIN_DISPLAY_NAME_LENGTH = 2
        private const val VERIFICATION_CODE_LENGTH = 6

        fun factory(authRepository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(authRepository) as T
                }
            }
    }
}
