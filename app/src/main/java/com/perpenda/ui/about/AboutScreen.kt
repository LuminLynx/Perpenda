package com.perpenda.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.perpenda.BuildConfig
import com.perpenda.R
import com.perpenda.data.repository.AuthRepository
import com.perpenda.ui.components.AppScreenScaffold
import com.perpenda.ui.components.SectionHeader
import com.perpenda.ui.components.screenContentPadding
import com.perpenda.ui.theme.PerpendaTheme
import kotlinx.coroutines.launch

private const val FEEDBACK_EMAIL = "hello@perpenda.com"
private const val TAGLINE =
    "AI-fluent enough to lead the decisions their teams now have to make"
private const val PRODUCT_DESCRIPTION =
    "The canonical curriculum: the LLM concepts a product manager actually has to " +
        "reason about — from tokenization and context windows to evals, RAG, and the " +
        "production trade-offs beyond — taught one trade-off at a time."

@Composable
fun AboutScreen(
    authRepository: AuthRepository,
    onAccountDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(authRepository.isLoggedIn()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    AppScreenScaffold(title = "About", subtitle = "Perpenda") { contentPadding ->
        Column(
            modifier = Modifier
                .screenContentPadding(contentPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_perpenda_mark),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Column {
                    Text(text = "Perpenda", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(text = TAGLINE, style = MaterialTheme.typography.titleMedium)

            Text(
                text = PRODUCT_DESCRIPTION,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionHeader(title = "Feedback")
            ActionCard(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.MailOutline,
                title = "Send feedback",
                subtitle = FEEDBACK_EMAIL,
                onClick = { sendFeedback(context) }
            )
            if (signedIn) {
                ActionCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.DeleteForever,
                    title = "Delete account",
                    subtitle = "Permanently delete your account and all data",
                    onClick = { showDeleteConfirm = true }
                )
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { if (!deleting) showDeleteConfirm = false },
                title = { Text("Delete account?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "This permanently deletes your account and all your data — " +
                                "completions, grades, and review schedule. This cannot be undone."
                        )
                        // A failed deletion must say so — silently leaving the
                        // dialog open reads as a hang, not a failure.
                        deleteError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !deleting,
                        onClick = {
                            deleting = true
                            deleteError = null
                            scope.launch {
                                try {
                                    authRepository.deleteAccount()
                                    signedIn = false
                                    showDeleteConfirm = false
                                    onAccountDeleted()
                                } catch (_: Exception) {
                                    // Account left intact; tell the user.
                                    deleteError =
                                        "Couldn't delete your account. Check your connection and try again."
                                } finally {
                                    deleting = false
                                }
                            }
                        }
                    ) { Text(if (deleting) "Deleting…" else "Delete") }
                },
                dismissButton = {
                    TextButton(
                        enabled = !deleting,
                        onClick = {
                            showDeleteConfirm = false
                            deleteError = null
                        }
                    ) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, PerpendaTheme.colors.hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun sendFeedback(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$FEEDBACK_EMAIL")
        putExtra(Intent.EXTRA_SUBJECT, "Perpenda feedback")
        putExtra(Intent.EXTRA_TEXT, "\n\n—\nPerpenda v${BuildConfig.VERSION_NAME}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // ACTION_SENDTO + mailto resolves only to email apps; fail quietly if the
    // device has none rather than crash on ActivityNotFoundException.
    runCatching { context.startActivity(intent) }
}
