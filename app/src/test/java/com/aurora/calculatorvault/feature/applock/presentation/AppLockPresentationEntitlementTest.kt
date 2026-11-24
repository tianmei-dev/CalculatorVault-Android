package com.aurora.calculatorvault.feature.applock.presentation

import com.aurora.calculatorvault.feature.entitlement.domain.*
import com.aurora.calculatorvault.feature.trial.TrialStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AppLockPresentationEntitlementTest {
    @Test fun observationDoesNotReevaluateOrWriteBackAndIgnoresCountdownTicks() = runTest {
        val granted=EntitlementState(source=EntitlementSource.DeviceTrial(TrialStatus.ACTIVE,60000))
        val snapshots=listOf(granted,granted.copy(evaluatedAtElapsedMillis=1),EntitlementState(),granted)
        var reads=0
        val output=snapshots.asFlow().onEach { reads++ }.appLockPresentationEntitled().toList()
        assertEquals(listOf(true,false,true),output)
        assertEquals(4,reads)
        assertEquals(60000L,(granted.source as EntitlementSource.DeviceTrial).remainingMillis)
    }
}
