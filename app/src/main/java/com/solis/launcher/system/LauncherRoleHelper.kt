package com.solis.launcher.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

/** Default-launcher handshake via RoleManager (Android 10+, which is our minSdk). */
class LauncherRoleHelper(private val context: Context) {

    private val roleManager: RoleManager? = context.getSystemService(RoleManager::class.java)

    fun isDefaultLauncher(): Boolean =
        roleManager?.let { it.isRoleAvailable(RoleManager.ROLE_HOME) && it.isRoleHeld(RoleManager.ROLE_HOME) }
            ?: false

    /**
     * The system dialog asking the user to make Solis their home app. Null when the role is not
     * available, in which case callers fall back to Settings.ACTION_HOME_SETTINGS.
     */
    fun requestRoleIntent(): Intent? =
        roleManager?.takeIf { it.isRoleAvailable(RoleManager.ROLE_HOME) }
            ?.createRequestRoleIntent(RoleManager.ROLE_HOME)
}
