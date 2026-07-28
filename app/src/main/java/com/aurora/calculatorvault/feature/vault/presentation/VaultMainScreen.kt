package com.aurora.calculatorvault.feature.vault.presentation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.navigation.VaultTabRoute
import com.aurora.calculatorvault.feature.disguise.presentation.DisguiseScreen
import com.aurora.calculatorvault.feature.hiddenapp.presentation.HiddenAppScreen
import com.aurora.calculatorvault.feature.privatemedia.presentation.PrivateMediaScreen
import com.aurora.calculatorvault.feature.settings.presentation.SettingsScreen
import com.aurora.calculatorvault.feature.settings.presentation.ChangePasswordScreen
import com.aurora.calculatorvault.feature.settings.presentation.ChangePasswordStep
import com.aurora.calculatorvault.feature.settings.presentation.ChangePasswordViewModel
import com.aurora.calculatorvault.feature.settings.presentation.ForgotPasswordScreen
import com.aurora.calculatorvault.ui.component.VaultBottomNavigation
import com.aurora.calculatorvault.ui.component.VaultNavigationItem
import kotlinx.coroutines.launch

@Composable
fun VaultMainScreen(
    onExitVault: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as CalculatorVaultApp
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val passwordChangedMessage = stringResource(R.string.password_change_success)
    val tabs = listOf(
        Triple(VaultTabRoute.Disguise, stringResource(R.string.tab_disguise), VaultIcons.Disguise),
        Triple(VaultTabRoute.HiddenApp, stringResource(R.string.tab_hidden_app), VaultIcons.Hidden),
        Triple(VaultTabRoute.PrivateMedia, stringResource(R.string.tab_private_media), VaultIcons.Photos),
        Triple(VaultTabRoute.Settings, stringResource(R.string.tab_settings), VaultIcons.Settings),
    )
    val tabRoutes = tabs.map { it.first.path }.toSet()

    BackHandler(enabled = currentRoute in tabRoutes) {
        onExitVault()
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        containerColor = AppColors.BackgroundPrimary,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = AppColors.SurfaceElevated,
                    contentColor = AppColors.TextPrimary,
                )
            }
        },
        bottomBar = {
            if (currentRoute in tabRoutes) {
                VaultBottomNavigation(
                    items = tabs.map { (route, label, icon) ->
                        VaultNavigationItem(
                            label = label,
                            icon = icon,
                            selected = currentRoute == route.path,
                            onClick = {
                                navController.navigate(route.path) {
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
            startDestination = VaultTabRoute.Disguise.path,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(VaultTabRoute.Disguise.path) { DisguiseScreen() }
            composable(VaultTabRoute.HiddenApp.path) { HiddenAppScreen() }
            composable(VaultTabRoute.PrivateMedia.path) { PrivateMediaScreen() }
            composable(VaultTabRoute.Settings.path) {
                SettingsScreen(
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
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(passwordChangedMessage)
                        }
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
