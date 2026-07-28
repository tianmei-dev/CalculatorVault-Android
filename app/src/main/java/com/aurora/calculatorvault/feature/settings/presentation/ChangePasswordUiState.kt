package com.aurora.calculatorvault.feature.settings.presentation

enum class ChangePasswordStep {
    VerifyCurrent,
    CreateNew,
    ConfirmNew,
    Completed,
}

sealed interface ChangePasswordError {
    data object InvalidLength : ChangePasswordError
    data object CurrentPasswordIncorrect : ChangePasswordError
    data object SecurityDataInvalid : ChangePasswordError
    data object VerificationFailed : ChangePasswordError
    data object PasswordMismatch : ChangePasswordError
    data object HashFailed : ChangePasswordError
    data object SaveFailed : ChangePasswordError
}

data class ChangePasswordUiState(
    val step: ChangePasswordStep = ChangePasswordStep.VerifyCurrent,
    val currentPasswordLength: Int = 0,
    val newPasswordLength: Int = 0,
    val confirmPasswordLength: Int = 0,
    val isProcessing: Boolean = false,
    val error: ChangePasswordError? = null,
    val showSamePasswordPrompt: Boolean = false,
) {
    val activeInputLength: Int
        get() = when (step) {
            ChangePasswordStep.VerifyCurrent -> currentPasswordLength
            ChangePasswordStep.CreateNew -> newPasswordLength
            ChangePasswordStep.ConfirmNew -> confirmPasswordLength
            ChangePasswordStep.Completed -> 0
        }

    val canSubmit: Boolean
        get() = activeInputLength in 4..8 && !isProcessing && !showSamePasswordPrompt
}
