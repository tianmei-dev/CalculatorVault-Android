package com.aurora.calculatorvault.feature.membership.domain

import com.aurora.calculatorvault.feature.payment.data.MembershipPlanOffer
import com.aurora.calculatorvault.feature.payment.data.PaymentError
import com.aurora.calculatorvault.feature.payment.data.PaymentOrderStatus
import com.aurora.calculatorvault.feature.payment.domain.PayLaunchDecision

/**
 * Display key for account membership status (settings / account rows).
 * Carries no price: purchasable plans and prices come only from the server catalog.
 */
enum class VipPlan(val serverCode: String) {
    Monthly("MONTHLY"),
    Annual("YEARLY"),
    Lifetime("LIFETIME");

    companion object {
        fun fromServerCode(code: String?): VipPlan? = entries.firstOrNull { it.serverCode == code }
    }
}

enum class VipPurchaseSource {
    Settings, AppLock, PrivateAlbum, TrialExpired;

    companion object {
        fun fromRoute(value: String?): VipPurchaseSource =
            entries.firstOrNull { it.name == value } ?: Settings
    }
}

sealed interface PlansState {
    data object Loading : PlansState
    data class Ready(val plans: List<MembershipPlanOffer>) : PlansState
    data object Empty : PlansState
    data class Failed(val error: PaymentError) : PlansState
}

/** Order query problem shown on the waiting page. Never changes the known order status. */
enum class OrderQueryIssue {
    /** The device reports no usable network: shown immediately, no HTTP attempt is wasted. */
    NetworkUnavailable,

    /** Network looks available but the query timed out or the server failed: auto retry. */
    Temporary,
}

/** Membership sync after the server reports FULFILLED. Local state never grants VIP. */
enum class MembershipSyncAfterPurchase { NotStarted, Syncing, Synced, Failed }

data class OrderView(
    val orderNo: String,
    val planCode: String,
    val planName: String,
    val amountDisplay: String,
    val status: PaymentOrderStatus,
    val payDecision: PayLaunchDecision,
    /** elapsedRealtime at which the server says the order expires (server-clock anchored). */
    val expiresAtElapsedMillis: Long?,
)

sealed interface PurchasePhase {
    data object Idle : PurchasePhase
    data object Creating : PurchasePhase

    /** Create was not confirmed. [retryKept] means the same clientRequestId will be reused. */
    data class CreateFailed(val error: PaymentError, val retryKept: Boolean) : PurchasePhase

    /** Recovered order number for the current account; details arrive with the first query. */
    data class Resuming(val orderNo: String) : PurchasePhase

    data class Ordering(
        val order: OrderView,
        val querying: Boolean = false,
        /** Why the latest query attempt did not refresh [order]; null after a successful query. */
        val queryIssue: OrderQueryIssue? = null,
        val membershipSync: MembershipSyncAfterPurchase = MembershipSyncAfterPurchase.NotStarted,
    ) : PurchasePhase {
        val lastQueryFailed: Boolean get() = queryIssue != null
    }
}

/** One-shot UI feedback. */
enum class PurchaseNotice {
    AgreementRequired,
    PlansNotReady,
    LoginRequired,
    LoginExpired,
    LoginError,
    SessionVerifying,
    SessionUnavailable,
    LifetimeOwned,
    PlanUnavailable,
    RequestConflict,
    PaymentConfigMissing,
    PayLinkInvalid,
    AccountChanged,
    OrderNotFound,
    PurchaseCompleted,
}

data class VipPurchaseState(
    val purchaseSource: VipPurchaseSource = VipPurchaseSource.Settings,
    val plans: PlansState = PlansState.Loading,
    val selectedPlanCode: String? = null,
    val agreementAccepted: Boolean = false,
    val lifetimeOwned: Boolean = false,
    val phase: PurchasePhase = PurchasePhase.Idle,
) {
    val selectedPlan: MembershipPlanOffer?
        get() = (plans as? PlansState.Ready)?.plans?.firstOrNull { it.planCode == selectedPlanCode }

    /** Purchase button is actionable only with a fresh server catalog and no active order flow. */
    val canRequestPurchase: Boolean
        get() = selectedPlan != null && !lifetimeOwned &&
            (phase == PurchasePhase.Idle || phase is PurchasePhase.CreateFailed)
}
