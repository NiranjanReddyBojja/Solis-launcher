package com.solis.launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.solis.launcher.BuildConfig
import com.solis.launcher.domain.CurfewPolicy
import com.solis.launcher.domain.LauncherPreferences
import com.solis.launcher.ui.LauncherAction
import com.solis.launcher.ui.LauncherUiState
import com.solis.launcher.ui.theme.SolisColors
import com.solis.launcher.ui.theme.SolisOutlinedButton
import com.solis.launcher.ui.theme.TextAction

private const val CURFEW_STEP_MINUTES = 15
private const val DELAY_STEP_SECONDS = 5
private const val MINUTES_PER_DAY = 24 * 60

/**
 * Settings, reached by long-pressing the clock or from the drawer. Pro rows stay visible when
 * locked; touching one opens the paywall (the ViewModel enforces the gate).
 */
@Composable
fun SettingsScreen(
    state: LauncherUiState,
    onAction: (LauncherAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = state.prefs
    val proColor = if (state.isPro) SolisColors.White else SolisColors.Accent

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("settings", style = MaterialTheme.typography.headlineMedium, color = SolisColors.White)
                Spacer(Modifier.weight(1f))
                TextAction("× close", { onAction(LauncherAction.BackPressed) }, color = SolisColors.Accent,
                    style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "${prefs.dodgedTotal} impulses dodged, all time",
                style = MaterialTheme.typography.labelMedium,
                color = SolisColors.Accent,
            )
        }

        section("system")
        item {
            StatusRow(
                label = "default launcher",
                value = if (state.isDefaultLauncher) "yes" else "no",
                actionLabel = if (state.isDefaultLauncher) null else "set",
                onAction = { onAction(LauncherAction.RequestDefaultLauncher) },
            )
            StatusRow(
                label = "accessibility",
                value = if (state.accessibilityEnabled) "on" else "off",
                actionLabel = "open",
                onAction = { onAction(LauncherAction.OpenAccessibilitySettings) },
            )
            StatusRow(
                label = "notes app",
                value = prefs.notesPackage?.let { pkg ->
                    state.allApps.firstOrNull { it.packageName == pkg }?.label?.lowercase() ?: pkg
                } ?: "auto",
                actionLabel = if (prefs.notesPackage != null) "reset" else null,
                onAction = { onAction(LauncherAction.ResetNotesApp) },
            )
        }

        section("lock screen")
        item {
            Toggle("show solis when the phone wakes", prefs.solisLockScreen, SolisColors.White) {
                onAction(LauncherAction.SetSolisLockScreen(!prefs.solisLockScreen))
            }
            Text(
                "sits on top of your normal lock. swipe up, then your pin or fingerprint as usual. " +
                    "needs accessibility on to appear over other apps.",
                style = MaterialTheme.typography.bodySmall,
                color = SolisColors.Accent,
            )
        }

        section("solis pro")
        item {
            if (state.isPro) {
                Text("unlocked. thank you.", style = MaterialTheme.typography.bodyMedium, color = SolisColors.White)
            } else {
                SolisOutlinedButton(
                    text = "[ unlock for ${state.price ?: "$4.99"} ]",
                    onClick = { onAction(LauncherAction.OpenPaywall) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextAction("restore purchase", { onAction(LauncherAction.RestorePurchase) },
                    color = SolisColors.Accent, style = MaterialTheme.typography.labelLarge)
            }
        }

        section("curfew", pro = !state.isPro)
        item {
            val curfew = prefs.curfew
            Toggle("lock the drawer at night", curfew.enabled, proColor) {
                onAction(LauncherAction.SetCurfew(curfew.copy(enabled = !curfew.enabled)))
            }
            Stepper(
                label = "from",
                value = CurfewPolicy.format(curfew.startMinutes),
                color = proColor,
                onMinus = { onAction(LauncherAction.SetCurfew(curfew.copy(startMinutes = stepMinutes(curfew.startMinutes, -1)))) },
                onPlus = { onAction(LauncherAction.SetCurfew(curfew.copy(startMinutes = stepMinutes(curfew.startMinutes, +1)))) },
            )
            Stepper(
                label = "until",
                value = CurfewPolicy.format(curfew.endMinutes),
                color = proColor,
                onMinus = { onAction(LauncherAction.SetCurfew(curfew.copy(endMinutes = stepMinutes(curfew.endMinutes, -1)))) },
                onPlus = { onAction(LauncherAction.SetCurfew(curfew.copy(endMinutes = stepMinutes(curfew.endMinutes, +1)))) },
            )
        }

        section("launch delay", pro = !state.isPro)
        item {
            Stepper(
                label = "wait",
                value = "${prefs.launchDelaySeconds}s",
                color = proColor,
                onMinus = {
                    onAction(LauncherAction.SetLaunchDelay(prefs.launchDelaySeconds - DELAY_STEP_SECONDS))
                },
                onPlus = {
                    onAction(LauncherAction.SetLaunchDelay(prefs.launchDelaySeconds + DELAY_STEP_SECONDS))
                },
                minusEnabled = prefs.launchDelaySeconds > LauncherPreferences.MIN_LAUNCH_DELAY_SECONDS,
                plusEnabled = prefs.launchDelaySeconds < LauncherPreferences.MAX_LAUNCH_DELAY_SECONDS,
            )
            Text(
                "${prefs.distractionPackages.size} apps flagged. long-press an app in the drawer to flag it.",
                style = MaterialTheme.typography.bodySmall,
                color = SolisColors.Accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${prefs.hiddenPackages.size} apps in the vault.",
                style = MaterialTheme.typography.bodySmall,
                color = SolisColors.Accent,
            )
        }

        section("harsh truths", pro = !state.isPro)
        item {
            Toggle("use my own quotes", prefs.useCustomQuotes, proColor) {
                onAction(LauncherAction.SetUseCustomQuotes(!prefs.useCustomQuotes))
            }
            Toggle("mirror quote to lock screen", prefs.lockscreenQuoteSync, proColor) {
                onAction(LauncherAction.SetLockscreenSync(!prefs.lockscreenQuoteSync))
            }
            Spacer(Modifier.height(8.dp))
        }
        itemsIndexed(prefs.customQuotes) { index, quote ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(quote, style = MaterialTheme.typography.bodyMedium, color = SolisColors.White,
                    modifier = Modifier.weight(1f))
                Text(
                    "×",
                    style = MaterialTheme.typography.titleMedium,
                    color = SolisColors.Accent,
                    modifier = Modifier
                        .clickable { onAction(LauncherAction.RemoveQuote(index)) }
                        .padding(start = 12.dp),
                )
            }
        }
        item { QuoteComposer(proColor, state.isPro) { onAction(LauncherAction.AddQuote(it)) } }

        if (BuildConfig.DEBUG) {
            section("debug")
            item {
                Toggle("pro (debug builds only)", state.isPro, SolisColors.White) {
                    onAction(LauncherAction.DebugTogglePro)
                }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }
}

private fun stepMinutes(current: Int, direction: Int): Int =
    Math.floorMod(current + direction * CURFEW_STEP_MINUTES, MINUTES_PER_DAY)

private fun LazyListScope.section(title: String, pro: Boolean = false) {
    item(key = "section-$title") {
        Spacer(Modifier.height(28.dp))
        Text(
            text = title + if (pro) "  (pro)" else "",
            style = MaterialTheme.typography.labelMedium,
            color = SolisColors.Accent,
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = SolisColors.Dim, thickness = 1.dp)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun StatusRow(label: String, value: String, actionLabel: String?, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SolisColors.White)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = SolisColors.Accent)
        if (actionLabel != null) {
            TextAction("[ $actionLabel ]", onAction, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** Text checkbox: "[x] label". */
@Composable
private fun Toggle(label: String, checked: Boolean, color: androidx.compose.ui.graphics.Color, onToggle: () -> Unit) {
    Text(
        text = (if (checked) "[x] " else "[ ] ") + label,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
        TextAction("[ - ]", { if (minusEnabled) onMinus() },
            color = if (minusEnabled) color else SolisColors.Dim, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = color,
            modifier = Modifier.padding(horizontal = 12.dp))
        TextAction("[ + ]", { if (plusEnabled) onPlus() },
            color = if (plusEnabled) color else SolisColors.Dim, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun QuoteComposer(color: androidx.compose.ui.graphics.Color, isPro: Boolean, onAdd: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it.take(240) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = SolisColors.White),
            cursorBrush = SolidColor(SolisColors.White),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (draft.isEmpty()) {
                        Text("write a truth you need to hear…", style = MaterialTheme.typography.bodyMedium,
                            color = SolisColors.Accent)
                    }
                    inner()
                }
            },
        )
        HorizontalDivider(color = SolisColors.Accent, thickness = 1.dp)
        TextAction(
            "[ add quote ]",
            onClick = {
                onAdd(draft)
                // Without Pro the paywall opens instead; keep the draft for after unlocking.
                if (isPro) draft = ""
            },
            color = color,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
