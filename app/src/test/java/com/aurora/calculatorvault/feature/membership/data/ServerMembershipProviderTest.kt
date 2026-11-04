package com.aurora.calculatorvault.feature.membership.data

import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementRepository
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.entitlement.domain.MembershipResult
import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.AccountSessionRepository
import com.aurora.calculatorvault.feature.login.session.AuthApi
import com.aurora.calculatorvault.feature.login.session.LoginResult
import com.aurora.calculatorvault.feature.login.session.ServerAccount
import com.aurora.calculatorvault.feature.login.session.SessionCredential
import com.aurora.calculatorvault.feature.login.session.TokenStore
import com.aurora.calculatorvault.feature.trial.DeviceIdentityProvider
import com.aurora.calculatorvault.feature.trial.DeviceTrialRepository
import com.aurora.calculatorvault.feature.trial.TRIAL_MILLIS
import com.aurora.calculatorvault.feature.trial.TrialApi
import com.aurora.calculatorvault.feature.trial.TrialClock
import com.aurora.calculatorvault.feature.trial.TrialRecord
import com.aurora.calculatorvault.feature.trial.TrialReply
import com.aurora.calculatorvault.feature.trial.TrialStore
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import com.aurora.calculatorvault.feature.login.domain.WeChatServerLoginRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ServerMembershipProviderTest {

    private val elapsed = AtomicLong(1_000_000L)
    private val hour = 3_600_000L
    private val day = 24L * hour

    @Test
    fun activeMonthlyCreatesVip() = runTest {
        val provider = provider(active(plan = "MONTHLY", expiresPlus = day))
        provider.refresh(scope(1, 0))
        assertVerified(provider, 1, 0)
    }

    @Test
    fun activeYearlyCreatesVip() = runTest {
        val provider = provider(active(plan = "YEARLY", expiresPlus = 365 * day))
        provider.refresh(scope(1, 0))
        assertVerified(provider, 1, 0)
    }

    @Test
    fun activeLifetimeCreatesVip() = runTest {
        val provider = provider(activeLifetime())
        provider.refresh(scope(1, 0))
        val result = provider.membershipFor(1, 0) as MembershipResult.VerifiedActive
        assertTrue(result.permanent)
    }

    @Test
    fun activeStackedCreatesVip() = runTest {
        val provider = provider(
            active(
                plan = null,
                contributing = listOf("MONTHLY", "YEARLY"),
                expiresPlus = day,
            ),
        )
        provider.refresh(scope(1, 0))
        assertVerified(provider, 1, 0)
    }

    @Test
    fun noneClearsVip() = runTest {
        val api = ScriptedApi()
        val provider = provider(api)
        api.next = active(plan = "MONTHLY", expiresPlus = day)
        provider.refresh(scope(1, 0))
        assertVerified(provider, 1, 0)
        api.next = notEntitled("NONE")
        provider.refresh(scope(1, 0))
        assertEquals(MembershipResult.NotEntitled, provider.membershipFor(1, 0))
    }

    @Test
    fun expiredClearsVip() = runTest {
        val api = ScriptedApi()
        val provider = provider(api)
        api.next = active(plan = "MONTHLY", expiresPlus = day)
        provider.refresh(scope(1, 0))
        api.next = notEntitled("EXPIRED")
        provider.refresh(scope(1, 0))
        assertEquals(MembershipResult.NotEntitled, provider.membershipFor(1, 0))
    }

    @Test
    fun revokedClearsVip() = runTest {
        val api = ScriptedApi()
        val provider = provider(api)
        api.next = activeLifetime()
        provider.refresh(scope(1, 0))
        api.next = notEntitled("REVOKED")
        provider.refresh(scope(1, 0))
        assertEquals(MembershipResult.NotEntitled, provider.membershipFor(1, 0))
    }

    @Test
    fun firstFailureDoesNotGrantVip() = runTest {
        val provider = provider(
            MembershipQueryResult.TemporarilyUnavailable(1, 0, MembershipUnavailableReason.TIMEOUT),
        )
        provider.refresh(scope(1, 0))
        assertEquals(MembershipResult.Unavailable, provider.membershipFor(1, 0))
    }

    @Test
    fun stickyValidAfterOneHourOffline() = runTest {
        val api = ScriptedApi()
        val provider = provider(api)
        api.next = active(plan = "MONTHLY", expiresPlus = 2 * day)
        provider.refresh(scope(1, 0))
        api.next = MembershipQueryResult.TemporarilyUnavailable(1, 0, MembershipUnavailableReason.NETWORK)
        elapsed.addAndGet(hour)
        provider.refresh(scope(1, 0))
        assertVerified(provider, 1, 0)
    }

    @Test
    fun stickyValidAt23h59() = runTest {
        val provider = provider(activeLifetime())
        provider.refresh(scope(1, 0))
        elapsed.addAndGet(day - 60_000L)
        assertVerified(provider, 1, 0)
    }

    @Test
    fun stickyExpiresAt24h() = runTest {
        val provider = provider(active(plan = "MONTHLY", expiresPlus = 10 * day))
        provider.refresh(scope(1, 0))
        elapsed.addAndGet(day)
        assertEquals(MembershipResult.Unavailable, provider.membershipFor(1, 0))
    }

    @Test
    fun lifetimeAlsoExpiresAt24h() = runTest {
        val provider = provider(activeLifetime())
        provider.refresh(scope(1, 0))
        elapsed.addAndGet(day)
        assertEquals(MembershipResult.Unavailable, provider.membershipFor(1, 0))
    }

    @Test
    fun timedMembershipExpiresBefore24h() = runTest {
        val provider = provider(active(plan = "MONTHLY", expiresPlus = 2 * hour))
        provider.refresh(scope(1, 0))
        elapsed.addAndGet(2 * hour)
        assertEquals(MembershipResult.Unavailable, provider.membershipFor(1, 0))
    }

    @Test
    fun consecutiveFailuresDoNotExtendCache() = runTest {
        val api = ScriptedApi()
        val provider = provider(api)
        val confirmedAt = elapsed.get()
        api.next = active(plan = "MONTHLY", expiresPlus = 10 * day, confirmedAt = confirmedAt)
        provider.refresh(scope(1, 0))
        api.next = MembershipQueryResult.TemporarilyUnavailable(1, 0, MembershipUnavailableReason.NETWORK)
        elapsed.addAndGet(hour)
        provider.refresh(scope(1, 0))
        elapsed.addAndGet(hour)
        provider.refresh(scope(1, 0))
        // Still based on original confirmedAt: after 23h from original still OK
        elapsed.set(confirmedAt + day - 60_000L)
        assertVerified(provider, 1, 0)
        elapsed.set(confirmedAt + day)
        assertEquals(MembershipResult.Unavailable, provider.membershipFor(1, 0))
    }

    @Test
    fun newActiveRefreshesOfflineWindow() = runTest {
        val api = ScriptedApi()
        val provider = provider(api)
        api.next = active(plan = "MONTHLY", expiresPlus = 10 * day)
        provider.refresh(scope(1, 0))
        elapsed.addAndGet(day - hour)
        api.next = active(plan = "MONTHLY", expiresPlus = 10 * day, confirmedAt = elapsed.get())
        provider.refresh(scope(1, 0))
        elapsed.addAndGet(day - 60_000L)
        assertVerified(provider, 1, 0)
    }

    @Test
    fun logoutClearsImmediately() = runTest {
        val provider = provider(activeLifetime())
        provider.refresh(scope(1, 0))
        provider.clearAll()
        assertEquals(MembershipResult.NotEntitled, provider.membershipFor(1, 0))
    }

    @Test
    fun accountSwitchClearsImmediately() = runTest {
        val provider = provider(activeLifetime())
        provider.refresh(scope(1, 0))
        provider.clearAccount(1, 0)
        assertEquals(MembershipResult.NotEntitled, provider.membershipFor(2, 1))
        assertEquals(MembershipResult.NotEntitled, provider.membershipFor(1, 0))
    }

    @Test
    fun sameUserIdDifferentGenerationDoesNotInherit() = runTest {
        val provider = provider(activeLifetime(accountId = 5, generation = 0))
        provider.refresh(scope(accountId = 5, generation = 0))
        assertVerified(provider, 5, 0)
        provider.clearAccount(5, 0)
        assertEquals(MembershipResult.NotEntitled, provider.membershipFor(5, 1))
    }

    @Test
    fun lateResponseDoesNotPolluteNewAccount() = runTest {
        val gate = CompletableDeferred<Unit>()
        val api = MembershipApi { query ->
            if (query.generation == 0L) {
                gate.await()
                activeLifetime(accountId = 1, generation = 0)
            } else {
                notEntitled("NONE", accountId = 2, generation = 1)
            }
        }
        val provider = provider(api)
        val slow = async { provider.refresh(scope(1, 0)) }
        yield()
        provider.clearAll()
        provider.refresh(scope(2, 1))
        gate.complete(Unit)
        try {
            slow.await()
        } catch (_: CancellationException) {
            // clearAll cancels in-flight; apply epoch already bumped
        }
        assertEquals(MembershipResult.NotEntitled, provider.membershipFor(2, 1))
        assertFalse(provider.membershipFor(1, 0) is MembershipResult.VerifiedActive)
    }

    @Test
    fun late401DoesNotAffectNewAccount() = runTest {
        val gate = CompletableDeferred<Unit>()
        val events = AtomicReference<MembershipSessionInvalidEvent?>(null)
        val api = MembershipApi { query ->
            if (query.generation == 0L) {
                gate.await()
                MembershipQueryResult.SessionInvalid(1, 0, 401, 40101)
            } else {
                activeLifetime(accountId = 2, generation = 1)
            }
        }
        val provider = provider(api)
        backgroundScope.launch { provider.sessionInvalidEvents.collect { events.set(it) } }
        val slow = async { provider.refresh(scope(1, 0)) }
        yield()
        provider.clearAll()
        provider.refresh(scope(2, 1))
        assertVerified(provider, 2, 1)
        gate.complete(Unit)
        try {
            slow.await()
        } catch (_: CancellationException) {
        }
        runCurrent()
        assertVerified(provider, 2, 1)
        val event = events.get()
        if (event != null) {
            assertEquals(0L, event.generation)
        }
        assertVerified(provider, 2, 1)
    }

    @Test
    fun currentSession401ClearsVipAndEmitsEvent() = runTest {
        val api = ScriptedApi()
        val provider = provider(api)
        val events = mutableListOf<MembershipSessionInvalidEvent>()
        backgroundScope.launch { provider.sessionInvalidEvents.collect { events += it } }
        api.next = activeLifetime()
        provider.refresh(scope(1, 0))
        api.next = MembershipQueryResult.SessionInvalid(1, 0, 401, 40101)
        provider.refresh(scope(1, 0))
        runCurrent()
        assertEquals(MembershipResult.NotEntitled, provider.membershipFor(1, 0))
        assertTrue(events.any { it.accountId == 1L && it.generation == 0L && it.businessCode == 40101 })
    }

    @Test
    fun concurrentRefreshDedupes() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)
        val api = MembershipApi {
            calls.incrementAndGet()
            started.complete(Unit)
            release.await()
            activeLifetime()
        }
        val provider = provider(api)
        val a = async { provider.refresh(scope(1, 0)) }
        started.await()
        val b = async { provider.refresh(scope(1, 0)) }
        release.complete(Unit)
        a.await()
        b.await()
        assertEquals(1, calls.get())
        assertVerified(provider, 1, 0)
    }

    @Test
    fun refreshCancellationPropagates() = runTest {
        val started = CompletableDeferred<Unit>()
        val api = MembershipApi {
            started.complete(Unit)
            CompletableDeferred<MembershipQueryResult>().await()
        }
        val provider = provider(api)
        val job = async { provider.refresh(scope(1, 0)) }
        started.await()
        job.cancel(CancellationException("stop"))
        try {
            job.await()
            fail("expected cancel")
        } catch (_: CancellationException) {
        }
        assertEquals(MembershipResult.Unknown, provider.membershipFor(1, 0))
    }

    @Test
    fun monotonicClockRegressInvalidates() = runTest {
        val provider = provider(activeLifetime())
        provider.refresh(scope(1, 0))
        elapsed.set(elapsed.get() - 10_000L)
        assertEquals(MembershipResult.Unavailable, provider.membershipFor(1, 0))
    }

    @Test
    fun newProviderInstanceHasNoMemoryVip() = runTest {
        val first = provider(activeLifetime())
        first.refresh(scope(1, 0))
        assertVerified(first, 1, 0)
        val second = provider(activeLifetime())
        assertEquals(MembershipResult.Unknown, second.membershipFor(1, 0))
    }

    @Test
    fun revisionNotifiesEntitlementRepository() = runTest {
        val api = ScriptedApi()
        val membership = ServerMembershipProvider(api, backgroundScope, elapsed::get)
        val clock = TrialClockProbe(elapsed)
        val trial = DeviceTrialRepository(
            Store(),
            clock,
            DeviceIdentityProvider { "dev" },
            object : TrialApi {
                override suspend fun activate(deviceKey: String, remainingSecondsCap: Long) =
                    TrialReply("ACTIVE", clock.wall(), clock.wall() + TRIAL_MILLIS, clock.wall(), TRIAL_MILLIS)
            },
            backgroundScope,
            { true },
            { false },
        )
        trial.initialize()
        val tokens = object : TokenStore {
            var v: SessionCredential? = null
            override fun read() = v
            override fun write(value: SessionCredential) {
                v = value
            }
            override fun clear() {
                v = null
            }
        }
        val sessions = AccountSessionRepository(
            object : AuthApi {
                override suspend fun login(code: String) =
                    LoginResult(SessionCredential("tok", Instant.now().plusSeconds(3600)), ServerAccount(1))
                override suspend fun me(token: String) = ServerAccount(1)
                override suspend fun logout(token: String) = Unit
            },
            tokens,
            backgroundScope,
        )
        sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        assertTrue(sessions.state.value is AccountSession.Authenticated)

        val entitlement = EntitlementRepository(
            deviceTrial = trial,
            accountSession = sessions,
            membershipProvider = membership,
            elapsedRealtime = elapsed::get,
            wallClockEpochMillis = { 1_700_000_000_000L + elapsed.get() },
            scope = backgroundScope,
        )
        runCurrent()
        val beforeRev = membership.membershipStateRevision.value
        api.next = activeLifetime()
        membership.refresh(scope(1, entitlement.currentAccountGeneration()))
        runCurrent()
        assertTrue(membership.membershipStateRevision.value > beforeRev)
        assertTrue(
            entitlement.actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION)
                is ActionDecision.Allowed,
        )
        assertTrue(entitlement.state.value.source is EntitlementSource.Vip)

        membership.clearAll()
        runCurrent()
        // VIP cleared; trial may still allow paid capability — source must not stay Vip
        assertFalse(entitlement.state.value.source is EntitlementSource.Vip)
    }

    @Test
    fun expiryBoundarySyncRejectsWithoutTicker() = runTest {
        val provider = provider(active(plan = "MONTHLY", expiresPlus = hour))
        provider.refresh(scope(1, 0))
        assertVerified(provider, 1, 0)
        elapsed.addAndGet(hour)
        assertEquals(MembershipResult.Unavailable, provider.membershipFor(1, 0))
    }

    @Test
    fun trialNotClearedWhenVipExpires() = runTest {
        val membership = ServerMembershipProvider(
            ScriptedApi().also { it.next = activeLifetime() },
            backgroundScope,
            elapsed::get,
        )
        membership.refresh(scope(1, 0))
        val clock = TrialClockProbe(elapsed)
        val trial = DeviceTrialRepository(
            Store(),
            clock,
            DeviceIdentityProvider { "dev" },
            object : TrialApi {
                override suspend fun activate(deviceKey: String, remainingSecondsCap: Long) =
                    TrialReply("ACTIVE", clock.wall(), clock.wall() + TRIAL_MILLIS, clock.wall(), TRIAL_MILLIS)
            },
            backgroundScope,
            { true },
            { false },
        )
        trial.initialize()
        val remainingBefore = trial.state.value.remainingMillis
        assertTrue(remainingBefore > 0L)
        membership.clearAll()
        assertTrue(trial.state.value.remainingMillis > 0L)
        assertEquals(remainingBefore, trial.state.value.remainingMillis)
    }

    // --- helpers ---

    private fun kotlinx.coroutines.test.TestScope.provider(
        result: MembershipQueryResult,
    ): ServerMembershipProvider = provider(ScriptedApi().also { it.next = result })

    private fun kotlinx.coroutines.test.TestScope.provider(api: MembershipApi): ServerMembershipProvider =
        ServerMembershipProvider(api, backgroundScope, elapsed::get)

    private fun scope(accountId: Long, generation: Long) =
        MembershipQueryScope(accountId, generation, "token")

    private fun assertVerified(provider: ServerMembershipProvider, accountId: Long, generation: Long) {
        val result = provider.membershipFor(accountId, generation)
        assertTrue("expected VerifiedActive was $result", result is MembershipResult.VerifiedActive)
    }

    private fun active(
        plan: String?,
        expiresPlus: Long,
        contributing: List<String> = listOfNotNull(plan),
        accountId: Long = 1,
        generation: Long = 0,
        confirmedAt: Long = elapsed.get(),
    ): MembershipQueryResult.ConfirmedActive {
        val server = Instant.ofEpochMilli(1_700_000_000_000L + confirmedAt)
        return MembershipQueryResult.ConfirmedActive(
            accountId = accountId,
            generation = generation,
            isLifetime = false,
            expiresAt = Instant.ofEpochMilli(server.toEpochMilli() + expiresPlus),
            startedAt = server,
            planType = plan,
            contributingPlanTypes = contributing,
            serverTime = server,
            activeGrantCount = contributing.size,
            confirmedAtElapsedRealtime = confirmedAt,
        )
    }

    private fun activeLifetime(
        accountId: Long = 1,
        generation: Long = 0,
        confirmedAt: Long = elapsed.get(),
    ): MembershipQueryResult.ConfirmedActive {
        val server = Instant.ofEpochMilli(1_700_000_000_000L + confirmedAt)
        return MembershipQueryResult.ConfirmedActive(
            accountId = accountId,
            generation = generation,
            isLifetime = true,
            expiresAt = null,
            startedAt = server,
            planType = "LIFETIME",
            contributingPlanTypes = listOf("LIFETIME"),
            serverTime = server,
            activeGrantCount = 1,
            confirmedAtElapsedRealtime = confirmedAt,
        )
    }

    private fun notEntitled(
        status: String,
        accountId: Long = 1,
        generation: Long = 0,
    ) = MembershipQueryResult.ConfirmedNotEntitled(
        accountId = accountId,
        generation = generation,
        serverStatus = status,
        serverTime = Instant.ofEpochMilli(1_700_000_000_000L),
        confirmedAtElapsedRealtime = elapsed.get(),
    )

    private class ScriptedApi : MembershipApi {
        @Volatile
        var next: MembershipQueryResult =
            MembershipQueryResult.TemporarilyUnavailable(1, 0, MembershipUnavailableReason.NETWORK)

        override suspend fun queryMe(scope: MembershipQueryScope): MembershipQueryResult = next
    }

    private class Store : TrialStore {
        var value: TrialRecord? = null
        var marker = false
        override fun read() = value
        override fun write(record: TrialRecord) {
            value = record
        }
        override fun seen() = marker
        override fun markSeen() {
            marker = true
        }
    }

    private class TrialClockProbe(private val elapsedRef: AtomicLong) : TrialClock {
        override fun wall() = 1_700_000_000_000L + elapsedRef.get()
        override fun elapsed() = elapsedRef.get()
        override fun boot() = "1"
    }
}
