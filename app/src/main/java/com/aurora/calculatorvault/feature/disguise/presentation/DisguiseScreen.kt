package com.aurora.calculatorvault.feature.disguise.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultPrimaryButton
import com.aurora.calculatorvault.ui.component.VaultSectionTitle
import com.aurora.calculatorvault.ui.layout.appPagePadding

@Composable
fun DisguiseScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(appPagePadding()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        PageHeader(
            title = stringResource(R.string.tab_disguise),
            description = stringResource(R.string.disguise_description),
        )
        VaultCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    VaultIcons.Lock,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .background(AppColors.AccentContainer, AppShapes.Medium)
                        .padding(AppSpacing.sm),
                    tint = AppColors.AccentPrimary,
                )
                Column(modifier = Modifier.padding(start = AppSpacing.md)) {
                    Text(
                        stringResource(R.string.lock_status_title),
                        style = AppTextStyles.CardTitle,
                        color = AppColors.TextPrimary,
                    )
                    Text(
                        stringResource(R.string.lock_status_value),
                        style = AppTextStyles.BodySecondary,
                        color = AppColors.Warning,
                    )
                    Text(
                        stringResource(R.string.disguise_status_note),
                        style = AppTextStyles.Caption,
                        color = AppColors.TextTertiary,
                    )
                }
            }
        }
        VaultSectionTitle(stringResource(R.string.disguise_examples))
        DisguiseExample(stringResource(R.string.example_notes), VaultIcons.Files)
        DisguiseExample(stringResource(R.string.example_weather), VaultIcons.Weather)
        DisguiseExample(stringResource(R.string.example_focus), VaultIcons.Timer)
        VaultPrimaryButton(text = stringResource(R.string.add_disguise), onClick = {})
    }
}

@Composable
private fun DisguiseExample(title: String, icon: ImageVector) {
    VaultCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AppColors.TextSecondary)
            Text(
                text = title,
                modifier = Modifier.padding(start = AppSpacing.md),
                style = AppTextStyles.CardTitle,
                color = AppColors.TextPrimary,
            )
        }
    }
}

@Composable
internal fun PageHeader(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(title, style = AppTextStyles.PageTitle, color = AppColors.TextPrimary)
        Text(description, style = AppTextStyles.Body, color = AppColors.TextSecondary)
    }
}
