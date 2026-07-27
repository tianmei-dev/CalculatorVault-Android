package com.aurora.calculatorvault.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles

@Composable
fun VaultCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = AppShapes.Large,
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfacePrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(AppSpacing.md), content = content)
    }
}

@Composable
fun VaultSearchBar(
    placeholder: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimensions.SearchBar)
            .background(AppColors.SurfacePrimary, AppShapes.Medium)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(VaultIcons.Search, contentDescription = null, tint = AppColors.TextTertiary)
        Spacer(Modifier.width(AppSpacing.sm))
        Text(placeholder, style = AppTextStyles.Body, color = AppColors.TextTertiary)
    }
}

@Composable
fun VaultSettingItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(AppDimensions.SettingItem),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = AppColors.TextSecondary)
                Spacer(Modifier.width(AppSpacing.sm))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = AppTextStyles.CardTitle, color = AppColors.TextPrimary)
                subtitle?.let { Text(it, style = AppTextStyles.Caption, color = AppColors.TextTertiary) }
            }
            Icon(VaultIcons.Chevron, contentDescription = null, tint = AppColors.TextTertiary)
        }
        if (showDivider) HorizontalDivider(color = AppColors.Divider)
    }
}

@Composable
fun VaultSwitchSettingItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(AppDimensions.SettingItem),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = AppTextStyles.CardTitle, color = AppColors.TextPrimary)
            subtitle?.let { Text(it, style = AppTextStyles.Caption, color = AppColors.TextTertiary) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.TextPrimary,
                checkedTrackColor = AppColors.AccentSecondary,
                uncheckedThumbColor = AppColors.TextTertiary,
                uncheckedTrackColor = AppColors.SurfaceElevated,
                uncheckedBorderColor = AppColors.BorderSubtle,
            ),
        )
    }
}

@Composable
fun VaultSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = AppTextStyles.SectionTitle,
        color = AppColors.TextPrimary,
    )
}

@Composable
fun VaultEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = VaultIcons.Info,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(AppSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(AppColors.SurfaceSecondary, AppShapes.Full),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AppColors.AccentPrimary)
        }
        Text(title, style = AppTextStyles.CardTitle, color = AppColors.TextPrimary)
        Text(description, style = AppTextStyles.BodySecondary, color = AppColors.TextTertiary)
    }
}

@Composable
fun VaultLoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = AppColors.AccentPrimary,
            trackColor = AppColors.SurfaceSecondary,
        )
    }
}

