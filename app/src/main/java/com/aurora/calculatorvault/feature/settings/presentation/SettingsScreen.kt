package com.aurora.calculatorvault.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.feature.disguise.presentation.PageHeader
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultSectionTitle
import com.aurora.calculatorvault.ui.component.VaultSettingItem

@Composable
fun SettingsScreen(
    onChangePassword: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        PageHeader(
            title = stringResource(R.string.tab_settings),
            description = stringResource(R.string.settings_description),
        )
        SettingsGroup(title = stringResource(R.string.security_group)) {
            VaultSettingItem(
                title = stringResource(R.string.change_password),
                icon = VaultIcons.Lock,
                onClick = onChangePassword,
            )
            VaultSettingItem(
                title = stringResource(R.string.forgot_password),
                icon = VaultIcons.Help,
                onClick = onForgotPassword,
            )
            VaultSettingItem(
                title = stringResource(R.string.auto_lock),
                subtitle = stringResource(R.string.auto_lock_enabled),
                icon = VaultIcons.Timer,
                onClick = {},
                enabled = false,
                showDivider = false,
            )
        }
        SettingsGroup(title = stringResource(R.string.data_group)) {
            VaultSettingItem(
                title = stringResource(R.string.storage_overview),
                subtitle = stringResource(R.string.coming_soon),
                icon = VaultIcons.Storage,
                onClick = {},
                enabled = false,
            )
            VaultSettingItem(
                title = stringResource(R.string.clear_cache),
                subtitle = stringResource(R.string.coming_soon),
                icon = VaultIcons.Files,
                onClick = {},
                enabled = false,
            )
            VaultSettingItem(
                title = stringResource(R.string.clear_recent_history),
                subtitle = stringResource(R.string.coming_soon),
                icon = VaultIcons.Timer,
                onClick = {},
                enabled = false,
                showDivider = false,
            )
        }
        SettingsGroup(title = stringResource(R.string.help_group)) {
            VaultSettingItem(
                title = stringResource(R.string.help_center),
                subtitle = stringResource(R.string.coming_soon),
                icon = VaultIcons.Help,
                onClick = {},
                enabled = false,
                showDivider = false,
            )
        }
        SettingsGroup(title = stringResource(R.string.about_group)) {
            VaultSettingItem(
                title = stringResource(R.string.about_app),
                subtitle = stringResource(R.string.version),
                icon = VaultIcons.Info,
                onClick = {},
                enabled = false,
                showDivider = false,
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        VaultSectionTitle(title)
        VaultCard(modifier = Modifier.fillMaxWidth(), content = content)
    }
}
