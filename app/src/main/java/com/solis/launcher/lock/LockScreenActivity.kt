package com.solis.launcher.lock

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.solis.launcher.SolisApp
import com.solis.launcher.ui.lock.LockScreen
import com.solis.launcher.ui.theme.SolisTheme

/**
 * The Solis lock screen. Android does not let a third-party app replace the secure keyguard, so
 * this is a show-when-locked activity that sits on top of it: clock, today's dodged impulses and
 * one quote. Swiping up hands over to the system keyguard (PIN, pattern or biometrics), and once
 * that succeeds the activity finishes, revealing whatever was open before.
 *
 * It runs in its own task so finishing never lands on the Solis home screen by accident.
 */
class LockScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)

        // Like the system keyguard, back does nothing here.
        onBackPressedDispatcher.addCallback(this) { }

        val preferences = (application as SolisApp).container.preferences
        setContent {
            SolisTheme {
                val prefs by preferences.collectAsStateWithLifecycle()
                LockScreen(prefs = prefs, onUnlock = ::unlock)
            }
        }
    }

    private fun unlock() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            finish()
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                // Cancelled or failed: stay put, the user can swipe again.
                override fun onDismissSucceeded() = finish()
            },
        )
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, LockScreenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
