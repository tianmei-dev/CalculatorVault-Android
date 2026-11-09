package com.aurora.calculatorvault.feature.payment

import android.os.SystemClock
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.feature.payment.data.MembershipPlanOffer
import com.aurora.calculatorvault.feature.payment.data.PaymentApi
import com.aurora.calculatorvault.feature.payment.data.PaymentOrder
import com.aurora.calculatorvault.feature.payment.data.PaymentRepository
import com.aurora.calculatorvault.feature.payment.data.PaymentResult
import java.util.concurrent.CopyOnWriteArrayList

/**
 * androidTest-only observer: wraps the live [PaymentRepository]'s API so order queries can be
 * counted. Delegates every call unchanged; never alters requests, results or production code.
 */
class PaymentQueryProbe private constructor(private val delegate: PaymentApi) : PaymentApi {
    /** elapsedRealtime of each GET /payment/orders/{orderNo} call. */
    val queryTimes = CopyOnWriteArrayList<Long>()

    override suspend fun plans(): PaymentResult<List<MembershipPlanOffer>> = delegate.plans()

    override suspend fun createOrder(accessToken: String, planCode: String, clientRequestId: String) =
        delegate.createOrder(accessToken, planCode, clientRequestId)

    override suspend fun queryOrder(accessToken: String, orderNo: String): PaymentResult<PaymentOrder> {
        queryTimes += SystemClock.elapsedRealtime()
        return delegate.queryOrder(accessToken, orderNo)
    }

    fun queriesBetween(fromElapsed: Long, toElapsed: Long): Int = queryTimes.count { it in fromElapsed..toElapsed }

    companion object {
        fun install(app: CalculatorVaultApp): PaymentQueryProbe {
            val field = PaymentRepository::class.java.getDeclaredField("api").apply { isAccessible = true }
            val current = field.get(app.paymentRepository) as PaymentApi
            val probe = (current as? PaymentQueryProbe) ?: PaymentQueryProbe(current)
            field.set(app.paymentRepository, probe)
            return probe
        }
    }
}
