package com.aurora.calculatorvault.core.navigation

import com.aurora.calculatorvault.core.security.session.VaultSessionState
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultRouteGuardTest {

    @Test
    fun `locked route redirects and unlocked route renders vault`() {
        assertEquals(
            VaultAccessDecision.RedirectToCalculator,
            VaultRouteGuard.decide(VaultSessionState.Locked),
        )
        assertEquals(
            VaultAccessDecision.ShowVault,
            VaultRouteGuard.decide(VaultSessionState.Unlocked),
        )
    }
}
