package com.aurora.calculatorvault.feature.login.session

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionCredential(val token: String, val expiresAt: Instant) {
    override fun toString() = "SessionCredential([REDACTED])"
}
data class ServerAccount(val userId: Long)
class LoginResult(val credential: SessionCredential, val account: ServerAccount)
class AuthFailure(val invalidSession: Boolean = false) : Exception("Account request failed")
interface AuthApi {
    suspend fun login(code: String): LoginResult
    suspend fun me(token: String): ServerAccount
    suspend fun logout(token: String)
}

/** No redirects, logging, automatic retries or cookies. Each code is submitted once. */
class HttpAuthApi(
    private val baseUrl: String, private val debug: Boolean,
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 15_000,
) : AuthApi, com.aurora.calculatorvault.feature.trial.TrialApi {
    private suspend fun request(path: String, token: String? = null, body: JSONObject? = null,
                                post: Boolean = false, trial: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        val base = URI(baseUrl)
        require(base.host != null && base.userInfo == null && base.query == null && base.fragment == null)
        require(base.scheme == "https" || (debug && base.scheme == "http"))
        val connection = URI(baseUrl.trimEnd('/') + (if (trial) "/api/v1/trial/device/" else "/api/v1/auth/") + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.requestMethod = if (post) "POST" else "GET"
            connection.setRequestProperty("X-App-Code", "calculator_vault")
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.toString().toByteArray(Charsets.UTF_8).size)
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            } else if (post) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(0)
                connection.outputStream.use { }
            }
            val status = connection.responseCode
            if (status == 429 && trial) throw com.aurora.calculatorvault.feature.trial.TrialRateLimited()
            if (status == 401) throw AuthFailure(true)
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readBounded() } ?: throw AuthFailure()
            val envelope = JSONObject(raw)
            val code = envelope.getInt("code")
            if (code != 0 || status !in 200..299) throw AuthFailure(
                code in setOf(40100, 40101, 40102, 40300, 40301, 40001, 40002))
            envelope
        } finally { connection.disconnect() }
    }
    override suspend fun activate(deviceKey: String): com.aurora.calculatorvault.feature.trial.TrialReply {
        val data = request("activate", body = JSONObject().put("deviceKey", deviceKey), post = true, trial = true).getJSONObject("data")
        return com.aurora.calculatorvault.feature.trial.TrialReply(data.getString("status"),
            Instant.parse(data.getString("trialStartedAt")).toEpochMilli(),
            Instant.parse(data.getString("trialExpiresAt")).toEpochMilli(),
            Instant.parse(data.getString("serverTime")).toEpochMilli(), Math.multiplyExact(data.getLong("remainingSeconds"), 1000))
    }
    private fun java.io.Reader.readBounded(): String {
        val buffer = CharArray(4096)
        val result = StringBuilder()
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(result.length + count <= 65536)
            result.append(buffer, 0, count)
        }
        return result.toString()
    }
    private fun account(data: JSONObject): ServerAccount {
        if (data.getString("appCode") != "calculator_vault") throw AuthFailure(true)
        val rawId = data.get("userId")
        require(rawId is Number)
        val id = rawId.toString().toLongOrNull() ?: throw AuthFailure()
        require(id > 0)
        return ServerAccount(id)
    }
    override suspend fun login(code: String): LoginResult {
        val data = request("wechat/login", body = JSONObject().put("code", code), post = true).getJSONObject("data")
        val user = account(data)
        require(data.getString("tokenType") == "Bearer")
        val token = data.get("accessToken") as? String ?: throw AuthFailure()
        require(token.isNotBlank() && token.none { it.isWhitespace() || it.isISOControl() })
        val expires = Instant.parse(data.getString("expiresAt"))
        require(expires.isAfter(Instant.now()))
        return LoginResult(SessionCredential(token, expires), user)
    }
    override suspend fun me(token: String): ServerAccount {
        val data = request("me", token).getJSONObject("data")
        if (data.getString("status") != "ACTIVE") throw AuthFailure(true)
        return account(data)
    }
    override suspend fun logout(token: String) { request("logout", token, post = true) }
}
