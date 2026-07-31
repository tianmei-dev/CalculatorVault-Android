package com.aurora.calculatorvault.feature.disguise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisguiseNamePolicyTest {
    @Test
    fun `blank name is invalid`() {
        assertFalse(DisguiseNamePolicy.isValid("   "))
    }

    @Test
    fun `twenty characters are valid and longer input is truncated`() {
        val twenty = "12345678901234567890"
        assertTrue(DisguiseNamePolicy.isValid(twenty))
        assertEquals(twenty, DisguiseNamePolicy.normalize(twenty + "extra"))
    }
}
