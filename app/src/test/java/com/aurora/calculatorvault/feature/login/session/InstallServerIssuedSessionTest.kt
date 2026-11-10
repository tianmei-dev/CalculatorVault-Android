package com.aurora.calculatorvault.feature.login.session

import com.aurora.calculatorvault.feature.login.domain.WeChatServerLoginRequest
import java.time.Instant
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InstallServerIssuedSessionTest {

    @Test
    fun install_reachesAuthenticated_andWritesStore() = runTest {
        val api = Api()
        val store = Store()
        val repo = AccountSessionRepository(api, store, backgroundScope, serverIssuedInstallAllowed = true)
        repo.installServerIssuedSession(
            SessionCredential("fixture-token", Instant.now().plusSeconds(3600)),
        )
        runCurrent()
        assertTrue(repo.state.value is AccountSession.Authenticated)
        assertEquals(7L, (repo.state.value as AccountSession.Authenticated).account.userId)
        assertEquals("fixture-token", store.value?.token)
        assertEquals("fixture-token", repo.peekAccessToken())
        assertEquals(1, api.meCalls)
    }

    @Test
    fun install_invalidSession_expiresWithoutLeavingToken() = runTest {
        val api = Api().also { it.meFailure = AuthFailure(invalidSession = true) }
        val store = Store()
        val repo = AccountSessionRepository(api, store, backgroundScope, serverIssuedInstallAllowed = true)
        repo.installServerIssuedSession(
            SessionCredential("bad-token", Instant.now().plusSeconds(3600)),
        )
        runCurrent()
        assertEquals(AccountSession.SessionExpired, repo.state.value)
        assertNull(repo.peekAccessToken())
        assertNull(store.value)
    }

    @Test
    fun switchInstall_doesNotRemoteRevokePreviousToken() = runTest {
        val api = Api()
        val store = Store()
        val repo = AccountSessionRepository(api, store, backgroundScope, serverIssuedInstallAllowed = true)
        repo.installServerIssuedSession(
            SessionCredential("token-a", Instant.now().plusSeconds(3600)),
        )
        runCurrent()
        api.accountId = 8L
        repo.installServerIssuedSession(
            SessionCredential("token-b", Instant.now().plusSeconds(3600)),
        )
        runCurrent()
        assertEquals(8L, (repo.state.value as AccountSession.Authenticated).account.userId)
        assertEquals(0, api.logoutCalls)
        assertEquals("token-b", store.value?.token)
    }

    @Test
    fun abandonLocalSession_clearsWithoutRemoteLogout() = runTest {
        val api = Api()
        val store = Store()
        val repo = AccountSessionRepository(api, store, backgroundScope, serverIssuedInstallAllowed = true)
        repo.installServerIssuedSession(
            SessionCredential("fixture-token", Instant.now().plusSeconds(3600)),
        )
        runCurrent()
        repo.abandonLocalSession()
        assertEquals(AccountSession.Guest, repo.state.value)
        assertNull(store.value)
        assertEquals(0, api.logoutCalls)
    }

    @Test
    fun wechatLoginPathUnchanged_stillUsesAcceptAuthorization() = runTest {
        val api = Api()
        val store = Store()
        val repo = AccountSessionRepository(api, store, backgroundScope, serverIssuedInstallAllowed = true)
        repo.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        assertTrue(repo.state.value is AccountSession.Authenticated)
        assertEquals(1, api.loginCalls)
    }

    @Test
    fun b2_installRefused_unlessBuildAllowsIt() = runTest {
        val store = Store()
        val repo = AccountSessionRepository(Api(), store, backgroundScope)
        val error = runCatching {
            repo.installServerIssuedSession(SessionCredential("fixture-token", Instant.now().plusSeconds(3600)))
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        runCurrent()
        assertEquals(AccountSession.Guest, repo.state.value)
        assertNull(store.value)
    }

    @Test
    fun b2_logout_reportsLoggedOutAccount_only() = runTest {
        val loggedOut = mutableListOf<Long>()
        val api = Api()
        val repo = AccountSessionRepository(api, Store(), backgroundScope, onLoggedOut = { loggedOut += it })
        repo.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        repo.logout()
        assertEquals(listOf(7L), loggedOut)
        // Logging out again as Guest, or a cancelled login, reports nothing.
        repo.logout()
        repo.cancelLogin()
        assertEquals(listOf(7L), loggedOut)
    }

    @Test
    fun b2_logoutCallbackFailure_doesNotBreakLogout() = runTest {
        val api = Api()
        val repo = AccountSessionRepository(api, Store(), backgroundScope, onLoggedOut = { error("boom") })
        repo.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        repo.logout()
        runCurrent()
        assertEquals(AccountSession.Guest, repo.state.value)
        assertEquals(1, api.logoutCalls)
    }

    private class Store : TokenStore {
        var value: SessionCredential? = null
        override fun read() = value
        override fun write(value: SessionCredential) {
            this.value = value
        }
        override fun clear() {
            value = null
        }
    }

    private class Api : AuthApi {
        var accountId: Long = 7L
        var meCalls = 0
        var loginCalls = 0
        var logoutCalls = 0
        var meFailure: Exception? = null
        override suspend fun login(code: String): LoginResult {
            loginCalls++
            return LoginResult(
                SessionCredential("login-token", Instant.now().plusSeconds(3600)),
                ServerAccount(accountId),
            )
        }
        override suspend fun me(token: String): ServerAccount {
            meCalls++
            meFailure?.let { throw it }
            return ServerAccount(accountId)
        }
        override suspend fun logout(token: String) {
            logoutCalls++
        }
    }
}
