package com.aurora.calculatorvault.feature.applock.presentation

import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/** Observation only. Never call mutating ActionPolicy.evaluate while collecting its own output. */
internal fun Flow<EntitlementState>.appLockPresentationEntitled(): Flow<Boolean> =
    map { it.hasPaidEntitlement }.distinctUntilChanged()
