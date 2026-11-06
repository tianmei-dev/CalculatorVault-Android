package com.aurora.calculatorvault.feature.membership

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.aurora.calculatorvault.BuildConfig
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.app.IsolatedTestSessionAccess
import com.aurora.calculatorvault.feature.applock.domain.AppLockSetResult
import com.aurora.calculatorvault.feature.applock.service.AppLockMonitorService
import com.aurora.calculatorvault.feature.entitlement.EntitlementInstrumentationBridge
import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementDisplayMapper
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.entitlement.domain.MembershipResult
import com.aurora.calculatorvault.feature.entitlement.domain.grantsTrialCapability
import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.IsolatedTestSessionBridge
import com.aurora.calculatorvault.feature.membership.data.MembershipQueryResult
import com.aurora.calculatorvault.feature.membership.data.MembershipQueryScope
import com.aurora.calculatorvault.feature.membership.data.MembershipSyncUi
import com.aurora.calculatorvault.feature.membership.domain.VipPlan
import com.aurora.calculatorvault.feature.onboarding.data.OnboardingResult
import com.aurora.calculatorvault.feature.onboarding.domain.StartupDestination
import com.aurora.calculatorvault.feature.privatemedia.SyntheticMediaFactory
import com.aurora.calculatorvault.feature.privatemedia.storage.VaultMediaStorage
import com.aurora.calculatorvault.feature.settings.presentation.MembershipStatus
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * I3.4.3-D2-B real membership end-to-end on CalculatorVault_Test.
 *
 * Membership source is ONLY the s32it server via real Bearer + GET /api/v1/membership/me.
 * [EntitlementInstrumentationBridge] is cleared and asserted unarmed in every test — no Fake VIP.
 *
 * Results that depend on "no other entitlement" are reported through [EVIDENCE_TAG] with
 * `trialGrants=` so the report can distinguish VIP loss from device-trial takeover.
 *
 * Tokens never appear in logs or assertion messages.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RealMembershipE2EInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app: CalculatorVaultApp = context.applicationContext as CalculatorVaultApp
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
    private lateinit var catalog: Map<String, IsolatedTestSessionBridge.FixtureAccount>

    private val createdMediaIds = CopyOnWriteArrayList<Long>()
    private val syntheticUris = CopyOnWriteArrayList<Uri>()
    private val restoredNames = CopyOnWriteArrayList<String>()
    private var lockBaseline: Set<String> = emptySet()

    @Before
    fun setUp(): Unit = runBlocking {
        assertEmulatorIdentity()
        assumeTrue("needs ISOLATED_MEMBERSHIP_TEST build", IsolatedTestSessionAccess.isInstallEnabled())
        assertEquals("http://10.0.2.2:8081", BuildConfig.API_BASE_URL)
        grantRuntimePrerequisites()
        ensureInitialized()
        EntitlementInstrumentationBridge.clear(app)
        assertNoFake()
        catalog = IsolatedTestSessionBridge.loadCatalog()
        lockBaseline = app.appLockRepository.observeLockedPackages().first()
        evidence("setup", "trial=${trialSummary()} lockedBaseline=${lockBaseline.size}")
    }

    @After
    fun tearDown(): Unit = runBlocking {
        setAirplane(false)
        assertNoFake()
        if (createdMediaIds.isNotEmpty()) {
            runCatching { app.vaultMediaRepository.deletePrivateMedia(createdMediaIds.toList()) }
        }
        syntheticUris.forEach { SyntheticMediaFactory.deleteUri(context, it) }
        restoredNames.forEach { deleteSystemImageByName(it) }
        val lockedNow = app.appLockRepository.observeLockedPackages().first()
        (lockedNow - lockBaseline).forEach { pkg ->
            app.appLockRepository.setLocked(pkg, pkg.substringAfterLast('.'), false)
        }
        AppLockMonitorService.stop(context)
        app.accountSession.abandonLocalSession()
        SystemClock.sleep(300)
        device.pressHome()
    }

    // ---------- 1. Membership states / UI mapping ----------

    @Test
    fun t01_allMembershipStates_realMe(): Unit = runBlocking {
        val expectations = listOf(
            "none" to false,
            "monthly" to true,
            "yearly" to true,
            "lifetime" to true,
            "stack" to true,
            "expired" to false,
            "revoked" to false,
        )
        for ((label, expectVip) in expectations) {
            val fixture = catalog.getValue(label)
            IsolatedTestSessionBridge.install(app, fixture)
            // StateFlow is updated by an async collector; the authorization path recomputes live.
            val flowLag = (app.entitlementRepository.state.value.source as? EntitlementSource.Vip)
                ?.let { it.accountId != fixture.userId } == true
            val importDecisionAtSwitch =
                app.entitlementRepository.actionPolicy.evaluate(EntitlementCapability.APP_MANAGEMENT)
            assertTrue(importDecisionAtSwitch is ActionDecision.Allowed)
            val liveSource = app.entitlementRepository.state.value.source
            assertFalse(
                "label=$label previous VIP must be gone on live recompute",
                liveSource is EntitlementSource.Vip && liveSource.accountId != fixture.userId,
            )
            evidence("switch_$label", "flowLagBeforeRecompute=$flowLag liveSource=${liveSource::class.simpleName}")
            awaitAuthenticated(fixture.userId)
            if (expectVip) {
                awaitUntil("label=$label VIP") {
                    app.entitlementRepository.state.value.source is EntitlementSource.Vip
                }
            } else {
                awaitSyncSettled()
                SystemClock.sleep(800)
                app.entitlementRepository.refresh()
            }
            val state = app.entitlementRepository.state.value
            val vip = state.source as? EntitlementSource.Vip
            assertEquals("label=$label vip", expectVip, vip != null)
            if (vip != null) assertEquals("label=$label isolation", fixture.userId, vip.accountId)
            val ui = EntitlementDisplayMapper.toAccountUiState(
                app.accountSession.state.value,
                state,
                sync = app.membershipRefreshCoordinator.syncState.value,
            )
            when (label) {
                "monthly" -> {
                    val m = ui.accountMembership as MembershipStatus.VipActive
                    assertEquals(VipPlan.Monthly, m.planType)
                }
                "yearly" -> {
                    val m = ui.accountMembership as MembershipStatus.VipActive
                    assertEquals(VipPlan.Annual, m.planType)
                }
                "lifetime" -> {
                    assertEquals(MembershipStatus.VipPermanent, ui.accountMembership)
                    assertNull(vip!!.expiresAtEpochMillis)
                }
                "stack" -> {
                    val m = ui.accountMembership as MembershipStatus.VipActive
                    assertNull("stack must not be labeled monthly/yearly", m.planType)
                }
                else -> assertEquals(MembershipStatus.NotSubscribed, ui.accountMembership)
            }
            val gen = app.entitlementRepository.currentAccountGeneration()
            val provider = app.membershipProvider.membershipFor(fixture.userId, gen)
            evidence(
                "state_$label",
                "userId=${fixture.userId} gen=$gen vip=${vip != null} " +
                    "plan=${vip?.planType} contributing=${vip?.contributingPlanTypes} " +
                    "expiresAt=${vip?.expiresAtEpochMillis?.let { Instant.ofEpochMilli(it) }} " +
                    "permanent=${vip?.permanent} provider=${provider::class.simpleName} " +
                    "ui=${ui.accountMembership::class.simpleName}:${ui.accountMembership.planType} " +
                    "visible=${ui.visibleMembership::class.simpleName} source=${state.source::class.simpleName} " +
                    "trial=${trialSummary()}",
            )
        }
    }

    // ---------- 2. Rendered settings UI ----------

    @Test
    fun t02_settingsUi_rendersRealMembership_andManualRefresh(): Unit = runBlocking {
        val cases = listOf(
            "monthly" to "月卡会员",
            "yearly" to "年卡会员",
            "lifetime" to "永久会员",
            "stack" to "VIP会员",
        )
        var first = true
        for ((label, title) in cases) {
            installAndAwaitVip(label)
            val opened = openSettings(first)
            first = false
            assumeTrue("settings UI not reachable via calculator PIN on this device", opened)
            val found = device.wait(Until.hasObject(By.text(title)), 8_000)
            val expiry = (app.entitlementRepository.state.value.source as EntitlementSource.Vip)
                .expiresAtEpochMillis
            val expiryShown = if (expiry == null) {
                device.hasObject(By.textContains("永久有效"))
            } else {
                device.hasObject(By.textContains("有效期至"))
            }
            val loggedInShown = device.hasObject(By.text("已登录"))
            evidence("ui_$label", "title=$title found=$found expiryLine=$expiryShown loggedIn=$loggedInShown")
            assertTrue("UI title $title for $label", found)
            assertTrue("UI expiry line for $label", expiryShown)
            assertTrue("login state shown separately", loggedInShown)
        }
        // Manual refresh button on the real settings screen (lifetime/stack account still installed).
        val rev0 = app.serverMembershipProvider.membershipStateRevision.value
        val button = device.findObject(By.text("刷新会员状态"))
        assertTrue("refresh button visible while logged in", button != null)
        button.click()
        awaitUntil("manual refresh bumps revision") {
            app.serverMembershipProvider.membershipStateRevision.value > rev0
        }
        awaitSyncSettled()
        evidence("ui_manual_refresh", "rev0=$rev0 rev1=${app.serverMembershipProvider.membershipStateRevision.value} sync=${app.membershipRefreshCoordinator.syncState.value}")

        // Non-VIP account: no VIP title on screen.
        IsolatedTestSessionBridge.installLabel(app, catalog, "revoked")
        awaitAuthenticated(catalog.getValue("revoked").userId)
        awaitSyncSettled()
        SystemClock.sleep(1_000)
        val noVipTitle = !device.hasObject(By.text("月卡会员")) &&
            !device.hasObject(By.text("年卡会员")) &&
            !device.hasObject(By.text("永久会员")) &&
            !device.hasObject(By.text("VIP会员"))
        evidence("ui_revoked", "noVipTitle=$noVipTitle trial=${trialSummary()}")
        assertTrue(noVipTitle)
    }

    // ---------- 3. Refresh: foreground + throttle + manual ----------

    @Test
    fun t03_foregroundRefresh_throttle_manualBypass(): Unit = runBlocking {
        // Lock a package so the monitor service runs under VIP; the sampler then shows whether the
        // foreground revalidation gap stops protection.
        val lockedPackage = pickLockablePackage()
        awaitLockedInRoom(lockedPackage)
        // From Guest + foreground: install is a SessionRestore-class refresh (not throttled, and it
        // does not consume this account key's foreground window).
        app.accountSession.abandonLocalSession()
        bringVaultToForeground()
        awaitForeground()
        installAndAwaitVip("monthly")
        awaitServiceRunning(25_000)
        awaitSyncSettled()
        val rev0 = rev()

        val fg1 = backgroundThenForeground()
        awaitSyncSettled()
        SystemClock.sleep(1_000)
        val rev1 = rev()

        val fg2 = backgroundThenForeground()
        awaitSyncSettled()
        SystemClock.sleep(1_500)
        val rev2 = rev()

        assertTrue(app.membershipRefreshCoordinator.refreshManual())
        awaitUntil("manual bump") { rev() > rev2 }
        awaitSyncSettled()
        val rev3 = rev()

        evidence(
            "foreground",
            "rev0=$rev0 afterFg1=$rev1 afterFg2(within5m)=$rev2 afterManual=$rev3 " +
                "fg1[$fg1] fg2[$fg2] service=${AppLockMonitorService.isRunning} trial=${trialSummary()}",
        )
        assertTrue("first foreground refreshes", rev1 > rev0)
        assertEquals("second foreground within 5m throttled", rev1, rev2)
        assertTrue("manual bypasses throttle", rev3 > rev2)
        assertTrue(app.entitlementRepository.state.value.source is EntitlementSource.Vip)
        for ((name, fg) in listOf("fg1" to fg1, "fg2" to fg2)) {
            assertTrue("$name must pass through re-validation", fg.validating)
            assertEquals("$name VIP gap during revalidation", 0, fg.vipGapSamples)
            assertEquals("$name import denied during revalidation", 0, fg.liveImportDenied)
            assertFalse("$name lock service stopped during revalidation", fg.serviceStopped)
        }
        assertTrue(AppLockMonitorService.isRunning)
    }

    // ---------- 4. 401 current / late 401 old ----------

    @Test
    fun t04_current401_sessionExpired_viaRealMembershipEndpoint(): Unit = runBlocking {
        val monthly = catalog.getValue("monthly")
        val revoked = catalog.getValue("session-revoked")
        assertEquals(monthly.userId, revoked.userId)
        installAndAwaitVip("monthly")
        awaitSyncSettled()
        val gen = app.entitlementRepository.currentAccountGeneration()
        val result = try {
            app.serverMembershipProvider.refresh(
                MembershipQueryScope(monthly.userId!!, gen, revoked.rawTokenForTest()),
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            // Coordinator's SessionInvalid handler clearAccount() cancels the in-flight deferred.
            null
        }
        evidence(
            "current401_http",
            "result=${result?.let { it::class.simpleName } ?: "CancelledBySessionInvalidHandler"} " +
                "http=${(result as? MembershipQueryResult.SessionInvalid)?.httpStatus} " +
                "code=${(result as? MembershipQueryResult.SessionInvalid)?.businessCode}",
        )
        assertTrue(result == null || result is MembershipQueryResult.SessionInvalid)
        awaitUntil("SessionExpired") { app.accountSession.state.value == AccountSession.SessionExpired }
        assertFalse(app.entitlementRepository.state.value.source is EntitlementSource.Vip)
        evidence("current401", "session=${app.accountSession.state.value} source=${app.entitlementRepository.state.value.source::class.simpleName} trial=${trialSummary()}")

        // Auth-layer 401 on install of an expired / revoked session.
        for (label in listOf("session-expired", "session-revoked")) {
            IsolatedTestSessionBridge.installLabel(app, catalog, label)
            awaitUntil("$label expired") { app.accountSession.state.value == AccountSession.SessionExpired }
            assertFalse(app.entitlementRepository.state.value.source is EntitlementSource.Vip)
            evidence("auth401_$label", "session=${app.accountSession.state.value}")
        }
    }

    @Test
    fun t05_late401_oldAccount_doesNotAffectNewAccount(): Unit = runBlocking {
        val monthly = catalog.getValue("monthly")
        val revoked = catalog.getValue("session-revoked")
        installAndAwaitVip("monthly")
        awaitSyncSettled()
        val oldGen = app.entitlementRepository.currentAccountGeneration()
        installAndAwaitVip("yearly")
        awaitSyncSettled()
        val yearly = catalog.getValue("yearly")
        val result = app.serverMembershipProvider.refresh(
            MembershipQueryScope(monthly.userId!!, oldGen, revoked.rawTokenForTest()),
        )
        SystemClock.sleep(1_000)
        val session = app.accountSession.state.value
        evidence(
            "late401",
            "oldResult=${result::class.simpleName} session=${session::class.simpleName} " +
                "user=${(session as? AccountSession.Authenticated)?.account?.userId} " +
                "vip=${app.entitlementRepository.state.value.source is EntitlementSource.Vip}",
        )
        assertTrue(result is MembershipQueryResult.SessionInvalid)
        assertEquals(yearly.userId, (session as AccountSession.Authenticated).account.userId)
        assertTrue(app.entitlementRepository.state.value.source is EntitlementSource.Vip)
    }

    // ---------- 5. Offline sticky (real airplane mode) ----------

    @Test
    fun t06_offlineSticky_realAirplane(): Unit = runBlocking {
        val lockedPackage = pickLockablePackage()
        awaitLockedInRoom(lockedPackage)
        installAndAwaitVip("yearly")
        bringVaultToForeground()
        awaitForeground()
        awaitServiceRunning(25_000)
        awaitSyncSettled()
        var offlineFg: FgSample? = null
        var serviceAfterOfflineFg = false
        var importAfterOfflineFg = false
        val userId = catalog.getValue("yearly").userId!!
        var vipAfterOfflineForeground = true
        try {
            setAirplane(true)
            SystemClock.sleep(2_500)
            assertTrue(app.membershipRefreshCoordinator.refreshManual())
            awaitSyncSettled(20_000)
            val sync = app.membershipRefreshCoordinator.syncState.value
            val vipAfterFailedRefresh = app.entitlementRepository.state.value.source is EntitlementSource.Vip
            val gen = app.entitlementRepository.currentAccountGeneration()
            val provider = app.membershipProvider.membershipFor(userId, gen)
            val ui = EntitlementDisplayMapper.toAccountUiState(
                app.accountSession.state.value, app.entitlementRepository.state.value, sync = sync,
            )
            evidence(
                "offline_manual",
                "sync=$sync vip=$vipAfterFailedRefresh provider=${provider::class.simpleName} hint=${ui.membershipSyncHint}",
            )
            assertEquals(MembershipSyncUi.Anomaly, sync)
            assertTrue("sticky VIP retained after real network failure", vipAfterFailedRefresh)

            // Foreground while offline drives AccountSession.revalidate().
            val fg = backgroundThenForeground()
            offlineFg = fg
            SystemClock.sleep(3_000)
            val session = app.accountSession.state.value
            vipAfterOfflineForeground = app.entitlementRepository.state.value.source is EntitlementSource.Vip
            serviceAfterOfflineFg = AppLockMonitorService.isRunning
            importAfterOfflineFg =
                app.entitlementRepository.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT) is
                    ActionDecision.Allowed
            val providerStill = app.membershipProvider.membershipFor(userId, gen)
            val offlineUi = EntitlementDisplayMapper.toAccountUiState(
                session, app.entitlementRepository.state.value,
                sync = app.membershipRefreshCoordinator.syncState.value,
            )
            evidence(
                "offline_foreground",
                "session=$session vip=$vipAfterOfflineForeground service=$serviceAfterOfflineFg " +
                    "importAllowed=$importAfterOfflineFg providerSticky=${providerStill::class.simpleName} " +
                    "ui=${offlineUi.account::class.simpleName}/${offlineUi.visibleMembership::class.simpleName} " +
                    "fg[$fg] trial=${trialSummary()}",
            )
        } finally {
            setAirplane(false)
        }
        val revBeforeRecovery = rev()
        val recovered = waitUntil(90_000) {
            if (app.accountSession.state.value !is AccountSession.Authenticated &&
                app.accountSession.state.value !is AccountSession.Validating
            ) {
                app.accountSession.revalidate()
            }
            SystemClock.sleep(2_000)
            app.accountSession.state.value is AccountSession.Authenticated &&
                app.entitlementRepository.state.value.source is EntitlementSource.Vip &&
                rev() > revBeforeRecovery
        }
        evidence(
            "offline_recovered",
            "recovered=$recovered session=${app.accountSession.state.value::class.simpleName} " +
                "revBefore=$revBeforeRecovery revAfter=${rev()} sync=${app.membershipRefreshCoordinator.syncState.value}",
        )
        assertTrue("back online: Authenticated + VIP + fresh /me reconfirm", recovered)
        assertTrue(
            "D-1: verified sticky VIP must survive an offline foreground (24h window)",
            vipAfterOfflineForeground,
        )
        assertTrue("offline foreground keeps lock service", serviceAfterOfflineFg)
        assertTrue("offline foreground keeps import allowed", importAfterOfflineFg)
        val fg = offlineFg!!
        assertTrue("offline foreground went through TemporarilyUnavailable", fg.unavailable)
        assertEquals("no VIP gap offline", 0, fg.vipGapSamples)
        assertEquals("no import denial offline", 0, fg.liveImportDenied)
        assertFalse("no service stop offline", fg.serviceStopped)
    }

    // ---------- 6. App lock with real membership ----------

    @Test
    fun t07_appLock_realVip_overlay_thenRevokedAccount(): Unit = runBlocking {
        val lockedPackage = pickLockablePackage()
        awaitLockedInRoom(lockedPackage)
        installAndAwaitVip("monthly")
        bringVaultToForeground()
        awaitForeground()
        awaitSyncSettled()
        assertTrue(app.entitlementRepository.state.value.source is EntitlementSource.Vip)
        awaitServiceRunning(25_000)
        launchPackage(lockedPackage)
        val overlay = waitUntil(20_000) { AppLockMonitorService.isOverlayShowing() }
        evidence("lock_vip", "pkg=$lockedPackage service=${AppLockMonitorService.isRunning} overlay=$overlay")
        assertTrue("overlay under real VIP", overlay)

        val trialGrants = trialGrants()
        device.pressHome()
        IsolatedTestSessionBridge.installLabel(app, catalog, "revoked")
        awaitAuthenticated(catalog.getValue("revoked").userId)
        awaitSyncSettled()
        app.appLockProtectionCoordinator.reconcile()
        SystemClock.sleep(1_500)
        val sourceAfter = app.entitlementRepository.state.value.source
        val allowed = app.appLockProtectionCoordinator.allowsLiveProtection()
        val roomAfter = app.appLockRepository.observeLockedPackages().first()
        assertTrue("Room lock kept after VIP loss", lockedPackage in roomAfter)
        if (trialGrants) {
            assertTrue("trial takes over", sourceAfter is EntitlementSource.DeviceTrial)
            assertTrue(allowed)
            evidence("lock_revoked_trial_takeover", "source=${sourceAfter::class.simpleName} allowed=$allowed room=${lockedPackage in roomAfter}")
        } else {
            assertFalse(allowed)
            assertTrue(waitUntil(12_000) { !AppLockMonitorService.isOverlayShowing() })
            assertTrue(waitUntil(12_000) { !AppLockMonitorService.isRunning })
            launchPackage(lockedPackage)
            SystemClock.sleep(2_000)
            val noOverlay = !AppLockMonitorService.isOverlayShowing() && !hasOverlayWindow()
            // Stale async triggers must not resurrect protection without entitlement.
            bringVaultToForeground()
            awaitForeground()
            app.appLockProtectionCoordinator.onProcessForeground()
            app.appLockProtectionCoordinator.reconcile()
            SystemClock.sleep(3_000)
            val stayedStopped = !AppLockMonitorService.isRunning
            evidence(
                "lock_revoked_stop",
                "service=${AppLockMonitorService.isRunning} noOverlayOnLaunch=$noOverlay " +
                    "staleTriggerStayedStopped=$stayedStopped room=${lockedPackage in roomAfter}",
            )
            assertTrue(noOverlay)
            assertTrue(stayedStopped)
        }

        // VIP back (yearly) → protection restored from saved Room config.
        installAndAwaitVip("yearly")
        bringVaultToForeground()
        awaitForeground()
        awaitServiceRunning(25_000)
        device.pressHome()
        launchPackage(lockedPackage)
        val restored = waitUntil(20_000) { AppLockMonitorService.isOverlayShowing() }
        evidence("lock_restored", "overlay=$restored trialGrants=$trialGrants")
        assertTrue(restored)
        device.pressHome()
    }

    // ---------- 7. Private album with real membership ----------

    @Test
    fun t08_album_realVip_import_thenExpired_manageExisting(): Unit = runBlocking {
        installAndAwaitVip("monthly")
        val album = app.vaultMediaRepository.ensureDefaultAlbum()
        val jpeg = track(SyntheticMediaFactory.createJpegUri(context, 801))
        val mp4 = track(SyntheticMediaFactory.createVideoUri(context, 802))
        val summary = app.vaultMediaRepository.importMedia(album.id, listOf(jpeg, mp4))
        createdMediaIds.addAll(summary.importedMediaIds)
        assertEquals(2, summary.successCount)
        evidence("album_vip_import", "success=${summary.successCount} ids=${summary.importedMediaIds}")

        val trialGrants = trialGrants()
        IsolatedTestSessionBridge.installLabel(app, catalog, "expired")
        awaitAuthenticated(catalog.getValue("expired").userId)
        awaitSyncSettled()
        app.entitlementRepository.refresh()
        assertFalse(app.entitlementRepository.state.value.source is EntitlementSource.Vip)
        val importDecision =
            app.entitlementRepository.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT)
        val beforeIds = createdMediaIds.toSet()
        val another = track(SyntheticMediaFactory.createJpegUri(context, 803))
        val afterExpire = app.vaultMediaRepository.importMedia(album.id, listOf(another))
        createdMediaIds.addAll(afterExpire.importedMediaIds)
        if (trialGrants) {
            assertTrue(importDecision is ActionDecision.Allowed)
        } else {
            assertTrue(importDecision is ActionDecision.Denied)
            assertEquals(0, afterExpire.successCount)
            assertEquals(1, afterExpire.skippedDueToEntitlement)
        }
        evidence("album_expired_import", "decision=${importDecision::class.simpleName} success=${afterExpire.successCount} skipped=${afterExpire.skippedDueToEntitlement} trialGrants=$trialGrants")

        // Existing content: view / restore / delete without VIP.
        val items = app.vaultMediaRepository.observeAlbumMedia(album.id).first()
            .filter { it.media.id in beforeIds }
        assertEquals(2, items.size)
        items.forEach { assertTrue(it.file.exists() && it.file.length() > 0L) }
        val image = items.first { it.media.mediaType.name == "IMAGE" }
        val restore = app.vaultMediaRepository.restoreMediaToSystem(listOf(image.media.id))
        image.media.originalDisplayName?.let { restoredNames += it }
        assertTrue("restore success=${restore.successCount}", restore.successCount >= 1)
        val remaining = createdMediaIds.toList()
        val deleted = app.vaultMediaRepository.deletePrivateMedia(remaining)
        createdMediaIds.removeAll(remaining.toSet())
        evidence("album_manage_no_vip", "viewed=${items.size} restored=${restore.successCount} deleted=${deleted.successCount}")
        assertTrue(deleted.successCount >= 1)
        assertTrue("no tmp residue", tmpFiles().isEmpty())
    }

    @Test
    fun t09_album_batchMidLoss_realAccountSwitch_andSwitchKeepsFiles(): Unit = runBlocking {
        installAndAwaitVip("yearly")
        val album = app.vaultMediaRepository.ensureDefaultAlbum()
        val uris = (1..16).map { track(createLargeJpegUri(900 + it)) }
        val trialGrants = trialGrants()
        val baseline = albumIds(album.id)
        val job = async(Dispatchers.IO) { app.vaultMediaRepository.importMedia(album.id, uris) }
        // Drop VIP via real local session change as soon as the first item lands.
        val sawFirst = waitUntil(15_000, pollMs = 10) {
            runBlocking { (albumIds(album.id) - baseline).isNotEmpty() }
        }
        app.accountSession.abandonLocalSession()
        val summary = job.await()
        createdMediaIds.addAll(summary.importedMediaIds)
        evidence(
            "album_mid_batch",
            "sawFirst=$sawFirst success=${summary.successCount} skipped=${summary.skippedDueToEntitlement} " +
                "fail=${summary.failureCount} trialGrants=$trialGrants",
        )
        assertTrue(summary.successCount >= 1)
        if (!trialGrants) {
            assertTrue("later items stopped", summary.skippedDueToEntitlement >= 1)
        }
        // Completed imports are not rolled back; files survive account changes.
        IsolatedTestSessionBridge.installLabel(app, catalog, "none")
        awaitAuthenticated(catalog.getValue("none").userId)
        val kept = app.vaultMediaRepository.observeAlbumMedia(album.id).first()
            .filter { it.media.id in summary.importedMediaIds }
        assertEquals(summary.importedMediaIds.size, kept.size)
        kept.forEach { assertTrue(it.file.exists()) }
        assertTrue("no tmp residue", tmpFiles().isEmpty())
        evidence("album_switch_keeps", "kept=${kept.size} tmp=0")
    }

    // ---------- 8. Logout (burns the lifetime fixture token; keep last) ----------

    @Test
    fun t10_logout_revokesVip_trialIndependent(): Unit = runBlocking {
        installAndAwaitVip("lifetime")
        val trialBefore = app.deviceTrial.state.value
        app.accountSession.logout()
        assertEquals(AccountSession.Guest, app.accountSession.state.value)
        assertFalse(
            "VIP gone synchronously after logout",
            app.entitlementRepository.actionPolicy.let {
                it.evaluate(EntitlementCapability.APP_MANAGEMENT)
                app.entitlementRepository.state.value.source is EntitlementSource.Vip
            },
        )
        app.deviceTrial.refresh()
        val trialAfter = app.deviceTrial.state.value
        evidence("logout", "trialBefore=${trialBefore.status}/${trialBefore.remainingMillis} trialAfter=${trialAfter.status}/${trialAfter.remainingMillis}")
        assertEquals(trialBefore.status, trialAfter.status)
        if (trialBefore.remainingMillis > 0L) assertTrue(trialAfter.remainingMillis > 0L)
    }

    // ---------- helpers ----------

    private fun IsolatedTestSessionBridge.FixtureAccount.rawTokenForTest(): String =
        credential().token

    private suspend fun installAndAwaitVip(label: String) {
        val fixture = catalog.getValue(label)
        IsolatedTestSessionBridge.install(app, fixture)
        awaitAuthenticated(fixture.userId)
        awaitUntil("VIP for $label") {
            val s = app.entitlementRepository.state.value.source
            s is EntitlementSource.Vip && s.accountId == fixture.userId
        }
        assertNoFake()
    }

    private fun rev(): Long = app.serverMembershipProvider.membershipStateRevision.value

    /**
     * Background → foreground with a 5 ms sampler running across the whole transition.
     * @return summary of what the sampler saw (session states, VIP gaps, service stops).
     */
    private data class FgSample(
        val validating: Boolean,
        val unavailable: Boolean,
        val vipGapSamples: Int,
        val liveImportDenied: Int,
        val serviceStopped: Boolean,
    ) {
        override fun toString() =
            "validating=$validating unavailable=$unavailable vipGapSamples=$vipGapSamples " +
                "liveImportDenied=$liveImportDenied serviceStoppedDuringFg=$serviceStopped"
    }

    private fun backgroundThenForeground(): FgSample {
        device.pressHome()
        waitUntil(8_000) { !isProcessStarted() }
        val sawValidating = java.util.concurrent.atomic.AtomicBoolean(false)
        val sawUnavailable = java.util.concurrent.atomic.AtomicBoolean(false)
        val vipGapSamples = java.util.concurrent.atomic.AtomicInteger(0)
        val liveDeniedSamples = java.util.concurrent.atomic.AtomicInteger(0)
        val serviceStopped = java.util.concurrent.atomic.AtomicBoolean(false)
        val serviceWasRunning = AppLockMonitorService.isRunning
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val sampler = Thread {
            while (running.get()) {
                when (app.accountSession.state.value) {
                    is AccountSession.Validating -> sawValidating.set(true)
                    is AccountSession.TemporarilyUnavailable -> sawUnavailable.set(true)
                    else -> Unit
                }
                if (app.entitlementRepository.state.value.source !is EntitlementSource.Vip) {
                    vipGapSamples.incrementAndGet()
                }
                if (serviceWasRunning && !AppLockMonitorService.isRunning) serviceStopped.set(true)
                SystemClock.sleep(5)
            }
        }
        sampler.start()
        bringVaultToForeground()
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < 6_000) {
            if (app.entitlementRepository.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT)
                    is ActionDecision.Denied
            ) {
                liveDeniedSamples.incrementAndGet()
            }
            SystemClock.sleep(20)
        }
        running.set(false)
        sampler.join(1_000)
        return FgSample(
            sawValidating.get(), sawUnavailable.get(), vipGapSamples.get(),
            liveDeniedSamples.get(), serviceStopped.get(),
        )
    }

    private fun createLargeJpegUri(seed: Int): Uri {
        val bitmap = android.graphics.Bitmap.createBitmap(1600, 1600, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val random = java.util.Random(seed.toLong())
        val paint = android.graphics.Paint()
        for (i in 0 until 400) {
            paint.color = android.graphics.Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            canvas.drawCircle(random.nextFloat() * 1600, random.nextFloat() * 1600, 20f + random.nextFloat() * 80f, paint)
        }
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "${SyntheticMediaFactory.TEST_PREFIX}d2b_big_$seed.jpg")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(
                android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_PICTURES + "/CalculatorVaultTest",
            )
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("insert failed")
        resolver.openOutputStream(uri)!!.use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it)) }
        bitmap.recycle()
        return uri
    }

    private fun hasOverlayWindow(): Boolean {
        val dump = shell("dumpsys window windows")
        return dump.lineSequence().any {
            (it.contains("TYPE_APPLICATION_OVERLAY") || it.contains("Application Overlay")) &&
                it.contains(context.packageName)
        }
    }

    private fun trialGrants(): Boolean {
        app.deviceTrial.refresh()
        val t = app.deviceTrial.state.value
        return t.status.grantsTrialCapability() && t.remainingMillis > 0L
    }

    private fun trialSummary(): String {
        val t = app.deviceTrial.state.value
        return "${t.status}/${t.remainingMillis / 60_000}min grants=${t.status.grantsTrialCapability() && t.remainingMillis > 0}"
    }

    private fun assertNoFake() {
        assertFalse(EntitlementInstrumentationBridge.isArmed())
        assertNull(app.entitlementRepository.stateOverride)
        assertNull(app.entitlementRepository.actionDecisionOverride)
        assertTrue(app.membershipProvider === app.serverMembershipProvider)
    }

    private fun awaitAuthenticated(userId: Long?, timeoutMs: Long = 15_000) {
        awaitUntil("Authenticated user=$userId", timeoutMs) {
            val s = app.accountSession.state.value
            s is AccountSession.Authenticated && s.account.userId == userId
        }
    }

    private fun awaitSyncSettled(timeoutMs: Long = 15_000) {
        SystemClock.sleep(300)
        awaitUntil("sync settled", timeoutMs) {
            app.membershipRefreshCoordinator.syncState.value !is MembershipSyncUi.Syncing &&
                app.accountSession.state.value !is AccountSession.Validating
        }
    }

    private fun awaitUntil(label: String, timeoutMs: Long = 15_000, predicate: () -> Boolean) {
        if (!waitUntil(timeoutMs, predicate = predicate)) {
            throw AssertionError(
                "timeout: $label session=${app.accountSession.state.value::class.simpleName} " +
                    "source=${app.entitlementRepository.state.value.source::class.simpleName} " +
                    "sync=${app.membershipRefreshCoordinator.syncState.value}",
            )
        }
    }

    private fun waitUntil(timeoutMs: Long, pollMs: Long = 100, predicate: () -> Boolean): Boolean {
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            if (predicate()) return true
            SystemClock.sleep(pollMs)
        }
        return predicate()
    }

    private fun isProcessStarted(): Boolean = try {
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    } catch (_: Throwable) {
        false
    }

    private fun awaitForeground() {
        assertTrue("process STARTED", waitUntil(12_000) { isProcessStarted() })
    }

    private fun bringVaultToForeground() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("no launcher")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(launch)
        device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 10_000)
    }

    private fun openSettings(fromScratch: Boolean): Boolean {
        bringVaultToForeground()
        if (!fromScratch && device.hasObject(By.text("刷新会员状态"))) return true
        val ready = waitUntil(10_000) {
            device.hasObject(By.desc("数字 1")) || device.hasObject(By.text("应用锁"))
        }
        if (!ready) return uiFail("neither calculator nor vault visible")
        if (device.hasObject(By.desc("数字 1"))) {
            for (digit in TEST_PIN) {
                val key = device.wait(Until.findObject(By.desc("数字 $digit")), 3_000)
                    ?: return uiFail("key $digit missing")
                key.click()
                SystemClock.sleep(200)
            }
            val equals = device.wait(Until.findObject(By.desc("计算结果")), 3_000)
                ?: return uiFail("equals missing")
            equals.click()
            if (!waitUntil(8_000) { device.hasObject(By.text("应用锁")) }) return uiFail("vault did not open")
        }
        val tab = device.findObjects(By.text("设置")).lastOrNull() ?: return uiFail("settings tab missing")
        tab.click()
        val ok = waitUntil(8_000) {
            device.hasObject(By.text("刷新会员状态")) || device.hasObject(By.text("退出登录"))
        }
        return ok || uiFail("settings account rows missing")
    }

    private fun uiFail(reason: String): Boolean {
        val texts = device.findObjects(By.textContains("")).mapNotNull { it.text }.take(25)
        evidence("ui_nav_fail", "reason=$reason visible=$texts")
        return false
    }

    private fun launchPackage(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("No launcher for $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 10_000)
    }

    private suspend fun pickLockablePackage(): String {
        val preferred = listOf(
            "com.android.chrome",
            "com.google.android.deskclock",
            "com.android.contacts",
            "com.google.android.documentsui",
        )
        for (pkg in preferred) {
            if (runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess &&
                app.appLockRepository.setLocked(pkg, pkg.substringAfterLast('.'), true) == AppLockSetResult.Success
            ) {
                return pkg
            }
        }
        val first = app.appLockRepository.loadLockableApps().firstOrNull() ?: error("no lockable app")
        assertEquals(AppLockSetResult.Success, app.appLockRepository.setLocked(first.packageName, first.appName, true))
        return first.packageName
    }

    private suspend fun awaitLockedInRoom(pkg: String) {
        assertTrue(waitUntil(8_000) { runBlocking { pkg in app.appLockRepository.observeLockedPackages().first() } })
    }

    private suspend fun awaitServiceRunning(timeoutMs: Long) {
        app.appLockProtectionCoordinator.onProcessForeground()
        app.appLockProtectionCoordinator.reconcile()
        assertTrue(
            "service not running allowed=${app.appLockProtectionCoordinator.allowsLiveProtection()}",
            waitUntil(timeoutMs) {
                if (!AppLockMonitorService.isRunning && isProcessStarted()) {
                    runBlocking {
                        app.appLockProtectionCoordinator.onProcessForeground()
                        app.appLockProtectionCoordinator.reconcile()
                    }
                }
                AppLockMonitorService.isRunning
            },
        )
    }

    private fun track(uri: Uri): Uri {
        syntheticUris += uri
        return uri
    }

    private suspend fun albumIds(albumId: Long): Set<Long> =
        app.vaultMediaRepository.observeAlbumMedia(albumId).first().map { it.media.id }.toSet()

    private fun tmpFiles(): List<String> {
        val root = File(context.filesDir, VaultMediaStorage.DIRECTORY_NAME)
        return root.listFiles()?.filter { it.name.endsWith(".tmp") }?.map { it.name }.orEmpty()
    }

    private fun deleteSystemImageByName(name: String) {
        runCatching {
            context.contentResolver.delete(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(name),
            )
        }
    }

    private fun setAirplane(enabled: Boolean) {
        shell("cmd connectivity airplane-mode ${if (enabled) "enable" else "disable"}")
    }

    private suspend fun ensureInitialized() {
        when (val dest = app.onboardingRepository.resolveStartupDestination()) {
            is OnboardingResult.Success -> when (dest.value) {
                StartupDestination.PrivacyConsent -> {
                    assertTrue(app.onboardingRepository.acceptPrivacy() is OnboardingResult.Success)
                    assertTrue(app.onboardingRepository.configurePassword(TEST_PIN.copyOf()) is OnboardingResult.Success)
                }
                StartupDestination.CreatePassword ->
                    assertTrue(app.onboardingRepository.configurePassword(TEST_PIN.copyOf()) is OnboardingResult.Success)
                StartupDestination.Calculator -> Unit
            }
            is OnboardingResult.Failure -> error("startup failed ${dest.error}")
        }
        app.onPrivacyAccepted()
        assertTrue(app.hasPrivacyConsent())
    }

    private fun grantRuntimePrerequisites() {
        val pkg = context.packageName
        shell("appops set $pkg SYSTEM_ALERT_WINDOW allow")
        shell("appops set $pkg GET_USAGE_STATS allow")
        shell("pm grant $pkg android.permission.POST_NOTIFICATIONS")
        shell("cmd appops set $pkg POST_NOTIFICATION allow")
    }

    private fun shell(command: String): String {
        val pfd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return BufferedReader(InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(pfd))).use { r ->
            r.readText()
        }
    }

    private fun assertEmulatorIdentity() {
        assertEquals("1", shell("getprop ro.kernel.qemu").trim())
        val avd = shell("getprop ro.boot.qemu.avd_name").trim()
        if (avd.isNotEmpty()) assertEquals("CalculatorVault_Test", avd)
    }

    private fun evidence(step: String, detail: String) {
        Log.i(EVIDENCE_TAG, "$step :: $detail")
    }

    private companion object {
        const val EVIDENCE_TAG = "CV_D2B_EVIDENCE"
        val TEST_PIN = charArrayOf('1', '3', '5', '7', '9', '0')
    }
}
