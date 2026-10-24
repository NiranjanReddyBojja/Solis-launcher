package com.solis.launcher.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.solis.launcher.domain.AppEntry
import com.solis.launcher.ui.theme.SolisColors
import com.solis.launcher.ui.theme.TextAction

/** Minimal long-press menu for a drawer entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionsSheet(
    app: AppEntry,
    isHidden: Boolean,
    isDistraction: Boolean,
    launchDelaySeconds: Int,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleDistraction: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = SolisColors.Black,
        contentColor = SolisColors.White,
        scrimColor = SolisColors.Black.copy(alpha = 0.7f),
        dragHandle = null,
    ) {
        HorizontalDivider(color = SolisColors.Accent, thickness = 1.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            Text(app.label.lowercase(), style = MaterialTheme.typography.labelMedium, color = SolisColors.Accent)
            Spacer(Modifier.height(8.dp))
            TextAction("app info", onAppInfo)
            TextAction("uninstall", onUninstall)
            TextAction(
                text = if (isHidden) "take out of the vault" else "move to the vault",
                onClick = onToggleHidden,
            )
            TextAction(
                text = if (isDistraction) "remove launch delay" else "delay launch ${launchDelaySeconds}s",
                onClick = onToggleDistraction,
            )
        }
    }
}
