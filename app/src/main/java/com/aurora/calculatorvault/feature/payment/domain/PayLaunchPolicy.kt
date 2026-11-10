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

    enum class Reason {
        MissingPayInfo,
        TestKindNotAllowed,
        UnknownKind,
        InvalidUrl,
        NonHttpsUrl,

        /** This build has no cashier host configured: every link is refused. */
        CashierHostsNotConfigured,

        /** The link's host is not exactly one of the configured cashier hosts. */
        HostNotAllowed,
    }
}

/**
 * [allowTestKind] is true only for Debug / localIntegration builds. Every build accepts kind=URL
 * only as `https://<host>` where `<host>` is exactly one of [cashierHosts] (canonical lowercase
 * ASCII, no userinfo, port or trailing dot). An empty host list refuses all links.
 *
 * Only the first cashier URL is checked; redirects inside the browser (WeChat / Alipay) are not.
 */
class PayLaunchPolicy(
    private val allowTestKind: Boolean,
    cashierHosts: Collection<String>,
) {
    private val allowedHosts: Set<String> = cashierHosts.map { it.trim() }.filter(::isCanonicalHost).toSet()

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
        val raw = value?.trim()
        if (raw.isNullOrEmpty() || raw.length > MAX_URL_LENGTH || raw.any { it !in URL_CHARS }) {
            return reject(PayLaunchDecision.Reason.InvalidUrl)
        }
        if (raw.startsWith("http://", ignoreCase = true)) return reject(PayLaunchDecision.Reason.NonHttpsUrl)
        if (!raw.startsWith(HTTPS_PREFIX, ignoreCase = true)) return reject(PayLaunchDecision.Reason.InvalidUrl)
        // The authority is read from the raw text, so parser differences between this check and
        // the browser cannot move the request to another host.
        val rest = raw.substring(HTTPS_PREFIX.length)
        val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, end)
        if (authority.isEmpty()) return reject(PayLaunchDecision.Reason.InvalidUrl)
        if (allowedHosts.isEmpty()) return reject(PayLaunchDecision.Reason.CashierHostsNotConfigured)
        if (authority !in allowedHosts) return reject(PayLaunchDecision.Reason.HostNotAllowed)
        val uri = try {
            URI(raw)
        } catch (_: Exception) {
            return reject(PayLaunchDecision.Reason.InvalidUrl)
        }
        if (uri.rawAuthority != authority || uri.host != authority || uri.rawUserInfo != null || uri.port != -1) {
            return reject(PayLaunchDecision.Reason.InvalidUrl)
        }
        return PayLaunchDecision.OpenCashier(raw)
    }

    private fun reject(reason: PayLaunchDecision.Reason) = PayLaunchDecision.Rejected(reason)

    companion object {
        const val KIND_URL = "URL"
        const val KIND_TEST = "TEST"
        private const val HTTPS_PREFIX = "https://"
        private const val MAX_URL_LENGTH = 4_096

        /** RFC 3986 characters only: no whitespace, backslash, quotes, angle brackets or non-ASCII. */
        private val URL_CHARS: Set<Char> =
            (('a'..'z') + ('A'..'Z') + ('0'..'9') + "-._~:/?#[]@!$&'()*+,;=%".toList()).toSet()

        private val LABEL = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")

        /** Lowercase ASCII DNS name with at least two labels and a non-numeric last label. */
        fun isCanonicalHost(host: String): Boolean {
            if (host.isEmpty() || host.length > 253) return false
            val labels = host.split('.')
            return labels.size >= 2 && labels.all { LABEL.matches(it) } && labels.last().any { it in 'a'..'z' }
        }

        /** Parses the comma-separated BuildConfig value; invalid entries are dropped (deny by default). */
        fun parseHosts(csv: String): List<String> = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
