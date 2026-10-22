package com.aurora.calculatorvault.feature.login

import com.aurora.calculatorvault.feature.login.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WeChatAuthorizationTest {
    private val sdk = FakeWeChatSdk()
    private val store = MemoryWeChatStore()
    private var now = 1000L
    private val delivered = mutableListOf<WeChatServerLoginRequest>()
    private fun coordinator(receiver: WeChatServerLoginEntry = WeChatServerLoginEntry { delivered += it }) =
        WeChatAuthorizationCoordinator(sdk, store, receiver, { now })

    @Test fun `missing AppID never sends or persists a request`() {
        sdk.availability = WeChatSdkAvailability.AppIdMissing
        val auth = coordinator()
        auth.start()
        assertEquals(WeChatAuthorizationStatus.AppIdMissing, auth.status.value)
        assertEquals(0, sdk.sendCount)
        assertNull(store.pending)
    }

    @Test fun `uninstalled or unusable WeChat has a distinct outcome`() {
        sdk.availability = WeChatSdkAvailability.WeChatUnavailable
        val auth = coordinator()
        auth.start()
        assertEquals(WeChatAuthorizationStatus.WeChatUnavailable, auth.status.value)
        assertEquals(0, sdk.sendCount)
    }

    @Test fun `failed registration and SDK exceptions do not crash`() {
        val auth = coordinator()
        sdk.availability = WeChatSdkAvailability.InitializationFailed
        auth.start()
        assertEquals(WeChatAuthorizationStatus.InitializationFailed, auth.status.value)
        sdk.throwOnPrepare = true
        auth.start()
        assertEquals(WeChatAuthorizationStatus.InitializationFailed, auth.status.value)
        assertNull(store.pending)
    }

    @Test fun `ready SDK sends correct scope and unpredictable state just once`() {
        val auth = coordinator()
        repeat(20) { auth.start() }
        assertEquals(WeChatAuthorizationStatus.Authorizing, auth.status.value)
        assertEquals(1, sdk.prepareCount)
        assertEquals(1, sdk.sendCount)
        assertEquals("snsapi_userinfo", sdk.sentScope)
        assertTrue(sdk.sentState!!.matches(Regex("[0-9a-f]{64}")))
        assertEquals(store.pending!!.state, sdk.sentState)
    }

    @Test fun `SDK send rejection or exception clears the pending request`() {
        val auth = coordinator()
        sdk.sendResult = false
        auth.start()
        assertEquals(WeChatAuthorizationStatus.LaunchFailed, auth.status.value)
        assertNull(store.pending)
        sdk.throwOnSend = true
        auth.start()
        assertEquals(WeChatAuthorizationStatus.LaunchFailed, auth.status.value)
        assertNull(store.pending)
    }

    @Test fun `successful authorization durably consumes before handing off exact code once`() {
        val auth = coordinator(WeChatServerLoginEntry {
            assertNull("consume must be durable before handoff", store.pending)
            delivered += it
        })
        auth.start()
        val state = sdk.sentState
        auth.onAuthorizationResponse(0, state, "test-only-code")
        repeat(5) { auth.onAuthorizationResponse(0, state, "test-only-code") }
        assertEquals(WeChatAuthorizationStatus.CodeReceived, auth.status.value)
        assertEquals(listOf("test-only-code"), delivered.map { it.code })
        assertNull(store.pending)
        assertFalse(delivered.first().toString().contains("test-only-code"))
    }

    @Test fun `cancel and denial consume state without any handoff and allow a fresh request`() {
        val auth = coordinator()
        for ((error, expected) in listOf(-2 to WeChatAuthorizationStatus.Cancelled,
            -4 to WeChatAuthorizationStatus.Failed, -1 to WeChatAuthorizationStatus.Failed)) {
            auth.start()
            auth.onAuthorizationResponse(error, sdk.sentState, null)
            assertEquals(expected, auth.status.value)
            assertNull(store.pending)
            assertTrue(delivered.isEmpty())
        }
    }

    @Test fun `mismatching or missing state cannot consume the valid pending authorization`() {
        val auth = coordinator()
        auth.start()
        val expectedState = sdk.sentState
        for (wrongState in listOf("wrong-state", "", null)) {
            auth.onAuthorizationResponse(0, wrongState, "test-only-code")
            auth.refresh()
            assertEquals(WeChatAuthorizationStatus.InvalidCallback, auth.status.value)
            assertNotNull(store.pending)
            assertTrue(delivered.isEmpty())
        }
        auth.onAuthorizationResponse(0, expectedState, "test-only-code")
        assertEquals(1, delivered.size)
    }

    @Test fun `explicit retry after invalid callback replaces nonce and rejects old callback`() {
        val auth = coordinator()
        auth.start()
        val old = sdk.sentState
        auth.onAuthorizationResponse(0, "wrong", "test-only-code")
        auth.start()
        assertNotEquals(old, sdk.sentState)
        auth.onAuthorizationResponse(0, old, "old-test-code")
        assertTrue(delivered.isEmpty())
        auth.onAuthorizationResponse(0, sdk.sentState, "fresh-test-code")
        assertEquals("fresh-test-code", delivered.single().code)
    }

    @Test fun `empty successful code is a failure and never reaches Server entry`() {
        val auth = coordinator()
        auth.start()
        auth.onAuthorizationResponse(0, sdk.sentState, "  ")
        assertEquals(WeChatAuthorizationStatus.Failed, auth.status.value)
        assertTrue(delivered.isEmpty())
        assertNull(store.pending)
    }

    @Test fun `process restoration accepts persisted nonce but replay after recreation is rejected`() {
        coordinator().start()
        val state = sdk.sentState
        val restored = coordinator()
        restored.refresh()
        assertEquals(WeChatAuthorizationStatus.Authorizing, restored.status.value)
        restored.onAuthorizationResponse(0, state, "restored-test-code")
        coordinator().onAuthorizationResponse(0, state, "restored-test-code")
        assertEquals(1, delivered.size)
    }

    @Test fun `callback without a pending request cannot log in or hand off a code`() {
        val auth = coordinator()
        auth.onAuthorizationResponse(0, "foreign-state", "test-only-code")
        assertEquals(WeChatAuthorizationStatus.InvalidCallback, auth.status.value)
        assertTrue(delivered.isEmpty())
    }

    @Test fun `expired or future-dated requests fail closed`() {
        val auth = coordinator()
        auth.start()
        now += WeChatAuthorizationCoordinator.REQUEST_TTL_MILLIS
        auth.onAuthorizationResponse(0, sdk.sentState, "test-only-code")
        assertEquals(WeChatAuthorizationStatus.Expired, auth.status.value)
        assertTrue(delivered.isEmpty())
        auth.start()
        now -= 100
        auth.refresh()
        assertEquals(WeChatAuthorizationStatus.Expired, auth.status.value)
        assertNull(store.pending)
    }

    @Test fun `request without callback times out and can be restarted`() {
        val auth = coordinator()
        auth.start()
        now += WeChatAuthorizationCoordinator.REQUEST_TTL_MILLIS
        auth.refresh()
        assertEquals(WeChatAuthorizationStatus.Expired, auth.status.value)
        auth.start()
        assertEquals(WeChatAuthorizationStatus.Authorizing, auth.status.value)
        assertEquals(2, sdk.sendCount)
    }

    @Test fun `storage save failure prevents SDK dispatch`() {
        store.saveResult = false
        val auth = coordinator()
        auth.start()
        assertEquals(WeChatAuthorizationStatus.StorageFailed, auth.status.value)
        assertEquals(0, sdk.sendCount)
    }

    @Test fun `failed durable consumption prevents Server handoff`() {
        val auth = coordinator()
        auth.start()
        store.clearResult = false
        auth.onAuthorizationResponse(0, sdk.sentState, "test-only-code")
        assertEquals(WeChatAuthorizationStatus.StorageFailed, auth.status.value)
        assertTrue(delivered.isEmpty())
    }

    @Test fun `closing prompt or withdrawing consent invalidates in flight state`() {
        val auth = coordinator()
        auth.start()
        val state = sdk.sentState
        auth.cancel()
        auth.onAuthorizationResponse(0, state, "test-only-code")
        assertTrue(delivered.isEmpty())
        auth.start()
        auth.cancel(privacyWithdrawn = true)
        assertEquals(WeChatAuthorizationStatus.PrivacyRequired, auth.status.value)
        assertEquals(1, sdk.releaseCount)
        assertNull(store.pending)
    }

    @Test fun `handoff exception does not replay a consumed code`() {
        val auth = coordinator(WeChatServerLoginEntry { error("Server not available") })
        auth.start()
        auth.onAuthorizationResponse(0, sdk.sentState, "test-only-code")
        assertEquals(WeChatAuthorizationStatus.HandoffFailed, auth.status.value)
        assertNull(store.pending)
        auth.onAuthorizationResponse(0, sdk.sentState, "test-only-code")
        assertEquals(WeChatAuthorizationStatus.HandoffFailed, auth.status.value)
    }

    @Test fun `dismissal still rejects callback if storage clear temporarily fails`() {
        val auth = coordinator()
        auth.start()
        val nonce = sdk.sentState
        store.clearResult = false
        auth.cancel()
        store.clearResult = true
        auth.refresh()
        auth.onAuthorizationResponse(0, nonce, "late-test-code")
        assertTrue(delivered.isEmpty())
        auth.start()
        assertNotEquals(nonce, sdk.sentState)
        auth.onAuthorizationResponse(0, sdk.sentState, "new-test-code")
        assertEquals("new-test-code", delivered.single().code)
    }

    @Test fun `nonces differ between fresh requests and never appear in debug strings`() {
        val auth = coordinator()
        val seen = mutableSetOf<String>()
        repeat(64) {
            auth.start()
            assertTrue(seen.add(sdk.sentState!!))
            assertFalse(store.pending.toString().contains(sdk.sentState!!))
            auth.cancel()
        }
    }

    @Test fun `S2_1 contract is fixed without real network or token generation`() {
        assertEquals("POST", WeChatServerLoginRequest.METHOD)
        assertEquals("/api/v1/auth/wechat/login", WeChatServerLoginRequest.PATH)
        assertEquals("X-App-Code", WeChatServerLoginRequest.APP_CODE_HEADER)
        assertEquals("calculator_vault", WeChatServerLoginRequest.APP_CODE)
        val auth = coordinator(PendingWeChatServerLoginEntry())
        auth.start()
        auth.onAuthorizationResponse(0, sdk.sentState, "test-only-code")
        assertEquals(WeChatAuthorizationStatus.CodeReceived, auth.status.value)
        assertTrue(delivered.isEmpty())
    }

    @Test fun `concurrent callback delivery still hands off only once`() {
        val auth = coordinator()
        auth.start()
        val nonce = sdk.sentState
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(4)
        try {
            val results = (1..16).map {
                workers.submit {
                    start.await()
                    auth.onAuthorizationResponse(0, nonce, "test-only-code")
                }
            }
            start.countDown()
            results.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, delivered.size)
            assertNull(store.pending)
        } finally {
            workers.shutdownNow()
        }
    }
}
