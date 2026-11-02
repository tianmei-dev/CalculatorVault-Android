package com.aurora.calculatorvault.feature.trial

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

/** Contract-only reproduction: fake identity, fake time/API, no device or network. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class I31OfflineReinstallContractProbe {
    @Test fun offline48HoursThenFirstSyncKeepsOnly24Hours() = runTest {
        val clock=DeviceTrialTest.Clock()
        val store=DeviceTrialTest.Store()
        val api=DeviceTrialTest.Api(clock)
        val repo=DeviceTrialRepository(store,clock,DeviceTrialTest.FakeDeviceIdentityProvider(),api,backgroundScope,{true},{true})
        repo.initialize()
        clock.advance(48*3600000L)
        api.reply=TrialReply("ACTIVE",clock.time,clock.time+TRIAL_MILLIS,clock.time,TRIAL_MILLIS)
        repo.sync();runCurrent()
        assertEquals(24*3600000L,repo.state.value.remainingMillis)
    }
    @Test fun expiredDeviceMustNotReactivateAfterReinstallWithStableIdentity() = runTest {
        val clock=DeviceTrialTest.Clock()
        val api=DeviceTrialTest.Api(clock)
        val identity=DeviceTrialTest.FakeDeviceIdentityProvider("i31-synthetic-device-fixture")
        val repo=DeviceTrialRepository(DeviceTrialTest.Store(),clock,identity,api,backgroundScope,{true},{true})
        repo.initialize()
        clock.advance(48*3600000L)
        val serverStarted=clock.time
        val serverExpires=serverStarted+TRIAL_MILLIS
        api.reply=TrialReply("ACTIVE",serverStarted,serverExpires,clock.time,TRIAL_MILLIS)
        repo.sync();runCurrent()
        clock.advance(25*3600000L)
        repo.refresh()
        assertEquals(TrialStatus.EXPIRED,repo.state.value.status)
        // Uninstall removes all app-private records; same fake SSAID resolves the same server record.
        api.reply=TrialReply("ACTIVE",serverStarted,serverExpires,clock.time,serverExpires-clock.time)
        val reinstalled=DeviceTrialRepository(DeviceTrialTest.Store(),clock,identity,api,backgroundScope,{true},{true})
        reinstalled.sync();runCurrent()
        assertEquals("Expired at original first-open + 72h must remain expired; observed remaining="+
            reinstalled.state.value.remainingMillis,TrialStatus.EXPIRED,reinstalled.state.value.status)
    }
}
