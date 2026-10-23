package com.solis.launcher.ui.dialogs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.solis.launcher.domain.AppEntry
import com.solis.launcher.ui.theme.SolisColors
import com.solis.launcher.ui.theme.SolisOutlinedButton

/** Pro: the forced pause before a flagged distraction app opens. Backing out counts as a dodge. */
@Composable
fun LaunchCountdownDialog(
    app: AppEntry,
    totalSeconds: Int,
    remainingSeconds: Int,
    onCancel: () -> Unit,
) {
    SolisFullScreenDialog(onDismiss = onCancel) {
        Spacer(Modifier.weight(1f))
        Text("opening ${app.label.lowercase()} in", style = MaterialTheme.typography.bodyLarge, color = SolisColors.Accent)
        Text("$remainingSeconds", style = MaterialTheme.typography.displayLarge, color = SolisColors.White)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { 1f - remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
            color = SolisColors.White,
            trackColor = SolisColors.Dim,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(24.dp))
        Text("is this a choice, or a reflex?", style = MaterialTheme.typography.titleMedium, color = SolisColors.White)
        Spacer(Modifier.weight(1f))
        SolisOutlinedButton("[ never mind ]", onCancel, modifier = Modifier.fillMaxWidth())
    }
}
