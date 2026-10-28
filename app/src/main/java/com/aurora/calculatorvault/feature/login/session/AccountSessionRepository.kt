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
    data object Validating : AccountSession
    data class Authenticated(val account: ServerAccount) : AccountSession
    data object TemporarilyUnavailable : AccountSession
    data object SessionExpired : AccountSession
    data object LoginFailed : AccountSession
    data object StorageFailed : AccountSession
}
/** App-scoped owner. All state transitions and storage operations are serialized on the app scope. */
class AccountSessionRepository(
    private val api: AuthApi, private val store: TokenStore, private val scope: CoroutineScope,
    private val consent: suspend () -> Boolean = { true },
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
        mutableState.value = AccountSession.Validating
        scope.launch {
            if (!hasConsent() || version != generation) { if (version == generation) mutableState.value = AccountSession.Guest; return@launch }
            val saved = try { store.read() } catch (_: Exception) {
                clearStorage(); mutableState.value = AccountSession.StorageFailed; return@launch
            }
            if (version != generation) return@launch
            credential = saved
            if (saved == null) { mutableState.value = AccountSession.Guest; return@launch }
            validate(saved, version)
        }
    }
    fun revalidate() {
        val saved = credential ?: return
        if (mutableState.value == AccountSession.Validating || mutableState.value == AccountSession.Authenticating) return
        val version = ++generation
        mutableState.value = AccountSession.Validating
        scope.launch {
            if (hasConsent() && version == generation) validate(saved, version)
            else if (version == generation) logout()
        }
    }
    private suspend fun hasConsent(): Boolean = try { consent() } catch (_: Exception) { false }
    private suspend fun validate(saved: SessionCredential, version: Long) {
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
            else mutableState.value = AccountSession.TemporarilyUnavailable
        }
    }
    override fun acceptAuthorization(request: WeChatServerLoginRequest) {
        if (mutableState.value == AccountSession.Authenticating || mutableState.value is AccountSession.Authenticated) return
        if (credential != null) logout()
        val version = ++generation
        mutableState.value = AccountSession.Authenticating
        scope.launch {
            if (!hasConsent() || version != generation) { if (version == generation) mutableState.value = AccountSession.Guest; return@launch }
            val result = try { api.login(request.code) } catch (_: Exception) {
                if (version == generation) mutableState.value = AccountSession.LoginFailed
                return@launch
            }
            if (!hasConsent() || version != generation) {
                if (version == generation) logout()
                revoke(result.credential); return@launch
            }
            try { store.write(result.credential) } catch (_: Exception) {
                credential = null
                clearStorage(); mutableState.value = AccountSession.StorageFailed
                revoke(result.credential); return@launch
            }
            credential = result.credential
            mutableState.value = AccountSession.Authenticated(result.account)
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
        credential = null
        mutableState.value = AccountSession.Guest
        mutableLogoutWarning.value = !clearStorage()
        if (previous != null) scope.launch { revoke(previous) }
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
