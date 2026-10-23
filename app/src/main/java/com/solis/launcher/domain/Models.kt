package com.solis.launcher.domain

/** A launchable activity, reduced to what a text-only launcher needs. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
) {
    /** Stable key for Compose lists; one package can expose several launcher activities. */
    val key: String get() = "$packageName/$activityName"
}

/** Lockout window during which the focus drawer is closed (Pro). Minutes since midnight. */
data class CurfewConfig(
    val enabled: Boolean = false,
    val startMinutes: Int = 22 * 60,
    val endMinutes: Int = 7 * 60,
)

/** Everything persisted in DataStore, emitted as one snapshot. */
data class LauncherPreferences(
    val isPro: Boolean = false,
    val dodgedToday: Int = 0,
    /** Raw counter and its ISO date, so the UI can zero the metric at midnight without a write. */
    val dodgedCount: Int = 0,
    val dodgedDate: String? = null,
    val dodgedTotal: Int = 0,
    val hiddenPackages: Set<String> = emptySet(),
    val distractionPackages: Set<String> = emptySet(),
    val launchDelaySeconds: Int = DEFAULT_LAUNCH_DELAY_SECONDS,
    val customQuotes: List<String> = emptyList(),
    val useCustomQuotes: Boolean = false,
    val quoteIndex: Int = 0,
    val quoteRotatedAtMillis: Long = 0L,
    val curfew: CurfewConfig = CurfewConfig(),
    val lockscreenQuoteSync: Boolean = false,
    /** Show the Solis lock screen over the system keyguard whenever the screen goes off. */
    val solisLockScreen: Boolean = false,
    val notesPackage: String? = null,
    val launcherSetupDismissed: Boolean = false,
    val accessibilityDisclosureAccepted: Boolean = false,
) {
    companion object {
        const val MIN_LAUNCH_DELAY_SECONDS = 5
        const val MAX_LAUNCH_DELAY_SECONDS = 30
        const val DEFAULT_LAUNCH_DELAY_SECONDS = 10
    }
}
