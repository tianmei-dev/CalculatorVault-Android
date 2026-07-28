package com.aurora.calculatorvault.feature.hiddenapp.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.hiddenapp.data.AppIconProvider
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppError
import com.aurora.calculatorvault.ui.component.VaultDialog
import com.aurora.calculatorvault.ui.component.VaultEmptyState
import com.aurora.calculatorvault.ui.component.VaultLoadingIndicator
import com.aurora.calculatorvault.ui.component.VaultPrimaryButton
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HiddenAppScreen(
    viewModel: HiddenAppViewModel,
    iconProvider: AppIconProvider,
    onAddApps: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val removedMessage = stringResource(R.string.hidden_app_removed_success)

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            if (effect == HiddenAppEffect.Removed) onMessage(removedMessage)
        }
    }

    HiddenAppContent(
        state = state,
        iconProvider = iconProvider,
        onQueryChange = viewModel::updateQuery,
        onAddApps = onAddApps,
        onRequestRemoval = viewModel::requestRemoval,
        onRetryLoad = viewModel::retryLoad,
    )

    state.pendingRemoval?.let { app ->
        VaultDialog(
            title = stringResource(R.string.hidden_app_remove_title, app.appName),
            message = stringResource(R.string.hidden_app_remove_message),
            confirmText = stringResource(R.string.hidden_app_remove_confirm),
            dismissText = stringResource(R.string.cancel),
            onConfirm = viewModel::confirmRemoval,
            onDismiss = viewModel::cancelRemoval,
        )
    }
}

@Composable
private fun HiddenAppContent(
    state: HiddenAppUiState,
    iconProvider: AppIconProvider,
    onQueryChange: (String) -> Unit,
    onAddApps: () -> Unit,
    onRequestRemoval: (HiddenApp) -> Unit,
    onRetryLoad: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(
                text = stringResource(R.string.tab_hidden_app),
                style = AppTextStyles.PageTitle,
                color = AppColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.hidden_description),
                style = AppTextStyles.BodySecondary,
                color = AppColors.TextTertiary,
            )
        }
        HiddenAppSearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.hidden_app_search),
        )

        when {
            state.isLoading -> VaultLoadingIndicator(Modifier.weight(1f))
            state.error == HiddenAppError.LoadFailed -> {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    VaultEmptyState(
                        title = stringResource(R.string.hidden_app_load_failed),
                        description = stringResource(R.string.retry),
                        icon = VaultIcons.Info,
                    )
                    TextButton(onClick = onRetryLoad) {
                        Text(
                            text = stringResource(R.string.retry),
                            style = AppTextStyles.Button,
                            color = AppColors.AccentPrimary,
                        )
                    }
                }
            }
            state.apps.isEmpty() -> {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    VaultEmptyState(
                        title = stringResource(R.string.hidden_app_empty_title),
                        description = stringResource(R.string.hidden_app_empty_description),
                        icon = VaultIcons.Apps,
                    )
                    VaultPrimaryButton(
                        text = stringResource(R.string.add_app),
                        onClick = onAddApps,
                    )
                }
            }
            state.visibleApps.isEmpty() -> {
                VaultEmptyState(
                    title = stringResource(R.string.hidden_app_no_search_result),
                    description = stringResource(R.string.hidden_app_search),
                    modifier = Modifier.weight(1f),
                    icon = VaultIcons.Search,
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                ) {
                    items(state.visibleApps, key = HiddenApp::packageName) { app ->
                        HiddenAppGridItem(
                            app = app,
                            iconProvider = iconProvider,
                            onRequestRemoval = { onRequestRemoval(app) },
                        )
                    }
                }
                VaultPrimaryButton(
                    text = stringResource(R.string.add_app),
                    onClick = onAddApps,
                    modifier = Modifier.padding(bottom = AppSpacing.sm),
                )
            }
        }

        if (state.error == HiddenAppError.RemoveFailed) {
            Text(
                text = stringResource(R.string.hidden_app_remove_failed),
                style = AppTextStyles.Caption,
                color = AppColors.Error,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun HiddenAppGridItem(
    app: HiddenApp,
    iconProvider: AppIconProvider,
    onRequestRemoval: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        Box {
            VaultInstalledAppIcon(
                packageName = app.packageName,
                appName = app.appName,
                iconProvider = iconProvider,
            )
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = VaultIcons.More,
                        contentDescription = stringResource(
                            R.string.hidden_app_remove_description,
                            app.appName,
                        ),
                        tint = AppColors.TextSecondary,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = AppColors.SurfaceElevated,
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.hidden_app_remove_action),
                                style = AppTextStyles.Body,
                                color = AppColors.TextPrimary,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRequestRemoval()
                        },
                    )
                }
            }
        }
        Text(
            text = app.appName,
            style = AppTextStyles.Caption,
            color = AppColors.TextPrimary,
            maxLines = 2,
        )
        if (!app.isInstalled) {
            Text(
                text = stringResource(R.string.hidden_app_uninstalled),
                style = AppTextStyles.Caption,
                color = AppColors.Error,
            )
        }
    }
}
