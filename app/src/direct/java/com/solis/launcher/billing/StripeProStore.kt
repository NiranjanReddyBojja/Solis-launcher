package com.solis.launcher.billing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.solis.launcher.BuildConfig
import com.solis.launcher.data.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The `direct` flavor sells Pro through Stripe Checkout. */
fun createProStore(context: Context, dataStore: DataStoreManager, scope: CoroutineScope): ProStore =
    StripeProStore(dataStore, scope, LicenseVerifier(BuildConfig.LICENSE_PUBLIC_KEY))

/**
 * Stripe purchase flow for builds distributed outside Google Play.
 *
 * 1. "unlock" opens the checkout server's `/checkout` in the browser, which redirects to Stripe.
 * 2. After payment Stripe returns to the server's `/success` page. The server confirms the session
 *    is paid with Stripe, signs a licence and links back to `solis://pro?license=...`.
 * 3. [handleIntent] verifies the signature with the embedded public key and unlocks Pro.
 *
 * No Stripe keys live in the app: the secret key and the signing key stay on the server.
 * "Restore" re-checks the stored licence; on a new phone, the buyer opens the link on their
 * purchase page again.
 */
class StripeProStore(
    private val dataStore: DataStoreManager,
    private val scope: CoroutineScope,
    private val verifier: LicenseVerifier,
) : ProStore {

    // The paywall falls back to the list price, which matches the Stripe price.
    override val formattedPrice: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 4)
    override val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    override fun start() {
        scope.launch { syncLicence(reportIfNone = false) }
    }

    override fun launchPurchaseFlow(activity: Activity) {
        val base = BuildConfig.CHECKOUT_URL.trimEnd('/')
        if (base.isEmpty() || !verifier.isConfigured) {
            scope.launch { _events.emit(BillingEvent.Error("Payments aren't set up in this build.")) }
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$base/checkout"))
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            scope.launch { _events.emit(BillingEvent.Error("No browser found to open checkout.")) }
        }
    }

    override fun restorePurchases() {
        scope.launch { syncLicence(reportIfNone = true) }
    }

    override fun handleIntent(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        if (intent.action != Intent.ACTION_VIEW || uri.scheme != SCHEME || uri.host != HOST) return false
        // A bare solis://pro (the cancel page's "back" link) just brings Solis forward.
        val token = uri.getQueryParameter("license") ?: return true
        scope.launch {
            if (verifier.isValid(token)) {
                dataStore.setProLicense(token)
                dataStore.setPro(true)
                _events.emit(BillingEvent.Unlocked)
            } else {
                _events.emit(BillingEvent.Error("That licence link isn't valid."))
            }
        }
        return true
    }

    /** Mirrors the stored licence into the Pro flag. */
    private suspend fun syncLicence(reportIfNone: Boolean) {
        if (verifier.isValid(dataStore.proLicense())) {
            dataStore.setPro(true)
            if (reportIfNone) _events.emit(BillingEvent.Unlocked)
            return
        }
        // Debug builds keep the settings-screen Pro toggle.
        if (!BuildConfig.DEBUG) dataStore.setPro(false)
        if (reportIfNone) {
            _events.emit(
                BillingEvent.Error("No licence on this phone. Open the link on your purchase page to restore."),
            )
        }
    }

    private companion object {
        const val SCHEME = "solis"
        const val HOST = "pro"
    }
}
