package com.aurora.calculatorvault.feature.hiddenapp.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.hiddenapp.data.AppIconProvider
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppError
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledApp
import com.aurora.calculatorvault.ui.component.VaultIconButton
import com.aurora.calculatorvault.ui.component.VaultLoadingIndicator
import com.aurora.calculatorvault.ui.component.VaultPrimaryButton
import com.aurora.calculatorvault.ui.component.VaultTopAppBar

@Composable
fun HiddenAppPickerScreen(
    viewModel: HiddenAppPickerViewModel,
    iconProvider: AppIconProvider,
    onBack: () -> Unit,
    onCompleted: (Int) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HiddenAppPickerEffect.Completed -> onCompleted(effect.addedCount)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        VaultTopAppBar(
            title = stringResource(R.string.hidden_app_picker_title),
            navigationIcon = {
                VaultIconButton(
                    icon = VaultIcons.Back,
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack,
                )
            },
            actions = {
                TextButton(
                    onClick = viewModel::saveSelection,
                    enabled = state.canSave,
                ) {
                    Text(
                        text = if (state.selectedPackages.isEmpty()) {
                            stringResource(R.string.hidden_app_complete)
                        } else {
                            stringResource(
                                R.string.hidden_app_complete_count,
                                state.selectedPackages.size,
                            )
                        },
                        style = AppTextStyles.Button,
                        color = if (state.canSave) {
                            AppColors.AccentPrimary
                        } else {
                            AppColors.TextDisabled
                        },
                    )
                }
            },
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            HiddenAppSearchField(
                query = state.query,
                onQueryChange = viewModel::updateQuery,
                placeholder = stringResource(R.string.hidden_app_search),
                enabled = !state.isSaving,
            )
            Text(
                text = stringResource(
                    R.string.hidden_app_selected_count,
                    state.selectedPackages.size,
                ),
                style = AppTextStyles.Caption,
                color = AppColors.TextTertiary,
            )
            if (
                state.error == HiddenAppError.SaveFailed ||
                state.error == HiddenAppError.LoadFailed
            ) {
                Text(
                    text = stringResource(
                        if (state.error == HiddenAppError.SaveFailed) {
                            R.string.hidden_app_save_failed
                        } else {
                            R.string.hidden_app_load_failed
                        },
                    ),
                    style = AppTextStyles.Caption,
                    color = AppColors.Error,
                )
            }
            PickerBody(
                state = state,
                iconProvider = iconProvider,
                onToggle = viewModel::toggleSelection,
                onRetry = viewModel::retryScan,
            )
            if (state.selectedPackages.isNotEmpty()) {
                VaultPrimaryButton(
                    text = stringResource(
                        R.string.hidden_app_complete_count,
                        state.selectedPackages.size,
                    ),
                    onClick = viewModel::saveSelection,
                    enabled = state.canSave,
                    modifier = Modifier.padding(bottom = AppSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.PickerBody(
    state: HiddenAppPickerUiState,
    iconProvider: AppIconProvider,
    onToggle: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.isLoading -> VaultLoadingIndicator(Modifier.fillMaxWidth())
        state.error == HiddenAppError.ScanFailed -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                Text(
                    stringResource(R.string.hidden_app_scan_failed),
                    style = AppTextStyles.Body,
                    color = AppColors.Error,
                )
                TextButton(onClick = onRetry) {
                    Text(
                        stringResource(R.string.hidden_app_retry_scan),
                        color = AppColors.AccentPrimary,
                    )
                }
            }
        }
        state.visibleApps.isEmpty() -> {
            Text(
                text = if (state.query.isBlank()) {
                    stringResource(R.string.hidden_app_no_available)
                } else {
                    stringResource(R.string.hidden_app_no_search_result)
                },
                modifier = Modifier.fillMaxWidth().padding(AppSpacing.xl),
                style = AppTextStyles.Body,
                color = AppColors.TextTertiary,
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                items(state.visibleApps, key = InstalledApp::packageName) { app ->
                    PickerAppRow(
                        app = app,
                        iconProvider = iconProvider,
                        isAdded = app.packageName in state.addedPackages,
                        isSelected = app.packageName in state.selectedPackages,
                        enabled = !state.isSaving,
                        onToggle = { onToggle(app.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerAppRow(
    app: InstalledApp,
    iconProvider: AppIconProvider,
    isAdded: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val selectable = enabled && !isAdded
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = selectable, onClick = onToggle)
            .padding(vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VaultInstalledAppIcon(
            packageName = app.packageName,
            appName = app.appName,
            iconProvider = iconProvider,
            modifier = Modifier.size(AppIconSize),
        )
        Spacer(Modifier.width(AppSpacing.md))
        Text(
            text = app.appName,
            modifier = Modifier.weight(1f),
            style = AppTextStyles.CardTitle,
            color = if (selectable) AppColors.TextPrimary else AppColors.TextSecondary,
            maxLines = 2,
        )
        if (isAdded) {
            Text(
                text = stringResource(R.string.hidden_app_already_added),
                style = AppTextStyles.Caption,
                color = AppColors.TextTertiary,
            )
        } else {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                enabled = selectable,
                colors = CheckboxDefaults.colors(
                    checkedColor = AppColors.AccentPrimary,
                    uncheckedColor = AppColors.TextTertiary,
                    checkmarkColor = AppColors.BackgroundPrimary,
                ),
            )
        }
    }
}

private val AppIconSize = 48.dp
