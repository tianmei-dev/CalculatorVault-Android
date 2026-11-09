package com.aurora.calculatorvault.feature.membership.data

import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementRepository
import com.aurora.calculatorvault.feature.entitlement.domain.MembershipResult
import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.AccountSessionRepository
import com.aurora.calculatorvault.feature.login.session.entitlementAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why a membership /me refresh was requested. */
enum class MembershipRefreshReason {
    LoginSuccess,
    SessionRestore,
    Foreground,
    Manual,
    PurchaseFulfilled,
}

/** Settings / account-row sync presentation (display only). */
sealed interface MembershipSyncUi {
    data object Idle : MembershipSyncUi
    data object Syncing : MembershipSyncUi
    /** No trusted VIP and query failed / unavailable. */
    data object Failed : MembershipSyncUi
    /** Sticky VIP retained while sync failed. */
    data object Anomaly : MembershipSyncUi
}

/**
 * App-scoped membership refresh orchestration (I3.4.3-C).
 *
 * Owns LoginSuccess / SessionRestore / Foreground(5m) / Manual refresh,
 * SessionInvalid → AccountSession linkage, and logout VIP clear.
 * Does not invent VIP; only [ServerMembershipProvider] may confirm ACTIVE.
 */
class MembershipRefreshCoordinator(
    private val accountSession: AccountSessionRepository,
    private val entitlementRepository: EntitlementRepository,
    private val serverMembership: ServerMembershipProvider,
    private val scope: CoroutineScope,
    private val elapsedRealtime: () -> Long,
    private val baseUrlConfigured: () -> Boolean = { true },
    private val foregroundThrottleMillis: Long = DEFAULT_FOREGROUND_THROTTLE_MILLIS,
) {
    private val syncMutable = MutableStateFlow<MembershipSyncUi>(MembershipSyncUi.Idle)
    val syncState: StateFlow<MembershipSyncUi> = syncMutable.asStateFlow()

    private var started = false
    private var previousSession: AccountSession = AccountSession.Guest
    private var authenticatedBeforeValidation = false
    private var recoveringFromUnavailable = false

    private var lastForegroundAtElapsed = 0L
    private var lastForegroundKey: Pair<Long, Long>? = null

    private var refreshJob: Job? = null
    private var manualInFlight = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            accountSession.state.collect { session ->
                onSessionChanged(session)
            }
        }
        scope.launch {
            serverMembership.sessionInvalidEvents.collect { event ->
                onSessionInvalid(event)
            }
        }
    }

    /** Process foreground: Authenticated only; 5-minute throttle. */
    fun onForeground() {
        if (accountSession.state.value !is AccountSession.Authenticated) return
        requestRefresh(MembershipRefreshReason.Foreground)
    }

    /**
     * Settings manual refresh. Bypasses foreground throttle.
     * @return false when not logged in or a refresh is already in flight.
     */
    fun refreshManual(): Boolean {
        if (accountSession.state.value !is AccountSession.Authenticated) return false
        if (manualInFlight || syncMutable.value is MembershipSyncUi.Syncing) return false
        requestRefresh(MembershipRefreshReason.Manual)
        return true
    }

    /**
     * Server reported a purchase FULFILLED: query /membership/me now, bypassing the foreground
     * throttle and the manual-tap guard. Same-key in-flight requests are still deduplicated by
     * the provider. VIP is granted only by the provider's confirmed result.
     */
    fun refreshAfterPurchase(): Boolean {
        if (accountSession.state.value !is AccountSession.Authenticated) return false
        requestRefresh(MembershipRefreshReason.PurchaseFulfilled)
        return true
    }

    private fun onSessionChanged(session: AccountSession) {
        val prev = previousSession
        when (session) {
            is AccountSession.Validating -> {
                authenticatedBeforeValidation = session.trustedAccount != null
                if (session.trustedAccount == null && prev.entitlementAccount != null) {
                    // Unknown identity replaces a known one: drop old claims and in-flight results.
                    clearForBoundary()
                }
                previousSession = session
            }
            is AccountSession.Authenticated -> {
                if (prev is AccountSession.Authenticated) {
                    previousSession = session
                    return
                }
                val reason = when {
                    prev == AccountSession.Authenticating -> MembershipRefreshReason.LoginSuccess
                    // Network came back after a transient failure: reconfirm now, not throttled.
                    recoveringFromUnavailable -> MembershipRefreshReason.SessionRestore
                    prev is AccountSession.Validating && authenticatedBeforeValidation ->
                        MembershipRefreshReason.Foreground
                    else -> MembershipRefreshReason.SessionRestore
                }
                recoveringFromUnavailable = false
                previousSession = session
                requestRefresh(reason)
            }
            AccountSession.Guest,
            AccountSession.SessionExpired,
            AccountSession.LoginFailed,
            AccountSession.StorageFailed,
            -> {
                previousSession = session
                authenticatedBeforeValidation = false
                recoveringFromUnavailable = false
                clearForBoundary()
            }
            is AccountSession.TemporarilyUnavailable -> {
                recoveringFromUnavailable = true
                previousSession = session
            }
            AccountSession.Authenticating -> {
                previousSession = session
            }
        }
    }

    private fun clearForBoundary() {
        serverMembership.clearAll()
        entitlementRepository.refresh()
        syncMutable.value = MembershipSyncUi.Idle
        manualInFlight = false
    }

    private fun onSessionInvalid(event: MembershipSessionInvalidEvent) {
        // Includes a trusted account under re-validation; a different account is ignored.
        val identity = accountSession.state.value.entitlementAccount ?: return
        if (identity.userId != event.accountId) return
        if (entitlementRepository.currentAccountGeneration() != event.generation) return
        serverMembership.clearAccount(event.accountId, event.generation)
        accountSession.notifyRemoteSessionInvalid(event.accountId)
        entitlementRepository.refresh()
        syncMutable.value = MembershipSyncUi.Idle
    }

    private fun requestRefresh(reason: MembershipRefreshReason) {
        val session = accountSession.state.value as? AccountSession.Authenticated ?: return
        val token = accountSession.peekAccessToken() ?: return
        entitlementRepository.refresh()
        val accountId = session.account.userId
        val generation = entitlementRepository.currentAccountGeneration()

        if (reason == MembershipRefreshReason.Foreground) {
            val now = elapsedRealtime()
            val key = accountId to generation
            if (lastForegroundKey == key &&
                lastForegroundAtElapsed > 0L &&
                now - lastForegroundAtElapsed < foregroundThrottleMillis
            ) {
                return
            }
        }

        if (!baseUrlConfigured()) {
            syncMutable.value = if (hasTrustedVip(accountId, generation)) {
                MembershipSyncUi.Anomaly
            } else {
                MembershipSyncUi.Failed
            }
            return
        }

        if (reason == MembershipRefreshReason.Foreground) {
            lastForegroundAtElapsed = elapsedRealtime()
            lastForegroundKey = accountId to generation
        }

        if (reason == MembershipRefreshReason.Manual) {
            manualInFlight = true
        }
        syncMutable.value = MembershipSyncUi.Syncing

        refreshJob = scope.launch {
            try {
                val result = serverMembership.refresh(
                    MembershipQueryScope(
                        accountId = accountId,
                        generation = generation,
                        accessToken = token,
                    ),
                )
                entitlementRepository.refresh()
                syncMutable.value = when (result) {
                    is MembershipQueryResult.ConfirmedActive,
                    is MembershipQueryResult.ConfirmedNotEntitled,
                    -> MembershipSyncUi.Idle
                    is MembershipQueryResult.TemporarilyUnavailable ->
                        if (hasTrustedVip(accountId, generation)) {
                            MembershipSyncUi.Anomaly
                        } else {
                            MembershipSyncUi.Failed
                        }
                    is MembershipQueryResult.SessionInvalid -> MembershipSyncUi.Idle
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                syncMutable.value = if (hasTrustedVip(accountId, generation)) {
                    MembershipSyncUi.Anomaly
                } else {
                    MembershipSyncUi.Failed
                }
            } finally {
                if (reason == MembershipRefreshReason.Manual) {
                    manualInFlight = false
                }
            }
        }
    }

    private fun hasTrustedVip(accountId: Long, generation: Long): Boolean =
        serverMembership.membershipFor(accountId, generation) is MembershipResult.VerifiedActive

    companion object {
        const val DEFAULT_FOREGROUND_THROTTLE_MILLIS: Long = 5L * 60L * 1_000L
    }
}
