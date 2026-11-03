package com.aurora.calculatorvault.feature.applock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockProtectionDecisionTest {

    private fun inputs(
        entitled: Boolean = true,
        locked: Int = 1,
        privacyOk: Boolean = true,
        usage: Boolean = true,
        overlay: Boolean = true,
        foreground: Boolean = true,
        running: Boolean = false,
    ) = AppLockProtectionInputs(
        entitled = entitled,
        lockedPackageCount = locked,
        privacyOk = privacyOk,
        usageAccess = usage,
        overlayPermission = overlay,
        processForeground = foreground,
        monitorRunning = running,
    )

    @Test
    fun `valid entitlement with locks starts monitor in foreground`() {
        val cmds = AppLockProtectionDecision.decide(inputs())
        assertEquals(listOf(AppLockProtectionCommand.StartMonitor), cmds)
    }

    @Test
    fun `entitlement loss revokes runtime and stops running monitor`() {
        val cmds = AppLockProtectionDecision.decide(inputs(entitled = false, running = true))
        assertEquals(
            listOf(AppLockProtectionCommand.RevokeRuntime, AppLockProtectionCommand.StopMonitor),
            cmds,
        )
    }

    @Test
    fun `entitlement loss still revokes when monitor already stopped`() {
        val cmds = AppLockProtectionDecision.decide(inputs(entitled = false, running = false))
        assertEquals(listOf(AppLockProtectionCommand.RevokeRuntime), cmds)
    }

    @Test
    fun `empty lock list does not require start and stops if running`() {
        val idle = AppLockProtectionDecision.decide(inputs(locked = 0, running = false))
        assertEquals(listOf(AppLockProtectionCommand.RevokeRuntime), idle)
        val running = AppLockProtectionDecision.decide(inputs(locked = 0, running = true))
        assertTrue(running.contains(AppLockProtectionCommand.StopMonitor))
        assertTrue(running.contains(AppLockProtectionCommand.RevokeRuntime))
    }

    @Test
    fun `background process defers FGS start without deleting intent`() {
        val cmds = AppLockProtectionDecision.decide(inputs(foreground = false, running = false))
        assertEquals(listOf(AppLockProtectionCommand.DeferUntilForeground), cmds)
        assertFalse(cmds.contains(AppLockProtectionCommand.StopMonitor))
    }

    @Test
    fun `privacy or permission loss stops protection`() {
        assertTrue(
            AppLockProtectionDecision.decide(inputs(privacyOk = false, running = true))
                .contains(AppLockProtectionCommand.StopMonitor),
        )
        assertTrue(
            AppLockProtectionDecision.decide(inputs(usage = false, running = true))
                .contains(AppLockProtectionCommand.StopMonitor),
        )
        assertTrue(
            AppLockProtectionDecision.decide(inputs(overlay = false, running = true))
                .contains(AppLockProtectionCommand.StopMonitor),
        )
    }

    @Test
    fun `already running and entitled needs no command`() {
        assertEquals(
            listOf(AppLockProtectionCommand.None),
            AppLockProtectionDecision.decide(inputs(running = true)),
        )
    }
}
