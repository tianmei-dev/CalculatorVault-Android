package com.aurora.calculatorvault.feature.payment.data

import java.time.Instant

/** Server catalog entry (GET /api/v1/membership/plans). Display only; orders snapshot server prices. */
data class MembershipPlanOffer(
    val planCode: String,
    val displayName: String,
    val priceFen: Int,
    val priceDisplay: String,
    val durationDays: Int?,
    val isLifetime: Boolean,
)

enum class PaymentOrderStatus {
    PENDING_PAYMENT,
    PAID,
    FULFILLED,
    EXPIRED,

    /** Status string this client does not understand; never treated as paid or failed. */
    UNKNOWN,
    ;

    companion object {
        fun parse(value: String?): PaymentOrderStatus =
            entries.firstOrNull { it != UNKNOWN && it.name == value } ?: UNKNOWN
    }
}

/**
 * `pay` block of an order. [value] is a signed cashier link for kind=URL and must never be
 * logged or persisted; [toString] redacts it.
 */
data class PayInstruction(val kind: String, val value: String?) {
    override fun toString(): String = "PayInstruction(kind=$kind, value=[REDACTED])"
}

data class PaymentOrder(
    val orderNo: String,
    val planCode: String,
    val amountFen: Int,
    val amountDisplay: String,
    val currency: String,
    val status: PaymentOrderStatus,
    val expiresAt: Instant?,
    val paidAt: Instant?,
    val fulfilledAt: Instant?,
    val serverTime: Instant?,
    val pay: PayInstruction?,
)

/** Business / transport failure of a payment call. Never carries tokens or pay links. */
sealed interface PaymentError {
    /** 40100 / 40101 / 40102 or HTTP 401. */
    data object Unauthorized : PaymentError

    /** 40011: clientRequestId already used for a different plan. */
    data object ClientRequestConflict : PaymentError

    /** 40012: lifetime membership already active. */
    data object LifetimeAlreadyActive : PaymentError

    /** 40008: plan disabled. */
    data object PlanDisabled : PaymentError

    /** 40401: plan not found. */
    data object PlanNotFound : PaymentError

    /** 40403: order not found or not owned by the caller. */
    data object OrderNotFound : PaymentError

    /** 50003: server payment configuration missing. */
    data object PaymentConfigMissing : PaymentError

    /** This build has no usable API address (empty, or cleartext where not allowed); nothing was sent. */
    data object EndpointNotConfigured : PaymentError

    /** Connect / read failure or timeout: the request may or may not have reached the server. */
    data class Network(val timeout: Boolean) : PaymentError

    /** Non-2xx with an unknown or missing business code. */
    data class Http(val status: Int, val code: Int?) : PaymentError

    /** 2xx with an envelope or data shape this client cannot read. */
    data object Malformed : PaymentError

    /** Same request can be re-sent safely (same clientRequestId for creates). */
    val retriable: Boolean
        get() = when (this) {
            is Network, Malformed -> true
            is Http -> status >= 500 || status == 0
            else -> false
        }
}

sealed interface PaymentResult<out T> {
    data class Success<T>(val value: T) : PaymentResult<T>
    data class Failure(val error: PaymentError) : PaymentResult<Nothing>
}
