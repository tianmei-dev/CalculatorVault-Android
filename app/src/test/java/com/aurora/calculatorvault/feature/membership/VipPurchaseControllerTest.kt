package com.aurora.calculatorvault.feature.membership

import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementState
import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.ServerAccount
import com.aurora.calculatorvault.feature.membership.data.MembershipSyncUi
import com.aurora.calculatorvault.feature.membership.domain.MembershipSyncAfterPurchase
import com.aurora.calculatorvault.feature.membership.domain.PlansState
import com.aurora.calculatorvault.feature.membership.domain.PurchaseNotice
import com.aurora.calculatorvault.feature.membership.domain.PurchasePhase
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseSource
import com.aurora.calculatorvault.feature.membership.presentation.PurchaseMembershipPort
import com.aurora.calculatorvault.feature.membership.presentation.VipPurchaseController
import com.aurora.calculatorvault.feature.membership.presentation.VipPurchaseEvent
import com.aurora.calculatorvault.feature.payment.FakePaymentApi
import com.aurora.calculatorvault.feature.payment.FakeSession
import com.aurora.calculatorvault.feature.payment.PaymentFixtures
import com.aurora.calculatorvault.feature.payment.data.InMemoryPendingPurchaseStore
import com.aurora.calculatorvault.feature.payment.data.PayInstruction
import com.aurora.calculatorvault.feature.payment.data.PaymentError
import com.aurora.calculatorvault.feature.payment.data.PaymentOrderStatus
import com.aurora.calculatorvault.feature.payment.data.PaymentRepository
import com.aurora.calculatorvault.feature.payment.data.PaymentResult
import com.aurora.calculatorvault.feature.payment.data.PendingPurchaseRecord
import com.aurora.calculatorvault.feature.payment.domain.PayLaunchDecision
import com.aurora.calculatorvault.feature.payment.domain.PayLaunchPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VipPurchaseControllerTest {

    private class FakeMembership : PurchaseMembershipPort {
        override val revision = MutableStateFlow(0L)
        override val syncState = MutableStateFlow<MembershipSyncUi>(MembershipSyncUi.Idle)
        var refreshCalls = 0
        var succeed = true
        var vipFor: Long? = null
        override fun requestRefreshAfterPurchase(): Boolean {
            refreshCalls++
            syncState.value = if (succeed) MembershipSyncUi.Idle else MembershipSyncUi.Failed
            if (succeed) vipFor = 7
            revision.value++
            return true
        }
        override fun hasVerifiedVip(userId: Long) = vipFor == userId
    }

    private inner class Env(
        scope: TestScope,
        initial: AccountSession = AccountSession.Authenticated(ServerAccount(7)),
        allowTestKind: Boolean = true,
        val store: InMemoryPendingPurchaseStore = InMemoryPendingPurchaseStore(),
    ) {
        val api = FakePaymentApi()
        val session = FakeSession(initial)
        val entitlement = MutableStateFlow(EntitlementState())
        val membership = FakeMembership()
        private var ids = 0
        val repository = PaymentRepository(api, store, session) { "uuid-${++ids}" }
        val events = mutableListOf<VipPurchaseEvent>()
        val controller = VipPurchaseController(
            source = VipPurchaseSource.Settings,
            scope = scope.backgroundScope,
            repository = repository,
            sessionState = session.state,
            entitlementState = entitlement,
            membership = membership,
            payLaunchPolicy = PayLaunchPolicy(allowTestKind),
            elapsedRealtime = { scope.testScheduler.currentTime },
        ).also { c -> scope.backgroundScope.launch { c.events.collect { events += it } } }

        val phase get() = controller.state.value.phase
        val ordering get() = phase as PurchasePhase.Ordering
        fun notices() = events.filterIsInstance<VipPurchaseEvent.Notice>().map { it.notice }

        fun readyToBuy() {
            controller.setAgreementAccepted(true)
        }
    }

    // 1 / 2: server plans, failure, empty, retry.
    @Test
    fun plans_displayServerData_defaultYearly() = runTest {
        val env = Env(this)
        runCurrent()
        val state = env.controller.state.value
        val plans = (state.plans as PlansState.Ready).plans
        assertEquals(listOf("月卡", "年卡", "永久会员"), plans.map { it.displayName })
        assertEquals(listOf("5.90", "19.90", "29.90"), plans.map { it.priceDisplay })
        assertEquals("YEARLY", state.selectedPlanCode)
        assertTrue(state.canRequestPurchase)
    }

    @Test
    fun plans_failureAndEmpty_blockPurchase_retryRecovers() = runTest {
        val env = Env(this)
        env.api.plansResult = PaymentResult.Failure(PaymentError.Network(false))
        env.controller.loadPlans()
        runCurrent()
        assertTrue(env.controller.state.value.plans is PlansState.Failed)
        env.readyToBuy()
        env.controller.requestPurchase()
        runCurrent()
        assertEquals(listOf(PurchaseNotice.PlansNotReady), env.notices())
        assertTrue(env.api.createCalls.isEmpty())

        env.api.plansResult = PaymentResult.Success(emptyList())
        env.controller.loadPlans()
        runCurrent()
        assertEquals(PlansState.Empty, env.controller.state.value.plans)
        assertFalse(env.controller.state.value.canRequestPurchase)

        env.api.plansResult = PaymentResult.Success(PaymentFixtures.plans)
        env.controller.loadPlans()
        runCurrent()
        assertTrue(env.controller.state.value.plans is PlansState.Ready)
    }

    // 3 + session gate
    @Test
    fun guest_isAskedToLogin_noOrder() = runTest {
        val env = Env(this, initial = AccountSession.Guest)
        runCurrent()
        env.readyToBuy()
        env.controller.requestPurchase()
        runCurrent()
        assertTrue(env.events.contains(VipPurchaseEvent.OpenLogin))
        assertEquals(listOf(PurchaseNotice.LoginRequired), env.notices())
        assertTrue(env.api.createCalls.isEmpty())
    }

    @Test
    fun nonAuthenticatedStates_neverCreate() = runTest {
        val cases = mapOf(
            AccountSession.Authenticating to PurchaseNotice.SessionVerifying,
            AccountSession.Validating(ServerAccount(7)) to PurchaseNotice.SessionVerifying,
            AccountSession.TemporarilyUnavailable(ServerAccount(7)) to PurchaseNotice.SessionUnavailable,
            AccountSession.SessionExpired to PurchaseNotice.LoginExpired,
            AccountSession.LoginFailed to PurchaseNotice.LoginError,
            AccountSession.StorageFailed to PurchaseNotice.LoginError,
        )
        for ((session, expected) in cases) {
            val env = Env(this, initial = session)
            runCurrent()
            env.readyToBuy()
            env.controller.requestPurchase()
            runCurrent()
            assertEquals("$session", listOf(expected), env.notices())
            assertTrue("$session", env.api.createCalls.isEmpty())
        }
    }

    @Test
    fun agreementRequired_beforeAnyRequest() = runTest {
        val env = Env(this)
        runCurrent()
        env.controller.requestPurchase()
        runCurrent()
        assertEquals(listOf(PurchaseNotice.AgreementRequired), env.notices())
        assertTrue(env.api.createCalls.isEmpty())
    }

    // 4 / 6 / 13: create, double tap, immediate query, URL opens cashier.
    @Test
    fun create_doubleTapCreatesOnce_queriesImmediately_urlOpensCashier() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        env.api.createGate = CompletableDeferred()
        repeat(5) { env.controller.requestPurchase() }
        runCurrent()
        assertEquals(PurchasePhase.Creating, env.phase)
        env.api.createGate!!.complete(Unit)
        runCurrent()
        assertEquals(1, env.api.createCalls.size)
        assertEquals("YEARLY", env.api.createCalls.single().planCode)
        assertEquals(listOf("CV0001"), env.api.queryCalls)
        assertEquals(PaymentOrderStatus.PENDING_PAYMENT, env.ordering.order.status)
        assertEquals("19.90", env.ordering.order.amountDisplay)
        assertEquals("年卡", env.ordering.order.planName)

        env.controller.openCashier()
        runCurrent()
        val open = env.events.filterIsInstance<VipPurchaseEvent.OpenCashier>().single()
        assertEquals(PaymentFixtures.CASHIER, open.url)
        assertFalse(open.toString().contains("secret"))
        // Opening the cashier never changes status by itself.
        assertEquals(PaymentOrderStatus.PENDING_PAYMENT, env.ordering.order.status)
    }

    // 5: lifetime owned blocks purchase; server 40012 also handled.
    @Test
    fun lifetimeOwned_blocksPurchase_and40012Refreshes() = runTest {
        val env = Env(this)
        env.entitlement.value = EntitlementState(source = EntitlementSource.Vip(7, null, permanent = true))
        runCurrent()
        assertTrue(env.controller.state.value.lifetimeOwned)
        assertFalse(env.controller.state.value.canRequestPurchase)
        env.readyToBuy()
        env.controller.requestPurchase()
        runCurrent()
        assertEquals(listOf(PurchaseNotice.LifetimeOwned), env.notices())
        assertTrue(env.api.createCalls.isEmpty())

        val other = Env(this)
        runCurrent()
        other.readyToBuy()
        other.api.createResults += PaymentResult.Failure(PaymentError.LifetimeAlreadyActive)
        other.controller.requestPurchase()
        runCurrent()
        assertEquals(PurchasePhase.Idle, other.phase)
        assertTrue(other.notices().contains(PurchaseNotice.LifetimeOwned))
        assertEquals(1, other.membership.refreshCalls)
    }

    // 7: timeout then retry with the same clientRequestId.
    @Test
    fun createTimeout_retryReusesClientRequestId() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        env.api.createResults += PaymentResult.Failure(PaymentError.Network(timeout = true))
        env.controller.requestPurchase()
        runCurrent()
        assertEquals(PurchasePhase.CreateFailed(PaymentError.Network(true), retryKept = true), env.phase)
        env.controller.requestPurchase()
        runCurrent()
        assertEquals(listOf("uuid-1", "uuid-1"), env.api.createCalls.map { it.clientRequestId })
        assertTrue(env.phase is PurchasePhase.Ordering)
    }

    @Test
    fun changingPlanAfterFailure_startsNewPurchase() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        env.api.createResults += PaymentResult.Failure(PaymentError.Network(timeout = true))
        env.controller.requestPurchase()
        runCurrent()
        env.controller.selectPlan("MONTHLY")
        env.controller.requestPurchase()
        runCurrent()
        assertEquals(listOf("uuid-1", "uuid-2"), env.api.createCalls.map { it.clientRequestId })
        assertEquals("MONTHLY", env.api.createCalls.last().planCode)
    }

    // 8 / 9: PENDING -> PAID -> FULFILLED, polling stops, membership refreshed once.
    @Test
    fun statusFlow_pendingPaidFulfilled_refreshesMembershipOnce() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        env.api.queryResults += PaymentResult.Success(PaymentFixtures.order(PaymentOrderStatus.PENDING_PAYMENT))
        env.api.queryResults += PaymentResult.Success(PaymentFixtures.order(PaymentOrderStatus.PAID))
        env.api.queryDefault = PaymentResult.Success(PaymentFixtures.order(PaymentOrderStatus.FULFILLED))
        env.controller.requestPurchase()
        runCurrent()
        assertEquals(PaymentOrderStatus.PENDING_PAYMENT, env.ordering.order.status)
        advanceTimeBy(2_001)
        assertEquals(PaymentOrderStatus.PAID, env.ordering.order.status)
        advanceTimeBy(2_001)
        assertEquals(PaymentOrderStatus.FULFILLED, env.ordering.order.status)
        assertEquals(MembershipSyncAfterPurchase.Synced, env.ordering.membershipSync)
        val queriesAtFulfilled = env.api.queryCalls.size
        advanceTimeBy(120_000)
        assertEquals("no polling after FULFILLED", queriesAtFulfilled, env.api.queryCalls.size)
        assertEquals(1, env.membership.refreshCalls)
        assertEquals(1, env.notices().count { it == PurchaseNotice.PurchaseCompleted })
        assertEquals(null, env.store.read())
    }

    // 10: fulfilled but membership sync fails, retry succeeds.
    @Test
    fun fulfilled_membershipSyncFails_thenRetrySucceeds() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        env.membership.succeed = false
        env.api.queryDefault = PaymentResult.Success(PaymentFixtures.order(PaymentOrderStatus.FULFILLED))
        env.controller.requestPurchase()
        runCurrent()
        assertEquals("bounded auto re-fetch still running", MembershipSyncAfterPurchase.Syncing, env.ordering.membershipSync)
        advanceTimeBy(10_000)
        assertEquals(MembershipSyncAfterPurchase.Failed, env.ordering.membershipSync)
        assertEquals("3 bounded attempts", 3, env.membership.refreshCalls)
        env.membership.succeed = true
        env.controller.retryMembershipSync()
        runCurrent()
        assertEquals(MembershipSyncAfterPurchase.Synced, env.ordering.membershipSync)
        assertEquals(PaymentOrderStatus.FULFILLED, env.ordering.order.status)
    }

    @Test
    fun fulfilled_staleFirstRefresh_autoRefetchSyncs() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        env.membership.succeed = false
        env.api.queryDefault = PaymentResult.Success(PaymentFixtures.order(PaymentOrderStatus.FULFILLED))
        env.controller.requestPurchase()
        runCurrent()
        env.membership.succeed = true
        advanceTimeBy(2_000)
        assertEquals(MembershipSyncAfterPurchase.Synced, env.ordering.membershipSync)
        assertEquals(2, env.membership.refreshCalls)
    }

    // 11: EXPIRED stops automatic polling; manual refresh can still observe a late payment.
    @Test
    fun expired_stopsPolling_manualRefreshSeesLatePayment() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        env.api.queryDefault = PaymentResult.Success(PaymentFixtures.order(PaymentOrderStatus.EXPIRED))
        env.controller.requestPurchase()
        runCurrent()
        assertEquals(PaymentOrderStatus.EXPIRED, env.ordering.order.status)
        val count = env.api.queryCalls.size
        advanceTimeBy(300_000)
        assertEquals(count, env.api.queryCalls.size)
        env.api.queryDefault = PaymentResult.Success(PaymentFixtures.order(PaymentOrderStatus.FULFILLED))
        env.controller.refreshOrder()
        runCurrent()
        assertEquals(PaymentOrderStatus.FULFILLED, env.ordering.order.status)
        assertEquals(MembershipSyncAfterPurchase.Synced, env.ordering.membershipSync)
    }

    // 12 / 14: TEST kind never opens a browser; Release policy rejects TEST.
    @Test
    fun testKind_debugShowsTestPayment_neverOpensBrowser() = runTest {
        val env = Env(this, allowTestKind = true)
        runCurrent()
        env.readyToBuy()
        env.api.createDefault = PaymentResult.Success(PaymentFixtures.order(pay = PayInstruction("TEST", null)))
        env.api.queryDefault = PaymentResult.Success(PaymentFixtures.order(pay = PayInstruction("TEST", null)))
        env.controller.requestPurchase()
        runCurrent()
        assertEquals(PayLaunchDecision.TestPayment, env.ordering.order.payDecision)
        env.controller.openCashier()
        runCurrent()
        assertTrue(env.events.none { it is VipPurchaseEvent.OpenCashier })
        assertEquals(PaymentOrderStatus.PENDING_PAYMENT, env.ordering.order.status)
    }

    @Test
    fun releasePolicy_rejectsTestAndHttpLinks() = runTest {
        for (pay in listOf(PayInstruction("TEST", null), PayInstruction("URL", "http://cashier.example/p"), PayInstruction("ALIPAY", "x"))) {
            val env = Env(this, allowTestKind = false)
            runCurrent()
            env.readyToBuy()
            env.api.createDefault = PaymentResult.Success(PaymentFixtures.order(pay = pay))
            env.api.queryDefault = PaymentResult.Success(PaymentFixtures.order(pay = pay))
            env.controller.requestPurchase()
            runCurrent()
            assertTrue("$pay", env.ordering.order.payDecision is PayLaunchDecision.Rejected)
            env.controller.openCashier()
            runCurrent()
            assertTrue("$pay", env.events.none { it is VipPurchaseEvent.OpenCashier })
            assertTrue("$pay", env.notices().contains(PurchaseNotice.PayLinkInvalid))
        }
    }

    // 15: background pauses polling; resume queries immediately.
    @Test
    fun pauseStopsPolling_resumeQueriesImmediately() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        env.controller.requestPurchase()
        runCurrent()
        env.controller.onVisibilityChanged(false)
        val paused = env.api.queryCalls.size
        advanceTimeBy(60_000)
        assertEquals(paused, env.api.queryCalls.size)
        env.api.queryDefault = PaymentResult.Success(PaymentFixtures.order(PaymentOrderStatus.PAID))
        env.controller.onVisibilityChanged(true)
        runCurrent()
        assertEquals(paused + 1, env.api.queryCalls.size)
        assertEquals(PaymentOrderStatus.PAID, env.ordering.order.status)
    }

    @Test
    fun pollCadence_fastThenSlow() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        env.controller.requestPurchase()
        runCurrent()
        val start = env.api.queryCalls.size
        advanceTimeBy(60_000)
        val fast = env.api.queryCalls.size - start
        advanceTimeBy(60_000)
        val slow = env.api.queryCalls.size - start - fast
        assertTrue("fast=$fast", fast in 29..31)
        assertTrue("slow=$slow", slow in 5..7)
    }

    // 15: process restore resumes the stored order without creating a new one.
    @Test
    fun processRestore_resumesStoredOrder_noNewCreate() = runTest {
        val store = InMemoryPendingPurchaseStore().also { it.write(PendingPurchaseRecord(7, "YEARLY", "uuid-old", "CV0001")) }
        val env = Env(this, store = store)
        runCurrent()
        assertTrue(env.phase is PurchasePhase.Ordering)
        assertEquals(listOf("CV0001"), env.api.queryCalls)
        assertTrue(env.api.createCalls.isEmpty())
    }

    @Test
    fun processRestore_unconfirmedCreate_retriesWithSameRequestId() = runTest {
        val store = InMemoryPendingPurchaseStore().also { it.write(PendingPurchaseRecord(7, "MONTHLY", "uuid-old", null)) }
        val env = Env(this, store = store)
        runCurrent()
        assertTrue(env.phase is PurchasePhase.CreateFailed)
        assertEquals("MONTHLY", env.controller.state.value.selectedPlanCode)
        env.readyToBuy()
        env.controller.requestPurchase()
        runCurrent()
        assertEquals("uuid-old", env.api.createCalls.single().clientRequestId)
    }

    @Test
    fun processRestore_foreignRecord_ignored_orderNotFoundDiscards() = runTest {
        val foreign = InMemoryPendingPurchaseStore().also { it.write(PendingPurchaseRecord(99, "YEARLY", "u", "CV9")) }
        val env = Env(this, store = foreign)
        runCurrent()
        assertEquals(PurchasePhase.Idle, env.phase)
        assertTrue(env.api.queryCalls.isEmpty())

        val mine = InMemoryPendingPurchaseStore().also { it.write(PendingPurchaseRecord(7, "YEARLY", "u", "CV0001")) }
        val env2 = Env(this, store = mine)
        env2.api.queryDefault = PaymentResult.Failure(PaymentError.OrderNotFound)
        runCurrent()
        assertEquals(PurchasePhase.Idle, env2.phase)
        assertTrue(env2.notices().contains(PurchaseNotice.OrderNotFound))
        assertEquals(null, mine.read())
    }

    // 16: logout / switch stops old order; late response ignored.
    @Test
    fun logoutDuringQuery_stopsPolling_lateResponseIgnored() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        env.controller.requestPurchase()
        runCurrent()
        env.api.queryGate = CompletableDeferred()
        env.api.queryDefault = PaymentResult.Success(PaymentFixtures.order(PaymentOrderStatus.FULFILLED))
        advanceTimeBy(2_001)
        env.session.state.value = AccountSession.Guest
        runCurrent()
        assertEquals(PurchasePhase.Idle, env.phase)
        assertTrue(env.notices().contains(PurchaseNotice.AccountChanged))
        env.api.queryGate!!.complete(Unit)
        runCurrent()
        assertEquals(PurchasePhase.Idle, env.phase)
        assertEquals(0, env.membership.refreshCalls)
        val calls = env.api.queryCalls.size
        advanceTimeBy(60_000)
        assertEquals(calls, env.api.queryCalls.size)
    }

    @Test
    fun switchAccount_stopsOldOrder_revalidationKeepsIt() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        env.controller.requestPurchase()
        runCurrent()
        env.session.state.value = AccountSession.Validating(ServerAccount(7))
        runCurrent()
        assertTrue("trusted revalidation keeps the order", env.phase is PurchasePhase.Ordering)
        env.session.state.value = AccountSession.Authenticated(ServerAccount(7))
        runCurrent()
        assertTrue(env.phase is PurchasePhase.Ordering)
        env.session.state.value = AccountSession.Authenticated(ServerAccount(8))
        env.session.generation = 1
        runCurrent()
        assertEquals(PurchasePhase.Idle, env.phase)
        val calls = env.api.queryCalls.size
        advanceTimeBy(30_000)
        assertEquals(calls, env.api.queryCalls.size)
    }

    @Test
    fun queryUnauthorized_stopsAndAsksLogin() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        env.api.queryDefault = PaymentResult.Failure(PaymentError.Unauthorized)
        env.controller.requestPurchase()
        runCurrent()
        assertEquals(PurchasePhase.Idle, env.phase)
        assertEquals(listOf(7L), env.session.unauthorizedCalls)
        assertTrue(env.events.contains(VipPurchaseEvent.OpenLogin))
    }

    // 17: network failure keeps last status, backs off, recovers on network restore.
    @Test
    fun queryFailures_keepStatus_backOff_networkRestoreQueriesNow() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        env.controller.requestPurchase()
        runCurrent()
        env.api.queryDefault = PaymentResult.Failure(PaymentError.Network(timeout = true))
        advanceTimeBy(2_001)
        assertEquals("status unchanged by failure", PaymentOrderStatus.PENDING_PAYMENT, env.ordering.order.status)
        assertTrue(env.ordering.lastQueryFailed)
        val before = env.api.queryCalls.size
        advanceTimeBy(20_000)
        val backedOff = env.api.queryCalls.size - before
        assertTrue("backoff calls=$backedOff", backedOff in 2..4)

        env.api.queryDefault = PaymentResult.Success(PaymentFixtures.order(PaymentOrderStatus.PAID))
        env.repository.onNetworkAvailable()
        runCurrent()
        assertEquals(PaymentOrderStatus.PAID, env.ordering.order.status)
        assertFalse(env.ordering.lastQueryFailed)
    }

    @Test
    fun serverTimeAnchorsDeadline_notDeviceWallClock() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        advanceTimeBy(5_000)
        env.controller.requestPurchase()
        runCurrent()
        val deadline = env.ordering.order.expiresAtElapsedMillis!!
        assertEquals(testScheduler.currentTime + 30 * 60_000L, deadline)
    }

    @Test
    fun planErrors_reloadPlans_conflictResets() = runTest {
        val env = Env(this)
        runCurrent()
        env.readyToBuy()
        val plansBefore = env.api.plansCalls
        env.api.createResults += PaymentResult.Failure(PaymentError.PlanDisabled)
        env.controller.requestPurchase()
        runCurrent()
        assertEquals(PurchasePhase.Idle, env.phase)
        assertEquals(plansBefore + 1, env.api.plansCalls)
        env.api.createResults += PaymentResult.Failure(PaymentError.ClientRequestConflict)
        env.controller.requestPurchase()
        runCurrent()
        assertTrue(env.notices().containsAll(listOf(PurchaseNotice.PlanUnavailable, PurchaseNotice.RequestConflict)))
    }
}
