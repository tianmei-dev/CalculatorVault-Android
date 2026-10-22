package com.aurora.calculatorvault.feature.trial

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

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
fun interface TrialApi { suspend fun activate(deviceKey: String): TrialReply }
class TrialRateLimited : Exception("Trial rate limited")
enum class TrialStatus { NOT_INITIALIZED, ACTIVE, EXPIRED, PENDING_SYNC, TEMPORARILY_UNAVAILABLE, VERIFICATION_REQUIRED }
data class DeviceTrialState(val status: TrialStatus = TrialStatus.NOT_INITIALIZED, val remainingMillis: Long = 0)

/** Independent of accounts and entitlements. All calls are serialized on the application scope. */
class DeviceTrialRepository(
    private val store: TrialStore, private val clock: TrialClock,
    private val identity: DeviceIdentityProvider, private val api: TrialApi,
    private val scope: CoroutineScope, private val consent: suspend () -> Boolean,
    private val uploadEnabled: () -> Boolean,
) {
    private val mutableState = MutableStateFlow(DeviceTrialState())
    val state = mutableState.asStateFlow()
    private var record: TrialRecord? = null
    private var initialized = false
    private var unavailable = false
    private var processAnchor = false
    private var syncing = false
    private var nextAttempt = 0L
    private var failures = 0
    private var lastSync = Long.MIN_VALUE

    fun initialize() {
        if (initialized) return
        initialized = true
        try {
            record = store.read()
            if (record == null && !store.seen()) {
                store.markSeen() // Persist before creating the grant; failed writes never grant again.
                record = TrialRecord(clock.wall(), TRIAL_MILLIS, clock.wall(), clock.elapsed(), clock.boot())
                store.write(record!!)
                processAnchor = true
            }
            if (record != null) store.markSeen()
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
            val remaining = (old.remaining - consumed).coerceAtLeast(0)
            val updated = old.copy(remaining = remaining, wall = wall, elapsed = elapsed, boot = boot,
                expired = old.expired || remaining == 0L, uncertain = uncertain)
            store.write(updated); record = updated; processAnchor = true
            mutableState.value = DeviceTrialState(when {
                updated.expired -> TrialStatus.EXPIRED
                uncertain -> TrialStatus.VERIFICATION_REQUIRED
                unavailable -> TrialStatus.TEMPORARILY_UNAVAILABLE
                updated.verified -> TrialStatus.ACTIVE
                else -> TrialStatus.PENDING_SYNC
            }, if (uncertain) 0 else remaining)
        } catch (_: Exception) { record = null; restricted() }
    }
    fun startTicker() { scope.launch { while (true) { delay(30_000); refresh() } } }
    fun sync() {
        initialize()
        if (syncing || clock.elapsed() < nextAttempt ||
            (lastSync != Long.MIN_VALUE && clock.elapsed() - lastSync < 15 * 60_000)) return
        syncing = true
        scope.launch {
            try {
                if (!uploadEnabled() || !consent()) return@launch
                val key = try { identity.deviceKey() } catch (_: Exception) { null }
                if (key.isNullOrBlank()) { restricted(); return@launch }
                refresh()
                val requestStarted = clock.elapsed()
                val response = api.activate(key)
                if (!consent() || !uploadEnabled()) return@launch
                validate(response)
                refresh()
                val old = record
                if (old?.serverStarted != null && (old.serverStarted != response.started ||
                    response.serverTime < (old.serverTime ?: response.serverTime) - 120_000)) {
                    record = old.copy(uncertain = true)
                    store.write(record!!)
                    nextAttempt = clock.elapsed() + 15 * 60_000
                    if (!old.expired) restricted()
                    return@launch
                }
                val serverRemaining = (response.remaining - (clock.elapsed() - requestStarted).coerceAtLeast(0)).coerceAtLeast(0)
                val remaining = if (old == null) serverRemaining else min(old.remaining, serverRemaining)
                val expired = old?.expired == true || response.status == "EXPIRED" || remaining == 0L
                val updated = TrialRecord(old?.firstOpened ?: response.started, if (expired) 0 else remaining,
                    clock.wall(), clock.elapsed(), clock.boot(), expired, response.serverTime,
                    response.started, response.expires, verified = old?.uncertain != true || old.verified, uncertain = old?.uncertain == true && !old.verified && !expired)
                store.markSeen(); store.write(updated); record = updated; processAnchor = true
                lastSync = clock.elapsed(); failures = 0; unavailable = false
                refresh()
            } catch (failure: Exception) {
                unavailable = true
                failures = (failures + 1).coerceAtMost(8)
                nextAttempt = clock.elapsed() + if (failure is TrialRateLimited) 15 * 60_000 else min(15 * 60_000L, 30_000L * (1L shl failures))
                if (record == null || record?.uncertain == true) restricted()
                else if (record?.expired != true) mutableState.value = DeviceTrialState(TrialStatus.TEMPORARILY_UNAVAILABLE, record!!.remaining)
            } finally { syncing = false }
        }
    }
    private fun validate(reply: TrialReply) {
        require(reply.status in setOf("ACTIVE", "EXPIRED"))
        require(reply.expires > reply.started && reply.expires - reply.started <= TRIAL_MILLIS)
        require(reply.remaining in 0..TRIAL_MILLIS)
        require(reply.remaining <= (reply.expires - reply.serverTime).coerceAtLeast(0))
        require(if (reply.status == "EXPIRED") reply.remaining == 0L && reply.serverTime >= reply.expires
                else reply.serverTime >= reply.started && reply.serverTime < reply.expires)
    }
    private fun restricted() { mutableState.value = DeviceTrialState(TrialStatus.VERIFICATION_REQUIRED) }
}
