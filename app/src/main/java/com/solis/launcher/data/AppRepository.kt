package com.solis.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import com.solis.launcher.domain.AppEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.Collator

/**
 * Holds the installed, launchable apps. Refreshed at start-up and whenever
 * [com.solis.launcher.system.PackageChangeReceiver] reports an install, removal or change.
 */
class AppRepository(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    suspend fun refresh() {
        _apps.value = withContext(io) { loadApps() }
    }

    private fun loadApps(): List<AppEntry> {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        // LauncherApps covers the main user profile; work profiles are out of scope for v1.
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .asSequence()
            .filter { it.applicationInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    label = it.label.toString().trim(),
                    packageName = it.componentName.packageName,
                    activityName = it.componentName.className,
                )
            }
            .distinctBy { it.key }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
            .toList()
    }

    /** Explicit launch intent for an entry, used by the drawer and the notes fallback. */
    fun launchIntent(app: AppEntry): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    fun isInstalled(packageName: String): Boolean =
        _apps.value.any { it.packageName == packageName }
}
