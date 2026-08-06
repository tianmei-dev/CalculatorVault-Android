package com.aurora.calculatorvault.feature.disguise.shortcut

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.core.security.SecureScreenEffect
import com.aurora.calculatorvault.ui.component.VaultLoadingIndicator
import com.aurora.calculatorvault.ui.component.VaultNumericKeypad
import com.aurora.calculatorvault.ui.component.VaultPasswordDots
import com.aurora.calculatorvault.ui.component.VaultPrimaryButton
import com.aurora.calculatorvault.ui.component.VaultSecondaryButton
import com.aurora.calculatorvault.ui.layout.AppLayout

@Composable
fun DisguiseShortcutEntryScreen(
    state: DisguiseShortcutEntryState,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onOpenCalculator: () -> Unit,
) {
    SecureScreenEffect()
    BackHandler(onBack = onCancel)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BackgroundPrimary)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.lg),
    ) {
        when (state) {
            DisguiseShortcutEntryState.Resolving,
            DisguiseShortcutEntryState.VerifyingPassword,
            DisguiseShortcutEntryState.LaunchingTarget,
            -> LoadingContent(state)
            is DisguiseShortcutEntryState.AwaitingPassword -> PasswordContent(
                state = state,
                onDigit = onDigit,
                onDelete = onDelete,
                onClear = onClear,
                onConfirm = onConfirm,
                onCancel = onCancel,
            )
            else -> ErrorContent(
                state = state,
                onClose = onCancel,
                onOpenCalculator = onOpenCalculator,
            )
        }
    }
}

@Composable
private fun PasswordContent(
    state: DisguiseShortcutEntryState.AwaitingPassword,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        SecurityIcon()
        Text(
            text = stringResource(R.string.disguise_shortcut_security_title),
            style = AppTextStyles.PageTitle,
            color = AppColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.disguise_shortcut_security_description),
            style = AppTextStyles.Body,
            color = AppColors.TextSecondary,
        )
        VaultPasswordDots(enteredLength = state.enteredLength)
        if (state.passwordIncorrect) {
            Text(
                text = stringResource(R.string.disguise_shortcut_password_incorrect),
                style = AppTextStyles.BodySecondary,
                color = AppColors.Error,
            )
        }
        TextButton(onClick = onClear, enabled = state.enteredLength > 0) {
            Text(
                text = stringResource(R.string.clear_action),
                style = AppTextStyles.Button,
                color = if (state.enteredLength > 0) AppColors.TextSecondary else AppColors.TextDisabled,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        VaultNumericKeypad(onDigit = onDigit, onDelete = onDelete)
        VaultPrimaryButton(
            text = stringResource(R.string.confirm),
            enabled = state.enteredLength in 4..8,
            onClick = onConfirm,
        )
        VaultSecondaryButton(text = stringResource(R.string.cancel), onClick = onCancel)
    }
}

@Composable
private fun LoadingContent(state: DisguiseShortcutEntryState) {
    val message = when (state) {
        DisguiseShortcutEntryState.VerifyingPassword -> R.string.disguise_shortcut_verifying
        DisguiseShortcutEntryState.LaunchingTarget -> R.string.disguise_shortcut_opening
        else -> R.string.loading
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VaultLoadingIndicator()
        Text(
            text = stringResource(message),
            modifier = Modifier.padding(top = AppSpacing.md),
            style = AppTextStyles.Body,
            color = AppColors.TextSecondary,
        )
    }
}

@Composable
private fun ErrorContent(
    state: DisguiseShortcutEntryState,
    onClose: () -> Unit,
    onOpenCalculator: () -> Unit,
) {
    val copy = errorCopy(state)
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SecurityIcon()
        Text(
            text = stringResource(copy.first),
            modifier = Modifier.padding(top = AppSpacing.lg),
            style = AppTextStyles.PageTitle,
            color = AppColors.TextPrimary,
        )
        Text(
            text = stringResource(copy.second),
            modifier = Modifier.padding(top = AppSpacing.sm),
            style = AppTextStyles.Body,
            color = AppColors.TextSecondary,
        )
        VaultPrimaryButton(
            text = stringResource(R.string.got_it),
            onClick = onClose,
            modifier = Modifier.padding(top = AppSpacing.xl, bottom = AppLayout.CtaBottomSpacing),
        )
        if (
            state == DisguiseShortcutEntryState.ConfigurationMissing ||
            state == DisguiseShortcutEntryState.InvalidRequest
        ) {
            VaultSecondaryButton(
                text = stringResource(R.string.disguise_shortcut_open_calculator),
                onClick = onOpenCalculator,
                modifier = Modifier.padding(top = AppSpacing.sm),
            )
        }
    }
}

@Composable
private fun SecurityIcon() {
    Box(
        modifier = Modifier.size(64.dp).background(AppColors.AccentContainer, AppShapes.Full),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = VaultIcons.Security,
            contentDescription = null,
            tint = AppColors.AccentPrimary,
            modifier = Modifier.size(30.dp),
        )
    }
}

private fun errorCopy(state: DisguiseShortcutEntryState): Pair<Int, Int> = when (state) {
    DisguiseShortcutEntryState.InvalidRequest ->
        R.string.disguise_shortcut_invalid_request to R.string.disguise_shortcut_invalid_request_description
    DisguiseShortcutEntryState.ConfigurationMissing ->
        R.string.disguise_shortcut_missing_title to R.string.disguise_shortcut_missing_description
    DisguiseShortcutEntryState.TargetNotInstalled ->
        R.string.disguise_shortcut_target_unavailable to R.string.disguise_shortcut_target_uninstalled
    DisguiseShortcutEntryState.TargetDisabled ->
        R.string.disguise_shortcut_target_disabled to R.string.disguise_shortcut_target_disabled_description
    DisguiseShortcutEntryState.NoLaunchIntent ->
        R.string.disguise_shortcut_target_unavailable to R.string.disguise_shortcut_no_launcher
    is DisguiseShortcutEntryState.LaunchFailed -> when (state.reason) {
        LaunchFailureReason.ActivityNotFound ->
            R.string.disguise_shortcut_target_unavailable to R.string.disguise_shortcut_no_launcher
        LaunchFailureReason.SecurityBlocked ->
            R.string.disguise_shortcut_target_unavailable to R.string.disguise_shortcut_launch_blocked
        LaunchFailureReason.Unknown ->
            R.string.disguise_shortcut_target_unavailable to R.string.disguise_shortcut_launch_failed
    }
    DisguiseShortcutEntryState.SessionExpired ->
        R.string.disguise_shortcut_session_expired to R.string.disguise_shortcut_session_expired_description
    else -> R.string.disguise_shortcut_target_unavailable to R.string.disguise_shortcut_launch_failed
}
