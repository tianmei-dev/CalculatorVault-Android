package com.aurora.calculatorvault.feature.membership.data

import java.time.Instant

/**
 * Caller-supplied scope for a membership /me query.
 * [accountId] / [generation] are never taken from the membership JSON.
 * [accessToken] must not be logged or persisted by this layer.
 */
data class MembershipQueryScope(
    val accountId: Long,
    val generation: Long,
    val accessToken: String,
) {
    override fun toString(): String =
        "MembershipQueryScope(accountId=$accountId, generation=$generation, accessToken=[REDACTED])"
}

/** Raw server `data` payload after envelope validation (protocol V1). */
data class MembershipMeDto(
    val status: String,
    val planType: String?,
    val contributingPlanTypes: List<String>,
    val isLifetime: Boolean,
    val membershipStartedAt: Instant?,
    val membershipExpiresAt: Instant?,
    val serverTime: Instant,
    val activeGrantCount: Int,
)

/**
 * Network-layer membership outcome for I3.4.3-B ServerMembershipProvider.
 * Does not authorize app-lock / album capabilities by itself.
 */
sealed interface MembershipQueryResult {
    val accountId: Long
    val generation: Long

    /**
     * Server confirmed ACTIVE VIP.
     * [confirmedAtElapsedRealtime] anchors a future 24h sticky window (not applied here).
     */
    data class ConfirmedActive(
        override val accountId: Long,
        override val generation: Long,
        val isLifetime: Boolean,
        val expiresAt: Instant?,
        val startedAt: Instant?,
        val planType: String?,
        val contributingPlanTypes: List<String>,
        val serverTime: Instant,
        val activeGrantCount: Int,
        val confirmedAtElapsedRealtime: Long,
    ) : MembershipQueryResult

    /** Server explicitly reported NONE / EXPIRED / REVOKED. */
    data class ConfirmedNotEntitled(
        override val accountId: Long,
        override val generation: Long,
        val serverStatus: String,
        val serverTime: Instant?,
        val confirmedAtElapsedRealtime: Long,
    ) : MembershipQueryResult

    /** Cannot confirm membership (network, 5xx, 403, malformed payload, unknown status, …). */
    data class TemporarilyUnavailable(
        override val accountId: Long,
        override val generation: Long,
        val reason: MembershipUnavailableReason,
    ) : MembershipQueryResult

    /** HTTP 401 or business codes 40100/40101/40102. Does not clear AccountSession here. */
    data class SessionInvalid(
        override val accountId: Long,
        override val generation: Long,
        val httpStatus: Int?,
        val businessCode: Int?,
    ) : MembershipQueryResult
}

enum class MembershipUnavailableReason {
    NETWORK,
    TIMEOUT,
    HTTP_FORBIDDEN,
    HTTP_SERVER_ERROR,
    HTTP_OTHER,
    BUSINESS_ERROR,
    INVALID_JSON,
    MISSING_DATA,
    UNKNOWN_STATUS,
    ACTIVE_MISSING_EXPIRY,
    ACTIVE_EXPIRY_NOT_AFTER_SERVER_TIME,
    CANCELLED,
}
