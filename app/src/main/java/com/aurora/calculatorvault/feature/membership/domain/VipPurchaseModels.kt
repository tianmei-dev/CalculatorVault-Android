package com.aurora.calculatorvault.feature.membership.domain

import java.math.BigDecimal

/** Local display catalog only. Replace with server product data when purchasing is integrated. */
enum class VipPlan(val priceFen: Int, val durationDays: Int?) {
    Monthly(590, 30),
    Annual(1990, 365),
    Lifetime(2990, null);

    val priceText: String
        get() = BigDecimal.valueOf(priceFen.toLong(), 2).stripTrailingZeros().toPlainString()
}

enum class VipPaymentMethod { WeChat, Alipay }

enum class VipPurchaseSource {
    Settings, AppLock, PrivateAlbum, TrialExpired;

    companion object {
        fun fromRoute(value: String?): VipPurchaseSource =
            entries.firstOrNull { it.name == value } ?: Settings
    }
}

/** Immutable context for a future login -> order continuation; contains no account or entitlement. */
data class VipPurchaseSelection(
    val plan: VipPlan,
    val paymentMethod: VipPaymentMethod,
    val source: VipPurchaseSource,
)

enum class PurchaseActionState { Idle, AgreementRequired, PaymentUnavailable }

data class VipPurchaseState(
    val selectedPlan: VipPlan = VipPlan.Annual,
    val selectedPaymentMethod: VipPaymentMethod = VipPaymentMethod.WeChat,
    val agreementAccepted: Boolean = false,
    val purchaseSource: VipPurchaseSource = VipPurchaseSource.Settings,
    val purchaseActionState: PurchaseActionState = PurchaseActionState.Idle,
)
