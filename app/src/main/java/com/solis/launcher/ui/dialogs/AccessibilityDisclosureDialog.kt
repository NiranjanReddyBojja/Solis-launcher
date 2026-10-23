package com.solis.launcher.ui.dialogs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solis.launcher.ui.AccessibilityPurpose
import com.solis.launcher.ui.theme.SolisColors
import com.solis.launcher.ui.theme.SolisOutlinedButton
import com.solis.launcher.ui.theme.TextAction

/**
 * Prominent disclosure shown before sending the user to accessibility settings, as required by
 * Google Play's AccessibilityService policy. States exactly what the service does and does not do.
 */
@Composable
fun AccessibilityDisclosureDialog(
    purpose: AccessibilityPurpose,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    SolisFullScreenDialog(onDismiss = onDismiss) {
        Text("one permission.", style = MaterialTheme.typography.headlineSmall, color = SolisColors.White)
        Spacer(Modifier.height(16.dp))
        Text(
            text = when (purpose) {
                AccessibilityPurpose.LOCK_SCREEN ->
                    "To put your phone to sleep after you burn an impulse, Android requires Solis to use an accessibility service."
                AccessibilityPurpose.NOTIFICATION_SHADE ->
                    "To pull down notifications when you swipe down, Android requires Solis to use an accessibility service."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = SolisColors.White,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Solis Focus only does two things, and only when you ask: lock the screen and " +
                "open the notification shade.\n\nIt cannot see, read or record what is on your screen. " +
                "Nothing is collected or shared.",
            style = MaterialTheme.typography.bodyMedium,
            color = SolisColors.Accent,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "In the next screen: Installed apps → Solis Focus → On.",
            style = MaterialTheme.typography.bodyMedium,
            color = SolisColors.White,
        )
        Spacer(Modifier.weight(1f))
        SolisOutlinedButton(
            text = "[ open accessibility settings ]",
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        TextAction("not now", onDismiss, color = SolisColors.Accent, style = MaterialTheme.typography.labelLarge)
    }
}
