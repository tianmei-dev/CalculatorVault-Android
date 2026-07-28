package com.aurora.calculatorvault.core.navigation

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.security.SecureScreenEffect
import com.aurora.calculatorvault.core.security.session.VaultSessionState
import com.aurora.calculatorvault.feature.calculator.presentation.CalculatorScreen
import com.aurora.calculatorvault.feature.calculator.presentation.CalculatorViewModel
import com.aurora.calculatorvault.feature.onboarding.presentation.ConfirmPasswordScreen
import com.aurora.calculatorvault.feature.onboarding.presentation.CreatePasswordScreen
import com.aurora.calculatorvault.feature.onboarding.presentation.OnboardingStep
import com.aurora.calculatorvault.feature.onboarding.presentation.OnboardingViewModel
import com.aurora.calculatorvault.feature.onboarding.presentation.PrivacyConsentScreen
import com.aurora.calculatorvault.feature.onboarding.presentation.PrivacyPolicyScreen
import com.aurora.calculatorvault.feature.onboarding.presentation.SplashScreen
import com.aurora.calculatorvault.feature.onboarding.presentation.UserAgreementScreen
import com.aurora.calculatorvault.feature.vault.presentation.VaultMainScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val application = context.applicationContext as CalculatorVaultApp
    val onboardingViewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModel.Factory(application.onboardingRepository),
    )
    val onboardingState by onboardingViewModel.uiState.collectAsState()
    val sessionState by application.vaultSessionManager.state.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(sessionState, currentRoute) {
        if (
            currentRoute == AppRoute.VaultMain.path &&
            VaultRouteGuard.decide(sessionState) == VaultAccessDecision.RedirectToCalculator
        ) {
            navController.navigate(AppRoute.Calculator.path) {
                popUpTo(AppRoute.VaultMain.path) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(onboardingState.step) {
        when (onboardingState.step) {
            OnboardingStep.Loading -> Unit
            OnboardingStep.PrivacyConsent -> {
                if (currentRoute != AppRoute.PrivacyConsent.path) {
                    navController.navigate(AppRoute.PrivacyConsent.path) {
                        launchSingleTop = true
                    }
                }
            }

            OnboardingStep.CreatePassword -> {
                if (currentRoute != AppRoute.CreatePassword.path) {
                    if (currentRoute == AppRoute.ConfirmPassword.path) {
                        navController.popBackStack(
                            route = AppRoute.CreatePassword.path,
                            inclusive = false,
                        )
                    } else {
                        navController.navigate(AppRoute.CreatePassword.path) {
                            if (currentRoute == AppRoute.PrivacyConsent.path) {
                                popUpTo(AppRoute.PrivacyConsent.path) { inclusive = true }
                            }
                            launchSingleTop = true
                        }
                    }
                }
            }

            OnboardingStep.ConfirmPassword -> {
                if (currentRoute != AppRoute.ConfirmPassword.path) {
                    navController.navigate(AppRoute.ConfirmPassword.path) {
                        launchSingleTop = true
                    }
                }
            }

            OnboardingStep.Calculator -> {
                if (currentRoute != AppRoute.Calculator.path) {
                    navController.navigate(AppRoute.Calculator.path) {
                        popUpTo(AppRoute.Splash.path) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.Splash.path,
        modifier = modifier,
    ) {
        composable(AppRoute.Splash.path) {
            SplashScreen(
                state = onboardingState,
                onRetry = onboardingViewModel::loadStartupDestination,
            )
        }
        composable(AppRoute.PrivacyConsent.path) {
            PrivacyConsentScreen(
                state = onboardingState,
                onAgree = onboardingViewModel::acceptPrivacy,
                onDecline = { (context as? Activity)?.finish() },
                onOpenUserAgreement = {
                    navController.navigate(AppRoute.UserAgreement.path)
                },
                onOpenPrivacyPolicy = {
                    navController.navigate(AppRoute.PrivacyPolicy.path)
                },
            )
        }
        composable(AppRoute.UserAgreement.path) {
            UserAgreementScreen(onBack = navController::popBackStack)
        }
        composable(AppRoute.PrivacyPolicy.path) {
            PrivacyPolicyScreen(onBack = navController::popBackStack)
        }
        composable(AppRoute.CreatePassword.path) {
            CreatePasswordScreen(
                state = onboardingState,
                onDigit = onboardingViewModel::addPasswordDigit,
                onDelete = onboardingViewModel::deletePasswordDigit,
                onContinue = onboardingViewModel::continueToConfirmation,
            )
        }
        composable(AppRoute.ConfirmPassword.path) {
            ConfirmPasswordScreen(
                state = onboardingState,
                onDigit = onboardingViewModel::addConfirmationDigit,
                onDelete = onboardingViewModel::deleteConfirmationDigit,
                onConfirm = onboardingViewModel::confirmPassword,
                onBack = {
                    onboardingViewModel.returnToCreatePassword()
                    navController.popBackStack()
                },
            )
        }
        composable(AppRoute.Calculator.path) {
            val calculatorViewModel: CalculatorViewModel = viewModel(
                factory = CalculatorViewModel.Factory(
                    unlockUseCase = application.vaultUnlockUseCase,
                    sessionManager = application.vaultSessionManager,
                ),
            )
            CalculatorScreen(
                viewModel = calculatorViewModel,
                onOpenVault = {
                    if (application.vaultSessionManager.isUnlocked()) {
                        navController.navigate(AppRoute.VaultMain.path) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        composable(AppRoute.VaultMain.path) {
            if (sessionState == VaultSessionState.Unlocked) {
                SecureScreenEffect()
                VaultMainScreen(
                    onExitVault = application.vaultSessionManager::lock,
                )
            } else {
                // 守卫阶段只绘制纯背景，避免私密内容先闪现再重定向。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.BackgroundPrimary),
                )
            }
        }
    }
}
