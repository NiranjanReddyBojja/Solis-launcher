package com.solis.launcher.domain

/** Timed focus sessions: the drawer stays closed until the session ends. */
object FocusSession {

    /** Session lengths offered in settings, in minutes. */
    val PRESETS_MINUTES = listOf(25, 50, 90)

    fun endsAt(nowMillis: Long, minutes: Int): Long = nowMillis + minutes * 60_000L

    /** Whole seconds left, rounded up so the lockout never shows "0 min" while still active. */
    fun remainingSeconds(untilMillis: Long, nowMillis: Long): Long =
        if (untilMillis <= nowMillis) 0L else (untilMillis - nowMillis + 999L) / 1_000L

    /** "1:05:00" or "24:59" for the home screen and the lockout. */
    fun format(remainingSeconds: Long): String {
        val h = remainingSeconds / 3_600
        val m = remainingSeconds % 3_600 / 60
        val s = remainingSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
