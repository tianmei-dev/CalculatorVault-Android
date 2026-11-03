package com.aurora.calculatorvault.feature.privatemedia.domain

import com.aurora.calculatorvault.feature.entitlement.domain.ActionDecision
import com.aurora.calculatorvault.feature.entitlement.domain.ActionPolicy
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementCapability

/**
 * Live entitlement gate for private-album **new media import** only.
 * View/restore/manage-existing paths must not call this.
 */
object PrivateAlbumImportAccess {
    fun evaluate(actionPolicy: ActionPolicy): ActionDecision =
        actionPolicy.evaluate(EntitlementCapability.PRIVATE_ALBUM_IMPORT)

    fun allowsImport(actionPolicy: ActionPolicy): Boolean =
        evaluate(actionPolicy) is ActionDecision.Allowed
}

sealed interface PrivateAlbumImportItemResult {
    data class Success(val mediaId: Long) : PrivateAlbumImportItemResult
    data object Failure : PrivateAlbumImportItemResult
}

/**
 * Batch import loop with per-item entitlement re-check.
 * Does not roll back prior successes when entitlement drops mid-batch.
 */
object PrivateAlbumImportBatch {
    suspend fun run(
        itemCount: Int,
        importAllowed: () -> Boolean,
        importAt: suspend (index: Int) -> PrivateAlbumImportItemResult,
    ): VaultMediaImportSummary {
        if (itemCount <= 0) return VaultMediaImportSummary(0, 0)
        var successes = 0
        var failures = 0
        var skippedDueToEntitlement = 0
        val importedIds = mutableListOf<Long>()
        var index = 0
        while (index < itemCount) {
            if (!importAllowed()) {
                skippedDueToEntitlement = itemCount - index
                break
            }
            when (val result = importAt(index)) {
                is PrivateAlbumImportItemResult.Success -> {
                    successes += 1
                    importedIds += result.mediaId
                }
                PrivateAlbumImportItemResult.Failure -> failures += 1
            }
            index += 1
        }
        return VaultMediaImportSummary(
            successCount = successes,
            failureCount = failures,
            importedMediaIds = importedIds,
            skippedDueToEntitlement = skippedDueToEntitlement,
        )
    }
}
