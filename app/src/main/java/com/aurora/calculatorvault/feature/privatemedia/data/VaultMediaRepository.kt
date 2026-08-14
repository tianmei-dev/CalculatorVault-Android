package com.aurora.calculatorvault.feature.privatemedia.data

import android.net.Uri
import android.util.Log
import com.aurora.calculatorvault.BuildConfig
import com.aurora.calculatorvault.feature.privatemedia.domain.OriginalMediaRemovalCandidate
import com.aurora.calculatorvault.feature.privatemedia.domain.OriginalMediaUriResolver
import com.aurora.calculatorvault.feature.privatemedia.domain.SystemMediaRestoreFailure
import com.aurora.calculatorvault.feature.privatemedia.domain.SystemMediaRestoreManager
import com.aurora.calculatorvault.feature.privatemedia.domain.SystemMediaRestoreRequest
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultAlbum
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultAlbumDeleteResult
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultAlbumSummary
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaDeleteSummary
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaImportSummary
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaImporter
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaMoveResult
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaRestoreSummary
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultOriginalMediaState
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaType
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaWithFile
import com.aurora.calculatorvault.feature.privatemedia.storage.VaultMediaStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class VaultMediaRepository(
    private val albumDao: VaultAlbumDao,
    private val mediaDao: VaultMediaDao,
    private val importer: VaultMediaImporter,
    private val storage: VaultMediaStorage,
    private val restoreManager: SystemMediaRestoreManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun ensureDefaultAlbum(): VaultAlbum = withContext(ioDispatcher) {
        albumDao.getDefaultAlbum()?.toDomain() ?: run {
            val now = currentTimeMillis()
            val id = albumDao.insert(
                VaultAlbumEntity(
                    name = DEFAULT_ALBUM_NAME,
                    isDefault = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            (albumDao.getDefaultAlbum() ?: VaultAlbumEntity(
                id = id,
                name = DEFAULT_ALBUM_NAME,
                isDefault = true,
                createdAt = now,
                updatedAt = now,
            )).toDomain()
        }
    }

    fun observeDefaultAlbum(): Flow<VaultAlbum?> =
        albumDao.observeDefaultAlbum().map { it?.toDomain() }

    fun observeAlbumSummaries(): Flow<List<VaultAlbumSummary>> = combine(
        albumDao.observeAllAlbums(),
        mediaDao.observeAllMedia(),
    ) { albums, media ->
        val mediaByAlbum = media.groupBy { it.albumId }
        albums
            .map { album ->
                val albumMedia = mediaByAlbum[album.id].orEmpty()
                val cover = albumMedia.firstNotNullOfOrNull { entity ->
                    val file = storage.privateFile(entity.privateFileName)
                    if (file != null && file.exists()) {
                        VaultMediaWithFile(entity.toDomain(), file)
                    } else {
                        null
                    }
                }
                VaultAlbumSummary(
                    album = album.toDomain(),
                    mediaCount = albumMedia.size,
                    cover = cover,
                )
            }
            .sortedWith(
                compareByDescending<VaultAlbumSummary> { it.album.isDefault }
                    .thenByDescending { it.album.updatedAt }
                    .thenBy { it.album.id },
            )
    }

    fun observeDefaultAlbumMedia(): Flow<List<VaultMediaWithFile>> =
        albumDao.observeDefaultAlbum().flatMapLatest { album ->
            if (album == null) {
                flowOf(emptyList())
            } else {
                observeAlbumMedia(album.id)
            }
        }

    fun observeAlbumMedia(albumId: Long): Flow<List<VaultMediaWithFile>> =
        mediaDao.observeMediaByAlbum(albumId).map { entities ->
            entities.mapNotNull { entity ->
                val file = storage.privateFile(entity.privateFileName)
                if (file != null && file.exists()) {
                    VaultMediaWithFile(entity.toDomain(), file)
                } else {
                    null
                }
            }
        }

    fun observeCounts(): Flow<VaultMediaCounts> = combine(
        mediaDao.observeMediaCount(),
        mediaDao.observeMediaCountByType(VaultMediaType.IMAGE.name),
        mediaDao.observeMediaCountByType(VaultMediaType.VIDEO.name),
    ) { total, images, videos ->
        VaultMediaCounts(total = total, images = images, videos = videos)
    }

    suspend fun importMedia(
        uris: List<Uri>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): VaultMediaImportSummary = withContext(ioDispatcher) {
        val album = ensureDefaultAlbum()
        importMedia(album.id, uris, onProgress)
    }

    suspend fun importMedia(
        albumId: Long,
        uris: List<Uri>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): VaultMediaImportSummary = withContext(ioDispatcher) {
        val album = albumDao.getAlbum(albumId) ?: return@withContext VaultMediaImportSummary(0, uris.distinct().size)
        var successes = 0
        var failures = 0
        val importedIds = mutableListOf<Long>()
        val uniqueUris = uris.distinct()
        debugLog("batch import started count=${uniqueUris.size}")
        uniqueUris.forEachIndexed { index, uri ->
            onProgress?.invoke(index + 1, uniqueUris.size)
            val result = importer.importToPrivateFile(uri, album.id)
            val entity = result.getOrNull()
            if (entity == null) {
                failures += 1
                debugLog("batch import failed reason=copy")
            } else {
                try {
                    val id = mediaDao.insertMedia(entity)
                    importedIds += id
                    successes += 1
                    debugLog("batch import success mediaId=$id")
                } catch (_: Exception) {
                    storage.deleteQuietly(entity.privateFileName)
                    failures += 1
                    debugLog("batch import failed reason=database")
                }
            }
            debugLog("batch import progress current=${index + 1} total=${uniqueUris.size}")
        }
        if (successes > 0) {
            albumDao.touchAlbum(album.id, currentTimeMillis())
        }
        debugLog("batch import completed success=$successes failed=$failures")
        VaultMediaImportSummary(successes, failures, importedIds)
    }

    suspend fun createAlbum(name: String): Result<VaultAlbum> = withContext(ioDispatcher) {
        runCatching {
            val trimmedName = name.trim().takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Album name is blank")
            val now = currentTimeMillis()
            val id = albumDao.insert(
                VaultAlbumEntity(
                    name = trimmedName,
                    isDefault = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            (albumDao.getAlbum(id) ?: VaultAlbumEntity(
                id = id,
                name = trimmedName,
                isDefault = false,
                createdAt = now,
                updatedAt = now,
            )).toDomain()
        }
    }

    suspend fun renameAlbum(albumId: Long, name: String): Boolean = withContext(ioDispatcher) {
        val trimmedName = name.trim().takeIf { it.isNotBlank() } ?: return@withContext false
        albumDao.renameAlbum(albumId, trimmedName, currentTimeMillis()) == 1
    }

    suspend fun deleteAlbum(albumId: Long): VaultAlbumDeleteResult = withContext(ioDispatcher) {
        val album = albumDao.getAlbum(albumId) ?: return@withContext VaultAlbumDeleteResult.NotFound
        if (album.isDefault) return@withContext VaultAlbumDeleteResult.DefaultAlbum
        val mediaCount = mediaDao.countMediaByAlbum(albumId)
        if (mediaCount > 0) return@withContext VaultAlbumDeleteResult.NotEmpty(mediaCount)
        runCatching { albumDao.deleteAlbum(albumId) }
            .fold(
                onSuccess = { deleted ->
                    if (deleted == 1) VaultAlbumDeleteResult.Deleted else VaultAlbumDeleteResult.Failed
                },
                onFailure = { VaultAlbumDeleteResult.Failed },
            )
    }

    suspend fun moveMediaToAlbum(mediaIds: List<Long>, targetAlbumId: Long): VaultMediaMoveResult =
        withContext(ioDispatcher) {
            val uniqueIds = mediaIds.distinct()
            if (uniqueIds.isEmpty()) return@withContext VaultMediaMoveResult.NoSelection
            val target = albumDao.getAlbum(targetAlbumId) ?: return@withContext VaultMediaMoveResult.TargetMissing
            val entities = mediaDao.getMediaByIds(uniqueIds)
            if (entities.isEmpty()) return@withContext VaultMediaMoveResult.NoSelection
            if (entities.all { it.albumId == target.id }) return@withContext VaultMediaMoveResult.SameAlbum
            runCatching {
                val moved = mediaDao.moveMediaToAlbum(uniqueIds, target.id)
                if (moved > 0) {
                    val now = currentTimeMillis()
                    albumDao.touchAlbum(target.id, now)
                    entities.map { it.albumId }.distinct().forEach { sourceAlbumId ->
                        albumDao.touchAlbum(sourceAlbumId, now)
                    }
                    VaultMediaMoveResult.Moved(moved)
                } else {
                    VaultMediaMoveResult.Failed
                }
            }.getOrElse {
                VaultMediaMoveResult.Failed
            }
        }

    suspend fun originalRemovalCandidates(mediaIds: List<Long>) = withContext(ioDispatcher) {
        mediaDao.getMediaByIds(mediaIds.distinct())
            .filter { entity ->
                entity.originalRemovalState != VaultOriginalMediaState.REMOVED.name &&
                    entity.originalUri?.isNotBlank() == true
            }
            .mapNotNull { entity ->
                val uri = runCatching { Uri.parse(entity.originalUri) }.getOrNull()
                val mediaType = runCatching { VaultMediaType.valueOf(entity.mediaType) }.getOrNull()
                if (uri == null) {
                    null
                } else {
                    val deletableUri = if (mediaType == null) {
                        uri
                    } else {
                        OriginalMediaUriResolver.resolveDeletableUri(uri, mediaType) ?: uri
                    }
                    OriginalMediaRemovalCandidate(
                        mediaId = entity.id,
                        uri = deletableUri,
                    )
                }
            }
    }

    suspend fun markOriginalRemovalState(
        mediaIds: List<Long>,
        state: VaultOriginalMediaState,
    ): Int = withContext(ioDispatcher) {
        if (mediaIds.isEmpty()) 0 else mediaDao.updateOriginalRemovalState(mediaIds.distinct(), state.name)
    }

    suspend fun cleanupMissingMediaRecords(): Int = withContext(ioDispatcher) {
        val missingIds = mediaDao.observeAllMediaSnapshot()
            .filter { entity ->
                val file = storage.privateFile(entity.privateFileName)
                file == null || !file.exists()
            }
            .map { it.id }
        if (missingIds.isEmpty()) 0 else mediaDao.deleteMediaRecords(missingIds)
    }

    suspend fun deletePrivateMedia(mediaId: Long): VaultMediaDeleteSummary =
        deletePrivateMedia(listOf(mediaId))

    suspend fun deletePrivateMedia(mediaIds: List<Long>): VaultMediaDeleteSummary = withContext(ioDispatcher) {
        val uniqueIds = mediaIds.distinct()
        if (uniqueIds.isEmpty()) return@withContext VaultMediaDeleteSummary(0, 0)
        var successes = 0
        var failures = 0
        val entitiesById = mediaDao.getMediaByIds(uniqueIds).associateBy { it.id }
        uniqueIds.forEach { id ->
            val entity = entitiesById[id]
            if (entity == null) {
                failures += 1
                return@forEach
            }
            val fileDeletedOrMissing = storage.deletePrivateFile(entity.privateFileName)
            if (!fileDeletedOrMissing) {
                failures += 1
                return@forEach
            }
            val recordDeleted = runCatching { mediaDao.deleteMediaRecord(id) }.getOrDefault(0)
            if (recordDeleted == 1) {
                successes += 1
            } else {
                failures += 1
            }
        }
        VaultMediaDeleteSummary(successes, failures)
    }

    suspend fun restoreMediaToSystem(mediaIds: List<Long>): VaultMediaRestoreSummary = withContext(ioDispatcher) {
        val uniqueIds = mediaIds.distinct()
        if (uniqueIds.isEmpty()) return@withContext VaultMediaRestoreSummary(0, 0)
        var successes = 0
        var failures = 0
        var sourceMissing = 0
        var storageUnavailable = 0
        val entitiesById = mediaDao.getMediaByIds(uniqueIds).associateBy { it.id }
        uniqueIds.forEach { id ->
            val entity = entitiesById[id]
            val mediaType = entity?.let {
                runCatching { VaultMediaType.valueOf(it.mediaType) }.getOrNull()
            }
            val file = entity?.privateFileName?.let(storage::privateFile)
            if (entity == null || mediaType == null || file == null || !file.exists() || file.length() <= 0L) {
                failures += 1
                sourceMissing += 1
                return@forEach
            }
            val result = restoreManager.restore(
                SystemMediaRestoreRequest(
                    mediaId = entity.id,
                    mediaType = mediaType,
                    sourceFile = file,
                    displayName = entity.originalDisplayName,
                    mimeType = entity.mimeType,
                ),
            )
            if (result.isSuccess) {
                successes += 1
            } else {
                failures += 1
                when (result.failure) {
                    SystemMediaRestoreFailure.SourceMissing -> sourceMissing += 1
                    SystemMediaRestoreFailure.StorageUnavailable -> storageUnavailable += 1
                    else -> Unit
                }
            }
        }
        VaultMediaRestoreSummary(
            successCount = successes,
            failureCount = failures,
            sourceMissingCount = sourceMissing,
            storageUnavailableCount = storageUnavailable,
        )
    }

    data class VaultMediaCounts(
        val total: Int,
        val images: Int,
        val videos: Int,
    )

    private companion object {
        const val DEFAULT_ALBUM_NAME = "私密相册"
        const val TAG = "CV_VAULT_MEDIA"

        fun debugLog(message: String) {
            if (BuildConfig.DEBUG) Log.d(TAG, message)
        }
    }
}
