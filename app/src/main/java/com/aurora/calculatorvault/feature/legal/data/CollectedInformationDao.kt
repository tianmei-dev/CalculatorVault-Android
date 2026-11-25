package com.aurora.calculatorvault.feature.legal.data

import androidx.room.Dao
import androidx.room.Query

data class LocalInformationRecords(
    val hasHiddenApps: Boolean,
    val hasDisguises: Boolean,
    val hasAppLocks: Boolean,
    val hasMedia: Boolean,
)

@Dao
interface CollectedInformationDao {
    /** Read record presence only; no names, identifiers, file paths, or media leave Room. */
    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM hidden_apps) AS hasHiddenApps,
               EXISTS(SELECT 1 FROM disguise_entries) AS hasDisguises,
               EXISTS(SELECT 1 FROM app_lock_entries) AS hasAppLocks,
               EXISTS(SELECT 1 FROM vault_media) AS hasMedia
        """,
    )
    suspend fun readLocalRecords(): LocalInformationRecords
}
