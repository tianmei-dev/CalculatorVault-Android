package com.aurora.calculatorvault.feature.membership.data

import java.time.Instant
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Pure parse + map helpers for GET /api/v1/membership/me (I3.4.2 V1).
 * No sticky cache; only records time bases for a future provider.
 */
object MembershipQueryMapper {

    private val KNOWN_STATUSES = setOf("NONE", "ACTIVE", "EXPIRED", "REVOKED")
    private val SESSION_INVALID_CODES = setOf(40100, 40101, 40102)

    fun mapHttpFailure(
        scope: MembershipQueryScope,
        httpStatus: Int,
        body: String?,
    ): MembershipQueryResult {
        if (httpStatus == 401) {
            val code = peekBusinessCode(body)
            return MembershipQueryResult.SessionInvalid(
                accountId = scope.accountId,
                generation = scope.generation,
                httpStatus = 401,
                businessCode = code,
            )
        }
        val code = peekBusinessCode(body)
        if (code != null && code in SESSION_INVALID_CODES) {
            return MembershipQueryResult.SessionInvalid(
                accountId = scope.accountId,
                generation = scope.generation,
                httpStatus = httpStatus,
                businessCode = code,
            )
        }
        val reason = when {
            httpStatus == 403 -> MembershipUnavailableReason.HTTP_FORBIDDEN
            httpStatus in 500..599 -> MembershipUnavailableReason.HTTP_SERVER_ERROR
            else -> MembershipUnavailableReason.HTTP_OTHER
        }
        return MembershipQueryResult.TemporarilyUnavailable(
            accountId = scope.accountId,
            generation = scope.generation,
            reason = reason,
        )
    }

    fun mapSuccessEnvelope(
        scope: MembershipQueryScope,
        rawBody: String,
        confirmedAtElapsedRealtime: Long,
    ): MembershipQueryResult {
        val envelope = try {
            JSONObject(rawBody)
        } catch (_: JSONException) {
            return unavailable(scope, MembershipUnavailableReason.INVALID_JSON)
        }
        val code = try {
            envelope.getInt("code")
        } catch (_: JSONException) {
            return unavailable(scope, MembershipUnavailableReason.INVALID_JSON)
        }
        if (code in SESSION_INVALID_CODES) {
            return MembershipQueryResult.SessionInvalid(
                accountId = scope.accountId,
                generation = scope.generation,
                httpStatus = 200,
                businessCode = code,
            )
        }
        if (code != 0) {
            return unavailable(scope, MembershipUnavailableReason.BUSINESS_ERROR)
        }
        if (!envelope.has("data") || envelope.isNull("data")) {
            return unavailable(scope, MembershipUnavailableReason.MISSING_DATA)
        }
        val data = try {
            envelope.getJSONObject("data")
        } catch (_: JSONException) {
            return unavailable(scope, MembershipUnavailableReason.MISSING_DATA)
        }
        val dto = try {
            parseData(data)
        } catch (_: Exception) {
            return unavailable(scope, MembershipUnavailableReason.INVALID_JSON)
        }
        return mapDto(scope, dto, confirmedAtElapsedRealtime)
    }

    fun mapDto(
        scope: MembershipQueryScope,
        dto: MembershipMeDto,
        confirmedAtElapsedRealtime: Long,
    ): MembershipQueryResult {
        if (dto.status !in KNOWN_STATUSES) {
            return unavailable(scope, MembershipUnavailableReason.UNKNOWN_STATUS)
        }
        return when (dto.status) {
            "NONE", "EXPIRED", "REVOKED" -> MembershipQueryResult.ConfirmedNotEntitled(
                accountId = scope.accountId,
                generation = scope.generation,
                serverStatus = dto.status,
                serverTime = dto.serverTime,
                confirmedAtElapsedRealtime = confirmedAtElapsedRealtime,
            )
            "ACTIVE" -> mapActive(scope, dto, confirmedAtElapsedRealtime)
            else -> unavailable(scope, MembershipUnavailableReason.UNKNOWN_STATUS)
        }
    }

    /**
     * Sticky helper for I3.4.3-B: limited VIP must still be after [serverTime];
     * lifetime has null expiry. Does not implement the 24h window itself.
     */
    fun isActiveRelativeToServerTime(dto: MembershipMeDto): Boolean {
        if (dto.status != "ACTIVE") return false
        if (dto.isLifetime) return true
        val expires = dto.membershipExpiresAt ?: return false
        return expires.isAfter(dto.serverTime)
    }

    private fun mapActive(
        scope: MembershipQueryScope,
        dto: MembershipMeDto,
        confirmedAtElapsedRealtime: Long,
    ): MembershipQueryResult {
        if (dto.isLifetime) {
            return MembershipQueryResult.ConfirmedActive(
                accountId = scope.accountId,
                generation = scope.generation,
                isLifetime = true,
                expiresAt = null,
                startedAt = dto.membershipStartedAt,
                planType = dto.planType,
                contributingPlanTypes = dto.contributingPlanTypes,
                serverTime = dto.serverTime,
                activeGrantCount = dto.activeGrantCount,
                confirmedAtElapsedRealtime = confirmedAtElapsedRealtime,
            )
        }
        val expires = dto.membershipExpiresAt
            ?: return unavailable(scope, MembershipUnavailableReason.ACTIVE_MISSING_EXPIRY)
        if (!expires.isAfter(dto.serverTime)) {
            return unavailable(scope, MembershipUnavailableReason.ACTIVE_EXPIRY_NOT_AFTER_SERVER_TIME)
        }
        return MembershipQueryResult.ConfirmedActive(
            accountId = scope.accountId,
            generation = scope.generation,
            isLifetime = false,
            expiresAt = expires,
            startedAt = dto.membershipStartedAt,
            planType = dto.planType,
            contributingPlanTypes = dto.contributingPlanTypes,
            serverTime = dto.serverTime,
            activeGrantCount = dto.activeGrantCount,
            confirmedAtElapsedRealtime = confirmedAtElapsedRealtime,
        )
    }

    fun parseData(data: JSONObject): MembershipMeDto {
        val status = data.getString("status")
        require(status.isNotBlank())
        val planType = if (data.has("planType") && !data.isNull("planType")) {
            data.getString("planType").takeIf { it.isNotBlank() }
        } else {
            null
        }
        val contributing = parseStringList(data.optJSONArray("contributingPlanTypes"))
        val isLifetime = data.getBoolean("isLifetime")
        val started = optionalInstant(data, "membershipStartedAt")
        val expires = optionalInstant(data, "membershipExpiresAt")
        val serverTime = Instant.parse(data.getString("serverTime"))
        val activeGrantCount = data.getInt("activeGrantCount")
        require(activeGrantCount >= 0)
        return MembershipMeDto(
            status = status,
            planType = planType,
            contributingPlanTypes = contributing,
            isLifetime = isLifetime,
            membershipStartedAt = started,
            membershipExpiresAt = expires,
            serverTime = serverTime,
            activeGrantCount = activeGrantCount,
        )
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val value = array.getString(i)
            require(value.isNotBlank())
            out += value
        }
        return out
    }

    private fun optionalInstant(data: JSONObject, key: String): Instant? {
        if (!data.has(key) || data.isNull(key)) return null
        return Instant.parse(data.getString(key))
    }

    private fun peekBusinessCode(body: String?): Int? {
        if (body.isNullOrBlank()) return null
        return try {
            JSONObject(body).getInt("code")
        } catch (_: Exception) {
            null
        }
    }

    private fun unavailable(
        scope: MembershipQueryScope,
        reason: MembershipUnavailableReason,
    ): MembershipQueryResult.TemporarilyUnavailable =
        MembershipQueryResult.TemporarilyUnavailable(
            accountId = scope.accountId,
            generation = scope.generation,
            reason = reason,
        )
}
