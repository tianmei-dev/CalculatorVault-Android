package com.aurora.calculatorvault.feature.membership.data

import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MembershipQueryMapperTest {

    private val scope = MembershipQueryScope(accountId = 42L, generation = 7L, accessToken = "tok")

    @Test
    fun activeMonthlyParsesToConfirmedActive() {
        val result = success(
            """
            {
              "status":"ACTIVE",
              "planType":"MONTHLY",
              "contributingPlanTypes":["MONTHLY"],
              "isLifetime":false,
              "membershipStartedAt":"2026-01-01T00:00:00Z",
              "membershipExpiresAt":"2026-01-31T00:00:00Z",
              "serverTime":"2026-01-15T00:00:00Z",
              "activeGrantCount":1
            }
            """.trimIndent(),
        )
        val active = result as MembershipQueryResult.ConfirmedActive
        assertEquals("MONTHLY", active.planType)
        assertEquals(listOf("MONTHLY"), active.contributingPlanTypes)
        assertEquals(Instant.parse("2026-01-31T00:00:00Z"), active.expiresAt)
        assertEquals(Instant.parse("2026-01-15T00:00:00Z"), active.serverTime)
        assertEquals(1_000L, active.confirmedAtElapsedRealtime)
        assertEquals(42L, active.accountId)
        assertEquals(7L, active.generation)
    }

    @Test
    fun activeYearlyParsesToConfirmedActive() {
        val result = success(
            """
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
            """.trimIndent(),
        )
        val active = result as MembershipQueryResult.ConfirmedActive
        assertEquals("YEARLY", active.planType)
        assertEquals(false, active.isLifetime)
    }

    @Test
    fun activeLifetimeWithoutExpiry() {
        val result = success(
            """
            {
              "status":"ACTIVE",
              "planType":"LIFETIME",
              "contributingPlanTypes":["LIFETIME"],
              "isLifetime":true,
              "membershipStartedAt":"2026-03-01T12:00:00Z",
              "serverTime":"2026-11-04T11:00:00Z",
              "activeGrantCount":1
            }
            """.trimIndent(),
        )
        val active = result as MembershipQueryResult.ConfirmedActive
        assertTrue(active.isLifetime)
        assertNull(active.expiresAt)
        assertEquals("LIFETIME", active.planType)
    }

    @Test
    fun activeStackedOmitsPlanType() {
        val result = success(
            """
            {
              "status":"ACTIVE",
              "contributingPlanTypes":["MONTHLY","YEARLY"],
              "isLifetime":false,
              "membershipStartedAt":"2026-01-01T00:00:00Z",
              "membershipExpiresAt":"2027-02-01T00:00:00Z",
              "serverTime":"2026-06-01T00:00:00Z",
              "activeGrantCount":2
            }
            """.trimIndent(),
        )
        val active = result as MembershipQueryResult.ConfirmedActive
        assertNull(active.planType)
        assertEquals(listOf("MONTHLY", "YEARLY"), active.contributingPlanTypes)
    }

    @Test
    fun noneIsConfirmedNotEntitled() {
        val result = success(noneBody())
        val not = result as MembershipQueryResult.ConfirmedNotEntitled
        assertEquals("NONE", not.serverStatus)
    }

    @Test
    fun expiredIsConfirmedNotEntitled() {
        val result = success(
            """
            {
              "status":"EXPIRED",
              "planType":"MONTHLY",
              "contributingPlanTypes":["MONTHLY"],
              "isLifetime":false,
              "membershipStartedAt":"2025-10-01T00:00:00Z",
              "membershipExpiresAt":"2025-10-31T00:00:00Z",
              "serverTime":"2026-11-04T11:00:00Z",
              "activeGrantCount":1
            }
            """.trimIndent(),
        )
        assertEquals("EXPIRED", (result as MembershipQueryResult.ConfirmedNotEntitled).serverStatus)
    }

    @Test
    fun revokedIsConfirmedNotEntitled() {
        val result = success(
            """
            {
              "status":"REVOKED",
              "contributingPlanTypes":[],
              "isLifetime":false,
              "serverTime":"2026-11-04T11:00:00Z",
              "activeGrantCount":0
            }
            """.trimIndent(),
        )
        assertEquals("REVOKED", (result as MembershipQueryResult.ConfirmedNotEntitled).serverStatus)
    }

    @Test
    fun activeMissingExpiresAtIsUnavailable() {
        val result = success(
            """
            {
              "status":"ACTIVE",
              "planType":"MONTHLY",
              "contributingPlanTypes":["MONTHLY"],
              "isLifetime":false,
              "serverTime":"2026-01-15T00:00:00Z",
              "activeGrantCount":1
            }
            """.trimIndent(),
        )
        val u = result as MembershipQueryResult.TemporarilyUnavailable
        assertEquals(MembershipUnavailableReason.ACTIVE_MISSING_EXPIRY, u.reason)
    }

    @Test
    fun activeExpiresAtNotAfterServerTimeIsUnavailable() {
        val result = success(
            """
            {
              "status":"ACTIVE",
              "planType":"MONTHLY",
              "contributingPlanTypes":["MONTHLY"],
              "isLifetime":false,
              "membershipExpiresAt":"2026-01-15T00:00:00Z",
              "serverTime":"2026-01-15T00:00:00Z",
              "activeGrantCount":1
            }
            """.trimIndent(),
        )
        val u = result as MembershipQueryResult.TemporarilyUnavailable
        assertEquals(MembershipUnavailableReason.ACTIVE_EXPIRY_NOT_AFTER_SERVER_TIME, u.reason)
    }

    @Test
    fun unknownStatusIsUnavailable() {
        val result = success(
            """
            {
              "status":"PENDING",
              "contributingPlanTypes":[],
              "isLifetime":false,
              "serverTime":"2026-01-01T00:00:00Z",
              "activeGrantCount":0
            }
            """.trimIndent(),
        )
        assertEquals(
            MembershipUnavailableReason.UNKNOWN_STATUS,
            (result as MembershipQueryResult.TemporarilyUnavailable).reason,
        )
    }

    @Test
    fun invalidJsonAndMissingData() {
        val bad = MembershipQueryMapper.mapSuccessEnvelope(scope, "{not-json", 1L)
        assertEquals(
            MembershipUnavailableReason.INVALID_JSON,
            (bad as MembershipQueryResult.TemporarilyUnavailable).reason,
        )
        val missing = MembershipQueryMapper.mapSuccessEnvelope(
            scope,
            """{"code":0,"message":"success"}""",
            1L,
        )
        assertEquals(
            MembershipUnavailableReason.MISSING_DATA,
            (missing as MembershipQueryResult.TemporarilyUnavailable).reason,
        )
    }

    @Test
    fun http401MapsToSessionInvalid() {
        val result = MembershipQueryMapper.mapHttpFailure(
            scope,
            401,
            """{"code":40101,"message":"Session expired","data":null}""",
        )
        val invalid = result as MembershipQueryResult.SessionInvalid
        assertEquals(401, invalid.httpStatus)
        assertEquals(40101, invalid.businessCode)
    }

    @Test
    fun http403MapsToTemporarilyUnavailable() {
        val result = MembershipQueryMapper.mapHttpFailure(
            scope,
            403,
            """{"code":40301,"message":"mismatch","data":null}""",
        )
        assertEquals(
            MembershipUnavailableReason.HTTP_FORBIDDEN,
            (result as MembershipQueryResult.TemporarilyUnavailable).reason,
        )
    }

    @Test
    fun http500MapsToTemporarilyUnavailable() {
        val result = MembershipQueryMapper.mapHttpFailure(
            scope,
            500,
            """{"code":50000,"message":"Internal server error","data":null}""",
        )
        assertEquals(
            MembershipUnavailableReason.HTTP_SERVER_ERROR,
            (result as MembershipQueryResult.TemporarilyUnavailable).reason,
        )
    }

    @Test
    fun isActiveRelativeToServerTimeHelper() {
        val ok = MembershipMeDto(
            status = "ACTIVE",
            planType = "YEARLY",
            contributingPlanTypes = listOf("YEARLY"),
            isLifetime = false,
            membershipStartedAt = Instant.parse("2026-01-01T00:00:00Z"),
            membershipExpiresAt = Instant.parse("2027-01-01T00:00:00Z"),
            serverTime = Instant.parse("2026-06-01T00:00:00Z"),
            activeGrantCount = 1,
        )
        assertTrue(MembershipQueryMapper.isActiveRelativeToServerTime(ok))
        assertTrue(
            !MembershipQueryMapper.isActiveRelativeToServerTime(
                ok.copy(membershipExpiresAt = Instant.parse("2026-06-01T00:00:00Z")),
            ),
        )
    }

    @Test
    fun parseDataDoesNotRequireUserId() {
        val data = JSONObject(noneBody())
        val dto = MembershipQueryMapper.parseData(data)
        assertEquals("NONE", dto.status)
        assertTrue(!data.has("userId"))
    }

    private fun success(dataJson: String): MembershipQueryResult {
        val envelope = """{"code":0,"message":"success","data":$dataJson}"""
        return MembershipQueryMapper.mapSuccessEnvelope(scope, envelope, 1_000L)
    }

    private fun noneBody(): String = """
        {
          "status":"NONE",
          "contributingPlanTypes":[],
          "isLifetime":false,
          "serverTime":"2026-01-01T00:00:00Z",
          "activeGrantCount":0
        }
    """.trimIndent()
}
