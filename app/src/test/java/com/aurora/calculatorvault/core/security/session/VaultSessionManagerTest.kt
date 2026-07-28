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

    private class TestLifecycleOwner : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry(this)
    }
}
