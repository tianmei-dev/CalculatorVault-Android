package com.aurora.calculatorvault.feature.membership

import com.aurora.calculatorvault.core.navigation.VaultTabRoute
import com.aurora.calculatorvault.feature.membership.domain.PurchaseActionState
import com.aurora.calculatorvault.feature.membership.domain.VipPaymentMethod
import com.aurora.calculatorvault.feature.membership.domain.VipPlan
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseSource
import com.aurora.calculatorvault.feature.membership.presentation.VipPurchaseViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VipPurchaseViewModelTest {
    @Test
    fun `fresh entry defaults to annual wechat and unchecked agreement`() {
        val state = VipPurchaseViewModel(VipPurchaseSource.Settings).state.value
        assertEquals(VipPlan.Annual, state.selectedPlan)
        assertEquals(VipPaymentMethod.WeChat, state.selectedPaymentMethod)
        assertFalse(state.agreementAccepted)
        assertEquals(VipPurchaseSource.Settings, state.purchaseSource)
        assertEquals(PurchaseActionState.Idle, state.purchaseActionState)
    }

    @Test
    fun `catalog contains exact prices and durations without floating point rounding`() {
        assertEquals(listOf(590, 1990, 2990), VipPlan.entries.map { it.priceFen })
        assertEquals(listOf("5.9", "19.9", "29.9"), VipPlan.entries.map { it.priceText })
        assertEquals(listOf(30, 365, null), VipPlan.entries.map { it.durationDays })
    }

    @Test
    fun `plan and payment selections are independent and retain consent`() {
        val model = VipPurchaseViewModel(VipPurchaseSource.PrivateAlbum)
        model.setAgreementAccepted(true)
        model.selectPaymentMethod(VipPaymentMethod.Alipay)
        VipPlan.entries.forEach { plan ->
            model.selectPlan(plan)
            assertEquals(plan, model.state.value.selectedPlan)
            assertEquals(VipPaymentMethod.Alipay, model.state.value.selectedPaymentMethod)
            assertTrue(model.state.value.agreementAccepted)
        }
        model.selectPaymentMethod(VipPaymentMethod.WeChat)
        assertEquals(VipPlan.Lifetime, model.state.value.selectedPlan)
        assertEquals(VipPaymentMethod.WeChat, model.state.value.selectedPaymentMethod)
        assertEquals(VipPurchaseSource.PrivateAlbum, model.state.value.purchaseSource)
    }

    @Test
    fun `unchecked request never reaches payment placeholder and consent allows immediate retry`() {
        val model = VipPurchaseViewModel(VipPurchaseSource.Settings) { 0L }
        assertEquals(PurchaseActionState.AgreementRequired, model.requestPurchase())
        assertEquals(PurchaseActionState.AgreementRequired, model.state.value.purchaseActionState)
        assertFalse(model.state.value.agreementAccepted)
        model.setAgreementAccepted(true)
        assertEquals(PurchaseActionState.PaymentUnavailable, model.requestPurchase())
        assertEquals(PurchaseActionState.PaymentUnavailable, model.state.value.purchaseActionState)
        assertEquals(VipPlan.Annual, model.state.value.selectedPlan)
    }

    @Test
    fun `rapid duplicate requests are ignored even after selection changes`() {
        var now = 100L
        val model = VipPurchaseViewModel(VipPurchaseSource.AppLock) { now }
        model.setAgreementAccepted(true)
        assertEquals(PurchaseActionState.PaymentUnavailable, model.requestPurchase())
        repeat(20) { assertNull(model.requestPurchase()) }
        model.selectPlan(VipPlan.Monthly)
        model.selectPaymentMethod(VipPaymentMethod.Alipay)
        now = 1_099L
        assertNull(model.requestPurchase())
        now = 1_100L
        assertEquals(PurchaseActionState.PaymentUnavailable, model.requestPurchase())
        assertEquals(VipPlan.Monthly, model.purchaseSelection().plan)
        assertEquals(VipPaymentMethod.Alipay, model.purchaseSelection().paymentMethod)
    }

    @Test
    fun `revoking consent blocks further requests and clears previous feedback`() {
        val model = VipPurchaseViewModel(VipPurchaseSource.Settings)
        model.setAgreementAccepted(true)
        model.requestPurchase()
        model.setAgreementAccepted(false)
        assertEquals(PurchaseActionState.Idle, model.state.value.purchaseActionState)
        assertEquals(PurchaseActionState.AgreementRequired, model.requestPurchase())
    }

    @Test
    fun `new navigation entry has fresh consent and selections`() {
        val previous = VipPurchaseViewModel(VipPurchaseSource.Settings)
        previous.selectPlan(VipPlan.Lifetime)
        previous.selectPaymentMethod(VipPaymentMethod.Alipay)
        previous.setAgreementAccepted(true)
        previous.requestPurchase()
        val reopened = VipPurchaseViewModel(VipPurchaseSource.Settings)
        assertFalse(reopened.state.value.agreementAccepted)
        assertEquals(VipPlan.Annual, reopened.state.value.selectedPlan)
        assertEquals(VipPaymentMethod.WeChat, reopened.state.value.selectedPaymentMethod)
        assertEquals(PurchaseActionState.Idle, reopened.state.value.purchaseActionState)
    }

    @Test
    fun `continuation snapshot retains original purchase context without changing page state`() {
        val model = VipPurchaseViewModel(VipPurchaseSource.TrialExpired)
        model.selectPlan(VipPlan.Monthly)
        model.selectPaymentMethod(VipPaymentMethod.Alipay)
        val snapshot = model.purchaseSelection()
        model.selectPlan(VipPlan.Lifetime)
        assertEquals(VipPlan.Monthly, snapshot.plan)
        assertEquals(VipPaymentMethod.Alipay, snapshot.paymentMethod)
        assertEquals(VipPurchaseSource.TrialExpired, snapshot.source)
        assertEquals(VipPlan.Lifetime, model.state.value.selectedPlan)
        assertFalse(model.state.value.agreementAccepted)
    }

    @Test
    fun `all source routes round trip and missing or invalid sources fall back to settings`() {
        VipPurchaseSource.entries.forEach { source ->
            val route = VaultTabRoute.VipMembership.createRoute(source)
            assertEquals("vault/vip?source=${source.name}", route)
            assertEquals(source, VipPurchaseSource.fromRoute(route.substringAfter("source=")))
        }
        assertEquals(VipPurchaseSource.Settings, VipPurchaseSource.fromRoute(null))
        assertEquals(VipPurchaseSource.Settings, VipPurchaseSource.fromRoute("unrecognized"))
    }
}
