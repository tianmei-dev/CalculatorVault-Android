package com.aurora.calculatorvault.core.analytics

interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
}
