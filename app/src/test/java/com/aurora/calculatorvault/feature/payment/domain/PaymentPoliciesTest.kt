package com.aurora.calculatorvault.feature.payment.domain

import com.aurora.calculatorvault.feature.payment.data.PayInstruction
import com.aurora.calculatorvault.feature.payment.data.PaymentOrderStatus
import com.aurora.calculatorvault.feature.payment.domain.PayLaunchDecision.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hosts are reserved `.example` names (RFC 2606); no test ever opens a real cashier. */
class PaymentPoliciesTest {
    private val hosts = listOf("cashier.example", "pay2.cashier.example")
    private val release = PayLaunchPolicy(allowTestKind = false, cashierHosts = hosts)
    private val debug = PayLaunchPolicy(allowTestKind = true, cashierHosts = hosts)
    private val https = PayInstruction("URL", "https://cashier.example/pay?sig=abc")

    private fun url(policy: PayLaunchPolicy, value: String?) = policy.decide(PayInstruction("URL", value))

    @Test
    fun httpsUrl_onAllowedHost_opensCashier_inBothBuilds() {
        assertEquals(PayLaunchDecision.OpenCashier(https.value!!), release.decide(https))
        assertEquals(PayLaunchDecision.OpenCashier(https.value!!), debug.decide(https))
        assertFalse(release.decide(https).toString().contains("sig=abc"))
        for (ok in listOf(
            "https://cashier.example",
            "https://cashier.example/",
            "https://cashier.example?order=1&sign=a%2Bb",
            "https://cashier.example#frag",
            "https://pay2.cashier.example/gateway/pay.htm?token=x~y_z-1.2",
            "HTTPS://cashier.example/pay",
        )) {
            assertEquals(ok, PayLaunchDecision.OpenCashier(ok), url(release, ok))
        }
    }

    @Test
    fun urlKindParsing_trimsSurroundingWhitespaceOnly() {
        assertEquals(PayLaunchDecision.OpenCashier("https://cashier.example/p"), url(release, "  https://cashier.example/p \n"))
        assertEquals(PayLaunchDecision.Rejected(Reason.InvalidUrl), url(release, "https://cashier.example/p q"))
    }

    @Test
    fun nonAllowlistedHosts_rejected() {
        for (bad in listOf(
            "https://evil.example/pay",
            "https://sub.cashier.example/pay", // subdomain of an allowed host
            "https://cashier.example.evil.example/pay", // allowed host as a prefix
            "https://xcashier.example/pay",
            "https://example/pay",
        )) {
            assertEquals(bad, PayLaunchDecision.Rejected(Reason.HostNotAllowed), url(release, bad))
        }
    }

    @Test
    fun caseTrailingDotPortAndUserinfo_cannotBypass() {
        for (bad in listOf(
            "https://CASHIER.example/pay",
            "https://Cashier.Example/pay",
            "https://cashier.example./pay",
            "https://cashier.example:443/pay",
            "https://cashier.example:8443/pay",
            "https://user@cashier.example/pay",
            "https://cashier.example@evil.example/pay",
            "https://cashier.example:pw@evil.example/pay",
            "https://evil.example#@cashier.example",
            "https://evil.example?@cashier.example",
        )) {
            val decision = url(release, bad)
            assertTrue("$bad -> $decision", decision is PayLaunchDecision.Rejected)
        }
    }

    @Test
    fun maliciousOrMalformedUrls_rejected() {
        for (bad in listOf(
            null, "", "   ",
            "cashier.example/pay",
            "//cashier.example/pay",
            "https://",
            "https:///pay",
            "javascript:alert(1)",
            "intent://cashier.example/#Intent;scheme=https;end",
            "file:///sdcard/pay.html",
            "data:text/html,<script>",
            "https://cashier.example\\@evil.example/",
            "https://cashier.example/\u0000",
            "https://cashier.example/pay\r\nX: y",
            "https://cashier.example/pay\"onload",
            "https://cashíer.example/pay",
            "https://xn--cashier-.example/pay",
            "https://[::1]/pay",
            "https://127.0.0.1/pay",
            "https://cashier.example/" + "a".repeat(5_000),
        )) {
            val decision = url(release, bad)
            assertTrue("$bad -> $decision", decision is PayLaunchDecision.Rejected)
        }
    }

    @Test
    fun httpRejected_evenOnAllowedHost_inEveryBuild() {
        for (policy in listOf(release, debug)) {
            assertEquals(PayLaunchDecision.Rejected(Reason.NonHttpsUrl), url(policy, "http://cashier.example/pay"))
            assertEquals(PayLaunchDecision.Rejected(Reason.NonHttpsUrl), url(policy, "HTTP://cashier.example/pay"))
        }
    }

    @Test
    fun emptyAllowList_refusesEveryLink() {
        val unconfigured = PayLaunchPolicy(allowTestKind = false, cashierHosts = emptyList())
        assertEquals(PayLaunchDecision.Rejected(Reason.CashierHostsNotConfigured), unconfigured.decide(https))
        // Invalid configured entries are dropped rather than widening the list.
        val invalid = PayLaunchPolicy(
            allowTestKind = false,
            cashierHosts = listOf("", " ", "HTTPS://cashier.example", "Cashier.example", "cashier.example.", "*.example", "1.2.3.4", "cashier.example:443"),
        )
        assertEquals(PayLaunchDecision.Rejected(Reason.CashierHostsNotConfigured), invalid.decide(https))
        assertEquals(listOf("a.example", "b.example"), PayLaunchPolicy.parseHosts(" a.example, ,b.example ,"))
        assertEquals(emptyList<String>(), PayLaunchPolicy.parseHosts(""))
    }

    @Test
    fun release_rejectsTest_unknown_missing() {
        assertEquals(PayLaunchDecision.Rejected(Reason.TestKindNotAllowed), release.decide(PayInstruction("TEST", null)))
        assertEquals(PayLaunchDecision.Rejected(Reason.UnknownKind), release.decide(PayInstruction("WECHAT_SDK", "x")))
        assertEquals(PayLaunchDecision.Rejected(Reason.UnknownKind), release.decide(PayInstruction("url", https.value)))
        assertEquals(PayLaunchDecision.Rejected(Reason.MissingPayInfo), release.decide(null))
    }

    @Test
    fun debug_recognisesTestKind_butStillRejectsHttpAndUnknown() {
        assertEquals(PayLaunchDecision.TestPayment, debug.decide(PayInstruction("TEST", null)))
        assertEquals(PayLaunchDecision.Rejected(Reason.NonHttpsUrl), url(debug, "http://10.0.2.2:8081/pay"))
        assertEquals(PayLaunchDecision.Rejected(Reason.UnknownKind), debug.decide(PayInstruction("test", null)))
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
