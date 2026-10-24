package com.solis.launcher.system

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import com.solis.launcher.domain.AwayTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads, on the device only, when each flagged app was last on screen. Needs the user to grant
 * "usage access" in system settings; nothing here leaves the phone.
 */
class UsageAccess(private val context: Context) {

    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    fun isGranted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Opens the usage access page, on Solis's own entry where the OEM supports it. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun fallbackSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Last time each package was on screen. Recent use comes from the exact event log (kept for
     * about a week); older use from the yearly aggregates. An app with no recorded use counts
     * from its install time, capped at a year back.
     */
    suspend fun lastSeen(packages: Set<String>, nowMillis: Long): Map<String, Long> =
        withContext(Dispatchers.IO) {
            if (packages.isEmpty() || !isGranted()) return@withContext emptyMap()
            val stats = usageStats ?: return@withContext emptyMap()
            val result = HashMap<String, Long>()

            runCatching {
                val events = stats.queryEvents(nowMillis - RECENT_WINDOW_MS, nowMillis)
                val event = UsageEvents.Event()
                val resumed = HashSet<String>()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val pkg = event.packageName ?: continue
                    if (pkg !in packages) continue
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> resumed += pkg
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.ACTIVITY_STOPPED -> resumed -= pkg
                        else -> continue
                    }
                    result[pkg] = maxOf(result[pkg] ?: 0L, event.timeStamp)
                }
                // Still on screen (split screen, picture-in-picture): the streak is zero.
                resumed.forEach { result[it] = nowMillis }
            }

            val yearAgo = nowMillis - AwayTime.DAY * 365
            val missing = packages - result.keys
            if (missing.isNotEmpty()) {
                val aggregated = runCatching { stats.queryAndAggregateUsageStats(yearAgo, nowMillis) }
                    .getOrDefault(emptyMap())
                missing.forEach { pkg ->
                    val visible = aggregated[pkg]?.lastTimeVisible ?: 0L
                    val seen = if (visible in yearAgo..nowMillis) visible else maxOf(yearAgo, installedAt(pkg))
                    result[pkg] = seen
                }
            }
            result
        }

    private fun installedAt(pkg: String): Long = runCatching {
        context.packageManager.getPackageInfo(pkg, 0).firstInstallTime
    }.getOrDefault(0L)

    private companion object {
        const val RECENT_WINDOW_MS = 7 * AwayTime.DAY
    }
}
