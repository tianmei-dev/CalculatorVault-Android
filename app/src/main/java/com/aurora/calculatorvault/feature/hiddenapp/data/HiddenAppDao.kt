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

    @Query("SELECT EXISTS(SELECT 1 FROM hidden_apps WHERE package_name = :packageName)")
    suspend fun exists(packageName: String): Boolean

    @Query("SELECT MAX(sort_order) FROM hidden_apps")
    suspend fun maxSortOrder(): Int?
}
