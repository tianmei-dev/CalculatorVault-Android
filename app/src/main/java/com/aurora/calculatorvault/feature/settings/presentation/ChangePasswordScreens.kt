package com.aurora.calculatorvault.feature.settings.presentation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultDialog
import com.aurora.calculatorvault.ui.component.VaultIconButton
import com.aurora.calculatorvault.ui.component.VaultNumericKeypad
import com.aurora.calculatorvault.ui.component.VaultPasswordDots
import com.aurora.calculatorvault.ui.component.VaultPrimaryButton
import com.aurora.calculatorvault.ui.component.VaultTopAppBar
import com.aurora.calculatorvault.ui.layout.AppLayout

@Composable
fun ChangePasswordScreen(
    state: ChangePasswordUiState,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    onBackStep: () -> Boolean,
    onExit: () -> Unit,
    onForgotPassword: () -> Unit,
    onResetSamePassword: () -> Unit,
    onAcceptSamePassword: () -> Unit,
) {
    val exitOrReturn: () -> Unit = {
        if (!onBackStep()) onExit()
    }
    BackHandler(enabled = !state.isProcessing, onBack = exitOrReturn)

    val titleRes = when (state.step) {
        ChangePasswordStep.VerifyCurrent -> R.string.verify_current_password_title
        ChangePasswordStep.CreateNew -> R.string.set_new_password_title
        ChangePasswordStep.ConfirmNew -> R.string.confirm_new_password_title
        ChangePasswordStep.Completed -> R.string.change_password
    }
    val descriptionRes = when (state.step) {
        ChangePasswordStep.VerifyCurrent -> R.string.verify_current_password_description
        ChangePasswordStep.CreateNew -> R.string.set_new_password_description
        ChangePasswordStep.ConfirmNew -> R.string.confirm_new_password_description
        ChangePasswordStep.Completed -> R.string.password_change_success
    }
    val buttonRes = when {
        state.isProcessing && state.step == ChangePasswordStep.VerifyCurrent ->
            R.string.password_verifying
        state.isProcessing -> R.string.password_updating
        state.step == ChangePasswordStep.VerifyCurrent -> R.string.next_step
        state.step == ChangePasswordStep.CreateNew -> R.string.continue_action
        else -> R.string.confirm_password_change
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppLayout.PageHorizontalPadding, vertical = AppSpacing.sm)
            .padding(bottom = AppLayout.BottomSafeSpace),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        VaultIconButton(
            icon = VaultIcons.Back,
            contentDescription = stringResource(R.string.back),
            onClick = exitOrReturn,
        )
        Text(
            text = stringResource(titleRes),
            style = AppTextStyles.PageTitle,
            color = AppColors.TextPrimary,
        )
        Text(
            text = stringResource(descriptionRes),
            style = AppTextStyles.Body,
            color = AppColors.TextSecondary,
        )
        VaultPasswordDots(state.activeInputLength)
        Text(
            text = stringResource(R.string.password_length_hint),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = AppTextStyles.Caption,
            color = AppColors.TextTertiary,
        )
        if (state.step == ChangePasswordStep.CreateNew) {
            Text(
                text = stringResource(R.string.new_password_hint),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = AppTextStyles.Caption,
                color = AppColors.Warning,
            )
            Text(
                text = stringResource(R.string.password_recovery_change_hint),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = AppTextStyles.Caption,
                color = AppColors.TextTertiary,
            )
        }
        ChangePasswordErrorText(state.error)
        if (state.step == ChangePasswordStep.VerifyCurrent) {
            TextButton(
                onClick = onForgotPassword,
                enabled = !state.isProcessing,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = stringResource(R.string.forgot_password),
                    style = AppTextStyles.Button,
                    color = AppColors.AccentPrimary,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        VaultNumericKeypad(
            onDigit = onDigit,
            onDelete = onDelete,
            enabled = !state.isProcessing && !state.showSamePasswordPrompt,
        )
        VaultPrimaryButton(
            text = stringResource(buttonRes),
            enabled = state.canSubmit,
            onClick = onSubmit,
            modifier = Modifier.padding(bottom = AppLayout.CtaBottomSpacing),
        )
    }

    if (state.showSamePasswordPrompt) {
        VaultDialog(
            title = stringResource(R.string.same_password_title),
            message = stringResource(R.string.same_password_message),
            confirmText = stringResource(R.string.continue_same_password_action),
            dismissText = stringResource(R.string.reset_password_action),
            onConfirm = onAcceptSamePassword,
            onDismiss = onResetSamePassword,
        )
    }
}

@Composable
private fun ChangePasswordErrorText(error: ChangePasswordError?) {
    @StringRes val messageRes = when (error) {
        ChangePasswordError.InvalidLength -> R.string.password_too_short
        ChangePasswordError.CurrentPasswordIncorrect -> R.string.current_password_incorrect
        ChangePasswordError.SecurityDataInvalid -> R.string.security_data_invalid
        ChangePasswordError.VerificationFailed -> R.string.password_verification_failed
        ChangePasswordError.PasswordMismatch -> R.string.new_password_mismatch
        ChangePasswordError.HashFailed,
        ChangePasswordError.SaveFailed,
        -> R.string.password_change_failed
        null -> return
    }
    Text(
        text = stringResource(messageRes),
        modifier = Modifier.fillMaxWidth(),
        style = AppTextStyles.BodySecondary,
        color = AppColors.Error,
    )
}

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        VaultTopAppBar(
            title = stringResource(R.string.forgot_password_title),
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
                .padding(horizontal = AppLayout.PageHorizontalPadding, vertical = AppSpacing.md)
                .padding(bottom = AppLayout.BottomSafeSpace),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
        ) {
            VaultCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.forgot_password_intro),
                    style = AppTextStyles.CardTitle,
                    color = AppColors.TextPrimary,
                )
                Text(
                    text = stringResource(R.string.forgot_password_local_note),
                    modifier = Modifier.padding(top = AppSpacing.md),
                    style = AppTextStyles.Body,
                    color = AppColors.TextSecondary,
                )
                Text(
                    text = stringResource(R.string.forgot_password_warning),
                    modifier = Modifier.padding(top = AppSpacing.md),
                    style = AppTextStyles.Body,
                    color = AppColors.Warning,
                )
            }
            Text(
                text = stringResource(R.string.forgot_password_no_recovery),
                style = AppTextStyles.BodySecondary,
                color = AppColors.TextTertiary,
            )
        }
    }
}
