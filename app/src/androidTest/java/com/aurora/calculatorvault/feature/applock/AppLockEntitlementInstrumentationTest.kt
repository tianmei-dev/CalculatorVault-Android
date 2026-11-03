package com.aurora.calculatorvault.feature.applock

import android.content.Context
import android.content.Intent
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
import com.aurora.calculatorvault.feature.applock.overlay.AppLockOverlayController
import com.aurora.calculatorvault.feature.applock.service.AppLockMonitorService
import com.aurora.calculatorvault.feature.entitlement.EntitlementInstrumentationBridge
import com.aurora.calculatorvault.feature.entitlement.InstrumentationEntitlementOverride
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.onboarding.data.OnboardingResult
import com.aurora.calculatorvault.feature.onboarding.domain.StartupDestination
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * I3.3.3-C Instrumentation acceptance on CalculatorVault_Test.
 *
 * Fake entitlement lives only in androidTest ([EntitlementInstrumentationBridge]).
 * Run via `adb shell am instrument` after `adb install -r` (do not use Gradle connected*).
 */
@RunWith(AndroidJUnit4::class)
class AppLockEntitlementInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app: CalculatorVaultApp = context.applicationContext as CalculatorVaultApp
    private val device: UiDevice = UiDevice.getInstance(instrumentation)

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
        assertTrue(
            "androidTest Fake override must install",
            run {
                EntitlementInstrumentationBridge.install(
                    app,
                    InstrumentationEntitlementOverride.ForceNone,
                )
                val armed = EntitlementInstrumentationBridge.isArmed() &&
                    app.entitlementRepository.stateOverride != null
                EntitlementInstrumentationBridge.clear(app)
                armed
            },
        )
    }

    @After
    fun tearDown() {
        EntitlementInstrumentationBridge.clear(app)
        assertFalse(
            "Fake must not residual after clear",
            EntitlementInstrumentationBridge.isArmed() ||
                app.entitlementRepository.stateOverride != null,
        )
        AppLockMonitorService.stop(context)
        runBlocking {
            app.entitlementRepository.refresh()
            app.appLockProtectionCoordinator.reconcile()
        }
        device.pressHome()
    }

    @Test
    fun fakeVip_overlayLifecycle_roomPreserved_disabledStaysOff() = runBlocking {
        val lockedPackage = pickLockablePackage()
        assertEquals(
            AppLockSetResult.Success,
            app.appLockRepository.setLocked(lockedPackage, "InstrTarget", true),
        )
        val enabledBefore = awaitLockedInRoom(lockedPackage)
        logEvidence("room_before", "locked=$enabledBefore")

        installForceVip()
        bringVaultToForeground()
        awaitProcessForeground()
        awaitServiceRunning(20_000)
        assertTrue(app.entitlementRepository.state.value.source is EntitlementSource.Vip)
        assertTrue(app.appLockProtectionCoordinator.allowsLiveProtection())
        assertTrue(AppLockMonitorService.isRunning)
        logEvidence("service_started", "running=true source=${app.entitlementRepository.state.value.source}")

        launchPackage(lockedPackage)
        assertTrue("Overlay must appear", waitForOverlay(20_000))
        logEvidence(
            "overlay_shown",
            "showing=${AppLockMonitorService.isOverlayShowing()} " +
                "target=${AppLockMonitorService.overlayTargetPackage()} dumpsys=${dumpOverlaySnippet()}",
        )

        installForceNone()
        app.appLockProtectionCoordinator.reconcile()
        assertFalse(app.appLockProtectionCoordinator.allowsLiveProtection())
        assertTrue(waitForOverlayGone(12_000))
        assertTrue(awaitServiceStopped(12_000))
        logEvidence(
            "after_revoke",
            "running=${AppLockMonitorService.isRunning} " +
                "overlay=${AppLockMonitorService.isOverlayShowing()}",
        )

        device.pressHome()
        launchPackage(lockedPackage)
        assertTrue(
            waitUntil(8_000) {
                !AppLockMonitorService.isOverlayShowing() &&
                    device.currentPackageName == lockedPackage
            },
        )

        val enabledAfterRevoke = app.appLockRepository.observeLockedPackages().first()
        assertTrue(lockedPackage in enabledAfterRevoke)
        assertEquals(enabledBefore, enabledAfterRevoke)
        logEvidence("room_after_revoke", "locked=$enabledAfterRevoke")

        installForceVip()
        bringVaultToForeground()
        awaitProcessForeground()
        awaitServiceRunning(20_000)
        device.pressHome()
        launchPackage(lockedPackage)
        assertTrue(waitForOverlay(20_000))
        logEvidence("overlay_restored", dumpOverlaySnippet())

        assertEquals(
            AppLockSetResult.Success,
            app.appLockRepository.setLocked(lockedPackage, "InstrTarget", false),
        )
        awaitUnlockedInRoom(lockedPackage)
        installForceNone()
        installForceVip()
        bringVaultToForeground()
        app.appLockProtectionCoordinator.reconcile()
        assertFalse(lockedPackage in app.appLockRepository.observeLockedPackages().first())
        logEvidence("user_disabled_stays_off", "locked=${app.appLockRepository.observeLockedPackages().first()}")
    }

    @Test
    fun backgroundFgs_defersWithoutWipingLocks_foregroundRecovers() = runBlocking {
        val lockedPackage = pickLockablePackage()
        assertEquals(
            AppLockSetResult.Success,
            app.appLockRepository.setLocked(lockedPackage, "InstrTarget", true),
        )
        awaitLockedInRoom(lockedPackage)
        installForceVip()
        bringVaultToForeground()
        awaitProcessForeground()
        awaitServiceRunning(20_000)
        val locked = app.appLockRepository.observeLockedPackages().first()

        AppLockMonitorService.stop(context)
        device.pressHome()
        // Drive reconcile while not in STARTED; Decision should Defer or keep Room intact.
        waitUntil(5_000) { !ProcessLifecycleOwnerCompat.isStarted() }
        app.appLockProtectionCoordinator.reconcile()
        assertEquals(locked, app.appLockRepository.observeLockedPackages().first())
        logEvidence(
            "background_reconcile",
            "fg=${ProcessLifecycleOwnerCompat.isStarted()} " +
                "pending=${app.appLockProtectionCoordinator.pendingForegroundStart.value} " +
                "running=${AppLockMonitorService.isRunning} locked=$locked",
        )

        bringVaultToForeground()
        awaitProcessForeground()
        app.appLockProtectionCoordinator.onProcessForeground()
        assertEquals(locked, app.appLockRepository.observeLockedPackages().first())
        assertTrue(
            waitUntil(15_000) {
                AppLockMonitorService.isRunning ||
                    app.appLockProtectionCoordinator.pendingForegroundStart.value
            },
        )
        logEvidence(
            "foreground_recover",
            "running=${AppLockMonitorService.isRunning} " +
                "pending=${app.appLockProtectionCoordinator.pendingForegroundStart.value}",
        )
    }

    @Test
    fun pinVerifyDuringRevoke_discardsSuccess() = runBlocking {
        installForceVip()
        bringVaultToForeground()
        awaitProcessForeground()
        app.appLockProtectionCoordinator.reconcile()
        val gate = CountDownLatch(1)
        val started = AtomicBoolean(false)
        // Unlock override may block; keep verification off the main thread to avoid ANR.
        AppLockMonitorService.stop(context)
        val controller = AppLockOverlayController(
            context = context,
            vaultUnlockUseCase = app.vaultUnlockUseCase,
            sessionManager = app.appLockSessionManager,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            protectionStillAllowed = { app.appLockProtectionCoordinator.allowsLiveProtection() },
            expectedGeneration = { app.appLockProtectionCoordinator.protectionGeneration() },
            passwordVerifierOverride = {
                started.set(true)
                gate.await(8, TimeUnit.SECONDS)
                true
            },
        )
        val generation = app.appLockProtectionCoordinator.protectionGeneration()
        assertTrue(
            controller.show(
                targetPackageName = "com.example.pinrace",
                targetAppName = "PinRace",
                detectedAtElapsed = SystemClock.elapsedRealtime(),
                protectionGeneration = generation,
            ),
        )
        instrumentation.runOnMainSync {
            val inputDigit = AppLockOverlayController::class.java
                .getDeclaredMethod("inputDigit", Int::class.javaPrimitiveType)
            inputDigit.isAccessible = true
            repeat(4) { inputDigit.invoke(controller, 1) }
            val verify = AppLockOverlayController::class.java.getDeclaredMethod("verifyPassword")
            verify.isAccessible = true
            verify.invoke(controller)
        }
        assertTrue(waitUntil(5_000) { started.get() })
        installForceNone()
        app.appLockProtectionCoordinator.bumpGeneration()
        app.appLockProtectionCoordinator.reconcile()
        gate.countDown()
        assertTrue(
            waitUntil(5_000) {
                !app.appLockSessionManager.isTemporarilyUnlocked("com.example.pinrace")
            },
        )
        assertTrue(!controller.isShowing() || waitForOverlayGone(5_000))
        instrumentation.runOnMainSync { controller.hide() }
        logEvidence("pin_revoke", "tempUnlock=false")
    }

    @Test
    fun repeatedRevokeRestore_noResidue() = runBlocking {
        val lockedPackage = pickLockablePackage()
        assertEquals(
            AppLockSetResult.Success,
            app.appLockRepository.setLocked(lockedPackage, "InstrTarget", true),
        )
        awaitLockedInRoom(lockedPackage)
        repeat(3) { cycle ->
            installForceVip()
            bringVaultToForeground()
            awaitProcessForeground()
            awaitServiceRunning(20_000)
            assertTrue("cycle=$cycle service", AppLockMonitorService.isRunning)
            launchPackage(lockedPackage)
            assertTrue("cycle=$cycle overlay", waitForOverlay(15_000))
            installForceNone()
            app.appLockProtectionCoordinator.reconcile()
            assertTrue("cycle=$cycle overlay gone", waitForOverlayGone(10_000))
            assertTrue("cycle=$cycle service stop", awaitServiceStopped(10_000))
            device.pressHome()
            logEvidence("cycle_$cycle", "service=false overlay=false")
        }
        assertTrue(lockedPackage in app.appLockRepository.observeLockedPackages().first())
    }

    private fun installForceVip() {
        EntitlementInstrumentationBridge.install(app, InstrumentationEntitlementOverride.ForceVip())
    }

    private fun installForceNone() {
        EntitlementInstrumentationBridge.install(app, InstrumentationEntitlementOverride.ForceNone)
    }

    private suspend fun ensureInitialized() {
        when (val dest = app.onboardingRepository.resolveStartupDestination()) {
            is OnboardingResult.Success -> when (dest.value) {
                StartupDestination.PrivacyConsent -> {
                    assertTrue(app.onboardingRepository.acceptPrivacy() is OnboardingResult.Success)
                    assertTrue(
                        app.onboardingRepository.configurePassword(TEST_PIN.copyOf())
                            is OnboardingResult.Success,
                    )
                }
                StartupDestination.CreatePassword -> {
                    assertTrue(
                        app.onboardingRepository.configurePassword(TEST_PIN.copyOf())
                            is OnboardingResult.Success,
                    )
                }
                StartupDestination.Calculator -> Unit
            }
            is OnboardingResult.Failure -> error("startup resolve failed: ${dest.error}")
        }
        app.onPrivacyAccepted()
        assertTrue(app.hasPrivacyConsent())
    }

    private suspend fun pickLockablePackage(): String {
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

    private suspend fun awaitLockedInRoom(packageName: String): Set<String> {
        assertTrue(
            waitUntil(8_000) {
                runBlocking { packageName in app.appLockRepository.observeLockedPackages().first() }
            },
        )
        app.appLockProtectionCoordinator.reconcile()
        return app.appLockRepository.observeLockedPackages().first()
    }

    private suspend fun awaitUnlockedInRoom(packageName: String) {
        assertTrue(
            waitUntil(8_000) {
                runBlocking { packageName !in app.appLockRepository.observeLockedPackages().first() }
            },
        )
        app.appLockProtectionCoordinator.reconcile()
    }

    private suspend fun awaitServiceRunning(timeoutMs: Long) {
        app.appLockProtectionCoordinator.onProcessForeground()
        app.appLockProtectionCoordinator.reconcile()
        assertTrue(
            "AppLockMonitorService did not start within ${timeoutMs}ms " +
                "entitled=${app.appLockProtectionCoordinator.allowsLiveProtection()} " +
                "pending=${app.appLockProtectionCoordinator.pendingForegroundStart.value} " +
                "locked=${app.appLockRepository.observeLockedPackages().first().size} " +
                "fg=${ProcessLifecycleOwnerCompat.isStarted()}",
            waitUntil(timeoutMs) {
                if (!AppLockMonitorService.isRunning) {
                    // Re-drive reconcile when Flow/lifecycle catches up (not a fixed blind sleep).
                    runBlocking {
                        if (ProcessLifecycleOwnerCompat.isStarted()) {
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

    private fun awaitProcessForeground() {
        assertTrue(
            "ProcessLifecycleOwner never reached STARTED",
            waitUntil(12_000) { ProcessLifecycleOwnerCompat.isStarted() },
        )
    }

    private fun bringVaultToForeground() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("No launcher for vault")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(launch)
        device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 10_000)
    }

    private fun launchPackage(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("No launcher for $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 10_000)
    }

    private fun waitForOverlay(timeoutMs: Long): Boolean =
        waitUntil(timeoutMs) {
            AppLockMonitorService.isOverlayShowing() || hasOverlayWindow()
        }

    private fun waitForOverlayGone(timeoutMs: Long): Boolean =
        // Prefer controller state: dumpsys may still list our Activity windows.
        waitUntil(timeoutMs) { !AppLockMonitorService.isOverlayShowing() }

    private fun waitUntil(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < timeoutMs) {
            if (predicate()) return true
            SystemClock.sleep(120)
        }
        return predicate()
    }

    private fun hasOverlayWindow(): Boolean {
        val dump = shell("dumpsys window windows")
        return (dump.contains("TYPE_APPLICATION_OVERLAY") || dump.contains("Application Overlay")) &&
            dump.contains(context.packageName)
    }

    private fun dumpOverlaySnippet(): String {
        val dump = shell("dumpsys window windows")
        return dump.lineSequence()
            .filter {
                it.contains(context.packageName, ignoreCase = true) ||
                    it.contains("TYPE_APPLICATION_OVERLAY") ||
                    it.contains("Application Overlay")
            }
            .take(12)
            .joinToString(" | ")
            .ifBlank { "no-overlay-lines" }
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
        return BufferedReader(InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(pfd))).use { reader ->
            buildString {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    appendLine(line)
                }
            }
        }
    }

    private fun assertEmulatorIdentity() {
        val qemu = shell("getprop ro.kernel.qemu").trim()
        assertEquals("1", qemu)
        val avd = shell("getprop ro.boot.qemu.avd_name").trim()
            .ifEmpty { shell("getprop ro.kernel.qemu.avd_name").trim() }
        if (avd.isNotEmpty()) {
            assertEquals("CalculatorVault_Test", avd)
        }
    }

    private fun logEvidence(step: String, detail: String) {
        Log.i(EVIDENCE_TAG, "$step :: $detail")
    }

    private object ProcessLifecycleOwnerCompat {
        fun isStarted(): Boolean = try {
            androidx.lifecycle.ProcessLifecycleOwner.get()
                .lifecycle
                .currentState
                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        } catch (_: Throwable) {
            false
        }
    }

    private companion object {
        const val EVIDENCE_TAG = "CV_B3_EVIDENCE"
        val TEST_PIN = charArrayOf('1', '3', '5', '7', '9', '0')
    }
}
