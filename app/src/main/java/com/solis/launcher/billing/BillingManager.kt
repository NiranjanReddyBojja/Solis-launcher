package com.solis.launcher.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.solis.launcher.data.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** Messages the paywall shows after a purchase or restore attempt. */
sealed interface BillingEvent {
    data object Unlocked : BillingEvent
    data object Pending : BillingEvent
    data object NothingToRestore : BillingEvent
    data object Cancelled : BillingEvent
    data class Error(val message: String) : BillingEvent
}

/**
 * Google Play Billing v7 integration for the single non-consumable "Solis Pro" lifetime unlock.
 *
 * Responsibilities:
 * - keep one [BillingClient] connected, reconnecting with backoff,
 * - load the product's localized price,
 * - launch the purchase flow,
 * - acknowledge new purchases (Play refunds unacknowledged purchases after 3 days),
 * - re-verify ownership on start and on restore, mirroring the result into [DataStoreManager].
 *
 * For stronger tamper resistance, send [Purchase.getPurchaseToken] to your own backend and verify
 * it with the Play Developer API before granting. Client-side verification is used here.
 */
class BillingManager(
    context: Context,
    private val dataStore: DataStoreManager,
    private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    private val connectMutex = Mutex()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)

    private val _formattedPrice = MutableStateFlow<String?>(null)

    /** Localized price string from Play (e.g. "$4.99", "₹399.00"), null until loaded. */
    val formattedPrice: StateFlow<String?> = _formattedPrice.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    /** Connects, loads the price and re-checks ownership. Call once at app start. */
    fun start() {
        scope.launch {
            if (ensureConnected()) {
                loadProductDetails()
                syncOwnedPurchases(reportIfNone = false)
            }
        }
    }

    /** Opens the Play purchase sheet. Results arrive in [onPurchasesUpdated]. */
    fun launchPurchaseFlow(activity: Activity) {
        scope.launch {
            if (!ensureConnected()) {
                _events.emit(BillingEvent.Error("Google Play is unavailable. Try again later."))
                return@launch
            }
            val details = _productDetails.value ?: loadProductDetails()
            if (details == null) {
                _events.emit(BillingEvent.Error("Solis Pro is not available on this account."))
                return@launch
            }
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build(),
                    ),
                )
                .build()
            val result = client.launchBillingFlow(activity, params)
            if (result.responseCode != BillingResponseCode.OK) {
                handleFailure(result)
            }
        }
    }

    /** "Restore Purchase": asks Play what this Google account owns. */
    fun restorePurchases() {
        scope.launch {
            if (!ensureConnected()) {
                _events.emit(BillingEvent.Error("Google Play is unavailable. Try again later."))
                return@launch
            }
            syncOwnedPurchases(reportIfNone = true)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        scope.launch {
            when (result.responseCode) {
                BillingResponseCode.OK -> purchases.orEmpty().forEach { handlePurchase(it) }
                BillingResponseCode.ITEM_ALREADY_OWNED -> syncOwnedPurchases(reportIfNone = false)
                BillingResponseCode.USER_CANCELED -> _events.emit(BillingEvent.Cancelled)
                else -> handleFailure(result)
            }
        }
    }

    // ---- Internals -------------------------------------------------------------------------

    private suspend fun loadProductDetails(): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val result = client.queryProductDetails(params)
        val details = result.productDetailsList?.firstOrNull { it.productId == PRODUCT_ID }
        _productDetails.value = details
        _formattedPrice.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
        if (result.billingResult.responseCode != BillingResponseCode.OK) {
            Log.w(TAG, "queryProductDetails: ${result.billingResult.debugMessage}")
        }
        return details
    }

    /**
     * Mirrors Play's view of ownership into DataStore. Only revokes Pro when Play answered
     * successfully, so being offline never takes away a paid unlock.
     */
    private suspend fun syncOwnedPurchases(reportIfNone: Boolean) {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        )
        if (result.billingResult.responseCode != BillingResponseCode.OK) {
            if (reportIfNone) handleFailure(result.billingResult)
            return
        }
        val owned = result.purchasesList.filter { PRODUCT_ID in it.products }
        val purchased = owned.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchased.isEmpty()) {
            dataStore.setPro(false)
            if (reportIfNone) {
                _events.emit(
                    if (owned.any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
                        BillingEvent.Pending
                    } else {
                        BillingEvent.NothingToRestore
                    },
                )
            }
            return
        }
        purchased.forEach { handlePurchase(it) }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (PRODUCT_ID !in purchase.products) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!purchase.isAcknowledged) {
                    val ack = client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build(),
                    )
                    if (ack.responseCode != BillingResponseCode.OK) {
                        // Grant anyway; acknowledgement is retried on the next start.
                        Log.w(TAG, "acknowledgePurchase failed: ${ack.debugMessage}")
                    }
                }
                dataStore.setPro(true)
                _events.emit(BillingEvent.Unlocked)
            }
            Purchase.PurchaseState.PENDING -> _events.emit(BillingEvent.Pending)
            else -> Unit
        }
    }

    private suspend fun handleFailure(result: BillingResult) {
        Log.w(TAG, "Billing error ${result.responseCode}: ${result.debugMessage}")
        val message = when (result.responseCode) {
            BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingResponseCode.SERVICE_DISCONNECTED,
            BillingResponseCode.NETWORK_ERROR -> "Can't reach Google Play. Check your connection."
            BillingResponseCode.BILLING_UNAVAILABLE -> "Billing isn't available on this device."
            BillingResponseCode.ITEM_UNAVAILABLE -> "Solis Pro is not available right now."
            else -> "Something went wrong with Google Play (${result.responseCode})."
        }
        _events.emit(BillingEvent.Error(message))
    }

    /** Connects if needed, retrying with exponential backoff. Returns true when ready. */
    private suspend fun ensureConnected(): Boolean = connectMutex.withLock {
        if (client.isReady) return@withLock true
        var backoffMs = INITIAL_BACKOFF_MS
        repeat(MAX_CONNECT_ATTEMPTS) { attempt ->
            val result = connectOnce()
            if (result.responseCode == BillingResponseCode.OK) return@withLock true
            if (result.responseCode == BillingResponseCode.BILLING_UNAVAILABLE) return@withLock false
            if (attempt < MAX_CONNECT_ATTEMPTS - 1) {
                delay(backoffMs)
                backoffMs *= 2
            }
        }
        false
    }

    private suspend fun connectOnce(): BillingResult = suspendCancellableCoroutine { cont ->
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (cont.isActive) cont.resume(result)
            }

            override fun onBillingServiceDisconnected() {
                // The next call to ensureConnected() reconnects lazily.
                if (cont.isActive) {
                    cont.resume(
                        BillingResult.newBuilder()
                            .setResponseCode(BillingResponseCode.SERVICE_DISCONNECTED)
                            .build(),
                    )
                }
            }
        })
    }

    fun endConnection() {
        if (client.isReady) client.endConnection()
    }

    companion object {
        const val PRODUCT_ID = "solis_pro_lifetime_499"
        private const val TAG = "SolisBilling"
        private const val MAX_CONNECT_ATTEMPTS = 4
        private const val INITIAL_BACKOFF_MS = 1_000L
    }
}
