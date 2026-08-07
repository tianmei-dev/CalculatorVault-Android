package com.aurora.calculatorvault.feature.applock.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_lock_entries")
data class AppLockEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "app_name_snapshot")
    val appNameSnapshot: String,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
