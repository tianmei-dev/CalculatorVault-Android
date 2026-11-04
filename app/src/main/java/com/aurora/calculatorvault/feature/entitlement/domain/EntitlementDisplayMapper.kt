package com.aurora.calculatorvault.feature.entitlement.domain

import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.membership.data.MembershipSyncUi
import com.aurora.calculatorvault.feature.membership.domain.VipPlan
import com.aurora.calculatorvault.feature.settings.presentation.AccountStatus
import com.aurora.calculatorvault.feature.settings.presentation.AccountUiState
import com.aurora.calculatorvault.feature.settings.presentation.MembershipStatus
import com.aurora.calculatorvault.feature.trial.DeviceTrialState
import com.aurora.calculatorvault.feature.trial.TrialStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Maps unified entitlement + session into settings display models.
 * Display only — never used as an authorization input.
 */
object EntitlementDisplayMapper {

    fun toAccountUiState(
        session: AccountSession,
        entitlement: EntitlementState,
        trial: DeviceTrialState = DeviceTrialState(entitlement.trialStatus, entitlement.trialRemainingMillis),
        sync: MembershipSyncUi = MembershipSyncUi.Idle,
    ): AccountUiState {
        val account = when (session) {
            is AccountSession.Authenticated -> AccountStatus.WeChatLoggedIn()
            else -> AccountStatus.Guest
        }
        return AccountUiState(
            account = account,
            accountMembership = toAccountMembership(entitlement),
            deviceTrial = toDeviceTrialMembership(trial),
            membershipSyncHint = syncHint(session, entitlement, sync),
            membershipRefreshing = sync is MembershipSyncUi.Syncing &&
                session is AccountSession.Authenticated,
        )
    }

    fun toAccountMembership(entitlement: EntitlementState): MembershipStatus {
        val vip = entitlement.source as? EntitlementSource.Vip ?: return MembershipStatus.NotSubscribed
        if (vip.permanent || vip.expiresAtEpochMillis == null) {
            return MembershipStatus.VipPermanent
        }
        val expiresAt = Instant.ofEpochMilli(vip.expiresAtEpochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val plan = resolveDisplayPlan(vip.planType, vip.contributingPlanTypes, permanent = false)
        return MembershipStatus.VipActive(planType = plan, expiresAt = expiresAt)
    }

    /**
     * Maps server plan codes to purchase-catalog [VipPlan] for display.
     * YEARLY → Annual (年卡). Multiple contributing plans → null (generic VIP会员).
     */
    fun resolveDisplayPlan(
        planType: String?,
        contributingPlanTypes: List<String>,
        permanent: Boolean,
    ): VipPlan? {
        if (permanent) return VipPlan.Lifetime
        val distinct = contributingPlanTypes
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (distinct.size > 1) return null
        val code = when {
            distinct.size == 1 -> distinct[0]
            else -> planType?.trim()?.uppercase().orEmpty()
        }
        return when (code) {
            "MONTHLY" -> VipPlan.Monthly
            "YEARLY", "ANNUAL" -> VipPlan.Annual
            "LIFETIME" -> VipPlan.Lifetime
            else -> null
        }
    }

    /**
     * Trial card mapping:
     * - granting statuses with remaining > 0 → TrialActive (including PENDING_SYNC / TEMP unavailable)
     * - EXPIRED → TrialExpired
     * - untrustworthy / not initialized / zero remaining → NotSubscribed (sessionMessage carries detail)
     */
    fun toDeviceTrialMembership(trial: DeviceTrialState): MembershipStatus = when {
        trial.status == TrialStatus.EXPIRED -> MembershipStatus.TrialExpired
        trial.status.grantsTrialCapability() && trial.remainingMillis > 0L ->
            MembershipStatus.TrialActive(Duration.ofMillis(trial.remainingMillis))
        else -> MembershipStatus.NotSubscribed
    }

    fun trialSessionMessage(trial: DeviceTrialState): String = when (trial.status) {
        TrialStatus.ACTIVE ->
            "设备试用中：剩余 ${trial.remainingMillis / 3_600_000} 小时 ${(trial.remainingMillis / 60_000) % 60} 分钟"
        TrialStatus.EXPIRED ->
            "设备试用已到期"
        TrialStatus.TEMPORARILY_UNAVAILABLE ->
            "设备试用暂时无法验证" + remainingSuffix(trial.remainingMillis)
        TrialStatus.PENDING_SYNC ->
            "设备试用等待联网验证" + remainingSuffix(trial.remainingMillis)
        TrialStatus.VERIFICATION_REQUIRED ->
            "设备试用需重新验证后才能继续计时"
        TrialStatus.NOT_INITIALIZED ->
            "设备试用尚未就绪"
    }

    private fun syncHint(
        session: AccountSession,
        entitlement: EntitlementState,
        sync: MembershipSyncUi,
    ): String? {
        if (session !is AccountSession.Authenticated) return null
        val hasVip = entitlement.source is EntitlementSource.Vip
        return when (sync) {
            MembershipSyncUi.Syncing ->
                if (!hasVip) "会员状态同步中" else null
            MembershipSyncUi.Failed ->
                "会员状态暂时无法同步"
            MembershipSyncUi.Anomaly ->
                "会员状态暂时无法同步"
            MembershipSyncUi.Idle -> null
        }
    }

    private fun remainingSuffix(remainingMillis: Long): String {
        if (remainingMillis <= 0L) return ""
        return "，本地剩余 ${remainingMillis / 3_600_000} 小时 ${(remainingMillis / 60_000) % 60} 分钟"
    }
}
