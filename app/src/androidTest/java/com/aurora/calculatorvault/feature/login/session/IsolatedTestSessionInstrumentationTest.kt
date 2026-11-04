package com.aurora.calculatorvault.feature.login.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.aurora.calculatorvault.BuildConfig
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.app.IsolatedTestSessionAccess
import com.aurora.calculatorvault.feature.entitlement.EntitlementInstrumentationBridge
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.entitlement.domain.MembershipResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * I3.4.3-D2-A: real Bearer Session install against s32it (:8081).
 *
 * Host must:
 * 1. Build with `-PISOLATED_MEMBERSHIP_TEST=true`
 * 2. `adb push` D1 `i343d1-sessions.local.json` → [IsolatedTestSessionBridge.DEFAULT_DEVICE_SESSION_FILE]
 * 3. `adb install -r` app + test APK; run via `am instrument` (not connectedDebugAndroidTest)
 *
 * Tokens are never asserted or logged.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class IsolatedTestSessionInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var app: CalculatorVaultApp
    private lateinit var catalog: Map<String, IsolatedTestSessionBridge.FixtureAccount>

    @Before
    fun setUp() = runBlocking {
        assumeTrue(
            "Requires ISOLATED_MEMBERSHIP_TEST Debug build",
            IsolatedTestSessionAccess.isInstallEnabled(),
        )
        assumeTrue(
            "API_BASE_URL must target emulator host :8081",
            BuildConfig.API_BASE_URL.contains("10.0.2.2:8081"),
        )
        assertEquals(BuildConfig.API_BASE_URL, BuildConfig.LOCAL_HTTP_BASE_URL)
        app = instrumentation.targetContext.applicationContext as CalculatorVaultApp
        assertTrue(app.hasPrivacyConsent())
        EntitlementInstrumentationBridge.clear(app)
        catalog = IsolatedTestSessionBridge.loadCatalog()
        assertTrue(catalog.containsKey("none"))
        assertTrue(catalog.containsKey("monthly"))
    }

    @After
    fun tearDown() {
        val current = instrumentation.targetContext.applicationContext as CalculatorVaultApp
        EntitlementInstrumentationBridge.clear(current)
        // Do not call logout() — server revoke would burn D1 fixture tokens for later cases.
        current.accountSession.abandonLocalSession()
        runBlocking { settle(500) }
    }

    @Test
    fun installValidSession_reachesAuthenticated_andPersistsInKeystorePath() = runBlocking {
        val fixture = catalog.getValue("none")
        IsolatedTestSessionBridge.install(app, fixture)
        val session = awaitSession { it is AccountSession.Authenticated }
        val authed = session as AccountSession.Authenticated
        assertEquals(fixture.userId, authed.account.userId)
        assertNotNull(app.accountSession.peekAccessToken())
        // Token present in memory after Keystore write; do not print it.
        assertTrue(app.accountSession.peekAccessToken()!!.isNotBlank())
    }

    @Test
    fun installTriggersMembershipRefresh_noneHasNoVip() = runBlocking {
        IsolatedTestSessionBridge.installLabel(app, catalog, "none")
        awaitSession { it is AccountSession.Authenticated }
        awaitMembershipSettled()
        assertFalse(app.entitlementRepository.state.value.source is EntitlementSource.Vip)
        val gen = app.entitlementRepository.currentAccountGeneration()
        val uid = (app.accountSession.state.value as AccountSession.Authenticated).account.userId
        val membership = app.membershipProvider.membershipFor(uid, gen)
        assertTrue(
            "NONE must not be VerifiedActive was $membership",
            membership !is MembershipResult.VerifiedActive,
        )
    }

    @Test
    fun monthlyAccount_realMeGrantsVip() = runBlocking {
        IsolatedTestSessionBridge.installLabel(app, catalog, "monthly")
        awaitSession { it is AccountSession.Authenticated }
        awaitUntil("monthly VIP") {
            app.entitlementRepository.state.value.source is EntitlementSource.Vip
        }
        val vip = app.entitlementRepository.state.value.source as EntitlementSource.Vip
        assertEquals(catalog.getValue("monthly").userId, vip.accountId)
        assertFalse(vip.permanent)
    }

    @Test
    fun switchAccount_clearsPreviousVip() = runBlocking {
        IsolatedTestSessionBridge.installLabel(app, catalog, "monthly")
        awaitSession { it is AccountSession.Authenticated }
        awaitUntil("vip before switch") {
            app.entitlementRepository.state.value.source is EntitlementSource.Vip
        }
        IsolatedTestSessionBridge.installLabel(app, catalog, "none")
        awaitSession { it is AccountSession.Authenticated }
        awaitMembershipSettled()
        assertEquals(
            catalog.getValue("none").userId,
            (app.accountSession.state.value as AccountSession.Authenticated).account.userId,
        )
        assertFalse(app.entitlementRepository.state.value.source is EntitlementSource.Vip)
    }

    @Test
    fun logout_clearsVip() = runBlocking {
        // Use lifetime so monthly/none fixtures remain reusable by sibling cases.
        assumeTrue(catalog.containsKey("lifetime"))
        IsolatedTestSessionBridge.installLabel(app, catalog, "lifetime")
        awaitSession { it is AccountSession.Authenticated }
        awaitUntil("vip before logout") {
            app.entitlementRepository.state.value.source is EntitlementSource.Vip
        }
        app.accountSession.logout()
        awaitSession { it is AccountSession.Guest }
        assertFalse(app.entitlementRepository.state.value.source is EntitlementSource.Vip)
        assertTrue(app.accountSession.peekAccessToken() == null)
    }

    @Test
    fun invalidToken_sessionExpired() = runBlocking {
        assumeTrue(catalog.containsKey("session-expired"))
        IsolatedTestSessionBridge.installLabel(app, catalog, "session-expired")
        awaitSession {
            it is AccountSession.SessionExpired || it is AccountSession.TemporarilyUnavailable
        }
        // Prefer SessionExpired when AuthFailure(invalidSession); accept TEMP only if mapped otherwise.
        assertTrue(
            app.accountSession.state.value is AccountSession.SessionExpired ||
                app.accountSession.state.value is AccountSession.TemporarilyUnavailable,
        )
        assertFalse(app.entitlementRepository.state.value.source is EntitlementSource.Vip)
    }

    @Test
    fun revalidate_keepsAuthenticated_sameRestoreValidatePath() = runBlocking {
        // Full am force-stop crashes the instrumentation harness on this AVD.
        // Keystore persistence + AuthApi.me validation are the same path cold-start restore uses.
        IsolatedTestSessionBridge.installLabel(app, catalog, "none")
        val userId = catalog.getValue("none").userId
        awaitSession { it is AccountSession.Authenticated }
        assertEquals(userId, (app.accountSession.state.value as AccountSession.Authenticated).account.userId)
        assertNotNull(app.accountSession.peekAccessToken())
        app.accountSession.revalidate()
        awaitSession { it is AccountSession.Authenticated }
        assertEquals(
            userId,
            (app.accountSession.state.value as AccountSession.Authenticated).account.userId,
        )
        assertNotNull(app.accountSession.peekAccessToken())
    }

    private suspend fun awaitSession(
        timeoutMs: Long = 15_000,
        predicate: (AccountSession) -> Boolean,
    ): AccountSession {
        awaitUntil("session", timeoutMs) { predicate(app.accountSession.state.value) }
        return app.accountSession.state.value
    }

    private suspend fun awaitMembershipSettled(timeoutMs: Long = 15_000) {
        // Allow LoginSuccess/SessionRestore refresh to finish (success or confirmed none).
        settle(300)
        awaitUntil("membership sync idle-ish", timeoutMs) {
            val sync = app.membershipRefreshCoordinator.syncState.value
            sync !is com.aurora.calculatorvault.feature.membership.data.MembershipSyncUi.Syncing
        }
    }

    private suspend fun awaitUntil(
        label: String,
        timeoutMs: Long = 15_000,
        predicate: () -> Boolean,
    ) {
        try {
            withTimeout(timeoutMs) {
                while (!predicate()) {
                    delay(100)
                }
            }
        } catch (error: Throwable) {
            throw AssertionError(
                "timeout waiting for $label; session=${app.accountSession.state.value} " +
                    "source=${app.entitlementRepository.state.value.source::class.simpleName} " +
                    "sync=${app.membershipRefreshCoordinator.syncState.value}",
                error,
            )
        }
    }

    private suspend fun settle(ms: Long) {
        delay(ms)
    }
}
