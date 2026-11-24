package com.aurora.calculatorvault.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aurora.calculatorvault.BuildConfig
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultIconButton
import com.aurora.calculatorvault.ui.component.VaultMainColors
import com.aurora.calculatorvault.ui.component.VaultTopAppBar
import com.aurora.calculatorvault.ui.component.useVaultDarkVisuals
import com.aurora.calculatorvault.ui.component.vaultSecondaryBackdrop
import com.aurora.calculatorvault.ui.layout.appPagePadding

@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .vaultSecondaryBackdrop()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        VaultTopAppBar(
            title = stringResource(R.string.settings_about_us),
            navigationIcon = {
                VaultIconButton(
                    icon = VaultIcons.Back,
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack,
                )
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(appPagePadding()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
        ) {
            VaultCard(modifier = Modifier.fillMaxWidth()) {
                AboutInfoRow(
                    label = stringResource(R.string.settings_about_app_name),
                    value = stringResource(R.string.app_name),
                )
                AboutInfoRow(
                    label = stringResource(R.string.settings_about_product_intro),
                    value = stringResource(R.string.settings_about_product_intro_text),
                )
                AboutInfoRow(
                    label = stringResource(R.string.settings_about_developer),
                    value = stringResource(R.string.developer_name),
                )
                AboutInfoRow(
                    label = stringResource(R.string.settings_about_version),
                    value = stringResource(R.string.settings_about_version_value, BuildConfig.VERSION_NAME),
                )
            }
        }
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
) {
    val dark = useVaultDarkVisuals()
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.padding(vertical = AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
    ) {
        Text(label, style = AppTextStyles.Caption, color = if (dark) VaultMainColors.TextSecondary else colors.onSurfaceVariant)
        Text(value, style = AppTextStyles.Body, color = if (dark) VaultMainColors.TextPrimary else colors.onSurface)
    }
}
