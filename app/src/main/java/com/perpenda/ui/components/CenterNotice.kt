package com.perpenda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.perpenda.ui.theme.PerpendaTheme
import kotlinx.coroutines.delay

/**
 * A transient, screen-centered notice ("Account created", "Password
 * updated", ...). Exists because Android's Toast ignores setGravity on
 * API 30+ — a system toast can only render at the bottom, so centered
 * feedback has to be drawn by the app itself.
 *
 * Identity matters: two notices with the same text must still restart
 * the dismiss timer, hence the id.
 */
data class CenterNotice(val id: Long, val text: String) {
    companion object {
        fun of(text: String) = CenterNotice(id = System.nanoTime(), text = text)
    }
}

/** Renders [notice] centered over the host's content and auto-dismisses. */
@Composable
fun CenterNoticeOverlay(
    notice: CenterNotice?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (notice == null) return
    val colors = PerpendaTheme.colors

    LaunchedEffect(notice) {
        delay(2_500)
        onDismiss()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = notice.text,
            // Oxblood completion family (primaryContainer), not bannerTint:
            // the ochre banner reads as "review needed", the wrong semantic
            // for a success confirmation. DESIGN_BRIEF: completion is oxblood.
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(2.dp))
                .border(1.dp, colors.hairline, RoundedCornerShape(2.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp)
        )
    }
}
