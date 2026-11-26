package com.aurora.calculatorvault.feature.membership.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementDisplayMapper
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementState
import com.aurora.calculatorvault.feature.membership.domain.VipPlan
import com.aurora.calculatorvault.ui.component.VaultIconButton
import com.aurora.calculatorvault.ui.component.VaultMainColors
import com.aurora.calculatorvault.ui.component.VaultMainIconTile
import com.aurora.calculatorvault.ui.component.useVaultDarkVisuals
import com.aurora.calculatorvault.ui.component.vaultMainBackdrop
import com.aurora.calculatorvault.ui.component.vaultMainPanel
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun VipMembershipInfoScreen(
    entitlement: EntitlementState,
    onBack: () -> Unit,
) {
    val vip = entitlement.source as? EntitlementSource.Vip
    val dark = useVaultDarkVisuals()
    val theme = MaterialTheme.colorScheme
    val primary = if (dark) VaultMainColors.TextPrimary else theme.onSurface
    val secondary = if (dark) VaultMainColors.TextSecondary else theme.onSurfaceVariant
    val accent = if (dark) VaultMainColors.Highlight else theme.primary
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("vip_info_page")
            .then(if (dark) Modifier.vaultMainBackdrop() else Modifier),
        containerColor = if (dark) Color.Transparent else theme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = AppDimensions.TopAppBar)
                    .padding(horizontal = AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VaultIconButton(
                    icon = VaultIcons.Back,
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack,
                    modifier = Modifier.testTag("vip_info_back"),
                )
                Text(
                    stringResource(R.string.vip_info_title),
                    style = AppTextStyles.SectionTitle.copy(fontSize = AppTextStyles.PageTitle.fontSize),
                    color = primary,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .then(if (dark) Modifier.vaultMainPanel(emphasized = true, shape = AppShapes.Large) else Modifier)
                    .padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                ) {
                    if (dark) VaultMainIconTile(VaultIcons.Security, emphasized = true, size = 32.dp, iconSize = 20.dp)
                    Text(
                        stringResource(R.string.vip_info_status_active),
                        style = AppTextStyles.SectionTitle.copy(fontSize = AppTextStyles.CardTitle.fontSize),
                        color = accent,
                        modifier = Modifier.testTag("vip_info_status"),
                    )
                }
                if (vip != null) {
                    // Same plan-code mapping as the settings membership card.
                    val plan = EntitlementDisplayMapper.resolveDisplayPlan(
                        vip.planType,
                        vip.contributingPlanTypes,
                        permanent = vip.permanent,
                    )
                    val typeLabel = when (plan) {
                        VipPlan.Monthly -> stringResource(R.string.account_vip_monthly)
                        VipPlan.Annual -> stringResource(R.string.account_vip_annual)
                        VipPlan.Lifetime -> stringResource(R.string.account_vip_permanent)
                        null -> stringResource(R.string.vip_title)
                    }
                    Text(
                        stringResource(R.string.vip_info_type, typeLabel),
                        style = AppTextStyles.Body,
                        color = primary,
                    )
                    if (vip.permanent) {
                        Text(
                            stringResource(R.string.vip_info_lifetime),
                            style = AppTextStyles.BodySecondary,
                            color = secondary,
                        )
                    } else if (vip.expiresAtEpochMillis != null) {
                        val formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                            .withZone(ZoneId.systemDefault())
                            .format(Instant.ofEpochMilli(vip.expiresAtEpochMillis))
                        Text(
                            stringResource(R.string.vip_info_expires, formatted),
                            style = AppTextStyles.BodySecondary,
                            color = secondary,
                        )
                    }
                }
            }
            Text(stringResource(R.string.vip_benefits_title), style = AppTextStyles.SectionTitle, color = primary)
            Column(
                modifier = Modifier.fillMaxWidth()
                    .then(if (dark) Modifier.vaultMainPanel(shape = AppShapes.Large) else Modifier)
                    .padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                Text(
                    stringResource(R.string.vip_benefit_lock_description),
                    style = AppTextStyles.BodySecondary,
                    color = secondary,
                )
                Text(
                    stringResource(R.string.vip_benefit_album_description),
                    style = AppTextStyles.BodySecondary,
                    color = secondary,
                )
                Text(
                    stringResource(R.string.vip_benefit_clean_description),
                    style = AppTextStyles.BodySecondary,
                    color = secondary,
                )
            }
        }
    }
}
