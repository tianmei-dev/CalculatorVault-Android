package com.aurora.calculatorvault.feature.hiddenapp.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.aurora.calculatorvault.feature.disguise.presentation.PageHeader
import com.aurora.calculatorvault.ui.component.VaultCard
import com.aurora.calculatorvault.ui.component.VaultPrimaryButton
import com.aurora.calculatorvault.ui.component.VaultSearchBar
import com.aurora.calculatorvault.ui.component.VaultSectionTitle

@Composable
fun HiddenAppScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        PageHeader(
            title = stringResource(R.string.tab_hidden_app),
            description = stringResource(R.string.hidden_description),
        )
        VaultSearchBar(placeholder = stringResource(R.string.search_apps))
        VaultSectionTitle(stringResource(R.string.recent_opened))
        VaultCard(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xl)) {
                AppTile(stringResource(R.string.example_browser), VaultIcons.Apps)
                AppTile(stringResource(R.string.example_mail), VaultIcons.Mail)
                AppTile(stringResource(R.string.example_files), VaultIcons.Files)
            }
        }
        VaultSectionTitle(stringResource(R.string.my_apps))
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AppTile(stringResource(R.string.example_calendar), VaultIcons.Timer)
                AppTile(stringResource(R.string.example_music), VaultIcons.Video)
                AppTile(stringResource(R.string.example_document), VaultIcons.Files)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AppTile(stringResource(R.string.example_notes), VaultIcons.Info)
                AppTile(stringResource(R.string.example_weather), VaultIcons.Weather)
                AppTile(stringResource(R.string.example_focus), VaultIcons.Timer)
            }
        }
        VaultPrimaryButton(text = stringResource(R.string.add_app), onClick = {})
    }
}

@Composable
private fun AppTile(title: String, icon: ImageVector) {
    Column(
        modifier = Modifier.size(width = 88.dp, height = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(AppColors.SurfaceSecondary, AppShapes.Medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AppColors.AccentPrimary)
        }
        Text(title, style = AppTextStyles.Caption, color = AppColors.TextSecondary)
    }
}

