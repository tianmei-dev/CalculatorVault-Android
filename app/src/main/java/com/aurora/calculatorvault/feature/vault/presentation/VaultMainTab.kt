package com.aurora.calculatorvault.feature.vault.presentation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.navigation.VaultTabRoute

enum class VaultMainTab(
    val route: VaultTabRoute,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val testTag: String,
) {
    AppManagement(
        route = VaultTabRoute.AppManagement,
        titleRes = R.string.tab_app_management,
        icon = VaultIcons.Apps,
        testTag = "tab_app_management",
    ),
    AppLock(
        route = VaultTabRoute.AppLock,
        titleRes = R.string.tab_app_lock,
        icon = VaultIcons.Lock,
        testTag = "tab_app_lock",
    ),
    PrivateAlbum(
        route = VaultTabRoute.PrivateMedia,
        titleRes = R.string.tab_private_media,
        icon = VaultIcons.Photos,
        testTag = "tab_private_album",
    ),
    Settings(
        route = VaultTabRoute.Settings,
        titleRes = R.string.tab_settings,
        icon = VaultIcons.Settings,
        testTag = "tab_settings",
    ),
    ;

    companion object {
        val default: VaultMainTab = AppManagement
    }
}
