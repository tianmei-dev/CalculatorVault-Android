package com.aurora.calculatorvault.feature.vault.presentation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.navigation.VaultTabRoute
import com.aurora.calculatorvault.feature.applock.presentation.AppLockScreen
import com.aurora.calculatorvault.feature.applock.presentation.AppLockViewModel
import com.aurora.calculatorvault.feature.appmanagement.presentation.AppManagementScreen
import com.aurora.calculatorvault.feature.appmanagement.presentation.AppManagementViewModel
import com.aurora.calculatorvault.feature.disguise.presentation.AppDisguiseScreen
import com.aurora.calculatorvault.feature.disguise.presentation.AppDisguiseViewModel
import com.aurora.calculatorvault.feature.hiddenapp.presentation.HiddenAppScreen
import com.aurora.calculatorvault.feature.hiddenapp.presentation.HiddenAppPickerScreen
import com.aurora.calculatorvault.feature.hiddenapp.presentation.HiddenAppPickerViewModel
import com.aurora.calculatorvault.feature.hiddenapp.presentation.HiddenAppViewModel
import com.aurora.calculatorvault.feature.privatemedia.presentation.PrivateMediaScreen
import com.aurora.calculatorvault.feature.privatemedia.presentation.PrivateMediaViewModel
import com.aurora.calculatorvault.feature.settings.presentation.SettingsScreen
import com.aurora.calculatorvault.feature.settings.presentation.RecentHistoryViewModel
import com.aurora.calculatorvault.feature.settings.presentation.ChangePasswordScreen
import com.aurora.calculatorvault.feature.settings.presentation.ChangePasswordStep
import com.aurora.calculatorvault.feature.settings.presentation.ChangePasswordViewModel
import com.aurora.calculatorvault.feature.settings.presentation.ForgotPasswordScreen
import com.aurora.calculatorvault.ui.component.VaultBottomNavigation
import com.aurora.calculatorvault.ui.component.VaultNavigationItem
import com.aurora.calculatorvault.ui.message.LocalAppMessageController

@Composable
fun VaultMainScreen(
    onExitVault: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as CalculatorVaultApp
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val messageController = LocalAppMessageController.current
    val passwordChangedMessage = stringResource(R.string.password_change_success)
    val tabs = VaultMainTab.entries
    val tabRoutes = tabs.map { it.route.path }.toSet()

    BackHandler(enabled = currentRoute in tabRoutes) {
        onExitVault()
    }

    Scaffold(
        containerColor = AppColors.BackgroundPrimary,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (currentRoute in tabRoutes) {
                VaultBottomNavigation(
                    items = tabs.map { tab ->
                        VaultNavigationItem(
                            label = stringResource(tab.titleRes),
                            icon = tab.icon,
                            selected = currentRoute == tab.route.path,
                            testTag = tab.testTag,
                            onClick = {
                                navController.navigate(tab.route.path) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = VaultMainTab.default.route.path,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(VaultTabRoute.AppManagement.path) {
                val appManagementViewModel: AppManagementViewModel = viewModel(
                    factory = AppManagementViewModel.Factory(
                        application.hiddenAppRepository,
                        application.disguiseEntryRepository,
                    ),
                )
                AppManagementScreen(
                    viewModel = appManagementViewModel,
                    onOpenDisguise = {
                        navController.navigate(VaultTabRoute.AppDisguise.path) {
                            launchSingleTop = true
                        }
                    },
                    onOpenPrivateApps = {
                        navController.navigate(VaultTabRoute.HiddenApp.path) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(VaultTabRoute.AppDisguise.path) {
                val appDisguiseViewModel: AppDisguiseViewModel = viewModel(
                    factory = AppDisguiseViewModel.Factory(
                        application.disguiseEntryRepository,
                        application.requestPinShortcutUseCase,
                        application.shortcutSyncManager,
                        application.shortcutRepository,
                    ),
                )
                AppDisguiseScreen(
                    viewModel = appDisguiseViewModel,
                    iconProvider = application.appIconProvider,
                    onBackToManagement = navController::popBackStack,
                    onMessage = messageController::show,
                )
            }
            composable(VaultTabRoute.AppLock.path) {
                val appLockViewModel: AppLockViewModel = viewModel(
                    factory = AppLockViewModel.Factory(
                        application.appLockRepository,
                        application.applicationContext,
                    ),
                )
                AppLockScreen(
                    viewModel = appLockViewModel,
                    iconProvider = application.appIconProvider,
                    onMessage = messageController::show,
                )
            }
            composable(VaultTabRoute.HiddenApp.path) {
                val hiddenAppViewModel: HiddenAppViewModel = viewModel(
                    factory = HiddenAppViewModel.Factory(
                        application.hiddenAppRepository,
                        application.launchHiddenAppUseCase,
                        application.hiddenAppPreferences,
                    ),
                )
                HiddenAppScreen(
                    viewModel = hiddenAppViewModel,
                    iconProvider = application.appIconProvider,
                    onAddApps = {
                        navController.navigate(VaultTabRoute.HiddenAppPicker.path) {
                            launchSingleTop = true
                        }
                    },
                    onBack = navController::popBackStack,
                    onMessage = messageController::show,
                )
            }
            composable(VaultTabRoute.HiddenAppPicker.path) {
                val pickerViewModel: HiddenAppPickerViewModel = viewModel(
                    factory = HiddenAppPickerViewModel.Factory(application.hiddenAppRepository),
                )
                HiddenAppPickerScreen(
                    viewModel = pickerViewModel,
                    iconProvider = application.appIconProvider,
                    onBack = navController::popBackStack,
                    onCompleted = { count ->
                        navController.popBackStack()
                        messageController.showSuccess(
                            context.getString(R.string.hidden_app_added_success, count),
                        )
                    },
                )
            }
            composable(VaultTabRoute.PrivateMedia.path) {
                val privateMediaViewModel: PrivateMediaViewModel = viewModel(
                    factory = PrivateMediaViewModel.Factory(application.vaultMediaRepository),
                )
                PrivateMediaScreen(
                    viewModel = privateMediaViewModel,
                    sessionManager = application.vaultSessionManager,
                    systemMediaRemovalManager = application.systemMediaRemovalManager,
                )
            }
            composable(VaultTabRoute.Settings.path) {
                val recentHistoryViewModel: RecentHistoryViewModel = viewModel(
                    factory = RecentHistoryViewModel.Factory(application.hiddenAppRepository),
                )
                val recentClearedMessage = stringResource(R.string.hidden_app_recent_cleared)
                LaunchedEffect(recentHistoryViewModel) {
                    recentHistoryViewModel.cleared.collect {
                        messageController.showSuccess(recentClearedMessage)
                    }
                }
                SettingsScreen(
                    recentHistoryViewModel = recentHistoryViewModel,
                    onChangePassword = {
                        navController.navigate(VaultTabRoute.ChangePassword.path)
                    },
                    onForgotPassword = {
                        navController.navigate(VaultTabRoute.ForgotPassword.path)
                    },
                )
            }
            composable(VaultTabRoute.ChangePassword.path) {
                val changePasswordViewModel: ChangePasswordViewModel = viewModel(
                    factory = ChangePasswordViewModel.Factory(
                        application.changePasswordRepository,
                    ),
                )
                val state by changePasswordViewModel.uiState.collectAsState()
                LaunchedEffect(state.step) {
                    if (state.step == ChangePasswordStep.Completed) {
                        changePasswordViewModel.cancelFlow()
                        navController.popBackStack(
                            route = VaultTabRoute.Settings.path,
                            inclusive = false,
                        )
                        messageController.showSuccess(passwordChangedMessage)
                    }
                }
                ChangePasswordScreen(
                    state = state,
                    onDigit = changePasswordViewModel::addDigit,
                    onDelete = changePasswordViewModel::deleteDigit,
                    onSubmit = changePasswordViewModel::submit,
                    onBackStep = changePasswordViewModel::returnToPreviousStep,
                    onExit = {
                        changePasswordViewModel.cancelFlow()
                        navController.popBackStack()
                    },
                    onForgotPassword = {
                        navController.navigate(VaultTabRoute.ForgotPassword.path)
                    },
                    onResetSamePassword = changePasswordViewModel::resetSamePassword,
                    onAcceptSamePassword = changePasswordViewModel::acceptSamePassword,
                )
            }
            composable(VaultTabRoute.ForgotPassword.path) {
                ForgotPasswordScreen(onBack = navController::popBackStack)
            }
        }
    }
}
