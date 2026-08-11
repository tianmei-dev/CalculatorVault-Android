package com.aurora.calculatorvault.feature.privatemedia.domain

import android.content.ContentResolver
import android.database.Cursor
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.aurora.calculatorvault.feature.privatemedia.data.VaultMediaEntity
import com.aurora.calculatorvault.feature.privatemedia.storage.VaultMediaStorage
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VaultMediaImporter(
    private val contentResolver: ContentResolver,
    private val storage: VaultMediaStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val uuidProvider: () -> String = { UUID.randomUUID().toString() },
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun importToPrivateFile(
        uri: Uri,
        albumId: Long,
    ): Result<VaultMediaEntity> = withContext(ioDispatcher) {
        runCatching {
            val mimeType = contentResolver.getType(uri)?.lowercase()
                ?: throw UnsupportedMediaTypeException()
            val mediaType = when {
                mimeType.startsWith("image/") -> VaultMediaType.IMAGE
                mimeType.startsWith("video/") -> VaultMediaType.VIDEO
                else -> throw UnsupportedMediaTypeException()
            }
            val metadata = readOpenableMetadata(uri)
            val fileName = "${uuidProvider()}.${extensionForMime(mimeType, mediaType)}"
            val tempFile = storage.tempFile(fileName) ?: throw StorageUnavailableException()
            val finalFile = storage.privateFile(fileName) ?: throw StorageUnavailableException()

            try {
                contentResolver.openInputStream(uri).use { input ->
                    if (input == null) throw FileNotFoundException(uri.toString())
                    tempFile.outputStream().buffered().use { output ->
                        input.copyTo(output, bufferSize = COPY_BUFFER_SIZE)
                    }
                }
                if (!tempFile.exists() || tempFile.length() <= 0L) {
                    throw IOException("empty private media copy")
                }
                if (metadata.sizeBytes != null && metadata.sizeBytes > 0L && tempFile.length() <= 0L) {
                    throw IOException("invalid private media copy")
                }
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    if (!tempFile.delete()) Unit
                }
                val dimensions = when (mediaType) {
                    VaultMediaType.IMAGE -> readImageDimensions(finalFile.absolutePath)
                    VaultMediaType.VIDEO -> readVideoMetadata(finalFile.absolutePath)
                }
                VaultMediaEntity(
                    albumId = albumId,
                    mediaType = mediaType.name,
                    privateFileName = fileName,
                    originalDisplayName = metadata.displayName,
                    mimeType = mimeType,
                    sizeBytes = finalFile.length(),
                    width = dimensions.width,
                    height = dimensions.height,
                    durationMs = dimensions.durationMs,
                    importedAt = currentTimeMillis(),
                    originalUri = OriginalMediaUriResolver.resolveDeletableUri(uri, mediaType)?.toString()
                        ?: uri.toString(),
                )
            } catch (error: Exception) {
                runCatching { tempFile.delete() }
                runCatching { finalFile.delete() }
                throw error
            }
        }
    }

    fun failureFor(error: Throwable): VaultMediaImportFailure = when (error) {
        is UnsupportedMediaTypeException -> VaultMediaImportFailure.UnsupportedMediaType
        is StorageUnavailableException -> VaultMediaImportFailure.StorageUnavailable
        is FileNotFoundException,
        is SecurityException,
        is IllegalArgumentException,
        -> VaultMediaImportFailure.SourceUnavailable
        is IOException -> VaultMediaImportFailure.CopyFailed
        else -> VaultMediaImportFailure.CopyFailed
    }

    private fun readOpenableMetadata(uri: Uri): OpenableMetadata {
        var name: String? = null
        var size: Long? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            name = cursor.stringColumn(OpenableColumns.DISPLAY_NAME)
            size = cursor.longColumn(OpenableColumns.SIZE)
        }
        return OpenableMetadata(name, size)
    }

    private fun readImageDimensions(path: String): MediaDimensions {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        return MediaDimensions(
            width = options.outWidth.takeIf { it > 0 },
            height = options.outHeight.takeIf { it > 0 },
            durationMs = null,
        )
    }

    private fun readVideoMetadata(path: String): MediaDimensions {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            MediaDimensions(
                width = retriever.extractInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                height = retriever.extractInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                durationMs = retriever.extractLong(MediaMetadataRetriever.METADATA_KEY_DURATION),
            )
        } catch (_: Exception) {
            MediaDimensions(width = null, height = null, durationMs = null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extensionForMime(mimeType: String, mediaType: VaultMediaType): String {
        val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        return fromMime ?: when (mediaType) {
            VaultMediaType.IMAGE -> "jpg"
            VaultMediaType.VIDEO -> "mp4"
        }
    }

    private fun Cursor.stringColumn(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && moveToFirst()) getString(index) else null
    }

    private fun Cursor.longColumn(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && moveToFirst()) getLong(index) else null
    }

    private fun MediaMetadataRetriever.extractInt(key: Int): Int? =
        extractMetadata(key)?.toIntOrNull()?.takeIf { it > 0 }

    private fun MediaMetadataRetriever.extractLong(key: Int): Long? =
        extractMetadata(key)?.toLongOrNull()?.takeIf { it > 0L }

    private data class OpenableMetadata(
        val displayName: String?,
        val sizeBytes: Long?,
    )

    private data class MediaDimensions(
        val width: Int?,
        val height: Int?,
        val durationMs: Long?,
    )

    private class UnsupportedMediaTypeException : IOException()
    private class StorageUnavailableException : IOException()

    private companion object {
        const val COPY_BUFFER_SIZE = 128 * 1024
    }
}
