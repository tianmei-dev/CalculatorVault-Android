package com.aurora.calculatorvault.feature.privatemedia.data

import android.net.Uri
import com.aurora.calculatorvault.feature.privatemedia.domain.OriginalMediaRemovalCandidate
import com.aurora.calculatorvault.feature.privatemedia.domain.OriginalMediaUriResolver
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultAlbum
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaDeleteSummary
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaImportSummary
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaImporter
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

    fun observeDefaultAlbumMedia(): Flow<List<VaultMediaWithFile>> =
        albumDao.observeDefaultAlbum().flatMapLatest { album ->
            if (album == null) {
                flowOf(emptyList())
            } else {
                mediaDao.observeMediaByAlbum(album.id).map { entities ->
                    entities.mapNotNull { entity ->
                        val file = storage.privateFile(entity.privateFileName)
                        if (file != null && file.exists()) {
                            VaultMediaWithFile(entity.toDomain(), file)
                        } else {
                            null
                        }
                    }
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

    suspend fun importMedia(uris: List<Uri>): VaultMediaImportSummary = withContext(ioDispatcher) {
        val album = ensureDefaultAlbum()
        var successes = 0
        var failures = 0
        val importedIds = mutableListOf<Long>()
        uris.distinct().forEach { uri ->
            val result = importer.importToPrivateFile(uri, album.id)
            val entity = result.getOrNull()
            if (entity == null) {
                failures += 1
            } else {
                try {
                    val id = mediaDao.insertMedia(entity)
                    importedIds += id
                    successes += 1
                } catch (_: Exception) {
                    storage.deleteQuietly(entity.privateFileName)
                    failures += 1
                }
            }
        }
        VaultMediaImportSummary(successes, failures, importedIds)
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
                    OriginalMediaRemovalCandidate(
                        mediaId = entity.id,
                        uri = if (mediaType == null) {
                            uri
                        } else {
                            OriginalMediaUriResolver.resolveDeletableUri(uri, mediaType) ?: uri
                        },
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

    data class VaultMediaCounts(
        val total: Int,
        val images: Int,
        val videos: Int,
    )

    private companion object {
        const val DEFAULT_ALBUM_NAME = "私密相册"
    }
}
