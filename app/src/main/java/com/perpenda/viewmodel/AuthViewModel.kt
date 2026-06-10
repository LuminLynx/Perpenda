package com.perpenda.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.perpenda.data.auth.AuthApiException
import com.perpenda.data.repository.AuthRepository
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
    val justAuthenticated: Boolean = false
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
                val session = when (mode) {
                    AuthMode.Login -> authRepository.login(email, password)
                    AuthMode.Signup -> authRepository.signup(email, password, displayName)
                }
                uiState.copy(
                    isSubmitting = false,
                    signedInUser = session.user,
                    justAuthenticated = true,
                    errorMessage = null,
                    password = ""
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

    fun acknowledgeNavigation() {
        if (uiState.justAuthenticated) {
            uiState = uiState.copy(justAuthenticated = false)
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
            else -> error.message.ifBlank { "Something went wrong. Please try again." }
        }
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MIN_DISPLAY_NAME_LENGTH = 2

        fun factory(authRepository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(authRepository) as T
                }
            }
    }
}
