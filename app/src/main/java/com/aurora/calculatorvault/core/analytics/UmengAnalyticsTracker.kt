package com.aurora.calculatorvault.core.analytics

import android.content.Context
import android.util.Log
import com.aurora.calculatorvault.BuildConfig
import com.umeng.analytics.MobclickAgent
import java.util.concurrent.atomic.AtomicBoolean

class UmengAnalyticsTracker(
    context: Context,
    private val dispatchEvent: (Context, String) -> Unit = { appContext, id ->
        MobclickAgent.onEvent(appContext, id)
    },
) : AnalyticsTracker {
    private val appContext = context.applicationContext
    private val initialized = AtomicBoolean(false)

    @Synchronized
    internal fun markInitialized() {
        initialized.set(true)
    }

    @Synchronized
    internal fun disable() {
        initialized.set(false)
    }

    @Synchronized
    override fun track(event: AnalyticsEvent) {
        if (!initialized.get()) return
        runCatching {
            dispatchEvent(appContext, event.id)
            if (BuildConfig.DEBUG) Log.d(LOG_TAG, "analytics event=${event.id}")
        }.onFailure { error ->
            if (BuildConfig.DEBUG) Log.w(LOG_TAG, "analytics event failed=${event.id}", error)
        }
    }

    private companion object {
        const val LOG_TAG = "CV_ANALYTICS"
    }
}
