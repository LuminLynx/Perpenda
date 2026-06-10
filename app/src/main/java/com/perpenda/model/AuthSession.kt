package com.perpenda.model

data class User(
    val id: String,
    val email: String,
    val displayName: String
)

data class AuthSession(
    val token: String,
    val user: User
)

/**
 * Signup now has two server outcomes: a ready session (legacy behavior,
 * EMAIL_VERIFICATION_REQUIRED off) or a created-but-unverified account
 * awaiting the emailed 6-digit code. The client must handle both — it
 * can't know which mode the backend is running.
 */
sealed interface SignupResult {
    data class Session(val session: AuthSession) : SignupResult
    data class VerificationRequired(val email: String) : SignupResult
}
