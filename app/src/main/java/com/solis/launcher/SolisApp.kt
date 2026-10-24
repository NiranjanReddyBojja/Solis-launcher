package com.solis.launcher

import android.app.Application
import android.content.Intent
import com.solis.launcher.billing.BillingManager
import com.solis.launcher.data.AppRepository
import com.solis.launcher.data.DataStoreManager
import com.solis.launcher.system.IntentResolver
import com.solis.launcher.system.LauncherRoleHelper
import com.solis.launcher.system.LockscreenQuoteWriter
import com.solis.launcher.system.PackageChangeReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SolisApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this).also { it.start() }
    }
}

/**
 * Manual dependency graph. Small enough that a DI framework would cost more than it saves.
 * Everything here lives for the whole process, which for a launcher is effectively forever.
 */
class AppContainer(private val app: Application) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val dataStore = DataStoreManager(app)
    val appRepository = AppRepository(app)
    val intentResolver = IntentResolver(app)
    val roleHelper = LauncherRoleHelper(app)
    val lockscreenWriter = LockscreenQuoteWriter(app)
    val billingManager = BillingManager(app, dataStore, appScope)

    private val packageReceiver = PackageChangeReceiver { action, packageName ->
        appScope.launch {
            appRepository.refresh()
            if (action == Intent.ACTION_PACKAGE_REMOVED && packageName != null) {
                dataStore.forgetPackage(packageName)
            }
        }
    }

    fun start() {
        packageReceiver.register(app)
        appScope.launch { appRepository.refresh() }
        billingManager.start()
    }
}
