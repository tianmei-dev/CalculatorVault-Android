package com.aurora.calculatorvault.feature.membership.data

import android.os.SystemClock
import java.net.URI
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * HttpURLConnection-backed membership /me client.
 *
 * Uses the same [API_BASE_URL] rules as login (https, or http when [debug] / [localHttpBaseUrl]).
 * Does not retry, does not log tokens, does not clear AccountSession on 401.
 */
class HttpMembershipApi(
    private val baseUrl: String,
    private val debug: Boolean,
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 15_000,
    private val localHttpBaseUrl: String = "",
    private val transport: MembershipHttpTransport = UrlConnectionMembershipHttpTransport(),
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
) : MembershipApi {

    override suspend fun queryMe(scope: MembershipQueryScope): MembershipQueryResult =
        withContext(Dispatchers.IO) {
            coroutineContext.ensureActive()
            require(scope.accountId > 0L)
            require(scope.generation >= 0L)
            require(scope.accessToken.isNotBlank())
            require(scope.accessToken.none { it.isWhitespace() || it.isISOControl() })

            val url = try {
                buildUrl()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Empty / unreachable Base URL must degrade, never crash the process.
                return@withContext MembershipQueryResult.TemporarilyUnavailable(
                    accountId = scope.accountId,
                    generation = scope.generation,
                    reason = MembershipUnavailableReason.NETWORK,
                )
            }
            val request = MembershipHttpRequest(
                url = url,
                headers = membershipHeaders(APP_CODE, scope.accessToken),
                connectTimeoutMillis = connectTimeoutMillis,
                readTimeoutMillis = readTimeoutMillis,
            )
            // Never log request.toString() in production paths that might dump Authorization —
            // MembershipHttpRequest.toString already redacts; still avoid verbose logging here.
            val raw = try {
                transport.execute(request)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                return@withContext MembershipQueryResult.TemporarilyUnavailable(
                    accountId = scope.accountId,
                    generation = scope.generation,
                    reason = isTimeoutOrNetwork(error),
                )
            }
            coroutineContext.ensureActive()
            val confirmedAt = elapsedRealtime()
            if (raw.statusCode !in 200..299) {
                return@withContext MembershipQueryMapper.mapHttpFailure(scope, raw.statusCode, raw.body)
            }
            val body = raw.body
                ?: return@withContext MembershipQueryResult.TemporarilyUnavailable(
                    accountId = scope.accountId,
                    generation = scope.generation,
                    reason = MembershipUnavailableReason.MISSING_DATA,
                )
            MembershipQueryMapper.mapSuccessEnvelope(scope, body, confirmedAt)
        }

    private fun buildUrl(): String {
        val base = URI(baseUrl.trimEnd('/'))
        require(base.host != null && base.userInfo == null && base.query == null && base.fragment == null)
        require(
            base.scheme == "https" ||
                (base.scheme == "http" &&
                    (debug || (localHttpBaseUrl.isNotEmpty() && base == URI(localHttpBaseUrl.trimEnd('/'))))),
        )
        return baseUrl.trimEnd('/') + PATH
    }

    companion object {
        const val PATH: String = "/api/v1/membership/me"
        const val APP_CODE: String = "calculator_vault"
    }
}
