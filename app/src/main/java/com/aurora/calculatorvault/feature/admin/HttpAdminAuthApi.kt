package com.aurora.calculatorvault.feature.admin

import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface AdminAuthApi {
    suspend fun login(username: String, password: String): AdminLoginSuccess
    suspend fun me(token: String): AdminMeResult
    suspend fun logout(token: String)
}

data class AdminMeResult(
    val username: String,
    val appCode: String,
    val role: String,
)

/**
 * Admin auth against Aurora `/api/v1/auth/admin/` endpoints.
 * Token is only used for admin endpoints — never for WeChat or payment APIs.
 */
class HttpAdminAuthApi(
    private val baseUrl: String,
    private val debug: Boolean,
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 15_000,
    private val localHttpBaseUrl: String = "",
) : AdminAuthApi {

    private suspend fun request(
        path: String,
        token: String? = null,
        body: JSONObject? = null,
        post: Boolean = false,
    ): JSONObject = withContext(Dispatchers.IO) {
        val base = URI(baseUrl)
        require(base.host != null && base.userInfo == null && base.query == null && base.fragment == null)
        require(
            base.scheme == "https" ||
                (base.scheme == "http" && (debug || (localHttpBaseUrl.isNotEmpty() && base == URI(localHttpBaseUrl)))),
        )
        val connection = URI(
            baseUrl.trimEnd('/') + "/api/v1/auth/admin/" + path,
        ).toURL().openConnection() as HttpURLConnection
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
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(bytes) }
            } else if (post) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(0)
                connection.outputStream.use { }
            }
            val status = connection.responseCode
            if (status == 401) throw AdminAuthFailure(invalidSession = true, code = 40100)
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readBounded() }
                ?: throw AdminAuthFailure()
            val envelope = JSONObject(raw)
            val code = envelope.getInt("code")
            if (code != 0 || status !in 200..299) {
                throw AdminAuthFailure(
                    invalidSession = code in setOf(40100, 40101, 40102, 40300, 40301),
                    code = code,
                )
            }
            envelope
        } finally {
            connection.disconnect()
        }
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

    override suspend fun login(username: String, password: String): AdminLoginSuccess {
        val data = request(
            path = "login",
            body = JSONObject().put("username", username).put("password", password),
            post = true,
        ).getJSONObject("data")
        if (data.getString("appCode") != "calculator_vault") throw AdminAuthFailure(true)
        require(data.getString("tokenType") == "Bearer")
        val token = data.get("accessToken") as? String ?: throw AdminAuthFailure()
        require(token.isNotBlank() && token.none { it.isWhitespace() || it.isISOControl() })
        val expires = Instant.parse(data.getString("expiresAt"))
        require(expires.isAfter(Instant.now()))
        val name = data.getString("username").ifBlank { username }
        return AdminLoginSuccess(
            credential = AdminCredential(token = token, expiresAt = expires, username = name),
            appCode = data.getString("appCode"),
        )
    }

    override suspend fun me(token: String): AdminMeResult {
        val data = request("me", token = token).getJSONObject("data")
        if (data.getString("appCode") != "calculator_vault") throw AdminAuthFailure(true)
        val username = data.getString("username")
        require(username.isNotBlank())
        return AdminMeResult(
            username = username,
            appCode = data.getString("appCode"),
            role = data.optString("role"),
        )
    }

    override suspend fun logout(token: String) {
        request("logout", token = token, post = true)
    }
}
