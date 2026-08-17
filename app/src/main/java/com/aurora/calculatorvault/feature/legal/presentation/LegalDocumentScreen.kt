package com.aurora.calculatorvault.feature.legal.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.legal.data.LegalDocumentRepository
import com.aurora.calculatorvault.ui.component.VaultIconButton
import com.aurora.calculatorvault.ui.component.VaultLoadingIndicator
import com.aurora.calculatorvault.ui.component.VaultTopAppBar
import com.aurora.calculatorvault.ui.layout.appPagePadding

@Composable
fun LegalDocumentScreen(
    type: LegalDocumentType,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { LegalDocumentRepository(context) }
    var content by remember(type) { mutableStateOf<String?>(null) }
    var loadFailed by remember(type) { mutableStateOf(false) }

    LaunchedEffect(type) {
        loadFailed = false
        content = null
        repository.read(type)
            .onSuccess { content = it }
            .onFailure { loadFailed = true }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        VaultTopAppBar(
            title = stringResource(type.titleRes),
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
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            when {
                loadFailed -> Text(
                    text = stringResource(R.string.legal_document_load_failed),
                    style = AppTextStyles.Body,
                    color = AppColors.Error,
                )
                content == null -> VaultLoadingIndicator()
                else -> Text(
                    text = content.orEmpty(),
                    style = AppTextStyles.Body,
                    color = AppColors.TextSecondary,
                )
            }
        }
    }
}
