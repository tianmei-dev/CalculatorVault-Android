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
import android.util.Log
import com.aurora.calculatorvault.BuildConfig
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
        debugLog("begin api=${Build.VERSION.SDK_INT} count=${validCandidates.size} kinds=${validCandidates.map { it.uri.safeKind() }}")
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
                debugLog("begin createDeleteRequest failed=${it.javaClass.simpleName}")
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
            debugLog("finish cancelled resultCode=$resultCode")
            return@withContext OriginalMediaRemovalResult(cancelled = true)
        }
        val validCandidates = candidates.filter { it.uri.scheme == ContentResolver.SCHEME_CONTENT }
        debugLog("finish api=${Build.VERSION.SDK_INT} count=${validCandidates.size} kinds=${validCandidates.map { it.uri.safeKind() }}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val removed = mutableListOf<Long>()
            val failed = mutableListOf<Long>()
            validCandidates.forEach { candidate ->
                if (isRemovedFromVisibleGallery(candidate.uri)) {
                    removed += candidate.mediaId
                } else {
                    failed += candidate.mediaId
                }
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
                    debugLog("direct delete returned false kind=${candidate.uri.safeKind()}")
                    failed += candidate.mediaId
                }
            } catch (exception: RecoverableSecurityException) {
                debugLog("direct delete needs user action kind=${candidate.uri.safeKind()}")
                return OriginalMediaRemovalStartResult.RequiresUserAction(
                    intentSender = exception.userAction.actionIntent.intentSender,
                    mediaIds = listOf(candidate.mediaId),
                )
            } catch (_: ActivityNotFoundException) {
                debugLog("direct delete failed=ActivityNotFoundException kind=${candidate.uri.safeKind()}")
                failed += candidate.mediaId
            } catch (exception: SecurityException) {
                debugLog("direct delete failed=${exception.javaClass.simpleName} kind=${candidate.uri.safeKind()}")
                failed += candidate.mediaId
            } catch (exception: IllegalArgumentException) {
                debugLog("direct delete failed=${exception.javaClass.simpleName} kind=${candidate.uri.safeKind()}")
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
        } catch (exception: Exception) {
            debugLog("deleteUri failed=${exception.javaClass.simpleName} kind=${uri.safeKind()}")
            false
        }

    private fun Uri.safeKind(): String =
        listOfNotNull(authority, pathSegments.firstOrNull(), pathSegments.getOrNull(1)).joinToString("/")

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    private companion object {
        const val TAG = "SystemMediaRemoval"
    }

    private fun isRemovedFromVisibleGallery(uri: Uri): Boolean {
        if (!exists(uri)) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return isTrashed(uri)
        }
        return false
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

    private fun isTrashed(uri: Uri): Boolean {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.IS_TRASHED),
                null,
                null,
                null,
            )
            if (cursor?.moveToFirst() == true) {
                cursor.getInt(0) == 1
            } else {
                true
            }
        } catch (_: Exception) {
            false
        } finally {
            cursor?.close()
        }
    }
}
