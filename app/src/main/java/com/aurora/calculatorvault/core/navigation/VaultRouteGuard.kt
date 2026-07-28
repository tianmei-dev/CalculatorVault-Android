package com.aurora.calculatorvault.core.navigation

import com.aurora.calculatorvault.core.security.session.VaultSessionState

enum class VaultAccessDecision {
    ShowVault,
    RedirectToCalculator,
}

object VaultRouteGuard {
    fun decide(sessionState: VaultSessionState): VaultAccessDecision =
        if (sessionState == VaultSessionState.Unlocked) {
            VaultAccessDecision.ShowVault
        } else {
            VaultAccessDecision.RedirectToCalculator
        }
}
