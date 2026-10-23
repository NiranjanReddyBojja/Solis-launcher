package com.solis.launcher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.solis.launcher.ui.theme.SolisColors

/**
 * Full-screen black dialog. Solis dialogs replace the whole view on purpose: one thing at a time.
 */
@Composable
fun SolisFullScreenDialog(
    onDismiss: () -> Unit,
    dismissOnBack: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissOnBack,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SolisColors.Black)
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            content = content,
        )
    }
}
