package com.solis.launcher.domain

/** Decides whether the drawer curfew is in force at a given minute of the day. */
object CurfewPolicy {

    /**
     * @param minuteOfDay 0..1439 in local time.
     * A window whose start is after its end wraps past midnight (e.g. 22:00 -> 07:00).
     * A zero-length window (start == end) never locks.
     */
    fun isLocked(config: CurfewConfig, minuteOfDay: Int): Boolean {
        if (!config.enabled) return false
        val start = config.startMinutes
        val end = config.endMinutes
        return when {
            start == end -> false
            start < end -> minuteOfDay in start until end
            else -> minuteOfDay >= start || minuteOfDay < end
        }
    }

    fun format(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
}
