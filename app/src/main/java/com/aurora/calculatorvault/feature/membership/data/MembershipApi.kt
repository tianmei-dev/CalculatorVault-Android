package com.aurora.calculatorvault.feature.membership.data

/**
 * Membership query API (I3.4.2 /me). Separate from login [com.aurora.calculatorvault.feature.login.session.AuthApi].
 */
fun interface MembershipApi {
    /**
     * GET /api/v1/membership/me using [scope.accessToken].
     * Runs off the main thread. Propagates [kotlinx.coroutines.CancellationException].
     */
    suspend fun queryMe(scope: MembershipQueryScope): MembershipQueryResult
}
