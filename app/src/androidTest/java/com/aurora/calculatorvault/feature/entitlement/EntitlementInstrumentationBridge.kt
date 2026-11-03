package com.aurora.calculatorvault.feature.entitlement

import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.DenialReason
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementState
import com.aurora.calculatorvault.feature.entitlement.domain.grantsTrialCapability
import java.util.concurrent.atomic.AtomicInteger

/**
 * androidTest-only Fake entitlement injection.
 *
 * Lives outside main/Release. Installs a full [EntitlementState] onto
 * [com.aurora.calculatorvault.feature.entitlement.domain.EntitlementRepository.stateOverride]
 * so production DEX never contains ForceVip / ForceNone grant logic.
 */
object EntitlementInstrumentationBridge {
    @Volatile
    private var installed: InstrumentationEntitlementOverride? = null

    @Volatile
    private var evaluationBudget: AtomicInteger? = null

    fun isArmed(): Boolean = installed != null || evaluationBudget != null

    fun install(app: CalculatorVaultApp, value: InstrumentationEntitlementOverride?) {
        evaluationBudget = null
        installed = value
        app.entitlementRepository.actionDecisionOverride = null
        if (value == null) {
            app.entitlementRepository.stateOverride = null
            app.entitlementRepository.refresh()
            return
        }
        val mode = value
        app.entitlementRepository.stateOverride = {
            when (mode) {
                InstrumentationEntitlementOverride.ForceNone -> forceNoneState(app)
                is InstrumentationEntitlementOverride.ForceVip -> forceVipState(app, mode)
            }
        }
        app.entitlementRepository.refresh()
    }

    /**
     * androidTest checkpoint for mid-batch import: first [budget] ActionPolicy.evaluate calls
     * for [EntitlementCapability.PRIVATE_ALBUM_IMPORT] allow, then deny.
     * Uses [actionDecisionOverride] so combine()/refresh computeState ticks do not burn slots.
     * Not a production API.
     */
    fun armImportEvaluationBudget(app: CalculatorVaultApp, budget: Int) {
        val counter = AtomicInteger(budget)
        evaluationBudget = counter
        installed = InstrumentationEntitlementOverride.ForceVip()
        // Keep VIP snapshot for UI/state; gate only ActionPolicy import evaluations.
        app.entitlementRepository.stateOverride = {
            forceVipState(app, InstrumentationEntitlementOverride.ForceVip())
        }
        app.entitlementRepository.actionDecisionOverride = gate@{ capability ->
            if (capability != EntitlementCapability.PRIVATE_ALBUM_IMPORT) return@gate null
            if (counter.getAndDecrement() > 0) {
                ActionDecision.Allowed
            } else {
                ActionDecision.Denied(DenialReason.REQUIRES_ENTITLEMENT)
            }
        }
    }

    fun clear(app: CalculatorVaultApp) {
        installed = null
        evaluationBudget = null
        app.entitlementRepository.actionDecisionOverride = null
        app.entitlementRepository.stateOverride = null
        app.entitlementRepository.refresh()
    }

    private fun forceNoneState(app: CalculatorVaultApp): EntitlementState {
        val trial = app.deviceTrial.state.value
        val now = android.os.SystemClock.elapsedRealtime()
        return EntitlementState(
            source = EntitlementSource.None,
            trialStatus = trial.status,
            trialRemainingMillis = trial.remainingMillis,
            trialDeadlineElapsedMillis = null,
            accountId = null,
            accountGeneration = app.entitlementRepository.currentAccountGeneration(),
            evaluatedAtElapsedMillis = now,
        )
    }

    private fun forceVipState(
        app: CalculatorVaultApp,
        fake: InstrumentationEntitlementOverride.ForceVip,
    ): EntitlementState {
        val trial = app.deviceTrial.state.value
        val now = android.os.SystemClock.elapsedRealtime()
        val vip = EntitlementSource.Vip(
            accountId = fake.accountId,
            expiresAtEpochMillis = fake.expiresAtEpochMillis,
            permanent = fake.expiresAtEpochMillis == null,
        )
        return EntitlementState(
            source = vip,
            trialStatus = trial.status,
            trialRemainingMillis = trial.remainingMillis,
            trialDeadlineElapsedMillis = if (trial.status.grantsTrialCapability() && trial.remainingMillis > 0L) {
                now + trial.remainingMillis
            } else {
                null
            },
            accountId = fake.accountId,
            accountGeneration = app.entitlementRepository.currentAccountGeneration(),
            vipExpiresAtEpochMillis = vip.expiresAtEpochMillis,
            vipPermanent = vip.permanent,
            evaluatedAtElapsedMillis = now,
        )
    }
}

sealed interface InstrumentationEntitlementOverride {
    data object ForceNone : InstrumentationEntitlementOverride
    data class ForceVip(
        val accountId: Long = SYNTHETIC_ACCOUNT_ID,
        val expiresAtEpochMillis: Long? = null,
    ) : InstrumentationEntitlementOverride

    companion object {
        const val SYNTHETIC_ACCOUNT_ID: Long = 9_001_333L
    }
}
