package com.aurora.calculatorvault.feature.privatemedia.domain

import android.app.Activity
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.IntentSender
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OriginalMediaRemovalCandidate(
    val mediaId: Long,
    val uri: Uri,
)

sealed interface OriginalMediaRemovalStartResult {
    data object NoCandidates : OriginalMediaRemovalStartResult
    data class RequiresUserAction(
        val intentSender: IntentSender,
        val mediaIds: List<Long>,
    ) : OriginalMediaRemovalStartResult
    data class Completed(val result: OriginalMediaRemovalResult) : OriginalMediaRemovalStartResult
    data object Failed : OriginalMediaRemovalStartResult
}

data class OriginalMediaRemovalResult(
    val removedIds: List<Long> = emptyList(),
    val failedIds: List<Long> = emptyList(),
    val cancelled: Boolean = false,
    val unsupportedIds: List<Long> = emptyList(),
) {
    val successCount: Int get() = removedIds.size
    val failureCount: Int get() = failedIds.size + unsupportedIds.size
}

class SystemMediaRemovalManager(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun beginRemoval(
        candidates: List<OriginalMediaRemovalCandidate>,
    ): OriginalMediaRemovalStartResult = withContext(ioDispatcher) {
        val validCandidates = candidates.filter { it.uri.scheme == ContentResolver.SCHEME_CONTENT }
        if (validCandidates.isEmpty()) return@withContext OriginalMediaRemovalStartResult.NoCandidates

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext runCatching {
                val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(
                    contentResolver,
                    validCandidates.map { it.uri },
                )
                OriginalMediaRemovalStartResult.RequiresUserAction(
                    intentSender = pendingIntent.intentSender,
                    mediaIds = validCandidates.map { it.mediaId },
                )
            }.getOrElse {
                OriginalMediaRemovalStartResult.Failed
            }
        }

        removeDirectlyOrRequestUserAction(validCandidates)
    }

    suspend fun finishAfterUserAction(
        candidates: List<OriginalMediaRemovalCandidate>,
        resultCode: Int,
    ): OriginalMediaRemovalResult = withContext(ioDispatcher) {
        if (resultCode != Activity.RESULT_OK) {
            return@withContext OriginalMediaRemovalResult(cancelled = true)
        }
        val validCandidates = candidates.filter { it.uri.scheme == ContentResolver.SCHEME_CONTENT }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val removed = mutableListOf<Long>()
            val failed = mutableListOf<Long>()
            validCandidates.forEach { candidate ->
                if (exists(candidate.uri)) failed += candidate.mediaId else removed += candidate.mediaId
            }
            OriginalMediaRemovalResult(removedIds = removed, failedIds = failed)
        } else {
            val removed = mutableListOf<Long>()
            val failed = mutableListOf<Long>()
            validCandidates.forEach { candidate ->
                if (deleteUri(candidate.uri)) removed += candidate.mediaId else failed += candidate.mediaId
            }
            OriginalMediaRemovalResult(removedIds = removed, failedIds = failed)
        }
    }

    private fun removeDirectlyOrRequestUserAction(
        candidates: List<OriginalMediaRemovalCandidate>,
    ): OriginalMediaRemovalStartResult {
        val removed = mutableListOf<Long>()
        val failed = mutableListOf<Long>()
        candidates.forEach { candidate ->
            try {
                if (deleteUri(candidate.uri)) {
                    removed += candidate.mediaId
                } else {
                    failed += candidate.mediaId
                }
            } catch (exception: RecoverableSecurityException) {
                return OriginalMediaRemovalStartResult.RequiresUserAction(
                    intentSender = exception.userAction.actionIntent.intentSender,
                    mediaIds = listOf(candidate.mediaId),
                )
            } catch (_: ActivityNotFoundException) {
                failed += candidate.mediaId
            } catch (_: SecurityException) {
                failed += candidate.mediaId
            } catch (_: IllegalArgumentException) {
                failed += candidate.mediaId
            }
        }
        return OriginalMediaRemovalStartResult.Completed(
            OriginalMediaRemovalResult(removedIds = removed, failedIds = failed),
        )
    }

    private fun deleteUri(uri: Uri): Boolean =
        try {
            contentResolver.delete(uri, null, null) > 0 || !exists(uri)
        } catch (exception: RecoverableSecurityException) {
            throw exception
        } catch (_: Exception) {
            false
        }

    private fun exists(uri: Uri): Boolean {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            cursor?.moveToFirst() == true
        } catch (_: Exception) {
            false
        } finally {
            cursor?.close()
        }
    }
}
