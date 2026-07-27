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
import com.aurora.calculatorvault.ui.component.VaultSwitchSettingItem

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        PageHeader(
            title = stringResource(R.string.tab_settings),
            description = stringResource(R.string.settings_description),
        )
        SettingsGroup(title = stringResource(R.string.security_group)) {
            VaultSwitchSettingItem(
                title = stringResource(R.string.auto_lock),
                subtitle = stringResource(R.string.setting_placeholder),
                checked = false,
                onCheckedChange = {},
            )
            VaultSwitchSettingItem(
                title = stringResource(R.string.secure_screen),
                subtitle = stringResource(R.string.setting_placeholder),
                checked = false,
                onCheckedChange = {},
            )
        }
        SettingsGroup(title = stringResource(R.string.data_group)) {
            VaultSettingItem(
                title = stringResource(R.string.storage_overview),
                subtitle = stringResource(R.string.data_not_configured),
                icon = VaultIcons.Storage,
                onClick = {},
            )
            VaultSettingItem(
                title = stringResource(R.string.data_backup),
                subtitle = stringResource(R.string.setting_placeholder),
                icon = VaultIcons.Files,
                onClick = {},
                showDivider = false,
            )
        }
        SettingsGroup(title = stringResource(R.string.help_group)) {
            VaultSettingItem(
                title = stringResource(R.string.help_center),
                icon = VaultIcons.Help,
                onClick = {},
            )
            VaultSettingItem(
                title = stringResource(R.string.privacy_policy),
                icon = VaultIcons.Privacy,
                onClick = {},
                showDivider = false,
            )
        }
        SettingsGroup(title = stringResource(R.string.about_group)) {
            VaultSettingItem(
                title = stringResource(R.string.about_app),
                subtitle = stringResource(R.string.version),
                icon = VaultIcons.Info,
                onClick = {},
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
