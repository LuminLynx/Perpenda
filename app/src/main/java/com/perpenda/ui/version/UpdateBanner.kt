package com.perpenda.ui.version

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.perpenda.ui.theme.PerpendaTheme

@Composable
fun UpdateBanner(viewModel: UpdateBannerViewModel) {
    val state by viewModel.state.collectAsState()
    val available = state as? UpdateBannerState.Available ?: return

    val context = LocalContext.current
    val colors = PerpendaTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bannerTint)
            .border(1.dp, colors.hairline, RoundedCornerShape(2.dp))
            .clickable {
                // No browser installed (or a malformed downloadUrl in
                // latest.json) must not crash the app.
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(available.downloadUrl))
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Update available — v${available.versionName}. Tap to download.",
            color = colors.onBannerTint,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { viewModel.dismiss() },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss update banner",
                tint = colors.onBannerTint
            )
        }
    }
}
