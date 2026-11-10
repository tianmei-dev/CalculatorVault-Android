package com.aurora.calculatorvault.feature.membership.data

import com.aurora.calculatorvault.feature.applock.domain.AppLockProtectionCoordinator
import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementRepository
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.entitlement.domain.MembershipResult
import com.aurora.calculatorvault.feature.login.domain.WeChatServerLoginRequest
import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.AccountSessionRepository
import com.aurora.calculatorvault.feature.login.session.AuthApi
import com.aurora.calculatorvault.feature.login.session.AuthFailure
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
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I3.4.3-D2-C: entitlement must stay consistent while a trusted session is being re-validated,
 * and must never be inherited by an unknown identity.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionRevalidationEntitlementTest {

    private val elapsed = AtomicLong(1_000_000L)
    private val hour = 3_600_000L
    private val day = 24L * hour
    private val wallBase = 1_700_000_000_000L

    // 1 / 3 / 16 / 17: online foreground re-validation keeps VIP, no service stop/restart.
    @Test
    fun authenticated_validating_authenticated_keepsVip_andLockServiceStable() = runTest {
        val env = Env(this)
        env.loginWithVip(1)
        assertEquals(1, env.lock.startCount.get())
        assertEquals(0, env.lock.stopCount.get())
        val revokesBefore = env.lock.revokeCount.get()

        env.auth.holdMe()
        env.sessions.revalidate()
        runCurrent()
        assertEquals(AccountSession.Validating(ServerAccount(1)), env.sessions.state.value)
        assertVip(env, 1)
        assertTrue(env.importAllowed())

        env.auth.releaseMe()
        runCurrent()
        assertTrue(env.sessions.state.value is AccountSession.Authenticated)
        assertVip(env, 1)
        assertEquals("service must not stop during revalidation", 0, env.lock.stopCount.get())
        assertEquals("service must not be started twice", 1, env.lock.startCount.get())
        assertEquals("no runtime revoke during revalidation", revokesBefore, env.lock.revokeCount.get())
    }

    // 2 / 4 / 16 / 17: offline foreground keeps a still-valid sticky VIP.
    @Test
    fun authenticated_validating_temporarilyUnavailable_keepsStickyVip() = runTest {
        val env = Env(this)
        env.loginWithVip(1)
        val revokesBefore = env.lock.revokeCount.get()
        env.auth.meFailure = IOException("offline")
        env.sessions.revalidate()
        runCurrent()
        assertEquals(AccountSession.TemporarilyUnavailable(ServerAccount(1)), env.sessions.state.value)
        assertVip(env, 1)
        assertTrue(env.importAllowed())
        assertEquals(0, env.lock.stopCount.get())
        assertEquals(revokesBefore, env.lock.revokeCount.get())
        assertTrue(env.lock.running.get())
    }

    // 5 / 15: 24h boundary still enforced while unavailable; trial fallback.
    @Test
    fun sticky24hBoundary_duringUnavailable_fallsBackToTrial() = runTest {
        val env = Env(this, trialActive = true)
        env.loginWithVip(1)
        env.auth.meFailure = IOException("offline")
        env.sessions.revalidate()
        runCurrent()
        elapsed.addAndGet(day - 1)
        env.entitlement.refresh()
        assertVip(env, 1)
        elapsed.addAndGet(1)
        env.entitlement.refresh()
        runCurrent()
        assertTrue(env.entitlement.state.value.source is EntitlementSource.DeviceTrial)
    }

    @Test
    fun sticky24hBoundary_noTrial_stopsProtection() = runTest {
        val env = Env(this)
        env.loginWithVip(1)
        env.auth.meFailure = IOException("offline")
        env.sessions.revalidate()
        runCurrent()
        elapsed.addAndGet(day)
        env.entitlement.refresh()
        runCurrent()
        assertEquals(EntitlementSource.None, env.entitlement.state.value.source)
        assertFalse(env.importAllowed())
        assertFalse(env.lock.running.get())
    }

    // 6: limited membership expiring before the 24h window.
    @Test
    fun limitedMembershipExpiresEarly_duringUnavailable() = runTest {
        val env = Env(this)
        env.loginWithVip(1, expiresPlus = hour)
        env.auth.meFailure = IOException("offline")
        env.sessions.revalidate()
        runCurrent()
        assertVip(env, 1)
        elapsed.addAndGet(hour)
        env.entitlement.refresh()
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
    }

    // 7 / 11: unknown identity (new credential) never inherits the previous VIP.
    @Test
    fun firstValidating_newCredential_doesNotInheritVip() = runTest {
        val env = Env(this)
        env.loginWithVip(1)
        env.auth.holdMe()
        env.sessions.installServerIssuedSession(SessionCredential("tok-b", Instant.now().plusSeconds(3600)))
        runCurrent()
        assertEquals(AccountSession.Validating(), env.sessions.state.value)
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
        assertFalse(env.importAllowed())
        env.auth.releaseMe()
        runCurrent()
    }

    // 7 / 18: cold start (new process) restore is untrusted; offline keeps no VIP.
    @Test
    fun coldStartRestore_offline_noVip_noStickyCarryOver() = runTest {
        val store = MemoryStore().also { it.value = SessionCredential("tok", Instant.now().plusSeconds(3600)) }
        val env = Env(this, tokens = store)
        env.auth.meFailure = IOException("offline")
        env.sessions.restore()
        runCurrent()
        assertEquals(AccountSession.TemporarilyUnavailable(), env.sessions.state.value)
        assertEquals(MembershipResult.Unknown, env.membership.membershipFor(1, 0))
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
        assertEquals(0, env.api.calls.get())
    }

    // 8: never-confirmed membership is not granted during unavailability.
    @Test
    fun unconfirmedMembership_notGrantedDuringUnavailable() = runTest {
        val env = Env(this)
        env.api.next = MembershipQueryResult.TemporarilyUnavailable(1, 0, MembershipUnavailableReason.NETWORK)
        env.login(1)
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
        env.auth.meFailure = IOException("offline")
        env.sessions.revalidate()
        runCurrent()
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
    }

    // 9: definitive 401 on /auth/me during re-validation revokes immediately.
    @Test
    fun auth401_duringRevalidation_revokesImmediately() = runTest {
        val env = Env(this)
        env.loginWithVip(1)
        env.auth.meFailure = AuthFailure(invalidSession = true)
        env.sessions.revalidate()
        runCurrent()
        assertEquals(AccountSession.SessionExpired, env.sessions.state.value)
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
        assertFalse(env.lock.running.get())
    }

    // 9: membership-layer SessionInvalid while unavailable revokes the trusted account.
    @Test
    fun membershipSessionInvalid_duringUnavailable_revokes() = runTest {
        val env = Env(this)
        env.loginWithVip(1)
        env.auth.meFailure = IOException("offline")
        env.sessions.revalidate()
        runCurrent()
        val gen = env.entitlement.currentAccountGeneration()
        env.api.next = MembershipQueryResult.SessionInvalid(1, gen, 401, 40102)
        runCatching { env.membership.refresh(MembershipQueryScope(1, gen, "tok")) }
        runCurrent()
        assertEquals(AccountSession.SessionExpired, env.sessions.state.value)
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
    }

    // 10: logout during unavailability.
    @Test
    fun logout_duringUnavailable_revokes() = runTest {
        val env = Env(this)
        env.loginWithVip(1)
        env.auth.meFailure = IOException("offline")
        env.sessions.revalidate()
        runCurrent()
        env.sessions.logout()
        runCurrent()
        assertEquals(AccountSession.Guest, env.sessions.state.value)
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
        assertEquals(MembershipResult.NotEntitled, env.membership.membershipFor(1, 0))
    }

    // 12: same user re-login gets a new generation and does not reuse the old claim.
    @Test
    fun sameUserNewGeneration_doesNotInherit() = runTest {
        val env = Env(this)
        env.loginWithVip(1)
        val gen1 = env.entitlement.currentAccountGeneration()
        env.sessions.logout()
        runCurrent()
        env.api.next = MembershipQueryResult.TemporarilyUnavailable(1, 0, MembershipUnavailableReason.NETWORK)
        env.login(1)
        val gen2 = env.entitlement.currentAccountGeneration()
        assertNotEquals(gen1, gen2)
        env.auth.meFailure = IOException("offline")
        env.sessions.revalidate()
        runCurrent()
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
    }

    // 13: late 401 for the previous account cannot expire the new account under re-validation.
    @Test
    fun late401OldAccount_ignoredWhileNewAccountRevalidating() = runTest {
        val env = Env(this)
        env.loginWithVip(1)
        val oldGen = env.entitlement.currentAccountGeneration()
        env.sessions.logout()
        runCurrent()
        env.loginWithVip(2)
        env.auth.meFailure = IOException("offline")
        env.sessions.revalidate()
        runCurrent()
        env.sessions.notifyRemoteSessionInvalid(1)
        env.api.next = MembershipQueryResult.SessionInvalid(1, oldGen, 401, 40102)
        runCatching { env.membership.refresh(MembershipQueryScope(1, oldGen, "old")) }
        runCurrent()
        assertEquals(AccountSession.TemporarilyUnavailable(ServerAccount(2)), env.sessions.state.value)
        assertVip(env, 2)
    }

    // 14: failures do not move the sticky start.
    @Test
    fun offlineFailures_doNotRefreshStickyStart() = runTest {
        val env = Env(this)
        env.loginWithVip(1)
        elapsed.addAndGet(20 * hour)
        env.api.next = MembershipQueryResult.TemporarilyUnavailable(1, 0, MembershipUnavailableReason.NETWORK)
        assertTrue(env.coordinator.refreshManual())
        runCurrent()
        env.auth.meFailure = IOException("offline")
        env.sessions.revalidate()
        runCurrent()
        assertVip(env, 1)
        elapsed.addAndGet(4 * hour)
        env.entitlement.refresh()
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
    }

    // No duplicate /me during re-validation; recovery after unavailability reconfirms once.
    @Test
    fun noDuplicateMe_andRecoveryReconfirmsOnce() = runTest {
        val env = Env(this)
        env.loginWithVip(1)
        val afterLogin = env.api.calls.get()
        env.auth.holdMe()
        env.sessions.revalidate()
        runCurrent()
        assertEquals("no /me while validating", afterLogin, env.api.calls.get())
        env.auth.releaseMe()
        runCurrent()
        val afterFg = env.api.calls.get()
        assertEquals("one foreground refresh", afterLogin + 1, afterFg)

        env.auth.meFailure = IOException("offline")
        env.sessions.revalidate()
        runCurrent()
        assertEquals(afterFg, env.api.calls.get())
        env.auth.meFailure = null
        env.sessions.revalidate()
        runCurrent()
        assertEquals("recovery reconfirms despite 5-min throttle", afterFg + 1, env.api.calls.get())
        assertVip(env, 1)
    }

    // O-2 check: account switch never authorizes the old VIP through the live policy.
    @Test
    fun switchAccount_livePolicyNeverAuthorizesOldVip() = runTest {
        val env = Env(this)
        env.loginWithVip(1)
        env.auth.holdMe()
        env.sessions.installServerIssuedSession(SessionCredential("tok-b", Instant.now().plusSeconds(3600)))
        // No runCurrent: StateFlow collector has not run yet; live policy must still deny.
        assertFalse(env.importAllowed())
        env.auth.releaseMe()
        runCurrent()
    }

    // ---------- fixtures ----------

    private fun assertVip(env: Env, accountId: Long) {
        val s = env.entitlement.state.value.source
        assertTrue("expected VIP for $accountId was $s", s is EntitlementSource.Vip && s.accountId == accountId)
    }

    private inner class Env(
        scope: TestScope,
        tokens: TokenStore = MemoryStore(),
        trialActive: Boolean = false,
    ) {
        val auth = AuthProbe()
        val api = ScriptedApi()
        val sessions = AccountSessionRepository(auth, tokens, scope.backgroundScope, serverIssuedInstallAllowed = true)
        val trial = trialRepo(scope, trialActive)
        val membership = ServerMembershipProvider(api, scope.backgroundScope, elapsed::get)
        val entitlement = EntitlementRepository(
            deviceTrial = trial,
            accountSession = sessions,
            membershipProvider = membership,
            elapsedRealtime = elapsed::get,
            wallClockEpochMillis = { wallBase + elapsed.get() },
            scope = scope.backgroundScope,
        )
        val coordinator = MembershipRefreshCoordinator(
            accountSession = sessions,
            entitlementRepository = entitlement,
            serverMembership = membership,
            scope = scope.backgroundScope,
            elapsedRealtime = elapsed::get,
        ).also { it.start() }
        val lock = LockProbe(scope, this)
        private val testScope = scope

        fun importAllowed(): Boolean =
            entitlement.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT) is ActionDecision.Allowed

        fun login(accountId: Long) {
            auth.accountId = accountId
            sessions.acceptAuthorization(WeChatServerLoginRequest("code-$accountId"))
            testScope.runCurrent()
            assertTrue(sessions.state.value is AccountSession.Authenticated)
        }

        fun loginWithVip(accountId: Long, expiresPlus: Long = 30 * day) {
            api.next = active(expiresPlus)
            login(accountId)
            testScope.runCurrent()
            assertVip(this, accountId)
            testScope.runCurrent()
        }
    }

    private inner class LockProbe(scope: TestScope, env: Env) {
        val running = java.util.concurrent.atomic.AtomicBoolean(false)
        val startCount = AtomicInteger(0)
        val stopCount = AtomicInteger(0)
        val revokeCount = AtomicInteger(0)
        private val locked = MutableStateFlow(setOf("com.example.locked"))
        val coordinator = AppLockProtectionCoordinator(
            entitlementState = env.entitlement.state,
            actionPolicy = env.entitlement.actionPolicy,
            observeLockedPackages = { locked },
            lockedPackageCount = { locked.value.size },
            privacyOk = { true },
            privacyWithdrawn = { false },
            usageAccessGranted = { true },
            overlayPermissionGranted = { true },
            processForeground = { true },
            isMonitorRunning = { running.get() },
            startMonitor = { startCount.incrementAndGet(); running.set(true); true },
            stopMonitor = { stopCount.incrementAndGet(); running.set(false) },
            revokeRuntimeProtection = { revokeCount.incrementAndGet() },
            scope = scope.backgroundScope,
        ).also { it.start() }
    }

    private fun trialRepo(scope: TestScope, active: Boolean): DeviceTrialRepository {
        val clock = object : TrialClock {
            override fun wall() = wallBase + elapsed.get()
            override fun elapsed() = elapsed.get()
            override fun boot() = "1"
        }
        val store = object : TrialStore {
            var value: TrialRecord? = if (active) {
                null
            } else {
                TrialRecord(clock.wall(), 0L, clock.wall(), clock.elapsed(), "1", expired = true)
            }
            var marker = !active
            override fun read() = value
            override fun write(record: TrialRecord) { value = record }
            override fun seen() = marker
            override fun markSeen() { marker = true }
        }
        return DeviceTrialRepository(
            store,
            clock,
            DeviceIdentityProvider { "dev" },
            object : TrialApi {
                override suspend fun activate(deviceKey: String, remainingSecondsCap: Long) =
                    TrialReply("ACTIVE", clock.wall(), clock.wall() + TRIAL_MILLIS, clock.wall(), TRIAL_MILLIS)
            },
            scope.backgroundScope,
            { true },
            { false },
        ).also { it.initialize() }
    }

    private fun active(expiresPlus: Long): MembershipQueryResult.ConfirmedActive {
        val now = elapsed.get()
        val server = Instant.ofEpochMilli(wallBase + now)
        return MembershipQueryResult.ConfirmedActive(
            accountId = 0, generation = 0, isLifetime = false,
            expiresAt = Instant.ofEpochMilli(server.toEpochMilli() + expiresPlus),
            startedAt = server, planType = "MONTHLY", contributingPlanTypes = listOf("MONTHLY"),
            serverTime = server, activeGrantCount = 1, confirmedAtElapsedRealtime = now,
        )
    }

    /** Re-binds scripted results to the requesting scope; ConfirmedActive re-stamps the clock. */
    private inner class ScriptedApi : MembershipApi {
        val calls = AtomicInteger(0)
        @Volatile var next: MembershipQueryResult =
            MembershipQueryResult.TemporarilyUnavailable(1, 0, MembershipUnavailableReason.NETWORK)

        override suspend fun queryMe(scope: MembershipQueryScope): MembershipQueryResult {
            calls.incrementAndGet()
            return when (val c = next) {
                is MembershipQueryResult.ConfirmedActive -> c.copy(
                    accountId = scope.accountId, generation = scope.generation,
                    confirmedAtElapsedRealtime = elapsed.get(),
                    serverTime = Instant.ofEpochMilli(wallBase + elapsed.get()),
                )
                is MembershipQueryResult.ConfirmedNotEntitled -> c.copy(accountId = scope.accountId, generation = scope.generation)
                is MembershipQueryResult.TemporarilyUnavailable -> c.copy(accountId = scope.accountId, generation = scope.generation)
                is MembershipQueryResult.SessionInvalid -> c
            }
        }
    }

    private class AuthProbe : AuthApi {
        var accountId: Long = 1
        var meFailure: Exception? = null
        private var gate: CompletableDeferred<Unit>? = null
        fun holdMe() { gate = CompletableDeferred() }
        fun releaseMe() { gate?.complete(Unit); gate = null }
        override suspend fun login(code: String) =
            LoginResult(SessionCredential("tok-$code", Instant.now().plusSeconds(3600)), ServerAccount(accountId))
        override suspend fun me(token: String): ServerAccount {
            gate?.await()
            meFailure?.let { throw it }
            return ServerAccount(accountId)
        }
        override suspend fun logout(token: String) = Unit
    }

    private class MemoryStore : TokenStore {
        var value: SessionCredential? = null
        override fun read() = value
        override fun write(value: SessionCredential) { this.value = value }
        override fun clear() { value = null }
    }
}
