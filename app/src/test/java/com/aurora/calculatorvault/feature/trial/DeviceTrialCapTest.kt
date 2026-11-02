package com.aurora.calculatorvault.feature.trial

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeviceTrialCapTest {
    private val hour = 3_600_000L
    /** S3.2 model: persistent server interval; synthetic identity only. */
    class Server(val clock: DeviceTrialTest.Clock) : TrialApi {
        var started: Long? = null
        var expires = Long.MAX_VALUE
        val caps = mutableListOf<Long>()
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun activate(deviceKey: String, remainingSecondsCap: Long): TrialReply {
            require(deviceKey == "i31-synthetic-device")
            caps += remainingSecondsCap
            failure?.let { throw it }
            if (started == null) started = clock.time
            expires = minOf(expires, clock.time + remainingSecondsCap * 1000)
            val now = clock.time
            val reply = TrialReply(if (now >= expires) "EXPIRED" else "ACTIVE", started!!, expires,
                now, (expires - now).coerceAtLeast(0))
            gate?.await()
            return reply
        }
    }
    private fun TestScope.repo(s: DeviceTrialTest.Store, c: DeviceTrialTest.Clock, api: TrialApi,
        consent: suspend () -> Boolean = { true }) = DeviceTrialRepository(s,c,
            DeviceTrialTest.FakeDeviceIdentityProvider("i31-synthetic-device"),api,backgroundScope,consent,{true})

    @Test fun offline48HoursUploads24HoursAndReinstallAfterExpiryStaysExpired() = runTest {
        val c=DeviceTrialTest.Clock(); val s=DeviceTrialTest.Store(); val api=Server(c); val r=repo(s,c,api)
        r.initialize(); val first=s.value!!.firstOpened
        c.advance(48*hour); r.sync(); runCurrent()
        assertEquals(listOf(86400L),api.caps)
        assertEquals(first+72*hour,api.expires)
        assertEquals(24*hour,r.state.value.remainingMillis)
        c.advance(25*hour); r.refresh(); assertEquals(TrialStatus.EXPIRED,r.state.value.status)
        val reinstalled=repo(DeviceTrialTest.Store(),c,api); reinstalled.sync(); runCurrent()
        assertEquals(TrialStatus.EXPIRED,reinstalled.state.value.status)
        assertEquals(first+72*hour,api.expires)
    }
    @Test fun expiredFirstNetworkSendsZeroAndEqualTimestampsPersistAcrossColdStart() = runTest {
        val c=DeviceTrialTest.Clock(); val s=DeviceTrialTest.Store(); val api=Server(c); val r=repo(s,c,api)
        r.initialize(); c.advance(73*hour); r.sync(); runCurrent()
        assertEquals(listOf(0L),api.caps); assertEquals(api.started,api.expires)
        assertEquals(TrialStatus.EXPIRED,r.state.value.status)
        val cold=repo(s,c,api); cold.initialize(); assertEquals(TrialStatus.EXPIRED,cold.state.value.status)
    }
    @Test fun floorSecondsAndRetryRecomputesInsteadOfRegranting() = runTest {
        val c=DeviceTrialTest.Clock(); val s=DeviceTrialTest.Store(); val api=Server(c); val r=repo(s,c,api)
        r.initialize(); c.advance(1001); api.failure=java.net.SocketTimeoutException(); r.sync(); runCurrent()
        assertEquals(259198L,api.caps.single())
        c.advance(60_000); api.failure=null; r.sync(); runCurrent()
        assertEquals(259138L,api.caps.last()); assertEquals(2,api.caps.size)
        assertTrue(r.state.value.remainingMillis <= 259138000L)
    }
    @Test fun serverSmallerCapAndSameCapLaterNeverExtend() = runTest {
        val c=DeviceTrialTest.Clock(); val api=Server(c)
        api.activate("i31-synthetic-device",86400); val initial=api.expires
        c.advance(hour); api.activate("i31-synthetic-device",86400); assertEquals(initial,api.expires)
        api.activate("i31-synthetic-device",3600); assertEquals(c.time+hour,api.expires)
        api.activate("i31-synthetic-device",0); val expired=api.expires
        api.activate("i31-synthetic-device",259200); assertEquals(expired,api.expires)
    }
    @Test fun delayedResponseCannotExtendCurrentLocalCountdown() = runTest {
        val c=DeviceTrialTest.Clock(); val s=DeviceTrialTest.Store(); val api=Server(c)
        api.gate=CompletableDeferred(); val r=repo(s,c,api); r.sync(); runCurrent()
        c.advance(hour); r.refresh(); api.gate!!.complete(Unit); runCurrent()
        assertEquals(71*hour,r.state.value.remainingMillis)
        assertEquals(api.expires,s.value!!.serverExpires)
        c.time-=hour; c.advance(60_000); r.refresh()
        assertEquals(71*hour-60_000,r.state.value.remainingMillis)
    }
    @Test fun revokedConsentDiscardsDelayedResponseAndStopsNewUploads() = runTest {
        val c=DeviceTrialTest.Clock(); val s=DeviceTrialTest.Store(); val api=Server(c); var accepted=true
        api.gate=CompletableDeferred(); val r=repo(s,c,api) { accepted }; r.sync(); runCurrent()
        accepted=false; api.gate!!.complete(Unit); runCurrent()
        assertFalse(s.value!!.verified); assertNull(s.value!!.serverExpires)
        c.advance(hour); r.sync(); runCurrent(); assertEquals(1,api.caps.size)
    }
    @Test fun missingOrUncertainHistoryNeverSendsFullGrant() = runTest {
        val c=DeviceTrialTest.Clock(); val api=Server(c)
        val s=DeviceTrialTest.Store().apply { marker=true }
        val r=repo(s,c,api); r.sync(); runCurrent(); assertEquals(0L,api.caps.single())
        assertEquals(TrialStatus.EXPIRED,r.state.value.status)
    }
    @Test fun malformedReplyDoesNotPersistServerConfirmation() = runTest {
        val c=DeviceTrialTest.Clock(); val s=DeviceTrialTest.Store()
        val api=TrialApi { _, _ -> TrialReply("ACTIVE",c.time,c.time,c.time,0) }
        val r=repo(s,c,api); r.sync(); runCurrent()
        assertFalse(s.value!!.verified); assertNull(s.value!!.serverExpires)
        assertEquals(TrialStatus.TEMPORARILY_UNAVAILABLE,r.state.value.status)
    }
    @Test fun httpZeroCapExpiredAndInvalidCapErrorAreHandled() = runTest {
        val server=com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1",0),0)
        var invalid=false
        server.createContext("/api/v1/trial/device/activate") { exchange ->
            val request=org.json.JSONObject(exchange.requestBody.bufferedReader().readText())
            assertEquals(0L,request.getLong("remainingSecondsCap"))
            assertEquals(setOf("deviceKey","remainingSecondsCap"),request.keys().asSequence().toSet())
            assertNull(exchange.requestHeaders.getFirst("Authorization"))
            val body=if(invalid) """{"code":40010,"message":"Trial remainingSecondsCap is invalid","data":null}"""
                else """{"code":0,"message":"success","data":{"status":"EXPIRED","trialStartedAt":"2026-01-01T00:00:00Z","trialExpiresAt":"2026-01-01T00:00:00Z","serverTime":"2026-01-01T00:00:00Z","remainingSeconds":0}}"""
            val bytes=body.toByteArray(); exchange.sendResponseHeaders(if(invalid)400 else 200,bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api=com.aurora.calculatorvault.feature.login.session.HttpAuthApi("http://127.0.0.1:${server.address.port}",true)
            val reply=api.activate("i31-synthetic-device",0)
            assertEquals("EXPIRED",reply.status); assertEquals(reply.started,reply.expires)
            invalid=true
            try { api.activate("i31-synthetic-device",0); fail("40010 must fail") }
            catch (_:com.aurora.calculatorvault.feature.login.session.AuthFailure) { }
            try { api.activate("i31-synthetic-device",259201); fail("invalid cap must fail locally") }
            catch (_:IllegalArgumentException) { }
        } finally { server.stop(0) }
    }

}
