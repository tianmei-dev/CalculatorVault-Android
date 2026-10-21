package com.aurora.calculatorvault.feature.membership.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.membership.domain.PurchaseActionState
import com.aurora.calculatorvault.feature.membership.domain.VipPaymentMethod
import com.aurora.calculatorvault.feature.membership.domain.VipPlan
import com.aurora.calculatorvault.ui.component.VaultIconButton
import com.aurora.calculatorvault.ui.component.VaultSectionTitle
import com.aurora.calculatorvault.ui.message.LocalAppMessageController

@Composable
fun VipPurchaseScreen(
    viewModel: VipPurchaseViewModel,
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val messages = LocalAppMessageController.current
    val agreementRequired = stringResource(R.string.vip_agreement_required)
    val paymentPlaceholder = stringResource(R.string.vip_payment_placeholder)
    val servicePending = stringResource(R.string.vip_service_pending)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Reserve space for the body even in a short window or at a large font scale.
        // Only the supplementary footer content scrolls; the purchase button stays visible.
        val footerMaxHeight = maxOf(maxHeight * 0.5f, AppDimensions.Button + AppSpacing.md)
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("vip_purchase_page"),
            containerColor = AppColors.BackgroundPrimary,
            // The enclosing vault Scaffold already supplies and consumes safeDrawing insets.
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
                        modifier = Modifier.testTag("vip_back"),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                        Text(stringResource(R.string.vip_purchase_title), style = AppTextStyles.SectionTitle,
                            color = AppColors.TextPrimary)
                        Text(stringResource(R.string.vip_purchase_subtitle), style = AppTextStyles.Caption,
                            color = AppColors.TextSecondary)
                    }
                }
            },
            bottomBar = {
                // The entire purchase footer stays outside the scrolling page body.
                Surface(modifier = Modifier.testTag("vip_purchase_footer"), color = AppColors.SurfacePrimary) {
                    Column {
                        HorizontalDivider(color = AppColors.BorderSubtle)
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = footerMaxHeight)
                                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                        ) {
                            Button(
                                onClick = {
                                    when (viewModel.requestPurchase()) {
                                        PurchaseActionState.AgreementRequired -> messages.showWarning(agreementRequired)
                                        PurchaseActionState.PaymentUnavailable -> messages.showInfo(paymentPlaceholder)
                                        else -> Unit
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = AppDimensions.Button)
                                    .testTag("vip_purchase_button"),
                                shape = AppShapes.Full,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.AccentPrimary,
                                    contentColor = AppColors.BackgroundPrimary,
                                ),
                            ) {
                                Text(
                                    stringResource(R.string.vip_purchase_action, state.selectedPlan.priceText),
                                    style = AppTextStyles.Button,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                            ) {
                                Text(stringResource(R.string.vip_non_renewing_notice),
                                    style = AppTextStyles.Caption, color = AppColors.TextSecondary,
                                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                PurchaseAgreement(
                                    accepted = state.agreementAccepted,
                                    onChanged = viewModel::setAgreementAccepted,
                                    onServiceAgreement = { messages.showInfo(servicePending) },
                                    onPrivacyPolicy = onOpenPrivacyPolicy,
                                )
                                Text(
                                    stringResource(R.string.vip_special_notice),
                                    style = AppTextStyles.Caption.copy(lineBreak = LineBreak.Paragraph),
                                    color = AppColors.TextSecondary,
                                    modifier = Modifier.fillMaxWidth().testTag("vip_special_notice"),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            BoxWithConstraints(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            ) {
                // Keep plans compact instead of stretching their cards on tall screens.
                // Content can still grow to accommodate wrapping and larger text.
                val moduleHeight = (maxHeight - AppSpacing.xl - AppSpacing.xs).coerceAtLeast(0.dp)
                val paymentCardHeight = (moduleHeight * 0.20f - 28.dp).coerceAtLeast(AppDimensions.Button)
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight).padding(horizontal = AppSpacing.md)
                        .padding(top = AppSpacing.xxs, bottom = AppSpacing.xxs)
                        .testTag("vip_purchase_content"),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                        VaultSectionTitle(stringResource(R.string.vip_benefits_title))
                        BenefitsCard(Modifier.heightIn(min = (moduleHeight * 0.36f - 28.dp).coerceAtLeast(0.dp)))
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                    ) {
                        VaultSectionTitle(stringResource(R.string.vip_plans_title))
                        PlanSelector(state.selectedPlan, viewModel::selectPlan)
                    }

                    // Match the plan section's 4dp gap + 12dp floating-badge inset.
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                        VaultSectionTitle(stringResource(R.string.vip_payment_title))
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max).selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                        ) {
                            VipPaymentMethod.entries.forEach { method ->
                                PaymentOption(
                                    method, method == state.selectedPaymentMethod,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    minHeight = paymentCardHeight,
                                ) { viewModel.selectPaymentMethod(method) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BenefitsCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("vip_benefits_card"),
        shape = AppShapes.Large,
        color = AppColors.SurfacePrimary,
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs, Alignment.CenterVertically),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Benefit(VaultIcons.Lock, stringResource(R.string.vip_benefit_lock),
                    stringResource(R.string.vip_benefit_lock_description), Modifier.weight(1f))
                Benefit(VaultIcons.Photos, stringResource(R.string.vip_benefit_album),
                    stringResource(R.string.vip_benefit_album_description), Modifier.weight(1f))
            }
            Benefit(VaultIcons.Success, stringResource(R.string.vip_benefit_clean),
                stringResource(R.string.vip_benefit_clean_description), inline = true)
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, title: String, description: String, modifier: Modifier = Modifier, inline: Boolean = false) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = AppColors.AccentPrimary)
        if (inline) {
            Text(
                buildAnnotatedString {
                    withStyle(AppTextStyles.BodySecondary.toSpanStyle().copy(fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary)) { append(title) }
                    append("  ")
                    append(description)
                },
                modifier = Modifier.weight(1f),
                style = AppTextStyles.Caption.copy(lineBreak = LineBreak.Paragraph),
                color = AppColors.TextSecondary,
            )
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                Text(title, style = AppTextStyles.BodySecondary.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.TextPrimary)
                Text(description, style = AppTextStyles.Caption.copy(lineBreak = LineBreak.Paragraph),
                    color = AppColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun PlanSelector(selected: VipPlan, onSelected: (VipPlan) -> Unit) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Keep all three cards on one row; allow scrolling instead of shrinking large text.
        val cardWidth = maxOf((maxWidth - AppSpacing.xs * 2) / 3,
            (104 * density.fontScale.coerceAtLeast(1f)).dp)
        LaunchedEffect(selected, cardWidth) {
            val offset = with(density) { ((cardWidth + AppSpacing.xs) * selected.ordinal).roundToPx() }
            scroll.animateScrollTo(offset)
        }
        Row(
            modifier = Modifier.horizontalScroll(scroll).height(IntrinsicSize.Max).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            VipPlan.entries.forEach { plan ->
                // Keep the floating badge inside the scroll viewport, outside the card clip.
                Box(Modifier.width(cardWidth).fillMaxHeight()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.sm)
                            .heightIn(min = 140.dp).fillMaxHeight()
                            .selectable(selected = selected == plan, role = Role.RadioButton,
                                onClick = { onSelected(plan) })
                            .testTag("vip_plan_${plan.name}"),
                        shape = AppShapes.Medium,
                        color = if (selected == plan) AppColors.AccentContainer else AppColors.SurfacePrimary,
                        border = BorderStroke(if (selected == plan) 2.dp else 1.dp,
                            if (selected == plan) AppColors.AccentPrimary else AppColors.BorderSubtle),
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.xs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs, Alignment.CenterVertically),
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Icon(
                                    if (plan == VipPlan.Lifetime) VaultIcons.Security else VaultIcons.Calendar,
                                    null, Modifier.size(20.dp), tint = AppColors.AccentPrimary,
                                )
                            }
                            Text(stringResource(when (plan) {
                                VipPlan.Monthly -> R.string.vip_monthly
                                VipPlan.Annual -> R.string.vip_annual
                                VipPlan.Lifetime -> R.string.vip_lifetime
                            }), style = AppTextStyles.BodySecondary, color = AppColors.TextPrimary,
                                textAlign = TextAlign.Center)
                            Text(stringResource(R.string.vip_price, plan.priceText),
                                style = AppTextStyles.PageTitle, color = AppColors.AccentPrimary,
                                textAlign = TextAlign.Center)
                            Text(plan.durationDays?.let { stringResource(R.string.vip_duration_days, it) }
                                ?: stringResource(R.string.vip_duration_lifetime),
                                style = AppTextStyles.Caption, color = AppColors.TextSecondary,
                                textAlign = TextAlign.Center)
                        }
                    }
                    if (plan == VipPlan.Annual) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd)
                                .offset(x = -AppSpacing.xs, y = AppSpacing.xs).rotate(12f),
                            shape = AppShapes.Small,
                            color = AppColors.Warning,
                            shadowElevation = 4.dp,
                        ) {
                            Text(
                                stringResource(R.string.vip_plan_recommended),
                                modifier = Modifier.padding(horizontal = AppSpacing.xs, vertical = AppSpacing.xxs),
                                style = AppTextStyles.Caption.copy(fontWeight = FontWeight.SemiBold),
                                color = AppColors.BackgroundPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentOption(
    method: VipPaymentMethod,
    selected: Boolean,
    modifier: Modifier = Modifier,
    minHeight: Dp = AppDimensions.Button,
    onSelected: () -> Unit,
) {
    Surface(
        modifier = modifier.heightIn(min = minHeight)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelected)
            .testTag("vip_payment_${method.name}"),
        shape = AppShapes.Small,
        color = if (selected) AppColors.AccentContainer else AppColors.SurfacePrimary,
        border = BorderStroke(1.dp, if (selected) AppColors.AccentPrimary else AppColors.BorderSubtle),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
        ) {
            Icon(painterResource(if (method == VipPaymentMethod.WeChat) R.drawable.ic_payment_wechat
                else R.drawable.ic_payment_alipay), null, Modifier.size(24.dp), tint = Color.Unspecified)
            Text(stringResource(if (method == VipPaymentMethod.WeChat) R.string.vip_payment_wechat
                else R.string.vip_payment_alipay), modifier = Modifier.weight(1f),
                style = AppTextStyles.Body, color = AppColors.TextPrimary)
            RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(
                selectedColor = AppColors.AccentPrimary, unselectedColor = AppColors.TextTertiary))
        }
    }
}

@Composable
private fun PurchaseAgreement(
    accepted: Boolean,
    onChanged: (Boolean) -> Unit,
    onServiceAgreement: () -> Unit,
    onPrivacyPolicy: () -> Unit,
) {
    val prefix = stringResource(R.string.vip_agreement_prefix)
    val service = stringResource(R.string.vip_agreement_service)
    val and = stringResource(R.string.vip_agreement_and)
    val privacy = stringResource(R.string.vip_agreement_privacy)
    val linkStyles = TextLinkStyles(SpanStyle(
        color = AppColors.AccentPrimary, textDecoration = TextDecoration.Underline))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = accepted,
            onCheckedChange = onChanged,
            modifier = Modifier.testTag("vip_agreement").semantics {
                contentDescription = prefix + service + and + privacy
            },
            colors = CheckboxDefaults.colors(checkedColor = AppColors.AccentPrimary,
                uncheckedColor = AppColors.TextSecondary, checkmarkColor = AppColors.BackgroundPrimary),
        )
        Text(
            buildAnnotatedString {
                append(prefix)
                withLink(LinkAnnotation.Clickable("vip_service", linkStyles) { onServiceAgreement() }) {
                    append(service)
                }
                append(and)
                withLink(LinkAnnotation.Clickable("vip_privacy", linkStyles) { onPrivacyPolicy() }) {
                    append(privacy)
                }
            },
            modifier = Modifier.weight(1f),
            style = AppTextStyles.BodySecondary.copy(lineBreak = LineBreak.Paragraph),
            color = AppColors.TextSecondary,
        )
    }
}
