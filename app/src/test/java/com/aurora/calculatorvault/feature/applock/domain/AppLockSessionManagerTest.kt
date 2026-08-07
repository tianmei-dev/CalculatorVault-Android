package com.aurora.calculatorvault.feature.applock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSessionManagerTest {
    @Test
    fun `initial state has no verified or unlocked package`() {
        val manager = AppLockSessionManager()

        assertNull(manager.currentVerifyingPackage())
        assertNull(manager.currentUnlockedPackage())
    }

    @Test
    fun `begin verification rejects duplicate package until finished`() {
        val manager = AppLockSessionManager()

        assertTrue(manager.beginVerification(TEST_PACKAGE))
        assertFalse(manager.beginVerification(TEST_PACKAGE))

        manager.finishVerification(TEST_PACKAGE)

        assertTrue(manager.beginVerification(TEST_PACKAGE))
    }

    @Test
    fun `mark unlocked clears verification and allows temporary pass`() {
        val manager = AppLockSessionManager()

        manager.beginVerification(TEST_PACKAGE)
        manager.markUnlocked(TEST_PACKAGE)

        assertNull(manager.currentVerifyingPackage())
        assertEquals(TEST_PACKAGE, manager.currentUnlockedPackage())
        assertTrue(manager.isTemporarilyUnlocked(TEST_PACKAGE))
        assertFalse(manager.beginVerification(TEST_PACKAGE))
    }

    @Test
    fun `clear unlocked only clears matching package`() {
        val manager = AppLockSessionManager()

        manager.markUnlocked(TEST_PACKAGE)
        manager.clearUnlocked(OTHER_PACKAGE)
        assertEquals(TEST_PACKAGE, manager.currentUnlockedPackage())

        manager.clearUnlocked(TEST_PACKAGE)
        assertNull(manager.currentUnlockedPackage())
    }

    @Test
    fun `clear all removes verification and temporary unlock`() {
        val manager = AppLockSessionManager()

        manager.beginVerification(TEST_PACKAGE)
        manager.markUnlocked(TEST_PACKAGE)
        manager.beginVerification(OTHER_PACKAGE)

        manager.clearAll()

        assertNull(manager.currentVerifyingPackage())
        assertNull(manager.currentUnlockedPackage())
        assertNull(manager.lastForegroundPackage())
        assertEquals(0L, manager.lastForegroundChangedAt())
    }

    @Test
    fun `foreground package updates only when changed`() {
        val manager = AppLockSessionManager()

        assertTrue(manager.updateForegroundPackage(TEST_PACKAGE, 100L))
        assertEquals(TEST_PACKAGE, manager.lastForegroundPackage())
        assertEquals(100L, manager.lastForegroundChangedAt())

        assertFalse(manager.updateForegroundPackage(TEST_PACKAGE, 200L))
        assertEquals(100L, manager.lastForegroundChangedAt())

        assertTrue(manager.updateForegroundPackage(OTHER_PACKAGE, 300L))
        assertEquals(OTHER_PACKAGE, manager.lastForegroundPackage())
        assertEquals(300L, manager.lastForegroundChangedAt())
    }

    private companion object {
        const val TEST_PACKAGE = "com.example.locked"
        const val OTHER_PACKAGE = "com.example.other"
    }
}
