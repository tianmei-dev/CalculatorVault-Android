package com.aurora.calculatorvault.feature.privatemedia.presentation

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.core.security.SecureScreenEffect
import com.aurora.calculatorvault.core.security.session.VaultSessionManager
import com.aurora.calculatorvault.feature.disguise.presentation.PageHeader
import com.aurora.calculatorvault.feature.privatemedia.domain.OriginalMediaRemovalCandidate
import com.aurora.calculatorvault.feature.privatemedia.domain.OriginalMediaRemovalStartResult
import com.aurora.calculatorvault.feature.privatemedia.domain.SystemMediaRemovalManager
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultAlbumSummary
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaType
import com.aurora.calculatorvault.feature.privatemedia.domain.VaultMediaWithFile
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultPrimaryButton
import com.aurora.calculatorvault.ui.component.VaultSectionTitle
import com.aurora.calculatorvault.ui.layout.appFabScrollContentPadding
import com.aurora.calculatorvault.ui.message.AppMessage
import com.aurora.calculatorvault.ui.message.AppMessageType
import com.aurora.calculatorvault.ui.message.LocalAppMessageController
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PrivateMediaScreen(
    viewModel: PrivateMediaViewModel,
    sessionManager: VaultSessionManager,
    systemMediaRemovalManager: SystemMediaRemovalManager,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val messageController = LocalAppMessageController.current
    val coroutineScope = rememberCoroutineScope()
    val importFailed = stringResource(R.string.private_media_import_failed)
    val importSuccess = stringResource(R.string.private_media_import_success)
    val importPartial = stringResource(R.string.private_media_import_partial)
    val deleteSuccess = stringResource(R.string.private_media_delete_success)
    val deletePartial = stringResource(R.string.private_media_delete_partial)
    val deleteFailed = stringResource(R.string.private_media_delete_failed)
    val restoreSuccess = stringResource(R.string.private_media_restore_success)
    val restorePartial = stringResource(R.string.private_media_restore_partial)
    val restoreFailed = stringResource(R.string.private_media_restore_failed)
    val originalKept = stringResource(R.string.private_media_original_kept)
    val originalRemoved = stringResource(R.string.private_media_original_removed)
    val originalPartial = stringResource(R.string.private_media_original_remove_partial)
    val originalFailed = stringResource(R.string.private_media_original_remove_failed)
    val originalUnsupported = stringResource(R.string.private_media_original_remove_unsupported)
    val originalPermissionRequired = stringResource(R.string.private_media_original_remove_permission_required)
    val albumCreated = stringResource(R.string.private_media_folder_created)
    val albumCreateFailed = stringResource(R.string.private_media_folder_create_failed)
    val albumRenamed = stringResource(R.string.private_media_folder_renamed)
    val albumRenameFailed = stringResource(R.string.private_media_folder_rename_failed)
    val albumDeleted = stringResource(R.string.private_media_folder_deleted)
    val albumDeleteFailed = stringResource(R.string.private_media_folder_delete_failed)
    val defaultAlbumCannotDelete = stringResource(R.string.private_media_default_folder_cannot_delete)
    val albumNotEmpty = stringResource(R.string.private_media_folder_not_empty)
    val mediaMoved = stringResource(R.string.private_media_move_success)
    val mediaMoveFailed = stringResource(R.string.private_media_move_failed)
    var pendingPickerAlbumId by remember { mutableStateOf<Long?>(null) }
    var showImportAlbumPicker by remember { mutableStateOf(false) }
    var showMoveAlbumPicker by remember { mutableStateOf(false) }
    var albumNameDialog by remember { mutableStateOf<AlbumNameDialogState?>(null) }
    var pendingDeleteAlbum by remember { mutableStateOf<VaultAlbumSummary?>(null) }
    var pendingOriginalRemovalCandidates by remember {
        mutableStateOf<List<OriginalMediaRemovalCandidate>>(emptyList())
    }
    val originalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result: ActivityResult ->
        sessionManager.endExternalResultFlow()
        coroutineScope.launch {
            val candidates = pendingOriginalRemovalCandidates.ifEmpty {
                viewModel.originalRemovalCandidates()
            }
            pendingOriginalRemovalCandidates = emptyList()
            val removalResult = systemMediaRemovalManager.finishAfterUserAction(
                candidates = candidates,
                resultCode = result.resultCode,
            )
            viewModel.consumeOriginalRemovalRequest()
            viewModel.handleOriginalRemovalResult(removalResult)
        }
    }
    fun launchOriginalRemovalFlow() {
        coroutineScope.launch {
            val candidates = viewModel.originalRemovalCandidates()
            when (val startResult = systemMediaRemovalManager.beginRemoval(candidates)) {
                OriginalMediaRemovalStartResult.NoCandidates -> {
                    pendingOriginalRemovalCandidates = emptyList()
                    viewModel.consumeOriginalRemovalRequest()
                    messageController.showWarning(originalUnsupported)
                }
                OriginalMediaRemovalStartResult.Failed -> {
                    pendingOriginalRemovalCandidates = emptyList()
                    viewModel.consumeOriginalRemovalRequest()
                    viewModel.handleOriginalRemovalFailed()
                }
                is OriginalMediaRemovalStartResult.Completed -> {
                    pendingOriginalRemovalCandidates = emptyList()
                    viewModel.consumeOriginalRemovalRequest()
                    viewModel.handleOriginalRemovalResult(startResult.result)
                }
                is OriginalMediaRemovalStartResult.RequiresUserAction -> {
                    pendingOriginalRemovalCandidates = candidates.filter { candidate ->
                        candidate.mediaId in startResult.mediaIds
                    }
                    sessionManager.beginExternalResultFlow()
                    runCatching {
                        originalLauncher.launch(
                            IntentSenderRequest.Builder(startResult.intentSender).build(),
                        )
                    }.onFailure {
                        sessionManager.endExternalResultFlow()
                        pendingOriginalRemovalCandidates = emptyList()
                        viewModel.consumeOriginalRemovalRequest()
                        viewModel.handleOriginalRemovalFailed()
                    }
                }
            }
        }
    }
    val legacyMediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchOriginalRemovalFlow()
        } else {
            messageController.showWarning(originalPermissionRequired)
        }
    }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_PICK_ITEMS),
    ) { uris ->
        // 继续保留安全规则：空结果不导入、不弹移除确认，并结束本次私密会话。
        val albumId = pendingPickerAlbumId
        pendingPickerAlbumId = null
        if (uris.isEmpty()) {
            sessionManager.cancelExternalResultFlowAndLock()
        } else {
            sessionManager.endExternalResultFlow()
            if (albumId != null) {
                viewModel.importMedia(albumId, uris)
            }
        }
    }
    val legacyPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        // ACTION_OPEN_DOCUMENT fallback 返回单项或多项时统一为 List<Uri>。
        val albumId = pendingPickerAlbumId
        pendingPickerAlbumId = null
        if (uris.isEmpty()) {
            sessionManager.cancelExternalResultFlowAndLock()
        } else {
            sessionManager.endExternalResultFlow()
            if (albumId != null) {
                viewModel.importMedia(albumId, uris)
            }
        }
    }

    fun launchPickerForAlbum(albumId: Long) {
        pendingPickerAlbumId = albumId
        sessionManager.beginExternalResultFlow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
            )
        } else {
            legacyPicker.launch(arrayOf("image/*", "video/*"))
        }
    }

    BackHandler(enabled = state.previewItem != null) {
        viewModel.closePreview()
    }
    BackHandler(enabled = state.isSelectionMode && state.previewItem == null) {
        viewModel.cancelSelection()
    }
    BackHandler(enabled = !state.isAlbumHome && !state.isSelectionMode && state.previewItem == null) {
        viewModel.backToAlbumHome()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PrivateMediaEffect.OpenMediaPicker -> launchPickerForAlbum(effect.albumId)
                PrivateMediaEffect.ImportFailed ->
                    messageController.showError(importFailed)
                is PrivateMediaEffect.ImportCompleted -> {
                    when {
                        effect.successCount > 0 && effect.failureCount == 0 ->
                            messageController.show(
                                AppMessage(
                                    message = importSuccess.format(effect.successCount),
                                    type = AppMessageType.Success,
                                ),
                            )
                        effect.successCount > 0 ->
                            messageController.show(
                                AppMessage(
                                    message = importPartial.format(effect.successCount, effect.failureCount),
                                    type = AppMessageType.Warning,
                                ),
                            )
                        else -> messageController.showError(importFailed)
                    }
                }
                PrivateMediaEffect.DeleteFailed -> messageController.showError(deleteFailed)
                is PrivateMediaEffect.RestoreCompleted -> {
                    when {
                        effect.successCount > 0 && effect.failureCount == 0 ->
                            messageController.show(
                                AppMessage(
                                    message = restoreSuccess.format(effect.successCount),
                                    type = AppMessageType.Success,
                                ),
                            )
                        effect.successCount > 0 ->
                            messageController.show(
                                AppMessage(
                                    message = restorePartial.format(effect.successCount, effect.failureCount),
                                    type = AppMessageType.Warning,
                                ),
                            )
                        else -> messageController.showError(restoreFailed)
                    }
                }
                PrivateMediaEffect.RestoreFailed -> messageController.showError(restoreFailed)
                is PrivateMediaEffect.DeleteCompleted -> {
                    when {
                        effect.successCount > 0 && effect.failureCount == 0 ->
                            messageController.show(
                                AppMessage(
                                    message = deleteSuccess.format(effect.successCount),
                                    type = AppMessageType.Success,
                                ),
                            )
                        effect.successCount > 0 ->
                            messageController.show(
                                AppMessage(
                                    message = deletePartial.format(effect.successCount, effect.failureCount),
                                    type = AppMessageType.Warning,
                                ),
                            )
                        else -> messageController.showError(deleteFailed)
                    }
                }
                PrivateMediaEffect.OriginalRemovalKept -> messageController.showInfo(originalKept)
                PrivateMediaEffect.OriginalRemovalFailed -> messageController.showError(originalFailed)
                PrivateMediaEffect.AlbumCreated -> messageController.showSuccess(albumCreated)
                PrivateMediaEffect.AlbumCreateFailed -> messageController.showError(albumCreateFailed)
                PrivateMediaEffect.AlbumRenamed -> messageController.showSuccess(albumRenamed)
                PrivateMediaEffect.AlbumRenameFailed -> messageController.showError(albumRenameFailed)
                PrivateMediaEffect.AlbumDeleted -> messageController.showSuccess(albumDeleted)
                PrivateMediaEffect.AlbumDeleteFailed -> messageController.showError(albumDeleteFailed)
                PrivateMediaEffect.DefaultAlbumCannotDelete -> messageController.showWarning(defaultAlbumCannotDelete)
                is PrivateMediaEffect.AlbumNotEmpty ->
                    messageController.showWarning(albumNotEmpty.format(effect.mediaCount))
                is PrivateMediaEffect.MediaMoved ->
                    messageController.showSuccess(mediaMoved.format(effect.count))
                PrivateMediaEffect.MediaMoveFailed -> messageController.showError(mediaMoveFailed)
                is PrivateMediaEffect.OriginalRemovalCompleted -> {
                    when {
                        effect.successCount > 0 && effect.failureCount == 0 ->
                            messageController.show(
                                AppMessage(
                                    message = originalRemoved.format(effect.successCount),
                                    type = AppMessageType.Success,
                                ),
                            )
                        effect.successCount > 0 ->
                            messageController.show(
                                AppMessage(
                                    message = originalPartial.format(effect.successCount, effect.failureCount),
                                    type = AppMessageType.Warning,
                                ),
                            )
                        else -> messageController.showWarning(originalUnsupported)
                    }
                }
            }
        }
    }

    PrivateMediaContent(
        state = state,
        onImportFromHome = { showImportAlbumPicker = true },
        onImportCurrentAlbum = viewModel::requestImportToCurrentAlbum,
        onOpenAlbum = viewModel::openAlbum,
        onBackToAlbumHome = viewModel::backToAlbumHome,
        onCreateAlbum = {
            albumNameDialog = AlbumNameDialogState(AlbumNameDialogPurpose.Create)
        },
        onRenameAlbum = { album ->
            albumNameDialog = AlbumNameDialogState(
                purpose = AlbumNameDialogPurpose.Rename(album.album.id),
                initialName = album.album.name,
            )
        },
        onDeleteAlbum = { album -> pendingDeleteAlbum = album },
        onMoveSelection = { showMoveAlbumPicker = true },
        onOpenPreview = viewModel::openPreview,
        onLongPressItem = viewModel::enterSelectionMode,
        onToggleSelection = viewModel::toggleSelection,
        onCancelSelection = viewModel::cancelSelection,
        onSelectAll = viewModel::selectAll,
        onRequestRestoreSelection = viewModel::requestRestoreSelection,
        onRequestDeleteSelection = viewModel::requestDeleteSelection,
        onRequestRestorePreview = viewModel::requestRestore,
        onRequestDeletePreview = viewModel::requestDelete,
        onPreviewPrevious = viewModel::previewPrevious,
        onPreviewNext = viewModel::previewNext,
        onClosePreview = viewModel::closePreview,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelRestore = viewModel::cancelRestore,
        onConfirmRestore = viewModel::confirmRestore,
        onKeepOriginalMedia = viewModel::keepOriginalMedia,
        onDismissOriginalRemoval = viewModel::dismissOriginalRemovalPrompt,
        onRemoveOriginalMedia = {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                legacyMediaPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                launchOriginalRemovalFlow()
            }
        },
    )

    if (showImportAlbumPicker) {
        AlbumPickerDialog(
            title = stringResource(R.string.private_media_select_import_folder),
            albums = state.albumSummaries,
            disabledAlbumId = null,
            onSelect = { albumId ->
                showImportAlbumPicker = false
                viewModel.requestImportToAlbum(albumId)
            },
            onCreate = {
                showImportAlbumPicker = false
                albumNameDialog = AlbumNameDialogState(AlbumNameDialogPurpose.CreateForImport)
            },
            onCancel = { showImportAlbumPicker = false },
        )
    }

    if (showMoveAlbumPicker) {
        AlbumPickerDialog(
            title = stringResource(R.string.private_media_select_move_folder),
            albums = state.albumSummaries,
            disabledAlbumId = state.currentAlbumId,
            onSelect = { albumId ->
                showMoveAlbumPicker = false
                viewModel.moveSelectedToAlbum(albumId)
            },
            onCreate = {
                showMoveAlbumPicker = false
                albumNameDialog = AlbumNameDialogState(AlbumNameDialogPurpose.CreateForMove)
            },
            onCancel = { showMoveAlbumPicker = false },
        )
    }

    albumNameDialog?.let { dialogState ->
        AlbumNameDialog(
            title = when (dialogState.purpose) {
                AlbumNameDialogPurpose.Create,
                AlbumNameDialogPurpose.CreateForImport,
                AlbumNameDialogPurpose.CreateForMove -> stringResource(R.string.private_media_create_folder)
                is AlbumNameDialogPurpose.Rename -> stringResource(R.string.private_media_rename_folder)
            },
            initialName = dialogState.initialName,
            onCancel = { albumNameDialog = null },
            onConfirm = { name ->
                albumNameDialog = null
                when (val purpose = dialogState.purpose) {
                    AlbumNameDialogPurpose.Create -> viewModel.createAlbum(name)
                    AlbumNameDialogPurpose.CreateForImport ->
                        viewModel.createAlbum(name, importAfterCreate = true)
                    AlbumNameDialogPurpose.CreateForMove ->
                        viewModel.createAlbum(name, moveSelectionAfterCreate = true)
                    is AlbumNameDialogPurpose.Rename ->
                        viewModel.renameAlbum(purpose.albumId, name)
                }
            },
        )
    }

    pendingDeleteAlbum?.let { summary ->
        DeleteAlbumDialog(
            albumName = summary.album.name,
            isDefault = summary.album.isDefault,
            mediaCount = summary.mediaCount,
            onCancel = { pendingDeleteAlbum = null },
            onConfirm = {
                pendingDeleteAlbum = null
                viewModel.deleteAlbum(summary.album.id)
            },
        )
    }
}

@Composable
private fun PrivateMediaContent(
    state: PrivateMediaUiState,
    onImportFromHome: () -> Unit,
    onImportCurrentAlbum: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onBackToAlbumHome: () -> Unit,
    onCreateAlbum: () -> Unit,
    onRenameAlbum: (VaultAlbumSummary) -> Unit,
    onDeleteAlbum: (VaultAlbumSummary) -> Unit,
    onMoveSelection: () -> Unit,
    onOpenPreview: (Long) -> Unit,
    onLongPressItem: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onCancelSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRequestRestoreSelection: () -> Unit,
    onRequestDeleteSelection: () -> Unit,
    onRequestRestorePreview: (Long) -> Unit,
    onRequestDeletePreview: (Long) -> Unit,
    onPreviewPrevious: () -> Unit,
    onPreviewNext: () -> Unit,
    onClosePreview: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onKeepOriginalMedia: () -> Unit,
    onDismissOriginalRemoval: () -> Unit,
    onRemoveOriginalMedia: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isAlbumHome) {
            AlbumHomeContent(
                state = state,
                onImport = onImportFromHome,
                onCreateAlbum = onCreateAlbum,
                onOpenAlbum = onOpenAlbum,
                onRenameAlbum = onRenameAlbum,
                onDeleteAlbum = onDeleteAlbum,
            )
        } else {
            AlbumDetailContent(
                state = state,
                onImport = onImportCurrentAlbum,
                onBack = onBackToAlbumHome,
                onMoveSelection = onMoveSelection,
                onOpenPreview = onOpenPreview,
                onLongPressItem = onLongPressItem,
                onToggleSelection = onToggleSelection,
                onCancelSelection = onCancelSelection,
                onSelectAll = onSelectAll,
                onRequestRestoreSelection = onRequestRestoreSelection,
                onRequestDeleteSelection = onRequestDeleteSelection,
            )
        }

        if (state.isImporting || state.isDeleting || state.isRestoring) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.BackgroundPrimary.copy(alpha = 0.58f)),
                contentAlignment = Alignment.Center,
            ) {
                VaultCard {
                    CircularProgressIndicator(color = AppColors.AccentPrimary)
                    Text(
                        text = progressMessage(state),
                        style = AppTextStyles.Body,
                        color = AppColors.TextPrimary,
                    )
                }
            }
        }

        state.previewItem?.let { item ->
            PrivateMediaPreview(
                state = state,
                item = item,
                onClose = onClosePreview,
                onRestore = { onRequestRestorePreview(item.media.id) },
                onDelete = { onRequestDeletePreview(item.media.id) },
                onPrevious = onPreviewPrevious,
                onNext = onPreviewNext,
            )
        }

        if (state.pendingDeleteMediaIds.isNotEmpty()) {
            DeletePrivateMediaDialog(
                count = state.pendingDeleteMediaIds.size,
                isDeleting = state.isDeleting,
                onCancel = onCancelDelete,
                onConfirm = onConfirmDelete,
            )
        }

        if (state.pendingRestoreMediaIds.isNotEmpty()) {
            RestoreMediaDialog(
                count = state.pendingRestoreMediaIds.size,
                isRestoring = state.isRestoring,
                onCancel = onCancelRestore,
                onConfirm = onConfirmRestore,
            )
        }

        if (state.pendingOriginalRemovalMediaIds.isNotEmpty()) {
            OriginalMediaRemovalDialog(
                onKeep = onKeepOriginalMedia,
                onRemove = onRemoveOriginalMedia,
                onDismiss = onDismissOriginalRemoval,
            )
        }
    }
}

@Composable
private fun AlbumHomeContent(
    state: PrivateMediaUiState,
    onImport: () -> Unit,
    onCreateAlbum: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onRenameAlbum: (VaultAlbumSummary) -> Unit,
    onDeleteAlbum: (VaultAlbumSummary) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = AppSpacing.xl,
                top = AppSpacing.xl,
                end = AppSpacing.xl,
            ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        PageHeader(
            title = stringResource(R.string.tab_private_media),
            description = stringResource(R.string.private_media_folder_home_description),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ImportMediaHeroButton(
                onClick = onImport,
                enabled = !state.isImporting,
                modifier = Modifier.weight(1f),
            )
            CreateFolderShortcut(
                enabled = !state.isImporting,
                onClick = onCreateAlbum,
            )
        }
        Text(
            text = stringResource(R.string.private_media_folder_count, state.albumSummaries.size),
            style = AppTextStyles.SectionTitle,
            color = AppColors.TextPrimary,
        )
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(
                bottom = appFabScrollContentPadding().calculateBottomPadding(),
            ),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            items(state.albumSummaries, key = { it.album.id }) { summary ->
                FolderCard(
                    summary = summary,
                    onClick = { onOpenAlbum(summary.album.id) },
                    onRename = { onRenameAlbum(summary) },
                    onDelete = { onDeleteAlbum(summary) },
                )
            }
        }
    }
}

@Composable
private fun AlbumDetailContent(
    state: PrivateMediaUiState,
    onImport: () -> Unit,
    onBack: () -> Unit,
    onMoveSelection: () -> Unit,
    onOpenPreview: (Long) -> Unit,
    onLongPressItem: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onCancelSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRequestRestoreSelection: () -> Unit,
    onRequestDeleteSelection: () -> Unit,
) {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(
            start = AppSpacing.xl,
            top = AppSpacing.xl,
            end = AppSpacing.xl,
            bottom = appFabScrollContentPadding().calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                if (state.isSelectionMode) {
                    SelectionHeader(
                        selectedCount = state.selectedCount,
                        allSelected = state.selectedCount == state.media.size && state.media.isNotEmpty(),
                        isDeleting = state.isDeleting,
                        isRestoring = state.isRestoring,
                        onCancelSelection = onCancelSelection,
                        onSelectAll = onSelectAll,
                        onMoveSelection = onMoveSelection,
                        onRequestRestoreSelection = onRequestRestoreSelection,
                        onRequestDeleteSelection = onRequestDeleteSelection,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(
                                VaultIcons.Back,
                                contentDescription = stringResource(R.string.private_media_back_to_folders),
                                tint = AppColors.TextPrimary,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.currentAlbumName ?: stringResource(R.string.tab_private_media),
                                style = AppTextStyles.PageTitle,
                                color = AppColors.TextPrimary,
                                maxLines = 1,
                            )
                            Text(
                                text = stringResource(R.string.private_media_folder_detail_count, state.media.size),
                                style = AppTextStyles.BodySecondary,
                                color = AppColors.TextTertiary,
                            )
                        }
                        TextButton(onClick = onImport, enabled = !state.isImporting) {
                            Text(stringResource(R.string.import_media))
                        }
                    }
                    VaultSectionTitle(stringResource(R.string.private_media_folder_media))
                }
            }
        }
        if (state.media.isEmpty() && !state.isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyPrivateMedia(
                    isImporting = state.isImporting,
                    onImport = onImport,
                )
            }
        } else {
            items(state.media, key = { it.media.id }) { item ->
                VaultMediaGridItem(
                    item = item,
                    isSelected = item.media.id in state.selectedMediaIds,
                    isSelectionMode = state.isSelectionMode,
                    onClick = {
                        if (state.isSelectionMode) {
                            onToggleSelection(item.media.id)
                        } else {
                            onOpenPreview(item.media.id)
                        }
                    },
                    onLongClick = { onLongPressItem(item.media.id) },
                )
            }
        }
    }
}

@Composable
private fun ImportMediaHeroButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(76.dp)
            .clip(AppShapes.Large)
            .background(
                if (enabled) AppColors.AccentPrimary else AppColors.SurfaceSecondary,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            VaultIcons.Image,
            contentDescription = null,
            tint = if (enabled) AppColors.TextPrimary else AppColors.TextDisabled,
            modifier = Modifier.size(30.dp),
        )
        Spacer(modifier = Modifier.size(AppSpacing.md))
        Text(
            text = stringResource(R.string.import_media),
            style = AppTextStyles.SectionTitle,
            color = if (enabled) AppColors.TextPrimary else AppColors.TextDisabled,
            maxLines = 1,
        )
    }
}

@Composable
private fun CreateFolderShortcut(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(width = 108.dp, height = 92.dp)
            .clip(AppShapes.Large)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Icon(
                VaultIcons.Files,
                contentDescription = null,
                tint = if (enabled) AppColors.AccentPrimary else AppColors.TextDisabled,
                modifier = Modifier.size(42.dp),
            )
            Icon(
                VaultIcons.Add,
                contentDescription = null,
                tint = if (enabled) AppColors.AccentPrimary else AppColors.TextDisabled,
                modifier = Modifier
                    .size(18.dp)
                    .background(AppColors.BackgroundPrimary, AppShapes.Small),
            )
        }
        Spacer(modifier = Modifier.height(AppSpacing.xs))
        Text(
            text = stringResource(R.string.private_media_create_folder),
            style = AppTextStyles.BodySecondary,
            color = if (enabled) AppColors.AccentPrimary else AppColors.TextDisabled,
            maxLines = 1,
        )
    }
}

@Composable
private fun FolderCard(
    summary: VaultAlbumSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    VaultCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
        ) {
            FolderCover(
                cover = summary.cover,
                modifier = Modifier.size(88.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                Text(
                    text = summary.album.name,
                    style = AppTextStyles.SectionTitle,
                    color = AppColors.TextPrimary,
                    maxLines = 2,
                )
                Text(
                    text = stringResource(R.string.private_media_folder_item_count, summary.mediaCount),
                    style = AppTextStyles.BodySecondary,
                    color = AppColors.TextTertiary,
                    maxLines = 1,
                )
            }
            Icon(
                VaultIcons.Chevron,
                contentDescription = null,
                tint = AppColors.TextTertiary,
                modifier = Modifier.size(28.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onRename) {
                Icon(
                    VaultIcons.Edit,
                    contentDescription = null,
                    tint = AppColors.AccentPrimary,
                )
                Spacer(modifier = Modifier.size(AppSpacing.xs))
                Text(
                    text = stringResource(R.string.private_media_rename_folder),
                    color = AppColors.AccentPrimary,
                    maxLines = 1,
                )
            }
            TextButton(
                onClick = onDelete,
                enabled = !summary.album.isDefault,
            ) {
                Icon(
                    VaultIcons.Delete,
                    contentDescription = null,
                    tint = if (summary.album.isDefault) AppColors.TextDisabled else AppColors.Error,
                )
                Spacer(modifier = Modifier.size(AppSpacing.xs))
                Text(
                    text = stringResource(R.string.delete),
                    color = if (summary.album.isDefault) AppColors.TextDisabled else AppColors.Error,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FolderCover(
    cover: VaultMediaWithFile?,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1.18f),
) {
    Box(
        modifier = modifier
            .clip(AppShapes.Medium)
            .background(AppColors.SurfaceSecondary),
        contentAlignment = Alignment.Center,
    ) {
        if (cover == null) {
            Icon(VaultIcons.Files, contentDescription = null, tint = AppColors.AccentPrimary)
            return@Box
        }
        val bitmap by produceState<Bitmap?>(initialValue = null, cover.file.absolutePath) {
            value = withContext(Dispatchers.IO) {
                when (cover.media.mediaType) {
                    VaultMediaType.IMAGE -> decodeSampledBitmap(cover.file, 512)
                    VaultMediaType.VIDEO -> decodeVideoFrame(cover.file)
                }
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = cover.media.originalDisplayName
                    ?: stringResource(R.string.private_media_thumbnail),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                if (cover.media.mediaType == VaultMediaType.VIDEO) VaultIcons.Video else VaultIcons.Image,
                contentDescription = null,
                tint = AppColors.TextDisabled,
            )
        }
        if (cover.media.mediaType == VaultMediaType.VIDEO) {
            Icon(
                VaultIcons.Video,
                contentDescription = null,
                tint = AppColors.TextPrimary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(AppShapes.Small)
                    .background(AppColors.BackgroundPrimary.copy(alpha = 0.72f))
                    .padding(6.dp),
            )
        }
    }
}

private fun LazyGridScope.header(
    state: PrivateMediaUiState,
    onImport: () -> Unit,
    onCancelSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRequestRestoreSelection: () -> Unit,
    onRequestDeleteSelection: () -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
            if (state.isSelectionMode) {
                SelectionHeader(
                    selectedCount = state.selectedCount,
                    allSelected = state.selectedCount == state.media.size && state.media.isNotEmpty(),
                    isDeleting = state.isDeleting,
                    isRestoring = state.isRestoring,
                    onCancelSelection = onCancelSelection,
                    onSelectAll = onSelectAll,
                    onMoveSelection = {},
                    onRequestRestoreSelection = onRequestRestoreSelection,
                    onRequestDeleteSelection = onRequestDeleteSelection,
                )
            } else {
                PageHeader(
                    title = stringResource(R.string.tab_private_media),
                    description = stringResource(R.string.private_media_description),
                )
                VaultSectionTitle(stringResource(R.string.media_overview))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                ) {
                    MediaStat(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.photos),
                        value = state.imageCount.toString(),
                        icon = VaultIcons.Image,
                    )
                    MediaStat(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.videos),
                        value = state.videoCount.toString(),
                        icon = VaultIcons.Video,
                    )
                }
                VaultPrimaryButton(
                    text = stringResource(R.string.import_media),
                    onClick = onImport,
                    enabled = !state.isImporting,
                )
                VaultSectionTitle(
                    stringResource(R.string.private_media_all_count, state.totalCount),
                )
            }
        }
    }
}

@Composable
private fun SelectionHeader(
    selectedCount: Int,
    allSelected: Boolean,
    isDeleting: Boolean,
    isRestoring: Boolean,
    onCancelSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onMoveSelection: () -> Unit,
    onRequestRestoreSelection: () -> Unit,
    onRequestDeleteSelection: () -> Unit,
) {
    VaultCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            IconButton(
                onClick = onCancelSelection,
                enabled = !isDeleting && !isRestoring,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(VaultIcons.Close, contentDescription = stringResource(R.string.cancel))
            }
            Text(
                text = stringResource(R.string.private_media_selected_count, selectedCount),
                modifier = Modifier.weight(1f),
                style = AppTextStyles.SectionTitle,
                color = AppColors.TextPrimary,
            )
            TextButton(onClick = onSelectAll, enabled = !isDeleting && !isRestoring && !allSelected) {
                Text(stringResource(R.string.hidden_app_select_all))
            }
            TextButton(onClick = onMoveSelection, enabled = !isDeleting && !isRestoring && selectedCount > 0) {
                Text(stringResource(R.string.private_media_move_to_folder))
            }
            TextButton(onClick = onRequestRestoreSelection, enabled = !isDeleting && !isRestoring && selectedCount > 0) {
                Text(stringResource(R.string.private_media_restore_action))
            }
            TextButton(onClick = onRequestDeleteSelection, enabled = !isDeleting && !isRestoring && selectedCount > 0) {
                Text(
                    text = stringResource(R.string.delete),
                    color = AppColors.Error,
                )
            }
        }
    }
}

@Composable
private fun MediaStat(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    VaultCard(modifier = modifier) {
        Icon(icon, contentDescription = null, tint = AppColors.AccentPrimary)
        Text(value, style = AppTextStyles.SectionTitle, color = AppColors.TextPrimary)
        Text(label, style = AppTextStyles.BodySecondary, color = AppColors.TextTertiary)
    }
}

@Composable
private fun EmptyPrivateMedia(
    isImporting: Boolean,
    onImport: () -> Unit,
) {
    VaultCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppSpacing.xl),
    ) {
        Icon(VaultIcons.Photos, contentDescription = null, tint = AppColors.AccentPrimary)
        Text(
            text = stringResource(R.string.private_media_empty_title),
            style = AppTextStyles.SectionTitle,
            color = AppColors.TextPrimary,
        )
        Spacer(modifier = Modifier.height(AppSpacing.md))
        Text(
            text = stringResource(R.string.private_media_empty_description),
            style = AppTextStyles.BodySecondary,
            color = AppColors.TextTertiary,
        )
        Spacer(modifier = Modifier.height(AppSpacing.xxl))
        VaultPrimaryButton(
            text = stringResource(R.string.import_media),
            onClick = onImport,
            enabled = !isImporting,
        )
    }
}

@Composable
private fun progressMessage(state: PrivateMediaUiState): String =
    when {
        state.isDeleting -> stringResource(R.string.private_media_deleting)
        state.isRestoring -> stringResource(R.string.private_media_restoring)
        state.isImporting &&
            state.importProgressTotal > 1 &&
            state.importProgressCurrent > 0 ->
            stringResource(
                R.string.private_media_importing_progress,
                state.importProgressCurrent,
                state.importProgressTotal,
            )
        else -> stringResource(R.string.private_media_importing)
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultMediaGridItem(
    item: VaultMediaWithFile,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(AppShapes.Medium)
            .background(AppColors.SurfaceSecondary)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap by produceState<Bitmap?>(initialValue = null, item.file.absolutePath) {
            value = withContext(Dispatchers.IO) {
                when (item.media.mediaType) {
                    VaultMediaType.IMAGE -> decodeSampledBitmap(item.file, 512)
                    VaultMediaType.VIDEO -> decodeVideoFrame(item.file)
                }
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.media.originalDisplayName
                    ?: stringResource(R.string.private_media_thumbnail),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                if (item.media.mediaType == VaultMediaType.VIDEO) VaultIcons.Video else VaultIcons.Image,
                contentDescription = null,
                tint = AppColors.TextDisabled,
            )
        }
        if (item.media.mediaType == VaultMediaType.VIDEO) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(AppShapes.Small)
                    .background(AppColors.BackgroundPrimary.copy(alpha = 0.72f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(VaultIcons.Video, contentDescription = null, tint = AppColors.TextPrimary)
                Text(
                    text = formatDuration(item.media.durationMs),
                    style = AppTextStyles.Caption,
                    color = AppColors.TextPrimary,
                )
            }
        }
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.BackgroundPrimary.copy(alpha = if (isSelected) 0.42f else 0.18f)),
            )
            Icon(
                imageVector = if (isSelected) VaultIcons.Selected else VaultIcons.Unselected,
                contentDescription = stringResource(
                    if (isSelected) R.string.hidden_app_selected else R.string.hidden_app_unselected,
                ),
                tint = if (isSelected) AppColors.AccentPrimary else AppColors.TextSecondary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun PrivateMediaPreview(
    state: PrivateMediaUiState,
    item: VaultMediaWithFile,
    onClose: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    SecureScreenEffect()
    var controlsVisible by remember(item.media.id) { mutableStateOf(true) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BackgroundPrimary),
    ) {
        when (item.media.mediaType) {
            VaultMediaType.IMAGE -> PrivateImagePreview(
                item = item,
                controlsVisible = controlsVisible,
                onToggleControls = { controlsVisible = !controlsVisible },
            )
            VaultMediaType.VIDEO -> PrivateVideoPreview(item = item)
        }

        if (controlsVisible || item.media.mediaType == VaultMediaType.VIDEO) {
            PreviewTopBar(
                title = stringResource(
                    R.string.private_media_preview_index,
                    state.previewIndex + 1,
                    state.media.size,
                ),
                subtitle = item.media.originalDisplayName ?: stringResource(R.string.private_media_unnamed),
                onClose = onClose,
                onRestore = onRestore,
                onDelete = onDelete,
            )
            PreviewNavigation(
                canPrevious = state.previewIndex > 0,
                canNext = state.previewIndex >= 0 && state.previewIndex < state.media.lastIndex,
                onPrevious = onPrevious,
                onNext = onNext,
            )
            PreviewMetadata(item)
        }
    }
}

@Composable
private fun PrivateImagePreview(
    item: VaultMediaWithFile,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, item.file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            decodeSampledBitmap(item.file, 1600)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onToggleControls),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.media.originalDisplayName
                    ?: stringResource(R.string.private_media_preview_image),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            BrokenMediaState(
                icon = VaultIcons.Image,
                message = stringResource(R.string.private_media_image_unavailable),
            )
        }
        if (!controlsVisible) {
            Spacer(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PrivateVideoPreview(item: VaultMediaWithFile) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var videoView by remember(item.media.id) { mutableStateOf<VideoView?>(null) }
    var hasError by remember(item.media.id) { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, videoView) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) {
                videoView?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { videoView?.stopPlayback() }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                VideoView(context).apply {
                    videoView = this
                    val controller = MediaController(context)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setVideoURI(Uri.fromFile(item.file))
                    setOnPreparedListener {
                        start()
                        controller.show()
                    }
                    setOnErrorListener { _, _, _ ->
                        hasError = true
                        true
                    }
                }
            },
            update = { view ->
                if (videoView !== view) videoView = view
            },
        )
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.BackgroundPrimary),
                contentAlignment = Alignment.Center,
            ) {
                BrokenMediaState(
                    icon = VaultIcons.Video,
                    message = stringResource(R.string.private_media_video_unavailable),
                )
            }
        }
    }
}

@Composable
private fun PreviewTopBar(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.BackgroundPrimary.copy(alpha = 0.82f))
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(VaultIcons.Back, contentDescription = stringResource(R.string.back), tint = AppColors.TextPrimary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = AppTextStyles.SectionTitle, color = AppColors.TextPrimary)
            Text(subtitle, style = AppTextStyles.Caption, color = AppColors.TextTertiary, maxLines = 1)
        }
        TextButton(onClick = onRestore) {
            Text(text = stringResource(R.string.private_media_restore_to_gallery_action))
        }
        TextButton(onClick = onDelete) {
            Text(text = stringResource(R.string.delete), color = AppColors.Error)
        }
    }
}

@Composable
private fun BoxScope.PreviewNavigation(
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xl)
            .padding(top = 96.dp)
            .align(Alignment.TopCenter),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious, enabled = canPrevious) {
            Text(stringResource(R.string.previous))
        }
        TextButton(onClick = onNext, enabled = canNext) {
            Text(stringResource(R.string.next))
        }
    }
}

@Composable
private fun BoxScope.PreviewMetadata(item: VaultMediaWithFile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .background(AppColors.BackgroundPrimary.copy(alpha = 0.72f))
            .padding(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        Text(
            text = item.media.originalDisplayName ?: stringResource(R.string.private_media_unnamed),
            style = AppTextStyles.Body,
            color = AppColors.TextPrimary,
        )
        Text(
            text = buildString {
                append(if (item.media.mediaType == VaultMediaType.VIDEO) stringResource(R.string.videos) else stringResource(R.string.photos))
                append(" · ")
                append(formatFileSize(item.media.sizeBytes))
                item.media.width?.let { width ->
                    val height = item.media.height
                    if (height != null) append(" · ${width}×$height")
                }
                if (item.media.mediaType == VaultMediaType.VIDEO) {
                    append(" · ")
                    append(formatDuration(item.media.durationMs))
                }
            },
            style = AppTextStyles.Caption,
            color = AppColors.TextTertiary,
        )
    }
}

@Composable
private fun BrokenMediaState(
    icon: ImageVector,
    message: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.TextDisabled)
        Text(message, style = AppTextStyles.Body, color = AppColors.TextSecondary)
    }
}

private sealed interface AlbumNameDialogPurpose {
    data object Create : AlbumNameDialogPurpose
    data object CreateForImport : AlbumNameDialogPurpose
    data object CreateForMove : AlbumNameDialogPurpose
    data class Rename(val albumId: Long) : AlbumNameDialogPurpose
}

private data class AlbumNameDialogState(
    val purpose: AlbumNameDialogPurpose,
    val initialName: String = "",
)

@Composable
private fun AlbumNameDialog(
    title: String,
    initialName: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val trimmed = name.trim()
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = AppColors.SurfacePrimary,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_ALBUM_NAME_LENGTH) },
                label = { Text(stringResource(R.string.private_media_folder_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun AlbumPickerDialog(
    title: String,
    albums: List<VaultAlbumSummary>,
    disabledAlbumId: Long?,
    onSelect: (Long) -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = AppColors.SurfacePrimary,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                albums.forEach { summary ->
                    val enabled = summary.album.id != disabledAlbumId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.Medium)
                            .clickable(enabled = enabled) { onSelect(summary.album.id) }
                            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(AppShapes.Small)
                                .background(AppColors.AccentContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                VaultIcons.Files,
                                contentDescription = null,
                                tint = if (enabled) AppColors.AccentPrimary else AppColors.TextDisabled,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = summary.album.name,
                                style = AppTextStyles.Body,
                                color = if (enabled) AppColors.TextPrimary else AppColors.TextDisabled,
                                maxLines = 1,
                            )
                            Text(
                                text = stringResource(R.string.private_media_folder_item_count, summary.mediaCount),
                                style = AppTextStyles.Caption,
                                color = if (enabled) AppColors.TextTertiary else AppColors.TextDisabled,
                                maxLines = 1,
                            )
                        }
                        Icon(
                            VaultIcons.Chevron,
                            contentDescription = null,
                            tint = if (enabled) AppColors.TextTertiary else AppColors.TextDisabled,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate) {
                Text(stringResource(R.string.private_media_create_folder))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun DeleteAlbumDialog(
    albumName: String,
    isDefault: Boolean,
    mediaCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = AppColors.SurfacePrimary,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary,
        title = {
            Text(
                text = when {
                    isDefault -> stringResource(R.string.private_media_default_folder_cannot_delete)
                    mediaCount > 0 -> stringResource(R.string.private_media_folder_not_empty_title)
                    else -> stringResource(R.string.private_media_delete_folder_title)
                },
            )
        },
        text = {
            Text(
                text = when {
                    isDefault -> stringResource(R.string.private_media_default_folder_cannot_delete_message)
                    mediaCount > 0 -> stringResource(R.string.private_media_folder_not_empty_message, mediaCount)
                    else -> stringResource(R.string.private_media_delete_folder_message, albumName)
                },
            )
        },
        confirmButton = {
            if (!isDefault && mediaCount == 0) {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.delete), color = AppColors.Error)
                }
            } else {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.got_it))
                }
            }
        },
        dismissButton = {
            if (!isDefault && mediaCount == 0) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun DeletePrivateMediaDialog(
    count: Int,
    isDeleting: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onCancel() },
        containerColor = AppColors.SurfacePrimary,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary,
        title = {
            Text(
                text = stringResource(
                    if (count == 1) {
                        R.string.private_media_delete_single_title
                    } else {
                        R.string.private_media_delete_batch_title
                    },
                ),
            )
        },
        text = {
            Text(
                text = if (count == 1) {
                    stringResource(R.string.private_media_delete_single_message)
                } else {
                    stringResource(R.string.private_media_delete_batch_message, count)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isDeleting) {
                Text(stringResource(R.string.delete), color = AppColors.Error)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isDeleting) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun RestoreMediaDialog(
    count: Int,
    isRestoring: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isRestoring) onCancel() },
        containerColor = AppColors.SurfacePrimary,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary,
        title = {
            Text(text = stringResource(R.string.private_media_restore_title))
        },
        text = {
            Text(
                text = if (count == 1) {
                    stringResource(R.string.private_media_restore_message_single)
                } else {
                    stringResource(R.string.private_media_restore_message_batch, count)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isRestoring) {
                Text(stringResource(R.string.private_media_restore_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isRestoring) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun OriginalMediaRemovalDialog(
    onKeep: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.SurfacePrimary,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary,
        title = {
            Text(text = stringResource(R.string.private_media_original_remove_title))
        },
        text = {
            Text(text = stringResource(R.string.private_media_original_remove_message))
        },
        confirmButton = {
            TextButton(onClick = onRemove) {
                Text(
                    text = stringResource(R.string.private_media_original_remove_action),
                    color = AppColors.Error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep) {
                Text(stringResource(R.string.private_media_original_keep_action))
            }
        },
    )
}

private fun decodeSampledBitmap(file: File, maxSize: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
    }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
    return applyExifOrientation(bitmap, file)
}

private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sample = 1
    while (width / sample > maxSize || height / sample > maxSize) {
        sample *= 2
    }
    return sample
}

private fun decodeVideoFrame(file: File): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.getFrameAtTime(0)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun applyExifOrientation(bitmap: Bitmap, file: File): Bitmap {
    val orientation = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }

    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { rotated ->
            if (rotated != bitmap) bitmap.recycle()
        }
    }.getOrElse {
        bitmap
    }
}

private fun formatDuration(durationMs: Long?): String {
    val totalSeconds = ((durationMs ?: 0L) / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun formatFileSize(sizeBytes: Long): String {
    val kb = sizeBytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        "%.1f MB".format(mb)
    } else {
        "%.0f KB".format(kb.coerceAtLeast(1.0))
    }
}

private const val MAX_PICK_ITEMS = 50
private const val MAX_ALBUM_NAME_LENGTH = 30
