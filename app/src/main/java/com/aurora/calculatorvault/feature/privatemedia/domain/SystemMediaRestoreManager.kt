package com.aurora.calculatorvault.feature.privatemedia.domain

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.aurora.calculatorvault.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SystemMediaRestoreRequest(
    val mediaId: Long,
    val mediaType: VaultMediaType,
    val sourceFile: File,
    val displayName: String?,
    val mimeType: String?,
)

sealed interface SystemMediaRestoreFailure {
    data object SourceMissing : SystemMediaRestoreFailure
    data object StorageUnavailable : SystemMediaRestoreFailure
    data object PermissionDenied : SystemMediaRestoreFailure
    data object WriteFailed : SystemMediaRestoreFailure
    data object UnsupportedType : SystemMediaRestoreFailure
}

data class SystemMediaRestoreItemResult(
    val mediaId: Long,
    val systemUri: Uri? = null,
    val failure: SystemMediaRestoreFailure? = null,
) {
    val isSuccess: Boolean get() = systemUri != null && failure == null
}

class SystemMediaRestoreManager(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun restore(request: SystemMediaRestoreRequest): SystemMediaRestoreItemResult =
        withContext(ioDispatcher) {
            debugLog("restore media started mediaId=${request.mediaId}")
            val source = request.sourceFile
            if (!source.exists() || !source.isFile || source.length() <= 0L) {
                debugLog("restore failed mediaId=${request.mediaId} reason=SourceMissing")
                return@withContext SystemMediaRestoreItemResult(
                    mediaId = request.mediaId,
                    failure = SystemMediaRestoreFailure.SourceMissing,
                )
            }

            val values = createContentValues(request)
            val targetUri = try {
                contentResolver.insert(collectionUri(request.mediaType), values)
            } catch (_: SecurityException) {
                debugLog("restore failed mediaId=${request.mediaId} reason=PermissionDenied")
                return@withContext SystemMediaRestoreItemResult(
                    mediaId = request.mediaId,
                    failure = SystemMediaRestoreFailure.PermissionDenied,
                )
            } catch (_: IllegalArgumentException) {
                debugLog("restore failed mediaId=${request.mediaId} reason=UnsupportedType")
                return@withContext SystemMediaRestoreItemResult(
                    mediaId = request.mediaId,
                    failure = SystemMediaRestoreFailure.UnsupportedType,
                )
            }

            if (targetUri == null) {
                debugLog("restore failed mediaId=${request.mediaId} reason=StorageUnavailable")
                return@withContext SystemMediaRestoreItemResult(
                    mediaId = request.mediaId,
                    failure = SystemMediaRestoreFailure.StorageUnavailable,
                )
            }

            try {
                contentResolver.openOutputStream(targetUri)?.use { output ->
                    FileInputStream(source).use { input ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                    }
                } ?: throw IOException("OutputStream is null")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !publishPendingItem(targetUri)) {
                    cleanup(targetUri)
                    debugLog("restore failed mediaId=${request.mediaId} reason=WriteFailed")
                    return@withContext SystemMediaRestoreItemResult(
                        mediaId = request.mediaId,
                        failure = SystemMediaRestoreFailure.WriteFailed,
                    )
                }

                debugLog("restore finalize success mediaId=${request.mediaId}")
                SystemMediaRestoreItemResult(mediaId = request.mediaId, systemUri = targetUri)
            } catch (_: SecurityException) {
                cleanup(targetUri)
                debugLog("restore failed mediaId=${request.mediaId} reason=PermissionDenied")
                SystemMediaRestoreItemResult(
                    mediaId = request.mediaId,
                    failure = SystemMediaRestoreFailure.PermissionDenied,
                )
            } catch (_: IOException) {
                cleanup(targetUri)
                debugLog("restore failed mediaId=${request.mediaId} reason=WriteFailed")
                SystemMediaRestoreItemResult(
                    mediaId = request.mediaId,
                    failure = SystemMediaRestoreFailure.WriteFailed,
                )
            } catch (_: Exception) {
                cleanup(targetUri)
                debugLog("restore failed mediaId=${request.mediaId} reason=WriteFailed")
                SystemMediaRestoreItemResult(
                    mediaId = request.mediaId,
                    failure = SystemMediaRestoreFailure.WriteFailed,
                )
            }
        }

    private fun createContentValues(request: SystemMediaRestoreRequest): ContentValues =
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeDisplayName(request))
            put(MediaStore.MediaColumns.MIME_TYPE, safeMimeType(request))
            put(MediaStore.MediaColumns.SIZE, request.sourceFile.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(request.mediaType))
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

    private fun publishPendingItem(uri: Uri): Boolean =
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }

    private fun cleanup(uri: Uri) {
        runCatching {
            contentResolver.delete(uri, null, null)
        }.onFailure {
            debugLog("restore cleanup failed uri=${uri.scheme}")
        }
    }

    private fun collectionUri(mediaType: VaultMediaType): Uri =
        when (mediaType) {
            VaultMediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            VaultMediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    private fun relativePath(mediaType: VaultMediaType): String =
        when (mediaType) {
            VaultMediaType.IMAGE -> "${Environment.DIRECTORY_PICTURES}/CalculatorVault/"
            VaultMediaType.VIDEO -> "${Environment.DIRECTORY_MOVIES}/CalculatorVault/"
        }

    private fun safeMimeType(request: SystemMediaRestoreRequest): String =
        request.mimeType
            ?.takeIf { it.contains('/') && it != "application/octet-stream" }
            ?: when (request.mediaType) {
                VaultMediaType.IMAGE -> "image/jpeg"
                VaultMediaType.VIDEO -> "video/mp4"
            }

    private fun safeDisplayName(request: SystemMediaRestoreRequest): String {
        val original = request.displayName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.replace(Regex("""[\\/:*?"<>|]"""), "_")
        if (original != null) return original
        val extension = when (request.mediaType) {
            VaultMediaType.IMAGE -> ".jpg"
            VaultMediaType.VIDEO -> ".mp4"
        }
        return "CV_${currentTimeMillis()}_${request.mediaId}$extension"
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    private companion object {
        const val TAG = "CV_VAULT_MEDIA"
        const val DEFAULT_BUFFER_SIZE = 128 * 1024
    }
}
