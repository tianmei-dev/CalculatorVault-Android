package com.aurora.calculatorvault.feature.privatemedia.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import com.aurora.calculatorvault.feature.privatemedia.domain.OriginalMediaRemovalStartResult
import com.aurora.calculatorvault.feature.privatemedia.domain.SystemMediaRemovalManager
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
    val messageController = LocalAppMessageController.current
    val coroutineScope = rememberCoroutineScope()
    val importFailed = stringResource(R.string.private_media_import_failed)
    val importSuccess = stringResource(R.string.private_media_import_success)
    val importPartial = stringResource(R.string.private_media_import_partial)
    val deleteSuccess = stringResource(R.string.private_media_delete_success)
    val deletePartial = stringResource(R.string.private_media_delete_partial)
    val deleteFailed = stringResource(R.string.private_media_delete_failed)
    val originalKept = stringResource(R.string.private_media_original_kept)
    val originalRemoved = stringResource(R.string.private_media_original_removed)
    val originalPartial = stringResource(R.string.private_media_original_remove_partial)
    val originalFailed = stringResource(R.string.private_media_original_remove_failed)
    val originalUnsupported = stringResource(R.string.private_media_original_remove_unsupported)
    val originalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result: ActivityResult ->
        sessionManager.endExternalResultFlow()
        coroutineScope.launch {
            val candidates = viewModel.originalRemovalCandidates()
            val removalResult = systemMediaRemovalManager.finishAfterUserAction(
                candidates = candidates,
                resultCode = result.resultCode,
            )
            viewModel.consumeOriginalRemovalRequest()
            viewModel.handleOriginalRemovalResult(removalResult)
        }
    }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_PICK_ITEMS),
    ) { uris ->
        sessionManager.endExternalResultFlow()
        viewModel.importMedia(uris)
    }

    BackHandler(enabled = state.previewItem != null) {
        viewModel.closePreview()
    }
    BackHandler(enabled = state.isSelectionMode && state.previewItem == null) {
        viewModel.cancelSelection()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
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
        onImport = {
            sessionManager.beginExternalResultFlow()
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
            )
        },
        onOpenPreview = viewModel::openPreview,
        onLongPressItem = viewModel::enterSelectionMode,
        onToggleSelection = viewModel::toggleSelection,
        onCancelSelection = viewModel::cancelSelection,
        onSelectAll = viewModel::selectAll,
        onRequestDeleteSelection = viewModel::requestDeleteSelection,
        onRequestDeletePreview = viewModel::requestDelete,
        onPreviewPrevious = viewModel::previewPrevious,
        onPreviewNext = viewModel::previewNext,
        onClosePreview = viewModel::closePreview,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onKeepOriginalMedia = viewModel::keepOriginalMedia,
        onDismissOriginalRemoval = viewModel::dismissOriginalRemovalPrompt,
        onRemoveOriginalMedia = {
            coroutineScope.launch {
                val candidates = viewModel.originalRemovalCandidates()
                when (val startResult = systemMediaRemovalManager.beginRemoval(candidates)) {
                    OriginalMediaRemovalStartResult.NoCandidates -> {
                        viewModel.consumeOriginalRemovalRequest()
                        messageController.showWarning(originalUnsupported)
                    }
                    OriginalMediaRemovalStartResult.Failed -> {
                        viewModel.consumeOriginalRemovalRequest()
                        viewModel.handleOriginalRemovalFailed()
                    }
                    is OriginalMediaRemovalStartResult.Completed -> {
                        viewModel.consumeOriginalRemovalRequest()
                        viewModel.handleOriginalRemovalResult(startResult.result)
                    }
                    is OriginalMediaRemovalStartResult.RequiresUserAction -> {
                        sessionManager.beginExternalResultFlow()
                        runCatching {
                            originalLauncher.launch(
                                IntentSenderRequest.Builder(startResult.intentSender).build(),
                            )
                        }.onFailure {
                            sessionManager.endExternalResultFlow()
                            viewModel.consumeOriginalRemovalRequest()
                            viewModel.handleOriginalRemovalFailed()
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun PrivateMediaContent(
    state: PrivateMediaUiState,
    onImport: () -> Unit,
    onOpenPreview: (Long) -> Unit,
    onLongPressItem: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onCancelSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRequestDeleteSelection: () -> Unit,
    onRequestDeletePreview: (Long) -> Unit,
    onPreviewPrevious: () -> Unit,
    onPreviewNext: () -> Unit,
    onClosePreview: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onKeepOriginalMedia: () -> Unit,
    onDismissOriginalRemoval: () -> Unit,
    onRemoveOriginalMedia: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
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
            header(
                state = state,
                onImport = onImport,
                onCancelSelection = onCancelSelection,
                onSelectAll = onSelectAll,
                onRequestDeleteSelection = onRequestDeleteSelection,
            )
            if (state.media.isEmpty() && !state.isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyPrivateMedia(onImport = onImport)
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

        if (state.isImporting || state.isDeleting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.BackgroundPrimary.copy(alpha = 0.58f)),
                contentAlignment = Alignment.Center,
            ) {
                VaultCard {
                    CircularProgressIndicator(color = AppColors.AccentPrimary)
                    Text(
                        text = stringResource(
                            if (state.isDeleting) {
                                R.string.private_media_deleting
                            } else {
                                R.string.private_media_importing
                            },
                        ),
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

        if (state.pendingOriginalRemovalMediaIds.isNotEmpty()) {
            OriginalMediaRemovalDialog(
                onKeep = onKeepOriginalMedia,
                onRemove = onRemoveOriginalMedia,
                onDismiss = onDismissOriginalRemoval,
            )
        }
    }
}

private fun LazyGridScope.header(
    state: PrivateMediaUiState,
    onImport: () -> Unit,
    onCancelSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRequestDeleteSelection: () -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
            if (state.isSelectionMode) {
                SelectionHeader(
                    selectedCount = state.selectedCount,
                    allSelected = state.selectedCount == state.media.size && state.media.isNotEmpty(),
                    isDeleting = state.isDeleting,
                    onCancelSelection = onCancelSelection,
                    onSelectAll = onSelectAll,
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
    onCancelSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onRequestDeleteSelection: () -> Unit,
) {
    VaultCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            IconButton(onClick = onCancelSelection, enabled = !isDeleting, modifier = Modifier.size(48.dp)) {
                Icon(VaultIcons.Close, contentDescription = stringResource(R.string.cancel))
            }
            Text(
                text = stringResource(R.string.private_media_selected_count, selectedCount),
                modifier = Modifier.weight(1f),
                style = AppTextStyles.SectionTitle,
                color = AppColors.TextPrimary,
            )
            TextButton(onClick = onSelectAll, enabled = !isDeleting && !allSelected) {
                Text(stringResource(R.string.hidden_app_select_all))
            }
            TextButton(onClick = onRequestDeleteSelection, enabled = !isDeleting && selectedCount > 0) {
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
        Text(
            text = stringResource(R.string.private_media_empty_description),
            style = AppTextStyles.BodySecondary,
            color = AppColors.TextTertiary,
        )
        VaultPrimaryButton(
            text = stringResource(R.string.import_media),
            onClick = onImport,
        )
    }
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
    return BitmapFactory.decodeFile(file.absolutePath, options)
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
