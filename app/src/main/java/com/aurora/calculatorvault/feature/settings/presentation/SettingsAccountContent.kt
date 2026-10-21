package com.aurora.calculatorvault.feature.settings.presentation

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.ui.component.VaultDialog
import com.aurora.calculatorvault.ui.component.VaultSettingItem

@Composable
internal fun SettingsAccountContent(
    state: AccountUiState,
    onLogin: () -> Unit,
    onVipMembership: () -> Unit,
    onLogoutConfirmed: () -> Unit,
) {
    var showLogout by rememberSaveable { mutableStateOf(false) }
    val loggedIn = state.account as? AccountStatus.WeChatLoggedIn
    VaultSettingItem(
        title = loggedIn?.displayNickname ?: stringResource(
            if (loggedIn == null) R.string.account_wechat_login else R.string.account_wechat_user),
        subtitle = stringResource(if (loggedIn == null) R.string.account_login_description else R.string.account_logged_in),
        icon = VaultIcons.Privacy,
        onClick = if (loggedIn == null) onLogin else ({}),
        enabled = loggedIn == null,
        modifier = Modifier.testTag("settings_account"),
        adaptiveHeight = true,
    )
    val membership = membershipText(state)
    VaultSettingItem(
        title = stringResource(membership.title),
        subtitle = stringResource(membership.subtitle, *membership.arguments.toTypedArray()),
        icon = VaultIcons.Security,
        onClick = onVipMembership,
        showDivider = loggedIn != null,
        modifier = Modifier.testTag("settings_membership"),
        adaptiveHeight = true,
    )
    if (loggedIn != null) {
        TextButton(onClick = { showLogout = true }, modifier = Modifier.testTag("settings_logout")) {
            Text(stringResource(R.string.account_logout), style = AppTextStyles.Button, color = AppColors.AccentPrimary)
        }
    }
    if (showLogout && loggedIn != null) {
        VaultDialog(
            title = stringResource(R.string.account_logout_title),
            message = stringResource(R.string.account_logout_message),
            confirmText = stringResource(R.string.account_logout_confirm),
            dismissText = stringResource(R.string.account_logout_cancel),
            onConfirm = {
                showLogout = false
                onLogoutConfirmed()
            },
            onDismiss = { showLogout = false },
        )
    }
}
