package com.solis.launcher.ui.dialogs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solis.launcher.ui.theme.SolisColors
import com.solis.launcher.ui.theme.SolisOutlinedButton
import com.solis.launcher.ui.theme.TextAction

private val PRO_FEATURES = listOf(
    "app vault" to "hide banking and utilities from the carousel",
    "curfew" to "lock the app drawer between hours you choose",
    "your own truths" to "write your quotes, mirror them to the lock screen",
    "launch delay" to "make distraction apps wait 5 to 30 seconds",
)

/**
 * Calm, single-screen paywall. No timers, no fake discounts.
 *
 * @param price localized price from Google Play; falls back to the list price until it loads.
 * @param message result of the last purchase or restore attempt, if any.
 */
@Composable
fun ProPaywallDialog(
    price: String?,
    message: String?,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val displayPrice = price ?: "$4.99"
    SolisFullScreenDialog(onDismiss = onDismiss) {
        Text("solis pro", style = MaterialTheme.typography.headlineMedium, color = SolisColors.White)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No subscriptions. $displayPrice once, yours forever.",
            style = MaterialTheme.typography.titleMedium,
            color = SolisColors.White,
        )
        Spacer(Modifier.height(32.dp))
        PRO_FEATURES.forEach { (name, detail) ->
            Text("— $name", style = MaterialTheme.typography.bodyLarge, color = SolisColors.White)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = SolisColors.Accent)
            Spacer(Modifier.height(14.dp))
        }
        Spacer(Modifier.weight(1f))
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = SolisColors.White)
            Spacer(Modifier.height(12.dp))
        }
        SolisOutlinedButton(
            text = "[ unlock for $displayPrice ]",
            onClick = onPurchase,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        TextAction("restore purchase", onRestore, color = SolisColors.White, style = MaterialTheme.typography.labelLarge)
        TextAction("not now", onDismiss, color = SolisColors.Accent, style = MaterialTheme.typography.labelLarge)
    }
}
