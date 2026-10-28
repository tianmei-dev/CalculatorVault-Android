package com.aurora.calculatorvault.core.monitoring

import android.content.Context
import android.util.Log
import com.aurora.calculatorvault.BuildConfig
import com.tencent.bugly.crashreport.CrashReport
import java.util.concurrent.atomic.AtomicBoolean

class BuglyInitializer(
    context: Context,
    private val consent: com.aurora.calculatorvault.core.privacy.PrivacyConsentGate,
) {
    private val appContext = context.applicationContext
    private val initialized = AtomicBoolean(false)

    fun withdrawConsent() {
        if (!initialized.get()) return
        try {
            // closeBugly checks the enabled flag, so close BEFORE disabling.
            CrashReport.closeBugly()
        } finally {
            CrashReport.enableBugly(false)
        }
        // Recovery uses a fresh process, not a second init on closed executors.
    }

    suspend fun initializeIfAllowed() {
        if (initialized.get()) {
            debugLog("bugly already initialized")
            return
        }
        val allowed = runCatching { consent.check() }.getOrElse { error ->
            debugLog("privacy consent read failed, skip bugly init=${error.javaClass.simpleName}")
            return
        }
        if (!allowed) {
            debugLog("privacy consent=false, skip bugly init")
            return
        }
        if (BuildConfig.BUGLY_APP_ID.isBlank()) {
            debugLog("bugly app id missing, skip init")
            return
        }

        try {
            consent.runIfAllowed {
                if (!initialized.compareAndSet(false, true)) return@runIfAllowed
                val strategy = CrashReport.UserStrategy(appContext).apply {
                    appChannel = BuildConfig.BUGLY_CHANNEL
                    appVersion = BuildConfig.VERSION_NAME
                    setEnableNativeCrashMonitor(true)
                    setEnableANRCrashMonitor(true)
                    setEnableUserInfo(false)
                    setBuglyLogUpload(false)
            }

            debugLog("bugly init started")
            CrashReport.initCrashReport(
                appContext,
                BuildConfig.BUGLY_APP_ID,
                BuildConfig.DEBUG,
                strategy,
            )
            debugLog("bugly init success")
            }
        } catch (error: Throwable) {
            initialized.set(false)
            debugLog("bugly init failed=${error.javaClass.simpleName}")
        }
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, message)
    }

    private companion object {
        const val LOG_TAG = "CV_BUGLY"
    }
}
