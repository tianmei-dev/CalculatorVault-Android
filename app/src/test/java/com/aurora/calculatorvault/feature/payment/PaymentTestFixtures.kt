package com.aurora.calculatorvault.feature.payment

import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.entitlementAccount
import com.aurora.calculatorvault.feature.payment.data.MembershipPlanOffer
import com.aurora.calculatorvault.feature.payment.data.PayInstruction
import com.aurora.calculatorvault.feature.payment.data.PaymentApi
import com.aurora.calculatorvault.feature.payment.data.PaymentOrder
import com.aurora.calculatorvault.feature.payment.data.PaymentOrderStatus
import com.aurora.calculatorvault.feature.payment.data.PaymentResult
import com.aurora.calculatorvault.feature.payment.data.PaymentSessionPort
import com.aurora.calculatorvault.feature.payment.data.PurchaseOwner
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

object PaymentFixtures {
    val plans = listOf(
        MembershipPlanOffer("MONTHLY", "月卡", 590, "5.90", 30, false),
        MembershipPlanOffer("YEARLY", "年卡", 1990, "19.90", 365, false),
        MembershipPlanOffer("LIFETIME", "永久会员", 2990, "29.90", null, true),
    )

    const val CASHIER = "https://cashier.example/pay?sig=secret"

    fun order(
        status: PaymentOrderStatus = PaymentOrderStatus.PENDING_PAYMENT,
        orderNo: String = "CV0001",
        planCode: String = "YEARLY",
        pay: PayInstruction? = PayInstruction("URL", CASHIER),
        serverTime: Instant = Instant.parse("2026-11-09T12:00:00Z"),
        expiresAt: Instant = serverTime.plusSeconds(30 * 60),
    ) = PaymentOrder(
        orderNo = orderNo,
        planCode = planCode,
        amountFen = 1990,
        amountDisplay = "19.90",
        currency = "CNY",
        status = status,
        expiresAt = expiresAt,
        paidAt = null,
        fulfilledAt = null,
        serverTime = serverTime,
        pay = pay,
    )
}

/** Scripted server. Queues are consumed in order; the last default repeats. */
class FakePaymentApi : PaymentApi {
    var plansResult: PaymentResult<List<MembershipPlanOffer>> = PaymentResult.Success(PaymentFixtures.plans)
    var plansCalls = 0

    data class CreateCall(val token: String, val planCode: String, val clientRequestId: String)
    val createCalls = mutableListOf<CreateCall>()
    val createResults = ArrayDeque<PaymentResult<PaymentOrder>>()
    var createDefault: PaymentResult<PaymentOrder> = PaymentResult.Success(PaymentFixtures.order())
    var createGate: CompletableDeferred<Unit>? = null

    val queryCalls = mutableListOf<String>()
    val queryResults = ArrayDeque<PaymentResult<PaymentOrder>>()
    var queryDefault: PaymentResult<PaymentOrder> = PaymentResult.Success(PaymentFixtures.order())
    var queryGate: CompletableDeferred<Unit>? = null

    override suspend fun plans(): PaymentResult<List<MembershipPlanOffer>> {
        plansCalls++
        return plansResult
    }

    override suspend fun createOrder(accessToken: String, planCode: String, clientRequestId: String): PaymentResult<PaymentOrder> {
        createCalls += CreateCall(accessToken, planCode, clientRequestId)
        createGate?.await()
        return createResults.removeFirstOrNull() ?: createDefault
    }

    override suspend fun queryOrder(accessToken: String, orderNo: String): PaymentResult<PaymentOrder> {
        queryCalls += orderNo
        queryGate?.await()
        return queryResults.removeFirstOrNull() ?: queryDefault
    }
}

/** Mirrors the app's PaymentSessionPort over a controllable session + generation. */
class FakeSession(initial: AccountSession) : PaymentSessionPort {
    val state = MutableStateFlow(initial)
    var generation = 0L
    val unauthorizedCalls = mutableListOf<Long>()

    override fun currentOwner(requireAuthenticated: Boolean): PurchaseOwner? {
        val session = state.value
        if (requireAuthenticated && session !is AccountSession.Authenticated) return null
        val id = session.entitlementAccount?.userId ?: return null
        return PurchaseOwner(id, generation)
    }

    override fun accessToken(): String? = state.value.entitlementAccount?.let { "tok-${it.userId}" }

    override fun onUnauthorized(userId: Long) {
        unauthorizedCalls += userId
    }
}
