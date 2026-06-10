package com.perpenda.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.perpenda.data.repository.AuthRepository
import com.perpenda.ui.components.PrimaryActionButton
import com.perpenda.ui.components.TertiaryActionButton
import com.perpenda.viewmodel.AuthMode
import com.perpenda.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    initialMode: AuthMode,
    authRepository: AuthRepository,
    onBack: () -> Unit,
    onAuthenticated: () -> Unit,
    onNotice: (String) -> Unit = {}
) {
    val viewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(authRepository)
    )
    val uiState = viewModel.uiState

    LaunchedEffect(initialMode) {
        viewModel.setMode(initialMode)
    }

    LaunchedEffect(uiState.justAuthenticated) {
        if (uiState.justAuthenticated) {
            // Verify/reset end with a silent jump into the app; the
            // centered notice (drawn by AppNav, above the NavHost, so it
            // survives the navigation) confirms the action landed. Plain
            // login/signup stays quiet (postAuthNotice is null there).
            uiState.postAuthNotice?.let(onNotice)
            viewModel.acknowledgeNavigation()
            onAuthenticated()
        }
    }

    val verifyingEmail = uiState.pendingVerificationEmail
    val resettingEmail = uiState.pendingResetEmail
    val screenTitle = when {
        resettingEmail != null -> "Reset password"
        verifyingEmail != null -> "Confirm your email"
        uiState.mode == AuthMode.Login -> "Sign in"
        else -> "Create account"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (verifyingEmail != null || resettingEmail != null) {
                                viewModel.cancelVerification()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (resettingEmail != null) {
            ResetPasswordStep(
                email = resettingEmail,
                code = uiState.verificationCode,
                newPassword = uiState.password,
                isSubmitting = uiState.isSubmitting,
                errorMessage = uiState.errorMessage,
                infoMessage = uiState.infoMessage,
                onCodeChanged = viewModel::onVerificationCodeChanged,
                onPasswordChanged = viewModel::onPasswordChanged,
                onSubmit = viewModel::submitPasswordReset,
                onResend = viewModel::resendResetCode,
                onCancel = viewModel::cancelVerification,
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            )
            return@Scaffold
        }
        if (verifyingEmail != null) {
            VerificationStep(
                email = verifyingEmail,
                code = uiState.verificationCode,
                isSubmitting = uiState.isSubmitting,
                errorMessage = uiState.errorMessage,
                infoMessage = uiState.infoMessage,
                onCodeChanged = viewModel::onVerificationCodeChanged,
                onSubmit = viewModel::submitVerificationCode,
                onResend = viewModel::resendVerificationCode,
                onCancel = viewModel::cancelVerification,
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState())
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (uiState.mode == AuthMode.Login) {
                    "Sign in to attribute your contributions and unlock scoring."
                } else {
                    "Create an account to track your contributions and earn scores as you learn."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (uiState.mode == AuthMode.Signup) {
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = viewModel::onDisplayNameChanged,
                    label = { Text("Display name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChanged,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None
                ),
                modifier = Modifier.fillMaxWidth()
            )

            var passwordVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChanged,
                label = { Text("Password") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                supportingText = {
                    if (uiState.mode == AuthMode.Signup) {
                        Text(
                            text = "At least 8 characters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            PrimaryActionButton(
                text = when {
                    uiState.isSubmitting && uiState.mode == AuthMode.Login -> "Signing in..."
                    uiState.isSubmitting -> "Creating account..."
                    uiState.mode == AuthMode.Login -> "Sign in"
                    else -> "Create account"
                },
                onClick = viewModel::submit,
                enabled = !uiState.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )

            TertiaryActionButton(
                text = if (uiState.mode == AuthMode.Login) {
                    "Don't have an account? Sign up"
                } else {
                    "Already have an account? Sign in"
                },
                onClick = {
                    viewModel.setMode(
                        if (uiState.mode == AuthMode.Login) AuthMode.Signup else AuthMode.Login
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.mode == AuthMode.Login) {
                TertiaryActionButton(
                    text = "Forgot password?",
                    onClick = viewModel::startPasswordReset,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ResetPasswordStep(
    email: String,
    code: String,
    newPassword: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onCodeChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Enter the 6-digit code we sent to $email and choose a new password. The code expires in 15 minutes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = code,
            onValueChange = onCodeChanged,
            label = { Text("Reset code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = newPassword,
            onValueChange = onPasswordChanged,
            label = { Text("New password") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            supportingText = {
                Text(
                    text = "At least 8 characters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (infoMessage != null) {
            Text(
                text = infoMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        PrimaryActionButton(
            text = if (isSubmitting) "Resetting..." else "Set new password",
            onClick = onSubmit,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        )

        TertiaryActionButton(
            text = "Resend code",
            onClick = onResend,
            modifier = Modifier.fillMaxWidth()
        )

        TertiaryActionButton(
            text = "Back to sign in",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun VerificationStep(
    email: String,
    code: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Enter the 6-digit code we sent to $email. It expires in 15 minutes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = code,
            onValueChange = onCodeChanged,
            label = { Text("Verification code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )

        if (infoMessage != null) {
            Text(
                text = infoMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        PrimaryActionButton(
            text = if (isSubmitting) "Verifying..." else "Verify",
            onClick = onSubmit,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        )

        TertiaryActionButton(
            text = "Resend code",
            onClick = onResend,
            modifier = Modifier.fillMaxWidth()
        )

        TertiaryActionButton(
            text = "Use a different account",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
