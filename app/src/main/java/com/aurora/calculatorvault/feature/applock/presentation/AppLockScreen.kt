package com.aurora.calculatorvault.feature.applock.presentation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
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
import com.aurora.calculatorvault.ui.component.VaultEmptyState
import com.aurora.calculatorvault.ui.component.VaultLoadingIndicator
import com.aurora.calculatorvault.ui.component.VaultMainColors
import com.aurora.calculatorvault.ui.component.VaultMainHeader
import com.aurora.calculatorvault.ui.component.VaultMainIconTile
import com.aurora.calculatorvault.ui.component.VaultVisualAlertDialog
import com.aurora.calculatorvault.ui.component.vaultMainBackdrop
import com.aurora.calculatorvault.ui.component.vaultMainPanel
import com.aurora.calculatorvault.ui.layout.appPagePadding
import com.aurora.calculatorvault.ui.message.AppMessage
import com.aurora.calculatorvault.ui.message.AppMessageType

@Composable
fun AppLockScreen(
    viewModel: AppLockViewModel = viewModel(),
    iconProvider: AppIconProvider,
    onMessage: (AppMessage) -> Unit,
    onNavigateToVip: () -> Unit = {},
    onGuideTargetBoundsChanged: (Rect) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val notificationState by viewModel.notificationState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationWarning = stringResource(R.string.app_lock_notification_unavailable)
    val notificationSettingsFailed = stringResource(R.string.app_lock_notification_settings_failed)
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onNotificationResult(granted)
        if (!granted) onMessage(AppMessage(notificationWarning, AppMessageType.Warning))
    }
    fun openNotificationSettings() {
        try { context.startActivity(viewModel.notificationSettingsIntent()) }
        catch (_: RuntimeException) {
            try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null))) }
            catch (_: RuntimeException) { onMessage(AppMessage(notificationSettingsFailed, AppMessageType.Warning)) }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val usageHelper = UsageAccessPermissionHelper(
        context.applicationContext,
        consent = (context.applicationContext as CalculatorVaultApp).privacyConsent,
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
                AppLockEffect.NavigateToVip -> onNavigateToVip()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_lock_screen")
            .vaultMainBackdrop()
            .padding(appPagePadding(horizontal = AppSpacing.lg, top = AppSpacing.lg)),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        item {
            VaultMainHeader(
                title = stringResource(R.string.tab_app_lock),
                description = stringResource(R.string.app_lock_formal_subtitle),
                modifier = Modifier.padding(bottom = AppSpacing.xxs),
            )
        }
        item {
            val reportGuideTarget by rememberUpdatedState(onGuideTargetBoundsChanged)
            DisposableEffect(Unit) {
                onDispose { reportGuideTarget(Rect.Zero) }
            }
            AppLockStatusCard(
                state = state,
                modifier = Modifier.onGloballyPositioned {
                    onGuideTargetBoundsChanged(it.boundsInRoot())
                },
            )
        }
        if (state.lockedCount > 0 && notificationState.showSettingsHint) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .testTag("app_lock_notification_hint")
                        .vaultMainPanel()
                        .padding(AppSpacing.md),
                ) {
                    Text(notificationWarning, style = AppTextStyles.BodySecondary, color = VaultMainColors.TextSecondary)
                    TextButton(onClick = { openNotificationSettings() }) {
                        Text(stringResource(R.string.app_lock_notification_settings), color = VaultMainColors.Highlight)
                    }
                }
            }
        }
        item {
            HiddenAppSearchField(
                query = state.query,
                onQueryChange = viewModel::updateQuery,
                placeholder = stringResource(R.string.app_lock_search_placeholder),
                surfaceModifier = Modifier
                    .background(VaultMainColors.Card, AppShapes.Medium)
                    .border(1.dp, VaultMainColors.Border, AppShapes.Medium),
                hintColor = VaultMainColors.TextSecondary,
                onClear = viewModel::clearQuery,
            )
        }
        item {
            Text(
                text = stringResource(R.string.app_lock_app_list_title),
                modifier = Modifier.padding(top = AppSpacing.xxs),
                style = AppTextStyles.SectionTitle,
                color = VaultMainColors.TextPrimary,
            )
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

    if (notificationState.showExplanation) {
        VaultVisualAlertDialog(
            onDismissRequest = viewModel::dismissNotificationExplanation,
            title = { Text(stringResource(R.string.app_lock_notification_title)) },
            text = { Text(stringResource(R.string.app_lock_notification_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    val allowed = (context.applicationContext as CalculatorVaultApp).privacyConsent.isAllowedNow()
                    if (allowed && Build.VERSION.SDK_INT >= 33 && viewModel.confirmNotificationRequest()) {
                        try { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        catch (_: RuntimeException) {
                            viewModel.onNotificationResult(false)
                            onMessage(AppMessage(notificationWarning, AppMessageType.Warning))
                        }
                    } else viewModel.dismissNotificationExplanation()
                }) { Text(stringResource(R.string.app_lock_notification_request)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissNotificationExplanation) {
                    Text(stringResource(R.string.app_lock_notification_later))
                }
            },
        )
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
private fun AppLockStatusCard(
    state: AppLockUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .vaultMainPanel(emphasized = true, shape = AppShapes.Large)
            .padding(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        VaultMainIconTile(
            icon = when (state.protectionStatus) {
                AppLockProtectionStatus.Protecting -> VaultIcons.Success
                AppLockProtectionStatus.NeedsPermission -> VaultIcons.Warning
                AppLockProtectionStatus.NotRunning -> VaultIcons.Info
                AppLockProtectionStatus.NoLockedApps -> VaultIcons.Lock
            },
            emphasized = true,
            size = 48.dp,
            iconSize = 27.dp,
        )
        Spacer(
            Modifier.width(1.dp).height(48.dp).background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, VaultMainColors.Highlight.copy(alpha = 0.36f), Color.Transparent),
                ),
            ),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
        ) {
            Text(
                text = stringResource(R.string.app_lock_status_title),
                style = AppTextStyles.SectionTitle,
                fontSize = 17.sp,
                color = VaultMainColors.TextPrimary,
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
                fontSize = 13.sp,
                color = when (state.protectionStatus) {
                    AppLockProtectionStatus.Protecting -> AppColors.Success
                    AppLockProtectionStatus.NeedsPermission -> AppColors.Warning
                    else -> VaultMainColors.TextSecondary
                },
            )
            Text(
                text = stringResource(
                    R.string.app_lock_permission_summary,
                    stringResource(if (state.hasUsageAccess) R.string.app_lock_permission_on else R.string.app_lock_permission_off),
                    stringResource(if (state.hasOverlayPermission) R.string.app_lock_permission_on else R.string.app_lock_permission_off),
                ),
                style = AppTextStyles.Caption,
                fontSize = 11.sp,
                color = VaultMainColors.TextSecondary,
            )
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
    Row(
        modifier = Modifier.fillMaxWidth().vaultMainPanel()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        VaultInstalledAppIcon(
            packageName = app.packageName,
            appName = app.appName,
            iconProvider = iconProvider,
            modifier = Modifier.size(44.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = AppTextStyles.CardTitle.copy(fontWeight = FontWeight.SemiBold),
                fontSize = 15.sp,
                color = VaultMainColors.TextPrimary,
            )
            Text(
                text = app.packageName,
                style = AppTextStyles.Caption,
                fontSize = 11.sp,
                color = VaultMainColors.TextSecondary,
            )
        }
        Switch(
            checked = app.locked,
            onCheckedChange = onCheckedChange,
            enabled = !isUpdating,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VaultMainColors.TextPrimary,
                checkedTrackColor = VaultMainColors.Accent,
                uncheckedThumbColor = VaultMainColors.Highlight,
                uncheckedTrackColor = Color(0xFF3C3458),
                uncheckedBorderColor = Color(0xFF635582),
            ),
        )
    }
}

@Composable
private fun PermissionRequiredDialog(
    target: AppLockPermissionTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    VaultVisualAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.app_lock_permission_required_title),
                style = AppTextStyles.SectionTitle,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = stringResource(R.string.app_lock_permission_required_message),
                    style = AppTextStyles.Body,
                )
                Text(
                    text = stringResource(
                        when (target) {
                            AppLockPermissionTarget.UsageAccess -> R.string.app_lock_usage_access_reason
                            AppLockPermissionTarget.Overlay -> R.string.app_lock_overlay_reason
                        },
                    ),
                    style = AppTextStyles.BodySecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.app_lock_go_settings),
                    style = AppTextStyles.Button,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = AppTextStyles.Button,
                )
            }
        },
    )
}
