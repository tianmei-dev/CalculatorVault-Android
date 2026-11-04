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
        /** Server planType for display; never used as authorization input. */
        val planType: String? = null,
        /** Active contributing plans when stacked; display-only. */
        val contributingPlanTypes: List<String> = emptyList(),
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

/**
 * Optional signal so [EntitlementRepository] can recompute when an async membership
 * provider updates or a sticky VIP window expires. Production Default never implements this.
 */
interface MembershipStateSignal {
    val membershipStateRevision: kotlinx.coroutines.flow.StateFlow<Long>
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
        planType: String? = if (expiresAtEpochMillis == null) "LIFETIME" else "MONTHLY",
        contributingPlanTypes: List<String> = listOfNotNull(planType),
    ) {
        result = MembershipResult.VerifiedActive(
            accountId = accountId,
            expiresAtEpochMillis = expiresAtEpochMillis,
            generation = generation,
            planType = planType,
            contributingPlanTypes = contributingPlanTypes,
        )
    }
}
