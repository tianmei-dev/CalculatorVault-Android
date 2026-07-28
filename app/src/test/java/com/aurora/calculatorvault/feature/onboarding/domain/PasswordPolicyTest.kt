package com.aurora.calculatorvault.feature.onboarding.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordPolicyTest {

    @Test
    fun `password length and numeric rules are enforced`() {
        assertEquals(PasswordValidation.TooShort, PasswordPolicy.validate("123".toCharArray()))
        assertEquals(PasswordValidation.Valid, PasswordPolicy.validate("1234".toCharArray()))
        assertEquals(PasswordValidation.Valid, PasswordPolicy.validate("12345678".toCharArray()))
        assertEquals(PasswordValidation.TooLong, PasswordPolicy.validate("123456789".toCharArray()))
        assertEquals(PasswordValidation.NonNumeric, PasswordPolicy.validate("12a4".toCharArray()))
    }
}

