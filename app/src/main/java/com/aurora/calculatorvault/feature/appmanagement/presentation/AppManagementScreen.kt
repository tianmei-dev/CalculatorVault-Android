package com.aurora.calculatorvault.feature.appmanagement.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.disguise.presentation.PageHeader
import com.aurora.calculatorvault.ui.component.VaultCard

@Composable
fun AppManagementScreen(
    viewModel: AppManagementViewModel,
    onOpenDisguise: () -> Unit,
    onOpenPrivateApps: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    AppManagementContent(state, onOpenDisguise, onOpenPrivateApps)
}

@Composable
internal fun AppManagementContent(
    state: AppManagementUiState,
    onOpenDisguise: () -> Unit,
    onOpenPrivateApps: () -> Unit,
) {
    val status = when {
        state.loadFailed -> stringResource(R.string.app_management_view_apps)
        state.isLoading -> stringResource(R.string.loading)
        state.privateAppCount == 0 -> stringResource(R.string.app_management_no_private_apps)
        else -> stringResource(R.string.app_management_private_app_count, state.privateAppCount)
    }
    val entryDescription = if (state.privateAppCount == 0) {
        stringResource(R.string.private_apps_entry_empty_description)
    } else {
        stringResource(R.string.private_apps_entry_count_description, state.privateAppCount)
    }
    val disguiseStatus = when {
        state.disguiseLoadFailed -> stringResource(R.string.app_management_view_apps)
        state.isDisguiseLoading -> stringResource(R.string.loading)
        state.disguiseEntryCount == 0 -> stringResource(R.string.app_disguise_no_entries)
        else -> stringResource(R.string.app_disguise_count, state.disguiseEntryCount)
    }
    val disguiseEntryDescription = stringResource(R.string.app_disguise) + "，" + disguiseStatus

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_management_screen")
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        PageHeader(
            title = stringResource(R.string.tab_app_management),
            description = stringResource(R.string.app_management_description),
        )
        AppManagementEntryCard(
            title = stringResource(R.string.app_disguise),
            description = stringResource(R.string.app_disguise_entry_description),
            status = disguiseStatus,
            icon = VaultIcons.Disguise,
            contentDescription = disguiseEntryDescription,
            testTag = "app_disguise_entry",
            hasError = state.disguiseLoadFailed,
            onClick = onOpenDisguise,
        )
        AppManagementEntryCard(
            title = stringResource(R.string.private_apps),
            description = stringResource(R.string.private_apps_description),
            status = status,
            icon = VaultIcons.Apps,
            contentDescription = entryDescription,
            testTag = "private_apps_entry",
            hasError = state.loadFailed,
            onClick = onOpenPrivateApps,
        )
        Text(
            text = stringResource(R.string.app_management_scope_note),
            style = AppTextStyles.BodySecondary,
            color = AppColors.TextTertiary,
            modifier = Modifier.padding(horizontal = AppSpacing.xs),
        )
    }
}

@Composable
private fun AppManagementEntryCard(
    title: String,
    description: String,
    status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    testTag: String,
    hasError: Boolean,
    onClick: () -> Unit,
) {
    VaultCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 144.dp)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(AppColors.SurfaceSecondary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppColors.AccentPrimary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(AppSpacing.md))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
            ) {
                Text(title, style = AppTextStyles.CardTitle, color = AppColors.TextPrimary)
                Text(
                    description,
                    style = AppTextStyles.BodySecondary,
                    color = AppColors.TextSecondary,
                )
                Text(
                    status,
                    style = AppTextStyles.Caption,
                    color = if (hasError) AppColors.Warning else AppColors.AccentSecondary,
                )
            }
            Icon(VaultIcons.Chevron, contentDescription = null, tint = AppColors.TextTertiary)
        }
    }
}
