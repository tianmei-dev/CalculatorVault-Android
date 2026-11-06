package com.aurora.calculatorvault.feature.membership

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aurora.calculatorvault.BuildConfig
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.app.IsolatedTestSessionAccess
import com.aurora.calculatorvault.feature.entitlement.EntitlementInstrumentationBridge
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.IsolatedTestSessionBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two independent `am instrument` invocations with a host-side `am force-stop` in between.
 * Phase 2 runs in a brand-new process, so Application.onCreate -> AccountSessionRepository.restore()
 * reads the Keystore session and SessionRestore triggers a fresh /me (no in-memory sticky carried over).
 */
@RunWith(AndroidJUnit4::class)
class RealMembershipColdStartTest {

    private val app: CalculatorVaultApp =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CalculatorVaultApp

    @Test
    fun phase1_installYearlyAndLeaveSession() {
        assumeTrue(IsolatedTestSessionAccess.isInstallEnabled())
        assertEquals("http://10.0.2.2:8081", BuildConfig.API_BASE_URL)
        EntitlementInstrumentationBridge.clear(app)
        val yearly = IsolatedTestSessionBridge.loadCatalog().getValue("yearly")
        IsolatedTestSessionBridge.install(app, yearly)
        assertTrue(
            waitUntil(15_000) {
                val s = app.entitlementRepository.state.value.source
                s is EntitlementSource.Vip && s.accountId == yearly.userId
            },
        )
        Log.i(
            TAG,
            "phase1 :: userId=${yearly.userId} vip=true pid=${android.os.Process.myPid()} " +
                "rev=${app.serverMembershipProvider.membershipStateRevision.value}",
        )
        // Intentionally no abandon/logout: host force-stops the process next.
    }

    @Test
    fun phase2_coldProcessRestoresSessionAndRequeriesMe() {
        assumeTrue(IsolatedTestSessionAccess.isInstallEnabled())
        assertNull(app.entitlementRepository.stateOverride)
        val yearly = IsolatedTestSessionBridge.loadCatalog().getValue("yearly")
        val restored = waitUntil(20_000) {
            val s = app.accountSession.state.value
            s is AccountSession.Authenticated && s.account.userId == yearly.userId
        }
        val vip = waitUntil(20_000) {
            val s = app.entitlementRepository.state.value.source
            s is EntitlementSource.Vip && s.accountId == yearly.userId
        }
        Log.i(
            TAG,
            "phase2 :: pid=${android.os.Process.myPid()} restored=$restored vip=$vip " +
                "rev=${app.serverMembershipProvider.membershipStateRevision.value} " +
                "session=${app.accountSession.state.value::class.simpleName}",
        )
        assertTrue("session restored from Keystore after process death", restored)
        assertTrue("fresh /me after cold start grants VIP", vip)
        app.accountSession.abandonLocalSession()
    }

    private fun waitUntil(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            if (predicate()) return true
            SystemClock.sleep(100)
        }
        return predicate()
    }

    private companion object {
        const val TAG = "CV_D2B_EVIDENCE"
    }
}
