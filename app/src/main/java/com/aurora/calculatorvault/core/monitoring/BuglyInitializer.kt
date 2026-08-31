package com.aurora.calculatorvault.core.monitoring

import android.content.Context
import android.util.Log
import com.aurora.calculatorvault.BuildConfig
import com.aurora.calculatorvault.core.datastore.security.SecurityPreferencesDataSource
import com.tencent.bugly.crashreport.CrashReport
import java.util.concurrent.atomic.AtomicBoolean

class BuglyInitializer(
    context: Context,
    private val consentDataSource: SecurityPreferencesDataSource,
) {
    private val appContext = context.applicationContext
    private val initialized = AtomicBoolean(false)

    suspend fun initializeIfAllowed() {
        if (initialized.get()) {
            debugLog("bugly already initialized")
            return
        }
        val allowed = runCatching { consentDataSource.read().privacyAccepted }.getOrElse { error ->
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
        if (!initialized.compareAndSet(false, true)) return

        try {
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
