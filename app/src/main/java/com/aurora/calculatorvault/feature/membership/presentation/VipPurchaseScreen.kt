package com.aurora.calculatorvault.feature.membership.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.legal.presentation.LegalDocumentType
import com.aurora.calculatorvault.feature.membership.domain.MembershipSyncAfterPurchase
import com.aurora.calculatorvault.feature.membership.domain.OrderQueryIssue
import com.aurora.calculatorvault.feature.membership.domain.OrderView
import com.aurora.calculatorvault.feature.membership.domain.PlansState
import com.aurora.calculatorvault.feature.membership.domain.PurchaseNotice
import com.aurora.calculatorvault.feature.membership.domain.PurchasePhase
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseState
import com.aurora.calculatorvault.feature.payment.data.MembershipPlanOffer
import com.aurora.calculatorvault.feature.payment.data.PaymentError
import com.aurora.calculatorvault.feature.payment.data.PaymentOrderStatus
import com.aurora.calculatorvault.feature.payment.domain.PayLaunchDecision
import com.aurora.calculatorvault.ui.component.VaultIconButton
import com.aurora.calculatorvault.ui.component.VaultMainColors
import com.aurora.calculatorvault.ui.component.VaultMainIconTile
import com.aurora.calculatorvault.ui.component.useVaultDarkVisuals
import com.aurora.calculatorvault.ui.component.vaultGradientAction
import com.aurora.calculatorvault.ui.component.vaultMainBackdrop
import com.aurora.calculatorvault.ui.component.vaultMainPanel
import com.aurora.calculatorvault.ui.message.AppMessageController
import com.aurora.calculatorvault.ui.message.LocalAppMessageController
import kotlinx.coroutines.delay

@Composable
fun VipPurchaseScreen(
    viewModel: VipPurchaseViewModel,
    onBack: () -> Unit,
    onOpenDocument: (LegalDocumentType) -> Unit,
    onRequestLogin: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val messages = LocalAppMessageController.current
    val context = LocalContext.current
    val noticeText = noticeTexts()
    val browserUnavailable = stringResource(R.string.vip_notice_browser_unavailable)
    val dark = useVaultDarkVisuals()
    val colors = vipPurchaseColors()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is VipPurchaseEvent.Notice -> showNotice(messages, event.notice, noticeText)
                VipPurchaseEvent.OpenLogin -> onRequestLogin()
                is VipPurchaseEvent.OpenCashier -> {
                    // System browser only; no WebView and no payment SDK. The link is not logged.
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        messages.showWarning(browserUnavailable)
                    }
                }
            }
        }
    }

    // Resume covers both returning from the cashier and the app returning to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onVisibilityChanged(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.onVisibilityChanged(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().then(if (dark) Modifier.vaultMainBackdrop() else Modifier)) {
        val footerMaxHeight = maxOf(maxHeight * 0.5f, AppDimensions.Button + AppSpacing.md)
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("vip_purchase_page"),
            containerColor = if (dark) Color.Transparent else colors.background,
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
                        Text(
                            stringResource(R.string.vip_purchase_title),
                            style = AppTextStyles.SectionTitle.copy(fontSize = AppTextStyles.PageTitle.fontSize),
                            color = colors.onSurface,
                        )
                        Text(
                            stringResource(R.string.vip_purchase_subtitle),
                            style = AppTextStyles.Caption.copy(fontSize = AppTextStyles.BodySecondary.fontSize),
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            },
            bottomBar = {
                Box(
                    modifier = Modifier.fillMaxWidth().testTag("vip_purchase_footer")
                        .background(
                            Brush.verticalGradient(listOf(colors.surface, colors.background)),
                        ),
                ) {
                    Column {
                        HorizontalDivider(color = colors.outline)
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = footerMaxHeight)
                                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                        ) {
                            FooterAction(state, viewModel, onBack)
                            Column(
                                modifier = Modifier.weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                            ) {
                                Text(stringResource(R.string.vip_non_renewing_notice),
                                    style = AppTextStyles.Caption, color = colors.onSurfaceVariant,
                                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                PurchaseAgreement(
                                    accepted = state.agreementAccepted,
                                    onChanged = viewModel::setAgreementAccepted,
                                    onServiceAgreement = { onOpenDocument(LegalDocumentType.MembershipServiceAgreement) },
                                    onPrivacyPolicy = { onOpenDocument(LegalDocumentType.PrivacyPolicy) },
                                )
                                Text(
                                    stringResource(R.string.vip_special_notice),
                                    style = AppTextStyles.Caption.copy(lineBreak = LineBreak.Paragraph),
                                    color = colors.onSurfaceVariant,
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
                // Measure the viewport after the header and pinned footer, before scrolling makes
                // the height unbounded. Keep the compact rhythm for narrow or large-text layouts.
                val fontScale = LocalDensity.current.fontScale
                val textReserve = 160.dp * (fontScale - 1f).coerceAtLeast(0f)
                val wrapReserve = (360.dp - maxWidth).coerceAtLeast(0.dp) * 0.6f
                val isCatalog = state.phase == PurchasePhase.Idle || state.phase == PurchasePhase.Creating
                val room = if (isCatalog && !state.lifetimeOwned) {
                    (maxHeight - 420.dp - textReserve - wrapReserve).coerceIn(0.dp, 160.dp)
                } else {
                    0.dp
                }
                val headingGap = AppSpacing.xxs + room * 0.06f
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AppSpacing.md)
                        .padding(top = AppSpacing.xxs + room * 0.12f, bottom = AppSpacing.xxs + room * 0.06f)
                        .testTag("vip_purchase_content"),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs + room * 0.15f),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(headingGap)) {
                        VipSectionHeading(stringResource(R.string.vip_benefits_title))
                        BenefitsCard(room)
                    }
                    if (state.lifetimeOwned) {
                        InfoCard(stringResource(R.string.vip_lifetime_owned_banner), Modifier.testTag("vip_lifetime_owned"))
                    }
                    when (val phase = state.phase) {
                        is PurchasePhase.Ordering -> OrderPanel(phase, viewModel)
                        is PurchasePhase.Resuming -> InfoCard(stringResource(R.string.vip_order_resuming))
                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(headingGap)) {
                                VipSectionHeading(stringResource(R.string.vip_plans_title))
                                PlansSection(state, viewModel, room)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(headingGap)) {
                                VipSectionHeading(stringResource(R.string.vip_payment_title))
                                PayChannelSection(state, viewModel)
                            }
                            if (phase is PurchasePhase.CreateFailed) {
                                InfoCard(stringResource(R.string.vip_create_failed), Modifier.testTag("vip_create_failed"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun vipPurchaseColors(): ColorScheme {
    val base = MaterialTheme.colorScheme
    val dark = useVaultDarkVisuals()
    return remember(base, dark) {
        if (dark) base.copy(
            primary = VaultMainColors.Highlight,
            background = VaultMainColors.Background,
            surface = VaultMainColors.Card,
            onSurface = VaultMainColors.TextPrimary,
            onSurfaceVariant = VaultMainColors.TextSecondary,
            outline = VaultMainColors.Border,
        ) else base
    }
}

@Composable
private fun Modifier.vipPanel(shape: Shape, emphasized: Boolean = false): Modifier {
    return if (useVaultDarkVisuals()) {
        vaultMainPanel(emphasized = emphasized, shape = shape)
    } else {
        val colors = MaterialTheme.colorScheme
        clip(shape)
            .background(if (emphasized) colors.primaryContainer else colors.surface, shape)
            .border(1.dp, if (emphasized) colors.primary else colors.outlineVariant, shape)
    }
}

@Composable
private fun VipIconTile(icon: ImageVector, emphasized: Boolean = false, size: Dp) {
    if (useVaultDarkVisuals()) {
        VaultMainIconTile(icon, emphasized = emphasized, size = size, iconSize = 18.dp)
    } else {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun VipSectionHeading(title: String) {
    Text(title, style = AppTextStyles.SectionTitle, color = vipPurchaseColors().onSurface)
}

@Composable
private fun FooterAction(state: VipPurchaseState, viewModel: VipPurchaseViewModel, onBack: () -> Unit) {
    val dark = useVaultDarkVisuals()
    val colors = vipPurchaseColors()
    val phase = state.phase
    val (label, enabled, action) = when {
        phase is PurchasePhase.Ordering -> when (phase.order.status) {
            PaymentOrderStatus.PENDING_PAYMENT ->
                if (phase.order.payDecision is PayLaunchDecision.OpenCashier) {
                    Triple(stringResource(R.string.vip_order_go_pay), true, viewModel::openCashier)
                } else {
                    Triple(stringResource(R.string.vip_order_refresh), !phase.querying, viewModel::refreshOrder)
                }
            PaymentOrderStatus.FULFILLED -> Triple(stringResource(R.string.vip_order_done), true, onBack)
            else -> Triple(stringResource(R.string.vip_order_refresh), !phase.querying, viewModel::refreshOrder)
        }
        phase is PurchasePhase.Resuming -> Triple(stringResource(R.string.vip_order_resuming), false) {}
        phase == PurchasePhase.Creating -> Triple(stringResource(R.string.vip_purchase_creating), false) {}
        state.lifetimeOwned -> Triple(stringResource(R.string.vip_purchase_lifetime_owned), false) {}
        phase is PurchasePhase.CreateFailed ->
            Triple(stringResource(R.string.vip_purchase_retry), state.selectedPlan != null, viewModel::requestPurchase)
        else -> {
            val plan = state.selectedPlan
            Triple(
                plan?.let { stringResource(R.string.vip_purchase_action_server, it.priceDisplay) }
                    ?: stringResource(R.string.vip_purchase_action_disabled),
                plan != null,
                viewModel::requestPurchase,
            )
        }
    }
    Button(
        onClick = action,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = AppDimensions.Button).testTag("vip_purchase_button")
            .then(if (dark) Modifier.vaultGradientAction(enabled = enabled) else Modifier),
        shape = AppShapes.Full,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (dark) Color.Transparent else colors.primary,
            contentColor = if (dark) colors.onSurface else colors.onPrimary,
            disabledContainerColor = if (dark) Color.Transparent else colors.onSurface.copy(alpha = 0.12f),
            disabledContentColor = colors.onSurfaceVariant,
        ),
    ) {
        Text(label, style = AppTextStyles.Button, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlansSection(state: VipPurchaseState, viewModel: VipPurchaseViewModel, room: Dp) {
    val plans = (state.plans as? PlansState.Ready)?.plans.orEmpty()
    PlanSelector(
        plans = plans,
        selected = state.selectedPlanCode,
        enabled = !state.lifetimeOwned,
        room = room,
        onSelected = viewModel::selectPlan,
    )
}

@Composable
private fun PayChannelSection(state: VipPurchaseState, viewModel: VipPurchaseViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup().testTag("vip_pay_channel_group"),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        PayChannelChip(
            label = stringResource(R.string.vip_payment_wechat),
            iconRes = R.drawable.ic_payment_wechat,
            selected = state.selectedPayChannel == com.aurora.calculatorvault.feature.membership.domain.VipPayChannel.WECHAT_PAY,
            enabled = !state.lifetimeOwned,
            testTag = "vip_pay_wechat",
            onClick = {
                viewModel.selectPayChannel(
                    com.aurora.calculatorvault.feature.membership.domain.VipPayChannel.WECHAT_PAY,
                )
            },
            modifier = Modifier.weight(1f),
        )
        PayChannelChip(
            label = stringResource(R.string.vip_payment_alipay),
            iconRes = R.drawable.ic_payment_alipay,
            selected = state.selectedPayChannel == com.aurora.calculatorvault.feature.membership.domain.VipPayChannel.ALIPAY,
            enabled = !state.lifetimeOwned,
            testTag = "vip_pay_alipay",
            onClick = {
                viewModel.selectPayChannel(
                    com.aurora.calculatorvault.feature.membership.domain.VipPayChannel.ALIPAY,
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PayChannelChip(
    label: String,
    @DrawableRes iconRes: Int,
    selected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = vipPurchaseColors()
    Row(
        modifier = modifier
            .heightIn(min = AppDimensions.Button)
            .vipPanel(emphasized = selected, shape = AppShapes.Large)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .testTag(testTag)
            .padding(AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Unspecified)
        Text(
            label,
            style = AppTextStyles.BodySecondary.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OrderPanel(phase: PurchasePhase.Ordering, viewModel: VipPurchaseViewModel) {
    val order = phase.order
    val colors = vipPurchaseColors()
    Box(
        modifier = Modifier.fillMaxWidth().testTag("vip_order_panel")
            .vipPanel(shape = AppShapes.Large),
    ) {
        Column(Modifier.padding(AppSpacing.sm), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(stringResource(R.string.vip_order_title), style = AppTextStyles.SectionTitle, color = colors.onSurface)
            Text(stringResource(R.string.vip_order_plan, order.planName), style = AppTextStyles.Body, color = colors.onSurface)
            Text(stringResource(R.string.vip_order_amount, order.amountDisplay), style = AppTextStyles.Body,
                color = colors.primary)
            Text(stringResource(R.string.vip_order_no, shortOrderNo(order.orderNo)), style = AppTextStyles.Caption,
                color = colors.onSurfaceVariant)
            Text(
                statusText(order.status),
                style = AppTextStyles.BodySecondary.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                modifier = Modifier.testTag("vip_order_status"),
            )
            if (order.status == PaymentOrderStatus.PENDING_PAYMENT) RemainingTime(order)
            when (order.payDecision) {
                PayLaunchDecision.TestPayment -> if (order.status == PaymentOrderStatus.PENDING_PAYMENT) {
                    Text(stringResource(R.string.vip_order_test_hint), style = AppTextStyles.Caption,
                        color = AppColors.Warning, modifier = Modifier.testTag("vip_order_test_hint"))
                }
                is PayLaunchDecision.Rejected -> if (order.status == PaymentOrderStatus.PENDING_PAYMENT) {
                    Text(stringResource(R.string.vip_order_link_invalid), style = AppTextStyles.Caption,
                        color = AppColors.Warning, modifier = Modifier.testTag("vip_order_link_invalid"))
                }
                is PayLaunchDecision.OpenCashier -> Unit
            }
            when {
                phase.querying -> Text(stringResource(R.string.vip_order_querying), style = AppTextStyles.Caption,
                    color = colors.onSurfaceVariant, modifier = Modifier.testTag("vip_order_querying"))
                phase.queryIssue == OrderQueryIssue.NetworkUnavailable ->
                    Text(stringResource(R.string.vip_order_network_unavailable), style = AppTextStyles.Caption,
                        color = AppColors.Warning, modifier = Modifier.testTag("vip_order_network_unavailable"))
                phase.queryIssue == OrderQueryIssue.Temporary ->
                    Text(stringResource(R.string.vip_order_query_failed), style = AppTextStyles.Caption,
                        color = colors.onSurfaceVariant, modifier = Modifier.testTag("vip_order_query_failed"))
            }
            if (order.status == PaymentOrderStatus.EXPIRED) {
                Text(stringResource(R.string.vip_order_expired_end_hint), style = AppTextStyles.Caption,
                    color = colors.onSurfaceVariant)
            }
            if (order.status == PaymentOrderStatus.FULFILLED) {
                when (phase.membershipSync) {
                    MembershipSyncAfterPurchase.Synced ->
                        Text(stringResource(R.string.vip_membership_synced), style = AppTextStyles.Body,
                            color = colors.primary, modifier = Modifier.testTag("vip_membership_synced"))
                    MembershipSyncAfterPurchase.Failed -> {
                        Text(stringResource(R.string.vip_membership_sync_failed), style = AppTextStyles.Caption,
                            color = colors.onSurfaceVariant)
                        TextButton(onClick = viewModel::retryMembershipSync, modifier = Modifier.testTag("vip_membership_retry")) {
                            Text(stringResource(R.string.vip_membership_sync_retry), color = colors.primary)
                        }
                    }
                    else -> Text(stringResource(R.string.vip_membership_syncing), style = AppTextStyles.Caption,
                        color = colors.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                if (order.status != PaymentOrderStatus.FULFILLED) {
                    TextButton(onClick = viewModel::refreshOrder, enabled = !phase.querying,
                        modifier = Modifier.testTag("vip_order_refresh")) {
                        Text(stringResource(R.string.vip_order_refresh), color = colors.primary)
                    }
                }
                TextButton(onClick = viewModel::closeOrderView, modifier = Modifier.testTag("vip_order_back")) {
                    Text(
                        stringResource(
                            if (order.status == PaymentOrderStatus.EXPIRED) R.string.vip_order_end_viewing
                            else R.string.vip_order_back_to_plans,
                        ),
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RemainingTime(order: OrderView) {
    val deadline = order.expiresAtElapsedMillis ?: return
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(deadline) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val remaining = deadline - now
    Text(
        if (remaining > 0) {
            val seconds = remaining / 1_000
            stringResource(R.string.vip_order_remaining, "%02d:%02d".format(seconds / 60, seconds % 60))
        } else {
            stringResource(R.string.vip_order_deadline_passed)
        },
        style = AppTextStyles.Caption,
        color = vipPurchaseColors().onSurfaceVariant,
        modifier = Modifier.testTag("vip_order_remaining"),
    )
}

@Composable
private fun statusText(status: PaymentOrderStatus): String = stringResource(
    when (status) {
        PaymentOrderStatus.PENDING_PAYMENT -> R.string.vip_order_status_pending
        PaymentOrderStatus.PAID -> R.string.vip_order_status_paid
        PaymentOrderStatus.FULFILLED -> R.string.vip_order_status_fulfilled
        PaymentOrderStatus.EXPIRED -> R.string.vip_order_status_expired
        PaymentOrderStatus.UNKNOWN -> R.string.vip_order_status_unknown
    },
)

private fun shortOrderNo(orderNo: String): String =
    if (orderNo.length <= 12) orderNo else "…" + orderNo.takeLast(10)

@Composable
private fun InfoCard(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().vipPanel(shape = AppShapes.Medium).padding(AppSpacing.sm),
        style = AppTextStyles.BodySecondary.copy(lineBreak = LineBreak.Paragraph),
        color = vipPurchaseColors().onSurfaceVariant,
    )
}

@Composable
private fun RetryCard(text: String, onRetry: () -> Unit, tag: String) {
    val colors = vipPurchaseColors()
    Row(
        modifier = Modifier.fillMaxWidth().testTag(tag)
            .vipPanel(shape = AppShapes.Medium).padding(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f), style = AppTextStyles.BodySecondary, color = colors.onSurfaceVariant)
        TextButton(onClick = onRetry, modifier = Modifier.testTag("vip_plans_retry")) {
            Text(stringResource(R.string.vip_plans_retry), color = colors.primary)
        }
    }
}

private class NoticeTexts(val values: Map<PurchaseNotice, String>)

@Composable
private fun noticeTexts(): NoticeTexts = NoticeTexts(
    mapOf(
        PurchaseNotice.AgreementRequired to stringResource(R.string.vip_agreement_required),
        PurchaseNotice.PlansNotReady to stringResource(R.string.vip_notice_plans_not_ready),
        PurchaseNotice.LoginRequired to stringResource(R.string.vip_notice_login_required),
        PurchaseNotice.LoginExpired to stringResource(R.string.vip_notice_login_expired),
        PurchaseNotice.LoginError to stringResource(R.string.vip_notice_login_error),
        PurchaseNotice.SessionVerifying to stringResource(R.string.vip_notice_session_verifying),
        PurchaseNotice.SessionUnavailable to stringResource(R.string.vip_notice_session_unavailable),
        PurchaseNotice.LifetimeOwned to stringResource(R.string.vip_lifetime_owned_banner),
        PurchaseNotice.PlanUnavailable to stringResource(R.string.vip_notice_plan_unavailable),
        PurchaseNotice.PlanMismatch to stringResource(R.string.vip_notice_plan_mismatch),
        PurchaseNotice.PayChannelInvalid to stringResource(R.string.vip_notice_pay_channel_invalid),
        PurchaseNotice.RequestConflict to stringResource(R.string.vip_notice_request_conflict),
        PurchaseNotice.PaymentConfigMissing to stringResource(R.string.vip_notice_config_missing),
        PurchaseNotice.PayLinkInvalid to stringResource(R.string.vip_order_link_invalid),
        PurchaseNotice.AmountMismatch to stringResource(R.string.vip_notice_amount_mismatch),
        PurchaseNotice.AccountChanged to stringResource(R.string.vip_notice_account_changed),
        PurchaseNotice.OrderNotFound to stringResource(R.string.vip_notice_order_not_found),
        PurchaseNotice.PurchaseCompleted to stringResource(R.string.vip_notice_purchase_completed),
        PurchaseNotice.MembershipUnconfirmed to stringResource(R.string.vip_notice_membership_unconfirmed),
        PurchaseNotice.AdminPurchaseBlocked to stringResource(R.string.vip_notice_admin_blocked),
    ),
)

private fun showNotice(messages: AppMessageController, notice: PurchaseNotice, texts: NoticeTexts) {
    val text = texts.values[notice] ?: return
    when (notice) {
        PurchaseNotice.PurchaseCompleted -> messages.showSuccess(text)
        PurchaseNotice.LoginRequired, PurchaseNotice.SessionVerifying, PurchaseNotice.LifetimeOwned,
        PurchaseNotice.PlansNotReady,
        -> messages.showInfo(text)
        else -> messages.showWarning(text)
    }
}

@Composable
private fun BenefitsCard(room: Dp) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("vip_benefits_card")
            .vipPanel(shape = AppShapes.Large)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.sm + room * 0.06f),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs + room * 0.04f, Alignment.CenterVertically),
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

@Composable
private fun Benefit(icon: ImageVector, title: String, description: String, modifier: Modifier = Modifier, inline: Boolean = false) {
    val colors = vipPurchaseColors()
    val titleStyle = AppTextStyles.BodySecondary.copy(
        fontSize = AppTextStyles.CardTitle.fontSize,
        fontWeight = FontWeight.SemiBold,
    )
    val descriptionStyle = AppTextStyles.Caption.copy(
        fontSize = AppTextStyles.BodySecondary.fontSize,
        lineBreak = LineBreak.Paragraph,
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        VipIconTile(icon, size = 24.dp)
        if (inline) {
            Text(
                buildAnnotatedString {
                    withStyle(titleStyle.toSpanStyle().copy(color = colors.onSurface)) { append(title) }
                    append("  ")
                    append(description)
                },
                modifier = Modifier.weight(1f),
                style = descriptionStyle,
                color = colors.onSurfaceVariant,
            )
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                Text(title, style = titleStyle, color = colors.onSurface)
                Text(description, style = descriptionStyle, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlanSelector(
    plans: List<MembershipPlanOffer>,
    selected: String?,
    enabled: Boolean,
    room: Dp,
    onSelected: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val colors = vipPurchaseColors()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Keep the cards on one row; allow scrolling instead of shrinking large text.
        val count = plans.size.coerceAtLeast(1)
        val cardWidth = maxOf((maxWidth - AppSpacing.xs * (count - 1)) / count,
            (104 * density.fontScale.coerceAtLeast(1f)).dp)
        val selectedIndex = plans.indexOfFirst { it.planCode == selected }.coerceAtLeast(0)
        LaunchedEffect(selectedIndex, cardWidth) {
            val offset = with(density) { ((cardWidth + AppSpacing.xs) * selectedIndex).roundToPx() }
            scroll.animateScrollTo(offset)
        }
        Row(
            modifier = Modifier.horizontalScroll(scroll).height(IntrinsicSize.Max).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            plans.forEach { plan ->
                val isSelected = plan.planCode == selected
                Box(Modifier.width(cardWidth).fillMaxHeight()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.sm)
                            .heightIn(min = 140.dp + room * 0.18f).fillMaxHeight()
                            .vipPanel(emphasized = isSelected, shape = AppShapes.Medium)
                            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton,
                                onClick = { onSelected(plan.planCode) })
                            .testTag("vip_plan_${plan.planCode}")
                            .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.xs + room * 0.0375f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs + room * 0.025f, Alignment.CenterVertically),
                    ) {
                        VipIconTile(
                            icon = if (plan.isLifetime) VaultIcons.Security else VaultIcons.Calendar,
                            emphasized = isSelected,
                            size = 28.dp,
                        )
                        Text(
                            plan.displayName,
                            style = AppTextStyles.BodySecondary.copy(
                                fontSize = AppTextStyles.CardTitle.fontSize,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.vip_price, plan.priceDisplay),
                            style = AppTextStyles.PageTitle.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                            color = if (isSelected) colors.primary else colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            plan.durationDays?.takeIf { !plan.isLifetime }
                                ?.let { stringResource(R.string.vip_duration_days, it) }
                                ?: stringResource(R.string.vip_duration_lifetime),
                            style = AppTextStyles.Caption.copy(fontSize = AppTextStyles.BodySecondary.fontSize),
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (plan.planCode == RECOMMENDED_PLAN) {
                        val badgeShape = AppShapes.Full
                        Text(
                            stringResource(R.string.vip_plan_recommended),
                            modifier = Modifier.align(Alignment.TopEnd)
                                .offset(x = -AppSpacing.xs)
                                .shadow(
                                    elevation = 3.dp,
                                    shape = badgeShape,
                                    clip = false,
                                    ambientColor = Color(0x4DFFB84D),
                                    spotColor = Color(0x80702B12),
                                )
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFFFFB84D), Color(0xFFFF8A3D))),
                                    badgeShape,
                                )
                                .border(0.5.dp, Color.White.copy(alpha = 0.32f), badgeShape)
                                .padding(horizontal = AppSpacing.sm, vertical = 3.dp),
                            style = AppTextStyles.Caption.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                shadow = Shadow(Color(0x80582512), Offset(0f, 0.8f), blurRadius = 1.5f),
                            ),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

private const val RECOMMENDED_PLAN = "YEARLY"

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
    val dark = useVaultDarkVisuals()
    val colors = vipPurchaseColors()
    val linkStyles = TextLinkStyles(SpanStyle(
        color = colors.primary, textDecoration = TextDecoration.Underline))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = accepted,
            onCheckedChange = onChanged,
            modifier = Modifier.testTag("vip_agreement").semantics {
                contentDescription = prefix + service + and + privacy
            },
            colors = CheckboxDefaults.colors(
                checkedColor = if (dark) VaultMainColors.Accent else colors.primary,
                uncheckedColor = colors.onSurfaceVariant,
                checkmarkColor = if (dark) colors.background else colors.onPrimary,
            ),
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
            color = colors.onSurfaceVariant,
        )
    }
}
