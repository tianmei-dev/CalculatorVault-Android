package com.aurora.calculatorvault.feature.membership.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aurora.calculatorvault.feature.membership.domain.PurchaseActionState
import com.aurora.calculatorvault.feature.membership.domain.VipPaymentMethod
import com.aurora.calculatorvault.feature.membership.domain.VipPlan
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseSelection
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseSource
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Scoped to the purchase navigation entry. No membership or login state is written. */
class VipPurchaseViewModel(
    source: VipPurchaseSource,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) : ViewModel() {
    private val mutableState = MutableStateFlow(VipPurchaseState(purchaseSource = source))
    val state = mutableState.asStateFlow()
    private var lastPurchaseActionAt: Long? = null

    fun selectPlan(plan: VipPlan) {
        mutableState.update { it.copy(selectedPlan = plan, purchaseActionState = PurchaseActionState.Idle) }
    }

    fun selectPaymentMethod(method: VipPaymentMethod) {
        mutableState.update {
            it.copy(selectedPaymentMethod = method, purchaseActionState = PurchaseActionState.Idle)
        }
    }

    fun setAgreementAccepted(accepted: Boolean) {
        mutableState.update {
            it.copy(agreementAccepted = accepted, purchaseActionState = PurchaseActionState.Idle)
        }
    }

    fun purchaseSelection(): VipPurchaseSelection = state.value.let {
        VipPurchaseSelection(it.selectedPlan, it.selectedPaymentMethod, it.purchaseSource)
    }

    /** Returns one UI feedback per accepted tap; null means a rapid duplicate was ignored. */
    @Synchronized
    fun requestPurchase(): PurchaseActionState? {
        if (!state.value.agreementAccepted) {
            mutableState.update { it.copy(purchaseActionState = PurchaseActionState.AgreementRequired) }
            return PurchaseActionState.AgreementRequired
        }
        val now = monotonicMillis()
        if (lastPurchaseActionAt?.let { now - it < PURCHASE_TAP_INTERVAL_MS } == true) return null
        lastPurchaseActionAt = now

        // A6.2 boundary: a future coordinator can take purchaseSelection(), check WeChat
        // authentication, open A6.1 with LoginSource.MembershipPurchase if necessary,
        // and continue the same selection only after real authentication. No order here.
        mutableState.update { it.copy(purchaseActionState = PurchaseActionState.PaymentUnavailable) }
        return PurchaseActionState.PaymentUnavailable
    }

    class Factory(private val source: VipPurchaseSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(VipPurchaseViewModel::class.java))
            return VipPurchaseViewModel(source) as T
        }
    }

    private companion object {
        const val PURCHASE_TAP_INTERVAL_MS = 1_000L
    }
}
