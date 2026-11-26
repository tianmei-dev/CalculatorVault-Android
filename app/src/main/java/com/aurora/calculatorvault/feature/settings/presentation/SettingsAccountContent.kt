package com.aurora.calculatorvault.feature.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.ui.component.VaultMainIconTile
import com.aurora.calculatorvault.ui.component.vaultGradientAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Text
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
    Row(
        modifier = Modifier.fillMaxWidth().testTag("settings_account").vaultMainPanel()
            .padding(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        VaultMainIconTile(Icons.Rounded.Forum, size = 42.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.settings_wechat_account_title),
                style = AppTextStyles.CardTitle,
                color = VaultMainColors.TextPrimary,
            )
            Text(stringResource(if (loggedIn == null) R.string.settings_account_signed_out else R.string.account_logged_in),
                style = AppTextStyles.BodySecondary, color = VaultMainColors.TextSecondary)
            loggedIn?.displayNickname?.let {
                Text(it, style = AppTextStyles.BodySecondary, color = VaultMainColors.TextSecondary)
            }
        }
        Button(
            onClick = if (loggedIn == null) onLogin else ({ showLogout = true }),
            shape = CircleShape,
            modifier = Modifier.testTag(if (loggedIn == null) "settings_login" else "settings_logout")
                .vaultGradientAction(shape = CircleShape),
            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        ) {
            Text(stringResource(if (loggedIn == null) R.string.settings_account_login else R.string.account_logout),
                style = AppTextStyles.Button, color = VaultMainColors.TextPrimary)
        }
    }
    val membership = membershipText(state)
    val adminActive = state.visibleMembership is MembershipStatus.AdminFullAccess
    // Presentation only: consume the already selected visible membership and existing duration text.
    val title = when (val status = state.visibleMembership) {
        MembershipStatus.AdminFullAccess -> R.string.admin_membership_title
        MembershipStatus.NotSubscribed, MembershipStatus.TrialExpired -> R.string.account_open_vip
        is MembershipStatus.VipActive -> when (status.planType) {
            com.aurora.calculatorvault.feature.membership.domain.VipPlan.Monthly -> R.string.settings_membership_monthly
            com.aurora.calculatorvault.feature.membership.domain.VipPlan.Annual -> R.string.settings_membership_annual
            else -> membership.title
        }
        MembershipStatus.VipPermanent -> R.string.settings_membership_lifetime
        else -> membership.title
    }
    val detail = when {
        adminActive -> stringResource(R.string.admin_membership_detail)
        state.visibleMembership == MembershipStatus.NotSubscribed ||
            state.visibleMembership == MembershipStatus.TrialExpired ->
            stringResource(R.string.settings_membership_free_detail)
        else -> stringResource(membership.subtitle, *membership.arguments.toTypedArray())
    }
    Row(
        modifier = Modifier.fillMaxWidth().testTag("settings_membership").vaultMainPanel(emphasized = true)
            .then(if (adminActive) Modifier else Modifier.clickable(onClick = onVipMembership))
            .padding(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        VaultMainIconTile(Icons.Rounded.WorkspacePremium, emphasized = true, size = 42.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(
                text = stringResource(R.string.settings_membership_center),
                style = AppTextStyles.CardTitle,
                color = VaultMainColors.TextPrimary,
            )
            Text(stringResource(title), style = AppTextStyles.CardTitle, color = VaultMainColors.Highlight)
            Text(detail, style = AppTextStyles.BodySecondary, color = VaultMainColors.TextSecondary)
            state.membershipSyncHint?.let {
                Text(it, style = AppTextStyles.BodySecondary, color = VaultMainColors.TextSecondary)
            }
        }
        if (!adminActive) {
            Icon(Icons.Rounded.ChevronRight, null, tint = VaultMainColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
    if (loggedIn != null && onRefreshMembership != null) {
        Box(Modifier.fillMaxWidth().padding(top = AppSpacing.xs), contentAlignment = Alignment.Center) {
            OutlinedButton(
                onClick = onRefreshMembership, enabled = !state.membershipRefreshing, shape = CircleShape,
                modifier = Modifier.testTag("settings_membership_refresh").vaultGradientAction(
                    enabled = !state.membershipRefreshing, secondary = true, shape = CircleShape),
            ) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(AppSpacing.xs))
                Text(stringResource(if (state.membershipRefreshing) R.string.account_membership_syncing else R.string.account_membership_refresh),
                    style = AppTextStyles.Button, color = VaultMainColors.Highlight)
            }
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
