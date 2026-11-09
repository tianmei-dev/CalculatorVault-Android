package com.aurora.calculatorvault.feature.membership

import com.aurora.calculatorvault.core.navigation.VaultTabRoute
import com.aurora.calculatorvault.feature.membership.domain.VipPlan
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseSource
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Purchase flow behaviour lives in [VipPurchaseControllerTest]; this covers routing and display keys. */
class VipPurchaseViewModelTest {

    @Test
    fun `fresh state has no plan, no consent and cannot purchase before server catalog`() {
        val state = VipPurchaseState(purchaseSource = VipPurchaseSource.Settings)
        assertNull(state.selectedPlan)
        assertFalse(state.agreementAccepted)
        assertFalse(state.canRequestPurchase)
    }

    @Test
    fun `display plan keys map server codes and carry no local prices`() {
        assertEquals(VipPlan.Monthly, VipPlan.fromServerCode("MONTHLY"))
        assertEquals(VipPlan.Annual, VipPlan.fromServerCode("YEARLY"))
        assertEquals(VipPlan.Lifetime, VipPlan.fromServerCode("LIFETIME"))
        assertNull(VipPlan.fromServerCode("WEEKLY"))
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
