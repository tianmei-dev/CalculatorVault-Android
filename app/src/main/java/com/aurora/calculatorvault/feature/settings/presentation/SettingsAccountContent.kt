package com.aurora.calculatorvault.feature.settings.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.WorkspacePremium
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
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.ui.component.VaultDialog
import com.aurora.calculatorvault.ui.component.VaultMainColors
import com.aurora.calculatorvault.ui.component.vaultMainPanel

@Composable
internal fun SettingsAccountContent(
    state: AccountUiState,
    onLogin: () -> Unit,
    onVipMembership: () -> Unit,
    onLogoutConfirmed: () -> Unit,
    onRefreshMembership: (() -> Unit)? = null,
) {
    var showLogout by rememberSaveable { mutableStateOf(false) }
    val loggedIn = state.account as? AccountStatus.WeChatLoggedIn
    MainSettingsRow(
        title = loggedIn?.displayNickname ?: stringResource(
            if (loggedIn == null) R.string.account_wechat_login else R.string.account_wechat_user),
        subtitle = stringResource(if (loggedIn == null) R.string.account_login_description else R.string.account_logged_in),
        icon = Icons.Rounded.Forum,
        onClick = if (loggedIn == null) onLogin else ({}),
        enabled = loggedIn == null,
        modifier = Modifier.testTag("settings_account").vaultMainPanel(),
        showDivider = false,
    )
    val membership = membershipText(state)
    val membershipSubtitle = buildString {
        append(stringResource(membership.subtitle, *membership.arguments.toTypedArray()))
        state.membershipSyncHint?.let { hint ->
            if (isNotEmpty()) append('\n')
            append(hint)
        }
    }
    MainSettingsRow(
        title = stringResource(membership.title),
        subtitle = membershipSubtitle,
        icon = Icons.Rounded.WorkspacePremium,
        onClick = onVipMembership,
        showDivider = false,
        modifier = Modifier.testTag("settings_membership").vaultMainPanel(emphasized = true),
        emphasized = true,
    )
    if (loggedIn != null && onRefreshMembership != null) {
        TextButton(
            onClick = onRefreshMembership,
            enabled = !state.membershipRefreshing,
            modifier = Modifier.testTag("settings_membership_refresh"),
        ) {
            Text(
                text = stringResource(
                    if (state.membershipRefreshing) {
                        R.string.account_membership_syncing
                    } else {
                        R.string.account_membership_refresh
                    },
                ),
                style = AppTextStyles.Button,
                color = if (state.membershipRefreshing) {
                    VaultMainColors.Highlight.copy(alpha = 0.5f)
                } else {
                    VaultMainColors.Highlight
                },
            )
        }
    }
    if (loggedIn != null) {
        TextButton(onClick = { showLogout = true }, modifier = Modifier.testTag("settings_logout")) {
            Text(stringResource(R.string.account_logout), style = AppTextStyles.Button, color = VaultMainColors.Highlight)
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
