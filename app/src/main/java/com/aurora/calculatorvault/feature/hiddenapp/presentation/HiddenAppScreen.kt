@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.aurora.calculatorvault.feature.hiddenapp.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.hiddenapp.data.AppIconProvider
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenApp
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppError
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppLayoutMode
import com.aurora.calculatorvault.feature.hiddenapp.domain.HiddenAppSortMode
import com.aurora.calculatorvault.feature.hiddenapp.domain.InstalledAppAvailability
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultDialog
import com.aurora.calculatorvault.ui.component.VaultEmptyState
import com.aurora.calculatorvault.ui.component.VaultLoadingIndicator
import com.aurora.calculatorvault.ui.component.VaultPrimaryButton
import kotlinx.coroutines.flow.collectLatest
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

@Composable
fun HiddenAppScreen(
    viewModel: HiddenAppViewModel,
    iconProvider: AppIconProvider,
    onAddApps: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val removedMessage = stringResource(R.string.hidden_app_removed_success)
    val clearedMessage = stringResource(R.string.hidden_app_recent_cleared)
    val noInvalidMessage = stringResource(R.string.hidden_app_no_invalid_apps)
    val preferenceFailedMessage = stringResource(R.string.hidden_app_preference_save_failed)
    val manualFailedMessage = stringResource(R.string.hidden_app_manual_sort_failed)
    val batchRemovedTemplate = stringResource(R.string.hidden_app_batch_removed)
    val batchRemoveFailed = stringResource(R.string.hidden_app_batch_remove_failed)
    val removeFailed = stringResource(R.string.hidden_app_remove_failed)
    val clearFailed = stringResource(R.string.hidden_app_clear_recent_failed)

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            onMessage(
                when (effect) {
                    HiddenAppEffect.Removed -> removedMessage
                    HiddenAppEffect.RecentCleared -> clearedMessage
                    HiddenAppEffect.NoInvalidApps -> noInvalidMessage
                    HiddenAppEffect.PreferenceSaveFailed -> preferenceFailedMessage
                    HiddenAppEffect.ManualOrderSaveFailed -> manualFailedMessage
                    HiddenAppEffect.BatchRemoveFailed -> batchRemoveFailed
                    HiddenAppEffect.RemoveFailed -> removeFailed
                    HiddenAppEffect.ClearRecentFailed -> clearFailed
                    is HiddenAppEffect.BatchRemoved ->
                        String.format(batchRemovedTemplate, effect.count)
                },
            )
        }
    }

    BackHandler(
        enabled = state.isBatchMode || state.isManualSortMode || state.query.isNotEmpty(),
    ) {
        when {
            state.isManualSortMode -> viewModel.requestCancelManualSort()
            state.isBatchMode -> viewModel.exitBatchMode()
            state.query.isNotEmpty() -> viewModel.clearQuery()
        }
    }

    HiddenAppContent(
        state = state,
        iconProvider = iconProvider,
        viewModel = viewModel,
        onAddApps = onAddApps,
    )
    HiddenAppDialogs(state, viewModel, iconProvider)
}

@Composable
private fun HiddenAppContent(
    state: HiddenAppUiState,
    iconProvider: AppIconProvider,
    viewModel: HiddenAppViewModel,
    onAddApps: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        HiddenAppTopBar(state, viewModel)

        when {
            state.isLoading -> VaultLoadingIndicator(Modifier.weight(1f))
            state.error == HiddenAppError.LoadFailed && state.apps.isEmpty() ->
                LoadFailed(viewModel::retryLoad)
            state.apps.isEmpty() -> EmptyApps(onAddApps)
            state.isManualSortMode -> {
                Text(
                    stringResource(R.string.hidden_app_manual_sort_hint),
                    style = AppTextStyles.BodySecondary,
                    color = AppColors.TextSecondary,
                )
                ManualSortList(
                    apps = state.visibleApps,
                    iconProvider = iconProvider,
                    onMove = viewModel::moveManualApp,
                    modifier = Modifier.weight(1f),
                )
            }
            else -> {
                if (state.showUsageNotice && !state.isBatchMode) {
                    UsageNotice(onDismiss = viewModel::dismissUsageNotice)
                }
                if (state.apps.all {
                        it.availability != InstalledAppAvailability.Available
                    }
                ) {
                    Surface(
                        shape = AppShapes.Medium,
                        color = AppColors.SurfaceSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(AppSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(VaultIcons.Info, null, tint = AppColors.Warning)
                            Spacer(Modifier.width(AppSpacing.sm))
                            Text(
                                stringResource(R.string.hidden_app_all_unavailable),
                                style = AppTextStyles.BodySecondary,
                                color = AppColors.TextSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = viewModel::retryLoad) {
                                Icon(
                                    VaultIcons.Refresh,
                                    stringResource(R.string.retry),
                                    tint = AppColors.AccentPrimary,
                                )
                            }
                        }
                    }
                }
                if (state.query.isBlank() && state.recentApps.isNotEmpty() && !state.isBatchMode) {
                    RecentApps(
                        apps = state.recentApps,
                        launchingPackageName = state.launchingPackageName,
                        iconProvider = iconProvider,
                        onLaunch = viewModel::launchApp,
                        onDetails = viewModel::openDetails,
                        onClear = viewModel::requestClearRecent,
                    )
                }
                HiddenAppSearchField(
                    query = state.query,
                    onQueryChange = viewModel::updateQuery,
                    onClear = viewModel::clearQuery,
                    placeholder = stringResource(R.string.hidden_app_search_name),
                    enabled = state.launchingPackageName == null,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.hidden_app_all_count, state.visibleApps.size),
                        style = AppTextStyles.SectionTitle,
                        color = AppColors.TextPrimary,
                    )
                    Text(
                        sortModeLabel(state.sortMode),
                        style = AppTextStyles.Caption,
                        color = AppColors.TextTertiary,
                    )
                }
                if (state.visibleApps.isEmpty()) {
                    SearchEmpty(
                        onClear = viewModel::clearQuery,
                        modifier = Modifier.weight(1f),
                    )
                } else if (state.layoutMode == HiddenAppLayoutMode.Grid) {
                    HiddenAppGrid(
                        state = state,
                        iconProvider = iconProvider,
                        onAppClick = { app ->
                            if (state.isBatchMode) viewModel.toggleSelection(app.packageName)
                            else viewModel.launchApp(app)
                        },
                        onDetails = viewModel::openDetails,
                        onRemoval = viewModel::requestRemoval,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    HiddenAppList(
                        state = state,
                        iconProvider = iconProvider,
                        onAppClick = { app ->
                            if (state.isBatchMode) viewModel.toggleSelection(app.packageName)
                            else viewModel.launchApp(app)
                        },
                        onDetails = viewModel::openDetails,
                        onRemoval = viewModel::requestRemoval,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.isBatchMode) {
                    BatchActionBar(state, viewModel)
                } else {
                    VaultPrimaryButton(
                        text = stringResource(R.string.add_app),
                        onClick = onAddApps,
                        enabled = state.launchingPackageName == null,
                        modifier = Modifier.padding(bottom = AppSpacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun HiddenAppTopBar(state: HiddenAppUiState, viewModel: HiddenAppViewModel) {
    var moreExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isBatchMode || state.isManualSortMode) {
            TextButton(
                onClick = if (state.isBatchMode) viewModel::exitBatchMode
                else viewModel::requestCancelManualSort,
            ) {
                Text(stringResource(R.string.cancel), color = AppColors.TextSecondary)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    state.isBatchMode -> stringResource(
                        R.string.hidden_app_selected_items,
                        state.selectedPackages.size,
                    )
                    state.isManualSortMode -> stringResource(R.string.hidden_app_adjust_order)
                    else -> stringResource(R.string.tab_hidden_app)
                },
                style = AppTextStyles.PageTitle,
                color = AppColors.TextPrimary,
            )
            if (!state.isBatchMode && !state.isManualSortMode) {
                Text(
                    text = stringResource(R.string.hidden_description),
                    style = AppTextStyles.BodySecondary,
                    color = AppColors.TextTertiary,
                )
            }
        }
        when {
            state.isBatchMode -> TextButton(
                onClick = viewModel::toggleSelectAllVisible,
                enabled = state.visibleApps.isNotEmpty(),
            ) {
                Text(
                    stringResource(
                        if (state.areAllVisibleSelected) {
                            R.string.hidden_app_deselect_all
                        } else {
                            R.string.hidden_app_select_all
                        },
                    ),
                    color = AppColors.AccentPrimary,
                )
            }
            state.isManualSortMode -> TextButton(
                onClick = viewModel::saveManualSort,
                enabled = !state.isSavingManualOrder,
            ) {
                Text(stringResource(R.string.save), color = AppColors.AccentPrimary)
            }
            else -> {
                IconButton(onClick = viewModel::toggleLayout) {
                    Icon(
                        if (state.layoutMode == HiddenAppLayoutMode.Grid) VaultIcons.List
                        else VaultIcons.Disguise,
                        contentDescription = stringResource(
                            if (state.layoutMode == HiddenAppLayoutMode.Grid) {
                                R.string.hidden_app_switch_list
                            } else {
                                R.string.hidden_app_switch_grid
                            },
                        ),
                        tint = AppColors.TextSecondary,
                    )
                }
                IconButton(onClick = viewModel::openSortDialog) {
                    Icon(
                        VaultIcons.Sort,
                        contentDescription = stringResource(R.string.hidden_app_sort),
                        tint = AppColors.TextSecondary,
                    )
                }
                Box {
                    IconButton(onClick = { moreExpanded = true }) {
                        Icon(
                            VaultIcons.More,
                            contentDescription = stringResource(R.string.more_actions),
                            tint = AppColors.TextSecondary,
                        )
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false },
                        containerColor = AppColors.SurfaceElevated,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hidden_app_batch_manage)) },
                            onClick = {
                                moreExpanded = false
                                viewModel.enterBatchMode()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hidden_app_adjust_order)) },
                            onClick = {
                                moreExpanded = false
                                viewModel.enterManualSort()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hidden_app_usage_guide)) },
                            onClick = {
                                moreExpanded = false
                                viewModel.showUsageNotice()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageNotice(onDismiss: () -> Unit) {
    VaultCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(VaultIcons.Info, null, tint = AppColors.AccentPrimary)
            Spacer(Modifier.width(AppSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.hidden_app_usage_title),
                    style = AppTextStyles.CardTitle,
                    color = AppColors.TextPrimary,
                )
                Text(
                    stringResource(R.string.hidden_app_usage_notice),
                    style = AppTextStyles.BodySecondary,
                    color = AppColors.TextSecondary,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(
                    VaultIcons.Close,
                    stringResource(R.string.hidden_app_dismiss_notice),
                    tint = AppColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun RecentApps(
    apps: List<HiddenApp>,
    launchingPackageName: String?,
    iconProvider: AppIconProvider,
    onLaunch: (HiddenApp) -> Unit,
    onDetails: (HiddenApp) -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.recent_opened),
                style = AppTextStyles.SectionTitle,
                color = AppColors.TextPrimary,
            )
            TextButton(onClick = onClear, enabled = launchingPackageName == null) {
                Text(stringResource(R.string.clear_action), color = AppColors.AccentPrimary)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            items(apps, key = HiddenApp::packageName) { app ->
                HiddenAppCompactItem(
                    app,
                    iconProvider,
                    launchingPackageName == null,
                    { onLaunch(app) },
                    { onDetails(app) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HiddenAppCompactItem(
    app: HiddenApp,
    iconProvider: AppIconProvider,
    enabled: Boolean,
    onLaunch: () -> Unit,
    onDetails: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .alpha(if (app.availability == InstalledAppAvailability.Available) 1f else 0.55f)
            .combinedClickable(enabled = enabled, onClick = onLaunch, onLongClick = onDetails)
            .padding(AppSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VaultInstalledAppIcon(app.packageName, app.appName, iconProvider)
        Text(
            app.appName,
            style = AppTextStyles.Caption,
            color = AppColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HiddenAppGrid(
    state: HiddenAppUiState,
    iconProvider: AppIconProvider,
    onAppClick: (HiddenApp) -> Unit,
    onDetails: (HiddenApp) -> Unit,
    onRemoval: (HiddenApp) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        items(state.visibleApps.chunked(4), key = { row -> row.joinToString { it.packageName } }) {
            rowApps ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                rowApps.forEach { app ->
                    HiddenAppGridItem(
                        app = app,
                        iconProvider = iconProvider,
                        selected = app.packageName in state.selectedPackages,
                        batchMode = state.isBatchMode,
                        enabled = state.launchingPackageName == null,
                        onClick = { onAppClick(app) },
                        onDetails = { onDetails(app) },
                        onRemoval = { onRemoval(app) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - rowApps.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HiddenAppGridItem(
    app: HiddenApp,
    iconProvider: AppIconProvider,
    selected: Boolean,
    batchMode: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDetails: () -> Unit,
    onRemoval: () -> Unit,
    modifier: Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .semantics {
                this.selected = selected
                contentDescription = app.appName
            }
            .alpha(if (app.availability == InstalledAppAvailability.Available) 1f else 0.55f)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = if (batchMode) null else onDetails,
            )
            .padding(vertical = AppSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            VaultInstalledAppIcon(app.packageName, app.appName, iconProvider)
            if (batchMode) {
                Icon(
                    if (selected) VaultIcons.Selected else VaultIcons.Unselected,
                    contentDescription = stringResource(
                        if (selected) R.string.hidden_app_selected
                        else R.string.hidden_app_unselected,
                    ),
                    tint = if (selected) AppColors.AccentPrimary else AppColors.TextTertiary,
                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
                )
            }
        }
        Text(
            app.appName,
            style = AppTextStyles.Caption,
            color = AppColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        AvailabilityLabel(app.availability, compact = true)
        if (!batchMode) {
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        VaultIcons.More,
                        stringResource(R.string.more_actions),
                        tint = AppColors.TextSecondary,
                    )
                }
                AppMenu(menuExpanded, { menuExpanded = false }, onDetails, onRemoval)
            }
        }
    }
}

@Composable
private fun HiddenAppList(
    state: HiddenAppUiState,
    iconProvider: AppIconProvider,
    onAppClick: (HiddenApp) -> Unit,
    onDetails: (HiddenApp) -> Unit,
    onRemoval: (HiddenApp) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        items(state.visibleApps, key = HiddenApp::packageName) { app ->
            var menuExpanded by remember { mutableStateOf(false) }
            Surface(
                shape = AppShapes.Large,
                color = AppColors.SurfacePrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(
                        if (app.availability == InstalledAppAvailability.Available) 1f else 0.65f,
                    )
                    .combinedClickable(
                        onClick = { onAppClick(app) },
                        onLongClick = if (state.isBatchMode) null else ({ onDetails(app) }),
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(AppSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VaultInstalledAppIcon(
                        app.packageName,
                        app.appName,
                        iconProvider,
                        Modifier.size(52.dp),
                    )
                    Spacer(Modifier.width(AppSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            app.appName,
                            style = AppTextStyles.CardTitle,
                            color = AppColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AvailabilityLabel(app.availability)
                    }
                    if (state.isBatchMode) {
                        val selected = app.packageName in state.selectedPackages
                        Icon(
                            if (selected) VaultIcons.Selected else VaultIcons.Unselected,
                            stringResource(
                                if (selected) R.string.hidden_app_selected
                                else R.string.hidden_app_unselected,
                            ),
                            tint = if (selected) AppColors.AccentPrimary else AppColors.TextTertiary,
                        )
                    } else {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    VaultIcons.More,
                                    stringResource(R.string.more_actions),
                                    tint = AppColors.TextSecondary,
                                )
                            }
                            AppMenu(
                                menuExpanded,
                                { menuExpanded = false },
                                { onDetails(app) },
                                { onRemoval(app) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onRemoval: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = AppColors.SurfaceElevated,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.hidden_app_details)) },
            onClick = {
                onDismiss()
                onDetails()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.hidden_app_remove_action)) },
            onClick = {
                onDismiss()
                onRemoval()
            },
        )
    }
}

@Composable
private fun BatchActionBar(state: HiddenAppUiState, viewModel: HiddenAppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        TextButton(
            onClick = viewModel::selectInvalidApps,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.hidden_app_select_invalid),
                color = AppColors.TextSecondary,
            )
        }
        VaultPrimaryButton(
            text = stringResource(
                R.string.hidden_app_batch_remove_count,
                state.selectedPackages.size,
            ),
            onClick = viewModel::requestBatchRemoval,
            enabled = state.selectedPackages.isNotEmpty() && !state.isBatchRemoving,
        )
    }
}

@Composable
private fun ManualSortList(
    apps: List<HiddenApp>,
    iconProvider: AppIconProvider,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        items(apps.size, key = { apps[it].packageName }) { index ->
            val app = apps[index]
            var dragAmount by remember(app.packageName) { mutableFloatStateOf(0f) }
            Surface(
                shape = AppShapes.Large,
                color = AppColors.SurfacePrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(app.packageName, index) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = { dragAmount = 0f },
                            onDragCancel = { dragAmount = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragAmount += amount.y
                                if (abs(dragAmount) > 56f) {
                                    val target = if (dragAmount > 0) index + 1 else index - 1
                                    if (target in apps.indices) onMove(index, target)
                                    dragAmount = 0f
                                }
                            },
                        )
                    },
            ) {
                Row(
                    modifier = Modifier.padding(AppSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        VaultIcons.Drag,
                        stringResource(R.string.hidden_app_drag_handle),
                        tint = AppColors.TextTertiary,
                    )
                    Spacer(Modifier.width(AppSpacing.sm))
                    VaultInstalledAppIcon(
                        app.packageName,
                        app.appName,
                        iconProvider,
                        Modifier.size(44.dp),
                    )
                    Spacer(Modifier.width(AppSpacing.md))
                    Text(
                        app.appName,
                        style = AppTextStyles.CardTitle,
                        color = AppColors.TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HiddenAppDialogs(
    state: HiddenAppUiState,
    viewModel: HiddenAppViewModel,
    iconProvider: AppIconProvider,
) {
    if (state.showSortDialog) {
        SortDialog(state.sortMode, viewModel::changeSortMode, viewModel::closeSortDialog)
    }
    state.selectedDetailApp?.let { app ->
        AppDetailsDialog(
            app = app,
            iconProvider = iconProvider,
            onDismiss = viewModel::closeDetails,
            onLaunch = {
                viewModel.closeDetails()
                viewModel.launchApp(app)
            },
            onRemove = {
                viewModel.closeDetails()
                viewModel.requestRemoval(app)
            },
            onOpenSettings = { viewModel.openAppSettings(app) },
            onRetry = viewModel::refreshDetails,
        )
    }
    state.pendingRemoval?.let { app ->
        VaultDialog(
            title = stringResource(R.string.hidden_app_remove_title, app.appName),
            message = stringResource(R.string.hidden_app_remove_message),
            confirmText = stringResource(R.string.hidden_app_remove_confirm),
            dismissText = stringResource(R.string.cancel),
            onConfirm = viewModel::confirmRemoval,
            onDismiss = viewModel::cancelRemoval,
        )
    }
    if (state.pendingBatchRemoval) {
        VaultDialog(
            title = stringResource(R.string.hidden_app_batch_remove_title),
            message = stringResource(
                R.string.hidden_app_batch_remove_message,
                state.selectedPackages.size,
            ),
            confirmText = stringResource(R.string.hidden_app_remove_confirm),
            dismissText = stringResource(R.string.cancel),
            onConfirm = viewModel::confirmBatchRemoval,
            onDismiss = viewModel::cancelBatchRemoval,
        )
    }
    if (state.showDiscardManualOrder) {
        VaultDialog(
            title = stringResource(R.string.hidden_app_discard_order_title),
            message = stringResource(R.string.hidden_app_discard_order_message),
            confirmText = stringResource(R.string.hidden_app_discard),
            dismissText = stringResource(R.string.continue_editing),
            onConfirm = viewModel::cancelManualSort,
            onDismiss = viewModel::keepManualSortEditing,
        )
    }
    if (state.pendingClearRecent) {
        VaultDialog(
            title = stringResource(R.string.hidden_app_clear_recent_title),
            message = stringResource(R.string.hidden_app_clear_recent_message),
            confirmText = stringResource(R.string.clear_action),
            dismissText = stringResource(R.string.cancel),
            onConfirm = viewModel::confirmClearRecent,
            onDismiss = viewModel::cancelClearRecent,
        )
    }
    state.launchErrorApp?.let { app ->
        val removable = state.error == HiddenAppError.NotInstalled ||
            state.error == HiddenAppError.NoLaunchIntent
        VaultDialog(
            title = launchErrorTitle(state.error),
            message = launchErrorMessage(state.error),
            confirmText = stringResource(
                if (removable) R.string.hidden_app_remove_action else R.string.got_it,
            ),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                viewModel.dismissLaunchError()
                if (removable) viewModel.requestRemoval(app)
            },
            onDismiss = viewModel::dismissLaunchError,
        )
    }
}

@Composable
private fun SortDialog(
    current: HiddenAppSortMode,
    onSelect: (HiddenAppSortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.SurfaceElevated,
        title = { Text(stringResource(R.string.hidden_app_sort)) },
        text = {
            Column {
                HiddenAppSortMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { onSelect(mode) })
                            .padding(vertical = AppSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == mode, onClick = { onSelect(mode) })
                        Text(sortModeLabel(mode), color = AppColors.TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = AppColors.AccentPrimary)
            }
        },
    )
}

@Composable
private fun AppDetailsDialog(
    app: HiddenApp,
    iconProvider: AppIconProvider,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onRemove: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.SurfaceElevated,
        title = { Text(stringResource(R.string.hidden_app_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VaultInstalledAppIcon(app.packageName, app.appName, iconProvider)
                    Spacer(Modifier.width(AppSpacing.md))
                    Text(
                        app.appName,
                        style = AppTextStyles.CardTitle,
                        color = AppColors.TextPrimary,
                    )
                }
                DetailRow(stringResource(R.string.hidden_app_package_name), app.packageName)
                DetailRow(
                    stringResource(R.string.hidden_app_current_status),
                    availabilityText(app.availability),
                )
                DetailRow(
                    stringResource(R.string.hidden_app_added_time),
                    dateFormat.format(Date(app.addedAt)),
                )
                DetailRow(
                    stringResource(R.string.hidden_app_recent_time),
                    app.lastOpenedAt?.let { dateFormat.format(Date(it)) }
                        ?: stringResource(R.string.hidden_app_never_opened),
                )
            }
        },
        confirmButton = {
            when (app.availability) {
                InstalledAppAvailability.Available -> TextButton(onClick = onLaunch) {
                    Text(
                        stringResource(R.string.hidden_app_open),
                        color = AppColors.AccentPrimary,
                    )
                }
                InstalledAppAvailability.Disabled -> TextButton(onClick = onOpenSettings) {
                    Text(
                        stringResource(R.string.hidden_app_go_to_settings),
                        color = AppColors.AccentPrimary,
                    )
                }
                InstalledAppAvailability.NoLauncher,
                InstalledAppAvailability.Unknown,
                -> TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry), color = AppColors.AccentPrimary)
                }
                InstalledAppAvailability.NotInstalled -> Unit
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.hidden_app_remove_action), color = AppColors.Error)
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), color = AppColors.TextSecondary)
                }
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = AppTextStyles.Caption, color = AppColors.TextTertiary)
        Text(value, style = AppTextStyles.Body, color = AppColors.TextPrimary)
    }
}

@Composable
private fun AvailabilityLabel(
    availability: InstalledAppAvailability,
    compact: Boolean = false,
) {
    if (availability == InstalledAppAvailability.Available) return
    Text(
        availabilityText(availability, compact),
        style = AppTextStyles.Caption,
        color = if (availability == InstalledAppAvailability.Unknown) {
            AppColors.TextTertiary
        } else {
            AppColors.Warning
        },
        maxLines = 1,
    )
}

@Composable
private fun availabilityText(
    availability: InstalledAppAvailability,
    compact: Boolean = false,
): String = stringResource(
    when (availability) {
        InstalledAppAvailability.Available -> R.string.hidden_app_available
        InstalledAppAvailability.NotInstalled ->
            if (compact) R.string.hidden_app_uninstalled_short else R.string.hidden_app_uninstalled
        InstalledAppAvailability.Disabled -> R.string.hidden_app_disabled
        InstalledAppAvailability.NoLauncher -> R.string.hidden_app_no_launcher_status
        InstalledAppAvailability.Unknown -> R.string.hidden_app_unknown_status
    },
)

@Composable
private fun sortModeLabel(mode: HiddenAppSortMode): String = stringResource(
    when (mode) {
        HiddenAppSortMode.Manual -> R.string.hidden_app_sort_manual
        HiddenAppSortMode.AddedNewest -> R.string.hidden_app_sort_newest
        HiddenAppSortMode.AddedOldest -> R.string.hidden_app_sort_oldest
        HiddenAppSortMode.NameAscending -> R.string.hidden_app_sort_name_asc
        HiddenAppSortMode.NameDescending -> R.string.hidden_app_sort_name_desc
    },
)

@Composable
private fun launchErrorTitle(error: HiddenAppError?): String = stringResource(
    when (error) {
        HiddenAppError.NotInstalled -> R.string.hidden_app_uninstalled
        HiddenAppError.Disabled -> R.string.hidden_app_disabled
        else -> R.string.hidden_app_cannot_open_title
    },
)

@Composable
private fun launchErrorMessage(error: HiddenAppError?): String = stringResource(
    when (error) {
        HiddenAppError.NotInstalled -> R.string.hidden_app_uninstalled_message
        HiddenAppError.Disabled -> R.string.hidden_app_disabled_message
        HiddenAppError.NoLaunchIntent -> R.string.hidden_app_no_launcher_message
        HiddenAppError.LaunchBlocked -> R.string.hidden_app_launch_blocked
        else -> R.string.hidden_app_launch_failed
    },
)

@Composable
private fun SearchEmpty(onClear: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VaultEmptyState(
            title = stringResource(R.string.hidden_app_no_search_result),
            description = stringResource(R.string.hidden_app_no_search_description),
            icon = VaultIcons.Search,
        )
        TextButton(onClick = onClear) {
            Text(stringResource(R.string.hidden_app_clear_search), color = AppColors.AccentPrimary)
        }
    }
}

@Composable
private fun LoadFailed(onRetryLoad: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VaultEmptyState(
            title = stringResource(R.string.hidden_app_load_failed),
            description = stringResource(R.string.hidden_app_load_failed_description),
            icon = VaultIcons.Info,
        )
        TextButton(onClick = onRetryLoad) {
            Text(stringResource(R.string.retry), color = AppColors.AccentPrimary)
        }
    }
}

@Composable
private fun EmptyApps(onAddApps: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        VaultEmptyState(
            title = stringResource(R.string.hidden_app_empty_title_new),
            description = stringResource(R.string.hidden_app_empty_description_new),
            icon = VaultIcons.Apps,
        )
        VaultPrimaryButton(
            text = stringResource(R.string.add_app),
            onClick = onAddApps,
        )
    }
}
