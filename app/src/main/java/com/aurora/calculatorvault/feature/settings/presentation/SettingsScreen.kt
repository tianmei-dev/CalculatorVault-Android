package com.aurora.calculatorvault.feature.settings.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.ui.component.VaultMainColors
import com.aurora.calculatorvault.ui.component.VaultMainHeader
import com.aurora.calculatorvault.ui.component.VaultMainIconTile
import com.aurora.calculatorvault.ui.component.vaultMainBackdrop
import com.aurora.calculatorvault.ui.component.vaultMainPanel
import com.aurora.calculatorvault.ui.layout.appPagePadding
import com.aurora.calculatorvault.ui.message.LocalAppMessageController

@Composable
fun SettingsScreen(
    onChangePassword: () -> Unit,
    onAbout: () -> Unit,
    onContactUs: () -> Unit,
    onPrivacyDocuments: () -> Unit,
    onPermissionSettingsClick: () -> Unit,
    onLogin: () -> Unit,
    onVipMembership: () -> Unit,
    onLogoutConfirmed: () -> Unit,
    accountState: AccountUiState = AccountUiState(),
    sessionMessage: String = "",
    onRetrySession: () -> Unit = {},
    sessionPending: Boolean = false,
    onRefreshMembership: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val messageController = LocalAppMessageController.current
    val appSettingsFallbackMessage = stringResource(R.string.settings_app_detail_open_failed)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .vaultMainBackdrop()
            .verticalScroll(rememberScrollState())
            .padding(appPagePadding(horizontal = AppSpacing.lg, top = AppSpacing.lg)),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        VaultMainHeader(
            title = stringResource(R.string.tab_settings),
            description = stringResource(R.string.settings_description),
        )
        SettingsGroup(title = stringResource(R.string.settings_account_group), panel = false) {
            SettingsAccountContent(
                state = accountState,
                onLogin = onLogin,
                onVipMembership = onVipMembership,
                onLogoutConfirmed = onLogoutConfirmed,
                onRefreshMembership = onRefreshMembership,
            )
            if (sessionMessage.isNotEmpty()) Text(sessionMessage, color = VaultMainColors.TextSecondary)
            if (sessionPending) {
                androidx.compose.material3.TextButton(onClick = onRetrySession) { Text("重新校验账号") }
                androidx.compose.material3.TextButton(onClick = onLogoutConfirmed) { Text("退出本地账号") }
            }
        }
        SettingsGroup(title = stringResource(R.string.security_group)) {
            MainSettingsRow(
                title = stringResource(R.string.change_password),
                icon = VaultIcons.Lock,
                onClick = onChangePassword,
            )
            MainSettingsRow(
                title = stringResource(R.string.settings_permission_view),
                icon = VaultIcons.Security,
                onClick = {
                    onPermissionSettingsClick()
                    val appDetailsIntent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    )
                    val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
                    try {
                        context.startActivity(appDetailsIntent)
                    } catch (_: ActivityNotFoundException) {
                        try {
                            context.startActivity(fallbackIntent)
                        } catch (_: ActivityNotFoundException) {
                            messageController.showError(appSettingsFallbackMessage)
                        }
                    }
                },
                showDivider = false,
            )
        }
        SettingsGroup(title = stringResource(R.string.about_group)) {
            MainSettingsRow(
                title = stringResource(R.string.settings_about_us),
                icon = VaultIcons.Info,
                onClick = onAbout,
            )
            MainSettingsRow(
                title = stringResource(R.string.settings_contact_us),
                icon = VaultIcons.Mail,
                onClick = onContactUs,
            )
            MainSettingsRow(
                title = stringResource(R.string.settings_privacy_documents),
                icon = VaultIcons.Privacy,
                onClick = onPrivacyDocuments,
                showDivider = false,
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    panel: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(
            text = title,
            style = AppTextStyles.SectionTitle,
            color = VaultMainColors.TextPrimary,
        )
        Column(
            modifier = if (panel) {
                Modifier.fillMaxWidth().vaultMainPanel()
            } else {
                Modifier.fillMaxWidth()
            },
            verticalArrangement = if (panel) Arrangement.Top else Arrangement.spacedBy(AppSpacing.xs),
            content = content,
        )
    }
}

@Composable
internal fun MainSettingsRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppDimensions.SettingItem)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            VaultMainIconTile(icon = icon, emphasized = emphasized, size = 42.dp)
            Box(
                modifier = Modifier.width(1.dp).height(28.dp)
                    .background(VaultMainColors.Border.copy(alpha = 0.45f)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = AppTextStyles.CardTitle.copy(fontWeight = FontWeight.SemiBold),
                    color = VaultMainColors.TextPrimary,
                )
                subtitle?.let { Text(it, style = AppTextStyles.BodySecondary, color = VaultMainColors.TextSecondary) }
            }
            if (enabled) {
                Icon(
                    imageVector = VaultIcons.Chevron,
                    contentDescription = null,
                    tint = VaultMainColors.Highlight,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (showDivider) HorizontalDivider(
            modifier = Modifier.padding(horizontal = AppSpacing.sm),
            color = VaultMainColors.Border.copy(alpha = 0.4f),
        )
    }
}
