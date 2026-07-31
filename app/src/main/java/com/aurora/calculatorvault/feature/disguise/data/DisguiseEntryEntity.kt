package com.aurora.calculatorvault.feature.disguise.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "disguise_entries",
    indices = [
        Index(value = ["package_name"]),
        Index(value = ["shortcut_id"], unique = true),
    ],
)
data class DisguiseEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "target_app_name")
    val targetAppName: String,
    @ColumnInfo(name = "custom_name")
    val customName: String,
    @ColumnInfo(name = "icon_id")
    val iconId: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "shortcut_id")
    val shortcutId: String? = null,
    @ColumnInfo(name = "shortcut_request_state", defaultValue = "NOT_REQUESTED")
    val shortcutRequestState: String = "NOT_REQUESTED",
    @ColumnInfo(name = "shortcut_requested_at")
    val shortcutRequestedAt: Long? = null,
    @ColumnInfo(name = "shortcut_callback_at")
    val shortcutCallbackAt: Long? = null,
    @ColumnInfo(name = "shortcut_last_error")
    val shortcutLastError: String? = null,
)
