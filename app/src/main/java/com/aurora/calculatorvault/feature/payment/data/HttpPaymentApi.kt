package com.aurora.calculatorvault.feature.payment.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

interface PaymentApi {
    suspend fun plans(): PaymentResult<List<MembershipPlanOffer>>

    /** Body carries only planCode + clientRequestId; amount / user / channel come from the server. */
    suspend fun createOrder(accessToken: String, planCode: String, clientRequestId: String): PaymentResult<PaymentOrder>

    suspend fun queryOrder(accessToken: String, orderNo: String): PaymentResult<PaymentOrder>
}

/** Injectable HTTP surface (JVM tests supply fakes). Implementations must not log headers or bodies. */
fun interface PaymentHttpTransport {
    @Throws(IOException::class)
    fun execute(request: PaymentHttpRequest): PaymentHttpResponse
}

data class PaymentHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
    val connectTimeoutMillis: Int,
    val readTimeoutMillis: Int,
) {
    override fun toString(): String =
        "PaymentHttpRequest(method=$method, url=$url, headers=${headers.keys}, body=${body?.let { "[${it.length} chars]" }})"
}

data class PaymentHttpResponse(val statusCode: Int, val body: String?)

/** HttpURLConnection, no redirects / retries / cookies. */
class UrlConnectionPaymentHttpTransport : PaymentHttpTransport {
    override fun execute(request: PaymentHttpRequest): PaymentHttpResponse {
        val connection = URI(request.url).toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = request.connectTimeoutMillis
            connection.readTimeout = request.readTimeoutMillis
            connection.requestMethod = request.method
            request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            val body = request.body
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val buffer = CharArray(4_096)
                val result = StringBuilder()
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    require(result.length + count <= MAX_BODY_CHARS) { "response too large" }
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
            return PaymentHttpResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_BODY_CHARS = 65_536
    }
}

/**
 * Payment / plan client for AuroraAppServer.
 *
 * Base URL rules match login and membership: https always; http only for Debug or the exact
 * [localHttpBaseUrl] of localIntegration. Release therefore never talks to cleartext endpoints.
 */
class HttpPaymentApi(
    private val baseUrl: String,
    private val debug: Boolean,
    private val localHttpBaseUrl: String = "",
    private val transport: PaymentHttpTransport = UrlConnectionPaymentHttpTransport(),
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 15_000,
    /** Order status polls are cheap to repeat, so they fail fast instead of stalling feedback. */
    private val queryConnectTimeoutMillis: Int = 5_000,
    private val queryReadTimeoutMillis: Int = 8_000,
) : PaymentApi {

    override suspend fun plans(): PaymentResult<List<MembershipPlanOffer>> =
        call("GET", "/api/v1/membership/plans", token = null, body = null) { data ->
            val array = (data as? org.json.JSONArray) ?: throw JSONException("plans not array")
            (0 until array.length()).map { PaymentJson.plan(array.getJSONObject(it)) }
        }

    override suspend fun createOrder(
        accessToken: String,
        planCode: String,
        clientRequestId: String,
    ): PaymentResult<PaymentOrder> {
        val body = JSONObject()
            .put("planCode", planCode)
            .put("clientRequestId", clientRequestId)
            .toString()
        return call("POST", ORDERS_PATH, accessToken, body) { PaymentJson.order(it as JSONObject) }
    }

    override suspend fun queryOrder(accessToken: String, orderNo: String): PaymentResult<PaymentOrder> {
        require(orderNo.isNotBlank())
        val path = ORDERS_PATH + "/" + URLEncoder.encode(orderNo, "UTF-8")
        return call(
            "GET", path, accessToken, body = null,
            connectTimeout = queryConnectTimeoutMillis, readTimeout = queryReadTimeoutMillis,
        ) { PaymentJson.order(it as JSONObject) }
    }

    private suspend fun <T> call(
        method: String,
        path: String,
        token: String?,
        body: String?,
        connectTimeout: Int = connectTimeoutMillis,
        readTimeout: Int = readTimeoutMillis,
        parse: (Any) -> T,
    ): PaymentResult<T> = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        if (token != null) {
            require(token.isNotBlank() && token.none { it.isWhitespace() || it.isISOControl() })
        }
        val url = try {
            buildUrl(path)
        } catch (_: Exception) {
            // Empty / disallowed Base URL: refuse explicitly, never fall back to another address.
            return@withContext PaymentResult.Failure(PaymentError.EndpointNotConfigured)
        }
        val headers = linkedMapOf(
            "X-App-Code" to APP_CODE,
            "Accept" to "application/json",
        )
        if (token != null) headers["Authorization"] = "Bearer $token"
        if (body != null) headers["Content-Type"] = "application/json; charset=utf-8"
        val request = PaymentHttpRequest(method, url, headers, body, connectTimeout, readTimeout)
        val raw = try {
            transport.execute(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            coroutineContext.ensureActive()
            return@withContext PaymentResult.Failure(PaymentError.Network(timeout = error is SocketTimeoutException))
        }
        coroutineContext.ensureActive()
        PaymentJson.envelope(raw, parse)
    }

    private fun buildUrl(path: String): String {
        val base = URI(baseUrl.trimEnd('/'))
        require(base.host != null && base.userInfo == null && base.query == null && base.fragment == null)
        require(
            base.scheme == "https" ||
                (base.scheme == "http" &&
                    (debug || (localHttpBaseUrl.isNotEmpty() && base == URI(localHttpBaseUrl.trimEnd('/'))))),
        )
        return baseUrl.trimEnd('/') + path
    }

    companion object {
        const val APP_CODE = "calculator_vault"
        const val ORDERS_PATH = "/api/v1/payment/orders"
    }
}

/** Pure envelope / DTO parsing, shared by tests. */
internal object PaymentJson {

    fun <T> envelope(raw: PaymentHttpResponse, parse: (Any) -> T): PaymentResult<T> {
        val root = try {
            raw.body?.takeIf { it.isNotBlank() }?.let(::JSONObject)
        } catch (_: JSONException) {
            null
        }
        val code = root?.takeIf { it.has("code") && !it.isNull("code") }?.optInt("code")
        if (raw.statusCode !in 200..299 || (code != null && code != 0)) {
            return PaymentResult.Failure(mapError(raw.statusCode, code))
        }
        if (root == null || code == null) return PaymentResult.Failure(PaymentError.Malformed)
        val data = root.opt("data")
        if (data == null || data == JSONObject.NULL) return PaymentResult.Failure(PaymentError.Malformed)
        return try {
            PaymentResult.Success(parse(data))
        } catch (_: Exception) {
            PaymentResult.Failure(PaymentError.Malformed)
        }
    }

    fun mapError(status: Int, code: Int?): PaymentError = when (code) {
        40100, 40101, 40102 -> PaymentError.Unauthorized
        40011 -> PaymentError.ClientRequestConflict
        40012 -> PaymentError.LifetimeAlreadyActive
        40008 -> PaymentError.PlanDisabled
        40401 -> PaymentError.PlanNotFound
        40403 -> PaymentError.OrderNotFound
        50003 -> PaymentError.PaymentConfigMissing
        else -> if (status == 401) PaymentError.Unauthorized else PaymentError.Http(status, code)
    }

    fun plan(json: JSONObject): MembershipPlanOffer {
        val priceFen = json.getInt("priceFen")
        require(priceFen > 0)
        val lifetime = json.optBoolean("isLifetime", false)
        val duration = if (json.has("durationDays") && !json.isNull("durationDays")) json.getInt("durationDays") else null
        require(lifetime || (duration != null && duration > 0))
        return MembershipPlanOffer(
            planCode = json.getString("planCode").also { require(it.isNotBlank()) },
            displayName = json.optString("displayName").ifBlank { json.getString("planCode") },
            priceFen = priceFen,
            priceDisplay = json.getString("priceDisplay").also { require(it.isNotBlank()) },
            durationDays = duration,
            isLifetime = lifetime,
        )
    }

    fun order(json: JSONObject): PaymentOrder {
        val pay = json.optJSONObject("pay")?.let { p ->
            PayInstruction(
                kind = p.optString("kind"),
                value = if (p.has("value") && !p.isNull("value")) p.getString("value") else null,
            )
        }
        return PaymentOrder(
            orderNo = json.getString("orderNo").also { require(it.isNotBlank()) },
            planCode = json.getString("planCode"),
            amountFen = json.getInt("amountFen"),
            amountDisplay = json.getString("amountDisplay"),
            currency = json.optString("currency", "CNY"),
            status = PaymentOrderStatus.parse(json.optString("status")),
            expiresAt = instant(json, "expiresAt"),
            paidAt = instant(json, "paidAt"),
            fulfilledAt = instant(json, "fulfilledAt"),
            serverTime = instant(json, "serverTime"),
            pay = pay,
        )
    }

    private fun instant(json: JSONObject, key: String): Instant? =
        if (json.has(key) && !json.isNull(key)) Instant.parse(json.getString(key)) else null
}
