package com.aurora.calculatorvault.feature.login.session

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LocalIntegrationHttpTest {
    @Test fun explicitLocalOriginSupportsLoginWithoutDebugOrSensitiveExtraFields() = runTest {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val count=AtomicInteger()
        var path=""; var method=""; var app=""; var body=""; var authorization:String?=null
        server.createContext("/api/v1/auth/wechat/login") { exchange ->
            count.incrementAndGet()
            path=exchange.requestURI.path; method=exchange.requestMethod
            app=exchange.requestHeaders.getFirst("X-App-Code")
            authorization=exchange.requestHeaders.getFirst("Authorization")
            body=exchange.requestBody.bufferedReader().readText()
            val reply="""{"code":0,"message":"success","data":{"accessToken":"synthetic-token","tokenType":"Bearer","expiresAt":"2099-01-01T00:00:00Z","userId":7,"appCode":"calculator_vault"}}""".toByteArray()
            exchange.sendResponseHeaders(200,reply.size.toLong())
            exchange.responseBody.use { it.write(reply) }
        }
        server.start()
        try {
            val origin="http://127.0.0.1:${server.address.port}"
            val api=HttpAuthApi(origin,false,localHttpBaseUrl=origin)
            assertEquals(7L,api.login("synthetic-code").account.userId)
            assertEquals(1,count.get()); assertEquals("POST",method)
            assertEquals("/api/v1/auth/wechat/login",path)
            assertEquals("calculator_vault",app); assertNull(authorization)
            val json=JSONObject(body)
            assertEquals(setOf("code"),json.keys().asSequence().toSet())
            assertEquals("synthetic-code",json.getString("code"))
            // The regular non-debug client has no exception, even for the same reachable origin.
            try { HttpAuthApi(origin,false).login("synthetic-code"); fail("HTTP must be rejected") }
            catch (_:IllegalArgumentException) { }
            assertEquals(1,count.get())
        } finally { server.stop(0) }
    }

    @Test fun localExceptionCannotAuthorizeOtherHostsPortsOrPaths() = runTest {
        val allowed="http://192.168.0.107:8080"
        for (url in listOf("http://192.168.0.108:8080", "http://192.168.0.107:8081",
            "http://192.168.0.107:8080/other", "http://name:password@192.168.0.107:8080",
            "http://192.168.0.107:8080?redirect=other", "http://192.168.0.107:8080#fragment")) {
            try { HttpAuthApi(url,false,localHttpBaseUrl=allowed).login("synthetic-code"); fail("Origin must be rejected") }
            catch (_:IllegalArgumentException) { }
        }
    }
}
