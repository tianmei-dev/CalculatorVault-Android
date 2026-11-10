package com.aurora.calculatorvault.feature.login.session

import com.aurora.calculatorvault.feature.login.domain.WeChatServerLoginEntry
import com.aurora.calculatorvault.feature.login.domain.WeChatServerLoginRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

sealed interface AccountSession {
    data object Guest : AccountSession
    data object Authenticating : AccountSession

    /**
     * Server check of a saved credential in progress.
     * [trustedAccount] is set only when re-validating a session that was already [Authenticated]
     * in this process; cold-start restore and newly installed credentials carry null.
     */
    data class Validating(val trustedAccount: ServerAccount? = null) : AccountSession
    data class Authenticated(val account: ServerAccount) : AccountSession

    /**
     * Transient failure (network / timeout / 5xx) while validating.
     * [trustedAccount] follows the same rule as [Validating]; never set after a definitive 401.
     */
    data class TemporarilyUnavailable(val trustedAccount: ServerAccount? = null) : AccountSession
    data object SessionExpired : AccountSession
    data object LoginFailed : AccountSession
    data object StorageFailed : AccountSession
}

/**
 * Account whose entitlement may still be evaluated: an authenticated session, or the same
 * trusted account while it is being re-validated. Null for first-time / unknown identity.
 */
val AccountSession.entitlementAccount: ServerAccount?
    get() = when (this) {
        is AccountSession.Authenticated -> account
        is AccountSession.Validating -> trustedAccount
        is AccountSession.TemporarilyUnavailable -> trustedAccount
        else -> null
    }
/** App-scoped owner. All state transitions and storage operations are serialized on the app scope. */
class AccountSessionRepository(
    private val api: AuthApi, private val store: TokenStore, private val scope: CoroutineScope,
    private val consent: suspend () -> Boolean = { true },
    /** Only Debug isolated builds pass true; everywhere else [installServerIssuedSession] refuses. */
    private val serverIssuedInstallAllowed: Boolean = false,
    /** User-initiated logout of a known account (local per-account data cleanup). */
    private val onLoggedOut: (userId: Long) -> Unit = {},
) : WeChatServerLoginEntry {
    private val mutableState = MutableStateFlow<AccountSession>(AccountSession.Guest)
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var expiryJob: kotlinx.coroutines.Job? = null
    private var credential: SessionCredential? = null
    private val mutableLogoutWarning = MutableStateFlow(false)
    val logoutWarning = mutableLogoutWarning.asStateFlow()

    fun restore() {
        // Startup restoration must never supersede a user action already taken in this process.
        if (generation != 0L) return
        val version = ++generation
        // Cold start: identity is unknown until the server confirms it.
        mutableState.value = AccountSession.Validating()
        scope.launch {
            if (!hasConsent() || version != generation) { if (version == generation) mutableState.value = AccountSession.Guest; return@launch }
            val saved = try { store.read() } catch (_: Exception) {
                clearStorage(); mutableState.value = AccountSession.StorageFailed; return@launch
            }
            if (version != generation) return@launch
            credential = saved
            if (saved == null) { mutableState.value = AccountSession.Guest; return@launch }
            validate(saved, version, trusted = null)
        }
    }
    fun revalidate() {
        val saved = credential ?: return
        val current = mutableState.value
        if (current is AccountSession.Validating || current == AccountSession.Authenticating) return
        // Only an identity this process already authenticated may stay trusted while re-checking.
        val trusted = current.entitlementAccount
        val version = ++generation
        mutableState.value = AccountSession.Validating(trusted)
        scope.launch {
            if (hasConsent() && version == generation) validate(saved, version, trusted)
            else if (version == generation) logout()
        }
    }
    private suspend fun hasConsent(): Boolean = try { consent() } catch (_: Exception) { false }
    private suspend fun validate(saved: SessionCredential, version: Long, trusted: ServerAccount?) {
        if (!saved.expiresAt.isAfter(Instant.now())) { expire(version); return }
        scheduleExpiry(saved, version)
        try {
            val account = api.me(saved.token)
            if (!hasConsent()) { if (version == generation) logout(); return }
            if (version == generation) {
                if (saved.expiresAt.isAfter(Instant.now())) mutableState.value = AccountSession.Authenticated(account)
                else expire(version)
            }
        } catch (failure: Exception) {
            if (version != generation) return
            if (failure is AuthFailure && failure.invalidSession) expire(version)
            else mutableState.value = AccountSession.TemporarilyUnavailable(trusted)
        }
    }
    override fun acceptAuthorization(request: WeChatServerLoginRequest) {
        AuthTrace.event(1)
        if (mutableState.value == AccountSession.Authenticating || mutableState.value is AccountSession.Authenticated) return
        if (credential != null) logout()
        val version = ++generation
        mutableState.value = AccountSession.Authenticating
        scope.launch {
            if (!hasConsent() || version != generation) { if (version == generation) mutableState.value = AccountSession.Guest; return@launch }
            val result = try { AuthTrace.event(2); api.login(request.code) } catch (failure: Exception) {
                AuthTrace.failure(failure)
                if (version == generation) mutableState.value = AccountSession.LoginFailed
                return@launch
            }
            if (!hasConsent() || version != generation) {
                if (version == generation) logout()
                revoke(result.credential); return@launch
            }
            try { store.write(result.credential) } catch (_: Exception) {
                AuthTrace.event(7)
                credential = null
                clearStorage(); mutableState.value = AccountSession.StorageFailed
                revoke(result.credential); return@launch
            }
            credential = result.credential
            mutableState.value = AccountSession.Authenticated(result.account)
            AuthTrace.event(8)
            scheduleExpiry(result.credential, version)
        }
    }
    private fun scheduleExpiry(saved: SessionCredential, version: Long) {
        expiryJob?.cancel()
        expiryJob = scope.launch {
            kotlinx.coroutines.delay(java.time.Duration.between(Instant.now(), saved.expiresAt).toMillis().coerceAtLeast(0))
            expire(version)
        }
    }
    fun cancelLogin() {
        if (mutableState.value == AccountSession.Authenticating) logout()
    }
    fun logout() {
        expiryJob?.cancel()
        ++generation
        val previous = credential
        val loggedOutUserId = mutableState.value.entitlementAccount?.userId
        credential = null
        mutableState.value = AccountSession.Guest
        mutableLogoutWarning.value = !clearStorage()
        if (loggedOutUserId != null) {
            try { onLoggedOut(loggedOutUserId) } catch (_: Exception) { /* Logout itself must not fail. */ }
        }
        if (previous != null) scope.launch { revoke(previous) }
    }

    /**
     * Drop local session + Keystore without calling server logout.
     * Used by isolation test tear-down / account switch so fixture Bearer tokens stay valid.
     */
    fun abandonLocalSession() {
        expiryJob?.cancel()
        ++generation
        credential = null
        clearStorage()
        mutableState.value = AccountSession.Guest
    }

    /** Current access token for authenticated membership queries; never logged. */
    fun peekAccessToken(): String? = credential?.token

    /**
     * Install a server-issued Bearer session (I3.4.3-D2-A).
     *
     * Writes through [TokenStore] (production [KeystoreTokenStore]), then validates via
     * [AuthApi.me] — the same path as cold-start [restore]. Does **not** invent VIP.
     *
     * Previous in-memory / stored credentials are cleared **without** calling server logout,
     * so other isolation test tokens remain usable when switching fixtures.
     *
     * Callers must be Debug-gated (see debug [com.aurora.calculatorvault.app] helpers);
     * production UI / WeChat login never invoke this.
     */
    fun installServerIssuedSession(credential: SessionCredential) {
        check(serverIssuedInstallAllowed) { "server-issued session install disabled in this build" }
        require(credential.token.isNotBlank()) { "blank session token" }
        require(credential.token.none { it.isWhitespace() || it.isISOControl() }) {
            "invalid session token characters"
        }
        require(credential.expiresAt.isAfter(Instant.now())) { "session already expired locally" }
        expiryJob?.cancel()
        val version = ++generation
        this.credential = null
        clearStorage()
        // A newly installed credential is an unknown identity: never inherits the previous VIP.
        mutableState.value = AccountSession.Validating()
        scope.launch {
            if (version != generation) return@launch
            try {
                store.write(credential)
            } catch (_: Exception) {
                if (version == generation) {
                    this@AccountSessionRepository.credential = null
                    clearStorage()
                    mutableState.value = AccountSession.StorageFailed
                }
                return@launch
            }
            if (version != generation) return@launch
            this@AccountSessionRepository.credential = credential
            validate(credential, version, trusted = null)
        }
    }

    /**
     * Membership-layer SessionInvalid entry (I3.4.3-C).
     * Only expires when [accountId] still matches the current session identity (including a
     * trusted account under re-validation) — a late 401 from a previous account cannot log out
     * the new session. Does not clear device trial, private album, or app-lock Room config.
     */
    fun notifyRemoteSessionInvalid(accountId: Long) {
        val current = mutableState.value.entitlementAccount ?: return
        if (current.userId != accountId) return
        expire(generation)
    }

    private suspend fun revoke(value: SessionCredential) {
        try { api.logout(value.token) } catch (_: Exception) { mutableLogoutWarning.value = true }
    }
    private fun clearStorage(): Boolean = try { store.clear(); true } catch (_: Exception) { false }
    private fun expire(version: Long) {
        if (version != generation) return
        ++generation
        credential = null
        clearStorage()
        mutableState.value = AccountSession.SessionExpired
    }
}
