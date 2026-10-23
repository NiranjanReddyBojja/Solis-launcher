package com.solis.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.solis.launcher.ui.LauncherAction
import com.solis.launcher.ui.LauncherEffect
import com.solis.launcher.ui.LauncherViewModel
import com.solis.launcher.ui.SolisRoot
import com.solis.launcher.ui.theme.SolisTheme
import kotlinx.coroutines.launch

/**
 * The home activity. Owns no logic: it renders [LauncherViewModel] state, forwards input as
 * actions, and performs effects that need an Activity (starting intents, the billing sheet,
 * the default-launcher role request).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels { LauncherViewModel.Factory }

    private val homeRoleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onAction(LauncherAction.HomeRoleRequestFinished)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) proStore.handleIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { handleEffect(it) }
            }
        }

        setContent {
            SolisTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                // Always enabled: on the root screen the ViewModel swallows back, so the
                // launcher can never be exited; elsewhere it closes the drawer or settings.
                BackHandler(enabled = true) { viewModel.onAction(LauncherAction.BackPressed) }

                LaunchedEffect(Unit) { viewModel.onAction(LauncherAction.Resumed) }

                SolisRoot(state = state, onAction = viewModel::onAction)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Default-launcher and accessibility status may have changed in system settings.
        viewModel.onAction(LauncherAction.Resumed)
    }

    /** Pressing Home while Solis is already in front returns to the bare home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (proStore.handleIntent(intent)) return
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.onAction(LauncherAction.HomePressed)
        }
    }

    private fun handleEffect(effect: LauncherEffect) {
        when (effect) {
            is LauncherEffect.StartActivity -> safeStart(effect.intent)
            is LauncherEffect.RequestHomeRole -> runCatching { homeRoleRequest.launch(effect.intent) }
                .onFailure { toast("open settings → default apps → home app") }
            LauncherEffect.LaunchPurchaseFlow -> proStore.launchPurchaseFlow(this)
            is LauncherEffect.Toast -> toast(effect.text)
        }
    }

    private val proStore get() = (application as SolisApp).container.proStore

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("nothing can open that")
        } catch (e: SecurityException) {
            toast("that app can't be opened from here")
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
