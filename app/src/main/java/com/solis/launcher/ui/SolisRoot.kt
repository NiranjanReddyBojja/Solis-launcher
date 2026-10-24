package com.solis.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.solis.launcher.ui.away.AwayScreen
import com.solis.launcher.ui.dialogs.AccessibilityDisclosureDialog
import com.solis.launcher.ui.dialogs.AppActionsSheet
import com.solis.launcher.ui.dialogs.ImpulseInterceptorDialog
import com.solis.launcher.ui.dialogs.LaunchCountdownDialog
import com.solis.launcher.ui.dialogs.NotesPickerDialog
import com.solis.launcher.ui.drawer.FocusDrawer
import com.solis.launcher.ui.home.HomeScreen
import com.solis.launcher.ui.moment.MomentScreen
import com.solis.launcher.ui.settings.SettingsScreen
import com.solis.launcher.ui.theme.SolisColors

/** Renders the current screen and, on top of it, at most one dialog. */
@Composable
fun SolisRoot(state: LauncherUiState, onAction: (LauncherAction) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(SolisColors.Black)) {
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = {
                if (targetState == Screen.DRAWER) {
                    (slideInVertically { it / 3 } + fadeIn()) togetherWith fadeOut()
                } else {
                    fadeIn() togetherWith (slideOutVertically { it / 3 } + fadeOut())
                }
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.HOME -> HomeScreen(state, onAction)
                Screen.DRAWER -> FocusDrawer(state, onAction)
                Screen.SETTINGS -> SettingsScreen(state, onAction)
                Screen.MOMENT -> MomentScreen(state, onAction)
                Screen.AWAY -> AwayScreen(state, onAction)
            }
        }

        val dismiss = { onAction(LauncherAction.DismissDialog) }
        when (val dialog = state.dialog) {
            null -> Unit
            LauncherDialog.ImpulseInterceptor -> ImpulseInterceptorDialog(
                accessibilityEnabled = state.accessibilityEnabled,
                onBurn = { onAction(LauncherAction.BurnImpulse) },
                onDismiss = dismiss,
            )
            is LauncherDialog.AccessibilityDisclosure -> AccessibilityDisclosureDialog(
                purpose = dialog.purpose,
                onAccept = { onAction(LauncherAction.AcceptAccessibilityDisclosure) },
                onDismiss = dismiss,
            )
            LauncherDialog.NotesPicker -> NotesPickerDialog(
                apps = state.allApps,
                onPick = { onAction(LauncherAction.PickNotesApp(it)) },
                onDismiss = dismiss,
            )
            is LauncherDialog.AppActions -> AppActionsSheet(
                app = dialog.app,
                isHidden = dialog.app.packageName in state.prefs.hiddenPackages,
                isDistraction = dialog.app.packageName in state.prefs.distractionPackages,
                launchDelaySeconds = state.prefs.launchDelaySeconds,
                onAppInfo = { onAction(LauncherAction.OpenAppInfo(dialog.app)) },
                onUninstall = { onAction(LauncherAction.Uninstall(dialog.app)) },
                onToggleHidden = { onAction(LauncherAction.ToggleHidden(dialog.app)) },
                onToggleDistraction = { onAction(LauncherAction.ToggleDistraction(dialog.app)) },
                onDismiss = dismiss,
            )
            is LauncherDialog.LaunchCountdown -> LaunchCountdownDialog(
                app = dialog.app,
                totalSeconds = dialog.totalSeconds,
                remainingSeconds = dialog.remainingSeconds,
                awayMillis = state.awayFrom(dialog.app.packageName),
                onCancel = { onAction(LauncherAction.CancelCountdown) },
            )
        }
    }
}
