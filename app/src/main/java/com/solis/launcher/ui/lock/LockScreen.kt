package com.solis.launcher.ui.lock

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solis.launcher.data.QuoteBank
import com.solis.launcher.domain.LauncherPreferences
import com.solis.launcher.domain.QuoteRotation
import com.solis.launcher.ui.theme.SolisColors
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Distance a swipe up must travel before it unlocks. */
private val UNLOCK_THRESHOLD = 96.dp

/**
 * Clock, date, today's dodged impulses and the current quote, on pure black. Swipe up anywhere
 * (or tap the hint, for switch and screen-reader users) to unlock.
 */
@Composable
fun LockScreen(
    prefs: LauncherPreferences,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            // Only minutes are shown, so wake once per minute boundary.
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val context = LocalContext.current
    val is24h = DateFormat.is24HourFormat(context)
    val timeFormatter = remember(is24h) {
        DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm", Locale.getDefault())
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()) }

    val dodged = if (prefs.dodgedDate == now.toLocalDate().toString()) prefs.dodgedCount else 0
    val quote = QuoteRotation.quoteAt(QuoteRotation.activeBank(prefs, QuoteBank.defaults), prefs.quoteIndex)

    val thresholdPx = with(LocalDensity.current) { UNLOCK_THRESHOLD.toPx() }
    val currentOnUnlock by rememberUpdatedState(onUnlock)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(thresholdPx) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        total += dragAmount
                        change.consume()
                    },
                    onDragEnd = { if (total < -thresholdPx) currentOnUnlock() },
                )
            }
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(48.dp))
            Text(
                text = now.format(timeFormatter),
                style = MaterialTheme.typography.displayLarge,
                color = SolisColors.White,
            )
            Text(
                text = now.format(dateFormatter).lowercase(),
                style = MaterialTheme.typography.titleSmall,
                color = SolisColors.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "[$dodged ${if (dodged == 1) "impulse" else "impulses"} dodged today]",
                style = MaterialTheme.typography.labelMedium,
                color = SolisColors.Accent,
            )

            Spacer(Modifier.weight(1f))
            Text(
                text = quote,
                style = MaterialTheme.typography.titleLarge,
                color = SolisColors.White,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))

            Text(
                text = "▲ swipe up to unlock",
                style = MaterialTheme.typography.labelSmall,
                color = SolisColors.Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onUnlock)
                    .padding(vertical = 12.dp),
            )
        }
    }
}
