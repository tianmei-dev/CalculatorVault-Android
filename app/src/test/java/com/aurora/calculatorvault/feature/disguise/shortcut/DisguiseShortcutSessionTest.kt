package com.aurora.calculatorvault.feature.disguise.shortcut

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisguiseShortcutSessionTest {
    @Test
    fun verificationAndLaunch_areSingleFlight() {
        val session = DisguiseShortcutSession()
        session.begin(TEST_SHORTCUT_ID)
        assertTrue(session.tryStartVerification())
        assertFalse(session.tryStartVerification())
        session.finishVerification()
        assertTrue(session.tryStartLaunch())
        assertFalse(session.tryStartLaunch())
    }

    @Test
    fun clear_removesTemporaryAuthorization() {
        val session = DisguiseShortcutSession()
        session.begin(TEST_SHORTCUT_ID)
        session.clear()
        assertNull(session.currentShortcutId())
        assertFalse(session.tryStartVerification())
    }
}
