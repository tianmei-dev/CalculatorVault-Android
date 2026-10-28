package com.aurora.calculatorvault.core.privacy

import com.aurora.calculatorvault.core.datastore.security.SecurityPreferences

/** Exact-match migration: legacy, missing and unknown versions require explicit consent. */
object PrivacyPolicy {
    const val CURRENT_VERSION = "2026-10-28"
    fun hasValidConsent(value: SecurityPreferences): Boolean =
        value.privacyAccepted && value.privacyVersion == CURRENT_VERSION &&
            (value.privacyAcceptedAt ?: 0L) > 0L
}

/** A revoked process cannot be re-enabled by a stale read or asynchronous callback. */
class PrivacyConsentGate(private val read: suspend () -> SecurityPreferences) {
    private val lock = Any()
    private var generation = 0L
    private var revoked = false
    private var allowed = false

    suspend fun check(): Boolean {
        val ticket = synchronized(lock) { generation }
        val valid = try { PrivacyPolicy.hasValidConsent(read()) } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { false }
        return synchronized(lock) {
            if (ticket != generation || revoked) false
            else { allowed = valid; valid }
        }
    }
    fun isAllowedNow(): Boolean = synchronized(lock) { allowed && !revoked }
    fun <T> runIfAllowed(action: () -> T): T? = synchronized(lock) {
        if (allowed && !revoked) action() else null
    }
    fun revoke() = synchronized(lock) {
        ++generation
        revoked = true
        allowed = false
    }
}
