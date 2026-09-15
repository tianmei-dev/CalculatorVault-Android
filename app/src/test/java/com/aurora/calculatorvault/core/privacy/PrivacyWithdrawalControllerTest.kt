package com.aurora.calculatorvault.core.privacy

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyWithdrawalControllerTest {
    @Test
    fun `rapid confirmation waits for persistence then ends sessions only once`() = runTest {
        val committed = CompletableDeferred<Unit>()
        val actions = mutableListOf<String>()
        val controller = PrivacyWithdrawalController(this, {
            actions += "write"
            committed.await()
            actions += "stop sdks"
        }, { actions += "end sessions" })
        controller.withdraw()
        controller.withdraw()
        runCurrent()
        assertEquals(listOf("write"), actions)
        assertEquals(PrivacyWithdrawalState.Saving, controller.state.value)
        committed.complete(Unit)
        advanceUntilIdle()
        controller.withdraw()
        assertEquals(listOf("write", "stop sdks", "end sessions"), actions)
        assertEquals(PrivacyWithdrawalState.RestartRequired, controller.state.value)
    }

    @Test
    fun `write failure leaves session intact and supports retry`() = runTest {
        var fail = true
        var sessionsEnded = 0
        val controller = PrivacyWithdrawalController(this, {
            if (fail) throw IOException("disk full")
        }, { sessionsEnded++ })
        controller.withdraw()
        advanceUntilIdle()
        assertEquals(PrivacyWithdrawalState.Failed, controller.state.value)
        assertEquals(0, sessionsEnded)
        fail = false
        controller.withdraw()
        advanceUntilIdle()
        assertEquals(1, sessionsEnded)
        assertEquals(PrivacyWithdrawalState.RestartRequired, controller.state.value)
    }

    @Test
    fun `service cleanup failure still requires replacing SDK process`() = runTest {
        val controller = PrivacyWithdrawalController(this, {}, { error("service failure") })
        controller.withdraw()
        advanceUntilIdle()
        assertEquals(PrivacyWithdrawalState.RestartRequired, controller.state.value)
    }
}
