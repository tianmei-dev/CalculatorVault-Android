package com.aurora.calculatorvault.feature.payment.data

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpPaymentApiTest {

    private val plansBody = """
        {"code":0,"message":"success","data":[
          {"planCode":"MONTHLY","displayName":"月卡","priceFen":590,"priceDisplay":"5.90","durationDays":30,"isLifetime":false},
          {"planCode":"YEARLY","displayName":"年卡","priceFen":1990,"priceDisplay":"19.90","durationDays":365,"isLifetime":false},
          {"planCode":"LIFETIME","displayName":"永久会员","priceFen":2990,"priceDisplay":"29.90","isLifetime":true}
        ]}
    """.trimIndent()

    private fun orderBody(status: String = "PENDING_PAYMENT", pay: String? = """{"kind":"URL","value":"https://cashier.example/pay?sig=secret"}""") =
        """
        {"code":0,"message":"success","data":{"orderNo":"CV202611090001","planCode":"YEARLY","amountFen":1990,
        "amountDisplay":"19.90","currency":"CNY","status":"$status","expiresAt":"2026-11-09T12:30:00Z",
        "serverTime":"2026-11-09T12:00:00Z"${pay?.let { ",\"pay\":$it" } ?: ""}}}
        """.trimIndent()

    private class Recording(var response: PaymentHttpResponse) : PaymentHttpTransport {
        val requests = mutableListOf<PaymentHttpRequest>()
        var failure: Exception? = null
        override fun execute(request: PaymentHttpRequest): PaymentHttpResponse {
            requests += request
            failure?.let { throw it }
            return response
        }
    }

    private fun api(transport: PaymentHttpTransport, base: String = "https://api.example.com", debug: Boolean = false) =
        HttpPaymentApi(base, debug, transport = transport)

    @Test
    fun plans_parsesThreeServerPlans_withoutBearer() = runTest {
        val t = Recording(PaymentHttpResponse(200, plansBody))
        val result = api(t).plans() as PaymentResult.Success
        assertEquals(listOf("MONTHLY", "YEARLY", "LIFETIME"), result.value.map { it.planCode })
        assertEquals(listOf("5.90", "19.90", "29.90"), result.value.map { it.priceDisplay })
        assertEquals(listOf(590, 1990, 2990), result.value.map { it.priceFen })
        assertEquals(listOf(30, 365, null), result.value.map { it.durationDays })
        assertTrue(result.value.last().isLifetime)
        val request = t.requests.single()
        assertEquals("GET", request.method)
        assertTrue(request.url.endsWith("/api/v1/membership/plans"))
        assertEquals("calculator_vault", request.headers["X-App-Code"])
        assertFalse(request.headers.containsKey("Authorization"))
    }

    @Test
    fun plans_emptyList_isSuccessEmpty() = runTest {
        val result = api(Recording(PaymentHttpResponse(200, """{"code":0,"data":[]}"""))).plans()
        assertEquals(emptyList<MembershipPlanOffer>(), (result as PaymentResult.Success).value)
    }

    @Test
    fun plans_badJson_missingData_andBadPlan_areMalformed() = runTest {
        for (body in listOf("not json", """{"code":0}""", """{"code":0,"data":[{"planCode":"X","priceFen":0,"priceDisplay":"0"}]}""")) {
            val result = api(Recording(PaymentHttpResponse(200, body))).plans()
            assertEquals(body, PaymentResult.Failure(PaymentError.Malformed), result)
        }
    }

    @Test
    fun create_sendsOnlyPlanAndRequestId_withBearer() = runTest {
        val t = Recording(PaymentHttpResponse(200, orderBody()))
        val result = api(t).createOrder("tok-123", "YEARLY", "req-1") as PaymentResult.Success
        val request = t.requests.single()
        assertEquals("POST", request.method)
        assertTrue(request.url.endsWith("/api/v1/payment/orders"))
        assertEquals("Bearer tok-123", request.headers["Authorization"])
        val body = JSONObject(request.body!!)
        assertEquals(setOf("planCode", "clientRequestId"), body.keys().asSequence().toSet())
        assertEquals("YEARLY", body.getString("planCode"))
        assertEquals("req-1", body.getString("clientRequestId"))
        assertFalse(request.toString().contains("tok-123"))

        val order = result.value
        assertEquals("CV202611090001", order.orderNo)
        assertEquals(1990, order.amountFen)
        assertEquals(PaymentOrderStatus.PENDING_PAYMENT, order.status)
        assertEquals(Instant.parse("2026-11-09T12:30:00Z"), order.expiresAt)
        assertNull(order.paidAt)
        assertNull(order.fulfilledAt)
        assertEquals("URL", order.pay?.kind)
        assertFalse("pay link must be redacted", order.pay.toString().contains("secret"))
    }

    @Test
    fun query_encodesOrderNo_andParsesNullableFields() = runTest {
        val t = Recording(PaymentHttpResponse(200, orderBody(status = "FULFILLED", pay = null)))
        val order = (api(t).queryOrder("tok", "CV 1/2") as PaymentResult.Success).value
        assertTrue(t.requests.single().url.endsWith("/api/v1/payment/orders/CV+1%2F2"))
        assertEquals(PaymentOrderStatus.FULFILLED, order.status)
        assertNull(order.pay)
    }

    @Test
    fun unknownStatus_mapsToUnknown_testKindHasNoValue() = runTest {
        val t = Recording(PaymentHttpResponse(200, orderBody(status = "REFUNDING", pay = """{"kind":"TEST"}""")))
        val order = (api(t).queryOrder("tok", "CV1") as PaymentResult.Success).value
        assertEquals(PaymentOrderStatus.UNKNOWN, order.status)
        assertEquals("TEST", order.pay?.kind)
        assertNull(order.pay?.value)
    }

    @Test
    fun businessErrorCodes_mapToTypedErrors() = runTest {
        val cases = mapOf(
            (401 to 40100) to PaymentError.Unauthorized,
            (401 to 40101) to PaymentError.Unauthorized,
            (401 to 40102) to PaymentError.Unauthorized,
            (409 to 40011) to PaymentError.ClientRequestConflict,
            (409 to 40012) to PaymentError.LifetimeAlreadyActive,
            (409 to 40008) to PaymentError.PlanDisabled,
            (404 to 40401) to PaymentError.PlanNotFound,
            (404 to 40403) to PaymentError.OrderNotFound,
            (500 to 50003) to PaymentError.PaymentConfigMissing,
            (400 to 49999) to PaymentError.Http(400, 49999),
        )
        for ((key, expected) in cases) {
            val (status, code) = key
            val t = Recording(PaymentHttpResponse(status, """{"code":$code,"message":"x","data":null}"""))
            assertEquals("code=$code", PaymentResult.Failure(expected), api(t).createOrder("tok", "YEARLY", "r"))
        }
    }

    @Test
    fun httpFailuresWithoutEnvelope_andNetwork_areTypedAndRetriable() = runTest {
        assertEquals(
            PaymentResult.Failure(PaymentError.Unauthorized),
            api(Recording(PaymentHttpResponse(401, null))).queryOrder("tok", "CV1"),
        )
        val e500 = api(Recording(PaymentHttpResponse(502, "<html>"))).queryOrder("tok", "CV1") as PaymentResult.Failure
        assertEquals(PaymentError.Http(502, null), e500.error)
        assertTrue(e500.error.retriable)

        val timeout = Recording(PaymentHttpResponse(200, "")).also { it.failure = SocketTimeoutException() }
        val t = api(timeout).createOrder("tok", "YEARLY", "r") as PaymentResult.Failure
        assertEquals(PaymentError.Network(timeout = true), t.error)
        assertTrue(t.error.retriable)

        val offline = Recording(PaymentHttpResponse(200, "")).also { it.failure = IOException("down") }
        assertEquals(PaymentError.Network(timeout = false), (api(offline).plans() as PaymentResult.Failure).error)
        assertFalse(PaymentError.ClientRequestConflict.retriable)
        assertFalse(PaymentError.Unauthorized.retriable)
    }

    @Test
    fun releaseRules_rejectCleartextBaseUrl_withoutSending() = runTest {
        val t = Recording(PaymentHttpResponse(200, plansBody))
        val release = HttpPaymentApi("http://10.0.2.2:8081", debug = false, transport = t)
        assertEquals(PaymentResult.Failure(PaymentError.Network(timeout = false)), release.plans())
        assertTrue(t.requests.isEmpty())
        val empty = HttpPaymentApi("", debug = false, transport = t)
        assertEquals(PaymentResult.Failure(PaymentError.Network(timeout = false)), empty.plans())
        // Debug may use the isolated http environment.
        assertTrue(HttpPaymentApi("http://10.0.2.2:8081", debug = true, transport = t).plans() is PaymentResult.Success)
    }

    @Test
    fun realHttp_embeddedServer_roundTripsCreateAndQuery() = runTest {
        val seenBody = AtomicReference<String>()
        val seenAuth = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/payment/orders") { exchange ->
            seenAuth.set(exchange.requestHeaders.getFirst("Authorization"))
            if (exchange.requestMethod == "POST") seenBody.set(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
            val status = if (exchange.requestURI.path.endsWith("/missing")) 404 else 200
            val bytes = (if (status == 404) """{"code":40403,"message":"nf","data":null}""" else orderBody())
                .toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = HttpPaymentApi("http://127.0.0.1:${server.address.port}", debug = true)
            val created = api.createOrder("tok-xyz", "YEARLY", "req-9") as PaymentResult.Success
            assertEquals("CV202611090001", created.value.orderNo)
            assertEquals(setOf("planCode", "clientRequestId"), JSONObject(seenBody.get()).keys().asSequence().toSet())
            assertEquals("Bearer tok-xyz", seenAuth.get())
            assertEquals(PaymentResult.Failure(PaymentError.OrderNotFound), api.queryOrder("tok-xyz", "missing"))
        } finally {
            server.stop(0)
        }
    }
}
