package com.aurora.calculatorvault.feature.membership.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
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
import com.aurora.calculatorvault.ui.component.VaultSectionTitle
import com.aurora.calculatorvault.ui.message.AppMessageController
import com.aurora.calculatorvault.ui.message.LocalAppMessageController
import kotlinx.coroutines.delay

@Composable
fun VipPurchaseScreen(
    viewModel: VipPurchaseViewModel,
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onRequestLogin: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val messages = LocalAppMessageController.current
    val context = LocalContext.current
    val servicePending = stringResource(R.string.vip_service_pending)
    val noticeText = noticeTexts()
    val browserUnavailable = stringResource(R.string.vip_notice_browser_unavailable)

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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val footerMaxHeight = maxOf(maxHeight * 0.5f, AppDimensions.Button + AppSpacing.md)
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("vip_purchase_page"),
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
                Surface(modifier = Modifier.testTag("vip_purchase_footer"), color = AppColors.SurfacePrimary) {
                    Column {
                        HorizontalDivider(color = AppColors.BorderSubtle)
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
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.md)
                    .padding(top = AppSpacing.xxs, bottom = AppSpacing.xxs)
                    .testTag("vip_purchase_content"),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                    VaultSectionTitle(stringResource(R.string.vip_benefits_title))
                    BenefitsCard()
                }
                if (state.lifetimeOwned) {
                    InfoCard(stringResource(R.string.vip_lifetime_owned_banner), Modifier.testTag("vip_lifetime_owned"))
                }
                when (val phase = state.phase) {
                    is PurchasePhase.Ordering -> OrderPanel(phase, viewModel)
                    is PurchasePhase.Resuming -> InfoCard(stringResource(R.string.vip_order_resuming))
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                            VaultSectionTitle(stringResource(R.string.vip_plans_title))
                            PlansSection(state, viewModel)
                        }
                        if (phase is PurchasePhase.CreateFailed) {
                            InfoCard(stringResource(R.string.vip_create_failed), Modifier.testTag("vip_create_failed"))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                            VaultSectionTitle(stringResource(R.string.vip_cashier_title))
                            InfoCard(stringResource(R.string.vip_cashier_description))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FooterAction(state: VipPurchaseState, viewModel: VipPurchaseViewModel, onBack: () -> Unit) {
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
        modifier = Modifier.fillMaxWidth().heightIn(min = AppDimensions.Button).testTag("vip_purchase_button"),
        shape = AppShapes.Full,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.AccentPrimary,
            contentColor = AppColors.BackgroundPrimary,
            disabledContainerColor = AppColors.AccentContainer,
            disabledContentColor = AppColors.TextSecondary,
        ),
    ) {
        Text(label, style = AppTextStyles.Button, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlansSection(state: VipPurchaseState, viewModel: VipPurchaseViewModel) {
    when (val plans = state.plans) {
        PlansState.Loading -> InfoCard(stringResource(R.string.vip_plans_loading), Modifier.testTag("vip_plans_loading"))
        PlansState.Empty -> RetryCard(stringResource(R.string.vip_plans_empty), viewModel::loadPlans, "vip_plans_empty")
        is PlansState.Failed ->
            if (plans.error == PaymentError.EndpointNotConfigured) {
                InfoCard(stringResource(R.string.vip_plans_endpoint_missing), Modifier.testTag("vip_plans_endpoint_missing"))
            } else {
                RetryCard(stringResource(R.string.vip_plans_failed), viewModel::loadPlans, "vip_plans_failed")
            }
        is PlansState.Ready -> PlanSelector(
            plans = plans.plans,
            selected = state.selectedPlanCode,
            enabled = !state.lifetimeOwned,
            onSelected = viewModel::selectPlan,
        )
    }
}

@Composable
private fun OrderPanel(phase: PurchasePhase.Ordering, viewModel: VipPurchaseViewModel) {
    val order = phase.order
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("vip_order_panel"),
        shape = AppShapes.Large,
        color = AppColors.SurfacePrimary,
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
    ) {
        Column(Modifier.padding(AppSpacing.sm), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(stringResource(R.string.vip_order_title), style = AppTextStyles.SectionTitle, color = AppColors.TextPrimary)
            Text(stringResource(R.string.vip_order_plan, order.planName), style = AppTextStyles.Body, color = AppColors.TextPrimary)
            Text(stringResource(R.string.vip_order_amount, order.amountDisplay), style = AppTextStyles.Body,
                color = AppColors.AccentPrimary)
            Text(stringResource(R.string.vip_order_no, shortOrderNo(order.orderNo)), style = AppTextStyles.Caption,
                color = AppColors.TextSecondary)
            Text(
                statusText(order.status),
                style = AppTextStyles.BodySecondary.copy(fontWeight = FontWeight.SemiBold),
                color = AppColors.TextPrimary,
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
                    color = AppColors.TextSecondary, modifier = Modifier.testTag("vip_order_querying"))
                phase.queryIssue == OrderQueryIssue.NetworkUnavailable ->
                    Text(stringResource(R.string.vip_order_network_unavailable), style = AppTextStyles.Caption,
                        color = AppColors.Warning, modifier = Modifier.testTag("vip_order_network_unavailable"))
                phase.queryIssue == OrderQueryIssue.Temporary ->
                    Text(stringResource(R.string.vip_order_query_failed), style = AppTextStyles.Caption,
                        color = AppColors.TextSecondary, modifier = Modifier.testTag("vip_order_query_failed"))
            }
            if (order.status == PaymentOrderStatus.EXPIRED) {
                Text(stringResource(R.string.vip_order_expired_end_hint), style = AppTextStyles.Caption,
                    color = AppColors.TextSecondary)
            }
            if (order.status == PaymentOrderStatus.FULFILLED) {
                when (phase.membershipSync) {
                    MembershipSyncAfterPurchase.Synced ->
                        Text(stringResource(R.string.vip_membership_synced), style = AppTextStyles.Body,
                            color = AppColors.AccentPrimary, modifier = Modifier.testTag("vip_membership_synced"))
                    MembershipSyncAfterPurchase.Failed -> {
                        Text(stringResource(R.string.vip_membership_sync_failed), style = AppTextStyles.Caption,
                            color = AppColors.TextSecondary)
                        TextButton(onClick = viewModel::retryMembershipSync, modifier = Modifier.testTag("vip_membership_retry")) {
                            Text(stringResource(R.string.vip_membership_sync_retry), color = AppColors.AccentPrimary)
                        }
                    }
                    else -> Text(stringResource(R.string.vip_membership_syncing), style = AppTextStyles.Caption,
                        color = AppColors.TextSecondary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                if (order.status != PaymentOrderStatus.FULFILLED) {
                    TextButton(onClick = viewModel::refreshOrder, enabled = !phase.querying,
                        modifier = Modifier.testTag("vip_order_refresh")) {
                        Text(stringResource(R.string.vip_order_refresh), color = AppColors.AccentPrimary)
                    }
                }
                TextButton(onClick = viewModel::closeOrderView, modifier = Modifier.testTag("vip_order_back")) {
                    Text(
                        stringResource(
                            if (order.status == PaymentOrderStatus.EXPIRED) R.string.vip_order_end_viewing
                            else R.string.vip_order_back_to_plans,
                        ),
                        color = AppColors.TextSecondary,
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
        color = AppColors.TextSecondary,
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.Medium,
        color = AppColors.SurfacePrimary,
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
    ) {
        Text(text, modifier = Modifier.padding(AppSpacing.sm),
            style = AppTextStyles.BodySecondary.copy(lineBreak = LineBreak.Paragraph), color = AppColors.TextSecondary)
    }
}

@Composable
private fun RetryCard(text: String, onRetry: () -> Unit, tag: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = AppShapes.Medium,
        color = AppColors.SurfacePrimary,
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
    ) {
        Row(Modifier.padding(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f), style = AppTextStyles.BodySecondary, color = AppColors.TextSecondary)
            TextButton(onClick = onRetry, modifier = Modifier.testTag("vip_plans_retry")) {
                Text(stringResource(R.string.vip_plans_retry), color = AppColors.AccentPrimary)
            }
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
        PurchaseNotice.RequestConflict to stringResource(R.string.vip_notice_request_conflict),
        PurchaseNotice.PaymentConfigMissing to stringResource(R.string.vip_notice_config_missing),
        PurchaseNotice.PayLinkInvalid to stringResource(R.string.vip_order_link_invalid),
        PurchaseNotice.AccountChanged to stringResource(R.string.vip_notice_account_changed),
        PurchaseNotice.OrderNotFound to stringResource(R.string.vip_notice_order_not_found),
        PurchaseNotice.PurchaseCompleted to stringResource(R.string.vip_notice_purchase_completed),
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
private fun PlanSelector(
    plans: List<MembershipPlanOffer>,
    selected: String?,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
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
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.sm)
                            .heightIn(min = 140.dp).fillMaxHeight()
                            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton,
                                onClick = { onSelected(plan.planCode) })
                            .testTag("vip_plan_${plan.planCode}"),
                        shape = AppShapes.Medium,
                        color = if (isSelected) AppColors.AccentContainer else AppColors.SurfacePrimary,
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp,
                            if (isSelected) AppColors.AccentPrimary else AppColors.BorderSubtle),
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.xs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs, Alignment.CenterVertically),
                        ) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Icon(
                                    if (plan.isLifetime) VaultIcons.Security else VaultIcons.Calendar,
                                    null, Modifier.size(20.dp), tint = AppColors.AccentPrimary,
                                )
                            }
                            Text(plan.displayName, style = AppTextStyles.BodySecondary, color = AppColors.TextPrimary,
                                textAlign = TextAlign.Center)
                            Text(stringResource(R.string.vip_price, plan.priceDisplay),
                                style = AppTextStyles.PageTitle, color = AppColors.AccentPrimary,
                                textAlign = TextAlign.Center)
                            Text(plan.durationDays?.takeIf { !plan.isLifetime }
                                ?.let { stringResource(R.string.vip_duration_days, it) }
                                ?: stringResource(R.string.vip_duration_lifetime),
                                style = AppTextStyles.Caption, color = AppColors.TextSecondary,
                                textAlign = TextAlign.Center)
                        }
                    }
                    if (plan.planCode == RECOMMENDED_PLAN) {
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
