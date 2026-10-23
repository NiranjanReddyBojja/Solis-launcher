package com.solis.launcher.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

/** The five pinned essentials plus the two clock/date shortcuts. */
enum class Essential(val label: String) {
    PHONE("phone"),
    MESSAGES("messages"),
    NOTES("notes"),
    CALENDAR("calendar"),
    CAMERA("camera"),
    ALARM("alarm"),
}

/** Outcome of resolving an [Essential]: either a launchable intent or a need to ask the user. */
sealed interface Resolution {
    data class Launch(val intent: Intent) : Resolution

    /** No notes app could be found automatically; ask the user to pick one, once. */
    data object PickNotesApp : Resolution

    data object Unavailable : Resolution
}

/**
 * Resolves system utilities through generic intents and categories instead of hard-coded
 * packages, so the same build works on Pixel, Samsung, Xiaomi, Motorola and others.
 */
class IntentResolver(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    /**
     * @param notesPackage the package the user previously picked for notes, if any.
     */
    fun resolve(essential: Essential, notesPackage: String? = null): Resolution {
        val candidates: List<Intent> = when (essential) {
            Essential.PHONE -> listOf(Intent(Intent.ACTION_DIAL))
            Essential.MESSAGES -> listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
                // Some OEM SMS apps only register for the sms: scheme.
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            )
            Essential.CAMERA -> listOf(
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            )
            Essential.CALENDAR -> listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
                // Fallback understood by Google Calendar, Samsung Calendar and most forks.
                Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/")),
            )
            Essential.ALARM -> listOf(
                Intent(AlarmClock.ACTION_SHOW_ALARMS),
                Intent(AlarmClock.ACTION_SET_ALARM),
            )
            Essential.NOTES -> return resolveNotes(notesPackage)
        }
        return candidates.firstNotNullOfOrNull { it.launchableOrNull() }
            ?.let { Resolution.Launch(it) }
            ?: Resolution.Unavailable
    }

    /**
     * Notes has no universal category, so try in order:
     * 1. the app the user picked before,
     * 2. the Android 14+ notes role via ACTION_CREATE_NOTE,
     * 3. well-known notes packages,
     * 4. otherwise ask the user to pick one (remembered afterwards).
     */
    private fun resolveNotes(notesPackage: String?): Resolution {
        notesPackage?.let { pkg -> launchIntentFor(pkg)?.let { return Resolution.Launch(it) } }

        Intent(ACTION_CREATE_NOTE).launchableOrNull()?.let { return Resolution.Launch(it) }

        KNOWN_NOTES_PACKAGES.firstNotNullOfOrNull(::launchIntentFor)
            ?.let { return Resolution.Launch(it) }

        return Resolution.PickNotesApp
    }

    fun launchIntentFor(packageName: String): Intent? =
        pm.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appInfoIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun homeSettingsIntent(): Intent =
        Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun Intent.launchableOrNull(): Intent? {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (resolveActivity(pm) != null) this else null
    }

    companion object {
        /** Intent.ACTION_CREATE_NOTE (API 34). Referenced by value so it compiles on minSdk 29. */
        const val ACTION_CREATE_NOTE = "android.intent.action.CREATE_NOTE"

        val KNOWN_NOTES_PACKAGES = listOf(
            "com.google.android.keep",
            "com.samsung.android.app.notes",
            "com.miui.notes",
            "com.motorola.notes",
            "com.microsoft.office.onenote",
        )
    }
}
