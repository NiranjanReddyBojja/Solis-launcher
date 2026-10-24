package com.solis.launcher.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.solis.launcher.SolisApp
import com.solis.launcher.data.AppRepository
import com.solis.launcher.data.DataStoreManager
import com.solis.launcher.data.QuoteBank
import com.solis.launcher.domain.AppEntry
import com.solis.launcher.domain.AppFilter
import com.solis.launcher.domain.AwayTime
import com.solis.launcher.domain.CurfewPolicy
import com.solis.launcher.domain.FocusSession
import com.solis.launcher.domain.LauncherPreferences
import com.solis.launcher.domain.QuoteRotation
import com.solis.launcher.domain.Reflection
import com.solis.launcher.system.FocusAccessibilityService
import com.solis.launcher.system.IntentResolver
import com.solis.launcher.system.LauncherRoleHelper
import com.solis.launcher.system.LockscreenQuoteWriter
import com.solis.launcher.system.Resolution
import com.solis.launcher.system.UsageAccess
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * The launcher's single state machine. Reduces [LauncherAction]s into [LauncherUiState] and
 * emits [LauncherEffect]s for anything that needs an Activity (starting intents).
 */
class LauncherViewModel(
    private val app: Application,
    private val dataStore: DataStoreManager,
    private val appRepository: AppRepository,
    private val intentResolver: IntentResolver,
    private val roleHelper: LauncherRoleHelper,
    private val lockscreenWriter: LockscreenQuoteWriter,
    private val usageAccess: UsageAccess,
) : ViewModel() {

    /** UI-only state that is not persisted. */
    private data class Session(
        val screen: Screen = Screen.HOME,
        val dialog: LauncherDialog? = null,
        val vaultOpen: Boolean = false,
        val isDefaultLauncher: Boolean = true,
        val accessibilityEnabled: Boolean = false,
        val usageAccessGranted: Boolean = false,
        val lastSeenMillis: Map<String, Long> = emptyMap(),
    )

    private val session = MutableStateFlow(Session())

    private val prefs: StateFlow<LauncherPreferences> =
        dataStore.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherPreferences())

    /** Emits the current time at every second boundary. */
    private val clock: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }

    private val effectChannel = Channel<LauncherEffect>(Channel.BUFFERED)
    val effects: Flow<LauncherEffect> = effectChannel.receiveAsFlow()

    private var countdownJob: Job? = null
    private var roleRequestFailed = false
    private var usageJob: Job? = null

    /** Everything except the clock, so app filtering is not redone on every one-second tick. */
    private val snapshot: Flow<LauncherUiState> = combine(
        session,
        prefs,
        appRepository.apps,
    ) { s, p, apps ->
        val hidden = p.hiddenPackages
        val visible = AppFilter.visible(apps, hidden)
        LauncherUiState(
            screen = s.screen,
            dialog = s.dialog,
            quote = currentQuote(p),
            prefs = p,
            allApps = apps,
            appSections = AppFilter.sections(visible),
            visibleAppCount = visible.size,
            vaultApps = AppFilter.vault(apps, hidden),
            vaultOpen = s.vaultOpen,
            isDefaultLauncher = s.isDefaultLauncher,
            accessibilityEnabled = s.accessibilityEnabled,
            usageAccessGranted = s.usageAccessGranted,
            lastSeenMillis = s.lastSeenMillis,
        )
    }

    val state: StateFlow<LauncherUiState> = combine(snapshot, clock) { base, now ->
        val p = base.prefs
        val today = now.toLocalDate().toString()
        base.copy(
            now = now,
            nowMillis = System.currentTimeMillis(),
            // Zero the metric at midnight without waiting for a DataStore write.
            prefs = if (p.dodgedDate == today || p.dodgedToday == 0) p else p.copy(dodgedToday = 0),
            curfewLocked = CurfewPolicy.isLocked(p.curfew, now.hour * 60 + now.minute),
            focusRemainingSeconds = FocusSession.remainingSeconds(p.focusUntilMillis, System.currentTimeMillis()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    init {
        refreshSystemStatus()
        rotateQuotesPeriodically()
        syncLockscreenQuote()
        refreshUsageWhenFlagsChange()
    }

    fun onAction(action: LauncherAction) {
        when (action) {
            LauncherAction.OpenDrawer -> session.update { it.copy(screen = Screen.DRAWER, vaultOpen = false) }
            LauncherAction.OpenMoment -> session.update { it.copy(screen = Screen.MOMENT, dialog = null) }
            LauncherAction.OpenAway -> {
                session.update { it.copy(screen = Screen.AWAY, dialog = null) }
                refreshUsage()
            }
            LauncherAction.OpenSettings -> session.update { it.copy(screen = Screen.SETTINGS, dialog = null) }
            LauncherAction.HomePressed -> goHome()
            LauncherAction.BackPressed -> onBack()
            LauncherAction.SwipeDown -> expandShade()
            LauncherAction.Resumed -> refreshSystemStatus()

            is LauncherAction.LaunchEssential -> launchEssential(action)
            LauncherAction.NextQuote -> advanceQuote()
            LauncherAction.OpenImpulseInterceptor -> showDialog(LauncherDialog.ImpulseInterceptor)
            LauncherAction.BurnImpulse -> burnImpulse()
            LauncherAction.RequestDefaultLauncher -> requestDefaultLauncher()
            LauncherAction.HomeRoleRequestFinished -> {
                refreshSystemStatus()
                roleRequestFailed = !roleHelper.isDefaultLauncher()
            }
            LauncherAction.DismissLauncherSetup -> viewModelScope.launch { dataStore.setLauncherSetupDismissed(true) }

            LauncherAction.ToggleVault -> session.update { it.copy(vaultOpen = !it.vaultOpen) }
            is LauncherAction.LaunchApp -> launchApp(action.app)
            is LauncherAction.LongPressApp -> showDialog(LauncherDialog.AppActions(action.app))
            is LauncherAction.OpenAppInfo -> {
                dismissDialog()
                emit(LauncherEffect.StartActivity(intentResolver.appInfoIntent(action.app.packageName)))
            }
            is LauncherAction.Uninstall -> {
                dismissDialog()
                emit(LauncherEffect.StartActivity(intentResolver.uninstallIntent(action.app.packageName)))
            }
            is LauncherAction.ToggleHidden -> toggleHidden(action.app)
            is LauncherAction.ToggleDistraction -> toggleDistraction(action.app)
            LauncherAction.CancelCountdown -> cancelCountdown()
            is LauncherAction.PickNotesApp -> pickNotesApp(action.app)

            is LauncherAction.SaveReflection -> saveReflection(action)
            is LauncherAction.DeleteReflection -> persist { dataStore.deleteReflection(action.atMillis) }
            LauncherAction.OpenUsageAccess -> emit(LauncherEffect.StartActivity(usageAccess.settingsIntent()))

            LauncherAction.DismissDialog -> dismissDialog()
            LauncherAction.AcceptAccessibilityDisclosure -> {
                viewModelScope.launch { dataStore.setAccessibilityDisclosureAccepted(true) }
                session.update { it.copy(dialog = null) }
                emit(LauncherEffect.StartActivity(intentResolver.accessibilitySettingsIntent()))
            }

            is LauncherAction.SetCurfew -> persist { dataStore.setCurfew(action.config) }
            is LauncherAction.SetLaunchDelay -> persist { dataStore.setLaunchDelay(action.seconds) }
            is LauncherAction.AddQuote -> persist {
                val text = action.text.trim()
                if (text.isNotEmpty()) dataStore.setCustomQuotes(prefs.value.customQuotes + text)
            }
            is LauncherAction.RemoveQuote -> persist {
                val quotes = prefs.value.customQuotes
                if (action.index in quotes.indices) {
                    dataStore.setCustomQuotes(quotes.filterIndexed { i, _ -> i != action.index })
                }
            }
            is LauncherAction.SetUseCustomQuotes -> persist { dataStore.setUseCustomQuotes(action.enabled) }
            is LauncherAction.SetLockscreenSync -> persist { dataStore.setLockscreenQuoteSync(action.enabled) }
            LauncherAction.ResetNotesApp -> viewModelScope.launch { dataStore.setNotesPackage(null) }
            LauncherAction.OpenAccessibilitySettings ->
                emit(LauncherEffect.StartActivity(intentResolver.accessibilitySettingsIntent()))
            is LauncherAction.StartFocus -> startFocus(action.minutes)
            LauncherAction.EndFocus -> persist { dataStore.setFocusUntil(0L) }
        }
    }

    // ---- Navigation ------------------------------------------------------------------------

    private fun goHome() {
        cancelCountdownSilently()
        session.update { it.copy(screen = Screen.HOME, dialog = null, vaultOpen = false) }
    }

    private fun onBack() {
        val s = session.value
        when {
            s.dialog != null -> dismissDialog()
            s.screen == Screen.DRAWER -> session.update { it.copy(screen = Screen.HOME, vaultOpen = false) }
            s.screen != Screen.HOME -> session.update { it.copy(screen = Screen.HOME) }
            // On the root screen, back does nothing: the launcher must never exit.
            else -> Unit
        }
    }

    private fun showDialog(dialog: LauncherDialog) =
        session.update { it.copy(dialog = dialog) }

    private fun dismissDialog() {
        if (session.value.dialog is LauncherDialog.LaunchCountdown) {
            cancelCountdown()
        } else {
            session.update { it.copy(dialog = null) }
        }
    }

    private fun refreshSystemStatus() {
        session.update {
            it.copy(
                isDefaultLauncher = roleHelper.isDefaultLauncher(),
                accessibilityEnabled = FocusAccessibilityService.isEnabled(app),
                usageAccessGranted = usageAccess.isGranted(),
            )
        }
        refreshUsage()
    }

    private fun expandShade() {
        if (!FocusAccessibilityService.expandNotifications(app)) {
            showDialog(LauncherDialog.AccessibilityDisclosure(AccessibilityPurpose.NOTIFICATION_SHADE))
        }
    }

    // ---- Launching -------------------------------------------------------------------------

    private fun launchEssential(action: LauncherAction.LaunchEssential) {
        when (val r = intentResolver.resolve(action.essential, prefs.value.notesPackage)) {
            is Resolution.Launch -> emit(LauncherEffect.StartActivity(r.intent))
            Resolution.PickNotesApp -> showDialog(LauncherDialog.NotesPicker)
            Resolution.Unavailable -> emit(LauncherEffect.Toast("no ${action.essential.label} app found"))
        }
    }

    private fun pickNotesApp(app: AppEntry) {
        viewModelScope.launch { dataStore.setNotesPackage(app.packageName) }
        session.update { it.copy(dialog = null) }
        emit(LauncherEffect.StartActivity(appRepository.launchIntent(app)))
    }

    private fun launchApp(app: AppEntry) {
        val p = prefs.value
        if (app.packageName in p.distractionPackages) {
            startCountdown(app, p.launchDelaySeconds)
        } else {
            openAndReturnHome(app)
        }
    }

    private fun openAndReturnHome(app: AppEntry) {
        emit(LauncherEffect.StartActivity(appRepository.launchIntent(app)))
        // Reset behind the launched app so coming back always lands on the calm home screen.
        viewModelScope.launch {
            delay(RESET_AFTER_LAUNCH_MS)
            session.update { it.copy(screen = Screen.HOME, dialog = null, vaultOpen = false) }
        }
    }

    private fun startCountdown(app: AppEntry, seconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                session.update {
                    it.copy(dialog = LauncherDialog.LaunchCountdown(app, seconds, remaining))
                }
                delay(1_000L)
            }
            session.update { it.copy(dialog = null) }
            openAndReturnHome(app)
        }
    }

    /** Backing out of a delayed launch counts as a dodged impulse. */
    private fun cancelCountdown() {
        cancelCountdownSilently()
        session.update { it.copy(dialog = null) }
        viewModelScope.launch { dataStore.incrementDodgedImpulses() }
        emit(LauncherEffect.Toast("impulse dodged"))
    }

    private fun cancelCountdownSilently() {
        countdownJob?.cancel()
        countdownJob = null
    }

    // ---- Impulse interceptor ---------------------------------------------------------------

    private fun burnImpulse() {
        session.update { it.copy(dialog = null) }
        viewModelScope.launch {
            dataStore.incrementDodgedImpulses()
            // Let the dialog animate out before the screen goes dark.
            delay(LOCK_DELAY_MS)
            if (!FocusAccessibilityService.lockScreen()) {
                showDialog(LauncherDialog.AccessibilityDisclosure(AccessibilityPurpose.LOCK_SCREEN))
            }
        }
    }

    // ---- Default launcher ------------------------------------------------------------------

    private fun requestDefaultLauncher() {
        val roleIntent = roleHelper.requestRoleIntent()
        // If the role dialog already failed once (some OEMs suppress it after a denial),
        // go straight to the system "Default home app" screen.
        if (roleIntent != null && !roleRequestFailed) {
            emit(LauncherEffect.RequestHomeRole(roleIntent))
        } else {
            emit(LauncherEffect.StartActivity(intentResolver.homeSettingsIntent()))
        }
    }

    // ---- Vault, launch delay, focus ----------------------------------------------------------

    private fun toggleHidden(app: AppEntry) {
        val hide = app.packageName !in prefs.value.hiddenPackages
        session.update { it.copy(dialog = null) }
        viewModelScope.launch { dataStore.setHidden(app.packageName, hide) }
        emit(
            LauncherEffect.Toast(
                if (hide) "moved to the vault, at the end of the list" else "back in the list",
            ),
        )
    }

    private fun toggleDistraction(app: AppEntry) {
        val flag = app.packageName !in prefs.value.distractionPackages
        session.update { it.copy(dialog = null) }
        viewModelScope.launch { dataStore.setDistraction(app.packageName, flag) }
        emit(
            LauncherEffect.Toast(
                if (flag) "${app.label} now waits ${prefs.value.launchDelaySeconds}s" else "delay removed",
            ),
        )
    }

    private fun startFocus(minutes: Int) {
        persist { dataStore.setFocusUntil(FocusSession.endsAt(System.currentTimeMillis(), minutes)) }
        session.update { it.copy(screen = Screen.HOME, dialog = null, vaultOpen = false) }
        emit(LauncherEffect.Toast("focus for $minutes min. the app list is closed."))
    }

    // ---- Self-care and time away --------------------------------------------------------------

    private fun saveReflection(action: LauncherAction.SaveReflection) {
        if (action.text.isBlank()) return
        persist { dataStore.addReflection(Reflection(System.currentTimeMillis(), action.prompt, action.text)) }
        emit(LauncherEffect.Toast("kept. thank you for checking in."))
    }

    /**
     * Re-reads when each flagged app was last on screen and raises the stored personal bests.
     * The launcher resumes every time the user comes home, so a streak is recorded at most one
     * home-visit late.
     */
    private fun refreshUsage() {
        usageJob?.cancel()
        usageJob = viewModelScope.launch {
            val flagged = prefs.value.distractionPackages
            val now = System.currentTimeMillis()
            val lastSeen = usageAccess.lastSeen(flagged, now)
            session.update { it.copy(lastSeenMillis = lastSeen, usageAccessGranted = usageAccess.isGranted()) }
            val best = prefs.value.awayBestMillis
            val raised = lastSeen.mapValues { (_, seen) -> AwayTime.awayMillis(seen, now) }
                .filter { (pkg, away) -> away > (best[pkg] ?: 0L) + AwayTime.MINUTE }
            if (raised.isNotEmpty()) dataStore.raiseAwayBest(raised)
        }
    }

    private fun refreshUsageWhenFlagsChange() {
        viewModelScope.launch {
            prefs.map { it.distractionPackages }.distinctUntilChanged().collectLatest { refreshUsage() }
        }
    }

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    // ---- Quotes ----------------------------------------------------------------------------

    private fun currentQuote(p: LauncherPreferences): String =
        QuoteRotation.quoteAt(QuoteRotation.activeBank(p, QuoteBank.defaults), p.quoteIndex)

    private fun advanceQuote() {
        viewModelScope.launch {
            val p = dataStore.preferences.first()
            dataStore.setQuotePosition(p.quoteIndex + 1, System.currentTimeMillis())
        }
    }

    private fun rotateQuotesPeriodically() {
        viewModelScope.launch {
            while (isActive) {
                val p = dataStore.preferences.first()
                val now = System.currentTimeMillis()
                if (QuoteRotation.isDue(p.quoteRotatedAtMillis, now)) {
                    // The very first launch starts at a random quote rather than always the first.
                    val next = if (p.quoteRotatedAtMillis == 0L) {
                        (QuoteBank.defaults.indices).random()
                    } else {
                        p.quoteIndex + 1
                    }
                    dataStore.setQuotePosition(next, now)
                }
                delay(QUOTE_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun syncLockscreenQuote() {
        viewModelScope.launch {
            dataStore.preferences
                .map { p -> if (p.lockscreenQuoteSync) currentQuote(p) else null }
                .distinctUntilChanged()
                .filterNotNull()
                .collectLatest { quote -> lockscreenWriter.write(quote) }
        }
    }

    private fun emit(effect: LauncherEffect) {
        effectChannel.trySend(effect)
    }

    companion object {
        private const val RESET_AFTER_LAUNCH_MS = 400L
        private const val LOCK_DELAY_MS = 250L
        private const val QUOTE_CHECK_INTERVAL_MS = 60_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SolisApp
                val c = app.container
                LauncherViewModel(
                    app = app,
                    dataStore = c.dataStore,
                    appRepository = c.appRepository,
                    intentResolver = c.intentResolver,
                    roleHelper = c.roleHelper,
                    lockscreenWriter = c.lockscreenWriter,
                    usageAccess = c.usageAccess,
                )
            }
        }
    }
}
