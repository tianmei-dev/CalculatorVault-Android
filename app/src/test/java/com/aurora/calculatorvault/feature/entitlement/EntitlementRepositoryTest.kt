package com.aurora.calculatorvault.feature.entitlement

import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.DefaultMembershipProvider
import com.aurora.calculatorvault.feature.entitlement.domain.DenialReason
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementDisplayMapper
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementRepository
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.entitlement.domain.FakeMembershipProvider
import com.aurora.calculatorvault.feature.login.domain.WeChatServerLoginRequest
import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.AccountSessionRepository
import com.aurora.calculatorvault.feature.login.session.AuthApi
import com.aurora.calculatorvault.feature.login.session.LoginResult
import com.aurora.calculatorvault.feature.login.session.ServerAccount
import com.aurora.calculatorvault.feature.login.session.SessionCredential
import com.aurora.calculatorvault.feature.login.session.TokenStore
import com.aurora.calculatorvault.feature.settings.presentation.AccountStatus
import com.aurora.calculatorvault.feature.settings.presentation.MembershipStatus
import com.aurora.calculatorvault.feature.trial.DeviceTrialRepository
import com.aurora.calculatorvault.feature.trial.DeviceTrialState
import com.aurora.calculatorvault.feature.trial.TRIAL_MILLIS
import com.aurora.calculatorvault.feature.trial.TrialApi
import com.aurora.calculatorvault.feature.trial.TrialClock
import com.aurora.calculatorvault.feature.trial.TrialRecord
import com.aurora.calculatorvault.feature.trial.TrialReply
import com.aurora.calculatorvault.feature.trial.TrialStatus
import com.aurora.calculatorvault.feature.trial.TrialStore
import com.aurora.calculatorvault.feature.trial.DeviceIdentityProvider
import java.time.Instant
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unified entitlement core tests.
 * Any test that asserts VIP Allowed uses [FakeMembershipProvider] and is labeled Fake — not real server VIP.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EntitlementRepositoryTest {

    private class Clock : TrialClock {
        var time = 1_800_000_000_000L
        var uptime = 1_000L
        var bootId: String? = "1"
        override fun wall() = time
        override fun elapsed() = uptime
        override fun boot() = bootId
        fun advance(ms: Long) {
            time += ms
            uptime += ms
        }
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

    private class Api(val clock: Clock) : TrialApi {
        var failure: Exception? = null
        override suspend fun activate(deviceKey: String, remainingSecondsCap: Long): TrialReply {
            failure?.let { throw it }
            return TrialReply(
                "ACTIVE",
                clock.time,
                clock.time + TRIAL_MILLIS,
                clock.time,
                TRIAL_MILLIS,
            )
        }
    }

    private class TokenBucket : TokenStore {
        var value: SessionCredential? = null
        override fun read() = value
        override fun write(value: SessionCredential) {
            this.value = value
        }
        override fun clear() {
            value = null
        }
    }

    private open class SessionApi(private var accountId: Long = 1L) : AuthApi {
        override suspend fun login(code: String) =
            LoginResult(SessionCredential("tok", Instant.now().plusSeconds(3600)), ServerAccount(accountId))
        override suspend fun me(token: String) = ServerAccount(accountId)
        override suspend fun logout(token: String) = Unit
        fun setAccountId(id: Long) {
            accountId = id
        }
    }

    private fun kotlinx.coroutines.test.TestScope.trial(
        store: Store = Store(),
        clock: Clock = Clock(),
        api: Api = Api(clock),
    ): Pair<DeviceTrialRepository, Clock> {
        val repo = DeviceTrialRepository(
            store,
            clock,
            DeviceIdentityProvider { "device-key" },
            api,
            backgroundScope,
            { true },
            { true },
        )
        return repo to clock
    }

    private fun kotlinx.coroutines.test.TestScope.session(
        api: SessionApi = SessionApi(),
        store: TokenBucket = TokenBucket(),
    ) = AccountSessionRepository(api, store, backgroundScope)

    private fun kotlinx.coroutines.test.TestScope.entitlements(
        trial: DeviceTrialRepository,
        session: AccountSessionRepository,
        membership: FakeMembershipProvider = FakeMembershipProvider(),
        clock: Clock,
    ): EntitlementRepository = EntitlementRepository(
        deviceTrial = trial,
        accountSession = session,
        membershipProvider = membership,
        elapsedRealtime = { clock.uptime },
        wallClockEpochMillis = { clock.time },
        scope = backgroundScope,
    )

    private fun ActionDecision.isAllowed() = this is ActionDecision.Allowed

    @Test
    fun `priority VIP over trial over free - Fake VIP`() = runTest {
        val (trial, clock) = trial()
        trial.initialize()
        val session = session()
        val membership = FakeMembershipProvider()
        val repo = entitlements(trial, session, membership, clock)
        runCurrent()
        assertTrue(repo.state.value.source is EntitlementSource.DeviceTrial)

        session.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        membership.grant(
            accountId = 1L,
            generation = repo.currentAccountGeneration(),
            expiresAtEpochMillis = clock.time + 86_400_000L,
        )
        repo.refresh()
        runCurrent()
        assertTrue(repo.state.value.source is EntitlementSource.Vip)
        assertTrue(repo.actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION).isAllowed())

        membership.clear()
        repo.refresh()
        runCurrent()
        assertTrue(repo.state.value.source is EntitlementSource.DeviceTrial)

        clock.advance(TRIAL_MILLIS)
        trial.refresh()
        repo.refresh()
        runCurrent()
        assertEquals(EntitlementSource.None, repo.state.value.source)
        assertFalse(repo.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT).isAllowed())
    }

    @Test
    fun `guest and signed-in users both use valid device trial`() = runTest {
        val (trial, clock) = trial()
        trial.initialize()
        val session = session()
        val repo = entitlements(trial, session, clock = clock)
        runCurrent()
        assertTrue(repo.actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION).isAllowed())

        session.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        repo.refresh()
        assertTrue(repo.state.value.source is EntitlementSource.DeviceTrial)
        assertTrue(repo.actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION).isAllowed())

        val ui = EntitlementDisplayMapper.toAccountUiState(session.state.value, repo.state.value, trial.state.value)
        assertTrue(ui.account is AccountStatus.WeChatLoggedIn)
        assertTrue(ui.visibleMembership is MembershipStatus.TrialActive)
    }

    @Test
    fun `offline valid trial is not revoked by network failure`() = runTest {
        val clock = Clock()
        val store = Store()
        val api = Api(clock).apply { failure = java.io.IOException("offline") }
        val (trial, _) = trial(store, clock, api)
        trial.initialize()
        assertEquals(TrialStatus.PENDING_SYNC, trial.state.value.status)
        trial.sync()
        runCurrent()
        assertEquals(TrialStatus.TEMPORARILY_UNAVAILABLE, trial.state.value.status)
        assertTrue(trial.state.value.remainingMillis > 0)

        val repo = entitlements(trial, session(), clock = clock)
        runCurrent()
        assertTrue(repo.state.value.source is EntitlementSource.DeviceTrial)
        assertTrue(repo.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT).isAllowed())
    }

    @Test
    fun `expired untrustworthy or zero remaining does not authorize`() = runTest {
        val (trial, clock) = trial()
        trial.initialize()
        clock.advance(TRIAL_MILLIS)
        trial.refresh()
        val repo = entitlements(trial, session(), clock = clock)
        runCurrent()
        assertEquals(TrialStatus.EXPIRED, trial.state.value.status)
        assertEquals(EntitlementSource.None, repo.state.value.source)
        assertEquals(
            ActionDecision.Denied(DenialReason.REQUIRES_ENTITLEMENT),
            repo.actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION),
        )

        // Untrustworthy reboot rollback → VERIFICATION_REQUIRED with remaining forced to 0 by trial repo.
        val store2 = Store()
        val clock2 = Clock()
        val (trial2, _) = trial(store2, clock2)
        trial2.initialize()
        clock2.time -= 3_600_000
        clock2.uptime = 0
        clock2.bootId = "2"
        val cold = DeviceTrialRepository(
            store2,
            clock2,
            DeviceIdentityProvider { "device-key" },
            Api(clock2),
            backgroundScope,
            { true },
            { true },
        )
        cold.initialize()
        assertEquals(TrialStatus.VERIFICATION_REQUIRED, cold.state.value.status)
        val repo2 = entitlements(cold, session(), clock = clock2)
        runCurrent()
        assertEquals(EntitlementSource.None, repo2.state.value.source)
    }

    @Test
    fun `Fake VIP expiry falls back to valid trial without free flash`() = runTest {
        val (trial, clock) = trial()
        trial.initialize()
        val session = session()
        val membership = FakeMembershipProvider()
        val repo = entitlements(trial, session, membership, clock)
        session.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        val expires = clock.time + 1_000L
        membership.grant(1L, repo.currentAccountGeneration(), expires)
        repo.refresh()
        runCurrent()
        assertTrue(repo.state.value.source is EntitlementSource.Vip)

        clock.advance(2_000L)
        // Precise boundary: evaluate uses wall clock without waiting for ticker.
        val decision = repo.actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION)
        assertTrue(decision.isAllowed())
        assertTrue(repo.state.value.source is EntitlementSource.DeviceTrial)
    }

    @Test
    fun `logout with valid trial still allows private album import`() = runTest {
        val (trial, clock) = trial()
        trial.initialize()
        val session = session()
        val membership = FakeMembershipProvider()
        val repo = entitlements(trial, session, membership, clock)
        session.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        membership.grant(1L, repo.currentAccountGeneration(), null)
        repo.refresh()
        runCurrent()
        assertTrue(repo.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT).isAllowed())
        session.logout()
        runCurrent()
        repo.refresh()
        assertTrue(repo.state.value.source is EntitlementSource.DeviceTrial)
        assertTrue(repo.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT).isAllowed())
    }

    @Test
    fun `logout revokes Fake VIP immediately but keeps trial`() = runTest {
        val (trial, clock) = trial()
        trial.initialize()
        val session = session()
        val membership = FakeMembershipProvider()
        val repo = entitlements(trial, session, membership, clock)
        session.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        membership.grant(1L, repo.currentAccountGeneration(), clock.time + 86_400_000L)
        repo.refresh()
        runCurrent()
        assertTrue(repo.state.value.source is EntitlementSource.Vip)

        session.logout()
        runCurrent()
        repo.refresh()
        assertTrue(repo.state.value.source is EntitlementSource.DeviceTrial)
        assertTrue(repo.actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION).isAllowed())
        // Stale Fake claim for previous generation must not resurrect VIP while guest.
        assertTrue(repo.state.value.source !is EntitlementSource.Vip)
    }

    @Test
    fun `account switch and stale Fake claim cannot restore VIP`() = runTest {
        val (trial, clock) = trial()
        trial.initialize()
        val api = SessionApi(1L)
        val session = session(api)
        val membership = FakeMembershipProvider()
        val repo = entitlements(trial, session, membership, clock)
        session.acceptAuthorization(WeChatServerLoginRequest("a"))
        runCurrent()
        val genA = repo.currentAccountGeneration()
        membership.grant(1L, genA, clock.time + 86_400_000L)
        repo.refresh()
        assertTrue(repo.state.value.source is EntitlementSource.Vip)

        session.logout()
        runCurrent()
        api.setAccountId(2L)
        session.acceptAuthorization(WeChatServerLoginRequest("b"))
        runCurrent()
        repo.refresh()
        // Old Fake claim still points at account 1 / genA — must not apply to account 2.
        assertTrue(repo.state.value.source is EntitlementSource.DeviceTrial)
        membership.grant(2L, repo.currentAccountGeneration(), clock.time + 86_400_000L)
        repo.refresh()
        assertTrue(repo.state.value.source is EntitlementSource.Vip)
    }

    @Test
    fun `precise trial expiry does not wait for thirty second ticker`() = runTest {
        val (trial, clock) = trial()
        trial.initialize()
        val repo = entitlements(trial, session(), clock = clock)
        runCurrent()
        assertTrue(repo.actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION).isAllowed())

        clock.advance(trial.state.value.remainingMillis)
        val decision = repo.actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION)
        assertFalse(decision.isAllowed())
        assertEquals(EntitlementSource.None, repo.state.value.source)
    }

    @Test
    fun `app management view restore and manage stay free without entitlement`() = runTest {
        val (trial, clock) = trial()
        trial.initialize()
        clock.advance(TRIAL_MILLIS)
        trial.refresh()
        val repo = entitlements(trial, session(), clock = clock)
        runCurrent()
        assertEquals(EntitlementSource.None, repo.state.value.source)
        assertTrue(repo.actionPolicy.evaluate(EntitlementCapability.APP_MANAGEMENT).isAllowed())
        assertTrue(repo.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_VIEW_RESTORE).isAllowed())
        assertTrue(repo.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_MANAGE_EXISTING).isAllowed())
        assertFalse(repo.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT).isAllowed())
        assertFalse(repo.actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION).isAllowed())
    }

    @Test
    fun `production DefaultMembershipProvider never grants VIP`() = runTest {
        val (trial, clock) = trial()
        trial.initialize()
        val session = session()
        session.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        val repo = EntitlementRepository(
            deviceTrial = trial,
            accountSession = session,
            membershipProvider = DefaultMembershipProvider(),
            elapsedRealtime = { clock.uptime },
            wallClockEpochMillis = { clock.time },
            scope = backgroundScope,
        )
        runCurrent()
        repo.refresh()
        assertTrue(repo.state.value.source is EntitlementSource.DeviceTrial)
        assertFalse(DefaultMembershipProvider().membershipFor(1L, 0L) is com.aurora.calculatorvault.feature.entitlement.domain.MembershipResult.VerifiedActive)
    }

    @Test
    fun `resolveEffectiveSource honors explicit None even when trial fields remain`() {
        val snap = com.aurora.calculatorvault.feature.entitlement.domain.EntitlementState(
            source = EntitlementSource.None,
            trialStatus = TrialStatus.ACTIVE,
            trialRemainingMillis = 3_600_000L,
            trialDeadlineElapsedMillis = null,
        )
        assertEquals(
            EntitlementSource.None,
            EntitlementRepository.resolveEffectiveSource(snap, nowElapsed = 10_000L, nowWall = 1L),
        )
    }

    @Test
    fun `display mapping does not mark pending sync as expired`() {
        val pending = DeviceTrialState(TrialStatus.PENDING_SYNC, 3_600_000L)
        assertTrue(
            EntitlementDisplayMapper.toDeviceTrialMembership(pending) is MembershipStatus.TrialActive,
        )
        val temp = DeviceTrialState(TrialStatus.TEMPORARILY_UNAVAILABLE, 3_600_000L)
        assertTrue(
            EntitlementDisplayMapper.toDeviceTrialMembership(temp) is MembershipStatus.TrialActive,
        )
        val verify = DeviceTrialState(TrialStatus.VERIFICATION_REQUIRED, 0L)
        assertEquals(MembershipStatus.NotSubscribed, EntitlementDisplayMapper.toDeviceTrialMembership(verify))
        assertFalse(EntitlementDisplayMapper.trialSessionMessage(pending).contains("已到期"))
        assertFalse(EntitlementDisplayMapper.trialSessionMessage(temp).contains("已到期"))
    }
}
