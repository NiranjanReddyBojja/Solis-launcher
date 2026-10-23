package com.solis.launcher.system

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Exists only to perform two global actions the platform reserves for accessibility services:
 * locking the screen and expanding the notification shade. It ignores every event it receives
 * and cannot read window content (see accessibility_service_config.xml).
 */
class FocusAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isRunning.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        _isRunning.value = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: FocusAccessibilityService? = null

        private val _isRunning = MutableStateFlow(false)

        /** True while the service is bound by the system. */
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        /** Turns the screen off. Returns false if the service is not enabled. */
        fun lockScreen(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false

        /**
         * Pulls down the notification shade. The platform constant is
         * GLOBAL_ACTION_NOTIFICATIONS. Falls back to the status bar service when the
         * accessibility service is off, which works on most (not all) OEM builds.
         */
        fun expandNotifications(context: Context): Boolean {
            instance?.let { return it.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) }
            return expandViaStatusBarService(context)
        }

        /** Reads the secure setting so the answer is right even before the service binds. */
        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            val expected = ComponentName(context, FocusAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == expected
            }
        }

        @SuppressLint("WrongConstant")
        private fun expandViaStatusBarService(context: Context): Boolean = runCatching {
            val service = context.getSystemService("statusbar") ?: return false
            service.javaClass.getMethod("expandNotificationsPanel").invoke(service)
            true
        }.getOrDefault(false)
    }
}
