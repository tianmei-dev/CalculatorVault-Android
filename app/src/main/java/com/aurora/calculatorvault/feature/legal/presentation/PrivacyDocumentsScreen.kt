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
                documents.forEachIndexed { index, type ->
                    VaultSettingItem(
                        title = stringResource(type.titleRes),
                        icon = when (type) {
                            LegalDocumentType.PrivacyPolicy -> VaultIcons.Privacy
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
        }
    }
}
