package com.aurora.calculatorvault.feature.legal.presentation

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.core.privacy.PrivacyWithdrawalState
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.ui.message.LocalAppMessageController
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultIconButton
import com.aurora.calculatorvault.ui.component.VaultSettingItem
import com.aurora.calculatorvault.ui.component.VaultTopAppBar
import com.aurora.calculatorvault.ui.layout.appPagePadding

@Composable
fun PrivacyDocumentsScreen(
    onBack: () -> Unit,
    onOpenDocument: (LegalDocumentType) -> Unit,
) {
    val app = LocalContext.current.applicationContext as CalculatorVaultApp
    val controller = app.privacyWithdrawalController
    val withdrawalState by controller.state.collectAsState()
    val saving = withdrawalState == PrivacyWithdrawalState.Saving
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    val messages = LocalAppMessageController.current
    val failureMessage = stringResource(R.string.privacy_withdraw_failed)
    LaunchedEffect(withdrawalState) {
        if (withdrawalState == PrivacyWithdrawalState.Failed) messages.showError(failureMessage)
    }
    BackHandler(enabled = saving) {}
    val documents = LegalDocumentType.entries
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        VaultTopAppBar(
            title = stringResource(R.string.settings_privacy_documents),
            navigationIcon = {
                VaultIconButton(
                    icon = VaultIcons.Back,
                    contentDescription = stringResource(R.string.back),
                    onClick = { if (!saving) onBack() },
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
                documents.forEachIndexed { index, type ->
                    VaultSettingItem(
                        title = stringResource(type.titleRes),
                        icon = when (type) {
                            LegalDocumentType.PrivacyPolicy -> VaultIcons.Privacy
                            LegalDocumentType.ChildrenPrivacyPolicy -> VaultIcons.Security
                            LegalDocumentType.UserAgreement -> VaultIcons.Files
                            LegalDocumentType.PersonalInformationList -> VaultIcons.Info
                            LegalDocumentType.ThirdPartySdkList -> VaultIcons.Apps
                            LegalDocumentType.PermissionDescription -> VaultIcons.Security
                        },
                        onClick = { onOpenDocument(type) },
                        showDivider = index != documents.lastIndex,
                    )
                }
            }
            Text(
                text = stringResource(R.string.privacy_management),
                modifier = Modifier.padding(top = AppSpacing.lg),
                color = AppColors.TextSecondary,
            )
            VaultCard(modifier = Modifier.fillMaxWidth()) {
                VaultSettingItem(
                    title = stringResource(R.string.privacy_withdraw_title),
                    icon = VaultIcons.Privacy,
                    showDivider = false,
                    enabled = !saving,
                    onClick = { showConfirmation = true },
                )
            }
        }
    }
    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!saving) showConfirmation = false },
            title = { Text(stringResource(R.string.privacy_withdraw_title)) },
            text = { Text(stringResource(R.string.privacy_withdraw_message)) },
            confirmButton = {
                TextButton(onClick = controller::withdraw, enabled = !saving) {
                    Text(stringResource(R.string.privacy_withdraw_confirm), color = AppColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }, enabled = !saving) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
