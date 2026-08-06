package com.aurora.calculatorvault.feature.disguise.presentation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseEntry
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseIconId
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseSortMode
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutRequestState
import com.aurora.calculatorvault.feature.disguise.domain.ShortcutStatus
import com.aurora.calculatorvault.feature.disguise.shortcut.SyncedDisguiseEntry
import com.aurora.calculatorvault.feature.hiddenapp.data.AppIconProvider
import com.aurora.calculatorvault.feature.hiddenapp.presentation.HiddenAppSearchField
import com.aurora.calculatorvault.feature.hiddenapp.presentation.VaultInstalledAppIcon
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultEmptyState
import com.aurora.calculatorvault.ui.component.VaultLoadingIndicator
import com.aurora.calculatorvault.ui.component.VaultPrimaryButton
import com.aurora.calculatorvault.ui.component.VaultSecondaryButton
import com.aurora.calculatorvault.ui.component.VaultSecondaryTopBar
import com.aurora.calculatorvault.ui.message.AppMessage
import com.aurora.calculatorvault.ui.message.AppMessageType
import com.aurora.calculatorvault.ui.layout.AppLayout
import com.aurora.calculatorvault.ui.layout.appFabScrollContentPadding
import com.aurora.calculatorvault.ui.layout.appPagePadding
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AppDisguiseScreen(
    viewModel: AppDisguiseViewModel,
    iconProvider: AppIconProvider,
    onBackToManagement: () -> Unit,
    onMessage: (AppMessage) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val saved = stringResource(R.string.app_disguise_saved)
    val updated = stringResource(R.string.app_disguise_updated)
    val deleted = stringResource(R.string.app_disguise_deleted)
    val shortcutFailed = stringResource(R.string.app_disguise_request_failed)
    val stateSaveFailed = stringResource(R.string.app_disguise_state_save_failed)
    val shortcutUpdated = stringResource(R.string.app_disguise_shortcut_update_success)

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            val message = when (effect) {
                    AppDisguiseEffect.Saved -> AppMessage(saved, AppMessageType.Success)
                    AppDisguiseEffect.Updated -> AppMessage(updated, AppMessageType.Success)
                    AppDisguiseEffect.Deleted -> AppMessage(deleted, AppMessageType.Success)
                    AppDisguiseEffect.ShortcutRequestFailed -> AppMessage(shortcutFailed, AppMessageType.Error)
                    AppDisguiseEffect.ShortcutRequestStateSaveFailed -> AppMessage(
                        stateSaveFailed,
                        AppMessageType.Warning,
                    )
                    AppDisguiseEffect.ShortcutUpdated -> AppMessage(shortcutUpdated, AppMessageType.Success)
                    AppDisguiseEffect.ShortcutDeleted -> AppMessage(deleted, AppMessageType.Success)
                    AppDisguiseEffect.ManualShortcutRemovalRequired -> null
                }
            message?.let(onMessage)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.refreshShortcutStatus()
    }

    BackHandler {
        if (!viewModel.back()) onBackToManagement()
    }

    when (state.page) {
        AppDisguisePage.List -> DisguiseListPage(
            state = state,
            iconProvider = iconProvider,
            onBack = onBackToManagement,
            viewModel = viewModel,
        )
        AppDisguisePage.SelectApp -> SelectAppPage(state, iconProvider, viewModel)
        AppDisguisePage.SetName -> SetNamePage(state, viewModel)
        AppDisguisePage.SelectIcon -> SelectIconPage(state, viewModel)
        AppDisguisePage.Preview -> PreviewPage(state, viewModel)
        AppDisguisePage.Saved -> SavedPage(state, viewModel)
        AppDisguisePage.Details -> DetailsPage(state, viewModel)
    }

    state.pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.app_disguise_delete_title)) },
            text = {
                Text(stringResource(R.string.app_disguise_delete_message, entry.customName))
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.app_disguise_delete), color = AppColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = AppColors.SurfaceElevated,
        )
    }

    state.pendingDuplicateRequest?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDuplicateRequest,
            title = { Text(stringResource(R.string.app_disguise_duplicate_title)) },
            text = { Text(stringResource(R.string.app_disguise_duplicate_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDuplicateRequest) {
                    Text(stringResource(R.string.app_disguise_continue_add))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDuplicateRequest) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = AppColors.SurfaceElevated,
        )
    }

    if (state.showUnsupportedDialog) {
        InfoDialog(
            title = stringResource(R.string.app_disguise_launcher_unsupported_title),
            message = stringResource(R.string.app_disguise_launcher_unsupported_message),
            onDismiss = viewModel::dismissUnsupportedDialog,
        )
    }
    if (state.showRequestSubmittedDialog) {
        InfoDialog(
            title = stringResource(R.string.app_disguise_request_submitted_title),
            message = stringResource(R.string.app_disguise_request_submitted_message),
            onDismiss = viewModel::dismissRequestSubmittedDialog,
        )
    }
    if (state.showManualDeleteDialog) {
        InfoDialog(
            title = stringResource(R.string.app_disguise_shortcut_delete_notice_title),
            message = stringResource(R.string.app_disguise_manual_delete_shortcut),
            onDismiss = viewModel::dismissManualDeleteDialog,
        )
    }
}

@Composable
private fun DisguiseListPage(
    state: AppDisguiseUiState,
    iconProvider: AppIconProvider,
    onBack: () -> Unit,
    viewModel: AppDisguiseViewModel,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("app_disguise_screen"),
        containerColor = AppColors.BackgroundPrimary,
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::startCreate,
                modifier = Modifier.padding(bottom = AppLayout.FabBottomSpacing),
                containerColor = AppColors.AccentPrimary,
                contentColor = AppColors.TextPrimary,
            ) {
                Icon(VaultIcons.Add, stringResource(R.string.app_disguise_create))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppLayout.PageHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            VaultSecondaryTopBar(
                title = stringResource(R.string.app_disguise),
                subtitle = stringResource(R.string.app_disguise_screen_description),
                onBack = onBack,
            )
            Text(
                stringResource(R.string.app_disguise_scope_note),
                style = AppTextStyles.Caption,
                color = AppColors.TextTertiary,
            )
            HiddenAppSearchField(
                query = state.query,
                onQueryChange = viewModel::updateQuery,
                onClear = { viewModel.updateQuery("") },
                placeholder = stringResource(R.string.app_disguise_search),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.app_disguise_entries_title),
                    style = AppTextStyles.SectionTitle,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    TextButton(onClick = { sortExpanded = true }) {
                        Icon(VaultIcons.Sort, null, tint = AppColors.AccentPrimary)
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text(stringResource(R.string.app_disguise_sort))
                    }
                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false },
                        containerColor = AppColors.SurfaceElevated,
                    ) {
                        DisguiseSortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(stringResource(sortLabel(mode))) },
                                leadingIcon = {
                                    RadioButton(
                                        selected = state.sortMode == mode,
                                        onClick = null,
                                    )
                                },
                                onClick = {
                                    sortExpanded = false
                                    viewModel.setSortMode(mode)
                                },
                            )
                        }
                    }
                }
            }
            errorText(state.error)
            when {
                state.isLoading -> VaultLoadingIndicator(Modifier.weight(1f))
                state.entries.isEmpty() -> VaultEmptyState(
                    title = stringResource(R.string.app_disguise_empty_title),
                    description = stringResource(R.string.app_disguise_empty_description),
                    icon = VaultIcons.Disguise,
                    modifier = Modifier.weight(1f),
                )
                state.visibleEntries.isEmpty() -> VaultEmptyState(
                    title = stringResource(R.string.hidden_app_no_search_result),
                    description = stringResource(R.string.hidden_app_no_search_description),
                    icon = VaultIcons.Search,
                    modifier = Modifier.weight(1f),
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    contentPadding = appFabScrollContentPadding(),
                ) {
                    items(state.visibleEntries, key = { it.entry.id }) { entry ->
                        DisguiseEntryRow(entry, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun DisguiseEntryRow(
    syncedEntry: SyncedDisguiseEntry,
    viewModel: AppDisguiseViewModel,
) {
    val entry = syncedEntry.entry
    val status = syncedEntry.shortcutStatus
    val statusLabel = shortcutStatusLabel(status)
    val cardDescription = stringResource(
        R.string.app_disguise_entry_card_description,
        entry.customName,
        entry.targetAppName,
        statusLabel,
    )
    val primaryActionDescription = stringResource(shortcutPrimaryActionDescription(status))
    val editDescription = stringResource(R.string.app_disguise_edit_content_description)
    val deleteDescription = stringResource(R.string.app_disguise_delete_content_description)
    VaultCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("disguise_entry_card_${entry.id}")
            .semantics { contentDescription = cardDescription }
            .clickable { viewModel.showDetails(entry) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DisguiseIcon(entry.iconId, Modifier.size(52.dp))
                Spacer(Modifier.width(AppSpacing.md))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                ) {
                    Text(
                        entry.customName,
                        style = AppTextStyles.CardTitle,
                        color = AppColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        entry.targetAppName,
                        style = AppTextStyles.Caption,
                        color = AppColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusLabel,
                        style = AppTextStyles.Caption,
                        color = shortcutStatusColor(status),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    modifier = Modifier
                        .testTag("disguise_entry_primary_action_${entry.id}")
                        .semantics {
                            contentDescription = primaryActionDescription
                        },
                    onClick = { viewModel.requestShortcut(syncedEntry) },
                    enabled = status != ShortcutStatus.TARGET_DISABLED &&
                        status != ShortcutStatus.CONFIG_INVALID,
                ) {
                    Text(stringResource(shortcutPrimaryAction(status)))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    modifier = Modifier
                        .testTag("disguise_entry_edit_${entry.id}")
                        .semantics { contentDescription = editDescription },
                    onClick = { viewModel.edit(entry) },
                ) {
                    Text(stringResource(R.string.app_disguise_edit))
                }
                TextButton(
                    modifier = Modifier
                        .testTag("disguise_entry_delete_${entry.id}")
                        .semantics { contentDescription = deleteDescription },
                    onClick = { viewModel.requestDelete(entry) },
                ) {
                    Text(stringResource(R.string.app_disguise_delete), color = AppColors.Error)
                }
            }
        }
    }
}

@StringRes
private fun shortcutPrimaryActionDescription(status: ShortcutStatus): Int = when (status) {
    ShortcutStatus.NOT_CREATED -> R.string.app_disguise_create_shortcut_content_description
    ShortcutStatus.CREATED -> R.string.app_disguise_update_shortcut_content_description
    ShortcutStatus.NEED_RECREATE -> R.string.app_disguise_recreate_shortcut_content_description
    ShortcutStatus.TARGET_UNINSTALLED -> R.string.app_disguise_remove_configuration
    ShortcutStatus.TARGET_DISABLED -> R.string.app_disguise_disabled_action
    ShortcutStatus.CONFIG_INVALID -> R.string.app_disguise_check_configuration
}

@Composable
private fun SelectAppPage(
    state: AppDisguiseUiState,
    iconProvider: AppIconProvider,
    viewModel: AppDisguiseViewModel,
) {
    WizardColumn(
        title = stringResource(R.string.app_disguise_select_app_title),
        subtitle = stringResource(R.string.app_disguise_select_app_description),
        step = 1,
        onBack = { viewModel.back() },
    ) {
        HiddenAppSearchField(
            query = state.appQuery,
            onQueryChange = viewModel::updateAppQuery,
            onClear = { viewModel.updateAppQuery("") },
            placeholder = stringResource(R.string.hidden_app_search),
        )
        errorText(state.error)
        if (state.isScanningApps) {
            VaultLoadingIndicator(Modifier.weight(1f))
        } else if (state.error == AppDisguiseError.ScanFailed) {
            VaultPrimaryButton(
                text = stringResource(R.string.retry),
                onClick = viewModel::retryScan,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                items(state.visibleInstalledApps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectApp(app) }
                            .padding(vertical = AppSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VaultInstalledAppIcon(
                            packageName = app.packageName,
                            appName = app.appName,
                            iconProvider = iconProvider,
                            modifier = Modifier.size(52.dp),
                        )
                        Spacer(Modifier.width(AppSpacing.md))
                        Text(
                            app.appName,
                            style = AppTextStyles.Body,
                            color = AppColors.TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(VaultIcons.Chevron, null, tint = AppColors.TextTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetNamePage(state: AppDisguiseUiState, viewModel: AppDisguiseViewModel) {
    val keyboardController = LocalSoftwareKeyboardController.current
    WizardColumn(
        title = stringResource(R.string.app_disguise_set_name_title),
        subtitle = stringResource(R.string.app_disguise_set_name_description),
        step = 2,
        onBack = { viewModel.back() },
        scrollable = true,
    ) {
        Text(
            state.selectedApp?.appName.orEmpty(),
            style = AppTextStyles.SectionTitle,
            color = AppColors.TextSecondary,
        )
        OutlinedTextField(
            value = state.customName,
            onValueChange = viewModel::updateCustomName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.app_disguise_custom_name)) },
            supportingText = {
                Text(stringResource(R.string.app_disguise_name_counter, state.customName.length))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = {
                    if (state.canContinueName) {
                        keyboardController?.hide()
                        viewModel.continueFromName()
                    }
                },
            ),
            colors = disguiseTextFieldColors(),
        )
        VaultPrimaryButton(
            text = stringResource(R.string.next_step),
            onClick = {
                keyboardController?.hide()
                viewModel.continueFromName()
            },
            enabled = state.canContinueName,
            modifier = Modifier.padding(bottom = AppSpacing.md),
        )
    }
}

@Composable
private fun SelectIconPage(state: AppDisguiseUiState, viewModel: AppDisguiseViewModel) {
    WizardColumn(
        title = stringResource(R.string.app_disguise_select_icon_title),
        subtitle = stringResource(R.string.app_disguise_select_icon_description),
        step = 3,
        onBack = { viewModel.back() },
        scrollable = true,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.height(430.dp),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            items(DisguiseIconId.entries) { iconId ->
                val selected = state.selectedIcon == iconId
                Column(
                    modifier = Modifier
                        .background(
                            if (selected) AppColors.AccentContainer else AppColors.SurfacePrimary,
                            AppShapes.Large,
                        )
                        .clickable { viewModel.selectIcon(iconId) }
                        .padding(AppSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                ) {
                    DisguiseIcon(iconId, Modifier.size(44.dp))
                    Text(
                        stringResource(iconLabel(iconId)),
                        style = AppTextStyles.Caption,
                        color = if (selected) AppColors.AccentPrimary else AppColors.TextSecondary,
                    )
                }
            }
        }
        VaultPrimaryButton(
            text = stringResource(R.string.next_step),
            onClick = viewModel::continueToPreview,
            modifier = Modifier.padding(bottom = AppSpacing.md),
        )
    }
}

@Composable
private fun PreviewPage(state: AppDisguiseUiState, viewModel: AppDisguiseViewModel) {
    WizardColumn(
        title = stringResource(R.string.app_disguise_preview_title),
        subtitle = stringResource(R.string.app_disguise_preview_description),
        step = 4,
        onBack = { viewModel.back() },
        scrollable = true,
    ) {
        Text(
            stringResource(R.string.app_disguise_desktop_preview),
            style = AppTextStyles.SectionTitle,
            color = AppColors.TextPrimary,
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(360.dp),
            contentAlignment = Alignment.Center,
        ) {
            VaultCard {
                Column(
                    modifier = Modifier.padding(AppSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    DisguiseIcon(state.selectedIcon, Modifier.size(76.dp))
                    Text(
                        state.customName,
                        style = AppTextStyles.CardTitle,
                        color = AppColors.TextPrimary,
                    )
                    Text(
                        state.selectedApp?.appName.orEmpty(),
                        style = AppTextStyles.Caption,
                        color = AppColors.TextTertiary,
                    )
                }
            }
        }
        Text(
            stringResource(R.string.app_disguise_icon_launcher_note),
            style = AppTextStyles.Caption,
            color = AppColors.TextTertiary,
        )
        errorText(state.error)
        VaultPrimaryButton(
            text = stringResource(
                if (state.editingId == null) R.string.app_disguise_save
                else R.string.app_disguise_update,
            ),
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier.padding(bottom = AppSpacing.md),
        )
    }
}

@Composable
private fun SavedPage(state: AppDisguiseUiState, viewModel: AppDisguiseViewModel) {
    val entry = state.savedEntry ?: return
    WizardColumn(
        title = stringResource(R.string.app_disguise_saved_title),
        subtitle = stringResource(R.string.app_disguise_saved_description),
        step = null,
        onBack = viewModel::finishSaved,
        scrollable = true,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(280.dp),
            contentAlignment = Alignment.Center,
        ) {
            VaultCard {
                Column(
                    modifier = Modifier.padding(AppSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    DisguiseIcon(entry.iconId, Modifier.size(76.dp))
                    Text(
                        entry.customName,
                        style = AppTextStyles.CardTitle,
                        color = AppColors.TextPrimary,
                    )
                }
            }
        }
        Text(
            stringResource(R.string.app_disguise_entry_safe_note),
            style = AppTextStyles.BodySecondary,
            color = AppColors.TextSecondary,
        )
        VaultPrimaryButton(
            text = if (state.requestingShortcutEntryId == entry.id) {
                stringResource(R.string.app_disguise_requesting)
            } else {
                stringResource(R.string.app_disguise_add_to_desktop)
            },
            onClick = { viewModel.requestShortcut(entry) },
            enabled = state.requestingShortcutEntryId == null,
        )
        VaultSecondaryButton(
            text = stringResource(R.string.app_disguise_finish),
            onClick = viewModel::finishSaved,
            modifier = Modifier.padding(bottom = AppSpacing.md),
        )
    }
}

@Composable
private fun DetailsPage(state: AppDisguiseUiState, viewModel: AppDisguiseViewModel) {
    val entry = state.selectedDetails ?: return
    WizardColumn(
        title = stringResource(R.string.app_disguise_details),
        subtitle = entry.customName,
        step = null,
        onBack = { viewModel.back() },
        scrollable = true,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DisguiseIcon(entry.iconId, Modifier.size(76.dp))
        }
        DetailLine(stringResource(R.string.app_disguise_target_app), entry.targetAppName)
        DetailLine(stringResource(R.string.app_disguise_custom_name), entry.customName)
        DetailLine(
            stringResource(R.string.app_disguise_icon),
            stringResource(iconLabel(entry.iconId)),
        )
        DetailLine(
            stringResource(R.string.app_disguise_created_at),
            formatTime(entry.createdAt),
        )
        DetailLine(
            stringResource(R.string.app_disguise_updated_at),
            formatTime(entry.updatedAt),
        )
        DetailLine(
            stringResource(R.string.app_disguise_desktop_entry),
            shortcutStateLabel(entry.shortcutRequestState),
        )
        Text(
            stringResource(R.string.app_disguise_icon_launcher_note),
            style = AppTextStyles.Caption,
            color = AppColors.TextTertiary,
        )
        VaultPrimaryButton(
            text = if (state.requestingShortcutEntryId == entry.id) {
                stringResource(R.string.app_disguise_requesting)
            } else {
                shortcutActionLabel(entry.shortcutRequestState)
            },
            onClick = { viewModel.requestShortcut(entry) },
            enabled = state.requestingShortcutEntryId == null,
        )
        VaultSecondaryButton(
            text = stringResource(R.string.app_disguise_edit),
            onClick = { viewModel.edit(entry) },
        )
        TextButton(
            onClick = { viewModel.requestDelete(entry) },
            modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.md),
        ) {
            Text(stringResource(R.string.app_disguise_delete), color = AppColors.Error)
        }
    }
}

@Composable
private fun shortcutStateLabel(state: ShortcutRequestState): String = stringResource(
    when (state) {
        ShortcutRequestState.NotRequested -> R.string.app_disguise_shortcut_not_requested
        ShortcutRequestState.RequestSubmitted -> R.string.app_disguise_shortcut_requested
        ShortcutRequestState.LauncherAccepted -> R.string.app_disguise_shortcut_accepted
        ShortcutRequestState.Unsupported -> R.string.app_disguise_shortcut_unsupported
        ShortcutRequestState.Failed -> R.string.app_disguise_shortcut_failed
    },
)

@Composable
private fun shortcutActionLabel(state: ShortcutRequestState): String = stringResource(
    when (state) {
        ShortcutRequestState.NotRequested -> R.string.app_disguise_add_to_desktop
        ShortcutRequestState.RequestSubmitted,
        ShortcutRequestState.LauncherAccepted -> R.string.app_disguise_add_again
        ShortcutRequestState.Unsupported,
        ShortcutRequestState.Failed -> R.string.app_disguise_retry_add
    },
)

@StringRes
private fun shortcutPrimaryAction(status: ShortcutStatus): Int = when (status) {
    ShortcutStatus.NOT_CREATED -> R.string.app_disguise_create_shortcut
    ShortcutStatus.CREATED -> R.string.app_disguise_update_shortcut
    ShortcutStatus.NEED_RECREATE -> R.string.app_disguise_recreate_shortcut
    ShortcutStatus.TARGET_UNINSTALLED -> R.string.app_disguise_remove_configuration
    ShortcutStatus.TARGET_DISABLED -> R.string.app_disguise_disabled_action
    ShortcutStatus.CONFIG_INVALID -> R.string.app_disguise_check_configuration
}

@Composable
private fun shortcutStatusLabel(status: ShortcutStatus): String = stringResource(
    when (status) {
        ShortcutStatus.NOT_CREATED -> R.string.app_disguise_status_not_created
        ShortcutStatus.CREATED -> R.string.app_disguise_status_created
        ShortcutStatus.NEED_RECREATE -> R.string.app_disguise_status_need_recreate
        ShortcutStatus.TARGET_UNINSTALLED -> R.string.app_disguise_status_target_uninstalled
        ShortcutStatus.TARGET_DISABLED -> R.string.app_disguise_status_target_disabled
        ShortcutStatus.CONFIG_INVALID -> R.string.app_disguise_status_config_invalid
    },
)

private fun shortcutStatusColor(status: ShortcutStatus) = when (status) {
    ShortcutStatus.CREATED -> AppColors.Success
    ShortcutStatus.NOT_CREATED,
    ShortcutStatus.NEED_RECREATE,
    -> AppColors.Warning
    ShortcutStatus.TARGET_UNINSTALLED,
    ShortcutStatus.TARGET_DISABLED,
    ShortcutStatus.CONFIG_INVALID,
    -> AppColors.Error
}

@Composable
private fun InfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.got_it))
            }
        },
        containerColor = AppColors.SurfaceElevated,
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    VaultCard(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = AppTextStyles.Caption, color = AppColors.TextTertiary)
        Text(value, style = AppTextStyles.Body, color = AppColors.TextPrimary)
    }
}

@Composable
private fun WizardColumn(
    title: String,
    subtitle: String,
    step: Int?,
    onBack: () -> Unit,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val columnModifier = if (scrollable) {
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(appPagePadding())
    } else {
        Modifier
            .fillMaxSize()
            .padding(appPagePadding(bottom = AppLayout.BottomSafeSpace))
    }
    Column(
        modifier = columnModifier,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        VaultSecondaryTopBar(title = title, subtitle = subtitle, onBack = onBack)
        if (step != null) {
            Text(
                stringResource(R.string.app_disguise_step_progress, step),
                style = AppTextStyles.Caption,
                color = AppColors.AccentPrimary,
            )
        }
        content()
    }
}

@Composable
private fun DisguiseIcon(iconId: DisguiseIconId, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(AppColors.SurfaceSecondary, AppShapes.Medium),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconVector(iconId),
            contentDescription = stringResource(iconLabel(iconId)),
            tint = AppColors.AccentPrimary,
            modifier = Modifier.padding(AppSpacing.sm),
        )
    }
}

@Composable
private fun errorText(error: AppDisguiseError?) {
    if (error == null) return
    Text(
        text = stringResource(
            when (error) {
                AppDisguiseError.LoadFailed -> R.string.app_disguise_load_failed
                AppDisguiseError.ScanFailed -> R.string.app_disguise_scan_failed
                AppDisguiseError.SaveFailed -> R.string.app_disguise_save_failed
                AppDisguiseError.DeleteFailed -> R.string.app_disguise_delete_failed
                AppDisguiseError.ShortcutUpdateFailed -> R.string.app_disguise_shortcut_update_failed
                AppDisguiseError.ShortcutDeleteFailed -> R.string.app_disguise_shortcut_delete_failed
            },
        ),
        style = AppTextStyles.BodySecondary,
        color = AppColors.Error,
    )
}

@Composable
private fun disguiseTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppColors.TextPrimary,
    unfocusedTextColor = AppColors.TextPrimary,
    focusedBorderColor = AppColors.AccentPrimary,
    unfocusedBorderColor = AppColors.BorderSubtle,
    focusedLabelColor = AppColors.AccentPrimary,
    unfocusedLabelColor = AppColors.TextTertiary,
    cursorColor = AppColors.AccentPrimary,
)

private fun iconVector(iconId: DisguiseIconId): ImageVector = when (iconId) {
    DisguiseIconId.Files -> VaultIcons.Files
    DisguiseIconId.Photos -> VaultIcons.Photos
    DisguiseIconId.Browser -> VaultIcons.Browser
    DisguiseIconId.Settings -> VaultIcons.Settings
    DisguiseIconId.Video -> VaultIcons.Video
    DisguiseIconId.Music -> VaultIcons.Music
    DisguiseIconId.Tools -> VaultIcons.Tools
    DisguiseIconId.Weather -> VaultIcons.Weather
    DisguiseIconId.Calendar -> VaultIcons.Calendar
    DisguiseIconId.Calculator -> VaultIcons.Calculator
}

@StringRes
private fun iconLabel(iconId: DisguiseIconId): Int = when (iconId) {
    DisguiseIconId.Files -> R.string.app_disguise_icon_files
    DisguiseIconId.Photos -> R.string.app_disguise_icon_photos
    DisguiseIconId.Browser -> R.string.app_disguise_icon_browser
    DisguiseIconId.Settings -> R.string.app_disguise_icon_settings
    DisguiseIconId.Video -> R.string.app_disguise_icon_video
    DisguiseIconId.Music -> R.string.app_disguise_icon_music
    DisguiseIconId.Tools -> R.string.app_disguise_icon_tools
    DisguiseIconId.Weather -> R.string.app_disguise_icon_weather
    DisguiseIconId.Calendar -> R.string.app_disguise_icon_calendar
    DisguiseIconId.Calculator -> R.string.app_disguise_icon_calculator
}

@StringRes
private fun sortLabel(mode: DisguiseSortMode): Int = when (mode) {
    DisguiseSortMode.CreatedNewest -> R.string.app_disguise_sort_created
    DisguiseSortMode.UpdatedNewest -> R.string.app_disguise_sort_updated
    DisguiseSortMode.Name -> R.string.app_disguise_sort_name
}

private fun formatTime(value: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
