package com.aurora.calculatorvault.feature.admin

import java.time.Instant

/** Persisted admin credential — never includes password. */
data class AdminCredential(
    val token: String,
    val expiresAt: Instant,
    val username: String,
) {
    override fun toString(): String = "AdminCredential(username=$username, expiresAt=$expiresAt, token=[REDACTED])"
}

data class AdminLoginSuccess(
    val credential: AdminCredential,
    val appCode: String,
)

sealed interface AdminSession {
    data object Inactive : AdminSession
    data class Active(
        val username: String,
        val expiresAt: Instant,
    ) : AdminSession
}

sealed interface AdminLoginResult {
    data class Success(val session: AdminSession.Active) : AdminLoginResult
    data object InvalidCredentials : AdminLoginResult
    data object AccountDisabled : AdminLoginResult
    data object RateLimited : AdminLoginResult
    data object ServiceUnavailable : AdminLoginResult
}

class AdminAuthFailure(
    val invalidSession: Boolean = false,
    val code: Int? = null,
) : Exception("Admin auth request failed")
