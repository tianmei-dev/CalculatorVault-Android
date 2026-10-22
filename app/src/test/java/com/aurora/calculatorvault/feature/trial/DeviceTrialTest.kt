package com.aurora.calculatorvault.feature.trial

import kotlinx.coroutines.test.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.junit.Assert.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeviceTrialTest {
    class Clock : TrialClock {
        var time = 1_800_000_000_000L; var uptime = 1000L; var bootId: String? = "1"
        override fun wall() = time
        override fun elapsed() = uptime
        override fun boot() = bootId
        fun advance(ms: Long) { time += ms; uptime += ms }
    }
    class Store : TrialStore {
        var value: TrialRecord? = null; var marker = false; var fail = false; var writeFail = false
        override fun read(): TrialRecord? { check(!fail); return value }
        override fun write(record: TrialRecord) { check(!writeFail); value = record }
        override fun seen() = marker
        override fun markSeen() { marker = true }
    }
    class FakeDeviceIdentityProvider(var key: String? = "0123456789abcdef") : DeviceIdentityProvider {
        var calls = 0
        override fun deviceKey(): String? { calls++; return key }
    }
    class Api(val clock: Clock) : TrialApi {
        private val began = clock.time
        var calls = 0; var failure: Exception? = null; var reply: TrialReply? = null
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun activate(deviceKey: String): TrialReply {
            calls++; gate?.await(); failure?.let { throw it }
            return reply ?: TrialReply("ACTIVE", began, began + TRIAL_MILLIS, clock.time, (began + TRIAL_MILLIS - clock.time).coerceAtLeast(0))
        }
    }
    private fun TestScope.repo(store: Store, clock: Clock, api: Api = Api(clock),
        id: FakeDeviceIdentityProvider = FakeDeviceIdentityProvider(), consent: Boolean = true, enabled: Boolean = true) =
        DeviceTrialRepository(store, clock, id, api, backgroundScope, { consent }, { enabled })
    @Test fun firstOfflineOpenCreatesContinuousTrial() = runTest {
        val s=Store(); val c=Clock(); val r=repo(s,c); r.initialize()
        assertEquals(TRIAL_MILLIS,r.state.value.remainingMillis); assertEquals(TrialStatus.PENDING_SYNC,r.state.value.status)
        c.advance(3600000); r.refresh(); assertEquals(TRIAL_MILLIS-3600000,r.state.value.remainingMillis)
    }
    @Test fun repeatedStartupAndColdStartNeverReset() = runTest {
        val s=Store(); val c=Clock(); val r=repo(s,c); r.initialize(); val start=s.value!!.firstOpened
        c.advance(3600000); r.initialize(); val cold=repo(s,c); cold.initialize()
        assertEquals(start,s.value!!.firstOpened); assertEquals(TRIAL_MILLIS-3600000,cold.state.value.remainingMillis)
    }
    @Test fun guestSyncUsesLocalServerMinimum() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c); val r=repo(s,c,api); r.initialize()
        c.advance(3600000); api.reply=TrialReply("ACTIVE",c.time,c.time+TRIAL_MILLIS,c.time,TRIAL_MILLIS); r.sync(); runCurrent()
        assertEquals(TRIAL_MILLIS-3600000,r.state.value.remainingMillis); assertEquals(TrialStatus.ACTIVE,r.state.value.status)
    }
    @Test fun existingServerRecordWins() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c).apply { reply=TrialReply("ACTIVE",c.time-TRIAL_MILLIS+100000,c.time+100000,c.time,100000) }
        val r=repo(s,c,api); r.sync(); runCurrent(); assertEquals(100000,r.state.value.remainingMillis)
    }
    @Test fun consumedUpperBoundNeverIncreases() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c); val r=repo(s,c,api); r.sync(); runCurrent()
        c.advance(3600000); r.sync(); runCurrent(); assertEquals(TRIAL_MILLIS-3600000,r.state.value.remainingMillis)
    }
    @Test fun expiryIsPersistentAcrossRestartAndNetworkFailure() = runTest {
        val s=Store(); val c=Clock(); repo(s,c).initialize(); c.advance(TRIAL_MILLIS)
        val api=Api(c).apply { failure=java.io.IOException() }; val r=repo(s,c,api); r.sync(); runCurrent()
        assertEquals(TrialStatus.EXPIRED,r.state.value.status); assertTrue(s.value!!.expired)
        c.time-=TRIAL_MILLIS; val cold=repo(s,c); cold.initialize(); assertEquals(TrialStatus.EXPIRED,cold.state.value.status)
    }
    @Test fun networkFailureIsNotExpiryAndBacksOff() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c).apply { failure=java.io.IOException() }; val r=repo(s,c,api)
        r.sync(); runCurrent(); repeat(10) { r.sync() }; runCurrent()
        assertEquals(1,api.calls); assertEquals(TrialStatus.TEMPORARILY_UNAVAILABLE,r.state.value.status)
        r.refresh(); assertEquals(TrialStatus.TEMPORARILY_UNAVAILABLE,r.state.value.status)
    }
    @Test fun rateLimitBacksOffFifteenMinutes() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c).apply { failure=TrialRateLimited() }; val r=repo(s,c,api)
        r.sync(); runCurrent(); c.advance(14*60000); r.sync(); runCurrent(); assertEquals(1,api.calls)
        c.advance(60000); r.sync(); runCurrent(); assertEquals(2,api.calls)
    }
    @Test fun serverExpiredCannotBeReactivated() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c).apply { reply=TrialReply("EXPIRED",c.time-TRIAL_MILLIS,c.time,c.time,0) }
        val r=repo(s,c,api); r.sync(); runCurrent(); assertEquals(TrialStatus.EXPIRED,r.state.value.status)
        c.advance(3600000); api.reply=null; r.sync(); runCurrent(); assertEquals(TrialStatus.EXPIRED,r.state.value.status)
    }
    @Test fun malformedServerDataDoesNotGrant() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c).apply { reply=TrialReply("VIP",0,1,0,TRIAL_MILLIS*2) }
        val r=repo(s,c,api); r.sync(); runCurrent(); assertEquals(TrialStatus.TEMPORARILY_UNAVAILABLE,r.state.value.status)
        assertFalse(s.value!!.verified)
    }
    @Test fun sameBootElapsedIgnoresWallRollbackAndForwardChanges() = runTest {
        val s=Store(); val c=Clock(); val r=repo(s,c); r.initialize()
        c.time-=10*TRIAL_MILLIS; c.uptime+=1000; r.refresh(); assertEquals(TRIAL_MILLIS-1000,r.state.value.remainingMillis)
        c.time+=20*TRIAL_MILLIS; c.uptime+=1000; r.refresh(); assertEquals(TRIAL_MILLIS-2000,r.state.value.remainingMillis)
    }
    @Test fun normalRebootUsesWallAnchor() = runTest {
        val s=Store(); val c=Clock(); repo(s,c).initialize(); c.time+=3600000; c.uptime=0; c.bootId="2"
        val r=repo(s,c); r.initialize(); assertEquals(TRIAL_MILLIS-3600000,r.state.value.remainingMillis)
    }
    @Test fun rebootRollbackRequiresVerification() = runTest {
        val s=Store(); val c=Clock(); repo(s,c).initialize(); c.time-=3600000; c.uptime=0; c.bootId="2"
        val r=repo(s,c); r.initialize(); assertEquals(TrialStatus.VERIFICATION_REQUIRED,r.state.value.status)
    }
    @Test fun missingBootMarkerRequiresVerificationOnRestart() = runTest {
        val s=Store(); val c=Clock(); repo(s,c).initialize(); c.bootId=null
        val r=repo(s,c); r.initialize(); assertEquals(TrialStatus.VERIFICATION_REQUIRED,r.state.value.status)
    }
    @Test fun corruptOrKeystoreFailureNeverRegrants() = runTest {
        val s=Store().apply { fail=true; marker=true }; val c=Clock(); val r=repo(s,c); r.initialize()
        assertEquals(TrialStatus.VERIFICATION_REQUIRED,r.state.value.status); assertNull(s.value)
    }
    @Test fun lostEncryptedRecordWithMarkerNeverRegrantsOffline() = runTest {
        val s=Store().apply { marker=true }; val c=Clock(); val r=repo(s,c); r.initialize()
        assertEquals(TrialStatus.VERIFICATION_REQUIRED,r.state.value.status)
    }
    @Test fun writeFailurePersistsMarkerAndFailsClosed() = runTest {
        val s=Store().apply { writeFail=true }; val c=Clock(); val r=repo(s,c); r.initialize()
        assertTrue(s.marker); assertEquals(TrialStatus.VERIFICATION_REQUIRED,r.state.value.status)
    }
    @Test fun missingDeviceIdentityDoesNotCrashOrCallApi() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c); val r=repo(s,c,api,FakeDeviceIdentityProvider(null)); r.sync(); runCurrent()
        assertEquals(0,api.calls); assertEquals(TrialStatus.VERIFICATION_REQUIRED,r.state.value.status)
    }
    @Test fun consentBeforeIdentityOrNetwork() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c); val id=FakeDeviceIdentityProvider(); val r=repo(s,c,api,id,false)
        r.sync(); runCurrent(); assertEquals(0,id.calls); assertEquals(0,api.calls); assertNotNull(s.value)
    }
    @Test fun complianceGatePreventsUploadsEvenAfterConsent() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c); val id=FakeDeviceIdentityProvider(); val r=repo(s,c,api,id,enabled=false)
        r.sync(); runCurrent(); assertEquals(0,id.calls); assertEquals(0,api.calls)
    }
    @Test fun concurrentSyncTriggersMerge() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c).apply { gate=CompletableDeferred() }; val r=repo(s,c,api)
        repeat(20) { r.sync() }; runCurrent(); assertEquals(1,api.calls)
        api.gate!!.complete(Unit); runCurrent(); repeat(20) { r.sync() }; runCurrent(); assertEquals(1,api.calls)
    }
    @Test fun noAccountDependencyOrPinMutation() = runTest {
        val s=Store(); val c=Clock(); val r=repo(s,c); r.initialize()
        val pin=com.aurora.calculatorvault.core.security.session.VaultSessionManager()
        pin.onAppForegrounded(); assertTrue(pin.tryUnlock())
        pin.onAppBackgrounded(); c.advance(1000); r.refresh()
        assertFalse(pin.isUnlocked())
        assertEquals(TRIAL_MILLIS-1000,r.state.value.remainingMillis)
        assertEquals(s.value!!.firstOpened,c.time-1000)
    }
    @Test fun laterPrivacyConsentDoesNotRestartFirstOpen() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c); var accepted=false
        val r=DeviceTrialRepository(s,c,FakeDeviceIdentityProvider(),api,backgroundScope,{accepted},{true})
        r.sync(); runCurrent(); val first=s.value!!.firstOpened
        c.advance(3600000); accepted=true; r.sync(); runCurrent()
        assertEquals(first,s.value!!.firstOpened); assertEquals(TRIAL_MILLIS-3600000,r.state.value.remainingMillis)
    }
    @Test fun missingLocalRecordRecoversOnlyFromServer() = runTest {
        val s=Store().apply { marker=true }; val c=Clock(); val api=Api(c).apply {
            reply=TrialReply("ACTIVE",c.time-TRIAL_MILLIS+1000,c.time+1000,c.time,1000)
        }
        val r=repo(s,c,api); r.sync(); runCurrent(); assertEquals(1000,r.state.value.remainingMillis)
    }
    @Test fun rebootForwardBeyondDeadlinePersistsExpired() = runTest {
        val s=Store(); val c=Clock(); repo(s,c).initialize()
        c.time+=TRIAL_MILLIS+1; c.uptime=0; c.bootId="2"
        val r=repo(s,c); r.initialize(); assertEquals(TrialStatus.EXPIRED,r.state.value.status)
    }
    @Test fun trialHttpContractIsGuestOnlyAndRecognizes429() = runTest {
        val server=com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1",0),0)
        var body=""; var auth:String?=null; var app:String?=null; var rateLimited=false
        server.createContext("/api/v1/trial/device/activate") { exchange ->
            body=exchange.requestBody.bufferedReader().readText()
            auth=exchange.requestHeaders.getFirst("Authorization"); app=exchange.requestHeaders.getFirst("X-App-Code")
            val json=if(rateLimited) "{}" else """{"code":0,"message":"success","data":{"status":"ACTIVE","trialStartedAt":"2026-01-01T00:00:00Z","trialExpiresAt":"2026-01-04T00:00:00Z","serverTime":"2026-01-01T00:00:00Z","remainingSeconds":259200}}"""
            val bytes=json.toByteArray(); exchange.sendResponseHeaders(if(rateLimited) 429 else 200,bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api=com.aurora.calculatorvault.feature.login.session.HttpAuthApi("http://127.0.0.1:${server.address.port}",true)
            assertEquals(TRIAL_MILLIS,api.activate("0123456789abcdef").remaining)
            assertNull(auth); assertEquals("calculator_vault",app)
            assertEquals(setOf("deviceKey"),org.json.JSONObject(body).keys().asSequence().toSet())
            rateLimited=true
            try { api.activate("0123456789abcdef"); fail() } catch (_:TrialRateLimited) { }
        } finally { server.stop(0) }
    }

    @Test fun accountLoginAndLogoutCannotChangeDeviceTrial() = runTest {
        val s=Store(); val c=Clock(); val trial=repo(s,c); trial.initialize(); val before=s.value
        var token:com.aurora.calculatorvault.feature.login.session.SessionCredential?=null
        val storage=object:com.aurora.calculatorvault.feature.login.session.TokenStore {
            override fun read()=token
            override fun write(value:com.aurora.calculatorvault.feature.login.session.SessionCredential) { token=value }
            override fun clear() { token=null }
        }
        val api=object:com.aurora.calculatorvault.feature.login.session.AuthApi {
            override suspend fun login(code:String)=com.aurora.calculatorvault.feature.login.session.LoginResult(
                com.aurora.calculatorvault.feature.login.session.SessionCredential("test",java.time.Instant.now().plusSeconds(3600)),
                com.aurora.calculatorvault.feature.login.session.ServerAccount(1))
            override suspend fun me(token:String)=com.aurora.calculatorvault.feature.login.session.ServerAccount(1)
            override suspend fun logout(token:String) { }
        }
        val account=com.aurora.calculatorvault.feature.login.session.AccountSessionRepository(api,storage,backgroundScope)
        account.acceptAuthorization(com.aurora.calculatorvault.feature.login.domain.WeChatServerLoginRequest("test-code")); runCurrent()
        account.logout(); runCurrent(); assertEquals(before,s.value)
    }

}
