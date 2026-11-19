package com.aurora.calculatorvault.feature.trial

import com.aurora.calculatorvault.feature.entitlement.domain.grantsTrialCapability
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
        var lastCap: Long? = Long.MIN_VALUE
        override suspend fun activate(deviceKey: String, remainingSecondsCap: Long?): TrialReply {
            calls++; lastCap = remainingSecondsCap; gate?.await(); failure?.let { throw it }
            return reply ?: TrialReply("ACTIVE", began, began + TRIAL_MILLIS, clock.time, (began + TRIAL_MILLIS - clock.time).coerceAtLeast(0))
        }
    }
    private fun TestScope.repo(store: Store, clock: Clock, api: Api = Api(clock),
        id: FakeDeviceIdentityProvider = FakeDeviceIdentityProvider(), consent: Boolean = true, enabled: Boolean = true) =
        DeviceTrialRepository(store, clock, id, api, backgroundScope, { consent }, { enabled })

    private fun TestScope.claim(store: Store, clock: Clock, api: Api = Api(clock)): DeviceTrialRepository {
        val r = repo(store, clock, api)
        r.sync(); runCurrent()
        assertEquals(TrialStatus.ACTIVE, r.state.value.status)
        return r
    }

    // T04 — first install offline must not grant unverified trial
    @Test fun firstOfflineOpenDoesNotGrantUnverifiedTrial() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c).apply { failure=java.io.IOException() }; val r=repo(s,c,api)
        r.initialize(); r.sync(); runCurrent()
        assertEquals(TrialStatus.VERIFICATION_REQUIRED, r.state.value.status)
        assertEquals(0L, r.state.value.remainingMillis)
        assertNull(s.value)
        assertFalse(r.state.value.status.grantsTrialCapability())
    }

    // T01 — first online claim starts continuous 72h
    @Test fun firstOnlineClaimStartsContinuousTrial() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c); val r=claim(s,c,api)
        assertEquals(TRIAL_MILLIS, r.state.value.remainingMillis)
        assertNull(api.lastCap)
        assertTrue(s.value!!.verified)
        c.advance(3_600_000); r.refresh()
        assertEquals(TRIAL_MILLIS - 3_600_000, r.state.value.remainingMillis)
    }

    // T12 — restart / cold start never resets confirmed trial
    @Test fun repeatedStartupAndColdStartNeverReset() = runTest {
        val s=Store(); val c=Clock(); val r=claim(s,c); val start=s.value!!.firstOpened
        c.advance(3_600_000); r.initialize(); val cold=repo(s,c); cold.initialize()
        assertEquals(start, s.value!!.firstOpened)
        assertEquals(TRIAL_MILLIS - 3_600_000, cold.state.value.remainingMillis)
        assertEquals(TrialStatus.ACTIVE, cold.state.value.status)
    }

    @Test fun guestSyncUsesLocalServerMinimum() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c); val r=claim(s,c,api)
        val started = s.value!!.serverStarted!!
        c.advance(3_600_000); r.refresh()
        // Inflate local remaining above the server deadline window — sync must tighten to server.
        s.value = s.value!!.copy(remaining = TRIAL_MILLIS)
        c.advance(15 * 60_000)
        val serverRemaining = (started + TRIAL_MILLIS - c.time).coerceAtLeast(0)
        api.reply = TrialReply("ACTIVE", started, started + TRIAL_MILLIS, c.time, serverRemaining)
        r.sync(); runCurrent()
        assertEquals(serverRemaining, r.state.value.remainingMillis)
        assertEquals(TrialStatus.ACTIVE, r.state.value.status)
    }

    // T02 / T07 — existing server record wins (clear-data / reclaim)
    @Test fun existingServerRecordWins() = runTest {
        val s=Store(); val c=Clock()
        val api=Api(c).apply { reply=TrialReply("ACTIVE",c.time-TRIAL_MILLIS+100000,c.time+100000,c.time,100000) }
        val r=repo(s,c,api); r.sync(); runCurrent()
        assertEquals(100000, r.state.value.remainingMillis)
        assertEquals(TrialStatus.ACTIVE, r.state.value.status)
    }

    @Test fun consumedUpperBoundNeverIncreases() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c); val r=claim(s,c,api)
        c.advance(3_600_000 + 15 * 60_000); r.sync(); runCurrent()
        assertEquals(TRIAL_MILLIS - 3_600_000 - 15 * 60_000, r.state.value.remainingMillis)
    }

    @Test fun expiryIsPersistentAcrossRestartAndNetworkFailure() = runTest {
        val s=Store(); val c=Clock(); claim(s,c); c.advance(TRIAL_MILLIS)
        val api=Api(c).apply { failure=java.io.IOException() }; val r=repo(s,c,api); r.sync(); runCurrent()
        assertEquals(TrialStatus.EXPIRED, r.state.value.status); assertTrue(s.value!!.expired)
        c.time -= TRIAL_MILLIS; val cold=repo(s,c); cold.initialize()
        assertEquals(TrialStatus.EXPIRED, cold.state.value.status)
    }

    // T13 — network failure after verified keeps offline remaining (not expiry)
    @Test fun networkFailureAfterVerifiedKeepsOfflineRemaining() = runTest {
        val s=Store(); val c=Clock(); claim(s,c)
        val api=Api(c).apply { failure=java.io.IOException() }; val r=repo(s,c,api)
        c.advance(15 * 60_000); r.sync(); runCurrent(); repeat(10) { r.sync() }; runCurrent()
        assertEquals(1, api.calls)
        assertEquals(TrialStatus.TEMPORARILY_UNAVAILABLE, r.state.value.status)
        assertTrue(r.state.value.remainingMillis > 0)
        assertTrue(r.state.value.status.grantsTrialCapability())
    }

    @Test fun firstNetworkFailureDoesNotGrantOrExpirePermanently() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c).apply { failure=java.io.IOException() }; val r=repo(s,c,api)
        r.sync(); runCurrent(); assertEquals(1, api.calls)
        assertEquals(TrialStatus.VERIFICATION_REQUIRED, r.state.value.status)
        c.advance(60_000); api.failure=null; r.sync(); runCurrent()
        assertEquals(TrialStatus.ACTIVE, r.state.value.status)
        // Api.began is fixed at construction; 60s already elapsed on the synthetic clock.
        assertEquals(TRIAL_MILLIS - 60_000, r.state.value.remainingMillis)
    }

    @Test fun rateLimitBacksOffFifteenMinutes() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c).apply { failure=TrialRateLimited() }; val r=repo(s,c,api)
        r.sync(); runCurrent(); c.advance(14 * 60_000); r.sync(); runCurrent(); assertEquals(1, api.calls)
        c.advance(60_000); r.sync(); runCurrent(); assertEquals(2, api.calls)
    }

    // T10 — server expired stops local grant
    @Test fun serverExpiredCannotBeReactivated() = runTest {
        val s=Store(); val c=Clock()
        val api=Api(c).apply { reply=TrialReply("EXPIRED",c.time-TRIAL_MILLIS,c.time,c.time,0) }
        val r=repo(s,c,api); r.sync(); runCurrent(); assertEquals(TrialStatus.EXPIRED, r.state.value.status)
        c.advance(3_600_000); api.reply=null; r.sync(); runCurrent()
        assertEquals(TrialStatus.EXPIRED, r.state.value.status)
    }

    /**
     * Production regression: cached trialStartedAt from pre-UTC-session skew differs by 8h from the
     * repaired server row. A trusted EXPIRED reply must still map to EXPIRED (not VERIFICATION_REQUIRED).
     */
    @Test fun trustedExpiredWinsDespiteEightHourStartedSkew() = runTest {
        val s = Store(); val c = Clock()
        val cachedStarted = c.time
        claim(s, c)
        assertEquals(cachedStarted, s.value!!.serverStarted)
        // Cached Instant came from pre-fix round-trip; repaired UTC session returns started −8h.
        val serverStarted = cachedStarted - 8 * 3_600_000L
        val serverExpires = c.time - 60_000L
        val api = Api(c).apply {
            reply = TrialReply("EXPIRED", serverStarted, serverExpires, c.time, 0)
        }
        c.advance(15 * 60_000)
        val cold = repo(s, c, api)
        cold.initialize(); cold.sync(); runCurrent()
        assertEquals(TrialStatus.EXPIRED, cold.state.value.status)
        assertEquals(0L, cold.state.value.remainingMillis)
        assertTrue(s.value!!.expired)
        assertFalse(s.value!!.uncertain)
        assertEquals(serverStarted, s.value!!.serverStarted)
        assertFalse(cold.state.value.status.grantsTrialCapability())
    }

    @Test fun activeStartedSkewStillRequiresVerificationAndDoesNotGrant() = runTest {
        val s = Store(); val c = Clock()
        val cachedStarted = c.time
        claim(s, c)
        // ACTIVE with trialStartedAt shifted −8h vs cache must not authorize.
        val skewedStarted = cachedStarted - 8 * 3_600_000L
        c.advance(15 * 60_000)
        val api = Api(c).apply {
            reply = TrialReply(
                "ACTIVE",
                skewedStarted,
                skewedStarted + TRIAL_MILLIS,
                c.time,
                (skewedStarted + TRIAL_MILLIS - c.time).coerceAtLeast(0),
            )
        }
        val cold = repo(s, c, api)
        cold.initialize(); cold.sync(); runCurrent()
        assertEquals(TrialStatus.VERIFICATION_REQUIRED, cold.state.value.status)
        assertEquals(0L, cold.state.value.remainingMillis)
        assertTrue(s.value!!.uncertain)
        assertFalse(s.value!!.expired)
        assertFalse(cold.state.value.status.grantsTrialCapability())
    }

    @Test fun networkFailureAfterSkewDoesNotFabricateExpired() = runTest {
        val s = Store(); val c = Clock()
        claim(s, c)
        val api = Api(c).apply { failure = java.io.IOException("offline") }
        c.advance(15 * 60_000)
        val cold = repo(s, c, api)
        cold.initialize(); cold.sync(); runCurrent()
        assertEquals(TrialStatus.TEMPORARILY_UNAVAILABLE, cold.state.value.status)
        assertTrue(cold.state.value.remainingMillis > 0)
        assertFalse(s.value!!.expired)
        assertTrue(cold.state.value.status.grantsTrialCapability())
    }

    @Test fun malformedExpiredPayloadDoesNotBecomeExpired() = runTest {
        val s = Store(); val c = Clock()
        claim(s, c)
        // EXPIRED with non-zero remaining fails validate → must not flip local to EXPIRED.
        val api = Api(c).apply {
            reply = TrialReply("EXPIRED", c.time - TRIAL_MILLIS, c.time, c.time, 1_000)
        }
        c.advance(15 * 60_000)
        val cold = repo(s, c, api)
        cold.initialize(); cold.sync(); runCurrent()
        assertEquals(TrialStatus.TEMPORARILY_UNAVAILABLE, cold.state.value.status)
        assertTrue(cold.state.value.remainingMillis > 0)
        assertFalse(s.value!!.expired)
    }

    @Test fun malformedServerDataDoesNotGrant() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c).apply { reply=TrialReply("VIP",0,1,0,TRIAL_MILLIS*2) }
        val r=repo(s,c,api); r.sync(); runCurrent()
        assertEquals(TrialStatus.VERIFICATION_REQUIRED, r.state.value.status)
        assertTrue(s.value == null || !s.value!!.verified)
    }

    // T11 — wall rollback on same boot ignored; elapsed drives countdown
    @Test fun sameBootElapsedIgnoresWallRollbackAndForwardChanges() = runTest {
        val s=Store(); val c=Clock(); val r=claim(s,c)
        c.time -= 10 * TRIAL_MILLIS; c.uptime += 1000; r.refresh()
        assertEquals(TRIAL_MILLIS - 1000, r.state.value.remainingMillis)
        c.time += 20 * TRIAL_MILLIS; c.uptime += 1000; r.refresh()
        assertEquals(TRIAL_MILLIS - 2000, r.state.value.remainingMillis)
    }

    @Test fun normalRebootUsesWallAnchor() = runTest {
        val s=Store(); val c=Clock(); claim(s,c); c.time += 3_600_000; c.uptime = 0; c.bootId = "2"
        val r=repo(s,c); r.initialize()
        assertEquals(TRIAL_MILLIS - 3_600_000, r.state.value.remainingMillis)
    }

    @Test fun rebootRollbackRequiresVerification() = runTest {
        val s=Store(); val c=Clock(); claim(s,c); c.time -= 3_600_000; c.uptime = 0; c.bootId = "2"
        val r=repo(s,c); r.initialize()
        assertEquals(TrialStatus.VERIFICATION_REQUIRED, r.state.value.status)
    }

    @Test fun missingBootMarkerRequiresVerificationOnRestart() = runTest {
        val s=Store(); val c=Clock(); claim(s,c); c.bootId = null
        val r=repo(s,c); r.initialize()
        assertEquals(TrialStatus.VERIFICATION_REQUIRED, r.state.value.status)
    }

    @Test fun corruptOrKeystoreFailureNeverRegrants() = runTest {
        val s=Store().apply { fail=true; marker=true }; val c=Clock(); val r=repo(s,c); r.initialize()
        assertEquals(TrialStatus.VERIFICATION_REQUIRED, r.state.value.status); assertNull(s.value)
    }

    @Test fun lostEncryptedRecordWithMarkerNeverRegrantsOffline() = runTest {
        val s=Store().apply { marker=true }; val c=Clock(); val r=repo(s,c); r.initialize()
        assertEquals(TrialStatus.VERIFICATION_REQUIRED, r.state.value.status)
    }

    @Test fun writeFailureAfterServerReplyFailsClosed() = runTest {
        val s=Store().apply { writeFail=true }; val c=Clock(); val api=Api(c); val r=repo(s,c,api)
        r.sync(); runCurrent()
        assertTrue(s.marker)
        assertEquals(TrialStatus.VERIFICATION_REQUIRED, r.state.value.status)
        assertFalse(r.state.value.status.grantsTrialCapability())
    }

    @Test fun missingDeviceIdentityDoesNotCrashOrCallApi() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c); val r=repo(s,c,api,FakeDeviceIdentityProvider(null))
        r.sync(); runCurrent()
        assertEquals(0, api.calls); assertEquals(TrialStatus.VERIFICATION_REQUIRED, r.state.value.status)
    }

    @Test fun consentBeforeIdentityOrNetwork() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c); val id=FakeDeviceIdentityProvider(); val r=repo(s,c,api,id,false)
        r.sync(); runCurrent(); assertEquals(0, id.calls); assertEquals(0, api.calls); assertNull(s.value)
    }

    @Test fun complianceGatePreventsUploadsEvenAfterConsent() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c); val id=FakeDeviceIdentityProvider(); val r=repo(s,c,api,id,enabled=false)
        r.sync(); runCurrent(); assertEquals(0, id.calls); assertEquals(0, api.calls)
    }

    @Test fun concurrentSyncTriggersMerge() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c).apply { gate=CompletableDeferred() }; val r=repo(s,c,api)
        repeat(20) { r.sync() }; runCurrent(); assertEquals(1, api.calls)
        api.gate!!.complete(Unit); runCurrent(); repeat(20) { r.sync() }; runCurrent(); assertEquals(1, api.calls)
    }

    @Test fun noAccountDependencyOrPinMutation() = runTest {
        val s=Store(); val c=Clock(); val r=claim(s,c)
        val pin=com.aurora.calculatorvault.core.security.session.VaultSessionManager()
        pin.onAppForegrounded(); assertTrue(pin.tryUnlock())
        pin.onAppBackgrounded(); c.advance(1000); r.refresh()
        assertFalse(pin.isUnlocked())
        assertEquals(TRIAL_MILLIS - 1000, r.state.value.remainingMillis)
    }

    // T09 — login / logout does not mutate device trial
    @Test fun accountLoginAndLogoutCannotChangeDeviceTrial() = runTest {
        val s=Store(); val c=Clock(); val trial=claim(s,c); val before=s.value
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
        account.logout(); runCurrent(); assertEquals(before, s.value)
    }

    @Test fun laterPrivacyConsentDoesNotRestartFirstOpen() = runTest {
        val s=Store(); val c=Clock(); val api=Api(c); var accepted=false
        val r=DeviceTrialRepository(s,c,FakeDeviceIdentityProvider(),api,backgroundScope,{accepted},{true})
        r.sync(); runCurrent(); assertNull(s.value)
        accepted=true; r.sync(); runCurrent(); val first=s.value!!.serverStarted
        c.advance(3_600_000 + 15 * 60_000); r.sync(); runCurrent()
        assertEquals(first, s.value!!.serverStarted)
        assertEquals(TRIAL_MILLIS - 3_600_000 - 15 * 60_000, r.state.value.remainingMillis)
    }

    @Test fun missingLocalRecordCannotRegrantAnUnknownConsumedInterval() = runTest {
        val s=Store().apply { marker=true }; val c=Clock(); val api=Api(c).apply {
            reply=TrialReply("ACTIVE",c.time-TRIAL_MILLIS+1000,c.time+1000,c.time,1000)
        }
        val r=repo(s,c,api); r.sync(); runCurrent()
        assertEquals(0, r.state.value.remainingMillis)
        assertEquals(0L, api.lastCap)
    }

    @Test fun rebootForwardBeyondDeadlinePersistsExpired() = runTest {
        val s=Store(); val c=Clock(); claim(s,c)
        c.time += TRIAL_MILLIS + 1; c.uptime = 0; c.bootId = "2"
        val r=repo(s,c); r.initialize(); assertEquals(TrialStatus.EXPIRED, r.state.value.status)
    }

    // T17 — legacy unverified local row does not grant until server confirms; cap = remaining
    @Test fun legacyUnverifiedLocalDoesNotGrantUntilServerConfirms() = runTest {
        val s=Store(); val c=Clock()
        s.value = TrialRecord(
            firstOpened = c.time - 3_600_000,
            remaining = TRIAL_MILLIS - 3_600_000,
            wall = c.time,
            elapsed = c.uptime,
            boot = c.bootId,
            verified = false,
        )
        s.marker = true
        val api=Api(c); val r=repo(s,c,api); r.initialize()
        assertEquals(TrialStatus.PENDING_SYNC, r.state.value.status)
        assertFalse(r.state.value.status.grantsTrialCapability())
        r.sync(); runCurrent()
        assertEquals(TrialStatus.ACTIVE, r.state.value.status)
        assertEquals((TRIAL_MILLIS - 3_600_000) / 1000, api.lastCap)
        assertTrue(r.state.value.remainingMillis <= TRIAL_MILLIS - 3_600_000)
    }

    @Test fun trialHttpContractOmitsCapOnFirstClaimAndRecognizes429() = runTest {
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
            assertEquals(TRIAL_MILLIS, api.activate("0123456789abcdef", null).remaining)
            assertFalse(org.json.JSONObject(body).has("remainingSecondsCap"))
            assertNull(auth); assertEquals("calculator_vault", app)
            assertEquals(setOf("deviceKey"), org.json.JSONObject(body).keys().asSequence().toSet())
            assertEquals(TRIAL_MILLIS, api.activate("0123456789abcdef", 259200).remaining)
            assertEquals(259200, org.json.JSONObject(body).getLong("remainingSecondsCap"))
            rateLimited=true
            try { api.activate("0123456789abcdef", null); fail() } catch (_:TrialRateLimited) { }
        } finally { server.stop(0) }
    }
}
