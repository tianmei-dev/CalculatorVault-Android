package com.aurora.calculatorvault.feature.entitlement.domain

/**
 * Normalized VIP claim. Does not invent server JSON fields.
 * Production must never fabricate membership from login success or purchase UI.
 */
sealed interface MembershipResult {
    /** No verified membership for the current account scope. */
    data object NotEntitled : MembershipResult

    /** Provider temporarily cannot answer; never treat as active VIP (no offline grace). */
    data object Unavailable : MembershipResult

    /** Provider has not been consulted yet. */
    data object Unknown : MembershipResult

    /**
     * Server-confirmed (or Fake-injected) active membership bound to [accountId].
     * @param expiresAtEpochMillis wall-clock expiry; null = permanent.
     * @param generation account generation that issued this claim; stale generations are ignored.
     */
    data class VerifiedActive(
        val accountId: Long,
        val expiresAtEpochMillis: Long?,
        val generation: Long,
    ) : MembershipResult {
        val permanent: Boolean get() = expiresAtEpochMillis == null
    }
}

/**
 * VIP source reservation. Implementations must ignore claims that do not match
 * the supplied [accountId] / [generation].
 */
fun interface MembershipProvider {
    fun membershipFor(accountId: Long?, generation: Long): MembershipResult
}

/** Production default: never grants VIP. */
class DefaultMembershipProvider : MembershipProvider {
    override fun membershipFor(accountId: Long?, generation: Long): MembershipResult =
        MembershipResult.NotEntitled
}

/**
 * Test-only VIP injector. Every positive VIP case in automated tests must use this
 * (or an equivalent Fake) and must be labeled as Fake — not real server membership.
 */
class FakeMembershipProvider : MembershipProvider {
    @Volatile
    var result: MembershipResult = MembershipResult.NotEntitled

    override fun membershipFor(accountId: Long?, generation: Long): MembershipResult {
        if (accountId == null) return MembershipResult.NotEntitled
        val current = result
        return when (current) {
            is MembershipResult.VerifiedActive -> {
                if (current.accountId != accountId || current.generation != generation) {
                    MembershipResult.NotEntitled
                } else {
                    current
                }
            }
            else -> current
        }
    }

    fun clear() {
        result = MembershipResult.NotEntitled
    }

    fun grant(
        accountId: Long,
        generation: Long,
        expiresAtEpochMillis: Long? = null,
    ) {
        result = MembershipResult.VerifiedActive(accountId, expiresAtEpochMillis, generation)
    }
}
