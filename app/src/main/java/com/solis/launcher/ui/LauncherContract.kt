package com.solis.launcher.ui

import android.content.Intent
import com.solis.launcher.domain.AppEntry
import com.solis.launcher.domain.CurfewConfig
import com.solis.launcher.domain.LauncherPreferences
import com.solis.launcher.system.Essential
import java.time.LocalDateTime

// MVI contract: the UI renders [LauncherUiState], sends [LauncherAction]s and performs
// one-shot [LauncherEffect]s that need an Activity.

enum class Screen { HOME, DRAWER, SETTINGS }

/** Why the accessibility disclosure is being shown, so we can explain the right thing. */
enum class AccessibilityPurpose { LOCK_SCREEN, NOTIFICATION_SHADE }

sealed interface LauncherDialog {
    data object ImpulseInterceptor : LauncherDialog
    data class AccessibilityDisclosure(val purpose: AccessibilityPurpose) : LauncherDialog
    data object NotesPicker : LauncherDialog
    data class AppActions(val app: AppEntry) : LauncherDialog
    data class LaunchCountdown(val app: AppEntry, val totalSeconds: Int, val remainingSeconds: Int) :
        LauncherDialog
}

data class LauncherUiState(
    val screen: Screen = Screen.HOME,
    val dialog: LauncherDialog? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    val quote: String = "",
    val prefs: LauncherPreferences = LauncherPreferences(),
    val allApps: List<AppEntry> = emptyList(),
    val carouselApps: List<AppEntry> = emptyList(),
    val searchResults: List<AppEntry> = emptyList(),
    val query: String = "",
    val curfewLocked: Boolean = false,
    /** Seconds left in the running focus session, 0 when none. */
    val focusRemainingSeconds: Long = 0L,
    val isDefaultLauncher: Boolean = true,
    val accessibilityEnabled: Boolean = false,
) {
    val focusActive: Boolean get() = focusRemainingSeconds > 0
    val drawerLocked: Boolean get() = curfewLocked || focusActive
    val showLauncherSetup: Boolean get() = !isDefaultLauncher && !prefs.launcherSetupDismissed
}

sealed interface LauncherAction {
    // Navigation
    data object OpenDrawer : LauncherAction
    data object OpenSettings : LauncherAction
    data object HomePressed : LauncherAction
    data object BackPressed : LauncherAction
    data object SwipeDown : LauncherAction
    data object Resumed : LauncherAction

    // Home
    data class LaunchEssential(val essential: Essential) : LauncherAction
    data object NextQuote : LauncherAction
    data object OpenImpulseInterceptor : LauncherAction
    data object BurnImpulse : LauncherAction
    data object RequestDefaultLauncher : LauncherAction
    data object HomeRoleRequestFinished : LauncherAction
    data object DismissLauncherSetup : LauncherAction

    // Drawer
    data class QueryChanged(val query: String) : LauncherAction
    data class LaunchApp(val app: AppEntry) : LauncherAction
    data class LongPressApp(val app: AppEntry) : LauncherAction
    data class OpenAppInfo(val app: AppEntry) : LauncherAction
    data class Uninstall(val app: AppEntry) : LauncherAction
    data class ToggleHidden(val app: AppEntry) : LauncherAction
    data class ToggleDistraction(val app: AppEntry) : LauncherAction
    data object CancelCountdown : LauncherAction
    data class PickNotesApp(val app: AppEntry) : LauncherAction

    // Dialogs
    data object DismissDialog : LauncherAction
    data object AcceptAccessibilityDisclosure : LauncherAction

    // Settings
    data class SetCurfew(val config: CurfewConfig) : LauncherAction
    data class SetLaunchDelay(val seconds: Int) : LauncherAction
    data class AddQuote(val text: String) : LauncherAction
    data class RemoveQuote(val index: Int) : LauncherAction
    data class SetUseCustomQuotes(val enabled: Boolean) : LauncherAction
    data class SetLockscreenSync(val enabled: Boolean) : LauncherAction
    data object ResetNotesApp : LauncherAction
    data object OpenAccessibilitySettings : LauncherAction
    data class StartFocus(val minutes: Int) : LauncherAction
    data object EndFocus : LauncherAction
}

sealed interface LauncherEffect {
    data class StartActivity(val intent: Intent) : LauncherEffect
    data class RequestHomeRole(val intent: Intent) : LauncherEffect
    data class Toast(val text: String) : LauncherEffect
}
