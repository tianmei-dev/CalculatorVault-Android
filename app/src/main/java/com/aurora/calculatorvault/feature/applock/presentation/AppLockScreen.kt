package com.aurora.calculatorvault.feature.applock.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.applock.domain.AppLockPermissionTarget
import com.aurora.calculatorvault.feature.applock.domain.AppLockProtectionStatus
import com.aurora.calculatorvault.feature.applock.domain.LockableApp
import com.aurora.calculatorvault.feature.applock.domain.OverlayPermissionHelper
import com.aurora.calculatorvault.feature.applock.domain.UsageAccessPermissionHelper
import com.aurora.calculatorvault.feature.hiddenapp.data.AppIconProvider
import com.aurora.calculatorvault.feature.hiddenapp.presentation.HiddenAppSearchField
import com.aurora.calculatorvault.feature.hiddenapp.presentation.VaultInstalledAppIcon
import com.aurora.calculatorvault.feature.disguise.presentation.PageHeader
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultEmptyState
import com.aurora.calculatorvault.ui.component.VaultLoadingIndicator
import com.aurora.calculatorvault.ui.component.VaultSectionTitle
import com.aurora.calculatorvault.ui.layout.appPagePadding
import com.aurora.calculatorvault.ui.message.AppMessage
import com.aurora.calculatorvault.ui.message.AppMessageType

@Composable
fun AppLockScreen(
    viewModel: AppLockViewModel = viewModel(),
    iconProvider: AppIconProvider,
    onMessage: (AppMessage) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val usageHelper = UsageAccessPermissionHelper(
        androidx.compose.ui.platform.LocalContext.current.applicationContext,
    )
    val overlayHelper = OverlayPermissionHelper(
        androidx.compose.ui.platform.LocalContext.current.applicationContext,
    )
    val saveFailedMessage = stringResource(R.string.app_lock_save_failed)
    val rejectedMessage = stringResource(R.string.app_lock_package_rejected)

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AppLockEffect.SaveFailed -> onMessage(
                    AppMessage(saveFailedMessage, AppMessageType.Error),
                )
                AppLockEffect.PackageRejected -> onMessage(
                    AppMessage(rejectedMessage, AppMessageType.Warning),
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_lock_screen")
            .padding(appPagePadding()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        item {
            PageHeader(
                title = stringResource(R.string.tab_app_lock),
                description = stringResource(R.string.app_lock_formal_subtitle),
            )
        }
        item {
            AppLockStatusCard(state)
        }
        item {
            HiddenAppSearchField(
                query = state.query,
                onQueryChange = viewModel::updateQuery,
                placeholder = stringResource(R.string.app_lock_search_placeholder),
                onClear = viewModel::clearQuery,
            )
        }
        item {
            VaultSectionTitle(stringResource(R.string.app_lock_app_list_title))
        }
        when {
            state.isLoading -> item { VaultLoadingIndicator(Modifier.fillMaxWidth()) }
            state.error == AppLockError.LoadFailed -> item {
                VaultEmptyState(
                    title = stringResource(R.string.app_lock_load_failed),
                    description = stringResource(R.string.app_lock_retry_load_hint),
                    icon = VaultIcons.Warning,
                )
            }
            state.visibleApps.isEmpty() -> item {
                VaultEmptyState(
                    title = stringResource(R.string.app_lock_empty_title),
                    description = stringResource(R.string.app_lock_empty_description),
                    icon = VaultIcons.Lock,
                )
            }
            else -> items(
                items = state.visibleApps,
                key = LockableApp::packageName,
            ) { app ->
                AppLockAppRow(
                    app = app,
                    iconProvider = iconProvider,
                    isUpdating = state.updatingPackage == app.packageName,
                    onCheckedChange = { locked -> viewModel.toggleLock(app, locked) },
                )
            }
        }
    }

    state.pendingPermissionTarget?.let { target ->
        PermissionRequiredDialog(
            target = target,
            onConfirm = {
                viewModel.dismissPermissionPrompt()
                when (target) {
                    AppLockPermissionTarget.UsageAccess -> usageHelper.openUsageAccessSettings()
                    AppLockPermissionTarget.Overlay -> overlayHelper.openOverlayPermissionSettings()
                }
            },
            onDismiss = viewModel::dismissPermissionPrompt,
        )
    }
}

@Composable
private fun AppLockStatusCard(state: AppLockUiState) {
    VaultCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (state.protectionStatus) {
                    AppLockProtectionStatus.Protecting -> VaultIcons.Success
                    AppLockProtectionStatus.NeedsPermission -> VaultIcons.Warning
                    AppLockProtectionStatus.NotRunning -> VaultIcons.Info
                    AppLockProtectionStatus.NoLockedApps -> VaultIcons.Lock
                },
                contentDescription = null,
                tint = when (state.protectionStatus) {
                    AppLockProtectionStatus.Protecting -> AppColors.Success
                    AppLockProtectionStatus.NeedsPermission -> AppColors.Warning
                    AppLockProtectionStatus.NotRunning -> AppColors.TextSecondary
                    AppLockProtectionStatus.NoLockedApps -> AppColors.TextSecondary
                },
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(AppSpacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                Text(
                    text = stringResource(R.string.app_lock_status_title),
                    style = AppTextStyles.CardTitle,
                    color = AppColors.TextPrimary,
                )
                Text(
                    text = stringResource(
                        when (state.protectionStatus) {
                            AppLockProtectionStatus.Protecting -> R.string.app_lock_status_protecting
                            AppLockProtectionStatus.NeedsPermission -> R.string.app_lock_status_needs_permission
                            AppLockProtectionStatus.NotRunning -> R.string.app_lock_status_not_running
                            AppLockProtectionStatus.NoLockedApps -> R.string.app_lock_status_no_locked_apps
                        },
                        state.lockedCount,
                    ),
                    style = AppTextStyles.BodySecondary,
                    color = AppColors.TextSecondary,
                )
                Text(
                    text = stringResource(
                        R.string.app_lock_permission_summary,
                        stringResource(if (state.hasUsageAccess) R.string.app_lock_permission_on else R.string.app_lock_permission_off),
                        stringResource(if (state.hasOverlayPermission) R.string.app_lock_permission_on else R.string.app_lock_permission_off),
                    ),
                    style = AppTextStyles.Caption,
                    color = AppColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun AppLockAppRow(
    app: LockableApp,
    iconProvider: AppIconProvider,
    isUpdating: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    VaultCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VaultInstalledAppIcon(
                packageName = app.packageName,
                appName = app.appName,
                iconProvider = iconProvider,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(AppSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = AppTextStyles.CardTitle,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    text = app.packageName,
                    style = AppTextStyles.Caption,
                    color = AppColors.TextTertiary,
                    maxLines = 1,
                )
            }
            Box(contentAlignment = Alignment.Center) {
                Switch(
                    checked = app.locked,
                    onCheckedChange = onCheckedChange,
                    enabled = !isUpdating,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppColors.TextPrimary,
                        checkedTrackColor = AppColors.AccentSecondary,
                        uncheckedThumbColor = AppColors.TextTertiary,
                        uncheckedTrackColor = AppColors.SurfaceElevated,
                        uncheckedBorderColor = AppColors.BorderSubtle,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PermissionRequiredDialog(
    target: AppLockPermissionTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.app_lock_permission_required_title),
                style = AppTextStyles.SectionTitle,
                color = AppColors.TextPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = stringResource(R.string.app_lock_permission_required_message),
                    style = AppTextStyles.Body,
                    color = AppColors.TextSecondary,
                )
                Text(
                    text = stringResource(
                        when (target) {
                            AppLockPermissionTarget.UsageAccess -> R.string.app_lock_usage_access_reason
                            AppLockPermissionTarget.Overlay -> R.string.app_lock_overlay_reason
                        },
                    ),
                    style = AppTextStyles.Caption,
                    color = AppColors.TextTertiary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.app_lock_go_settings),
                    style = AppTextStyles.Button,
                    color = AppColors.AccentPrimary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = AppTextStyles.Button,
                    color = AppColors.TextSecondary,
                )
            }
        },
        containerColor = AppColors.SurfaceElevated,
    )
}
