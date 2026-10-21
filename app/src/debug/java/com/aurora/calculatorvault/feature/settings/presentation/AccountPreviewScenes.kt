package com.aurora.calculatorvault.feature.settings.presentation

import com.aurora.calculatorvault.feature.membership.domain.VipPlan
import java.time.Duration
import java.time.LocalDate

/** Debug-only fixtures, never connected to the app's navigation or feature authorization. */
object AccountPreviewScenes {
    data class Scene(val name: String, val state: AccountUiState)

    val scenes: List<Scene> = listOf(
        Scene("游客试用中", AccountUiState(deviceTrial = MembershipStatus.TrialActive(Duration.ofHours(53)))),
        Scene("游客试用到期", AccountUiState(deviceTrial = MembershipStatus.TrialExpired)),
        Scene("微信非VIP", AccountUiState(account = AccountStatus.WeChatLoggedIn())),
        Scene("微信月卡VIP", AccountUiState(
            account = AccountStatus.WeChatLoggedIn("月卡用户"),
            accountMembership = MembershipStatus.VipActive(VipPlan.Monthly, LocalDate.of(2026, 11, 20)),
        )),
        Scene("微信年卡VIP", AccountUiState(
            account = AccountStatus.WeChatLoggedIn("年卡用户"),
            accountMembership = MembershipStatus.VipActive(VipPlan.Annual, LocalDate.of(2027, 10, 21)),
        )),
        Scene("微信永久VIP", AccountUiState(
            account = AccountStatus.WeChatLoggedIn("永久用户"),
            accountMembership = MembershipStatus.VipPermanent,
        )),
    )
}
