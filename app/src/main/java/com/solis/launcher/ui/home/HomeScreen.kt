package com.solis.launcher.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.solis.launcher.system.Essential
import com.solis.launcher.ui.LauncherAction
import com.solis.launcher.ui.LauncherUiState
import com.solis.launcher.ui.theme.SolisColors
import com.solis.launcher.ui.theme.SolisOutlinedButton
import com.solis.launcher.ui.theme.TextAction
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PINNED = listOf(
    Essential.PHONE,
    Essential.MESSAGES,
    Essential.NOTES,
    Essential.CALENDAR,
    Essential.CAMERA,
)

/** Distance a vertical fling must travel before it counts as a swipe. */
private val SWIPE_THRESHOLD = 72.dp

/**
 * The root screen: clock, date, dodged metric, one quote, five essentials, and the impulse
 * button. Swiping up anywhere opens the focus drawer; swiping down pulls the notification shade.
 */
@Composable
fun HomeScreen(
    state: LauncherUiState,
    onAction: (LauncherAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    val currentOnAction by rememberUpdatedState(onAction)

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
                    onDragEnd = {
                        when {
                            total < -thresholdPx -> currentOnAction(LauncherAction.OpenDrawer)
                            total > thresholdPx -> currentOnAction(LauncherAction.SwipeDown)
                        }
                    },
                )
            }
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(state, onAction)

            if (state.showLauncherSetup) {
                Spacer(Modifier.height(28.dp))
                LauncherSetupCard(onAction)
            }

            Spacer(Modifier.weight(1f))
            QuoteBlock(quote = state.quote, onTap = { onAction(LauncherAction.NextQuote) })
            Spacer(Modifier.weight(1f))

            PinnedEssentials(onAction)
            Spacer(Modifier.height(28.dp))

            SolisOutlinedButton(
                text = "[ ⚡ INTERRUPT IMPULSE ]",
                onClick = { onAction(LauncherAction.OpenImpulseInterceptor) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "▲ swipe up for apps  •  ▼ swipe down for shade",
                style = MaterialTheme.typography.labelSmall,
                color = SolisColors.Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopBar(state: LauncherUiState, onAction: (LauncherAction) -> Unit) {
    val context = LocalContext.current
    val is24h = DateFormat.is24HourFormat(context)
    val timeFormatter = remember(is24h) {
        DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm", Locale.getDefault())
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()) }
    val noRipple = remember { MutableInteractionSource() }

    Column {
        // Tap: alarms. Long-press: Solis settings (kept hidden so the home stays bare).
        Text(
            text = state.now.format(timeFormatter),
            style = MaterialTheme.typography.displayLarge,
            color = SolisColors.White,
            modifier = Modifier.combinedClickable(
                interactionSource = noRipple,
                indication = null,
                onClick = { onAction(LauncherAction.LaunchEssential(Essential.ALARM)) },
                onLongClick = { onAction(LauncherAction.OpenSettings) },
            ),
        )
        Text(
            text = state.now.format(dateFormatter).lowercase(),
            style = MaterialTheme.typography.titleSmall,
            color = SolisColors.White,
            modifier = Modifier.combinedClickable(
                interactionSource = noRipple,
                indication = null,
                onClick = { onAction(LauncherAction.LaunchEssential(Essential.CALENDAR)) },
            ),
        )
        Spacer(Modifier.height(8.dp))
        val n = state.prefs.dodgedToday
        Text(
            text = "[$n ${if (n == 1) "impulse" else "impulses"} dodged today]",
            style = MaterialTheme.typography.labelMedium,
            color = SolisColors.Accent,
        )
    }
}

@Composable
private fun LauncherSetupCard(onAction: (LauncherAction) -> Unit) {
    Column {
        Text(
            text = "solis is not your home app yet.\nmake it the first thing you see.",
            style = MaterialTheme.typography.bodyMedium,
            color = SolisColors.White,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SolisOutlinedButton(
                text = "[ set as default ]",
                onClick = { onAction(LauncherAction.RequestDefaultLauncher) },
            )
            Spacer(Modifier.weight(1f))
            TextAction(
                text = "not now",
                onClick = { onAction(LauncherAction.DismissLauncherSetup) },
                color = SolisColors.Accent,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuoteBlock(quote: String, onTap: () -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    Text(
        text = quote,
        style = MaterialTheme.typography.titleLarge,
        color = SolisColors.White,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(interactionSource = noRipple, indication = null, onClick = onTap),
    )
}

@Composable
private fun PinnedEssentials(onAction: (LauncherAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        PINNED.forEach { essential ->
            TextAction(
                text = essential.label,
                onClick = { onAction(LauncherAction.LaunchEssential(essential)) },
            )
        }
    }
}
