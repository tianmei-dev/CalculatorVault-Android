package com.aurora.calculatorvault.feature.privatemedia.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.calculatorvault.feature.privatemedia.data.VaultMediaRepository
import com.aurora.calculatorvault.feature.privatemedia.domain.OriginalMediaRemovalResult
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultOriginalMediaState
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaWithFile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrivateMediaUiState(
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val importProgressCurrent: Int = 0,
    val importProgressTotal: Int = 0,
    val isDeleting: Boolean = false,
    val isRestoring: Boolean = false,
    val media: List<VaultMediaWithFile> = emptyList(),
    val totalCount: Int = 0,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    val selectedMediaIds: Set<Long> = emptySet(),
    val previewMediaId: Long? = null,
    val pendingDeleteMediaIds: Set<Long> = emptySet(),
    val pendingRestoreMediaIds: Set<Long> = emptySet(),
    val pendingOriginalRemovalMediaIds: List<Long> = emptyList(),
) {
    val selectedCount: Int
        get() = selectedMediaIds.size

    val isSelectionMode: Boolean
        get() = selectedMediaIds.isNotEmpty()

    val previewIndex: Int
        get() = media.indexOfFirst { it.media.id == previewMediaId }

    val previewItem: VaultMediaWithFile?
        get() = media.getOrNull(previewIndex)
}

sealed interface PrivateMediaEffect {
    data class ImportCompleted(val successCount: Int, val failureCount: Int) : PrivateMediaEffect
    data object ImportFailed : PrivateMediaEffect
    data class DeleteCompleted(val successCount: Int, val failureCount: Int) : PrivateMediaEffect
    data object DeleteFailed : PrivateMediaEffect
    data class RestoreCompleted(val successCount: Int, val failureCount: Int) : PrivateMediaEffect
    data object RestoreFailed : PrivateMediaEffect
    data class OriginalRemovalCompleted(val successCount: Int, val failureCount: Int) : PrivateMediaEffect
    data object OriginalRemovalKept : PrivateMediaEffect
    data object OriginalRemovalFailed : PrivateMediaEffect
}

class PrivateMediaViewModel(
    private val repository: VaultMediaRepository,
) : ViewModel() {
    private val transientState = MutableStateFlow(TransientState())

    val effects = MutableSharedFlow<PrivateMediaEffect>(extraBufferCapacity = 8)

    val uiState: StateFlow<PrivateMediaUiState> = combine(
        repository.observeDefaultAlbumMedia(),
        repository.observeCounts(),
        transientState,
    ) { media, counts, transient ->
        val validIds = media.map { it.media.id }.toSet()
        PrivateMediaUiState(
            isLoading = false,
            isImporting = transient.isImporting,
            importProgressCurrent = transient.importProgressCurrent,
            importProgressTotal = transient.importProgressTotal,
            isDeleting = transient.isDeleting,
            isRestoring = transient.isRestoring,
            media = media,
            totalCount = counts.total,
            imageCount = counts.images,
            videoCount = counts.videos,
            selectedMediaIds = transient.selectedMediaIds.intersect(validIds),
            previewMediaId = transient.previewMediaId?.takeIf(validIds::contains),
            pendingDeleteMediaIds = transient.pendingDeleteMediaIds,
            pendingRestoreMediaIds = transient.pendingRestoreMediaIds,
            pendingOriginalRemovalMediaIds = transient.pendingOriginalRemovalMediaIds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PrivateMediaUiState(),
    )

    init {
        viewModelScope.launch {
            runCatching { repository.ensureDefaultAlbum() }
            runCatching { repository.cleanupMissingMediaRecords() }
        }
    }

    fun importMedia(uris: List<Uri>) {
        val uniqueUris = uris.distinct().take(MAX_IMPORT_BATCH_SIZE)
        if (uniqueUris.isEmpty() || transientState.value.isImporting) return
        viewModelScope.launch {
            transientState.update {
                it.copy(
                    isImporting = true,
                    importProgressCurrent = 0,
                    importProgressTotal = uniqueUris.size,
                )
            }
            try {
                val summary = repository.importMedia(uniqueUris) { current, total ->
                    transientState.update {
                        it.copy(
                            importProgressCurrent = current,
                            importProgressTotal = total,
                        )
                    }
                }
                if (summary.successCount > 0) {
                    transientState.update {
                        it.copy(pendingOriginalRemovalMediaIds = summary.importedMediaIds)
                    }
                }
                effects.tryEmit(
                    PrivateMediaEffect.ImportCompleted(summary.successCount, summary.failureCount),
                )
            } catch (_: Exception) {
                effects.tryEmit(PrivateMediaEffect.ImportFailed)
            } finally {
                transientState.update {
                    it.copy(
                        isImporting = false,
                        importProgressCurrent = 0,
                        importProgressTotal = 0,
                    )
                }
            }
        }
    }

    fun openPreview(mediaId: Long) {
        if (transientState.value.isDeleting || transientState.value.isRestoring) return
        transientState.update { it.copy(previewMediaId = mediaId) }
    }

    fun closePreview() {
        transientState.update { it.copy(previewMediaId = null) }
    }

    fun previewNext() {
        val state = uiState.value
        val index = state.previewIndex
        if (index >= 0 && index < state.media.lastIndex) {
            transientState.update { it.copy(previewMediaId = state.media[index + 1].media.id) }
        }
    }

    fun previewPrevious() {
        val state = uiState.value
        val index = state.previewIndex
        if (index > 0) {
            transientState.update { it.copy(previewMediaId = state.media[index - 1].media.id) }
        }
    }

    fun enterSelectionMode(mediaId: Long) {
        val transient = transientState.value
        if (transient.isDeleting || transient.isImporting || transient.isRestoring) return
        transientState.update { it.copy(selectedMediaIds = setOf(mediaId)) }
    }

    fun toggleSelection(mediaId: Long) {
        if (transientState.value.isDeleting || transientState.value.isRestoring) return
        transientState.update { transient ->
            val updated = transient.selectedMediaIds.toMutableSet().apply {
                if (!add(mediaId)) remove(mediaId)
            }
            transient.copy(selectedMediaIds = updated)
        }
    }

    fun selectAll() {
        if (transientState.value.isDeleting || transientState.value.isRestoring) return
        transientState.update {
            it.copy(selectedMediaIds = uiState.value.media.map { item -> item.media.id }.toSet())
        }
    }

    fun cancelSelection() {
        if (transientState.value.isDeleting || transientState.value.isRestoring) return
        transientState.update {
            it.copy(
                selectedMediaIds = emptySet(),
                pendingDeleteMediaIds = emptySet(),
                pendingRestoreMediaIds = emptySet(),
            )
        }
    }

    fun requestDelete(mediaId: Long) {
        if (transientState.value.isDeleting || transientState.value.isRestoring) return
        transientState.update { it.copy(pendingDeleteMediaIds = setOf(mediaId)) }
    }

    fun requestDeleteSelection() {
        val selected = transientState.value.selectedMediaIds
        if (selected.isEmpty() || transientState.value.isDeleting || transientState.value.isRestoring) return
        transientState.update { it.copy(pendingDeleteMediaIds = selected) }
    }

    fun cancelDelete() {
        if (transientState.value.isDeleting || transientState.value.isRestoring) return
        transientState.update { it.copy(pendingDeleteMediaIds = emptySet()) }
    }

    fun confirmDelete() {
        val ids = transientState.value.pendingDeleteMediaIds
        if (ids.isEmpty() || transientState.value.isDeleting) return
        viewModelScope.launch {
            transientState.update { it.copy(isDeleting = true) }
            try {
                val stateBeforeDelete = uiState.value
                val summary = repository.deletePrivateMedia(ids.toList())
                effects.tryEmit(
                    PrivateMediaEffect.DeleteCompleted(summary.successCount, summary.failureCount),
                )
                val previewId = transientState.value.previewMediaId
                val nextPreviewId = if (previewId in ids) {
                    val deletedIndex = stateBeforeDelete.media.indexOfFirst { it.media.id == previewId }
                    val remaining = stateBeforeDelete.media.filterNot { it.media.id in ids }
                    when {
                        remaining.isEmpty() -> null
                        deletedIndex <= 0 -> remaining.first().media.id
                        deletedIndex >= remaining.size -> remaining.last().media.id
                        else -> remaining[deletedIndex].media.id
                    }
                } else {
                    previewId
                }
                transientState.update {
                    it.copy(
                        selectedMediaIds = it.selectedMediaIds - ids,
                        pendingDeleteMediaIds = emptySet(),
                        previewMediaId = nextPreviewId,
                    )
                }
            } catch (_: Exception) {
                effects.tryEmit(PrivateMediaEffect.DeleteFailed)
            } finally {
                transientState.update { it.copy(isDeleting = false) }
            }
        }
    }

    fun requestRestore(mediaId: Long) {
        if (transientState.value.isDeleting || transientState.value.isRestoring) return
        transientState.update { it.copy(pendingRestoreMediaIds = setOf(mediaId)) }
    }

    fun requestRestoreSelection() {
        val selected = transientState.value.selectedMediaIds
        if (selected.isEmpty() || transientState.value.isDeleting || transientState.value.isRestoring) return
        transientState.update { it.copy(pendingRestoreMediaIds = selected) }
    }

    fun cancelRestore() {
        if (transientState.value.isRestoring) return
        transientState.update { it.copy(pendingRestoreMediaIds = emptySet()) }
    }

    fun confirmRestore() {
        val ids = transientState.value.pendingRestoreMediaIds
        if (ids.isEmpty() || transientState.value.isRestoring) return
        viewModelScope.launch {
            transientState.update { it.copy(isRestoring = true) }
            try {
                val summary = repository.restoreMediaToSystem(ids.toList())
                effects.tryEmit(
                    PrivateMediaEffect.RestoreCompleted(summary.successCount, summary.failureCount),
                )
                transientState.update {
                    it.copy(
                        selectedMediaIds = emptySet(),
                        pendingRestoreMediaIds = emptySet(),
                    )
                }
            } catch (_: Exception) {
                effects.tryEmit(PrivateMediaEffect.RestoreFailed)
            } finally {
                transientState.update { it.copy(isRestoring = false) }
            }
        }
    }

    fun keepOriginalMedia() {
        transientState.update { it.copy(pendingOriginalRemovalMediaIds = emptyList()) }
        effects.tryEmit(PrivateMediaEffect.OriginalRemovalKept)
    }

    fun dismissOriginalRemovalPrompt() {
        transientState.update { it.copy(pendingOriginalRemovalMediaIds = emptyList()) }
    }

    suspend fun originalRemovalCandidates() =
        repository.originalRemovalCandidates(transientState.value.pendingOriginalRemovalMediaIds)

    fun consumeOriginalRemovalRequest() {
        transientState.update { it.copy(pendingOriginalRemovalMediaIds = emptyList()) }
    }

    fun handleOriginalRemovalResult(result: OriginalMediaRemovalResult) {
        viewModelScope.launch {
            if (result.cancelled) {
                repository.markOriginalRemovalState(result.removedIds, VaultOriginalMediaState.REMOVED)
                effects.tryEmit(PrivateMediaEffect.OriginalRemovalKept)
                return@launch
            }
            if (result.removedIds.isNotEmpty()) {
                repository.markOriginalRemovalState(result.removedIds, VaultOriginalMediaState.REMOVED)
            }
            if (result.failedIds.isNotEmpty()) {
                repository.markOriginalRemovalState(result.failedIds, VaultOriginalMediaState.DELETE_FAILED)
            }
            if (result.unsupportedIds.isNotEmpty()) {
                repository.markOriginalRemovalState(result.unsupportedIds, VaultOriginalMediaState.UNAVAILABLE)
            }
            effects.tryEmit(
                PrivateMediaEffect.OriginalRemovalCompleted(
                    successCount = result.successCount,
                    failureCount = result.failureCount,
                ),
            )
        }
    }

    fun handleOriginalRemovalFailed() {
        effects.tryEmit(PrivateMediaEffect.OriginalRemovalFailed)
    }

    private data class TransientState(
        val isImporting: Boolean = false,
        val importProgressCurrent: Int = 0,
        val importProgressTotal: Int = 0,
        val isDeleting: Boolean = false,
        val isRestoring: Boolean = false,
        val selectedMediaIds: Set<Long> = emptySet(),
        val previewMediaId: Long? = null,
        val pendingDeleteMediaIds: Set<Long> = emptySet(),
        val pendingRestoreMediaIds: Set<Long> = emptySet(),
        val pendingOriginalRemovalMediaIds: List<Long> = emptyList(),
    )

    class Factory(
        private val repository: VaultMediaRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PrivateMediaViewModel(repository) as T
        }
    }

    private companion object {
        const val MAX_IMPORT_BATCH_SIZE = 50
    }
}
