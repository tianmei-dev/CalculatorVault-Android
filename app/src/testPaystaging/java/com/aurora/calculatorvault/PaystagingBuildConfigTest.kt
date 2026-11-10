package com.aurora.calculatorvault

import com.aurora.calculatorvault.feature.payment.data.PayInstruction
import com.aurora.calculatorvault.feature.payment.domain.PayLaunchDecision
import com.aurora.calculatorvault.feature.payment.domain.PayLaunchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs against the generated paystaging BuildConfig (`testPaystagingUnitTest`). */
class PaystagingBuildConfigTest {

    private val policy = PayLaunchPolicy(
        BuildConfig.PAYMENT_TEST_KIND_ALLOWED,
        PayLaunchPolicy.parseHosts(BuildConfig.PAYMENT_CASHIER_HOSTS),
    )

    @Test
    fun flags_disableTestPathsAndDebugging() {
        assertEquals("paystaging", BuildConfig.BUILD_TYPE)
        assertFalse(BuildConfig.DEBUG)
        assertFalse(BuildConfig.PAYMENT_TEST_KIND_ALLOWED)
        assertFalse(BuildConfig.TEST_SESSION_INSTALL_ENABLED)
        assertFalse(BuildConfig.TRIAL_DEVICE_UPLOAD_ENABLED)
        assertEquals("", BuildConfig.LOCAL_HTTP_BASE_URL)
        assertEquals("", BuildConfig.TRIAL_HTTP_BASE_URL)
    }

    @Test
    fun apiAddress_isHttpsOrUnset_neverADevelopmentAddress() {
        for (url in listOf(BuildConfig.API_BASE_URL, BuildConfig.TRIAL_API_BASE_URL)) {
            assertTrue(url, url.isEmpty() || url.startsWith("https://"))
            assertFalse(url, url.contains("10.0.2.2") || url.contains("192.168.") || url.contains(":808"))
        }
    }

    @Test
    fun testKindAndHttpLinks_rejected() {
        assertEquals(
            PayLaunchDecision.Rejected(PayLaunchDecision.Reason.TestKindNotAllowed),
            policy.decide(PayInstruction("TEST", null)),
        )
        assertTrue(policy.decide(PayInstruction("URL", "http://cashier.example/pay")) is PayLaunchDecision.Rejected)
        assertTrue(policy.decide(PayInstruction("URL", "https://evil.example/pay")) is PayLaunchDecision.Rejected)
    }
}
