package com.aurora.calculatorvault.feature.hiddenapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenAppDao {
    @Query("SELECT * FROM hidden_apps ORDER BY sort_order ASC, added_at ASC")
    fun observeAll(): Flow<List<HiddenAppEntity>>

    @Query("SELECT package_name FROM hidden_apps")
    fun observeAllPackageNames(): Flow<List<String>>

    @Query("SELECT package_name FROM hidden_apps WHERE package_name IN (:packageNames)")
    suspend fun findExistingPackageNames(packageNames: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(apps: List<HiddenAppEntity>): List<Long>

    @Query("DELETE FROM hidden_apps WHERE package_name = :packageName")
    suspend fun deleteByPackageName(packageName: String): Int

    @Query("DELETE FROM hidden_apps WHERE package_name IN (:packageNames)")
    suspend fun deleteByPackageNames(packageNames: List<String>): Int

    @Query("UPDATE hidden_apps SET sort_order = :sortOrder WHERE package_name = :packageName")
    suspend fun updateSortOrder(packageName: String, sortOrder: Int): Int

    @Query("SELECT EXISTS(SELECT 1 FROM hidden_apps WHERE package_name = :packageName)")
    suspend fun exists(packageName: String): Boolean

    @Query("SELECT MAX(sort_order) FROM hidden_apps")
    suspend fun maxSortOrder(): Int?

    @Query(
        """
        SELECT * FROM hidden_apps
        WHERE last_opened_at IS NOT NULL
        ORDER BY last_opened_at DESC
        LIMIT :limit
        """,
    )
    fun observeRecentlyOpened(limit: Int): Flow<List<HiddenAppEntity>>

    @Query(
        """
        UPDATE hidden_apps
        SET last_opened_at = :openedAt,
            open_count = open_count + 1
        WHERE package_name = :packageName
        """,
    )
    suspend fun markOpened(packageName: String, openedAt: Long): Int

    @Query(
        """
        UPDATE hidden_apps
        SET last_opened_at = NULL,
            open_count = 0
        WHERE last_opened_at IS NOT NULL OR open_count != 0
        """,
    )
    suspend fun clearRecentHistory(): Int
}
