package com.aurora.calculatorvault.feature.login.session

import com.aurora.calculatorvault.feature.login.domain.WeChatServerLoginRequest
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountSessionTest {
    private val credential get() = SessionCredential("test-only-token", Instant.now().plusSeconds(3600))
    private class Store : TokenStore {
        var value: SessionCredential? = null
        var failRead = false
        var failWrite = false
        var failClear = false
        override fun read(): SessionCredential? { check(!failRead); return value }
        override fun write(value: SessionCredential) { check(!failWrite); this.value = value }
        override fun clear() { check(!failClear); value = null }
    }
    private open class Api : AuthApi {
        var fail: Exception? = null
        var loginCalls = 0
        var logoutCalls = 0
        override suspend fun login(code: String): LoginResult {
            loginCalls++; fail?.let { throw it }
            return LoginResult(SessionCredential("test", Instant.now().plusSeconds(3600)), ServerAccount(1))
        }
        override suspend fun me(token: String): ServerAccount { fail?.let { throw it }; return ServerAccount(1) }
        override suspend fun logout(token: String) { logoutCalls++; fail?.let { throw it } }
    }
    @Test fun loginSavesOnlySuccess() = runTest {
        val api = Api(); val store = Store(); val repo = AccountSessionRepository(api, store, backgroundScope)
        repo.acceptAuthorization(WeChatServerLoginRequest("one-use")); runCurrent()
        assertTrue(repo.state.value is AccountSession.Authenticated); assertNotNull(store.value)
        repo.logout(); runCurrent(); api.fail = AuthFailure()
        repo.acceptAuthorization(WeChatServerLoginRequest("new-code")); runCurrent()
        assertNull(store.value); assertEquals(AccountSession.LoginFailed, repo.state.value)
    }
    @Test fun coldStartMeAndInvalidation() = runTest {
        val api = Api(); val store = Store().apply { value = credential }
        val repo = AccountSessionRepository(api, store, backgroundScope)
        repo.restore(); runCurrent(); assertTrue(repo.state.value is AccountSession.Authenticated)
        api.fail = java.io.IOException(); repo.revalidate(); runCurrent()
        assertEquals(AccountSession.TemporarilyUnavailable, repo.state.value); assertNotNull(store.value)
        api.fail = AuthFailure(true); repo.revalidate(); runCurrent()
        assertEquals(AccountSession.SessionExpired, repo.state.value); assertNull(store.value)
    }
    @Test fun logoutOfflineStaysGuest() = runTest {
        val api = Api(); val store = Store().apply { value = credential }; val repo = AccountSessionRepository(api, store, backgroundScope)
        repo.restore(); runCurrent(); api.fail = java.io.IOException()
        repo.logout(); repo.logout(); runCurrent()
        assertEquals(AccountSession.Guest, repo.state.value); assertNull(store.value)
        assertTrue(repo.logoutWarning.value); assertEquals(1, api.logoutCalls)
    }
    @Test fun duplicateAndLateLoginCannotUndoLogout() = runTest {
        val response = CompletableDeferred<LoginResult>()
        val api = object : Api() {
            override suspend fun login(code: String): LoginResult { loginCalls++; return response.await() }
        }
        val store = Store(); val repo = AccountSessionRepository(api, store, backgroundScope)
        repo.acceptAuthorization(WeChatServerLoginRequest("code")); runCurrent()
        repo.acceptAuthorization(WeChatServerLoginRequest("code")); repo.logout()
        response.complete(LoginResult(credential, ServerAccount(1))); runCurrent()
        assertEquals(1, api.loginCalls); assertEquals(1, api.logoutCalls)
        assertNull(store.value); assertEquals(AccountSession.Guest, repo.state.value)
    }
    @Test fun secureStorageFailuresFailClosed() = runTest {
        val api = Api(); val store = Store().apply { failRead = true }
        val repo = AccountSessionRepository(api, store, backgroundScope)
        repo.restore(); runCurrent(); assertEquals(AccountSession.StorageFailed, repo.state.value)
        store.failRead = false; store.failWrite = true
        repo.acceptAuthorization(WeChatServerLoginRequest("code")); runCurrent()
        assertEquals(AccountSession.StorageFailed, repo.state.value); assertNull(store.value)
        assertEquals(1, api.logoutCalls)
    }
    @Test fun expiredTokenIsNeverRestored() = runTest {
        val store = Store().apply { value = SessionCredential("expired", Instant.EPOCH) }
        val repo = AccountSessionRepository(Api(), store, backgroundScope)
        repo.restore(); runCurrent(); assertEquals(AccountSession.SessionExpired, repo.state.value); assertNull(store.value)
    }
    @Test fun cancellationDuringConsentNeverSubmitsCode() = runTest {
        val consent = CompletableDeferred<Boolean>()
        val api = Api(); val store = Store()
        val repo = AccountSessionRepository(api, store, backgroundScope) { consent.await() }
        repo.acceptAuthorization(WeChatServerLoginRequest("one-use")); runCurrent()
        repo.cancelLogin(); consent.complete(true); runCurrent()
        assertEquals(0, api.loginCalls); assertNull(store.value)
        assertEquals(AccountSession.Guest, repo.state.value)
    }
    @Test fun successfulLogoutRevokesOnlyCurrentSession() = runTest {
        val api = Api(); val store = Store().apply { value = credential }
        val repo = AccountSessionRepository(api, store, backgroundScope)
        repo.restore(); runCurrent(); repo.logout(); runCurrent()
        assertEquals(1, api.logoutCalls); assertNull(store.value)
        assertEquals(AccountSession.Guest, repo.state.value); assertFalse(repo.logoutWarning.value)
    }
    @Test fun temporaryServerFailureDoesNotDeleteSavedToken() = runTest {
        val api = Api().apply { fail = AuthFailure(false) }
        val store = Store().apply { value = credential }
        val repo = AccountSessionRepository(api, store, backgroundScope)
        repo.restore(); runCurrent()
        assertEquals(AccountSession.TemporarilyUnavailable, repo.state.value); assertNotNull(store.value)
    }
    @Test fun authorizationStateValidationAndReplayStillGateServerLogin() = runTest {
        val sdk = com.aurora.calculatorvault.feature.login.FakeWeChatSdk()
        val store = Store(); val api = Api()
        val repo = AccountSessionRepository(api, store, backgroundScope)
        val authorization = com.aurora.calculatorvault.feature.login.domain.WeChatAuthorizationCoordinator(
            sdk, com.aurora.calculatorvault.feature.login.MemoryWeChatStore(), repo)
        authorization.start()
        authorization.onAuthorizationResponse(0, "wrong-state", "code")
        runCurrent(); assertEquals(0, api.loginCalls)
        authorization.onAuthorizationResponse(0, sdk.sentState, "code")
        authorization.onAuthorizationResponse(0, sdk.sentState, "code")
        runCurrent(); assertEquals(1, api.loginCalls)
        assertTrue(repo.state.value is AccountSession.Authenticated)
    }
    @Test fun delayedStartupRestoreCannotSupersedeLogout() = runTest {
        val store = Store().apply { value = credential }
        val repo = AccountSessionRepository(Api(), store, backgroundScope)
        repo.logout(); repo.restore(); runCurrent()
        assertEquals(AccountSession.Guest, repo.state.value); assertNull(store.value)
    }
    @Test fun failedStorageClearStillStopsLocalIdentity() = runTest {
        val store = Store().apply { value = credential }
        val repo = AccountSessionRepository(Api(), store, backgroundScope)
        repo.restore(); runCurrent(); store.failClear = true
        repo.logout(); runCurrent()
        assertEquals(AccountSession.Guest, repo.state.value)
        assertTrue(repo.logoutWarning.value)
    }
    @Test fun lateMeCannotRestoreSessionAfterExpiryWatchdog() = runTest {
        val response = CompletableDeferred<ServerAccount>()
        val api = object : Api() { override suspend fun me(token: String) = response.await() }
        val store = Store().apply { value = SessionCredential("expiring", Instant.now().plusMillis(500)) }
        val repo = AccountSessionRepository(api, store, backgroundScope)
        repo.restore(); runCurrent()
        testScheduler.advanceTimeBy(1000); runCurrent()
        response.complete(ServerAccount(1)); runCurrent()
        assertEquals(AccountSession.SessionExpired, repo.state.value); assertNull(store.value)
    }
    @Test fun releaseRejectsCleartextAndEmbeddedCredentials() = runTest {
        try { HttpAuthApi("http://127.0.0.1:1", false).login("code"); fail() }
        catch (_: IllegalArgumentException) { }
        try { HttpAuthApi("https://name:secret@example.com", false).login("code"); fail() }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun transportTimeoutAndStoppedServerFailWithoutRetry() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = java.util.concurrent.atomic.AtomicInteger()
        server.createContext("/api/v1/auth/wechat/login") { exchange ->
            requests.incrementAndGet()
            exchange.requestBody.use { it.readBytes() }
            Thread.sleep(200)
            exchange.close()
        }
        server.start()
        val api = HttpAuthApi("http://127.0.0.1:${server.address.port}", true, 100, 50)
        try {
            try { api.login("one-shot"); fail() } catch (_: java.net.SocketTimeoutException) { }
            assertEquals(1, requests.get())
        } finally { server.stop(0) }
        try { api.login("new-code"); fail() } catch (_: java.io.IOException) { }
    }
    @Test fun httpContractAndAppIsolation() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var response = ""; var httpStatus = 200
        var submitted = ""; var app = ""; var bearer: String? = null
        server.createContext("/api/v1/auth/") { exchange ->
            submitted = exchange.requestBody.bufferedReader().readText()
            app = exchange.requestHeaders.getFirst("X-App-Code")
            bearer = exchange.requestHeaders.getFirst("Authorization")
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(httpStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = HttpAuthApi("http://127.0.0.1:${server.address.port}", true)
            response = """{"code":0,"message":"success","data":{"accessToken":"test-token","tokenType":"Bearer","expiresAt":"2099-01-01T00:00:00Z","userId":7,"appCode":"calculator_vault"}}"""
            assertEquals(7L, api.login("one-use-code").account.userId)
            assertEquals("calculator_vault", app); assertTrue(submitted.contains("one-use-code")); assertNull(bearer)
            response = """{"code":0,"message":"success","data":{"userId":7,"appCode":"calculator_vault","status":"ACTIVE"}}"""
            assertEquals(7L, api.me("test-token").userId); assertEquals("Bearer test-token", bearer)
            response = """{"code":0,"message":"success","data":null}"""; api.logout("test-token")
            response = """{"code":40301,"message":"mismatch","data":null}"""; httpStatus = 403
            try { api.me("app-b-token"); fail() } catch (e: AuthFailure) { assertTrue(e.invalidSession) }
            httpStatus = 401; response = "not-json"
            try { api.me("revoked"); fail() } catch (e: AuthFailure) { assertTrue(e.invalidSession) }
            httpStatus = 200; response = "invalid-json"
            try { api.me("token"); fail() } catch (_: org.json.JSONException) { }
            response = """{"code":40005,"message":"invalid","data":null}"""
            try { api.login("invalid-code"); fail() } catch (e: AuthFailure) { assertFalse(e.invalidSession) }
            response = """{"code":0,"data":{"userId":7,"appCode":"other","status":"ACTIVE"}}"""
            try { api.me("wrong-app"); fail() } catch (e: AuthFailure) { assertTrue(e.invalidSession) }
        } finally { server.stop(0) }
    }
}
