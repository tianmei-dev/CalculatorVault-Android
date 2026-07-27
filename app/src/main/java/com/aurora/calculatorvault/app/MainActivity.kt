package com.aurora.calculatorvault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.theme.CalculatorVaultTheme
import com.aurora.calculatorvault.core.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalculatorVaultTheme {
                AppNavHost(
                    navController = rememberNavController(),
                    modifier = Modifier.fillMaxSize().background(AppColors.BackgroundPrimary),
                )
            }
        }
    }
}

