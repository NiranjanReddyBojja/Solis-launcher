package com.solis.launcher.ui

import android.content.Intent
import com.solis.launcher.domain.AppEntry
import com.solis.launcher.domain.AppSection
import com.solis.launcher.domain.AwayStat
import com.solis.launcher.domain.AwayTime
import com.solis.launcher.domain.CurfewConfig
import com.solis.launcher.domain.LauncherPreferences
import com.solis.launcher.system.Essential
import java.time.LocalDateTime

// MVI contract: the UI renders [LauncherUiState], sends [LauncherAction]s and performs
// one-shot [LauncherEffect]s that need an Activity.

enum class Screen { HOME, DRAWER, SETTINGS, MOMENT, AWAY }

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
    /** Every visible app, A to Z, grouped by first letter. */
    val appSections: List<AppSection> = emptyList(),
    val visibleAppCount: Int = 0,
    val vaultApps: List<AppEntry> = emptyList(),
    val vaultOpen: Boolean = false,
    val curfewLocked: Boolean = false,
    /** Seconds left in the running focus session, 0 when none. */
    val focusRemainingSeconds: Long = 0L,
    val isDefaultLauncher: Boolean = true,
    val accessibilityEnabled: Boolean = false,
    val usageAccessGranted: Boolean = false,
    /** Wall-clock time of [now], for streak arithmetic. */
    val nowMillis: Long = System.currentTimeMillis(),
    /** When each flagged app was last on screen, by package name. Empty without usage access. */
    val lastSeenMillis: Map<String, Long> = emptyMap(),
) {
    val focusActive: Boolean get() = focusRemainingSeconds > 0
    val drawerLocked: Boolean get() = curfewLocked || focusActive
    val showLauncherSetup: Boolean get() = !isDefaultLauncher && !prefs.launcherSetupDismissed

    /** Time away from each flagged app, shortest streak first. */
    val awayStats: List<AwayStat>
        get() = AwayTime.stats(prefs.distractionPackages, allApps, lastSeenMillis, prefs.awayBestMillis, nowMillis)

    fun awayFrom(packageName: String): Long? =
        lastSeenMillis[packageName]?.let { AwayTime.awayMillis(it, nowMillis) }
}

sealed interface LauncherAction {
    // Navigation
    data object OpenDrawer : LauncherAction
    data object OpenSettings : LauncherAction
    data object HomePressed : LauncherAction
    data object BackPressed : LauncherAction
    data object SwipeDown : LauncherAction
    data object Resumed : LauncherAction
    data object OpenMoment : LauncherAction
    data object OpenAway : LauncherAction

    // Home
    data class LaunchEssential(val essential: Essential) : LauncherAction
    data object NextQuote : LauncherAction
    data object OpenImpulseInterceptor : LauncherAction
    data object BurnImpulse : LauncherAction
    data object RequestDefaultLauncher : LauncherAction
    data object HomeRoleRequestFinished : LauncherAction
    data object DismissLauncherSetup : LauncherAction

    // Drawer
    data object ToggleVault : LauncherAction
    data class LaunchApp(val app: AppEntry) : LauncherAction
    data class LongPressApp(val app: AppEntry) : LauncherAction
    data class OpenAppInfo(val app: AppEntry) : LauncherAction
    data class Uninstall(val app: AppEntry) : LauncherAction
    data class ToggleHidden(val app: AppEntry) : LauncherAction
    data class ToggleDistraction(val app: AppEntry) : LauncherAction
    data object CancelCountdown : LauncherAction
    data class PickNotesApp(val app: AppEntry) : LauncherAction

    // Self-care and time away
    data class SaveReflection(val prompt: String, val text: String) : LauncherAction
    data class DeleteReflection(val atMillis: Long) : LauncherAction
    data object OpenUsageAccess : LauncherAction

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
