package com.aurora.calculatorvault.feature.membership.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.Collections

/**
 * Injectable HTTP surface for membership /me (JVM tests supply fakes).
 * Implementations must not log Authorization values.
 */
fun interface MembershipHttpTransport {
    @Throws(IOException::class)
    fun execute(request: MembershipHttpRequest): MembershipHttpRawResponse
}

data class MembershipHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val connectTimeoutMillis: Int,
    val readTimeoutMillis: Int,
) {
    /** Redacted view for assertions / debug — never includes Bearer secret. */
    fun headerNames(): Set<String> = headers.keys

    fun authorizationPresent(): Boolean =
        headers["Authorization"]?.startsWith("Bearer ") == true

    fun authorizationLooksLikeBearerToken(): Boolean {
        val value = headers["Authorization"] ?: return false
        return value.startsWith("Bearer ") && value.length > "Bearer ".length
    }

    override fun toString(): String =
        "MembershipHttpRequest(url=$url, headers=${redactedHeaders()}, " +
            "connectTimeoutMillis=$connectTimeoutMillis, readTimeoutMillis=$readTimeoutMillis)"

    private fun redactedHeaders(): Map<String, String> =
        headers.mapValues { (key, value) ->
            if (key.equals("Authorization", ignoreCase = true)) "[REDACTED]" else value
        }
}

data class MembershipHttpRawResponse(
    val statusCode: Int,
    val body: String?,
)

/**
 * Default transport: HttpURLConnection, no redirects / retries / cookies.
 */
class UrlConnectionMembershipHttpTransport : MembershipHttpTransport {
    override fun execute(request: MembershipHttpRequest): MembershipHttpRawResponse {
        val connection = URI(request.url).toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = request.connectTimeoutMillis
            connection.readTimeout = request.readTimeoutMillis
            connection.requestMethod = "GET"
            request.headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readBounded(65_536) }
            return MembershipHttpRawResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }
}

internal fun isTimeoutOrNetwork(error: Throwable): MembershipUnavailableReason = when (error) {
    is SocketTimeoutException -> MembershipUnavailableReason.TIMEOUT
    is UnknownHostException -> MembershipUnavailableReason.NETWORK
    is IOException -> MembershipUnavailableReason.NETWORK
    else -> MembershipUnavailableReason.NETWORK
}

private fun java.io.Reader.readBounded(maxChars: Int): String {
    val buffer = CharArray(4_096)
    val result = StringBuilder()
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(result.length + count <= maxChars) { "response too large" }
        result.append(buffer, 0, count)
    }
    return result.toString()
}

/** Test helper: immutable header map that refuses accidental mutation of secrets in assertions. */
fun membershipHeaders(appCode: String, bearerToken: String): Map<String, String> =
    Collections.unmodifiableMap(
        linkedMapOf(
            "X-App-Code" to appCode,
            "Accept" to "application/json",
            "Authorization" to "Bearer $bearerToken",
        ),
    )
