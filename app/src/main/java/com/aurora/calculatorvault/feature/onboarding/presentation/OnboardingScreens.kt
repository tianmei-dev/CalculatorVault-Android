package com.aurora.calculatorvault.feature.onboarding.presentation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultIconButton
import com.aurora.calculatorvault.ui.component.VaultLoadingIndicator
import com.aurora.calculatorvault.ui.component.VaultNumericKeypad
import com.aurora.calculatorvault.ui.component.VaultPasswordDots
import com.aurora.calculatorvault.ui.component.VaultPrimaryButton
import com.aurora.calculatorvault.ui.component.VaultSecondaryButton
import com.aurora.calculatorvault.ui.layout.AppLayout
import com.aurora.calculatorvault.ui.layout.appPagePadding
import com.aurora.calculatorvault.feature.legal.presentation.LegalDocumentScreen
import com.aurora.calculatorvault.feature.legal.presentation.LegalDocumentType

@Composable
fun SplashScreen(
    state: OnboardingUiState,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(appPagePadding()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Icon(
                imageVector = VaultIcons.Calculator,
                contentDescription = null,
                tint = AppColors.AccentPrimary,
            )
            Text(
                text = stringResource(R.string.splash_title),
                style = AppTextStyles.PageTitle,
                color = AppColors.TextPrimary,
            )
            if (state.error == OnboardingError.StartupReadFailed) {
                Text(
                    text = stringResource(R.string.startup_read_failed),
                    style = AppTextStyles.BodySecondary,
                    color = AppColors.Error,
                )
                VaultSecondaryButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                )
            } else {
                Text(
                    text = stringResource(R.string.splash_subtitle),
                    style = AppTextStyles.BodySecondary,
                    color = AppColors.TextTertiary,
                )
                VaultLoadingIndicator()
            }
        }
    }
}

@Composable
fun PrivacyConsentScreen(
    state: OnboardingUiState,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onOpenUserAgreement: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(appPagePadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(AppColors.AccentContainer, AppShapes.ExtraLarge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = VaultIcons.Security,
                contentDescription = null,
                tint = AppColors.AccentPrimary,
            )
        }
        Text(
            text = stringResource(R.string.privacy_title),
            style = AppTextStyles.PageTitle,
            color = AppColors.TextPrimary,
        )
        VaultCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.privacy_intro),
                style = AppTextStyles.Body,
                color = AppColors.TextSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onOpenUserAgreement, enabled = !state.isSaving) {
                    Text(
                        text = stringResource(R.string.user_agreement),
                        style = AppTextStyles.Button,
                        color = AppColors.AccentPrimary,
                    )
                }
                TextButton(onClick = onOpenPrivacyPolicy, enabled = !state.isSaving) {
                    Text(
                        text = stringResource(R.string.privacy_policy),
                        style = AppTextStyles.Button,
                        color = AppColors.AccentPrimary,
                    )
                }
            }
            Text(
                text = stringResource(R.string.privacy_local_note),
                style = AppTextStyles.BodySecondary,
                color = AppColors.TextTertiary,
            )
        }
        OnboardingErrorText(state.error)
        Spacer(modifier = Modifier.weight(1f))
        VaultPrimaryButton(
            text = stringResource(
                if (state.isSaving) R.string.privacy_saving else R.string.agree_and_continue,
            ),
            enabled = !state.isSaving,
            onClick = onAgree,
        )
        VaultSecondaryButton(
            text = stringResource(R.string.decline_and_exit),
            enabled = !state.isSaving,
            onClick = onDecline,
        )
    }
}

@Composable
fun CreatePasswordScreen(
    state: OnboardingUiState,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onContinue: () -> Unit,
) {
    PasswordEntryScreen(
        titleRes = R.string.create_password_title,
        descriptionRes = R.string.create_password_description,
        enteredLength = state.passwordLength,
        state = state,
        buttonTextRes = R.string.continue_action,
        buttonEnabled = state.canContinuePassword,
        onDigit = onDigit,
        onDelete = onDelete,
        onSubmit = onContinue,
        showWeakHint = true,
        topPadding = AppDimensions.OnboardingCreateTopPadding,
    )
}

@Composable
fun ConfirmPasswordScreen(
    state: OnboardingUiState,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(enabled = !state.isSaving, onBack = onBack)
    PasswordEntryScreen(
        titleRes = R.string.confirm_password_title,
        descriptionRes = R.string.confirm_password_description,
        enteredLength = state.confirmPasswordLength,
        state = state,
        buttonTextRes = if (state.isSaving) {
            R.string.password_saving
        } else {
            R.string.confirm_action
        },
        buttonEnabled = state.canConfirmPassword,
        onDigit = onDigit,
        onDelete = onDelete,
        onSubmit = onConfirm,
        onBack = onBack,
    )
}

@Composable
private fun PasswordEntryScreen(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    enteredLength: Int,
    state: OnboardingUiState,
    @StringRes buttonTextRes: Int,
    buttonEnabled: Boolean,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    onBack: (() -> Unit)? = null,
    showWeakHint: Boolean = false,
    topPadding: Dp = AppSpacing.sm,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(
                start = AppSpacing.xl,
                top = topPadding,
                end = AppSpacing.xl,
                bottom = AppLayout.BottomSafeSpace,
            ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        PasswordTitleBlock(
            titleRes = titleRes,
            descriptionRes = descriptionRes,
            onBack = onBack,
        )
        VaultPasswordDots(enteredLength)
        Text(
            text = stringResource(R.string.password_length_hint),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = AppTextStyles.Caption,
            color = AppColors.TextTertiary,
        )
        if (showWeakHint) {
            Text(
                text = stringResource(R.string.password_weak_hint),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = AppTextStyles.Caption,
                color = AppColors.Warning,
            )
            Text(
                text = stringResource(R.string.password_recovery_setup_hint),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = AppTextStyles.Caption,
                color = AppColors.TextTertiary,
            )
        }
        OnboardingErrorText(state.error)
        Spacer(modifier = Modifier.weight(1f))
        VaultNumericKeypad(
            onDigit = onDigit,
            onDelete = onDelete,
            enabled = !state.isSaving,
        )
        VaultPrimaryButton(
            text = stringResource(buttonTextRes),
            enabled = buttonEnabled,
            onClick = onSubmit,
        )
    }
}

@Composable
private fun PasswordTitleBlock(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    onBack: (() -> Unit)?,
) {
    Column {
        if (onBack != null) {
            Box(modifier = Modifier.height(AppDimensions.Button)) {
                VaultIconButton(
                    icon = VaultIcons.Back,
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack,
                )
            }
        }
        Text(
            text = stringResource(titleRes),
            style = AppTextStyles.PageTitle,
            color = AppColors.TextPrimary,
        )
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        Text(
            text = stringResource(descriptionRes),
            style = AppTextStyles.Body,
            color = AppColors.TextSecondary,
        )
    }
}

@Composable
private fun OnboardingErrorText(error: OnboardingError?) {
    val messageRes = when (error) {
        OnboardingError.StartupReadFailed -> R.string.startup_read_failed
        OnboardingError.PrivacySaveFailed -> R.string.privacy_save_failed
        OnboardingError.PasswordTooShort -> R.string.password_too_short
        OnboardingError.PasswordTooLong -> R.string.password_too_long
        OnboardingError.PasswordNonNumeric -> R.string.password_non_numeric
        OnboardingError.PasswordMismatch -> R.string.password_mismatch
        OnboardingError.PasswordHashFailed -> R.string.password_hash_failed
        OnboardingError.PasswordSaveFailed -> R.string.password_save_failed
        null -> null
    }
    if (messageRes != null) {
        Text(
            text = stringResource(messageRes),
            modifier = Modifier.fillMaxWidth(),
            style = AppTextStyles.BodySecondary,
            color = AppColors.Error,
        )
    }
}

@Composable
fun UserAgreementScreen(onBack: () -> Unit) {
    LegalDocumentScreen(
        type = LegalDocumentType.UserAgreement,
        onBack = onBack,
    )
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LegalDocumentScreen(
        type = LegalDocumentType.PrivacyPolicy,
        onBack = onBack,
    )
}
