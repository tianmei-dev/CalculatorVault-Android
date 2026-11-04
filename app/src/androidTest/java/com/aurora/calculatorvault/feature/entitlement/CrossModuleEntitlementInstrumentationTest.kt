package com.aurora.calculatorvault.feature.entitlement

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.feature.applock.domain.AppLockSetResult
import com.aurora.calculatorvault.feature.applock.service.AppLockMonitorService
import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.onboarding.data.OnboardingResult
import com.aurora.calculatorvault.feature.onboarding.domain.StartupDestination
import com.aurora.calculatorvault.feature.privatemedia.SyntheticMediaFactory
import com.aurora.calculatorvault.feature.privatemedia.presentation.PrivateMediaEffect
import com.aurora.calculatorvault.feature.privatemedia.presentation.PrivateMediaViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * I3.3.5 cross-module Fake entitlement regression on CalculatorVault_Test.
 *
 * Marks Fake environment only — not real WeChat / server VIP verification.
 * Run: adb install -r + am instrument (not connectedDebugAndroidTest).
 */
@RunWith(AndroidJUnit4::class)
class CrossModuleEntitlementInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app: CalculatorVaultApp = context.applicationContext as CalculatorVaultApp
    private val device: UiDevice = UiDevice.getInstance(instrumentation)

    private val createdMediaIds = CopyOnWriteArrayList<Long>()
    private val syntheticUris = CopyOnWriteArrayList<android.net.Uri>()
    private var lockedPackage: String? = null

    @Before
    fun setUp() = runBlocking {
        assertEmulatorIdentity()
        grantRuntimePrerequisites()
        ensureInitialized()
        EntitlementInstrumentationBridge.clear(app)
        app.appLockSessionManager.clearAll()
        AppLockMonitorService.stop(context)
        bringVaultToForeground()
        awaitProcessForeground()
        app.appLockProtectionCoordinator.reconcile()
    }

    @After
    fun tearDown() {
        runBlocking {
            EntitlementInstrumentationBridge.clear(app)
            AppLockMonitorService.stop(context)
            lockedPackage?.let { pkg ->
                runCatching { app.appLockRepository.setLocked(pkg, "CrossModule", false) }
            }
            if (createdMediaIds.isNotEmpty()) {
                app.vaultMediaRepository.deletePrivateMedia(createdMediaIds.toList())
            }
            syntheticUris.forEach { SyntheticMediaFactory.deleteUri(context, it) }
            createdMediaIds.clear()
            syntheticUris.clear()
        }
        device.pressHome()
    }

    @Test
    fun lockAndAlbum_revokeStopsLockKeepsExistingMedia() = runBlocking {
        // Fake VIP seed — not real membership server.
        installForceVip()
        val pkg = pickAndLockPackage()
        lockedPackage = pkg
        awaitLockedInRoom(pkg)

        val album = app.vaultMediaRepository.ensureDefaultAlbum()
        val uri = trackUri(SyntheticMediaFactory.createJpegUri(context, 501))
        val seeded = app.vaultMediaRepository.importMedia(album.id, listOf(uri))
        trackImported(seeded.importedMediaIds)
        assertEquals(1, seeded.successCount)
        val mediaId = seeded.importedMediaIds.first()

        bringVaultToForeground()
        awaitProcessForeground()
        app.appLockProtectionCoordinator.onProcessForeground()
        app.appLockProtectionCoordinator.reconcile()
        awaitServiceRunning(20_000)
        assertTrue(app.appLockProtectionCoordinator.allowsLiveProtection())
        assertTrue(AppLockMonitorService.isRunning)

        // D1: revoke → lock stops; existing album still readable; import blocked
        installForceNone()
        app.appLockProtectionCoordinator.reconcile()
        assertFalse(app.appLockProtectionCoordinator.allowsLiveProtection())
        assertTrue(awaitServiceStopped(12_000))
        assertTrue(pkg in app.appLockRepository.observeLockedPackages().first())

        val viewed = app.vaultMediaRepository.observeAlbumMedia(album.id).first()
            .first { it.media.id == mediaId }
        assertTrue(viewed.file.exists() && viewed.file.length() > 0L)
        assertEquals(
            mediaId,
            viewed.media.id,
        )
        assertFalse(
            app.entitlementRepository.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT)
                is ActionDecision.Allowed,
        )
        val effects = collectImportEffects { vm ->
            vm.requestImportToAlbum(album.id)
            SystemClock.sleep(250)
        }
        assertTrue(effects.any { it is PrivateMediaEffect.NavigateToVip })
        assertFalse(effects.any { it is PrivateMediaEffect.OpenMediaPicker })

        val cleaned = app.vaultMediaRepository.cleanupMissingMediaRecords()
        assertTrue(
            app.vaultMediaRepository.observeAlbumMedia(album.id).first()
                .any { it.media.id == mediaId && it.file.exists() },
        )
        logEvidence("D1_revoke", "service=false mediaKept=true cleaned=$cleaned")
    }

    @Test
    fun restoreEntitlement_reprotectsAndAllowsImport() = runBlocking {
        installForceVip()
        val pkg = pickAndLockPackage()
        lockedPackage = pkg
        awaitLockedInRoom(pkg)
        val album = app.vaultMediaRepository.ensureDefaultAlbum()
        val seedUri = trackUri(SyntheticMediaFactory.createJpegUri(context, 502))
        val seeded = app.vaultMediaRepository.importMedia(album.id, listOf(seedUri))
        trackImported(seeded.importedMediaIds)
        assertEquals(1, seeded.successCount)

        installForceNone()
        app.appLockProtectionCoordinator.reconcile()
        assertTrue(awaitServiceStopped(12_000))

        // D2: restore Fake VIP
        installForceVip()
        bringVaultToForeground()
        awaitProcessForeground()
        app.appLockProtectionCoordinator.onProcessForeground()
        app.appLockProtectionCoordinator.reconcile()
        awaitServiceRunning(20_000)
        assertTrue(app.appLockProtectionCoordinator.allowsLiveProtection())
        assertTrue(AppLockMonitorService.isRunning)
        assertTrue(pkg in app.appLockRepository.observeLockedPackages().first())

        val more = trackUri(SyntheticMediaFactory.createJpegUri(context, 503))
        val again = app.vaultMediaRepository.importMedia(album.id, listOf(more))
        trackImported(again.importedMediaIds)
        assertEquals(1, again.successCount)
        assertTrue(seeded.importedMediaIds.all { id ->
            app.vaultMediaRepository.observeAlbumMedia(album.id).first().any { it.media.id == id }
        })
        logEvidence("D2_restore", "service=true importOk=${again.importedMediaIds}")
    }

    @Test
    fun bothPaidDenied_lockStopsAlbumImportOnly() = runBlocking {
        installForceVip()
        val pkg = pickAndLockPackage()
        lockedPackage = pkg
        awaitLockedInRoom(pkg)
        val album = app.vaultMediaRepository.ensureDefaultAlbum()
        val uri = trackUri(SyntheticMediaFactory.createJpegUri(context, 504))
        val seeded = app.vaultMediaRepository.importMedia(album.id, listOf(uri))
        trackImported(seeded.importedMediaIds)
        val mediaId = seeded.importedMediaIds.first()

        // D4 Fake: VIP gone + trial overridden to None via ForceNone
        installForceNone()
        app.appLockProtectionCoordinator.reconcile()
        assertFalse(app.appLockProtectionCoordinator.allowsLiveProtection())
        assertTrue(awaitServiceStopped(12_000))
        assertFalse(
            app.entitlementRepository.actionPolicy.evaluate(EntitlementCapability.APP_LOCK_PROTECTION)
                is ActionDecision.Allowed,
        )
        assertFalse(
            app.entitlementRepository.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT)
                is ActionDecision.Allowed,
        )
        assertTrue(
            app.entitlementRepository.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_VIEW_RESTORE)
                is ActionDecision.Allowed,
        )
        assertTrue(
            app.entitlementRepository.actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_MANAGE_EXISTING)
                is ActionDecision.Allowed,
        )
        val item = app.vaultMediaRepository.observeAlbumMedia(album.id).first()
            .first { it.media.id == mediaId }
        assertTrue(item.file.exists())
        logEvidence("D4_both_denied", "lockDenied=true importDenied=true viewAllowed=true")
    }

    @Test
    fun rapidFakeSwitch_lockAndAlbumStable() = runBlocking {
        installForceVip()
        val pkg = pickAndLockPackage()
        lockedPackage = pkg
        awaitLockedInRoom(pkg)
        val album = app.vaultMediaRepository.ensureDefaultAlbum()
        val uri = trackUri(SyntheticMediaFactory.createJpegUri(context, 505))
        val seeded = app.vaultMediaRepository.importMedia(album.id, listOf(uri))
        trackImported(seeded.importedMediaIds)
        assertEquals(1, seeded.successCount)

        repeat(4) { cycle ->
            installForceNone()
            app.appLockProtectionCoordinator.reconcile()
            assertTrue("cycle=$cycle stop", awaitServiceStopped(10_000))
            installForceVip()
            bringVaultToForeground()
            awaitProcessForeground()
            app.appLockProtectionCoordinator.onProcessForeground()
            app.appLockProtectionCoordinator.reconcile()
            awaitServiceRunning(15_000)
            assertTrue("cycle=$cycle allow", app.appLockProtectionCoordinator.allowsLiveProtection())
            logEvidence("D5_cycle_$cycle", "service=${AppLockMonitorService.isRunning}")
        }
        assertTrue(pkg in app.appLockRepository.observeLockedPackages().first())
        assertTrue(
            app.vaultMediaRepository.observeAlbumMedia(album.id).first()
                .any { it.media.id in createdMediaIds && it.file.exists() },
        )
        assertTrue(app.entitlementRepository.state.value.source is EntitlementSource.Vip)
        logEvidence("D5_done", "mediaIntact=true")
    }

    private fun installForceVip() {
        EntitlementInstrumentationBridge.install(app, InstrumentationEntitlementOverride.ForceVip())
    }

    private fun installForceNone() {
        EntitlementInstrumentationBridge.install(app, InstrumentationEntitlementOverride.ForceNone)
    }

    private fun trackUri(uri: android.net.Uri): android.net.Uri {
        syntheticUris += uri
        return uri
    }

    private fun trackImported(ids: List<Long>) {
        createdMediaIds.addAll(ids)
    }

    private fun collectImportEffects(block: (PrivateMediaViewModel) -> Unit): List<PrivateMediaEffect> {
        val vm = PrivateMediaViewModel(app.vaultMediaRepository, app.entitlementRepository.actionPolicy)
        val received = CopyOnWriteArrayList<PrivateMediaEffect>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val job = scope.launch { vm.effects.collect { received += it } }
        try {
            instrumentation.runOnMainSync { block(vm) }
            SystemClock.sleep(200)
        } finally {
            job.cancel()
            scope.cancel()
        }
        return received
    }

    private suspend fun ensureInitialized() {
        when (val dest = app.onboardingRepository.resolveStartupDestination()) {
            is OnboardingResult.Success -> when (dest.value) {
                StartupDestination.PrivacyConsent -> {
                    assertTrue(app.onboardingRepository.acceptPrivacy() is OnboardingResult.Success)
                    assertTrue(
                        app.onboardingRepository.configurePassword(charArrayOf('1', '3', '5', '7', '9', '0'))
                            is OnboardingResult.Success,
                    )
                }
                StartupDestination.CreatePassword -> {
                    assertTrue(
                        app.onboardingRepository.configurePassword(charArrayOf('1', '3', '5', '7', '9', '0'))
                            is OnboardingResult.Success,
                    )
                }
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

    private suspend fun pickAndLockPackage(): String {
        val preferred = listOf(
            "com.android.chrome",
            "com.google.android.deskclock",
            "com.android.contacts",
            "com.google.android.documentsui",
            "com.google.android.apps.maps",
        )
        for (pkg in preferred) {
            if (runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess) {
                when (app.appLockRepository.setLocked(pkg, pkg.substringAfterLast('.'), true)) {
                    AppLockSetResult.Success -> return pkg
                    else -> Unit
                }
            }
        }
        val apps = app.appLockRepository.loadLockableApps()
        val first = apps.firstOrNull() ?: error("No lockable packages")
        assertEquals(
            AppLockSetResult.Success,
            app.appLockRepository.setLocked(first.packageName, first.appName, true),
        )
        return first.packageName
    }

    private suspend fun awaitLockedInRoom(pkg: String) {
        assertTrue(
            waitUntil(8_000) {
                runBlocking { pkg in app.appLockRepository.observeLockedPackages().first() }
            },
        )
        app.appLockProtectionCoordinator.reconcile()
    }

    private fun bringVaultToForeground() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("no launch intent")
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 8_000)
    }

    private fun awaitProcessForeground() {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isProcessStarted()) return
            SystemClock.sleep(100)
        }
        error("process not foreground")
    }

    private fun isProcessStarted(): Boolean = try {
        androidx.lifecycle.ProcessLifecycleOwner.get()
            .lifecycle
            .currentState
            .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
    } catch (_: Throwable) {
        false
    }

    private fun awaitServiceRunning(timeoutMs: Long) {
        assertTrue(
            "service not running entitled=${app.appLockProtectionCoordinator.allowsLiveProtection()} " +
                "pending=${app.appLockProtectionCoordinator.pendingForegroundStart.value}",
            waitUntil(timeoutMs) {
                if (!AppLockMonitorService.isRunning) {
                    runBlocking {
                        if (isProcessStarted()) {
                            app.appLockProtectionCoordinator.onProcessForeground()
                            app.appLockProtectionCoordinator.reconcile()
                        }
                    }
                }
                AppLockMonitorService.isRunning
            },
        )
    }

    private fun awaitServiceStopped(timeoutMs: Long): Boolean =
        waitUntil(timeoutMs) { !AppLockMonitorService.isRunning }

    private fun waitUntil(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return true
            SystemClock.sleep(100)
        }
        return predicate()
    }

    private fun assertEmulatorIdentity() {
        val qemu = shell("getprop ro.kernel.qemu").trim()
        assertEquals("1", qemu)
        val avd = shell("getprop ro.boot.qemu.avd_name").trim()
            .ifEmpty { shell("getprop ro.kernel.qemu.avd_name").trim() }
        if (avd.isNotEmpty()) assertEquals("CalculatorVault_Test", avd)
    }

    private fun shell(command: String): String {
        val pfd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return BufferedReader(InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(pfd))).use { reader ->
            buildString {
                var line: String?
                while (reader.readLine().also { line = it } != null) appendLine(line)
            }
        }
    }

    private fun logEvidence(step: String, detail: String) {
        Log.i(TAG, "$step :: $detail")
    }

    private companion object {
        const val TAG = "CV_I335_CROSS"
    }
}
