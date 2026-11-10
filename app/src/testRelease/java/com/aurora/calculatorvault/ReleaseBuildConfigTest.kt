package com.aurora.calculatorvault

import com.aurora.calculatorvault.feature.payment.data.PayInstruction
import com.aurora.calculatorvault.feature.payment.domain.PayLaunchDecision
import com.aurora.calculatorvault.feature.payment.domain.PayLaunchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs against the generated Release BuildConfig (`testReleaseUnitTest`). */
class ReleaseBuildConfigTest {

    private val policy = PayLaunchPolicy(
        BuildConfig.PAYMENT_TEST_KIND_ALLOWED,
        PayLaunchPolicy.parseHosts(BuildConfig.PAYMENT_CASHIER_HOSTS),
    )

    @Test
    fun flags_disableTestPaths() {
        assertEquals("release", BuildConfig.BUILD_TYPE)
        assertFalse(BuildConfig.DEBUG)
        assertFalse(BuildConfig.PAYMENT_TEST_KIND_ALLOWED)
        assertFalse(BuildConfig.TEST_SESSION_INSTALL_ENABLED)
        assertEquals("", BuildConfig.LOCAL_HTTP_BASE_URL)
        assertTrue(BuildConfig.API_BASE_URL.isEmpty() || BuildConfig.API_BASE_URL.startsWith("https://"))
    }

    @Test
    fun testKindAndHttpLinks_rejected() {
        assertEquals(
            PayLaunchDecision.Rejected(PayLaunchDecision.Reason.TestKindNotAllowed),
            policy.decide(PayInstruction("TEST", null)),
        )
        assertTrue(policy.decide(PayInstruction("URL", "http://cashier.example/pay")) is PayLaunchDecision.Rejected)
    }
}
