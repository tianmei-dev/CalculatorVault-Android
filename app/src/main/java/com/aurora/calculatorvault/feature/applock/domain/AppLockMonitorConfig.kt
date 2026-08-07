package com.aurora.calculatorvault.feature.applock.domain

object AppLockMonitorConfig {
    const val POLL_INTERVAL_MILLIS = 250L
    const val FOREGROUND_LOOKBACK_MILLIS = 5_000L
    const val TEMPORARY_UNLOCK_CLEAR_DEBOUNCE_MILLIS = 500L
}
