package com.aurora.calculatorvault.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.aurora.calculatorvault.feature.calculator.presentation.CalculatorScreen
import com.aurora.calculatorvault.feature.onboarding.presentation.ConfirmPasswordScreen
import com.aurora.calculatorvault.feature.onboarding.presentation.CreatePasswordScreen
import com.aurora.calculatorvault.feature.onboarding.presentation.PrivacyConsentScreen
import com.aurora.calculatorvault.feature.onboarding.presentation.SplashScreen
import com.aurora.calculatorvault.feature.vault.presentation.VaultMainScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Splash.path,
        modifier = modifier,
    ) {
        composable(AppRoute.Splash.path) {
            SplashScreen(
                // Phase 1 为便于调试临时直达普通计算器，首次使用路由仍完整保留。
                onFinished = {
                    navController.navigate(AppRoute.Calculator.path) {
                        popUpTo(AppRoute.Splash.path) { inclusive = true }
                    }
                },
            )
        }
        composable(AppRoute.PrivacyConsent.path) {
            PrivacyConsentScreen { navController.navigate(AppRoute.CreatePassword.path) }
        }
        composable(AppRoute.CreatePassword.path) {
            CreatePasswordScreen { navController.navigate(AppRoute.ConfirmPassword.path) }
        }
        composable(AppRoute.ConfirmPassword.path) {
            ConfirmPasswordScreen {
                navController.navigate(AppRoute.Calculator.path) {
                    popUpTo(AppRoute.PrivacyConsent.path) { inclusive = true }
                }
            }
        }
        composable(AppRoute.Calculator.path) {
            CalculatorScreen {
                navController.navigate(AppRoute.VaultMain.path)
            }
        }
        composable(AppRoute.VaultMain.path) {
            VaultMainScreen()
        }
    }
}

