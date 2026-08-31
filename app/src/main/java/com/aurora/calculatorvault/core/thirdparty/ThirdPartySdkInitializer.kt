package com.aurora.calculatorvault.core.thirdparty

import android.util.Log
import com.aurora.calculatorvault.BuildConfig
import com.aurora.calculatorvault.core.analytics.AnalyticsInitializer
import com.aurora.calculatorvault.core.monitoring.BuglyInitializer

class ThirdPartySdkInitializer(
    private val analyticsInitializer: AnalyticsInitializer,
    private val buglyInitializer: BuglyInitializer,
) {
    suspend fun initializeIfAllowed() {
        runCatching { analyticsInitializer.initializeIfAllowed() }
            .onFailure { error -> debugLog("umeng init dispatch failed", error) }
        runCatching { buglyInitializer.initializeIfAllowed() }
            .onFailure { error -> debugLog("bugly init dispatch failed", error) }
    }

    private fun debugLog(message: String, error: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.w(LOG_TAG, "$message=${error.javaClass.simpleName}")
        }
    }

    private companion object {
        const val LOG_TAG = "CV_THIRD_PARTY"
    }
}
