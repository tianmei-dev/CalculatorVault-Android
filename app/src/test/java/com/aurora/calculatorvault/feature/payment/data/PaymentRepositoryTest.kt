package com.aurora.calculatorvault.feature.payment.data

import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.ServerAccount
import com.aurora.calculatorvault.feature.payment.FakePaymentApi
import com.aurora.calculatorvault.feature.payment.FakeSession
import com.aurora.calculatorvault.feature.payment.PaymentFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertFalse
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PaymentRepositoryTest {

    private val api = FakePaymentApi()
    private val store = InMemoryPendingPurchaseStore()
    private val session = FakeSession(AccountSession.Authenticated(ServerAccount(7)))
    private var ids = 0
    private val repo = PaymentRepository(api, store, session) { "uuid-${++ids}" }
    private val owner get() = session.currentOwner(requireAuthenticated = true)!!

    @Test
    fun create_success_persistsOnlyMetadata_andUsesCurrentToken() = runTest {
        val outcome = repo.createOrder(owner, "YEARLY", retrySamePurchase = false)
        assertTrue(outcome is PaymentRepository.CreateOutcome.Created)
        assertEquals(FakePaymentApi.CreateCall("tok-7", "YEARLY", "uuid-1"), api.createCalls.single())
        assertEquals(PendingPurchaseRecord(7, "YEARLY", "uuid-1", "CV0001"), store.read())
    }

    @Test
    fun timeoutRetry_reusesSameClientRequestId_newPurchaseGetsNewOne() = runTest {
        api.createResults += PaymentResult.Failure(PaymentError.Network(timeout = true))
        val first = repo.createOrder(owner, "YEARLY", retrySamePurchase = false)
        assertEquals(PaymentRepository.CreateOutcome.Failed(PaymentError.Network(true), retryKept = true), first)
        assertEquals(PendingPurchaseRecord(7, "YEARLY", "uuid-1", null), store.read())

        repo.createOrder(owner, "YEARLY", retrySamePurchase = true)
        assertEquals(listOf("uuid-1", "uuid-1"), api.createCalls.map { it.clientRequestId })

        repo.createOrder(owner, "YEARLY", retrySamePurchase = false)
        assertEquals("uuid-2", api.createCalls.last().clientRequestId)
    }

    @Test
    fun retryForDifferentPlan_isANewRequestId() = runTest {
        api.createResults += PaymentResult.Failure(PaymentError.Http(503, null))
        repo.createOrder(owner, "YEARLY", retrySamePurchase = false)
        repo.createOrder(owner, "MONTHLY", retrySamePurchase = true)
        assertNotEquals(api.createCalls[0].clientRequestId, api.createCalls[1].clientRequestId)
    }

    @Test
    fun definitiveErrors_clearContext_unauthorizedNotifiesSession() = runTest {
        for (error in listOf(
            PaymentError.ClientRequestConflict, PaymentError.LifetimeAlreadyActive,
            PaymentError.PlanDisabled, PaymentError.PlanNotFound, PaymentError.Unauthorized,
        )) {
            api.createResults += PaymentResult.Failure(error)
            val outcome = repo.createOrder(owner, "YEARLY", retrySamePurchase = false)
            assertEquals("$error", PaymentRepository.CreateOutcome.Failed(error, retryKept = false), outcome)
            assertNull("$error", store.read())
        }
        assertEquals(listOf(7L), session.unauthorizedCalls)
    }

    @Test
    fun configMissing_and_unknownCodes_keepContextForSafeRetry() = runTest {
        api.createResults += PaymentResult.Failure(PaymentError.PaymentConfigMissing)
        assertEquals(
            PaymentRepository.CreateOutcome.Failed(PaymentError.PaymentConfigMissing, retryKept = true),
            repo.createOrder(owner, "YEARLY", false),
        )
        assertEquals("uuid-1", store.read()?.clientRequestId)
    }

    @Test
    fun concurrentCreate_onlyOneReachesServer() = runTest {
        api.createGate = CompletableDeferred()
        val first = async { repo.createOrder(owner, "YEARLY", false) }
        runCurrent()
        val second = repo.createOrder(owner, "YEARLY", false)
        assertEquals(PaymentRepository.CreateOutcome.Busy, second)
        api.createGate!!.complete(Unit)
        assertTrue(first.await() is PaymentRepository.CreateOutcome.Created)
        assertEquals(1, api.createCalls.size)
    }

    @Test
    fun guestOrValidating_cannotCreate() = runTest {
        val bound = owner
        session.state.value = AccountSession.Validating(ServerAccount(7))
        assertEquals(PaymentRepository.CreateOutcome.AccountChanged, repo.createOrder(bound, "YEARLY", false))
        session.state.value = AccountSession.Guest
        assertEquals(PaymentRepository.CreateOutcome.AccountChanged, repo.createOrder(bound, "YEARLY", false))
        assertTrue(api.createCalls.isEmpty())
    }

    @Test
    fun accountSwitchDuringCreate_dropsResult() = runTest {
        val bound = owner
        api.createGate = CompletableDeferred()
        val pending = async { repo.createOrder(bound, "YEARLY", false) }
        runCurrent()
        session.state.value = AccountSession.Authenticated(ServerAccount(8))
        session.generation = 1
        api.createGate!!.complete(Unit)
        assertEquals(PaymentRepository.CreateOutcome.AccountChanged, pending.await())
    }

    @Test
    fun query_acceptsTrustedRevalidation_butNotOtherAccount() = runTest {
        val bound = owner
        session.state.value = AccountSession.Validating(ServerAccount(7))
        assertTrue(repo.queryOrder(bound, "CV0001") is PaymentRepository.QueryOutcome.Updated)
        session.state.value = AccountSession.Authenticated(ServerAccount(8))
        assertEquals(PaymentRepository.QueryOutcome.AccountChanged, repo.queryOrder(bound, "CV0001"))
        session.state.value = AccountSession.Authenticated(ServerAccount(7))
        session.generation = 5
        assertEquals("new generation is a different owner", PaymentRepository.QueryOutcome.AccountChanged, repo.queryOrder(bound, "CV0001"))
    }

    @Test
    fun query_orderNotFound_andFulfilled_clearRecord_foreignRecordHidden() = runTest {
        repo.createOrder(owner, "YEARLY", false)
        api.queryResults += PaymentResult.Failure(PaymentError.OrderNotFound)
        repo.queryOrder(owner, "CV0001")
        assertNull(store.read())

        repo.createOrder(owner, "YEARLY", false)
        api.queryResults += PaymentResult.Success(PaymentFixtures.order(PaymentOrderStatus.FULFILLED))
        repo.queryOrder(owner, "CV0001")
        assertNull(store.read())

        store.write(PendingPurchaseRecord(99, "YEARLY", "x", "CV9"))
        assertNull(repo.pendingFor(7))
        assertEquals("CV9", repo.pendingFor(99)?.orderNo)
    }

    @Test
    fun f3_networkSignal_lostThenRestoredEmitsRestore() = runTest {
        assertTrue(repo.networkAvailable.value)
        repo.onNetworkLost()
        assertFalse(repo.networkAvailable.value)
        val restored = async { repo.networkRestored.first() }
        runCurrent()
        repo.onNetworkAvailable()
        restored.await()
        assertTrue(repo.networkAvailable.value)
    }

    @Test
    fun f4_discardOrderRecord_onlyMatchingUserAndOrder() = runTest {
        store.write(PendingPurchaseRecord(7, "YEARLY", "u", "CV1"))
        repo.discardOrderRecord(8, "CV1")
        repo.discardOrderRecord(7, "CV2")
        assertEquals("CV1", store.read()?.orderNo)
        repo.discardOrderRecord(7, "CV1")
        assertNull(store.read())
    }

    @Test
    fun query_mismatchedOrderNo_isMalformed_notApplied() = runTest {
        api.queryResults += PaymentResult.Success(PaymentFixtures.order(orderNo = "OTHER"))
        assertEquals(PaymentRepository.QueryOutcome.Failed(PaymentError.Malformed), repo.queryOrder(owner, "CV0001"))
    }
}
