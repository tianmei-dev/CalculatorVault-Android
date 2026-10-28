package com.aurora.calculatorvault.feature.applock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.aurora.calculatorvault.BuildConfig
import androidx.core.app.NotificationCompat
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.feature.applock.domain.AppLockMonitorConfig
import com.aurora.calculatorvault.feature.applock.domain.ForegroundAppResult
import com.aurora.calculatorvault.feature.applock.domain.OverlayPermissionHelper
import com.aurora.calculatorvault.feature.applock.domain.UsageAccessPermissionHelper
import com.aurora.calculatorvault.feature.applock.domain.UsageStatsForegroundAppDetector
import com.aurora.calculatorvault.feature.applock.overlay.AppLockOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppLockMonitorService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var lockedPackagesJob: Job? = null
    private var clearUnlockJob: Job? = null
    private var overlayController: AppLockOverlayController? = null

    @Volatile
    private var lockedPackages: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        runningService = java.lang.ref.WeakReference(this)
        if (BuildConfig.DEBUG) Log.d(TAG, "service started")
        val app = applicationContext as CalculatorVaultApp
        overlayController = AppLockOverlayController(
            context = applicationContext,
            vaultUnlockUseCase = app.vaultUnlockUseCase,
            sessionManager = app.appLockSessionManager,
            scope = serviceScope,
        )
        startAsForegroundIfPossible()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> serviceScope.launch {
                val app = applicationContext as CalculatorVaultApp
                val allowed = runCatching { app.hasPrivacyConsent() }.getOrDefault(false)
                withContext(Dispatchers.Main) {
                    if (allowed && !app.privacyWithdrawn) {
                        observeLockedPackages()
                        startMonitoring()
                    } else {
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) Log.d(TAG, "service destroyed")
        releaseMonitoring()
        runningService = null
        super.onDestroy()
    }

    private fun releaseMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        lockedPackagesJob?.cancel()
        lockedPackagesJob = null
        clearUnlockJob?.cancel()
        clearUnlockJob = null
        overlayController?.hide()
        overlayController = null
        (applicationContext as CalculatorVaultApp).appLockSessionManager.clearAll()
        isRunning = false
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        val app = applicationContext as CalculatorVaultApp
        val usagePermissionHelper = UsageAccessPermissionHelper(applicationContext)
        val overlayPermissionHelper = OverlayPermissionHelper(applicationContext)
        val detector = UsageStatsForegroundAppDetector(
            context = applicationContext,
            permissionHelper = usagePermissionHelper,
        )
        monitorJob = serviceScope.launch {
            if (BuildConfig.DEBUG) Log.d(TAG, "usage access granted=${usagePermissionHelper.hasUsageAccess()}")
            if (BuildConfig.DEBUG) Log.d(TAG, "overlay permission granted=${overlayPermissionHelper.isGranted()}")
            while (isActive) {
                if (app.privacyWithdrawn) break
                handleForegroundResult(
                    app = app,
                    overlayPermissionHelper = overlayPermissionHelper,
                    result = detector.currentForegroundPackage(),
                )
                delay(AppLockMonitorConfig.POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun observeLockedPackages() {
        if (lockedPackagesJob?.isActive == true) return
        val app = applicationContext as CalculatorVaultApp
        lockedPackagesJob = serviceScope.launch {
            app.appLockRepository.observeLockedPackages().collect { packages ->
                lockedPackages = packages.toSet()
                if (BuildConfig.DEBUG) Log.d(TAG, "locked packages updated count=${packages.size}")
                handleLockedPackagesChanged(app, packages)
            }
        }
    }

    private suspend fun handleLockedPackagesChanged(
        app: CalculatorVaultApp,
        packages: Set<String>,
    ) {
        val verifyingPackage = app.appLockSessionManager.currentVerifyingPackage()
        val unlockedPackage = app.appLockSessionManager.currentUnlockedPackage()
        if (verifyingPackage != null && verifyingPackage !in packages) {
            app.appLockSessionManager.finishVerification(verifyingPackage)
            withContext(Dispatchers.Main) { overlayController?.hide() }
            if (BuildConfig.DEBUG) Log.d(TAG, "verification cleared because package disabled")
        }
        if (unlockedPackage != null && unlockedPackage !in packages) {
            app.appLockSessionManager.clearUnlocked(unlockedPackage)
            if (BuildConfig.DEBUG) Log.d(TAG, "temporary unlock cleared because package disabled")
        }
        if (packages.isEmpty()) {
            withContext(Dispatchers.Main) { overlayController?.hide() }
            app.appLockSessionManager.clearAll()
            if (BuildConfig.DEBUG) Log.d(TAG, "service stopped because no locked apps")
            stopSelf()
        }
    }

    private suspend fun handleForegroundResult(
        app: CalculatorVaultApp,
        overlayPermissionHelper: OverlayPermissionHelper,
        result: ForegroundAppResult,
    ) {
        if (app.privacyWithdrawn) return
        val packageName = (result as? ForegroundAppResult.Success)?.packageName ?: return
        val now = SystemClock.elapsedRealtime()
        val session = app.appLockSessionManager
        if (session.updateForegroundPackage(packageName, now)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "foreground state updated")
        }

        handleTemporaryUnlockLifecycle(app, packageName)
        handleVerificationLifecycle(app, packageName)

        if (packageName == packageNameSelf) return
        if (packageName !in lockedPackages) return
        if (BuildConfig.DEBUG) Log.d(TAG, "protected target matched")
        if (session.isTemporarilyUnlocked(packageName)) return
        if (!session.beginVerification(packageName)) return
        if (BuildConfig.DEBUG) Log.d(TAG, "verification requested")

        if (!overlayPermissionHelper.isGranted()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "overlay permission lost")
            withContext(Dispatchers.Main) { overlayController?.hide() }
            session.finishVerification(packageName)
            return
        }

        val targetName = runCatching { app.hiddenAppRuntime.resolve(packageName).appName }.getOrNull()
        val shown = withContext(Dispatchers.Main) {
            !app.privacyWithdrawn && overlayController?.show(
                targetPackageName = packageName,
                targetAppName = targetName,
                detectedAtElapsed = now,
            ) == true
        }
        if (!shown) {
            session.finishVerification(packageName)
        }
    }

    private fun handleTemporaryUnlockLifecycle(
        app: CalculatorVaultApp,
        foregroundPackage: String,
    ) {
        val unlockedPackage = app.appLockSessionManager.currentUnlockedPackage() ?: return
        if (foregroundPackage == unlockedPackage) {
            clearUnlockJob?.cancel()
            clearUnlockJob = null
            if (BuildConfig.DEBUG) Log.d(TAG, "unlock expiry cancelled")
            return
        }
        scheduleClear(app, unlockedPackage)
    }

    private fun handleVerificationLifecycle(
        app: CalculatorVaultApp,
        foregroundPackage: String,
    ) {
        val verifyingPackage = app.appLockSessionManager.currentVerifyingPackage() ?: return
        if (foregroundPackage == verifyingPackage) {
            clearUnlockJob?.cancel()
            clearUnlockJob = null
            return
        }
        scheduleClear(app, verifyingPackage)
    }

    private fun scheduleClear(
        app: CalculatorVaultApp,
        packageName: String,
    ) {
        if (clearUnlockJob?.isActive == true) return
        if (BuildConfig.DEBUG) Log.d(TAG, "unlock expiry scheduled")
        clearUnlockJob = serviceScope.launch {
            delay(AppLockMonitorConfig.TEMPORARY_UNLOCK_CLEAR_DEBOUNCE_MILLIS)
            if (app.appLockSessionManager.lastForegroundPackage() != packageName) {
                app.appLockSessionManager.clearUnlocked(packageName)
                app.appLockSessionManager.finishVerification(packageName)
                withContext(Dispatchers.Main) { overlayController?.hide() }
                if (BuildConfig.DEBUG) Log.d(TAG, "temporary unlock cleared")
            }
        }
    }

    private val packageNameSelf: String
        get() = packageName

    private fun startAsForegroundIfPossible() {
        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (error: RuntimeException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "start foreground failed=${error::class.java.simpleName}")
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_lock_monitor_notification_title))
            .setContentText(getString(R.string.app_lock_monitor_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_lock_monitor_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager {
        return getSystemService(NotificationManager::class.java)
    }

    companion object {
        private const val TAG = "CV_APPLOCK"
        internal const val CHANNEL_ID = "app_lock_monitor"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_STOP = "com.aurora.calculatorvault.action.STOP_APP_LOCK_MONITOR"

        @Volatile
        var isRunning: Boolean = false
            private set

        private var runningService: java.lang.ref.WeakReference<AppLockMonitorService>? = null

        fun start(context: Context) {
            if ((context.applicationContext as CalculatorVaultApp).privacyWithdrawn) return
            val intent = Intent(context, AppLockMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Re-post the existing foreground status after a notification grant, without restarting monitoring. */
        fun refreshStatusNotification() {
            if (isRunning) runningService?.get()?.startAsForegroundIfPossible()
        }

        fun stop(context: Context) {
            runningService?.get()?.releaseMonitoring()
            context.stopService(Intent(context, AppLockMonitorService::class.java))
        }
    }
}
