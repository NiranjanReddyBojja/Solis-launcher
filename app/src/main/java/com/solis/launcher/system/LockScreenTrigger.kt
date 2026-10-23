package com.solis.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.solis.launcher.lock.LockScreenActivity

/**
 * Starts [LockScreenActivity] each time the screen goes off, so it is already sitting on top of
 * the system keyguard when the screen comes back on.
 *
 * SCREEN_OFF is only delivered to receivers registered at runtime, so this lives as long as the
 * process (which, for the default launcher, is effectively always). Android 10+ blocks activity
 * starts from the background unless the app is in front or has a system-bound accessibility
 * service, so the lock screen appears reliably only while Solis Focus is on, or when Solis
 * itself was on screen as the phone went to sleep.
 */
class LockScreenTrigger(
    private val isEnabled: () -> Boolean,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_OFF) return
        if (!isEnabled() || isInCall(context)) return
        runCatching { context.startActivity(LockScreenActivity.intent(context)) }
    }

    fun register(context: Context) {
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        // A system broadcast, so it reaches a non-exported receiver.
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * The proximity sensor blanks the screen during calls; covering the call UI with a lock
     * screen would be hostile. Audio mode needs no permission, unlike the telephony call state.
     */
    private fun isInCall(context: Context): Boolean {
        val mode = context.getSystemService(AudioManager::class.java)?.mode ?: return false
        return mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
    }
}
