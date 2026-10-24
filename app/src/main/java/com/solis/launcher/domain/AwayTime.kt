package com.solis.launcher.domain

/** How long the user has stayed out of one flagged app. */
data class AwayStat(
    val app: AppEntry,
    val awayMillis: Long,
    val bestMillis: Long,
)

/**
 * Turns "last time this app was on screen" into a streak, a message and the next milestone.
 * Pure, so the wording and thresholds are unit tested.
 */
object AwayTime {

    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    /** Milestones worth celebrating, in order. */
    val MILESTONES = listOf(HOUR, 6 * HOUR, DAY, 3 * DAY, 7 * DAY, 14 * DAY, 30 * DAY, 90 * DAY, 365 * DAY)

    fun awayMillis(lastSeenMillis: Long, nowMillis: Long): Long = (nowMillis - lastSeenMillis).coerceAtLeast(0L)

    /** "3d 4h", "5h 12m", "12m", "under a minute". Two units at most. */
    fun format(millis: Long): String {
        val days = millis / DAY
        val hours = millis % DAY / HOUR
        val minutes = millis % HOUR / MINUTE
        return when {
            days > 0 -> if (hours > 0) "${days}d ${hours}h" else "${days}d"
            hours > 0 -> if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "under a minute"
        }
    }

    fun nextMilestone(millis: Long): Long? = MILESTONES.firstOrNull { it > millis }

    /** One line of encouragement that grows with the streak. */
    fun encouragement(millis: Long): String = when {
        millis < 15 * MINUTE -> "the urge peaks and passes. ride it out."
        millis < HOUR -> "the first hour is the hardest one."
        millis < 6 * HOUR -> "hours back in your day."
        millis < DAY -> "almost a full day. notice how it feels."
        millis < 3 * DAY -> "the pull is getting weaker."
        millis < 7 * DAY -> "a new habit is forming."
        millis < 30 * DAY -> "weeks free. this is who you are now."
        else -> "it doesn't own you anymore."
    }

    /** Builds one stat per flagged app that is still installed, longest-struggling first. */
    fun stats(
        flagged: Set<String>,
        apps: List<AppEntry>,
        lastSeen: Map<String, Long>,
        best: Map<String, Long>,
        nowMillis: Long,
    ): List<AwayStat> = apps
        .filter { it.packageName in flagged }
        .distinctBy { it.packageName }
        .mapNotNull { app ->
            val seen = lastSeen[app.packageName] ?: return@mapNotNull null
            val away = awayMillis(seen, nowMillis)
            AwayStat(app, away, maxOf(away, best[app.packageName] ?: 0L))
        }
        .sortedBy { it.awayMillis }

    // ---- Persistence of personal bests -----------------------------------------------------

    private const val ENTRY_SEPARATOR = '\u001F'
    private const val FIELD_SEPARATOR = '='

    fun encodeBest(best: Map<String, Long>): String =
        best.entries.joinToString(ENTRY_SEPARATOR.toString()) { "${it.key}$FIELD_SEPARATOR${it.value}" }

    fun decodeBest(raw: String?): Map<String, Long> =
        raw?.split(ENTRY_SEPARATOR)?.mapNotNull { entry ->
            val pkg = entry.substringBeforeLast(FIELD_SEPARATOR, "")
            val millis = entry.substringAfterLast(FIELD_SEPARATOR).toLongOrNull()
            if (pkg.isEmpty() || millis == null) null else pkg to millis
        }?.toMap() ?: emptyMap()
}
