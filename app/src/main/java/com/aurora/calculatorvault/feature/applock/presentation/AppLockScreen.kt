package com.aurora.calculatorvault.feature.applock.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.disguise.presentation.PageHeader
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultSectionTitle

@Composable
fun AppLockScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_lock_screen")
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        PageHeader(
            title = stringResource(R.string.tab_app_lock),
            description = stringResource(R.string.app_lock_subtitle),
        )
        VaultCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(AppColors.SurfaceSecondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        VaultIcons.Lock,
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
                    Text(
                        stringResource(R.string.app_lock_protect_title),
                        style = AppTextStyles.CardTitle,
                        color = AppColors.TextPrimary,
                    )
                    Text(
                        stringResource(R.string.app_lock_description),
                        style = AppTextStyles.BodySecondary,
                        color = AppColors.TextSecondary,
                    )
                }
            }
        }
        VaultSectionTitle(stringResource(R.string.app_lock_validation_status))
        VaultCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(VaultIcons.Security, null, tint = AppColors.Warning)
                Spacer(Modifier.width(AppSpacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    Text(
                        stringResource(R.string.app_lock_validation_pending),
                        style = AppTextStyles.CardTitle,
                        color = AppColors.TextPrimary,
                    )
                    Text(
                        stringResource(R.string.app_lock_permission_note),
                        style = AppTextStyles.BodySecondary,
                        color = AppColors.TextSecondary,
                    )
                    Text(
                        stringResource(R.string.app_lock_compatibility_note),
                        style = AppTextStyles.Caption,
                        color = AppColors.TextTertiary,
                    )
                }
            }
        }
    }
}
