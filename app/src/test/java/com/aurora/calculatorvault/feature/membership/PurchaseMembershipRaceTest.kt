package com.aurora.calculatorvault.feature.membership

import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementRepository
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.entitlement.domain.MembershipResult
import com.aurora.calculatorvault.feature.login.domain.WeChatServerLoginRequest
import com.aurora.calculatorvault.feature.login.session.AccountSessionRepository
import com.aurora.calculatorvault.feature.login.session.AuthApi
import com.aurora.calculatorvault.feature.login.session.LoginResult
import com.aurora.calculatorvault.feature.login.session.ServerAccount
import com.aurora.calculatorvault.feature.login.session.SessionCredential
import com.aurora.calculatorvault.feature.login.session.TokenStore
import com.aurora.calculatorvault.feature.membership.data.MembershipApi
import com.aurora.calculatorvault.feature.membership.data.MembershipQueryResult
import com.aurora.calculatorvault.feature.membership.data.MembershipQueryScope
import com.aurora.calculatorvault.feature.membership.data.MembershipRefreshCoordinator
import com.aurora.calculatorvault.feature.membership.data.ServerMembershipProvider
import com.aurora.calculatorvault.feature.membership.domain.MembershipSyncAfterPurchase
import com.aurora.calculatorvault.feature.membership.domain.PurchasePhase
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseSource
import com.aurora.calculatorvault.feature.membership.presentation.PurchaseMembershipPort
import com.aurora.calculatorvault.feature.membership.presentation.VipPurchaseController
import com.aurora.calculatorvault.feature.payment.FakePaymentApi
import com.aurora.calculatorvault.feature.payment.PaymentFixtures
import com.aurora.calculatorvault.feature.payment.data.InMemoryPendingPurchaseStore
import com.aurora.calculatorvault.feature.payment.data.PaymentOrderStatus
import com.aurora.calculatorvault.feature.payment.data.PaymentRepository
import com.aurora.calculatorvault.feature.payment.data.PaymentResult
import com.aurora.calculatorvault.feature.payment.data.PaymentSessionPort
import com.aurora.calculatorvault.feature.payment.data.PurchaseOwner
import com.aurora.calculatorvault.feature.payment.domain.PayLaunchPolicy
import com.aurora.calculatorvault.feature.trial.DeviceIdentityProvider
import com.aurora.calculatorvault.feature.trial.DeviceTrialRepository
import com.aurora.calculatorvault.feature.trial.TrialApi
import com.aurora.calculatorvault.feature.trial.TrialClock
import com.aurora.calculatorvault.feature.trial.TrialRecord
import com.aurora.calculatorvault.feature.trial.TrialReply
import com.aurora.calculatorvault.feature.trial.TrialStore
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B05: FULFILLED arrives while a same-account /membership/me request is already in flight.
 * The provider deduplicates the post-purchase refresh into that request, whose server read
 * predates the grant. Real coordinator + provider + entitlement; only HTTP is scripted.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PurchaseMembershipRaceTest {

    private val wallBase = 1_700_000_000_000L

    private inner class GatedMeApi(private val scope: TestScope) : MembershipApi {
        val calls = AtomicInteger(0)
        var gate: CompletableDeferred<Unit>? = null
        @Volatile var active = false
        override suspend fun queryMe(scope: MembershipQueryScope): MembershipQueryResult {
            calls.incrementAndGet()
            // Server answer is decided when the request reaches the server (before the gate).
            val answerActive = active
            gate?.await()
            val now = this.scope.testScheduler.currentTime
            val server = Instant.ofEpochMilli(wallBase + now)
            return if (answerActive) {
                MembershipQueryResult.ConfirmedActive(
                    scope.accountId, scope.generation, false, server.plusSeconds(30L * 86_400), server,
                    "MONTHLY", listOf("MONTHLY"), server, 1, now,
                )
            } else {
                MembershipQueryResult.ConfirmedNotEntitled(scope.accountId, scope.generation, "NONE", server, now)
            }
        }
    }

    @Test
    fun fulfilledDuringInFlightMe_staleJoin_thenBoundedRefetchSyncs() = runTest {
        val elapsed = { testScheduler.currentTime }
        val meApi = GatedMeApi(this)
        val auth = object : AuthApi {
            override suspend fun login(code: String) =
                LoginResult(SessionCredential("tok", Instant.now().plusSeconds(3600)), ServerAccount(7))
            override suspend fun me(token: String) = ServerAccount(7)
            override suspend fun logout(token: String) = Unit
        }
        val tokens = object : TokenStore {
            var v: SessionCredential? = null
            override fun read() = v
            override fun write(value: SessionCredential) { v = value }
            override fun clear() { v = null }
        }
        val sessions = AccountSessionRepository(auth, tokens, backgroundScope)
        val clock = object : TrialClock {
            override fun wall() = wallBase + elapsed()
            override fun elapsed() = elapsed()
            override fun boot() = "1"
        }
        val trial = DeviceTrialRepository(
            object : TrialStore {
                var value: TrialRecord? = TrialRecord(clock.wall(), 0L, clock.wall(), 0L, "1", expired = true)
                override fun read() = value
                override fun write(record: TrialRecord) { value = record }
                override fun seen() = true
                override fun markSeen() = Unit
            },
            clock, DeviceIdentityProvider { "dev" },
            TrialApi { _, _ -> TrialReply("EXPIRED", 0, 0, 0, 0) },
            backgroundScope, { true }, { false },
        ).also { it.initialize() }
        val provider = ServerMembershipProvider(meApi, backgroundScope, elapsed)
        val entitlement = EntitlementRepository(trial, sessions, provider, elapsed, { wallBase + elapsed() }, backgroundScope)
        val coordinator = MembershipRefreshCoordinator(sessions, entitlement, provider, backgroundScope, elapsed)
            .also { it.start() }

        sessions.acceptAuthorization(WeChatServerLoginRequest("code"))
        runCurrent()
        assertEquals(1, meApi.calls.get())

        // A manual /me is in flight; its server read happens before the payment is fulfilled.
        meApi.gate = CompletableDeferred()
        assertTrue(coordinator.refreshManual())
        runCurrent()
        assertEquals(2, meApi.calls.get())
        meApi.active = true // fulfillment happens on the server now

        val api = FakePaymentApi().also {
            it.queryDefault = PaymentResult.Success(PaymentFixtures.order(PaymentOrderStatus.FULFILLED, planCode = "MONTHLY"))
        }
        val repository = PaymentRepository(
            api, InMemoryPendingPurchaseStore(),
            object : PaymentSessionPort {
                override fun currentOwner(requireAuthenticated: Boolean) =
                    PurchaseOwner(7, entitlement.currentAccountGeneration())
                override fun accessToken() = "tok"
                override fun onUnauthorized(userId: Long) = Unit
            },
        )
        val controller = VipPurchaseController(
            source = VipPurchaseSource.Settings,
            scope = backgroundScope,
            repository = repository,
            sessionState = sessions.state,
            entitlementState = entitlement.state,
            membership = object : PurchaseMembershipPort {
                override val revision = provider.membershipStateRevision
                override val syncState = coordinator.syncState
                override fun requestRefreshAfterPurchase() = coordinator.refreshAfterPurchase()
                override fun hasVerifiedVip(userId: Long) =
                    provider.membershipFor(userId, entitlement.currentAccountGeneration()) is MembershipResult.VerifiedActive
            },
            payLaunchPolicy = PayLaunchPolicy(allowTestKind = true),
            elapsedRealtime = elapsed,
        )
        runCurrent()
        controller.setAgreementAccepted(true)
        controller.requestPurchase()
        runCurrent()
        val ordering = controller.state.value.phase as PurchasePhase.Ordering
        assertEquals(PaymentOrderStatus.FULFILLED, ordering.order.status)
        assertEquals("post-purchase refresh joined the in-flight request", 2, meApi.calls.get())

        meApi.gate!!.complete(Unit)
        meApi.gate = null
        runCurrent()
        // Reproduction: the joined (stale) answer leaves no VIP although the server granted it.
        assertTrue(entitlement.state.value.source !is EntitlementSource.Vip)
        assertEquals(MembershipSyncAfterPurchase.Syncing, (controller.state.value.phase as PurchasePhase.Ordering).membershipSync)

        advanceTimeBy(2_000)
        assertEquals("bounded re-fetch issued a fresh request", 3, meApi.calls.get())
        assertTrue(entitlement.state.value.source is EntitlementSource.Vip)
        assertEquals(MembershipSyncAfterPurchase.Synced, (controller.state.value.phase as PurchasePhase.Ordering).membershipSync)
    }
}
