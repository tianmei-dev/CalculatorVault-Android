package com.aurora.calculatorvault.feature.privatemedia.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.disguise.presentation.PageHeader
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultPrimaryButton
import com.aurora.calculatorvault.ui.component.VaultSectionTitle
import com.aurora.calculatorvault.ui.layout.appPagePadding

@Composable
fun PrivateMediaScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(appPagePadding()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        PageHeader(
            title = stringResource(R.string.tab_private_media),
            description = stringResource(R.string.media_description),
        )
        VaultSectionTitle(stringResource(R.string.media_overview))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            MediaStat(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.photos),
                value = stringResource(R.string.photo_count),
                icon = VaultIcons.Image,
            )
            MediaStat(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.videos),
                value = stringResource(R.string.video_count),
                icon = VaultIcons.Video,
            )
        }
        VaultSectionTitle(stringResource(R.string.recent_imports))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.82f)
                        .background(AppColors.SurfaceSecondary, AppShapes.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VaultIcons.Image, contentDescription = null, tint = AppColors.TextDisabled)
                }
            }
        }
        VaultPrimaryButton(text = stringResource(R.string.import_media), onClick = {})
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
