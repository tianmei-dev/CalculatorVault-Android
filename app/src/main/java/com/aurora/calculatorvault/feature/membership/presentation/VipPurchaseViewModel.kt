package com.aurora.calculatorvault.feature.membership.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementState
import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseSource
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseState
import com.aurora.calculatorvault.feature.payment.data.PaymentRepository
import com.aurora.calculatorvault.feature.payment.domain.PayLaunchPolicy
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** App-scoped collaborators the purchase page needs; none of them can grant VIP locally. */
class VipPurchaseDependencies(
    val repository: PaymentRepository,
    val sessionState: StateFlow<AccountSession>,
    val entitlementState: StateFlow<EntitlementState>,
    val membership: PurchaseMembershipPort,
    val payLaunchPolicy: PayLaunchPolicy,
    val elapsedRealtime: () -> Long,
)

/**
 * Scoped to the purchase navigation entry: survives rotation / Activity recreation, so an order
 * is never created twice by recomposition. Polling stops with [viewModelScope].
 */
class VipPurchaseViewModel(source: VipPurchaseSource, deps: VipPurchaseDependencies) : ViewModel() {
    private val controller = VipPurchaseController(
        source = source,
        scope = viewModelScope,
        repository = deps.repository,
        sessionState = deps.sessionState,
        entitlementState = deps.entitlementState,
        membership = deps.membership,
        payLaunchPolicy = deps.payLaunchPolicy,
        elapsedRealtime = deps.elapsedRealtime,
    )

    val state: StateFlow<VipPurchaseState> = controller.state
    val events: SharedFlow<VipPurchaseEvent> = controller.events

    fun loadPlans() = controller.loadPlans()
    fun selectPlan(planCode: String) = controller.selectPlan(planCode)
    fun setAgreementAccepted(accepted: Boolean) = controller.setAgreementAccepted(accepted)
    fun requestPurchase() = controller.requestPurchase()
    fun openCashier() = controller.openCashier()
    fun refreshOrder() = controller.refreshOrder()
    fun retryMembershipSync() = controller.retryMembershipSync()
    fun closeOrderView() = controller.closeOrderView()
    fun onVisibilityChanged(visible: Boolean) = controller.onVisibilityChanged(visible)

    class Factory(
        private val source: VipPurchaseSource,
        private val deps: VipPurchaseDependencies,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(VipPurchaseViewModel::class.java))
            return VipPurchaseViewModel(source, deps) as T
        }
    }
}
