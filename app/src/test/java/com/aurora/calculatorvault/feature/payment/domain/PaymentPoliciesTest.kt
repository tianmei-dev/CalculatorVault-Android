package com.aurora.calculatorvault.feature.payment.domain

import com.aurora.calculatorvault.feature.payment.data.PayInstruction
import com.aurora.calculatorvault.feature.payment.data.PaymentOrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentPoliciesTest {
    private val release = PayLaunchPolicy(allowTestKind = false)
    private val debug = PayLaunchPolicy(allowTestKind = true)
    private val https = PayInstruction("URL", "https://cashier.example/pay?sig=abc")

    @Test
    fun httpsUrl_opensCashier_inBothBuilds() {
        assertEquals(PayLaunchDecision.OpenCashier(https.value!!), release.decide(https))
        assertEquals(PayLaunchDecision.OpenCashier(https.value!!), debug.decide(https))
        assertFalse(release.decide(https).toString().contains("sig=abc"))
    }

    @Test
    fun release_rejectsTest_http_unknown_missing_andInvalid() {
        fun rejected(reason: PayLaunchDecision.Reason) = PayLaunchDecision.Rejected(reason)
        assertEquals(rejected(PayLaunchDecision.Reason.TestKindNotAllowed), release.decide(PayInstruction("TEST", null)))
        assertEquals(rejected(PayLaunchDecision.Reason.NonHttpsUrl), release.decide(PayInstruction("URL", "http://cashier.example/pay")))
        assertEquals(rejected(PayLaunchDecision.Reason.UnknownKind), release.decide(PayInstruction("WECHAT_SDK", "x")))
        assertEquals(rejected(PayLaunchDecision.Reason.MissingPayInfo), release.decide(null))
        for (bad in listOf(null, "", "   ", "cashier.example/pay", "https://", "https://user@cashier.example/x", "javascript:alert(1)")) {
            val decision = release.decide(PayInstruction("URL", bad))
            assertEquals("value=$bad", true, decision is PayLaunchDecision.Rejected)
        }
    }

    @Test
    fun debug_recognisesTestKind_butStillRejectsHttpAndUnknown() {
        assertEquals(PayLaunchDecision.TestPayment, debug.decide(PayInstruction("TEST", null)))
        assertEquals(
            PayLaunchDecision.Rejected(PayLaunchDecision.Reason.NonHttpsUrl),
            debug.decide(PayInstruction("URL", "http://10.0.2.2:8081/pay")),
        )
        assertEquals(PayLaunchDecision.Rejected(PayLaunchDecision.Reason.UnknownKind), debug.decide(PayInstruction("test", null)))
    }

    @Test
    fun poll_fastForSixtySeconds_thenSlow_andStopsOnTerminal() {
        assertEquals(2_000L, PaymentPollPolicy.nextDelayMillis(PaymentOrderStatus.PENDING_PAYMENT, 0, 0))
        assertEquals(2_000L, PaymentPollPolicy.nextDelayMillis(PaymentOrderStatus.PAID, 59_999, 0))
        assertEquals(10_000L, PaymentPollPolicy.nextDelayMillis(PaymentOrderStatus.PENDING_PAYMENT, 60_000, 0))
        assertEquals(2_000L, PaymentPollPolicy.nextDelayMillis(PaymentOrderStatus.UNKNOWN, 0, 0))
        assertNull(PaymentPollPolicy.nextDelayMillis(PaymentOrderStatus.FULFILLED, 0, 0))
        assertNull(PaymentPollPolicy.nextDelayMillis(PaymentOrderStatus.EXPIRED, 0, 0))
    }

    @Test
    fun poll_failuresBackOff_andCapAtThirtySeconds() {
        assertEquals(
            listOf(2_000L, 5_000L, 10_000L, 30_000L, 30_000L),
            (1..5).map { PaymentPollPolicy.nextDelayMillis(PaymentOrderStatus.PENDING_PAYMENT, 0, it) },
        )
        assertNull(PaymentPollPolicy.nextDelayMillis(PaymentOrderStatus.EXPIRED, 0, 3))
    }
}
