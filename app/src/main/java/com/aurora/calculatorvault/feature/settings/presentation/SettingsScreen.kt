package com.aurora.calculatorvault.feature.settings.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.disguise.presentation.PageHeader
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultSettingItem
import com.aurora.calculatorvault.ui.layout.appPagePadding
import com.aurora.calculatorvault.ui.message.LocalAppMessageController

@Composable
fun SettingsScreen(
    onChangePassword: () -> Unit,
    onAbout: () -> Unit,
    onContactUs: () -> Unit,
    onPrivacyDocuments: () -> Unit,
) {
    val context = LocalContext.current
    val messageController = LocalAppMessageController.current
    val appSettingsFallbackMessage = stringResource(R.string.settings_app_detail_open_failed)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(appPagePadding()),
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
                title = stringResource(R.string.settings_permission_view),
                icon = VaultIcons.Security,
                onClick = {
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
            VaultSettingItem(
                title = stringResource(R.string.settings_about_us),
                icon = VaultIcons.Info,
                onClick = onAbout,
            )
            VaultSettingItem(
                title = stringResource(R.string.settings_contact_us),
                icon = VaultIcons.Mail,
                onClick = onContactUs,
            )
            VaultSettingItem(
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
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(
            text = title,
            style = AppTextStyles.Caption,
            color = AppColors.TextTertiary,
        )
        VaultCard(modifier = Modifier.fillMaxWidth(), content = content)
    }
}
