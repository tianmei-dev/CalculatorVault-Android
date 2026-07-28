package com.aurora.calculatorvault.feature.onboarding.domain

enum class PasswordValidation {
    Valid,
    TooShort,
    TooLong,
    NonNumeric,
}

object PasswordPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    fun validate(password: CharArray): PasswordValidation = when {
        password.size < MIN_LENGTH -> PasswordValidation.TooShort
        password.size > MAX_LENGTH -> PasswordValidation.TooLong
        password.any { !it.isDigit() } -> PasswordValidation.NonNumeric
        else -> PasswordValidation.Valid
    }
}
