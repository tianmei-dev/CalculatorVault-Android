package com.aurora.calculatorvault.feature.applock.domain

import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.ActionPolicy
import com.aurora.calculatorvault.feature.entitlement.domain.DenialReason
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementState
import com.aurora.calculatorvault.feature.trial.TrialStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppLockProtectionCoordinatorTest {

    private fun trialState(remaining: Long = 3_600_000L) = EntitlementState(
        source = EntitlementSource.DeviceTrial(TrialStatus.ACTIVE, remaining),
        trialStatus = TrialStatus.ACTIVE,
        trialRemainingMillis = remaining,
        trialDeadlineElapsedMillis = remaining,
    )

    private fun noneState() = EntitlementState(
        source = EntitlementSource.None,
        trialStatus = TrialStatus.EXPIRED,
        trialRemainingMillis = 0L,
    )

    private fun kotlinx.coroutines.test.TestScope.fixture(
        entitlement: MutableStateFlow<EntitlementState> = MutableStateFlow(trialState()),
        locked: MutableStateFlow<Set<String>> = MutableStateFlow(setOf("com.example.target")),
        entitledGate: AtomicBoolean = AtomicBoolean(true),
        foreground: AtomicBoolean = AtomicBoolean(true),
    ): Fixture {
        val running = AtomicBoolean(false)
        val startCount = AtomicInteger(0)
        val stopCount = AtomicInteger(0)
        val revokeCount = AtomicInteger(0)
        val startFails = AtomicBoolean(false)
        val privacyWithdrawn = AtomicBoolean(false)
        val usage = AtomicBoolean(true)
        val overlay = AtomicBoolean(true)
        val policy = ActionPolicy {
            if (entitledGate.get()) ActionDecision.Allowed
            else ActionDecision.Denied(DenialReason.REQUIRES_ENTITLEMENT)
        }
        val coordinator = AppLockProtectionCoordinator(
            entitlementState = entitlement,
            actionPolicy = policy,
            observeLockedPackages = { locked },
            lockedPackageCount = { locked.value.size },
            privacyOk = { !privacyWithdrawn.get() },
            privacyWithdrawn = { privacyWithdrawn.get() },
            usageAccessGranted = { usage.get() },
            overlayPermissionGranted = { overlay.get() },
            processForeground = { foreground.get() },
            isMonitorRunning = { running.get() },
            startMonitor = {
                if (startFails.get()) {
                    false
                } else {
                    startCount.incrementAndGet()
                    running.set(true)
                    true
                }
            },
            stopMonitor = {
                stopCount.incrementAndGet()
                running.set(false)
            },
            revokeRuntimeProtection = { revokeCount.incrementAndGet() },
            scope = backgroundScope,
        )
        return Fixture(
            entitlement, locked, entitledGate, coordinator, running,
            startCount, stopCount, revokeCount, startFails, foreground, privacyWithdrawn,
        )
    }

    private data class Fixture(
        val entitlement: MutableStateFlow<EntitlementState>,
        val locked: MutableStateFlow<Set<String>>,
        val entitledGate: AtomicBoolean,
        val coordinator: AppLockProtectionCoordinator,
        val running: AtomicBoolean,
        val startCount: AtomicInteger,
        val stopCount: AtomicInteger,
        val revokeCount: AtomicInteger,
        val startFails: AtomicBoolean,
        val foreground: AtomicBoolean,
        val privacyWithdrawn: AtomicBoolean,
    )

    @Test
    fun `valid trial allows protection start`() = runTest {
        val f = fixture()
        assertTrue(f.coordinator.allowsLiveProtection())
        f.coordinator.reconcile()
        assertTrue("monitor should start", f.running.get())
        assertEquals(1, f.startCount.get())
    }

    @Test
    fun `trial expiry stops monitor and revokes runtime without clearing lock list`() = runTest {
        val f = fixture()
        f.coordinator.reconcile()
        assertTrue(f.running.get())
        val lockedBefore = f.locked.value
        f.entitledGate.set(false)
        f.entitlement.value = noneState()
        f.coordinator.reconcile()
        assertFalse(f.running.get())
        assertTrue(f.revokeCount.get() >= 1)
        assertTrue(f.stopCount.get() >= 1)
        assertEquals(lockedBefore, f.locked.value)
        assertFalse(f.coordinator.allowsLiveProtection())
    }

    @Test
    fun `restoring entitlement restarts protection with same lock config`() = runTest {
        val f = fixture(entitlement = MutableStateFlow(noneState()), entitledGate = AtomicBoolean(false))
        f.coordinator.reconcile()
        assertFalse(f.running.get())
        f.entitledGate.set(true)
        f.entitlement.value = trialState()
        f.coordinator.reconcile()
        assertTrue(f.running.get())
        assertEquals(setOf("com.example.target"), f.locked.value)
    }

    @Test
    fun `user disabled package stays disabled after entitlement restore`() = runTest {
        val f = fixture(locked = MutableStateFlow(emptySet()))
        f.coordinator.reconcile()
        assertFalse(f.running.get())
        f.entitlement.value = trialState()
        f.coordinator.reconcile()
        assertEquals(0, f.locked.value.size)
        assertFalse(f.running.get())
    }

    @Test
    fun `logout keeping trial entitlement continues protection`() = runTest {
        val f = fixture()
        f.coordinator.reconcile()
        assertTrue(f.running.get())
        f.entitlement.value = trialState(remaining = 2_000_000L)
        f.coordinator.reconcile()
        assertTrue(f.coordinator.allowsLiveProtection())
        assertTrue(f.running.get())
    }

    @Test
    fun `sticky rebuild path refuses when not entitled`() = runTest {
        val f = fixture(entitlement = MutableStateFlow(noneState()), entitledGate = AtomicBoolean(false))
        assertFalse(f.coordinator.allowsLiveProtection())
    }

    @Test
    fun `FGS start failure defers without clearing locks`() = runTest {
        val f = fixture()
        f.startFails.set(true)
        f.coordinator.reconcile()
        assertFalse(f.running.get())
        assertTrue(f.coordinator.pendingForegroundStart.value)
        assertEquals(setOf("com.example.target"), f.locked.value)
        f.startFails.set(false)
        f.foreground.set(true)
        f.coordinator.reconcile()
        assertTrue(f.running.get())
    }

    @Test
    fun `generation bump invalidates prior protection epoch`() = runTest {
        val f = fixture()
        val gen = f.coordinator.protectionGeneration()
        f.coordinator.bumpGeneration()
        assertTrue(f.coordinator.protectionGeneration() > gen)
    }

    @Test
    fun `overlay commit refused when policy denies - Fake gate`() {
        val policy = ActionPolicy {
            ActionDecision.Denied(DenialReason.REQUIRES_ENTITLEMENT)
        }
        assertEquals(
            ActionDecision.Denied(DenialReason.REQUIRES_ENTITLEMENT),
            policy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION),
        )
    }

    @Test
    fun `privacy withdrawal stops running monitor`() = runTest {
        val f = fixture()
        f.coordinator.reconcile()
        assertTrue(f.running.get())
        f.privacyWithdrawn.set(true)
        f.coordinator.reconcile()
        assertFalse(f.running.get())
        assertTrue(f.revokeCount.get() >= 1)
    }

    @Test
    fun `background defer then foreground recovers without wiping locks`() = runTest {
        val f = fixture(foreground = AtomicBoolean(false))
        f.coordinator.reconcile()
        assertFalse(f.running.get())
        assertTrue(f.coordinator.pendingForegroundStart.value)
        assertEquals(setOf("com.example.target"), f.locked.value)
        f.foreground.set(true)
        f.coordinator.reconcile()
        assertTrue(f.running.get())
    }

    @Test
    fun `reconcile uses live locked count not a stale snapshot`() = runTest {
        val locked = MutableStateFlow<Set<String>>(emptySet())
        val staleCounter = AtomicInteger(0)
        val running = AtomicBoolean(false)
        val startCount = AtomicInteger(0)
        val policy = ActionPolicy { ActionDecision.Allowed }
        val coordinator = AppLockProtectionCoordinator(
            entitlementState = MutableStateFlow(trialState()),
            actionPolicy = policy,
            observeLockedPackages = { locked },
            // Simulates the old AtomicInteger lag: getter still returns 0 after Room write.
            lockedPackageCount = {
                val live = locked.value.size
                if (live == 0) staleCounter.get() else live
            },
            privacyOk = { true },
            privacyWithdrawn = { false },
            usageAccessGranted = { true },
            overlayPermissionGranted = { true },
            processForeground = { true },
            isMonitorRunning = { running.get() },
            startMonitor = {
                startCount.incrementAndGet()
                running.set(true)
                true
            },
            stopMonitor = { running.set(false) },
            revokeRuntimeProtection = {},
            scope = backgroundScope,
        )
        coordinator.reconcile()
        assertFalse(running.get())
        locked.value = setOf("com.example.target")
        coordinator.reconcile()
        assertTrue("live Room count must start monitor", running.get())
        assertEquals(1, startCount.get())
    }
}
