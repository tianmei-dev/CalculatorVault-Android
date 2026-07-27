package com.aurora.calculatorvault.feature.onboarding.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultPrimaryButton
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(700)
        onFinished()
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(AppSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            Text(
                text = stringResource(R.string.splash_subtitle),
                style = AppTextStyles.BodySecondary,
                color = AppColors.TextTertiary,
            )
        }
    }
}

@Composable
fun PrivacyConsentScreen(onContinue: () -> Unit) {
    OnboardingPlaceholder(
        titleRes = R.string.privacy_title,
        descriptionRes = R.string.privacy_placeholder,
        onContinue = onContinue,
    )
}

@Composable
fun CreatePasswordScreen(onContinue: () -> Unit) {
    OnboardingPlaceholder(
        titleRes = R.string.create_password_title,
        descriptionRes = R.string.create_password_placeholder,
        onContinue = onContinue,
    )
}

@Composable
fun ConfirmPasswordScreen(onContinue: () -> Unit) {
    OnboardingPlaceholder(
        titleRes = R.string.confirm_password_title,
        descriptionRes = R.string.confirm_password_placeholder,
        onContinue = onContinue,
    )
}

@Composable
private fun OnboardingPlaceholder(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(AppSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        Text(
            text = stringResource(titleRes),
            style = AppTextStyles.PageTitle,
            color = AppColors.TextPrimary,
        )
        VaultCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(descriptionRes),
                style = AppTextStyles.Body,
                color = AppColors.TextSecondary,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        VaultPrimaryButton(
            text = stringResource(R.string.continue_action),
            onClick = onContinue,
        )
    }
}
