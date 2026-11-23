package com.aurora.calculatorvault.feature.membership.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementDisplayMapper
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementState
import com.aurora.calculatorvault.feature.membership.domain.VipPlan
import com.aurora.calculatorvault.ui.component.VaultIconButton
import com.aurora.calculatorvault.ui.component.VaultSectionTitle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun VipMembershipInfoScreen(
    entitlement: EntitlementState,
    onBack: () -> Unit,
) {
    val vip = entitlement.source as? EntitlementSource.Vip
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("vip_info_page"),
        containerColor = AppColors.BackgroundPrimary,
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
                    style = AppTextStyles.SectionTitle,
                    color = AppColors.TextPrimary,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Text(
                stringResource(R.string.vip_info_status_active),
                style = AppTextStyles.SectionTitle,
                color = AppColors.AccentPrimary,
                modifier = Modifier.testTag("vip_info_status"),
            )
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
                    color = AppColors.TextPrimary,
                )
                if (vip.permanent) {
                    Text(
                        stringResource(R.string.vip_info_lifetime),
                        style = AppTextStyles.BodySecondary,
                        color = AppColors.TextSecondary,
                    )
                } else if (vip.expiresAtEpochMillis != null) {
                    val formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(vip.expiresAtEpochMillis))
                    Text(
                        stringResource(R.string.vip_info_expires, formatted),
                        style = AppTextStyles.BodySecondary,
                        color = AppColors.TextSecondary,
                    )
                }
            }
            VaultSectionTitle(stringResource(R.string.vip_benefits_title))
            Text(stringResource(R.string.vip_benefit_lock_description), style = AppTextStyles.BodySecondary, color = AppColors.TextSecondary)
            Text(stringResource(R.string.vip_benefit_album_description), style = AppTextStyles.BodySecondary, color = AppColors.TextSecondary)
            Text(stringResource(R.string.vip_benefit_clean_description), style = AppTextStyles.BodySecondary, color = AppColors.TextSecondary)
        }
    }
}
