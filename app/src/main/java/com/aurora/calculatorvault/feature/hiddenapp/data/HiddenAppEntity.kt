package com.aurora.calculatorvault.feature.hiddenapp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hidden_apps",
    indices = [Index(value = ["package_name"], unique = true)],
)
data class HiddenAppEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "app_name_snapshot")
    val appNameSnapshot: String,
    @ColumnInfo(name = "added_at")
    val addedAt: Long,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "last_opened_at")
    val lastOpenedAt: Long? = null,
    @ColumnInfo(name = "open_count")
    val openCount: Int = 0,
)
