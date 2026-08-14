package com.aurora.calculatorvault.core.security.session

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSessionManagerTest {

    @Test
    fun `session starts locked and only unlocks while foreground`() {
        val manager = VaultSessionManager()

        assertEquals(VaultSessionState.Locked, manager.state.value)
        assertFalse(manager.tryUnlock())
        manager.onAppForegrounded()
        assertTrue(manager.tryUnlock())
        assertEquals(VaultSessionState.Unlocked, manager.state.value)
    }

    @Test
    fun `background and repeated locks are safe and increment lock generation`() {
        val manager = VaultSessionManager()
        manager.onAppForegrounded()
        manager.tryUnlock()

        manager.onAppBackgrounded()
        val firstGeneration = manager.lockGeneration.value
        manager.lock()

        assertEquals(VaultSessionState.Locked, manager.state.value)
        assertEquals(firstGeneration + 1L, manager.lockGeneration.value)
        assertFalse(manager.tryUnlock())
    }

    @Test
    fun `new manager simulates process rebuild as locked`() {
        val original = VaultSessionManager()
        original.onAppForegrounded()
        original.tryUnlock()

        assertEquals(VaultSessionState.Locked, VaultSessionManager().state.value)
    }

    @Test
    fun `process lifecycle observer locks on stop`() {
        val manager = VaultSessionManager()
        val observer = AppLifecycleObserver(manager)
        val owner = TestLifecycleOwner()

        observer.onStart(owner)
        assertTrue(manager.tryUnlock())
        observer.onStop(owner)

        assertEquals(VaultSessionState.Locked, manager.state.value)
    }

    @Test
    fun `external result flow keeps session while system picker is open`() {
        val manager = VaultSessionManager()
        manager.onAppForegrounded()
        assertTrue(manager.tryUnlock())

        manager.beginExternalResultFlow()
        manager.onAppBackgrounded()

        assertEquals(VaultSessionState.Unlocked, manager.state.value)

        manager.onAppForegrounded()
        manager.endExternalResultFlow()
        manager.onAppBackgrounded()

        assertEquals(VaultSessionState.Locked, manager.state.value)
    }

    @Test
    fun `external result flow locks after app is reopened without result callback`() {
        var timeoutAction: (() -> Unit)? = null
        val manager = VaultSessionManager(
            scheduleExternalResultTimeout = { _, action ->
                timeoutAction = action
            },
        )
        manager.onAppForegrounded()
        assertTrue(manager.tryUnlock())

        manager.beginExternalResultFlow()
        manager.onAppBackgrounded()
        manager.onAppForegrounded()
        timeoutAction?.invoke()

        assertEquals(VaultSessionState.Locked, manager.state.value)
    }

    @Test
    fun `host activity resume also schedules pending external result relock`() {
        var timeoutAction: (() -> Unit)? = null
        val manager = VaultSessionManager(
            scheduleExternalResultTimeout = { _, action ->
                timeoutAction = action
            },
        )
        manager.onAppForegrounded()
        assertTrue(manager.tryUnlock())

        manager.beginExternalResultFlow()
        manager.onAppBackgrounded()
        manager.onHostActivityResumed()
        timeoutAction?.invoke()

        assertEquals(VaultSessionState.Locked, manager.state.value)
    }

    @Test
    fun `external result callback cancels pending relock`() {
        var timeoutAction: (() -> Unit)? = null
        val manager = VaultSessionManager(
            scheduleExternalResultTimeout = { _, action ->
                timeoutAction = action
            },
        )
        manager.onAppForegrounded()
        assertTrue(manager.tryUnlock())

        manager.beginExternalResultFlow()
        manager.onAppBackgrounded()
        manager.onAppForegrounded()
        manager.endExternalResultFlow()
        timeoutAction?.invoke()

        assertEquals(VaultSessionState.Unlocked, manager.state.value)
    }

    @Test
    fun `ending external result flow restores normal background locking`() {
        val manager = VaultSessionManager()
        manager.onAppForegrounded()
        assertTrue(manager.tryUnlock())

        manager.beginExternalResultFlow()
        manager.endExternalResultFlow()
        manager.onAppBackgrounded()

        assertEquals(VaultSessionState.Locked, manager.state.value)
    }

    @Test
    fun `canceling external result flow locks immediately`() {
        val manager = VaultSessionManager()
        manager.onAppForegrounded()
        assertTrue(manager.tryUnlock())

        manager.beginExternalResultFlow()
        manager.onAppBackgrounded()
        manager.cancelExternalResultFlowAndLock()

        assertEquals(VaultSessionState.Locked, manager.state.value)
    }

    @Test
    fun `ending external result flow restores foreground unlock ability`() {
        val manager = VaultSessionManager()
        manager.onAppForegrounded()

        manager.beginExternalResultFlow()
        manager.onAppBackgrounded()
        manager.endExternalResultFlow()

        assertTrue(manager.tryUnlock())
        assertEquals(VaultSessionState.Unlocked, manager.state.value)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry(this)
    }
}
