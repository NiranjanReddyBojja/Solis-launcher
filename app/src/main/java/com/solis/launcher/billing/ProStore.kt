package com.solis.launcher.billing

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Messages the paywall shows after a purchase or restore attempt. */
sealed interface BillingEvent {
    data object Unlocked : BillingEvent
    data object Pending : BillingEvent
    data object NothingToRestore : BillingEvent
    data object Cancelled : BillingEvent
    data class Error(val message: String) : BillingEvent
}

/**
 * Where Solis Pro is bought. Each build flavor supplies one through `createProStore`:
 * - `play`: Google Play Billing (`BillingManager`), required for the Play Store listing,
 * - `direct`: Stripe Checkout plus a signed licence, for the APK distributed outside Play.
 *
 * Implementations mirror the verified entitlement into DataStore and report results as [events].
 */
interface ProStore {

    /** Localized price to show on the paywall, or null to fall back to the list price. */
    val formattedPrice: StateFlow<String?>

    val events: SharedFlow<BillingEvent>

    /** Re-checks ownership. Call once at app start. */
    fun start()

    fun launchPurchaseFlow(activity: Activity)

    fun restorePurchases()

    /** Handles a purchase-return deep link. Returns true when the intent was consumed. */
    fun handleIntent(intent: Intent): Boolean = false
}
