package com.aurora.calculatorvault.feature.settings.presentation

import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.feature.membership.domain.VipPlan
import java.time.Duration
import java.time.LocalDate

/** Display data only: never used to authorize protected features or persist membership. */
sealed interface AccountStatus {
    data object Guest : AccountStatus
    data class WeChatLoggedIn(val nickname: String? = null) : AccountStatus {
        val displayNickname: String? get() = nickname?.trim()?.takeIf { it.isNotEmpty() }
    }
}

sealed interface MembershipStatus {
    val planType: VipPlan? get() = null
    val expiresAt: LocalDate? get() = null
    val trialRemaining: Duration? get() = null

    data object NotSubscribed : MembershipStatus
    data class TrialActive(override val trialRemaining: Duration) : MembershipStatus {
        init { require(!trialRemaining.isNegative) }
    }
    data object TrialExpired : MembershipStatus
    /**
     * Time-limited VIP. [planType] null means stacked / unknown → generic「VIP会员」.
     * Lifetime must use [VipPermanent], never [VipPlan.Lifetime] here.
     */
    data class VipActive(
        override val planType: VipPlan? = null,
        override val expiresAt: LocalDate,
    ) : MembershipStatus {
        init { require(planType != VipPlan.Lifetime) }
    }
    data object VipPermanent : MembershipStatus {
        override val planType = VipPlan.Lifetime
    }
}

data class AccountUiState(
    val account: AccountStatus = AccountStatus.Guest,
    val accountMembership: MembershipStatus = MembershipStatus.NotSubscribed,
    // Independent device trial snapshot; no clock, device ID or persistence in this task.
    val deviceTrial: MembershipStatus = MembershipStatus.NotSubscribed,
    /** Signed-in membership sync hint (同步中 / 暂时无法同步); never authorizes. */
    val membershipSyncHint: String? = null,
    val membershipRefreshing: Boolean = false,
) {
    init {
        require(deviceTrial is MembershipStatus.TrialActive ||
            deviceTrial == MembershipStatus.TrialExpired || deviceTrial == MembershipStatus.NotSubscribed)
    }

    /**
     * Display priority aligned with product entitlement:
     * account VIP (signed-in only) > device trial > free/not subscribed.
     * Guests never surface a stale account VIP; signed-in users may still show an active device trial
     * when no real VIP claim is present.
     */
    val visibleMembership: MembershipStatus
        get() {
            val accountVip = account != AccountStatus.Guest &&
                (accountMembership is MembershipStatus.VipActive ||
                    accountMembership is MembershipStatus.VipPermanent)
            if (accountVip) return accountMembership
            if (deviceTrial is MembershipStatus.TrialActive ||
                deviceTrial is MembershipStatus.TrialExpired
            ) {
                return deviceTrial
            }
            return if (account == AccountStatus.Guest) deviceTrial else accountMembership
        }
}

internal data class MembershipText(val title: Int, val subtitle: Int, val arguments: List<Any> = emptyList())

internal fun membershipText(state: AccountUiState): MembershipText = when (val status = state.visibleMembership) {
    is MembershipStatus.TrialActive -> MembershipText(
        R.string.account_trial_active, R.string.account_trial_remaining,
        listOf(status.trialRemaining.toDays(), status.trialRemaining.toHours() % 24),
    )
    MembershipStatus.TrialExpired -> MembershipText(R.string.account_vip_inactive, R.string.account_trial_expired_description)
    MembershipStatus.NotSubscribed -> if (state.account == AccountStatus.Guest) {
        MembershipText(R.string.account_vip_inactive, R.string.account_trial_pending)
    } else {
        MembershipText(R.string.account_open_vip, R.string.account_open_vip_description)
    }
    is MembershipStatus.VipActive -> MembershipText(
        title = when (status.planType) {
            VipPlan.Monthly -> R.string.account_vip_monthly
            VipPlan.Annual -> R.string.account_vip_annual
            null -> R.string.vip_title
            VipPlan.Lifetime -> R.string.vip_title
        },
        subtitle = R.string.account_vip_expires,
        arguments = listOf(status.expiresAt.toString()),
    )
    MembershipStatus.VipPermanent -> MembershipText(R.string.account_vip_permanent, R.string.vip_duration_lifetime)
}
