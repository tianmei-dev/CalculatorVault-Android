package com.aurora.calculatorvault.core.analytics

import android.content.Context
import android.util.Log
import com.aurora.calculatorvault.BuildConfig
import com.aurora.calculatorvault.core.datastore.security.SecurityPreferencesDataSource
import com.umeng.analytics.MobclickAgent
import com.umeng.commonsdk.UMConfigure
import java.util.concurrent.atomic.AtomicBoolean

class AnalyticsInitializer(
    context: Context,
    private val consentDataSource: SecurityPreferencesDataSource,
    private val tracker: UmengAnalyticsTracker,
) {
    private val appContext = context.applicationContext
    private val initialized = AtomicBoolean(false)

    fun withdrawConsent() {
        tracker.disable()
        if (initialized.get()) MobclickAgent.disable()
        // disable has no public inverse in common 9.9.8. Keep initialized true
        // until the process is replaced; never pretend the SDK was unloaded.
    }

    suspend fun initializeIfAllowed() {
        if (initialized.get()) return
        val allowed = runCatching { consentDataSource.read().privacyAccepted }.getOrElse {
            debugLog("analytics consent read failed, skip init", it)
            return
        }
        if (!allowed) {
            debugLog("analytics consent=false, skip init")
            return
        }
        if (BuildConfig.UMENG_APP_KEY.isBlank()) {
            debugLog("analytics app key missing, skip init")
            return
        }
        if (!initialized.compareAndSet(false, true)) return

        runCatching {
            UMConfigure.setLogEnabled(BuildConfig.DEBUG)
            MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.MANUAL)
            UMConfigure.init(
                appContext,
                BuildConfig.UMENG_APP_KEY,
                BuildConfig.UMENG_CHANNEL,
                UMConfigure.DEVICE_TYPE_PHONE,
                null,
            )
            tracker.markInitialized()
            debugLog("analytics init success")
        }.onFailure { error ->
            initialized.set(false)
            debugLog("analytics init failed", error)
        }
    }

    private fun debugLog(message: String, error: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (error == null) Log.d(LOG_TAG, message) else Log.w(LOG_TAG, message, error)
    }

    private companion object {
        const val LOG_TAG = "CV_ANALYTICS"
    }
}
