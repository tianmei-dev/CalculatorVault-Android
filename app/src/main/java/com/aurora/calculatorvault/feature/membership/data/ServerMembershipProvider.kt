package com.aurora.calculatorvault.feature.membership.data

import com.aurora.calculatorvault.feature.entitlement.domain.MembershipProvider
import com.aurora.calculatorvault.feature.entitlement.domain.MembershipResult
import com.aurora.calculatorvault.feature.entitlement.domain.MembershipStateSignal
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Server-backed membership cache with 24h sticky VIP (I3.4.3-B/C).
 * Production wiring: [com.aurora.calculatorvault.app.CalculatorVaultApp].
 */
class ServerMembershipProvider(
    private val api: MembershipApi,
    private val scope: CoroutineScope,
    private val elapsedRealtime: () -> Long,
    private val offlineWindowMillis: Long = DEFAULT_OFFLINE_WINDOW_MILLIS,
) : MembershipProvider, MembershipStateSignal {

    private val lock = Any()
    private val mutex = Mutex()

    private val revision = MutableStateFlow(0L)
    override val membershipStateRevision: StateFlow<Long> = revision.asStateFlow()

    private val sessionInvalidMutable = MutableSharedFlow<MembershipSessionInvalidEvent>(
        extraBufferCapacity = 16,
    )
    val sessionInvalidEvents: SharedFlow<MembershipSessionInvalidEvent> =
        sessionInvalidMutable.asSharedFlow()

    @Volatile
    private var sticky: StickyVip? = null

    @Volatile
    private var phase: Phase = Phase.NeverQueried

    private var inFlight: Deferred<MembershipQueryResult>? = null
    private var inFlightKey: QueryKey? = null
    private val applyEpoch = AtomicLong(0L)
    private var expireJob: Job? = null

    override fun membershipFor(accountId: Long?, generation: Long): MembershipResult {
        if (accountId == null) return MembershipResult.NotEntitled
        synchronized(lock) {
            return evaluateLocked(accountId, generation, bumpOnTransition = true)
        }
    }

    /**
     * Async /me refresh. Same accountId+generation shares one in-flight call.
     * Late results after [clearAll]/[clearAccount] (epoch bump) are not applied.
     */
    suspend fun refresh(query: MembershipQueryScope): MembershipQueryResult {
        require(query.accountId > 0L)
        require(query.generation >= 0L)
        val key = QueryKey(query.accountId, query.generation)
        val epochAtStart = applyEpoch.get()

        val deferred = mutex.withLock {
            val existing = inFlight
            if (existing != null && inFlightKey == key && existing.isActive) {
                existing
            } else {
                val created = scope.async {
                    val result = api.queryMe(query)
                    applyQueryResult(result, expectedEpoch = epochAtStart)
                    result
                }
                inFlight = created
                inFlightKey = key
                created
            }
        }

        try {
            return deferred.await()
        } finally {
            mutex.withLock {
                if (inFlight === deferred) {
                    inFlight = null
                    inFlightKey = null
                }
            }
        }
    }

    /** Immediate clear for a specific account generation (logout / switch). */
    fun clearAccount(accountId: Long, generation: Long) {
        synchronized(lock) {
            val current = sticky
            if (current != null &&
                current.accountId == accountId &&
                current.generation == generation
            ) {
                clearStickyLocked()
            } else if (current != null && current.generation == generation) {
                clearStickyLocked()
            } else {
                // Still clear sticky if generation matches OR always on explicit account clear
                clearStickyLocked()
            }
            phase = Phase.Cleared
            cancelExpireJobLocked()
            bumpRevisionLocked()
        }
        invalidateInFlight()
    }

    /** Clears any cached VIP (logout). */
    fun clearAll() {
        synchronized(lock) {
            clearStickyLocked()
            phase = Phase.Cleared
            cancelExpireJobLocked()
            bumpRevisionLocked()
        }
        invalidateInFlight()
    }

    private fun invalidateInFlight() {
        applyEpoch.incrementAndGet()
        inFlight?.cancel()
    }

    private fun applyQueryResult(result: MembershipQueryResult, expectedEpoch: Long) {
        if (applyEpoch.get() != expectedEpoch) return
        when (result) {
            is MembershipQueryResult.ConfirmedActive -> {
                synchronized(lock) {
                    if (applyEpoch.get() != expectedEpoch) return
                    sticky = StickyVip(
                        accountId = result.accountId,
                        generation = result.generation,
                        serverTimeEpochMillis = result.serverTime.toEpochMilli(),
                        confirmedAtElapsedRealtime = result.confirmedAtElapsedRealtime,
                        expiresAtEpochMillis = result.expiresAt?.toEpochMilli(),
                        isLifetime = result.isLifetime,
                        planType = result.planType,
                        contributingPlanTypes = result.contributingPlanTypes,
                    )
                    phase = Phase.ConfirmedActive
                    scheduleExpireJobLocked()
                    bumpRevisionLocked()
                }
            }
            is MembershipQueryResult.ConfirmedNotEntitled -> {
                synchronized(lock) {
                    if (applyEpoch.get() != expectedEpoch) return
                    val current = sticky
                    if (current != null &&
                        (current.accountId != result.accountId ||
                            current.generation != result.generation)
                    ) {
                        return
                    }
                    clearStickyLocked()
                    phase = Phase.ConfirmedNotEntitled
                    cancelExpireJobLocked()
                    bumpRevisionLocked()
                }
            }
            is MembershipQueryResult.TemporarilyUnavailable -> {
                synchronized(lock) {
                    if (applyEpoch.get() != expectedEpoch) return
                    if (phase == Phase.NeverQueried || phase == Phase.Cleared) {
                        phase = Phase.Unavailable
                        bumpRevisionLocked()
                    }
                    // Sticky retained; confirmedAt not refreshed.
                }
            }
            is MembershipQueryResult.SessionInvalid -> {
                synchronized(lock) {
                    if (applyEpoch.get() != expectedEpoch) return
                    val current = sticky
                    if (current != null &&
                        (current.accountId != result.accountId ||
                            current.generation != result.generation)
                    ) {
                        return
                    }
                    // Also accept SessionInvalid when sticky already empty but generation matches request.
                    clearStickyLocked()
                    phase = Phase.SessionInvalid
                    cancelExpireJobLocked()
                    bumpRevisionLocked()
                }
                sessionInvalidMutable.tryEmit(
                    MembershipSessionInvalidEvent(
                        accountId = result.accountId,
                        generation = result.generation,
                        httpStatus = result.httpStatus,
                        businessCode = result.businessCode,
                    ),
                )
            }
        }
    }

    private fun evaluateLocked(
        accountId: Long,
        generation: Long,
        bumpOnTransition: Boolean,
    ): MembershipResult {
        val nowElapsed = elapsedRealtime()
        val current = sticky
        if (current == null) {
            return when (phase) {
                Phase.NeverQueried -> MembershipResult.Unknown
                Phase.ConfirmedNotEntitled, Phase.Cleared, Phase.SessionInvalid ->
                    MembershipResult.NotEntitled
                Phase.Unavailable, Phase.ConfirmedActive -> MembershipResult.Unavailable
            }
        }
        if (current.accountId != accountId || current.generation != generation) {
            return MembershipResult.NotEntitled
        }
        if (nowElapsed < current.confirmedAtElapsedRealtime) {
            clearStickyLocked()
            phase = Phase.Unavailable
            if (bumpOnTransition) bumpRevisionLocked()
            cancelExpireJobLocked()
            return MembershipResult.Unavailable
        }
        val offlineDeadline = current.confirmedAtElapsedRealtime + offlineWindowMillis
        if (nowElapsed >= offlineDeadline) {
            clearStickyLocked()
            phase = Phase.Unavailable
            if (bumpOnTransition) bumpRevisionLocked()
            cancelExpireJobLocked()
            return MembershipResult.Unavailable
        }
        val estimatedServerNow =
            current.serverTimeEpochMillis + (nowElapsed - current.confirmedAtElapsedRealtime)
        if (!current.isLifetime) {
            val expires = current.expiresAtEpochMillis
            if (expires == null || estimatedServerNow >= expires) {
                clearStickyLocked()
                phase = Phase.Unavailable
                if (bumpOnTransition) bumpRevisionLocked()
                cancelExpireJobLocked()
                return MembershipResult.Unavailable
            }
        }
        return MembershipResult.VerifiedActive(
            accountId = current.accountId,
            expiresAtEpochMillis = if (current.isLifetime) null else current.expiresAtEpochMillis,
            generation = current.generation,
            planType = current.planType,
            contributingPlanTypes = current.contributingPlanTypes,
        )
    }

    private fun scheduleExpireJobLocked() {
        cancelExpireJobLocked()
        val current = sticky ?: return
        val now = elapsedRealtime()
        var delayMs = current.confirmedAtElapsedRealtime + offlineWindowMillis - now
        if (!current.isLifetime) {
            val expires = current.expiresAtEpochMillis ?: return
            val estimatedNow =
                current.serverTimeEpochMillis + (now - current.confirmedAtElapsedRealtime)
            delayMs = minOf(delayMs, expires - estimatedNow)
        }
        if (delayMs <= 0L) {
            evaluateLocked(current.accountId, current.generation, bumpOnTransition = true)
            return
        }
        val accountId = current.accountId
        val generation = current.generation
        expireJob = scope.launch {
            delay(delayMs)
            synchronized(lock) {
                val s = sticky ?: return@synchronized
                if (s.accountId != accountId || s.generation != generation) return@synchronized
                evaluateLocked(accountId, generation, bumpOnTransition = true)
            }
        }
    }

    private fun clearStickyLocked() {
        sticky = null
    }

    private fun cancelExpireJobLocked() {
        expireJob?.cancel()
        expireJob = null
    }

    private fun bumpRevisionLocked() {
        revision.value = revision.value + 1L
    }

    private data class StickyVip(
        val accountId: Long,
        val generation: Long,
        val serverTimeEpochMillis: Long,
        val confirmedAtElapsedRealtime: Long,
        val expiresAtEpochMillis: Long?,
        val isLifetime: Boolean,
        val planType: String?,
        val contributingPlanTypes: List<String>,
    )

    private data class QueryKey(val accountId: Long, val generation: Long)

    private enum class Phase {
        NeverQueried,
        ConfirmedActive,
        ConfirmedNotEntitled,
        Unavailable,
        SessionInvalid,
        Cleared,
    }

    companion object {
        const val DEFAULT_OFFLINE_WINDOW_MILLIS: Long = 24L * 60L * 60L * 1_000L
    }
}

data class MembershipSessionInvalidEvent(
    val accountId: Long,
    val generation: Long,
    val httpStatus: Int?,
    val businessCode: Int?,
)
