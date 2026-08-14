package com.aurora.calculatorvault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.theme.CalculatorVaultTheme
import com.aurora.calculatorvault.core.navigation.AppNavHost
import com.aurora.calculatorvault.ui.message.AppMessageHost
import com.aurora.calculatorvault.ui.message.LocalAppMessageController
import com.aurora.calculatorvault.ui.message.rememberAppMessageController

class MainActivity : ComponentActivity() {
    private val calculatorVaultApp: CalculatorVaultApp
        get() = applicationContext as CalculatorVaultApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalculatorVaultTheme {
                val appMessageController = rememberAppMessageController()
                CompositionLocalProvider(LocalAppMessageController provides appMessageController) {
                    Box(Modifier.fillMaxSize()) {
                        AppNavHost(
                            navController = rememberNavController(),
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AppColors.BackgroundPrimary),
                        )
                        AppMessageHost(controller = appMessageController)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        calculatorVaultApp.vaultSessionManager.onHostActivityResumed()
    }
}
