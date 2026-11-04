package com.aurora.calculatorvault.feature.entitlement.domain

import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.AccountSessionRepository
import com.aurora.calculatorvault.feature.trial.DeviceTrialRepository
import com.aurora.calculatorvault.feature.trial.DeviceTrialState
import com.aurora.calculatorvault.feature.trial.TrialStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Application-scoped entitlement truth.
 * Combines device trial, account session, and [MembershipProvider] without mutating those sources.
 */
class EntitlementRepository(
    private val deviceTrial: DeviceTrialRepository,
    private val accountSession: AccountSessionRepository,
    private val membershipProvider: MembershipProvider,
    private val elapsedRealtime: () -> Long,
    private val wallClockEpochMillis: () -> Long,
    scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(EntitlementState())
    val state: StateFlow<EntitlementState> = mutableState.asStateFlow()

    private var accountGeneration: Long = 0L
    private var lastAuthenticatedAccountId: Long? = null

    /**
     * Optional process-local state supplier used only by androidTest.
     * Production wiring never assigns this. When non-null, [computeState] returns the
     * supplied snapshot and does not invent VIP/trial rules itself — Fake VIP construction
     * must live in the test source set.
     */
    @Volatile
    var stateOverride: (() -> EntitlementState?)? = null

    /**
     * Optional process-local ActionPolicy gate used only by androidTest (e.g. mid-batch budget).
     * When non-null and returns non-null, [ActionPolicy.evaluate] returns that decision and
     * skips decide(). Production never assigns this — not a public Fake VIP entry point.
     */
    @Volatile
    var actionDecisionOverride: ((EntitlementCapability) -> ActionDecision?)? = null

    val actionPolicy: ActionPolicy = object : ActionPolicy {
        override fun evaluate(capability: EntitlementCapability): ActionDecision {
            actionDecisionOverride?.invoke(capability)?.let { return it }
            // Re-read trial with trusted clock before paid checks so expiry is not delayed to the UI ticker.
            if (capability.requiresPaidEntitlement()) {
                deviceTrial.refresh()
            }
            val trial = deviceTrial.state.value
            val session = accountSession.state.value
            val snap = computeState(trial, session, elapsedRealtime(), wallClockEpochMillis())
            mutableState.value = snap
            return decide(capability, resolveEffectiveSource(snap, elapsedRealtime(), wallClockEpochMillis()))
        }
    }

    init {
        // Optional membership revision ticks (ServerMembershipProvider); Default/Fake ignore.
        val membershipRevision =
            (membershipProvider as? MembershipStateSignal)?.membershipStateRevision ?: flowOf(0L)
        combine(deviceTrial.state, accountSession.state, membershipRevision) { trial, session, _ ->
            computeState(trial, session, elapsedRealtime(), wallClockEpochMillis())
        }.onEach { mutableState.value = it }.launchIn(scope)
    }

    /** Forces a recompute from current upstream sources (tests / foreground resume). */
    fun refresh() {
        deviceTrial.refresh()
        mutableState.value = computeState(
            deviceTrial.state.value,
            accountSession.state.value,
            elapsedRealtime(),
            wallClockEpochMillis(),
        )
    }

    fun currentAccountGeneration(): Long = accountGeneration

    private fun trackAccountGeneration(session: AccountSession) {
        when (session) {
            is AccountSession.Authenticated -> {
                val id = session.account.userId
                if (lastAuthenticatedAccountId != null && lastAuthenticatedAccountId != id) {
                    accountGeneration++
                }
                lastAuthenticatedAccountId = id
            }
            AccountSession.Guest,
            AccountSession.SessionExpired,
            AccountSession.LoginFailed,
            AccountSession.StorageFailed,
            -> {
                if (lastAuthenticatedAccountId != null) {
                    accountGeneration++
                    lastAuthenticatedAccountId = null
                }
            }
            AccountSession.Authenticating,
            AccountSession.Validating,
            AccountSession.TemporarilyUnavailable,
            -> {
                // VIP candidate paused; keep generation so a Fake claim for this generation
                // cannot apply until Authenticated again, and logout still bumps generation.
            }
        }
    }

    private fun computeState(
        trial: DeviceTrialState,
        session: AccountSession,
        nowElapsed: Long,
        nowWall: Long,
    ): EntitlementState {
        trackAccountGeneration(session)
        stateOverride?.invoke()?.let { return it }
        val accountId = (session as? AccountSession.Authenticated)?.account?.userId
        val trialGrant = resolveTrialGrant(trial)
        val vip = resolveVip(session, accountId, accountGeneration, nowWall)
        val source = when {
            vip != null -> vip
            trialGrant != null -> trialGrant
            else -> EntitlementSource.None
        }
        return EntitlementState(
            source = source,
            trialStatus = trial.status,
            trialRemainingMillis = trial.remainingMillis,
            trialDeadlineElapsedMillis = trialGrant?.let { nowElapsed + trial.remainingMillis },
            accountId = accountId,
            accountGeneration = accountGeneration,
            vipExpiresAtEpochMillis = (vip as? EntitlementSource.Vip)?.expiresAtEpochMillis,
            vipPermanent = (vip as? EntitlementSource.Vip)?.permanent == true,
            vipPlanType = (vip as? EntitlementSource.Vip)?.planType,
            vipContributingPlanTypes = (vip as? EntitlementSource.Vip)?.contributingPlanTypes.orEmpty(),
            evaluatedAtElapsedMillis = nowElapsed,
        )
    }

    private fun resolveVip(
        session: AccountSession,
        accountId: Long?,
        generation: Long,
        nowWall: Long,
    ): EntitlementSource.Vip? {
        if (session !is AccountSession.Authenticated || accountId == null) return null
        return when (val result = membershipProvider.membershipFor(accountId, generation)) {
            is MembershipResult.VerifiedActive -> {
                if (result.accountId != accountId || result.generation != generation) return null
                if (!result.permanent) {
                    val expires = result.expiresAtEpochMillis ?: return null
                    if (expires <= nowWall) return null
                }
                EntitlementSource.Vip(
                    accountId = accountId,
                    expiresAtEpochMillis = result.expiresAtEpochMillis,
                    permanent = result.permanent,
                    planType = result.planType,
                    contributingPlanTypes = result.contributingPlanTypes,
                )
            }
            MembershipResult.NotEntitled,
            MembershipResult.Unavailable,
            MembershipResult.Unknown,
            -> null
        }
    }

    private fun resolveTrialGrant(trial: DeviceTrialState): EntitlementSource.DeviceTrial? {
        if (trial.remainingMillis <= 0L) return null
        if (!trial.status.grantsTrialCapability()) return null
        return EntitlementSource.DeviceTrial(trial.status, trial.remainingMillis)
    }

    companion object {
        fun resolveEffectiveSource(
            snap: EntitlementState,
            nowElapsed: Long,
            nowWall: Long,
        ): EntitlementSource {
            // VIP first — wall expiry is checked live so a Fake claim can lapse without a flow tick.
            when (val current = snap.source) {
                is EntitlementSource.Vip -> {
                    if (current.permanent) return current
                    val expires = current.expiresAtEpochMillis
                    if (expires != null && expires > nowWall) return current
                    // Expired VIP falls through to trial / free atomically (no Free flash).
                }
                is EntitlementSource.DeviceTrial -> Unit
                // Explicit None must win; do not rebuild from leftover trialRemaining fields.
                EntitlementSource.None -> return EntitlementSource.None
            }
            val deadline = snap.trialDeadlineElapsedMillis
            if (deadline != null &&
                nowElapsed < deadline &&
                snap.trialStatus.grantsTrialCapability()
            ) {
                val remaining = deadline - nowElapsed
                if (remaining > 0L) {
                    return EntitlementSource.DeviceTrial(snap.trialStatus, remaining)
                }
            }
            if (snap.trialRemainingMillis > 0L && snap.trialStatus.grantsTrialCapability()) {
                return EntitlementSource.DeviceTrial(snap.trialStatus, snap.trialRemainingMillis)
            }
            return EntitlementSource.None
        }

        fun decide(
            capability: EntitlementCapability,
            source: EntitlementSource,
        ): ActionDecision {
            if (!capability.requiresPaidEntitlement()) return ActionDecision.Allowed
            return when (source) {
                is EntitlementSource.Vip,
                is EntitlementSource.DeviceTrial,
                -> ActionDecision.Allowed
                EntitlementSource.None -> ActionDecision.Denied(DenialReason.REQUIRES_ENTITLEMENT)
            }
        }
    }
}

fun interface ActionPolicy {
    fun evaluate(capability: EntitlementCapability): ActionDecision
}

private fun EntitlementCapability.requiresPaidEntitlement(): Boolean = when (this) {
    EntitlementCapability.APP_MANAGEMENT,
    EntitlementCapability.PRIVATE_ALBUM_VIEW_RESTORE,
    EntitlementCapability.PRIVATE_ALBUM_MANAGE_EXISTING,
    -> false
    EntitlementCapability.APP_LOCK_PROTECTION,
    EntitlementCapability.PRIVATE_ALBUM_IMPORT,
    -> true
}
