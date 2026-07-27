package com.aurora.calculatorvault.feature.vault.presentation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.navigation.VaultTabRoute
import com.aurora.calculatorvault.feature.disguise.presentation.DisguiseScreen
import com.aurora.calculatorvault.feature.hiddenapp.presentation.HiddenAppScreen
import com.aurora.calculatorvault.feature.privatemedia.presentation.PrivateMediaScreen
import com.aurora.calculatorvault.feature.settings.presentation.SettingsScreen
import com.aurora.calculatorvault.ui.component.VaultBottomNavigation
import com.aurora.calculatorvault.ui.component.VaultNavigationItem

@Composable
fun VaultMainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = listOf(
        Triple(VaultTabRoute.Disguise, stringResource(R.string.tab_disguise), VaultIcons.Disguise),
        Triple(VaultTabRoute.HiddenApp, stringResource(R.string.tab_hidden_app), VaultIcons.Hidden),
        Triple(VaultTabRoute.PrivateMedia, stringResource(R.string.tab_private_media), VaultIcons.Photos),
        Triple(VaultTabRoute.Settings, stringResource(R.string.tab_settings), VaultIcons.Settings),
    )

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        containerColor = AppColors.BackgroundPrimary,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
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
            composable(VaultTabRoute.Settings.path) { SettingsScreen() }
        }
    }
}

