package com.aurora.calculatorvault.feature.membership.domain

import com.aurora.calculatorvault.feature.payment.data.MembershipPlanOffer

/**
 * Local VIP purchase catalog for immediate offline display. Server plans are fetched only when
 * the user taps purchase; [matchesServer] rejects price / duration drift before create-order.
 */
object LocalVipCatalog {
    const val DEFAULT_PLAN_CODE = "YEARLY"

    val plans: List<MembershipPlanOffer> = listOf(
        MembershipPlanOffer(
            planCode = "MONTHLY",
            displayName = "月卡",
            priceFen = 1,
            priceDisplay = "0.01",
            durationDays = 30,
            isLifetime = false,
        ),
        MembershipPlanOffer(
            planCode = "YEARLY",
            displayName = "年卡",
            priceFen = 2,
            priceDisplay = "0.02",
            durationDays = 365,
            isLifetime = false,
        ),
        MembershipPlanOffer(
            planCode = "LIFETIME",
            displayName = "永久会员",
            priceFen = 3,
            priceDisplay = "0.03",
            durationDays = null,
            isLifetime = true,
        ),
    )

    fun offer(planCode: String): MembershipPlanOffer? = plans.firstOrNull { it.planCode == planCode }

    fun matchesServer(local: MembershipPlanOffer, server: MembershipPlanOffer): Boolean =
        local.planCode == server.planCode &&
            local.priceFen == server.priceFen &&
            local.durationDays == server.durationDays &&
            local.isLifetime == server.isLifetime
}

enum class VipPayChannel(val apiValue: String) {
    WECHAT_PAY("WECHAT_PAY"),
    ALIPAY("ALIPAY"),
}
