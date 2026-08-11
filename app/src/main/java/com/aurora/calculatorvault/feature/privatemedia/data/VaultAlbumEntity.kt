package com.aurora.calculatorvault.feature.privatemedia.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultAlbum

@Entity(tableName = "vault_albums")
data class VaultAlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

fun VaultAlbumEntity.toDomain(): VaultAlbum = VaultAlbum(
    id = id,
    name = name,
    isDefault = isDefault,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
