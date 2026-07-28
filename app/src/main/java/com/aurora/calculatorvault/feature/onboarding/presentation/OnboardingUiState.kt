package com.aurora.calculatorvault.feature.onboarding.presentation

enum class OnboardingStep {
    Loading,
    PrivacyConsent,
    CreatePassword,
    ConfirmPassword,
    Calculator,
}

sealed interface OnboardingError {
    data object StartupReadFailed : OnboardingError
    data object PrivacySaveFailed : OnboardingError
    data object PasswordTooShort : OnboardingError
    data object PasswordTooLong : OnboardingError
    data object PasswordNonNumeric : OnboardingError
    data object PasswordMismatch : OnboardingError
    data object PasswordHashFailed : OnboardingError
    data object PasswordSaveFailed : OnboardingError
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Loading,
    val passwordLength: Int = 0,
    val confirmPasswordLength: Int = 0,
    val isSaving: Boolean = false,
    val error: OnboardingError? = null,
) {
    val canContinuePassword: Boolean
        get() = passwordLength in 4..8 && !isSaving

    val canConfirmPassword: Boolean
        get() = confirmPasswordLength in 4..8 && !isSaving
}

