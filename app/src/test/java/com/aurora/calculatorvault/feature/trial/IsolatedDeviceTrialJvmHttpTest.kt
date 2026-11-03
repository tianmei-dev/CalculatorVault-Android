package com.aurora.calculatorvault.feature.trial

import com.aurora.calculatorvault.feature.login.session.HttpAuthApi
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in only. Never uses application trial state, Android ID or persistent device storage. */
class IsolatedDeviceTrialJvmHttpTest {
    private class Clock : TrialClock {
        var now=1_800_000_000_000L; var ticks=1000L
        override fun wall()=now
        override fun elapsed()=ticks
        override fun boot()="synthetic-boot"
        fun advance(ms:Long) { now+=ms; ticks+=ms }
    }
    private class Memory : TrialStore {
        var record:TrialRecord?=null; var marker=false
        override fun read()=record
        override fun write(record:TrialRecord) { this.record=record }
        override fun seen()=marker
        override fun markSeen() { marker=true }
    }
    private fun api(): HttpAuthApi {
        val url="http://192.168.0.107:8081"
        assumeTrue("Requires independently confirmed isolated Server/database/HMAC",System.getProperty("isolatedTrialConfirmed")=="true")
        val uri=URI(url)
        // Never target the currently used local instance, regardless of host spelling.
        require(url == "http://192.168.0.107:8081" && uri.port == 8081)
        require(uri.host!=null && uri.userInfo==null && uri.query==null && uri.fragment==null && uri.path.isNullOrEmpty())
        require(uri.scheme in listOf("http","https"))
        return HttpAuthApi(url,true,localHttpBaseUrl=url)
    }
    private suspend fun until(done:()->Boolean) { withTimeout(20_000) { while(!done()) delay(10) } }

    @Test fun offline48HoursPrivacyCooldownAndPersistedDeadline() = runBlocking {
        val http=api(); val c=Clock(); val store=Memory(); val key="i32a-"+UUID.randomUUID()
        var accepted=false; var reads=0; var calls=0; var lastCap=-1L; var offline=false
        val scope=CoroutineScope(coroutineContext+SupervisorJob())
        val transport=TrialApi { identity,cap -> calls++; lastCap=cap; if(offline) throw java.io.IOException(); http.activate(identity,cap) }
        val repo=DeviceTrialRepository(store,c,DeviceIdentityProvider { reads++;key },transport,scope,{accepted},{true})
        try {
            repo.initialize();assertEquals(TRIAL_MILLIS,repo.state.value.remainingMillis)
            repo.sync();delay(30);assertEquals(0,reads);assertEquals(0,calls)
            c.advance(48*3_600_000L);accepted=true
            repeat(20){repo.sync()};until{store.record?.verified==true}
            assertEquals(86400L,lastCap);assertEquals(1,calls)
            assertTrue(repo.state.value.remainingMillis<=86400000L)
            val deadline=store.record!!.serverExpires!!
            repeat(20){repo.sync()};delay(30);assertEquals(1,calls)
            c.advance(3_600_000);offline=true;repo.sync();until{calls==2};delay(30)
            assertTrue(repo.state.value.remainingMillis<=23*3_600_000L)
            offline=false;c.advance(60_000);repo.sync();until{calls==3 && repo.state.value.status==TrialStatus.ACTIVE}
            assertTrue(store.record!!.serverExpires!!<=deadline)
            val persisted=http.activate(key,259200)
            assertTrue(persisted.expires<=deadline) // Existing record observed through a new HTTP request.
            accepted=false;c.advance(3_600_000);val before=calls;repo.sync();delay(30);assertEquals(before,calls)
        } finally { scope.cancel() }
    }

    @Test fun expired73HoursZeroCapCannotReviveAfterSimulatedReinstall() = runBlocking {
        val http=api();val c=Clock();val key="i32a-"+UUID.randomUUID();val store=Memory()
        val scope=CoroutineScope(coroutineContext+SupervisorJob())
        fun repo(memory:Memory)=DeviceTrialRepository(memory,c,DeviceIdentityProvider{key},http,scope,{true},{true})
        try {
            val first=repo(store);first.initialize();c.advance(73*3_600_000L);first.sync()
            until{store.record?.verified==true};assertEquals(TrialStatus.EXPIRED,first.state.value.status)
            assertEquals(store.record!!.serverStarted,store.record!!.serverExpires)
            val again=http.activate(key,259200);assertEquals("EXPIRED",again.status)
            val clean=Memory();val installed=repo(clean);installed.sync();until{clean.record?.verified==true}
            assertEquals(TrialStatus.EXPIRED,installed.state.value.status)
        } finally { scope.cancel() }
    }

    @Test fun sixtySecondServerExpiryWithoutChangingProductDuration() = runBlocking {
        val http=api();val key="i32a-"+UUID.randomUUID();val reply=http.activate(key,60)
        assertTrue(reply.remaining<=60000);assertTrue(reply.expires-reply.started<=60000)
        delay(61_000) // Server time, not the phone or server system clock, elapses naturally.
        val expired=http.activate(key,259200)
        assertEquals("EXPIRED",expired.status);assertEquals(reply.expires,expired.expires)
    }
    @Test fun shorterServerDeadlineAndLatePrivacyResponse() = runBlocking {
        val http=api();val key="i32a-"+UUID.randomUUID();val c=Clock();val store=Memory()
        http.activate(key,120)
        val scope=CoroutineScope(coroutineContext+SupervisorJob())
        val returned=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val finished=CompletableDeferred<Unit>()
        var accepted=true;var calls=0
        val transport=TrialApi { k,cap -> calls++;val response=http.activate(k,cap);returned.complete(Unit);release.await();finished.complete(Unit);response }
        val repo=DeviceTrialRepository(store,c,DeviceIdentityProvider{key},transport,scope,{accepted},{true})
        try {
            repo.sync();withTimeout(20000){returned.await()}
            accepted=false;release.complete(Unit);finished.await();delay(30)
            assertFalse(store.record!!.verified);assertNull(store.record!!.serverExpires)
            val count=calls;c.advance(3600000);repo.sync();delay(30);assertEquals(count,calls)
            accepted=true
            val restored=DeviceTrialRepository(Memory(),c,DeviceIdentityProvider{key},http,scope,{true},{true})
            restored.sync();until{restored.state.value.status==TrialStatus.ACTIVE}
            assertTrue(restored.state.value.remainingMillis<=120000)
        } finally { scope.cancel() }
    }

}
