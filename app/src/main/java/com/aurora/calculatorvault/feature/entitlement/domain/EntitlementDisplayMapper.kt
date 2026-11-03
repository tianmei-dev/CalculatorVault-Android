package com.aurora.calculatorvault.feature.entitlement.domain

import com.aurora.calculatorvault.feature.login.session.AccountSession
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
    ): AccountUiState {
        val account = when (session) {
            is AccountSession.Authenticated -> AccountStatus.WeChatLoggedIn()
            else -> AccountStatus.Guest
        }
        return AccountUiState(
            account = account,
            accountMembership = toAccountMembership(entitlement),
            deviceTrial = toDeviceTrialMembership(trial),
        )
    }

    fun toAccountMembership(entitlement: EntitlementState): MembershipStatus {
        val vip = entitlement.source as? EntitlementSource.Vip ?: return MembershipStatus.NotSubscribed
        return if (vip.permanent || vip.expiresAtEpochMillis == null) {
            MembershipStatus.VipPermanent
        } else {
            // Plan type is display-only; Monthly is a stable label until real catalog wiring exists.
            MembershipStatus.VipActive(
                planType = com.aurora.calculatorvault.feature.membership.domain.VipPlan.Monthly,
                expiresAt = Instant.ofEpochMilli(vip.expiresAtEpochMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate(),
            )
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

    private fun remainingSuffix(remainingMillis: Long): String {
        if (remainingMillis <= 0L) return ""
        return "，本地剩余 ${remainingMillis / 3_600_000} 小时 ${(remainingMillis / 60_000) % 60} 分钟"
    }
}
