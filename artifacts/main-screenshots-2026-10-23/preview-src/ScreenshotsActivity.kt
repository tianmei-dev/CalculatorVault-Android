package com.aurora.calculatorvault.screenshotpreview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.theme.CalculatorVaultTheme
import com.aurora.calculatorvault.feature.appmanagement.presentation.*
import com.aurora.calculatorvault.feature.calculator.presentation.*
import com.aurora.calculatorvault.feature.applock.presentation.*
import com.aurora.calculatorvault.feature.privatemedia.presentation.*
import com.aurora.calculatorvault.feature.settings.presentation.SettingsScreen
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseSource
import com.aurora.calculatorvault.feature.membership.presentation.*
import com.aurora.calculatorvault.feature.vault.presentation.VaultMainTab
import com.aurora.calculatorvault.ui.component.*
import com.aurora.calculatorvault.ui.message.*

/** Isolated screenshot harness; never compiled into the production application. */
class ScreenshotsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val page = intent.getStringExtra("screen") ?: "management"
        val app = application as CalculatorVaultApp
        setContent {
            CalculatorVaultTheme {
                val messages = rememberAppMessageController()
                CompositionLocalProvider(LocalAppMessageController provides messages) {
                    if (page == "calculator") {
                        val model = remember { CalculatorViewModel() }
                        androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = AppColors.BackgroundPrimary) { CalculatorScreen(model, {}) }
                    } else {
                        val selected = when (page) {
                            "album" -> VaultMainTab.PrivateAlbum
                            "lock" -> VaultMainTab.AppLock
                            "settings" -> VaultMainTab.Settings
                            else -> VaultMainTab.AppManagement
                        }
                        Scaffold(
                            containerColor = AppColors.BackgroundPrimary,
                            contentWindowInsets = WindowInsets.safeDrawing,
                            bottomBar = {
                                if (page != "vip") VaultBottomNavigation(VaultMainTab.entries.map {
                                    VaultNavigationItem(stringResource(it.titleRes), it.icon, selected == it, {})
                                })
                            },
                        ) { padding ->
                            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                                when (page) {
                                    "management" -> AppManagementContent(AppManagementUiState(isLoading = false, isDisguiseLoading = false), {}, {})
                                    "settings" -> SettingsScreen({}, {}, {}, {}, {}, {}, {}, {})
                                    "album" -> {
                                        val model: PrivateMediaViewModel = viewModel(factory = PrivateMediaViewModel.Factory(app.vaultMediaRepository))
                                        PrivateMediaScreen(model, app.vaultSessionManager, app.systemMediaRemovalManager, {}, {}, {})
                                    }
                                    "lock" -> {
                                        val model: AppLockViewModel = viewModel(factory = AppLockViewModel.Factory(app.appLockRepository, applicationContext, app.analyticsTracker))
                                        AppLockScreen(model, app.appIconProvider, {})
                                    }
                                    "vip" -> {
                                        val model: VipPurchaseViewModel = viewModel(factory = VipPurchaseViewModel.Factory(VipPurchaseSource.Settings))
                                        VipPurchaseScreen(model, {}, {})
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
