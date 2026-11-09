package com.aurora.calculatorvault.feature.payment.domain

import com.aurora.calculatorvault.feature.payment.data.PayInstruction
import java.net.URI

/** What the waiting page may do with an order's `pay` block. Never implies the order is paid. */
sealed interface PayLaunchDecision {
    /** Open the Yusen cashier in the system browser. [url] is secret: do not log or persist it. */
    data class OpenCashier(val url: String) : PayLaunchDecision {
        override fun toString(): String = "OpenCashier(url=[REDACTED])"
    }

    /** s32it isolated environment: no link; payment is simulated from the PC side. */
    data object TestPayment : PayLaunchDecision

    data class Rejected(val reason: Reason) : PayLaunchDecision

    enum class Reason { MissingPayInfo, TestKindNotAllowed, UnknownKind, InvalidUrl, NonHttpsUrl }
}

/**
 * [allowTestKind] is true only for Debug / localIntegration builds. Release accepts nothing but
 * kind=URL with an absolute https URL; TEST and unknown kinds are rejected.
 */
class PayLaunchPolicy(private val allowTestKind: Boolean) {

    fun decide(pay: PayInstruction?): PayLaunchDecision {
        pay ?: return PayLaunchDecision.Rejected(PayLaunchDecision.Reason.MissingPayInfo)
        return when (pay.kind) {
            KIND_URL -> decideUrl(pay.value)
            KIND_TEST ->
                if (allowTestKind) PayLaunchDecision.TestPayment
                else PayLaunchDecision.Rejected(PayLaunchDecision.Reason.TestKindNotAllowed)
            else -> PayLaunchDecision.Rejected(PayLaunchDecision.Reason.UnknownKind)
        }
    }

    private fun decideUrl(value: String?): PayLaunchDecision {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() }
            ?: return PayLaunchDecision.Rejected(PayLaunchDecision.Reason.InvalidUrl)
        val uri = try {
            URI(raw)
        } catch (_: Exception) {
            return PayLaunchDecision.Rejected(PayLaunchDecision.Reason.InvalidUrl)
        }
        if (!uri.isAbsolute || uri.host.isNullOrBlank() || uri.userInfo != null) {
            return PayLaunchDecision.Rejected(PayLaunchDecision.Reason.InvalidUrl)
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return PayLaunchDecision.Rejected(PayLaunchDecision.Reason.NonHttpsUrl)
        }
        return PayLaunchDecision.OpenCashier(raw)
    }

    companion object {
        const val KIND_URL = "URL"
        const val KIND_TEST = "TEST"
    }
}
