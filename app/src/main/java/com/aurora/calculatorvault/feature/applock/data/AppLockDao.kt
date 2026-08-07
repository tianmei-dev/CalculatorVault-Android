package com.aurora.calculatorvault.feature.applock.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLockDao {
    @Query("SELECT * FROM app_lock_entries ORDER BY app_name_snapshot COLLATE NOCASE ASC")
    fun observeAllEntries(): Flow<List<AppLockEntryEntity>>

    @Query("SELECT * FROM app_lock_entries WHERE enabled = 1 ORDER BY updated_at DESC")
    fun observeEnabledEntries(): Flow<List<AppLockEntryEntity>>

    @Query("SELECT package_name FROM app_lock_entries WHERE enabled = 1")
    fun observeEnabledPackages(): Flow<List<String>>

    @Query("SELECT * FROM app_lock_entries WHERE package_name = :packageName LIMIT 1")
    suspend fun getEntry(packageName: String): AppLockEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AppLockEntryEntity)

    @Query(
        """
        UPDATE app_lock_entries
        SET enabled = :enabled,
            app_name_snapshot = :appNameSnapshot,
            updated_at = :updatedAt
        WHERE package_name = :packageName
        """,
    )
    suspend fun updateEnabled(
        packageName: String,
        enabled: Boolean,
        appNameSnapshot: String,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM app_lock_entries WHERE package_name = :packageName")
    suspend fun delete(packageName: String): Int

    @Query("DELETE FROM app_lock_entries")
    suspend fun deleteAll(): Int
}
