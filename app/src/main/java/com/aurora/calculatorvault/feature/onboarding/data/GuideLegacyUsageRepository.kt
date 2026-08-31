package com.aurora.calculatorvault.feature.onboarding.data

import com.aurora.calculatorvault.core.database.CalculatorVaultDatabase

class GuideLegacyUsageRepository(
    private val database: CalculatorVaultDatabase,
) {
    suspend fun hasEnabledAppLock(): Boolean =
        database.appLockDao().countEnabledEntries() > 0

    suspend fun hasDisguiseEntries(): Boolean =
        database.disguiseEntryDao().countEntries() > 0

    suspend fun hasPrivateMedia(): Boolean =
        database.vaultMediaDao().countAllMedia() > 0
}
