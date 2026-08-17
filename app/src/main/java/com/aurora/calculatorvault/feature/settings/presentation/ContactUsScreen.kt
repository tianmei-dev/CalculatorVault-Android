package com.aurora.calculatorvault.feature.settings.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultIconButton
import com.aurora.calculatorvault.ui.component.VaultSettingItem
import com.aurora.calculatorvault.ui.component.VaultTopAppBar
import com.aurora.calculatorvault.ui.layout.appPagePadding
import com.aurora.calculatorvault.ui.message.LocalAppMessageController

@Composable
fun ContactUsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val messageController = LocalAppMessageController.current
    val email = stringResource(R.string.contact_email)
    val emailCopied = stringResource(R.string.contact_email_copied)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        VaultTopAppBar(
            title = stringResource(R.string.settings_contact_us),
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
                VaultSettingItem(
                    title = stringResource(R.string.settings_about_developer),
                    subtitle = stringResource(R.string.developer_name),
                    icon = VaultIcons.Info,
                    onClick = {},
                    enabled = false,
                    showChevron = false,
                )
                VaultSettingItem(
                    title = stringResource(R.string.settings_contact_email),
                    subtitle = email,
                    icon = VaultIcons.Mail,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            clipboardManager.setText(AnnotatedString(email))
                            messageController.showInfo(emailCopied)
                        }
                    },
                    showDivider = false,
                )
            }
        }
    }
}
