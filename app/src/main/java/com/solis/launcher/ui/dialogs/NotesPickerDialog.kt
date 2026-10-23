package com.solis.launcher.ui.dialogs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solis.launcher.domain.AppEntry
import com.solis.launcher.ui.theme.SolisColors
import com.solis.launcher.ui.theme.TextAction

/** Shown once when no notes app can be resolved automatically. The choice is remembered. */
@Composable
fun NotesPickerDialog(
    apps: List<AppEntry>,
    onPick: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    SolisFullScreenDialog(onDismiss = onDismiss) {
        Text("which app holds your notes?", style = MaterialTheme.typography.titleLarge, color = SolisColors.White)
        Text("asked once. change it later in settings.", style = MaterialTheme.typography.bodySmall, color = SolisColors.Accent)
        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(apps, key = { it.key }) { app ->
                TextAction(app.label.lowercase(), onClick = { onPick(app) }, modifier = Modifier.fillMaxWidth())
            }
        }
        TextAction("cancel", onDismiss, color = SolisColors.Accent, style = MaterialTheme.typography.labelLarge)
    }
}
