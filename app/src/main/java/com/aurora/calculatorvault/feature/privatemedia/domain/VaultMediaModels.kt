package com.aurora.calculatorvault.feature.privatemedia.domain

import java.io.File

enum class VaultMediaType {
    IMAGE,
    VIDEO,
}

enum class VaultOriginalMediaState {
    PRESENT,
    REMOVED,
    UNAVAILABLE,
    DELETE_FAILED,
    UNKNOWN,
}

data class VaultAlbum(
    val id: Long,
    val name: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class VaultMedia(
    val id: Long,
    val albumId: Long,
    val mediaType: VaultMediaType,
    val privateFileName: String,
    val originalDisplayName: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val importedAt: Long,
    val originalUri: String?,
    val originalRemovalState: VaultOriginalMediaState,
)

data class VaultMediaWithFile(
    val media: VaultMedia,
    val file: File,
)

data class VaultMediaImportSummary(
    val successCount: Int,
    val failureCount: Int,
    val importedMediaIds: List<Long> = emptyList(),
)

data class VaultMediaDeleteSummary(
    val successCount: Int,
    val failureCount: Int,
) {
    val isSuccess: Boolean
        get() = successCount > 0 && failureCount == 0
}

data class VaultMediaRestoreSummary(
    val successCount: Int,
    val failureCount: Int,
    val sourceMissingCount: Int = 0,
    val storageUnavailableCount: Int = 0,
)

sealed interface VaultMediaImportFailure {
    data object UnsupportedMediaType : VaultMediaImportFailure
    data object SourceUnavailable : VaultMediaImportFailure
    data object StorageUnavailable : VaultMediaImportFailure
    data object CopyFailed : VaultMediaImportFailure
    data object DatabaseFailed : VaultMediaImportFailure
}
