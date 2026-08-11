package com.aurora.calculatorvault.feature.privatemedia.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMedia
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultOriginalMediaState
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaType

@Entity(
    tableName = "vault_media",
    foreignKeys = [
        ForeignKey(
            entity = VaultAlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["album_id"]),
        Index(value = ["imported_at"]),
    ],
)
data class VaultMediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "album_id")
    val albumId: Long,
    @ColumnInfo(name = "media_type")
    val mediaType: String,
    @ColumnInfo(name = "private_file_name")
    val privateFileName: String,
    @ColumnInfo(name = "original_display_name")
    val originalDisplayName: String?,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "width")
    val width: Int?,
    @ColumnInfo(name = "height")
    val height: Int?,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?,
    @ColumnInfo(name = "imported_at")
    val importedAt: Long,
    @ColumnInfo(name = "original_uri")
    val originalUri: String?,
    @ColumnInfo(name = "original_removal_state")
    val originalRemovalState: String = VaultOriginalMediaState.PRESENT.name,
)

fun VaultMediaEntity.toDomain(): VaultMedia = VaultMedia(
    id = id,
    albumId = albumId,
    mediaType = VaultMediaType.valueOf(mediaType),
    privateFileName = privateFileName,
    originalDisplayName = originalDisplayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    durationMs = durationMs,
    importedAt = importedAt,
    originalUri = originalUri,
    originalRemovalState = runCatching {
        VaultOriginalMediaState.valueOf(originalRemovalState)
    }.getOrDefault(VaultOriginalMediaState.UNKNOWN),
)
