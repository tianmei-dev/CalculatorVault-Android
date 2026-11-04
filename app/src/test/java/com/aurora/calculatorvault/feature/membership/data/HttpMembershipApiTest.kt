package com.aurora.calculatorvault.feature.membership.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HttpMembershipApiTest {

    private val scope = MembershipQueryScope(
        accountId = 9L,
        generation = 3L,
        accessToken = "secret-token-value",
    )

    @Test
    fun headersAreCorrectAndOmitUserId() = runBlocking {
        val captured = AtomicReference<MembershipHttpRequest>()
        val api = api(
            transport = MembershipHttpTransport { request ->
                captured.set(request)
                okBody(activeYearlyData())
            },
        )
        api.queryMe(scope)
        val request = captured.get()
        assertTrue(request.url.endsWith("/api/v1/membership/me"))
        assertEquals("calculator_vault", request.headers["X-App-Code"])
        assertEquals("application/json", request.headers["Accept"])
        assertTrue(request.authorizationLooksLikeBearerToken())
        assertFalse(request.headers.keys.any { it.equals("userId", ignoreCase = true) })
        assertFalse(request.url.contains("userId"))
        assertEquals(10_000, request.connectTimeoutMillis)
        assertEquals(15_000, request.readTimeoutMillis)
    }

    @Test
    fun tokenNeverAppearsInRequestToString() {
        val request = MembershipHttpRequest(
            url = "https://example.test/api/v1/membership/me",
            headers = membershipHeaders("calculator_vault", "super-secret-token"),
            connectTimeoutMillis = 10_000,
            readTimeoutMillis = 15_000,
        )
        val text = request.toString()
        assertFalse(text.contains("super-secret-token"))
        assertTrue(text.contains("[REDACTED]"))
        assertFalse(scope.toString().contains("secret-token-value"))
    }

    @Test
    fun recordsServerTimeAndElapsedRealtimeOnSuccess() = runBlocking {
        val api = api(
            transport = MembershipHttpTransport { okBody(activeYearlyData()) },
            elapsed = { 55_555L },
        )
        val result = api.queryMe(scope) as MembershipQueryResult.ConfirmedActive
        assertEquals(55_555L, result.confirmedAtElapsedRealtime)
        assertEquals(java.time.Instant.parse("2026-01-01T00:00:00Z"), result.serverTime)
    }

    @Test
    fun networkTimeoutMapsToTemporarilyUnavailable() = runBlocking {
        val api = api(
            transport = MembershipHttpTransport { throw SocketTimeoutException("slow") },
        )
        val result = api.queryMe(scope) as MembershipQueryResult.TemporarilyUnavailable
        assertEquals(MembershipUnavailableReason.TIMEOUT, result.reason)
    }

    @Test
    fun networkDisconnectMapsToTemporarilyUnavailable() = runBlocking {
        val api = api(
            transport = MembershipHttpTransport { throw IOException("unreachable") },
        )
        val result = api.queryMe(scope) as MembershipQueryResult.TemporarilyUnavailable
        assertEquals(MembershipUnavailableReason.NETWORK, result.reason)
    }

    @Test
    fun http401ViaTransport() = runBlocking {
        val api = api(
            transport = MembershipHttpTransport {
                MembershipHttpRawResponse(
                    401,
                    """{"code":40100,"message":"Unauthorized","data":null}""",
                )
            },
        )
        val result = api.queryMe(scope) as MembershipQueryResult.SessionInvalid
        assertEquals(401, result.httpStatus)
        assertEquals(40100, result.businessCode)
    }

    @Test
    fun http403And500ViaTransport() = runBlocking {
        val forbidden = api(
            transport = MembershipHttpTransport {
                MembershipHttpRawResponse(403, """{"code":40301,"message":"x","data":null}""")
            },
        ).queryMe(scope) as MembershipQueryResult.TemporarilyUnavailable
        assertEquals(MembershipUnavailableReason.HTTP_FORBIDDEN, forbidden.reason)

        val server = api(
            transport = MembershipHttpTransport {
                MembershipHttpRawResponse(500, """{"code":50000,"message":"x","data":null}""")
            },
        ).queryMe(scope) as MembershipQueryResult.TemporarilyUnavailable
        assertEquals(MembershipUnavailableReason.HTTP_SERVER_ERROR, server.reason)
    }

    @Test
    fun errorStreamBodyConsumedForNonSuccess() = runBlocking {
        var closed = false
        val api = api(
            transport = MembershipHttpTransport {
                closed = true
                MembershipHttpRawResponse(
                    403,
                    """{"code":40301,"message":"mismatch","data":null}""",
                )
            },
        )
        api.queryMe(scope)
        assertTrue(closed)
    }

    @Test
    fun cancellationIsNotMappedToNetworkError() = runBlocking {
        val started = AtomicBoolean(false)
        val release = AtomicBoolean(false)
        val api = api(
            transport = MembershipHttpTransport {
                started.set(true)
                val deadline = System.nanoTime() + TimeUnit_SECONDS_5
                while (!release.get()) {
                    if (System.nanoTime() > deadline) break
                    Thread.sleep(10)
                }
                okBody(activeYearlyData())
            },
        )
        val deferred = async(Dispatchers.IO) { api.queryMe(scope) }
        withTimeout(5_000) {
            while (!started.get()) delay(10)
        }
        deferred.cancel(CancellationException("user-cancel"))
        release.set(true)
        try {
            deferred.await()
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // expected — not TemporarilyUnavailable
        }
    }

    private companion object {
        const val TimeUnit_SECONDS_5: Long = 5_000_000_000L
    }

    @Test
    fun cancellationExceptionFromTransportPropagates() = runBlocking {
        val api = api(
            transport = MembershipHttpTransport {
                throw CancellationException("transport-cancel")
            },
        )
        try {
            api.queryMe(scope)
            fail("expected CancellationException")
        } catch (_: CancellationException) {
            // ok
        }
    }

    @Test
    fun endToEndNoneStatus() = runBlocking {
        val api = api(
            transport = MembershipHttpTransport {
                okBody(
                    """
                    {
                      "status":"NONE",
                      "contributingPlanTypes":[],
                      "isLifetime":false,
                      "serverTime":"2026-01-01T00:00:00Z",
                      "activeGrantCount":0
                    }
                    """.trimIndent(),
                )
            },
        )
        val result = api.queryMe(scope) as MembershipQueryResult.ConfirmedNotEntitled
        assertEquals("NONE", result.serverStatus)
    }

    private fun api(
        transport: MembershipHttpTransport,
        elapsed: () -> Long = { 1L },
    ): HttpMembershipApi =
        HttpMembershipApi(
            baseUrl = "https://membership.test",
            debug = false,
            transport = transport,
            elapsedRealtime = elapsed,
        )

    private fun okBody(dataJson: String): MembershipHttpRawResponse =
        MembershipHttpRawResponse(
            statusCode = 200,
            body = """{"code":0,"message":"success","data":$dataJson}""",
        )

    private fun activeYearlyData(): String = """
        {
          "status":"ACTIVE",
          "planType":"YEARLY",
          "contributingPlanTypes":["YEARLY"],
          "isLifetime":false,
          "membershipStartedAt":"2026-01-01T00:00:00Z",
          "membershipExpiresAt":"2027-01-01T00:00:00Z",
          "serverTime":"2026-01-01T00:00:00Z",
          "activeGrantCount":1
        }
    """.trimIndent()
}
