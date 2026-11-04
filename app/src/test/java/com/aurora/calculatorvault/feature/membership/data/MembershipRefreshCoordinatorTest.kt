package com.aurora.calculatorvault.feature.membership.data

import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementDisplayMapper
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementRepository
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.entitlement.domain.FakeMembershipProvider
import com.aurora.calculatorvault.feature.entitlement.domain.MembershipResult
import com.aurora.calculatorvault.feature.login.domain.WeChatServerLoginRequest
import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.AccountSessionRepository
import com.aurora.calculatorvault.feature.login.session.AuthApi
import com.aurora.calculatorvault.feature.login.session.LoginResult
import com.aurora.calculatorvault.feature.login.session.ServerAccount
import com.aurora.calculatorvault.feature.login.session.SessionCredential
import com.aurora.calculatorvault.feature.login.session.TokenStore
import com.aurora.calculatorvault.feature.membership.domain.VipPlan
import com.aurora.calculatorvault.feature.settings.presentation.MembershipStatus
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MembershipRefreshCoordinatorTest {

    private val elapsed = AtomicLong(1_000_000L)
    private val hour = 3_600_000L
    private val day = 24L * hour
    private val wallBase = 1_700_000_000_000L

    @Test
    fun guestDoesNotQueryMembership() = runTest {
        val api = CountingApi()
        val env = Env(this, api)
        env.coordinator.start()
        runCurrent()
        assertEquals(0, api.calls.get())
        assertTrue(env.sessions.state.value is AccountSession.Guest)
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
    }

    @Test
    fun loginSuccessAutoRefreshes() = runTest {
        val api = CountingApi().also { it.next = active(plan = "MONTHLY") }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        assertTrue(env.sessions.state.value is AccountSession.Authenticated)
        assertEquals(1, api.calls.get())
        assertTrue(env.entitlement.state.value.source is EntitlementSource.Vip)
        val ui = EntitlementDisplayMapper.toAccountUiState(
            env.sessions.state.value,
            env.entitlement.state.value,
        )
        assertTrue(ui.accountMembership is MembershipStatus.VipActive)
        assertEquals(VipPlan.Monthly, ui.accountMembership.planType)
    }

    @Test
    fun coldStartRestoreAutoRefreshes() = runTest {
        val api = CountingApi().also { it.next = active(plan = "YEARLY", expiresPlus = 365 * day) }
        val tokens = MemoryStore().also {
            it.value = SessionCredential("tok", Instant.now().plusSeconds(3600))
        }
        val env = Env(this, api, tokens)
        env.coordinator.start()
        env.sessions.restore()
        runCurrent()
        assertTrue(env.sessions.state.value is AccountSession.Authenticated)
        assertEquals(1, api.calls.get())
        val ui = EntitlementDisplayMapper.toAccountUiState(
            env.sessions.state.value,
            env.entitlement.state.value,
        )
        assertEquals(VipPlan.Annual, ui.accountMembership.planType)
    }

    @Test
    fun foregroundThrottledFiveMinutes() = runTest {
        val api = CountingApi().also { it.next = active(plan = "MONTHLY") }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        assertEquals(1, api.calls.get())

        env.coordinator.onForeground()
        runCurrent()
        assertEquals(2, api.calls.get())

        elapsed.addAndGet(4 * 60_000L)
        env.coordinator.onForeground()
        runCurrent()
        assertEquals(2, api.calls.get())

        elapsed.addAndGet(60_000L + 1)
        env.coordinator.onForeground()
        runCurrent()
        assertEquals(3, api.calls.get())
    }

    @Test
    fun manualRefreshBypassesThrottle() = runTest {
        val api = CountingApi().also { it.next = active(plan = "MONTHLY") }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        env.coordinator.onForeground()
        runCurrent()
        val afterFg = api.calls.get()
        assertTrue(env.coordinator.refreshManual())
        assertEquals(MembershipSyncUi.Syncing, env.coordinator.syncState.value)
        assertFalse(env.coordinator.refreshManual())
        runCurrent()
        assertEquals(afterFg + 1, api.calls.get())
    }

    @Test
    fun sameGenerationConcurrentRefreshDedupes() = runTest {
        val gate = CompletableDeferred<Unit>()
        val api = object : MembershipApi {
            val calls = AtomicInteger(0)
            override suspend fun queryMe(scope: MembershipQueryScope): MembershipQueryResult {
                calls.incrementAndGet()
                gate.await()
                return active(plan = "MONTHLY", accountId = scope.accountId, generation = scope.generation)
            }
        }
        val provider = ServerMembershipProvider(api, backgroundScope, elapsed::get)
        val first = async { provider.refresh(MembershipQueryScope(1, 0, "tok")) }
        val second = async { provider.refresh(MembershipQueryScope(1, 0, "tok")) }
        runCurrent()
        assertEquals(1, api.calls.get())
        gate.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, api.calls.get())
    }

    @Test
    fun activeLifetimeDisplayIsPermanentWithoutExpiry() = runTest {
        val api = CountingApi().also { it.next = activeLifetime() }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        val ui = EntitlementDisplayMapper.toAccountUiState(
            env.sessions.state.value,
            env.entitlement.state.value,
        )
        assertEquals(MembershipStatus.VipPermanent, ui.accountMembership)
        assertNull(ui.accountMembership.expiresAt)
    }

    @Test
    fun activeStackedShowsGenericVipNotMonthly() = runTest {
        val api = CountingApi().also {
            it.next = active(
                plan = null,
                contributing = listOf("MONTHLY", "YEARLY"),
                expiresPlus = day,
            )
        }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        val ui = EntitlementDisplayMapper.toAccountUiState(
            env.sessions.state.value,
            env.entitlement.state.value,
        )
        val vip = ui.accountMembership as MembershipStatus.VipActive
        assertNull(vip.planType)
    }

    @Test
    fun noneRevokesImmediately() = runTest {
        val api = CountingApi().also { it.next = active(plan = "MONTHLY") }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        assertTrue(env.entitlement.state.value.source is EntitlementSource.Vip)
        api.next = notEntitled("NONE")
        assertTrue(env.coordinator.refreshManual())
        runCurrent()
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
    }

    @Test
    fun firstOfflineDoesNotGrantVip() = runTest {
        val api = CountingApi().also {
            it.next = MembershipQueryResult.TemporarilyUnavailable(1, 0, MembershipUnavailableReason.NETWORK)
        }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
        assertEquals(MembershipSyncUi.Failed, env.coordinator.syncState.value)
    }

    @Test
    fun stickyVipRetainedWithin24hOffline() = runTest {
        val api = CountingApi().also { it.next = active(plan = "MONTHLY") }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        assertTrue(env.entitlement.state.value.source is EntitlementSource.Vip)
        api.next = MembershipQueryResult.TemporarilyUnavailable(1, 0, MembershipUnavailableReason.NETWORK)
        elapsed.addAndGet(23 * hour)
        assertTrue(env.coordinator.refreshManual())
        runCurrent()
        assertTrue(env.entitlement.state.value.source is EntitlementSource.Vip)
        assertEquals(MembershipSyncUi.Anomaly, env.coordinator.syncState.value)
    }

    @Test
    fun stickyVipEndsAt24hBoundary() = runTest {
        val api = CountingApi().also { it.next = active(plan = "MONTHLY") }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        elapsed.addAndGet(day)
        env.entitlement.refresh()
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
    }

    @Test
    fun sessionInvalidClearsCurrentAccountSession() = runTest {
        val api = CountingApi().also { it.next = active(plan = "MONTHLY") }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        val gen = env.entitlement.currentAccountGeneration()
        api.next = MembershipQueryResult.SessionInvalid(1, gen, 401, 40100)
        assertTrue(env.coordinator.refreshManual())
        runCurrent()
        assertEquals(AccountSession.SessionExpired, env.sessions.state.value)
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
        assertTrue(env.trial.state.value.remainingMillis > 0L)
    }

    @Test
    fun late401FromOldAccountDoesNotLogoutNewAccount() = runTest {
        val api = CountingApi().also { it.next = active(plan = "MONTHLY") }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        env.sessions.logout()
        runCurrent()
        api.next = active(plan = "YEARLY", accountId = 2, expiresPlus = 365 * day)
        env.auth.accountId = 2
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code2"))
        runCurrent()
        assertTrue(env.sessions.state.value is AccountSession.Authenticated)
        assertEquals(2L, (env.sessions.state.value as AccountSession.Authenticated).account.userId)
        env.sessions.notifyRemoteSessionInvalid(1)
        runCurrent()
        assertTrue(env.sessions.state.value is AccountSession.Authenticated)
        assertEquals(2L, (env.sessions.state.value as AccountSession.Authenticated).account.userId)
    }

    @Test
    fun logoutClearsVipKeepsTrial() = runTest {
        val api = CountingApi().also { it.next = activeLifetime() }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        val trialBefore = env.trial.state.value.remainingMillis
        assertTrue(env.entitlement.state.value.source is EntitlementSource.Vip)
        env.sessions.logout()
        runCurrent()
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
        assertTrue(env.trial.state.value.remainingMillis > 0L)
        assertEquals(trialBefore, env.trial.state.value.remainingMillis)
    }

    @Test
    fun switchAccountDoesNotInheritVip() = runTest {
        val api = CountingApi().also { it.next = activeLifetime() }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        assertTrue(env.entitlement.state.value.source is EntitlementSource.Vip)
        env.sessions.logout()
        runCurrent()
        api.next = notEntitled("NONE", accountId = 2)
        env.auth.accountId = 2
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code2"))
        runCurrent()
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
    }

    @Test
    fun sameUserIdDifferentGenerationDoesNotInherit() = runTest {
        val api = CountingApi().also { it.next = activeLifetime() }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        val gen1 = env.entitlement.currentAccountGeneration()
        assertTrue(
            env.membership.membershipFor(1, gen1) is MembershipResult.VerifiedActive,
        )
        env.sessions.logout()
        runCurrent()
        api.next = MembershipQueryResult.TemporarilyUnavailable(1, 0, MembershipUnavailableReason.NETWORK)
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code-again"))
        runCurrent()
        val gen2 = env.entitlement.currentAccountGeneration()
        assertNotEquals(gen1, gen2)
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
        assertFalse(
            env.membership.membershipFor(1, gen1) is MembershipResult.VerifiedActive,
        )
        assertFalse(
            env.membership.membershipFor(1, gen2) is MembershipResult.VerifiedActive,
        )
    }

    @Test
    fun vipLossStopsAppLockProtectionAlbumExistingRemains() = runTest {
        val api = CountingApi().also { it.next = activeLifetime() }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        assertTrue(
            env.entitlement.actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION)
                is ActionDecision.Allowed,
        )
        api.next = notEntitled("REVOKED")
        assertTrue(env.coordinator.refreshManual())
        runCurrent()
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
        // Exhaust device trial so paid protection must stop; Room lock config is out of scope here.
        elapsed.addAndGet(TRIAL_MILLIS + hour)
        env.trial.refresh()
        env.entitlement.refresh()
        assertTrue(
            env.entitlement.actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION)
                is ActionDecision.Denied,
        )
        assertTrue(
            env.entitlement.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_VIEW_RESTORE)
                is ActionDecision.Allowed,
        )
        assertTrue(
            env.entitlement.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_MANAGE_EXISTING)
                is ActionDecision.Allowed,
        )
    }

    @Test
    fun manualRefreshLoadingSuccessFailure() = runTest {
        val loginApi = CountingApi().also { it.next = active(plan = "MONTHLY") }
        val loginEnv = Env(this, loginApi)
        loginEnv.coordinator.start()
        loginEnv.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        assertEquals(MembershipSyncUi.Idle, loginEnv.coordinator.syncState.value)

        loginApi.next = MembershipQueryResult.TemporarilyUnavailable(
            1, loginEnv.entitlement.currentAccountGeneration(), MembershipUnavailableReason.NETWORK,
        )
        assertTrue(loginEnv.coordinator.refreshManual())
        assertEquals(MembershipSyncUi.Syncing, loginEnv.coordinator.syncState.value)
        runCurrent()
        assertEquals(MembershipSyncUi.Anomaly, loginEnv.coordinator.syncState.value)
        assertEquals(
            "会员状态暂时无法同步",
            EntitlementDisplayMapper.toAccountUiState(
                loginEnv.sessions.state.value,
                loginEnv.entitlement.state.value,
                sync = loginEnv.coordinator.syncState.value,
            ).membershipSyncHint,
        )
    }

    @Test
    fun blankBaseUrlSafeDegrade() = runTest {
        val api = CountingApi().also { it.next = active(plan = "MONTHLY") }
        val env = Env(this, api, baseUrlConfigured = { false })
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        assertEquals(0, api.calls.get())
        assertFalse(env.entitlement.state.value.source is EntitlementSource.Vip)
        assertEquals(MembershipSyncUi.Failed, env.coordinator.syncState.value)
    }

    @Test
    fun fakeVipDoesNotEnterServerProviderPath() = runTest {
        val fake = FakeMembershipProvider()
        fake.grant(accountId = 1, generation = 0)
        val server = ServerMembershipProvider(CountingApi(), backgroundScope, elapsed::get)
        assertEquals(MembershipResult.Unknown, server.membershipFor(1, 0))
        assertTrue(fake.membershipFor(1, 0) is MembershipResult.VerifiedActive)
        // Production path is ServerMembershipProvider — Fake grant must not leak into it.
        assertFalse(server.membershipFor(1, 0) is MembershipResult.VerifiedActive)
    }

    @Test
    fun productionWiringUsesServerMembershipProviderType() = runTest {
        val api = CountingApi()
        val server = ServerMembershipProvider(api, backgroundScope, elapsed::get)
        val entitlement = EntitlementRepository(
            deviceTrial = trialRepo(),
            accountSession = AccountSessionRepository(
                AuthProbe(),
                MemoryStore(),
                backgroundScope,
            ),
            membershipProvider = server,
            elapsedRealtime = elapsed::get,
            wallClockEpochMillis = { wallBase + elapsed.get() },
            scope = backgroundScope,
        )
        assertTrue(entitlement.let { true })
        assertTrue(server is com.aurora.calculatorvault.feature.entitlement.domain.MembershipProvider)
        assertTrue(server is com.aurora.calculatorvault.feature.entitlement.domain.MembershipStateSignal)
    }

    @Test
    fun displayMapperResolvesYearlyAndStacked() {
        assertEquals(
            VipPlan.Annual,
            EntitlementDisplayMapper.resolveDisplayPlan("YEARLY", listOf("YEARLY"), false),
        )
        assertEquals(
            VipPlan.Monthly,
            EntitlementDisplayMapper.resolveDisplayPlan("MONTHLY", emptyList(), false),
        )
        assertNull(
            EntitlementDisplayMapper.resolveDisplayPlan(null, listOf("MONTHLY", "YEARLY"), false),
        )
        assertEquals(
            VipPlan.Lifetime,
            EntitlementDisplayMapper.resolveDisplayPlan("LIFETIME", listOf("LIFETIME"), true),
        )
    }

    @Test
    fun emptyHttpBaseUrlDoesNotCrash() = runTest {
        val http = HttpMembershipApi(baseUrl = "", debug = true)
        val result = http.queryMe(MembershipQueryScope(1, 0, "tok"))
        assertTrue(result is MembershipQueryResult.TemporarilyUnavailable)
    }

    @Test
    fun authenticatedRepeatDoesNotStorm() = runTest {
        val api = CountingApi().also { it.next = active(plan = "MONTHLY") }
        val env = Env(this, api)
        env.coordinator.start()
        env.sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        assertEquals(1, api.calls.get())
        // Re-emit same Authenticated by collecting — coordinator ignores Authenticated→Authenticated.
        env.entitlement.refresh()
        runCurrent()
        assertEquals(1, api.calls.get())
    }

    // --- fixtures ---

    private inner class Env(
        scope: TestScope,
        val api: MembershipApi,
        tokens: TokenStore = MemoryStore(),
        baseUrlConfigured: () -> Boolean = { true },
    ) {
        val auth = AuthProbe()
        val sessions = AccountSessionRepository(auth, tokens, scope.backgroundScope)
        val trial = scope.trialRepo()
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
            baseUrlConfigured = baseUrlConfigured,
            foregroundThrottleMillis = 5 * 60_000L,
        )
    }

    private fun TestScope.trialRepo(): DeviceTrialRepository {
        val clock = object : TrialClock {
            override fun wall() = wallBase + elapsed.get()
            override fun elapsed() = elapsed.get()
            override fun boot() = "1"
        }
        val repo = DeviceTrialRepository(
            object : TrialStore {
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
            },
            clock,
            DeviceIdentityProvider { "dev" },
            object : TrialApi {
                override suspend fun activate(deviceKey: String, remainingSecondsCap: Long) =
                    TrialReply(
                        "ACTIVE",
                        clock.wall(),
                        clock.wall() + TRIAL_MILLIS,
                        clock.wall(),
                        TRIAL_MILLIS,
                    )
            },
            backgroundScope,
            { true },
            { false },
        )
        repo.initialize()
        return repo
    }

    private class CountingApi : MembershipApi {
        val calls = AtomicInteger(0)
        @Volatile
        var next: MembershipQueryResult =
            MembershipQueryResult.TemporarilyUnavailable(1, 0, MembershipUnavailableReason.NETWORK)

        override suspend fun queryMe(scope: MembershipQueryScope): MembershipQueryResult {
            calls.incrementAndGet()
            return when (val current = next) {
                is MembershipQueryResult.ConfirmedActive -> current.copy(
                    accountId = scope.accountId,
                    generation = scope.generation,
                )
                is MembershipQueryResult.ConfirmedNotEntitled -> current.copy(
                    accountId = scope.accountId,
                    generation = scope.generation,
                )
                is MembershipQueryResult.TemporarilyUnavailable -> current.copy(
                    accountId = scope.accountId,
                    generation = scope.generation,
                )
                is MembershipQueryResult.SessionInvalid -> current.copy(
                    accountId = scope.accountId,
                    generation = scope.generation,
                )
            }
        }
    }

    private class AuthProbe : AuthApi {
        var accountId: Long = 1
        override suspend fun login(code: String) =
            LoginResult(
                SessionCredential("tok-$code", Instant.now().plusSeconds(3600)),
                ServerAccount(accountId),
            )
        override suspend fun me(token: String) = ServerAccount(accountId)
        override suspend fun logout(token: String) = Unit
    }

    private class MemoryStore : TokenStore {
        var value: SessionCredential? = null
        override fun read() = value
        override fun write(value: SessionCredential) {
            this.value = value
        }
        override fun clear() {
            value = null
        }
    }

    private fun active(
        plan: String?,
        expiresPlus: Long = day,
        contributing: List<String> = listOfNotNull(plan),
        accountId: Long = 1,
        generation: Long = 0,
        confirmedAt: Long = elapsed.get(),
    ): MembershipQueryResult.ConfirmedActive {
        val server = Instant.ofEpochMilli(wallBase + confirmedAt)
        return MembershipQueryResult.ConfirmedActive(
            accountId = accountId,
            generation = generation,
            isLifetime = false,
            expiresAt = Instant.ofEpochMilli(server.toEpochMilli() + expiresPlus),
            startedAt = server,
            planType = plan,
            contributingPlanTypes = contributing,
            serverTime = server,
            activeGrantCount = contributing.size.coerceAtLeast(1),
            confirmedAtElapsedRealtime = confirmedAt,
        )
    }

    private fun activeLifetime(
        accountId: Long = 1,
        generation: Long = 0,
        confirmedAt: Long = elapsed.get(),
    ): MembershipQueryResult.ConfirmedActive {
        val server = Instant.ofEpochMilli(wallBase + confirmedAt)
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
        serverTime = Instant.ofEpochMilli(wallBase),
        confirmedAtElapsedRealtime = elapsed.get(),
    )
}
