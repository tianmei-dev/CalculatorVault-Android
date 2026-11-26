package com.aurora.calculatorvault.feature.admin

import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Independent admin session — never writes WeChat [AccountSession] or membership sticky.
 *
 * Cold start restores encrypted credential; online [me] validates without extending [expiresAt].
 * Transient network errors keep the session; expiry or explicit invalidation clears it.
 */
class AdminSessionRepository(
    private val api: AdminAuthApi,
    private val store: AdminSessionStore,
    private val scope: CoroutineScope,
    private val privacyAllowed: suspend () -> Boolean,
    private val wallClock: () -> Instant = { Instant.now() },
) {
    private val mutex = Mutex()
    private var credential: AdminCredential? = null
    private var validateJob: Job? = null
    private var expiryJob: Job? = null

    private val mutableState = MutableStateFlow<AdminSession>(AdminSession.Inactive)
    val state: StateFlow<AdminSession> = mutableState.asStateFlow()

    init {
        restore()
    }

    fun isActive(): Boolean = mutableState.value is AdminSession.Active

    /** Admin Bearer only — never pass to payment / WeChat APIs. */
    fun peekAccessToken(): String? {
        val current = credential ?: return null
        if (!wallClock().isBefore(current.expiresAt)) return null
        return current.token
    }

    suspend fun login(username: String, password: String): AdminLoginResult {
        val trimmed = username.trim()
        if (trimmed.isEmpty() || password.isEmpty()) return AdminLoginResult.InvalidCredentials
        if (!privacyAllowed.invoke()) return AdminLoginResult.ServiceUnavailable
        return try {
            val success = api.login(trimmed, password)
            mutex.withLock { persist(success.credential) }
            AdminLoginResult.Success(
                AdminSession.Active(success.credential.username, success.credential.expiresAt),
            )
        } catch (failure: AdminAuthFailure) {
            when (failure.code) {
                40018 -> AdminLoginResult.InvalidCredentials
                40019 -> AdminLoginResult.AccountDisabled
                42902 -> AdminLoginResult.RateLimited
                else -> AdminLoginResult.ServiceUnavailable
            }
        } catch (_: Exception) {
            AdminLoginResult.ServiceUnavailable
        }
    }

    fun logout() {
        val previous = credential
        clearLocal()
        if (previous != null) {
            scope.launch {
                try {
                    api.logout(previous.token)
                } catch (_: Exception) {
                    // Local clear already committed.
                }
            }
        }
    }

    /** Privacy withdrawal / forced local wipe — does not call server. */
    fun clearLocal() {
        validateJob?.cancel()
        expiryJob?.cancel()
        credential = null
        try {
            store.clear()
        } catch (_: Exception) {
            // Still drop in-memory session.
        }
        mutableState.value = AdminSession.Inactive
    }

    /**
     * Foreground / resume / after privacy accept:
     * reload from disk if needed, then validate online without extending expiry.
     */
    fun revalidate() {
        scope.launch { restoreOrValidate() }
    }

    private fun restore() {
        scope.launch { restoreOrValidate() }
    }

    private suspend fun restoreOrValidate() {
        if (!privacyAllowed()) {
            if (credential != null || mutableState.value is AdminSession.Active) clearLocal()
            else mutableState.value = AdminSession.Inactive
            return
        }
        if (credential == null) {
            val saved = try {
                store.read()
            } catch (_: Exception) {
                null
            }
            if (saved == null) {
                mutableState.value = AdminSession.Inactive
                return
            }
            if (!wallClock().isBefore(saved.expiresAt)) {
                try {
                    store.clear()
                } catch (_: Exception) {
                }
                mutableState.value = AdminSession.Inactive
                return
            }
            credential = saved
            mutableState.value = AdminSession.Active(saved.username, saved.expiresAt)
            scheduleExpiry(saved)
        }
        val current = credential ?: return
        if (!wallClock().isBefore(current.expiresAt)) {
            clearLocal()
            return
        }
        scheduleValidation(current)
    }

    private suspend fun persist(value: AdminCredential) {
        validateJob?.cancel()
        expiryJob?.cancel()
        try {
            store.write(value)
        } catch (_: Exception) {
            credential = null
            mutableState.value = AdminSession.Inactive
            throw AdminAuthFailure()
        }
        credential = value
        mutableState.value = AdminSession.Active(value.username, value.expiresAt)
        scheduleExpiry(value)
        scheduleValidation(value)
    }

    private fun scheduleExpiry(value: AdminCredential) {
        expiryJob?.cancel()
        expiryJob = scope.launch {
            val delayMs = java.time.Duration.between(wallClock(), value.expiresAt).toMillis().coerceAtLeast(0)
            kotlinx.coroutines.delay(delayMs)
            if (credential?.token == value.token) clearLocal()
        }
    }

    private fun scheduleValidation(value: AdminCredential) {
        validateJob?.cancel()
        validateJob = scope.launch {
            try {
                val me = api.me(value.token)
                mutex.withLock {
                    val current = credential ?: return@withLock
                    if (current.token != value.token) return@withLock
                    // Keep original expiresAt — network success must not extend lease.
                    if (me.username != current.username) {
                        val updated = current.copy(username = me.username)
                        try {
                            store.write(updated)
                            credential = updated
                            mutableState.value = AdminSession.Active(updated.username, updated.expiresAt)
                        } catch (_: Exception) {
                            // Keep previous in-memory username if rewrite fails.
                        }
                    }
                }
            } catch (failure: AdminAuthFailure) {
                if (failure.invalidSession || failure.code in setOf(40018, 40019)) {
                    if (credential?.token == value.token) clearLocal()
                }
                // Transient / other errors: keep session, do not extend expiry.
            } catch (_: Exception) {
                // Network blip — keep session.
            }
        }
    }
}
