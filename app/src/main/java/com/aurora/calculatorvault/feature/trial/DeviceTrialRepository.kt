package com.aurora.calculatorvault.feature.trial

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min
import com.aurora.calculatorvault.feature.login.session.TrialTrace

const val TRIAL_MILLIS = 72L * 60 * 60 * 1000
interface TrialClock { fun wall(): Long; fun elapsed(): Long; fun boot(): String? }
fun interface DeviceIdentityProvider { fun deviceKey(): String? }
interface TrialStore { fun read(): TrialRecord?; fun write(record: TrialRecord); fun seen(): Boolean; fun markSeen() }
data class TrialRecord(
    val firstOpened: Long, val remaining: Long, val wall: Long, val elapsed: Long,
    val boot: String?, val expired: Boolean = false, val serverTime: Long? = null,
    val serverStarted: Long? = null, val serverExpires: Long? = null,
    val verified: Boolean = false, val uncertain: Boolean = false,
)
data class TrialReply(val status: String, val started: Long, val expires: Long, val serverTime: Long, val remaining: Long)
fun interface TrialApi {
    /** [remainingSecondsCap] null = first claim (server assigns full duration); 0 = no remaining. */
    suspend fun activate(deviceKey: String, remainingSecondsCap: Long?): TrialReply
}
class TrialRateLimited : Exception("Trial rate limited")
enum class TrialStatus { NOT_INITIALIZED, ACTIVE, EXPIRED, PENDING_SYNC, TEMPORARILY_UNAVAILABLE, VERIFICATION_REQUIRED }
data class DeviceTrialState(val status: TrialStatus = TrialStatus.NOT_INITIALIZED, val remainingMillis: Long = 0)

/**
 * Device trial is server-authoritative for the first grant. Local storage only keeps a confirmed
 * countdown (and migration leftovers that must not grant until [verified]).
 */
class DeviceTrialRepository(
    private val store: TrialStore, private val clock: TrialClock,
    private val identity: DeviceIdentityProvider, private val api: TrialApi,
    private val scope: CoroutineScope, private val consent: suspend () -> Boolean,
    private val uploadEnabled: () -> Boolean,
) {
    private val mutableState = MutableStateFlow(DeviceTrialState())
    val state = mutableState.asStateFlow()
    private val firstGrantMutable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits once when this process first receives a server-confirmed trial grant. */
    val firstTrialGrantEvents: SharedFlow<Unit> = firstGrantMutable.asSharedFlow()
    private var record: TrialRecord? = null

    // A server timestamp is retained after expiry, independently of the current grant status.
    internal fun hasVerifiedTrialResponse(): Boolean = record?.serverTime != null
    private var initialized = false
    private var unavailable = false
    private var processAnchor = false
    private var syncing = false
    private var nextAttempt = 0L
    private var failures = 0
    private var lastSync = Long.MIN_VALUE
    /** True when local store already had a verified or expired trial before this process. */
    var hadPriorServerTrial: Boolean = false
        private set

    fun initialize() {
        if (initialized) return
        initialized = true
        try {
            record = store.read()
            // Never mint a fresh unverified 72h grant here. Legacy unverified rows stay on disk
            // for migration (cap on first activate) but do not authorize until the server confirms.
            if (record != null) store.markSeen()
            hadPriorServerTrial = record?.let { it.verified || it.expired } == true
            refresh()
        } catch (_: Exception) { record = null; restricted() }
    }

    fun refresh() {
        val old = record ?: run { restricted(); return }
        try {
            require(old.remaining in 0..TRIAL_MILLIS)
            val wall = clock.wall(); val elapsed = clock.elapsed(); val boot = clock.boot()
            val sameBoot = (processAnchor || (boot != null && boot == old.boot)) && elapsed >= old.elapsed
            val wallDelta = wall - old.wall
            val uncertain = old.uncertain || (!sameBoot && (boot == null || old.boot == null || wallDelta < -120_000))
            val consumed = if (sameBoot) elapsed - old.elapsed else wallDelta.coerceAtLeast(0)
            var remaining = (old.remaining - consumed).coerceAtLeast(0)
            // Across reboot, never outlive the server deadline using wall clock. On the same boot,
            // elapsed is the trusted meter (wall changes must not extend; they also must not
            // falsely zero a still-valid elapsed countdown before the next sync).
            if (!sameBoot && old.verified && old.serverExpires != null) {
                remaining = min(remaining, (old.serverExpires - wall).coerceAtLeast(0))
            }
            val updated = old.copy(remaining = remaining, wall = wall, elapsed = elapsed, boot = boot,
                expired = old.expired || remaining == 0L, uncertain = uncertain)
            store.write(updated); record = updated; processAnchor = true
            publish(updated)
            TrialTrace.event(9, mutableState.value.status.ordinal)
        } catch (_: Exception) { record = null; restricted() }
    }

    fun startTicker() { scope.launch { while (true) { delay(30_000); refresh() } } }

    fun sync() {
        TrialTrace.event(0)
        initialize()
        if (syncing || clock.elapsed() < nextAttempt ||
            (lastSync != Long.MIN_VALUE && clock.elapsed() - lastSync < 15 * 60_000)) return
        syncing = true
        scope.launch {
            try {
                if (!uploadEnabled() || !consent()) return@launch
                val key = try { identity.deviceKey() } catch (_: Exception) { null }
                if (key.isNullOrBlank()) { restricted(); return@launch }
                if (!consent() || !uploadEnabled()) return@launch
                refresh()
                val requestStarted = clock.elapsed()
                val current = record
                // null = first claim; 0 = lost/expired/uncertain history must not widen the grant.
                val cap: Long? = when {
                    current == null && store.seen() -> 0L
                    current == null -> null
                    current.expired || current.uncertain -> 0L
                    else -> (current.remaining.coerceIn(0, TRIAL_MILLIS) / 1000)
                }
                TrialTrace.event(2)
                val response = api.activate(key, cap)
                if (!consent() || !uploadEnabled()) return@launch
                TrialTrace.event(7)
                validate(response)
                refresh()
                val old = record
                // Trusted server expiry must win even when cached trialStartedAt drifted (e.g. prior
                // UTC session skew of ±8h). Do not leave the device stuck in VERIFICATION_REQUIRED.
                val serverExpired = response.status == "EXPIRED" && response.remaining == 0L
                val startedSkew = old?.serverStarted != null && (
                    old.serverStarted != response.started ||
                        response.serverTime < (old.serverTime ?: response.serverTime) - 120_000
                    )
                if (startedSkew && !serverExpired) {
                    // ACTIVE (or non-expired) reply with inconsistent anchors — keep fail-closed.
                    record = old!!.copy(uncertain = true)
                    store.write(record!!)
                    nextAttempt = clock.elapsed() + 15 * 60_000
                    if (!old.expired) restricted()
                    return@launch
                }
                val latency = (clock.elapsed() - requestStarted).coerceAtLeast(0)
                val cappedRemaining = if (cap == null) {
                    response.remaining
                } else {
                    min(response.remaining, cap * 1000)
                }
                val serverRemaining = (cappedRemaining - latency).coerceAtLeast(0)
                val remaining = if (old == null) serverRemaining else min(old.remaining, serverRemaining)
                val expired = old?.expired == true || serverExpired || remaining == 0L
                val updated = TrialRecord(
                    firstOpened = old?.firstOpened ?: response.started,
                    remaining = if (expired) 0 else remaining,
                    wall = clock.wall(),
                    elapsed = clock.elapsed(),
                    boot = clock.boot(),
                    expired = expired,
                    serverTime = response.serverTime,
                    // Adopt server anchors after a trusted reply (including EXPIRED after TZ repair).
                    serverStarted = response.started,
                    serverExpires = min(old?.serverExpires ?: response.expires, response.expires),
                    // Successful activate always clears local uncertainty; expiry ends capability.
                    verified = !expired,
                    uncertain = false,
                )
                val firstVerifiedGrant = !expired && updated.verified && (old == null || !old.verified)
                store.markSeen(); store.write(updated); record = updated; processAnchor = true
                lastSync = clock.elapsed(); failures = 0; unavailable = false
                refresh()
                if (firstVerifiedGrant) firstGrantMutable.tryEmit(Unit)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                TrialTrace.event(8, when (failure) {
                    is java.net.SocketTimeoutException -> 1
                    is java.net.ConnectException -> 2
                    is java.net.UnknownHostException -> 3
                    is IllegalArgumentException -> 4
                    is java.io.IOException -> 5
                    else -> 0
                })
                refresh()
                unavailable = true
                failures = (failures + 1).coerceAtMost(8)
                nextAttempt = clock.elapsed() + if (failure is TrialRateLimited) 15 * 60_000 else min(15 * 60_000L, 30_000L * (1L shl failures))
                val current = record
                when {
                    current == null || current.uncertain || !current.verified -> {
                        // Unverified / missing: never authorize from a network error.
                        if (current != null && !current.expired) publish(current)
                        else restricted()
                    }
                    current.expired -> publish(current)
                    else -> {
                        // Verified + still time left: keep using offline remaining.
                        publish(current)
                        mutableState.value = DeviceTrialState(
                            TrialStatus.TEMPORARILY_UNAVAILABLE,
                            current.remaining,
                        )
                    }
                }
            } finally { syncing = false }
        }
    }

    private fun publish(updated: TrialRecord) {
        mutableState.value = DeviceTrialState(when {
            updated.expired -> TrialStatus.EXPIRED
            updated.uncertain -> TrialStatus.VERIFICATION_REQUIRED
            unavailable && updated.verified -> TrialStatus.TEMPORARILY_UNAVAILABLE
            updated.verified -> TrialStatus.ACTIVE
            else -> TrialStatus.PENDING_SYNC
        }, when {
            updated.uncertain -> 0
            updated.expired -> 0
            // Unverified leftovers may keep a countdown for migration display, but remaining
            // shown as 0 for capability (grantsTrialCapability excludes PENDING_SYNC).
            !updated.verified -> updated.remaining
            else -> updated.remaining
        })
    }

    private fun validate(reply: TrialReply) {
        require(reply.status in setOf("ACTIVE", "EXPIRED"))
        require(reply.expires >= reply.started && reply.expires - reply.started <= TRIAL_MILLIS)
        require(reply.remaining in 0..TRIAL_MILLIS)
        require(reply.remaining <= (reply.expires - reply.serverTime).coerceAtLeast(0))
        require(if (reply.status == "EXPIRED") reply.remaining == 0L && reply.serverTime >= reply.expires
                else reply.serverTime >= reply.started && reply.serverTime < reply.expires)
    }

    private fun restricted() { mutableState.value = DeviceTrialState(TrialStatus.VERIFICATION_REQUIRED) }
}
