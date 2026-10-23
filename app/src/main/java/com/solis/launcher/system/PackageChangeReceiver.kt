package com.solis.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Registered dynamically (not in the manifest, which no longer receives these broadcasts for
 * apps targeting API 26+). Fires [onPackagesChanged] for installs, removals and enable/disable
 * changes so the drawer never shows a stale list.
 */
class PackageChangeReceiver(
    private val onPackagesChanged: (action: String, packageName: String?) -> Unit,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        // A replace (update) sends REMOVED then ADDED with EXTRA_REPLACING; one refresh is enough.
        if (action == Intent.ACTION_PACKAGE_REMOVED &&
            intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        ) {
            return
        }
        onPackagesChanged(action, intent.data?.schemeSpecificPart)
    }

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        // Package broadcasts come from the system, which is allowed to reach non-exported receivers.
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun unregister(context: Context) {
        runCatching { context.unregisterReceiver(this) }
    }
}
