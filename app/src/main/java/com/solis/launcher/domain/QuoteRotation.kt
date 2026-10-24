package com.solis.launcher.domain

/** Rotation rules for the home-screen quote. */
object QuoteRotation {

    const val ROTATION_INTERVAL_MILLIS: Long = 6L * 60 * 60 * 1000

    fun isDue(rotatedAtMillis: Long, nowMillis: Long): Boolean =
        rotatedAtMillis == 0L || nowMillis - rotatedAtMillis >= ROTATION_INTERVAL_MILLIS

    /** Picks the quote list in force: custom quotes only when enabled and non-empty. */
    fun activeBank(prefs: LauncherPreferences, defaults: List<String>): List<String> =
        if (prefs.useCustomQuotes && prefs.customQuotes.isNotEmpty()) {
            prefs.customQuotes
        } else {
            defaults
        }

    fun quoteAt(bank: List<String>, index: Int): String =
        if (bank.isEmpty()) "" else bank[Math.floorMod(index, bank.size)]
}
