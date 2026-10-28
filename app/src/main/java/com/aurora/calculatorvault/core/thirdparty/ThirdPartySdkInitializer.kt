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
    private val consent: com.aurora.calculatorvault.core.privacy.PrivacyConsentGate,
) {
    private val consentMutex = Mutex()
    private var withdrawn = false

    suspend fun withdrawConsent(persist: suspend () -> Unit) {
        // Close the gate before waiting for an initializer or an asynchronous preference write.
        consent.revoke()
        consentMutex.withLock {
            withdrawn = true
            try {
                persist()
            } finally {
                // Even if persistence fails, remain disabled in this process until retry/restart.
                runCatching { analyticsInitializer.withdrawConsent() }
                    .onFailure { debugLog("umeng stop failed; process replacement required", it) }
                runCatching { buglyInitializer.withdrawConsent() }
                    .onFailure { debugLog("bugly stop failed; process replacement required", it) }
            }
        }
    }

    suspend fun initializeIfAllowed() = consentMutex.withLock {
        if (withdrawn || !consent.check()) return@withLock
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
