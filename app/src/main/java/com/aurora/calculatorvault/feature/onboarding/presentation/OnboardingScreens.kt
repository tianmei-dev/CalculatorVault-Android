package com.aurora.calculatorvault.feature.onboarding.presentation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultIconButton
import com.aurora.calculatorvault.ui.component.VaultLoadingIndicator
import com.aurora.calculatorvault.ui.component.VaultMainColors
import com.aurora.calculatorvault.ui.component.VaultMainIconTile
import com.aurora.calculatorvault.ui.component.VaultNumericKeypad
import com.aurora.calculatorvault.ui.component.VaultPasswordDots
import com.aurora.calculatorvault.ui.component.VaultPrimaryButton
import com.aurora.calculatorvault.ui.component.VaultSecondaryButton
import com.aurora.calculatorvault.ui.component.useVaultDarkVisuals
import com.aurora.calculatorvault.ui.component.vaultMainBackdrop
import com.aurora.calculatorvault.ui.layout.AppLayout
import com.aurora.calculatorvault.ui.layout.appPagePadding
import com.aurora.calculatorvault.feature.legal.presentation.LegalDocumentScreen
import com.aurora.calculatorvault.feature.legal.presentation.LegalDocumentType

@Composable
fun SplashScreen(
    state: OnboardingUiState,
    onRetry: () -> Unit,
) {
    val polished = useVaultDarkVisuals()
    val palette = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onboardingBackdrop(polished)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(appPagePadding()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            if (polished) {
                VaultMainIconTile(VaultIcons.Calculator, emphasized = true, size = 48.dp, iconSize = 26.dp)
            } else {
                Icon(VaultIcons.Calculator, contentDescription = null, tint = palette.primary)
            }
            Text(
                text = stringResource(R.string.splash_title),
                style = AppTextStyles.PageTitle,
                color = if (polished) VaultMainColors.TextPrimary else palette.onBackground,
            )
            if (state.error == OnboardingError.StartupReadFailed) {
                Text(
                    text = stringResource(R.string.startup_read_failed),
                    style = AppTextStyles.BodySecondary,
                    color = if (polished) AppColors.Error else palette.error,
                )
                VaultSecondaryButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                    polished = true,
                )
            } else {
                Text(
                    text = stringResource(R.string.splash_subtitle),
                    style = AppTextStyles.BodySecondary,
                    color = if (polished) VaultMainColors.TextSecondary else palette.onBackground,
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
    onOpenChildrenPrivacyPolicy: () -> Unit,
    onOpenThirdPartySdkList: () -> Unit,
) {
    val polished = useVaultDarkVisuals()
    val palette = MaterialTheme.colorScheme
    val titleColor = if (polished) VaultMainColors.TextPrimary else palette.onBackground
    val bodyColor = if (polished) VaultMainColors.TextSecondary else palette.onSurface
    val linkColor = if (polished) VaultMainColors.Highlight else palette.primary
    val linkStyle = AppTextStyles.Button.toSpanStyle().copy(color = linkColor)
    val linkStyles = TextLinkStyles(style = linkStyle)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onboardingBackdrop(polished)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(appPagePadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        if (polished) {
            VaultMainIconTile(VaultIcons.Security, emphasized = true, size = 72.dp, iconSize = 33.dp)
        } else {
            Icon(VaultIcons.Security, contentDescription = null, tint = palette.primary)
        }
        Text(
            text = stringResource(R.string.privacy_title),
            style = AppTextStyles.PageTitle.copy(fontWeight = FontWeight.Bold),
            color = titleColor,
            textAlign = TextAlign.Center,
        )
        VaultCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.privacy_intro),
                style = AppTextStyles.Body,
                color = bodyColor,
            )
            Spacer(Modifier.height(AppSpacing.md))
            Text(
                text = buildAnnotatedString {
                    fun appendDocumentLink(label: String, type: LegalDocumentType, onOpen: () -> Unit) {
                        if (state.isSaving) {
                            withStyle(linkStyle) { append(label) }
                        } else {
                            withLink(LinkAnnotation.Clickable(type.routeValue, linkStyles) { onOpen() }) {
                                append(label)
                            }
                        }
                    }

                    append(stringResource(R.string.privacy_reading_prefix))
                    appendDocumentLink(
                        stringResource(R.string.privacy_user_agreement_link),
                        LegalDocumentType.UserAgreement,
                        onOpenUserAgreement,
                    )
                    append("、")
                    appendDocumentLink(
                        stringResource(R.string.privacy_policy_link),
                        LegalDocumentType.PrivacyPolicy,
                        onOpenPrivacyPolicy,
                    )
                    append("及")
                    appendDocumentLink(
                        stringResource(R.string.privacy_children_policy_link),
                        LegalDocumentType.ChildrenPrivacyPolicy,
                        onOpenChildrenPrivacyPolicy,
                    )
                    append(stringResource(R.string.privacy_reading_suffix))
                    append("\n\n")
                    append(stringResource(R.string.privacy_sdk_disclosure_prefix))
                    appendDocumentLink(
                        stringResource(R.string.privacy_third_party_sdk_link),
                        LegalDocumentType.ThirdPartySdkList,
                        onOpenThirdPartySdkList,
                    )
                    append("。")
                },
                style = AppTextStyles.Body,
                color = bodyColor,
            )
        }
        Text(
            text = stringResource(R.string.privacy_calculator_notice),
            style = AppTextStyles.Body.copy(fontSize = AppTextStyles.BodySecondary.fontSize),
            color = bodyColor,
        )
        OnboardingErrorText(state.error)
        VaultPrimaryButton(
            text = stringResource(
                if (state.isSaving) R.string.privacy_saving else R.string.agree_and_continue,
            ),
            enabled = !state.isSaving,
            onClick = onAgree,
            polished = true,
        )
        VaultSecondaryButton(
            text = stringResource(R.string.decline_and_use_calculator),
            enabled = !state.isSaving,
            onClick = onDecline,
            polished = true,
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
    val polished = useVaultDarkVisuals()
    val supportColor = if (polished) VaultMainColors.TextSecondary else MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onboardingBackdrop(polished)
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
            color = supportColor,
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
                color = supportColor,
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
            polished = true,
        )
    }
}

@Composable
private fun PasswordTitleBlock(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    onBack: (() -> Unit)?,
) {
    val polished = useVaultDarkVisuals()
    val palette = MaterialTheme.colorScheme
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
            color = if (polished) VaultMainColors.TextPrimary else palette.onBackground,
        )
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        Text(
            text = stringResource(descriptionRes),
            style = AppTextStyles.Body,
            color = if (polished) VaultMainColors.TextSecondary else palette.onBackground,
        )
    }
}

private fun Modifier.onboardingBackdrop(polished: Boolean): Modifier =
    if (polished) vaultMainBackdrop() else this

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

@Composable
fun ChildrenPrivacyPolicyScreen(onBack: () -> Unit) {
    LegalDocumentScreen(
        type = LegalDocumentType.ChildrenPrivacyPolicy,
        onBack = onBack,
    )
}
