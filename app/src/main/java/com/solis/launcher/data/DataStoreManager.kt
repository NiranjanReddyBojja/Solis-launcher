package com.solis.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.solis.launcher.domain.CurfewConfig
import com.solis.launcher.domain.LauncherPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

/**
 * Single source of truth for everything Solis persists: settings, pinned/hidden apps, quotes,
 * the dodged-impulse metric and the cached Pro entitlement.
 *
 * Pro status here is a cache. [com.solis.launcher.billing.BillingManager] re-verifies it against
 * Google Play on every start and overwrites it, so editing the file on a rooted device does not
 * survive the next successful Play query.
 */
class DataStoreManager(
    context: Context,
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.applicationContext.preferencesDataStoreFile(STORE_NAME) },
    )

    private object Keys {
        val IS_PRO = booleanPreferencesKey("is_pro")
        val DODGED_COUNT = intPreferencesKey("dodged_count")
        val DODGED_DATE = stringPreferencesKey("dodged_date")
        val DODGED_TOTAL = intPreferencesKey("dodged_total")
        val HIDDEN = stringSetPreferencesKey("hidden_packages")
        val DISTRACTIONS = stringSetPreferencesKey("distraction_packages")
        val LAUNCH_DELAY = intPreferencesKey("launch_delay_seconds")
        val CUSTOM_QUOTES = stringPreferencesKey("custom_quotes")
        val USE_CUSTOM_QUOTES = booleanPreferencesKey("use_custom_quotes")
        val QUOTE_INDEX = intPreferencesKey("quote_index")
        val QUOTE_ROTATED_AT = longPreferencesKey("quote_rotated_at")
        val CURFEW_ENABLED = booleanPreferencesKey("curfew_enabled")
        val CURFEW_START = intPreferencesKey("curfew_start")
        val CURFEW_END = intPreferencesKey("curfew_end")
        val LOCKSCREEN_SYNC = booleanPreferencesKey("lockscreen_quote_sync")
        val NOTES_PACKAGE = stringPreferencesKey("notes_package")
        val SETUP_DISMISSED = booleanPreferencesKey("launcher_setup_dismissed")
        val A11Y_DISCLOSURE = booleanPreferencesKey("a11y_disclosure_accepted")
    }

    val preferences: Flow<LauncherPreferences> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::toModel)
        .distinctUntilChanged()

    private fun toModel(p: Preferences): LauncherPreferences {
        val isToday = p[Keys.DODGED_DATE] == today().toString()
        return LauncherPreferences(
            isPro = p[Keys.IS_PRO] ?: false,
            dodgedToday = if (isToday) p[Keys.DODGED_COUNT] ?: 0 else 0,
            dodgedCount = p[Keys.DODGED_COUNT] ?: 0,
            dodgedDate = p[Keys.DODGED_DATE],
            dodgedTotal = p[Keys.DODGED_TOTAL] ?: 0,
            hiddenPackages = p[Keys.HIDDEN] ?: emptySet(),
            distractionPackages = p[Keys.DISTRACTIONS] ?: emptySet(),
            launchDelaySeconds = (p[Keys.LAUNCH_DELAY] ?: LauncherPreferences.DEFAULT_LAUNCH_DELAY_SECONDS)
                .coerceIn(LauncherPreferences.MIN_LAUNCH_DELAY_SECONDS, LauncherPreferences.MAX_LAUNCH_DELAY_SECONDS),
            customQuotes = decodeQuotes(p[Keys.CUSTOM_QUOTES]),
            useCustomQuotes = p[Keys.USE_CUSTOM_QUOTES] ?: false,
            quoteIndex = p[Keys.QUOTE_INDEX] ?: 0,
            quoteRotatedAtMillis = p[Keys.QUOTE_ROTATED_AT] ?: 0L,
            curfew = CurfewConfig(
                enabled = p[Keys.CURFEW_ENABLED] ?: false,
                startMinutes = p[Keys.CURFEW_START] ?: CurfewConfig().startMinutes,
                endMinutes = p[Keys.CURFEW_END] ?: CurfewConfig().endMinutes,
            ),
            lockscreenQuoteSync = p[Keys.LOCKSCREEN_SYNC] ?: false,
            notesPackage = p[Keys.NOTES_PACKAGE],
            launcherSetupDismissed = p[Keys.SETUP_DISMISSED] ?: false,
            accessibilityDisclosureAccepted = p[Keys.A11Y_DISCLOSURE] ?: false,
        )
    }

    // ---- Metrics ---------------------------------------------------------------------------

    /** Adds one to today's dodged impulses, resetting the counter when the day has rolled over. */
    suspend fun incrementDodgedImpulses() {
        store.edit { p ->
            val todayKey = today().toString()
            val current = if (p[Keys.DODGED_DATE] == todayKey) p[Keys.DODGED_COUNT] ?: 0 else 0
            p[Keys.DODGED_DATE] = todayKey
            p[Keys.DODGED_COUNT] = current + 1
            p[Keys.DODGED_TOTAL] = (p[Keys.DODGED_TOTAL] ?: 0) + 1
        }
    }

    // ---- Pro entitlement -------------------------------------------------------------------

    suspend fun setPro(isPro: Boolean) = store.edit { it[Keys.IS_PRO] = isPro }

    // ---- App vault and distraction flags ---------------------------------------------------

    suspend fun setHidden(packageName: String, hidden: Boolean) =
        store.edit { it.toggleInSet(Keys.HIDDEN, packageName, hidden) }

    suspend fun setDistraction(packageName: String, flagged: Boolean) =
        store.edit { it.toggleInSet(Keys.DISTRACTIONS, packageName, flagged) }

    suspend fun setLaunchDelay(seconds: Int) = store.edit {
        it[Keys.LAUNCH_DELAY] = seconds.coerceIn(
            LauncherPreferences.MIN_LAUNCH_DELAY_SECONDS,
            LauncherPreferences.MAX_LAUNCH_DELAY_SECONDS,
        )
    }

    /** Forgets vault/flag/notes entries for a package that was uninstalled. */
    suspend fun forgetPackage(packageName: String) = store.edit { p ->
        p[Keys.HIDDEN]?.let { p[Keys.HIDDEN] = it - packageName }
        p[Keys.DISTRACTIONS]?.let { p[Keys.DISTRACTIONS] = it - packageName }
        if (p[Keys.NOTES_PACKAGE] == packageName) p.remove(Keys.NOTES_PACKAGE)
    }

    // ---- Quotes ----------------------------------------------------------------------------

    suspend fun setCustomQuotes(quotes: List<String>) =
        store.edit { it[Keys.CUSTOM_QUOTES] = encodeQuotes(quotes) }

    suspend fun setUseCustomQuotes(enabled: Boolean) = store.edit {
        it[Keys.USE_CUSTOM_QUOTES] = enabled
        it[Keys.QUOTE_INDEX] = 0
    }

    suspend fun setQuotePosition(index: Int, rotatedAtMillis: Long) = store.edit {
        it[Keys.QUOTE_INDEX] = index
        it[Keys.QUOTE_ROTATED_AT] = rotatedAtMillis
    }

    suspend fun setLockscreenQuoteSync(enabled: Boolean) =
        store.edit { it[Keys.LOCKSCREEN_SYNC] = enabled }

    // ---- Curfew ----------------------------------------------------------------------------

    suspend fun setCurfew(config: CurfewConfig) = store.edit {
        it[Keys.CURFEW_ENABLED] = config.enabled
        it[Keys.CURFEW_START] = config.startMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
        it[Keys.CURFEW_END] = config.endMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
    }

    // ---- Misc ------------------------------------------------------------------------------

    suspend fun setNotesPackage(packageName: String?) = store.edit {
        if (packageName == null) {
            it.remove(Keys.NOTES_PACKAGE)
        } else {
            it[Keys.NOTES_PACKAGE] = packageName
        }
    }

    suspend fun setLauncherSetupDismissed(dismissed: Boolean) =
        store.edit { it[Keys.SETUP_DISMISSED] = dismissed }

    suspend fun setAccessibilityDisclosureAccepted(accepted: Boolean) =
        store.edit { it[Keys.A11Y_DISCLOSURE] = accepted }

    private fun MutablePreferences.toggleInSet(
        key: Preferences.Key<Set<String>>,
        value: String,
        include: Boolean,
    ) {
        val current = this[key] ?: emptySet()
        this[key] = if (include) current + value else current - value
    }

    companion object {
        private const val STORE_NAME = "solis_prefs"
        private const val MINUTES_PER_DAY = 24 * 60

        /** ASCII unit separator: quotes may contain newlines and commas, never this. */
        private const val QUOTE_SEPARATOR = '\u001F'

        internal fun encodeQuotes(quotes: List<String>): String =
            quotes.map { it.replace(QUOTE_SEPARATOR, ' ').trim() }
                .filter { it.isNotEmpty() }
                .joinToString(QUOTE_SEPARATOR.toString())

        internal fun decodeQuotes(raw: String?): List<String> =
            raw?.split(QUOTE_SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
    }
}
