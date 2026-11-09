package com.aurora.calculatorvault.feature.payment.domain

import com.aurora.calculatorvault.feature.payment.data.PaymentOrderStatus

/**
 * Order polling cadence.
 *
 * - PENDING_PAYMENT / PAID / UNKNOWN: every 2 s for the first 60 s of this polling session,
 *   then every 10 s.
 * - Consecutive query failures back off 2 → 5 → 10 → 30 s; a failure never changes order status.
 * - FULFILLED / EXPIRED: no automatic polling (EXPIRED keeps a manual refresh for late payments).
 */
object PaymentPollPolicy {
    const val FAST_INTERVAL_MS = 2_000L
    const val FAST_WINDOW_MS = 60_000L
    const val SLOW_INTERVAL_MS = 10_000L
    private val FAILURE_BACKOFF_MS = longArrayOf(2_000L, 5_000L, 10_000L, 30_000L)

    /** @return delay before the next automatic query, or null to stop automatic polling. */
    fun nextDelayMillis(
        status: PaymentOrderStatus,
        elapsedInSessionMillis: Long,
        consecutiveFailures: Int,
    ): Long? {
        if (status == PaymentOrderStatus.FULFILLED || status == PaymentOrderStatus.EXPIRED) return null
        if (consecutiveFailures > 0) {
            return FAILURE_BACKOFF_MS[(consecutiveFailures - 1).coerceAtMost(FAILURE_BACKOFF_MS.lastIndex)]
        }
        return if (elapsedInSessionMillis < FAST_WINDOW_MS) FAST_INTERVAL_MS else SLOW_INTERVAL_MS
    }
}
