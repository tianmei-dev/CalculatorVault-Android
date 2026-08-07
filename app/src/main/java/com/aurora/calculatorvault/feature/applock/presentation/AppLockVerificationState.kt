package com.aurora.calculatorvault.feature.applock.presentation

data class AppLockVerificationState(
    val targetPackageName: String = "",
    val enteredLength: Int = 0,
    val isVerifying: Boolean = false,
    val passwordIncorrect: Boolean = false,
    val invalidRequest: Boolean = false,
)

sealed interface AppLockVerificationEffect {
    data object Finish : AppLockVerificationEffect
}
