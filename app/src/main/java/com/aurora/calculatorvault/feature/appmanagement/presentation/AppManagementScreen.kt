package com.aurora.calculatorvault.feature.appmanagement.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.ui.component.VaultMainColors
import com.aurora.calculatorvault.ui.component.VaultMainHeader
import com.aurora.calculatorvault.ui.component.VaultMainIconTile
import com.aurora.calculatorvault.ui.component.useVaultDarkVisuals
import com.aurora.calculatorvault.ui.component.vaultMainBackdrop
import com.aurora.calculatorvault.ui.component.vaultMainPanel
import com.aurora.calculatorvault.ui.layout.appPagePadding

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .vaultMainBackdrop(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_management_screen")
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(appPagePadding(horizontal = AppSpacing.lg, top = AppSpacing.lg, bottom = AppSpacing.sm)),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                VaultMainHeader(
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
                    emphasized = true,
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
                    emphasized = false,
                    onClick = onOpenPrivateApps,
                )
            }
            AppManagementNotice(
                modifier = Modifier.padding(
                    start = AppSpacing.xs,
                    top = AppSpacing.lg,
                    end = AppSpacing.xs,
                ),
            )
        }
    }
}

@Composable
private fun AppManagementNotice(modifier: Modifier = Modifier) {
    val dark = useVaultDarkVisuals()
    val textColor = if (dark) VaultMainColors.TextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = AppShapes.Small
    val notes = listOf(
        stringResource(R.string.app_management_notice_collection),
        stringResource(R.string.app_management_notice_shortcut),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(VaultMainColors.Border.copy(alpha = if (dark) 0.24f else 0.08f), shape)
            .border(1.dp, VaultMainColors.Border.copy(alpha = if (dark) 0.52f else 0.20f), shape)
            .padding(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = VaultIcons.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = textColor,
            )
            Text(
                text = stringResource(R.string.app_management_notice_title),
                style = AppTextStyles.BodySecondary.copy(fontWeight = FontWeight.Medium),
                color = textColor,
            )
        }
        notes.forEachIndexed { index, note ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Text("${index + 1}.", style = AppTextStyles.Caption, color = textColor)
                Text(note, modifier = Modifier.weight(1f), style = AppTextStyles.Caption, color = textColor)
            }
        }
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
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 130.dp)
            .vaultMainPanel(
                emphasized = emphasized,
                shape = RoundedCornerShape(20.dp),
                accentSweep = !emphasized,
            )
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VaultMainIconTile(icon, emphasized = emphasized, size = 52.dp, iconSize = 29.dp)
        Spacer(Modifier.width(AppSpacing.sm))
        Box(Modifier.width(1.dp).height(54.dp).background(VaultMainColors.Border))
        Spacer(Modifier.width(AppSpacing.sm))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
        ) {
            Text(
                title,
                style = AppTextStyles.SectionTitle,
                color = VaultMainColors.TextPrimary,
                fontSize = AppTextStyles.CardTitle.fontSize,
            )
            Text(description, style = AppTextStyles.BodySecondary, color = VaultMainColors.TextSecondary)
            Text(
                status,
                style = AppTextStyles.BodySecondary.copy(fontWeight = FontWeight.Medium),
                color = if (hasError) AppColors.Warning else VaultMainColors.Accent,
            )
        }
        Spacer(Modifier.width(AppSpacing.xxs))
        Icon(VaultIcons.Chevron, contentDescription = null, tint = VaultMainColors.Highlight, modifier = Modifier.size(19.dp))
    }
}
