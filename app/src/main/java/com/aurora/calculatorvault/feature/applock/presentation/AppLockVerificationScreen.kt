package com.aurora.calculatorvault.feature.applock.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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

@Composable
fun AppLockVerificationScreen(
    state: AppLockVerificationState,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
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
        when {
            state.invalidRequest -> InvalidRequestContent(onCancel)
            state.isVerifying -> VerifyingContent()
            else -> PasswordContent(
                state = state,
                onDigit = onDigit,
                onDelete = onDelete,
                onClear = onClear,
                onConfirm = onConfirm,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun PasswordContent(
    state: AppLockVerificationState,
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
            text = stringResource(R.string.app_lock_verify_title),
            style = AppTextStyles.PageTitle,
            color = AppColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.app_lock_verify_description),
            style = AppTextStyles.Body,
            color = AppColors.TextSecondary,
        )
        VaultPasswordDots(enteredLength = state.enteredLength)
        if (state.passwordIncorrect) {
            Text(
                text = stringResource(R.string.app_lock_password_incorrect),
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
            text = stringResource(R.string.app_lock_confirm),
            enabled = state.enteredLength in 4..8,
            onClick = onConfirm,
        )
        VaultSecondaryButton(text = stringResource(R.string.cancel), onClick = onCancel)
    }
}

@Composable
private fun VerifyingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VaultLoadingIndicator()
        Text(
            text = stringResource(R.string.app_lock_verifying),
            modifier = Modifier.padding(top = AppSpacing.md),
            style = AppTextStyles.Body,
            color = AppColors.TextSecondary,
        )
    }
}

@Composable
private fun InvalidRequestContent(onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SecurityIcon()
        Text(
            text = stringResource(R.string.app_lock_verify_invalid_request),
            modifier = Modifier.padding(top = AppSpacing.lg),
            style = AppTextStyles.PageTitle,
            color = AppColors.TextPrimary,
        )
        VaultPrimaryButton(
            text = stringResource(R.string.got_it),
            modifier = Modifier.padding(top = AppSpacing.xl),
            onClick = onCancel,
        )
    }
}

@Composable
private fun SecurityIcon() {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(AppColors.AccentContainer, AppShapes.Full),
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
