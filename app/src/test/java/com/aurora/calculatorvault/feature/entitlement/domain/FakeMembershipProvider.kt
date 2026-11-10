package com.aurora.calculatorvault.feature.entitlement.domain

/**
 * Test-only VIP injector. Every positive VIP case in automated tests must use this
 * (or an equivalent Fake) and must be labeled as Fake — not real server membership.
 * Lives in the JVM `test` source set so it is never compiled into any app variant.
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
