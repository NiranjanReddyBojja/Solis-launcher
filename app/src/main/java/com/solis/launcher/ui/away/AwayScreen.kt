package com.solis.launcher.ui.away

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.solis.launcher.domain.AwayStat
import com.solis.launcher.domain.AwayTime
import com.solis.launcher.ui.LauncherAction
import com.solis.launcher.ui.LauncherUiState
import com.solis.launcher.ui.theme.SolisColors
import com.solis.launcher.ui.theme.SolisOutlinedButton
import com.solis.launcher.ui.theme.TextAction

/** How long the user has stayed away from each app they flagged as a distraction. */
@Composable
fun AwayScreen(
    state: LauncherUiState,
    onAction: (LauncherAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = state.awayStats

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("time away.", style = MaterialTheme.typography.headlineMedium, color = SolisColors.White)
                Spacer(Modifier.weight(1f))
                TextAction("× close", { onAction(LauncherAction.BackPressed) }, color = SolisColors.Accent,
                    style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "every hour away is an hour back.",
                style = MaterialTheme.typography.labelMedium,
                color = SolisColors.Accent,
            )
            Spacer(Modifier.height(28.dp))
        }

        when {
            state.prefs.distractionPackages.isEmpty() -> item {
                Explainer(
                    "no apps flagged yet.\n\nlong-press the app that pulls at you in the app list and " +
                        "choose \"delay launch\". solis will count how long you stay away from it.",
                )
            }
            !state.usageAccessGranted -> item {
                Explainer(
                    "to see how long you've stayed away, solis needs usage access. it only reads " +
                        "when your flagged apps were last on screen, and it never leaves this phone.",
                )
                Spacer(Modifier.height(20.dp))
                SolisOutlinedButton(
                    "[ allow usage access ]",
                    { onAction(LauncherAction.OpenUsageAccess) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            stats.isEmpty() -> item { Explainer("counting…") }
            else -> items(stats, key = { it.app.packageName }) { StatBlock(it) }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
}

@Composable
private fun StatBlock(stat: AwayStat) {
    val away = stat.awayMillis
    val next = AwayTime.nextMilestone(away)
    val previous = AwayTime.MILESTONES.lastOrNull { it <= away } ?: 0L

    Text(stat.app.label.lowercase(), style = MaterialTheme.typography.labelLarge, color = SolisColors.Accent)
    Text(AwayTime.format(away), style = MaterialTheme.typography.displaySmall, color = SolisColors.White)
    Text(AwayTime.encouragement(away), style = MaterialTheme.typography.bodyMedium, color = SolisColors.White)
    Spacer(Modifier.height(12.dp))
    if (next != null) {
        LinearProgressIndicator(
            progress = { ((away - previous).toFloat() / (next - previous)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = SolisColors.White,
            trackColor = SolisColors.Dim,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "next: ${AwayTime.format(next)} in ${AwayTime.format(next - away)}",
            style = MaterialTheme.typography.labelSmall,
            color = SolisColors.Accent,
        )
    }
    Text(
        if (stat.bestMillis > away) "your best: ${AwayTime.format(stat.bestMillis)}" else "this is your best yet.",
        style = MaterialTheme.typography.labelSmall,
        color = SolisColors.Accent,
    )
    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = SolisColors.Dim, thickness = 1.dp)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun Explainer(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = SolisColors.Accent)
}
