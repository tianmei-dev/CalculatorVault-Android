package com.aurora.calculatorvault.core.thirdparty

import android.util.Log
import com.aurora.calculatorvault.BuildConfig
import com.aurora.calculatorvault.core.analytics.AnalyticsInitializer
import com.aurora.calculatorvault.core.monitoring.BuglyInitializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ThirdPartySdkInitializer(
    private val analyticsInitializer: AnalyticsInitializer,
    private val buglyInitializer: BuglyInitializer,
) {
    private val consentMutex = Mutex()
    private var withdrawn = false

    suspend fun withdrawConsent(persist: suspend () -> Unit) = consentMutex.withLock {
        persist() // A failed write must not change any runtime state.
        withdrawn = true
        runCatching { analyticsInitializer.withdrawConsent() }
            .onFailure { debugLog("umeng stop failed; process replacement required", it) }
        runCatching { buglyInitializer.withdrawConsent() }
            .onFailure { debugLog("bugly stop failed; process replacement required", it) }
    }

    suspend fun initializeIfAllowed() = consentMutex.withLock {
        if (withdrawn) return@withLock
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
