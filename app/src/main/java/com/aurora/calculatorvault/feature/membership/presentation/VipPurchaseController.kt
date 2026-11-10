package com.aurora.calculatorvault.feature.membership.presentation

import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementSource
import com.aurora.calculatorvault.feature.entitlement.domain.EntitlementState
import com.aurora.calculatorvault.feature.login.session.AccountSession
import com.aurora.calculatorvault.feature.login.session.entitlementAccount
import com.aurora.calculatorvault.feature.membership.data.MembershipSyncUi
import com.aurora.calculatorvault.feature.membership.domain.MembershipSyncAfterPurchase
import com.aurora.calculatorvault.feature.membership.domain.OrderQueryIssue
import com.aurora.calculatorvault.feature.membership.domain.OrderView
import com.aurora.calculatorvault.feature.membership.domain.PlansState
import com.aurora.calculatorvault.feature.membership.domain.PurchaseNotice
import com.aurora.calculatorvault.feature.membership.domain.PurchasePhase
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseSource
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseState
import com.aurora.calculatorvault.feature.payment.data.PaymentError
import com.aurora.calculatorvault.feature.payment.data.PaymentOrder
import com.aurora.calculatorvault.feature.payment.data.PaymentOrderStatus
import com.aurora.calculatorvault.feature.payment.data.PaymentRepository
import com.aurora.calculatorvault.feature.payment.data.PaymentResult
import com.aurora.calculatorvault.feature.payment.data.PurchaseOwner
import com.aurora.calculatorvault.feature.payment.domain.PayLaunchDecision
import com.aurora.calculatorvault.feature.payment.domain.PayLaunchPolicy
import com.aurora.calculatorvault.feature.payment.domain.PaymentPollPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Membership side effects after FULFILLED, backed by the existing refresh coordinator. */
interface PurchaseMembershipPort {
    val revision: StateFlow<Long>
    val syncState: StateFlow<MembershipSyncUi>

    /** Request a /membership/me refresh now (not throttled). */
    fun requestRefreshAfterPurchase(): Boolean

    /** Server-confirmed VIP for [userId] in the current session generation. */
    fun hasVerifiedVip(userId: Long): Boolean
}

sealed interface VipPurchaseEvent {
    data class Notice(val notice: PurchaseNotice) : VipPurchaseEvent
    data object OpenLogin : VipPurchaseEvent

    /** [url] is a signed cashier link: hand it to the browser only, never log it. */
    data class OpenCashier(val url: String) : VipPurchaseEvent {
        override fun toString(): String = "OpenCashier(url=[REDACTED])"
    }
}

/**
 * Page-scoped purchase state machine. Lives in the purchase ViewModel's scope, so polling stops
 * when the page is closed; the pending order survives through [PaymentRepository] metadata.
 *
 * Payment success is only ever taken from the server order status; VIP only from the existing
 * membership refresh -> MembershipProvider -> EntitlementRepository path.
 */
class VipPurchaseController(
    source: VipPurchaseSource,
    private val scope: CoroutineScope,
    private val repository: PaymentRepository,
    private val sessionState: StateFlow<AccountSession>,
    entitlementState: StateFlow<EntitlementState>,
    private val membership: PurchaseMembershipPort,
    private val payLaunchPolicy: PayLaunchPolicy,
    private val elapsedRealtime: () -> Long,
    private val membershipSyncTimeoutMillis: Long = 20_000L,
) {
    private val mutableState = MutableStateFlow(VipPurchaseState(purchaseSource = source))
    val state: StateFlow<VipPurchaseState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<VipPurchaseEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<VipPurchaseEvent> = mutableEvents.asSharedFlow()

    private var owner: PurchaseOwner? = null
    private var plansJob: Job? = null
    private var pollJob: Job? = null

    /** True while an order-status HTTP request is running; repeated triggers reuse it. */
    private var queryInFlight = false
    private var offlineSkips = 0
    private var lastCashierLaunchAt: Long? = null
    private var syncJob: Job? = null
    private var queryEpoch = 0L
    private var visible = true
    private var pollSessionStart = 0L
    private var consecutiveFailures = 0
    private val completedOrders = mutableSetOf<String>()

    init {
        scope.launch {
            combine(sessionState, entitlementState) { session, entitlement ->
                val id = session.entitlementAccount?.userId
                val vip = entitlement.source as? EntitlementSource.Vip
                vip != null && vip.permanent && vip.accountId == id
            }.collect { owned -> mutableState.update { it.copy(lifetimeOwned = owned) } }
        }
        scope.launch { sessionState.collect(::onSessionChanged) }
        scope.launch {
            repository.networkRestored.collect { requestImmediateQuery() }
        }
        scope.launch {
            repository.networkAvailable.collect { available ->
                // Immediate feedback when the device loses its network, even mid-request.
                if (!available) updateOrdering { it.copy(queryIssue = OrderQueryIssue.NetworkUnavailable) }
            }
        }
        loadPlans()
    }

    // ---------- plans ----------

    fun loadPlans() {
        plansJob?.cancel()
        mutableState.update { it.copy(plans = PlansState.Loading) }
        plansJob = scope.launch {
            val next = when (val result = repository.loadPlans()) {
                is PaymentResult.Success ->
                    if (result.value.isEmpty()) PlansState.Empty else PlansState.Ready(result.value)
                is PaymentResult.Failure -> PlansState.Failed(result.error)
            }
            mutableState.update { current ->
                val plans = (next as? PlansState.Ready)?.plans.orEmpty()
                val keep = current.selectedPlanCode?.takeIf { code -> plans.any { it.planCode == code } }
                val default = plans.firstOrNull { it.planCode == DEFAULT_PLAN }?.planCode ?: plans.firstOrNull()?.planCode
                current.copy(plans = next, selectedPlanCode = keep ?: default)
            }
        }
    }

    fun selectPlan(planCode: String) {
        val current = state.value
        if (current.phase is PurchasePhase.Creating || current.phase is PurchasePhase.Ordering ||
            current.phase is PurchasePhase.Resuming
        ) {
            return
        }
        if (current.selectedPlanCode == planCode) return
        if (current.phase is PurchasePhase.CreateFailed) {
            // A different plan is a new purchase: drop the unconfirmed request context.
            owner?.let { repository.discardPending(it.userId) }
        }
        mutableState.update { it.copy(selectedPlanCode = planCode, phase = PurchasePhase.Idle) }
    }

    fun setAgreementAccepted(accepted: Boolean) {
        mutableState.update { it.copy(agreementAccepted = accepted) }
    }

    // ---------- create ----------

    /** Main purchase button. Retries the same request when the last create was not confirmed. */
    fun requestPurchase() {
        val current = state.value
        if (current.phase is PurchasePhase.Creating || current.phase is PurchasePhase.Ordering ||
            current.phase is PurchasePhase.Resuming
        ) {
            return
        }
        if (!current.agreementAccepted) return notice(PurchaseNotice.AgreementRequired)
        if (current.lifetimeOwned) return notice(PurchaseNotice.LifetimeOwned)
        val plan = current.selectedPlan ?: return notice(PurchaseNotice.PlansNotReady)
        if (!sessionAllowsCreate()) return
        val createOwner = repository.currentOwner(requireAuthenticated = true)
            ?: return notice(PurchaseNotice.SessionVerifying)
        val failed = current.phase as? PurchasePhase.CreateFailed
        val retrySame = failed != null && failed.retryKept && owner == createOwner
        mutableState.update { it.copy(phase = PurchasePhase.Creating) }
        owner = createOwner
        scope.launch { handleCreate(createOwner, plan.planCode, retrySame) }
    }

    private suspend fun handleCreate(createOwner: PurchaseOwner, planCode: String, retrySame: Boolean) {
        when (val outcome = repository.createOrder(createOwner, planCode, retrySame)) {
            is PaymentRepository.CreateOutcome.Created -> {
                if (owner != createOwner) return
                mutableState.update { it.copy(phase = PurchasePhase.Ordering(viewOf(outcome.order))) }
                consecutiveFailures = 0
                pollSessionStart = elapsedRealtime()
                // Query once immediately after creation, then follow the poll policy.
                restartPolling()
            }
            is PaymentRepository.CreateOutcome.Failed -> onCreateFailed(outcome.error, outcome.retryKept)
            PaymentRepository.CreateOutcome.AccountChanged -> abandonForAccountChange()
            PaymentRepository.CreateOutcome.Busy -> Unit
        }
    }

    private fun onCreateFailed(error: PaymentError, retryKept: Boolean) {
        if (retryKept) {
            mutableState.update { it.copy(phase = PurchasePhase.CreateFailed(error, retryKept = true)) }
            if (error == PaymentError.PaymentConfigMissing) notice(PurchaseNotice.PaymentConfigMissing)
            return
        }
        owner = null
        mutableState.update { it.copy(phase = PurchasePhase.Idle) }
        when (error) {
            PaymentError.Unauthorized -> {
                notice(PurchaseNotice.LoginExpired)
                mutableEvents.tryEmit(VipPurchaseEvent.OpenLogin)
            }
            PaymentError.ClientRequestConflict -> notice(PurchaseNotice.RequestConflict)
            PaymentError.LifetimeAlreadyActive -> {
                notice(PurchaseNotice.LifetimeOwned)
                membership.requestRefreshAfterPurchase()
            }
            PaymentError.PlanDisabled, PaymentError.PlanNotFound -> {
                notice(PurchaseNotice.PlanUnavailable)
                loadPlans()
            }
            PaymentError.EndpointNotConfigured -> notice(PurchaseNotice.PaymentConfigMissing)
            else -> Unit
        }
    }

    private fun sessionAllowsCreate(): Boolean = when (sessionState.value) {
        is AccountSession.Authenticated -> true
        AccountSession.Guest -> {
            notice(PurchaseNotice.LoginRequired)
            mutableEvents.tryEmit(VipPurchaseEvent.OpenLogin)
            false
        }
        AccountSession.SessionExpired -> {
            notice(PurchaseNotice.LoginExpired)
            mutableEvents.tryEmit(VipPurchaseEvent.OpenLogin)
            false
        }
        AccountSession.Authenticating, is AccountSession.Validating -> {
            notice(PurchaseNotice.SessionVerifying)
            false
        }
        is AccountSession.TemporarilyUnavailable -> {
            notice(PurchaseNotice.SessionUnavailable)
            false
        }
        AccountSession.LoginFailed, AccountSession.StorageFailed -> {
            notice(PurchaseNotice.LoginError)
            mutableEvents.tryEmit(VipPurchaseEvent.OpenLogin)
            false
        }
    }

    // ---------- waiting / polling ----------

    /** Repeated taps within [CASHIER_DEBOUNCE_MS] of a launch open the browser only once. */
    fun openCashier() {
        val ordering = state.value.phase as? PurchasePhase.Ordering ?: return
        if (ordering.order.status != PaymentOrderStatus.PENDING_PAYMENT) return
        when (val decision = ordering.order.payDecision) {
            is PayLaunchDecision.OpenCashier -> {
                val now = elapsedRealtime()
                val last = lastCashierLaunchAt
                if (last != null && now - last < CASHIER_DEBOUNCE_MS) return
                lastCashierLaunchAt = now
                mutableEvents.tryEmit(VipPurchaseEvent.OpenCashier(decision.url))
            }
            is PayLaunchDecision.Rejected -> notice(PurchaseNotice.PayLinkInvalid)
            PayLaunchDecision.TestPayment -> Unit
        }
    }

    /**
     * Manual "refresh payment status". If a query for the order is already running, that request
     * is reused (no cancel + re-send); otherwise one immediate query, then the poll policy.
     */
    fun refreshOrder() {
        if (activeOrderNo() == null || queryInFlight) return
        consecutiveFailures = 0
        offlineSkips = 0
        restartPolling()
    }

    /** Network restored / page resumed: query now unless a query is already in flight. */
    private fun requestImmediateQuery() {
        if (!visible || activeOrderNo() == null || queryInFlight) return
        consecutiveFailures = 0
        offlineSkips = 0
        restartPolling()
    }

    /** Page resumed / paused (also covers returning from the cashier browser). */
    fun onVisibilityChanged(isVisible: Boolean) {
        visible = isVisible
        if (!isVisible) {
            pollJob?.cancel()
            pollJob = null
            return
        }
        if (activeOrderNo() != null) {
            pollSessionStart = elapsedRealtime()
            requestImmediateQuery()
        }
    }

    fun retryMembershipSync() {
        val ordering = state.value.phase as? PurchasePhase.Ordering ?: return
        if (ordering.order.status != PaymentOrderStatus.FULFILLED) return
        owner?.let(::startMembershipSync)
    }

    /**
     * Leave the waiting view. For an EXPIRED order this ends viewing it: only the local recovery
     * record is dropped, so the page opens on plans next time. The server order is untouched and
     * a late payment still reaches the account through /membership/me. A PENDING order keeps its
     * record so it can be resumed.
     */
    fun closeOrderView() {
        val ordering = state.value.phase as? PurchasePhase.Ordering
        val viewOwner = owner
        if (ordering != null && viewOwner != null && ordering.order.status == PaymentOrderStatus.EXPIRED) {
            repository.discardOrderRecord(viewOwner.userId, ordering.order.orderNo)
        }
        cancelOrderWork()
        mutableState.update { it.copy(phase = PurchasePhase.Idle) }
    }

    private fun restartPolling() {
        pollJob?.cancel()
        if (!visible) return
        pollJob = scope.launch {
            while (true) {
                val status = queryOnce() ?: break
                val delayMs = PaymentPollPolicy.nextDelayMillis(
                    status,
                    elapsedRealtime() - pollSessionStart,
                    consecutiveFailures,
                ) ?: break
                delay(delayMs)
            }
        }
    }

    /** @return status to schedule from, or null when polling must stop. */
    private suspend fun queryOnce(): PaymentOrderStatus? {
        val queryOwner = owner ?: return null
        val orderNo = activeOrderNo() ?: return null
        if (!repository.networkAvailable.value && offlineSkips < MAX_OFFLINE_SKIPS) {
            // Known offline: report it now and back off without a doomed HTTP request. Every
            // MAX_OFFLINE_SKIPS ticks a real request still goes out in case the signal is stale.
            offlineSkips++
            consecutiveFailures++
            updateOrdering { it.copy(querying = false, queryIssue = OrderQueryIssue.NetworkUnavailable) }
            return currentKnownStatus()
        }
        offlineSkips = 0
        val epoch = ++queryEpoch
        updateOrdering { it.copy(querying = true) }
        queryInFlight = true
        val outcome = try {
            repository.queryOrder(queryOwner, orderNo)
        } finally {
            queryInFlight = false
        }
        if (epoch != queryEpoch || owner != queryOwner) return null
        return when (outcome) {
            is PaymentRepository.QueryOutcome.Updated -> {
                consecutiveFailures = 0
                val view = viewOf(outcome.order)
                mutableState.update { current ->
                    val previous = current.phase as? PurchasePhase.Ordering
                    current.copy(
                        phase = PurchasePhase.Ordering(
                            order = view,
                            membershipSync = previous?.membershipSync ?: MembershipSyncAfterPurchase.NotStarted,
                        ),
                    )
                }
                if (view.status == PaymentOrderStatus.FULFILLED) onFulfilled(queryOwner, orderNo)
                view.status
            }
            is PaymentRepository.QueryOutcome.Failed -> when (outcome.error) {
                PaymentError.OrderNotFound -> {
                    cancelOrderWork()
                    mutableState.update { it.copy(phase = PurchasePhase.Idle) }
                    notice(PurchaseNotice.OrderNotFound)
                    null
                }
                PaymentError.Unauthorized -> {
                    abandonForAccountChange()
                    notice(PurchaseNotice.LoginExpired)
                    mutableEvents.tryEmit(VipPurchaseEvent.OpenLogin)
                    null
                }
                else -> {
                    // A failed query never changes the known order status.
                    consecutiveFailures++
                    val issue = if (outcome.error is PaymentError.Network && !repository.networkAvailable.value) {
                        OrderQueryIssue.NetworkUnavailable
                    } else {
                        OrderQueryIssue.Temporary
                    }
                    updateOrdering { it.copy(querying = false, queryIssue = issue) }
                    currentKnownStatus()
                }
            }
            PaymentRepository.QueryOutcome.AccountChanged -> {
                abandonForAccountChange()
                null
            }
        }
    }

    private fun onFulfilled(fulfilledOwner: PurchaseOwner, orderNo: String) {
        if (!completedOrders.add(orderNo)) return
        notice(PurchaseNotice.PurchaseCompleted)
        startMembershipSync(fulfilledOwner)
    }

    /**
     * A FULFILLED refresh can join a same-account /me request that was already in flight and
     * read the server before the grant. So after each settled refresh without a server-confirmed
     * VIP, re-fetch a bounded number of times; VIP still comes only from the provider.
     */
    private fun startMembershipSync(syncOwner: PurchaseOwner) {
        syncJob?.cancel()
        updateOrdering { it.copy(membershipSync = MembershipSyncAfterPurchase.Syncing) }
        syncJob = scope.launch {
            var synced = false
            for (attempt in 0 until MEMBERSHIP_SYNC_ATTEMPTS) {
                if (attempt > 0) delay(MEMBERSHIP_SYNC_RETRY_DELAY_MS * attempt)
                if (owner != syncOwner) return@launch
                val before = membership.revision.value
                val accepted = membership.requestRefreshAfterPurchase()
                val settled = accepted && withTimeoutOrNull(membershipSyncTimeoutMillis) {
                    membership.revision.first { it > before }
                    membership.syncState.first { it !is MembershipSyncUi.Syncing }
                    true
                } == true
                if (owner != syncOwner) return@launch
                synced = settled && membership.syncState.value == MembershipSyncUi.Idle &&
                    membership.hasVerifiedVip(syncOwner.userId)
                if (synced) break
            }
            updateOrdering {
                it.copy(
                    membershipSync = if (synced) MembershipSyncAfterPurchase.Synced else MembershipSyncAfterPurchase.Failed,
                )
            }
        }
    }

    // ---------- session / recovery ----------

    private fun onSessionChanged(session: AccountSession) {
        val identity = session.entitlementAccount?.userId
        val current = owner
        if (current != null) {
            val stillSame = identity == current.userId &&
                repository.currentOwner(requireAuthenticated = false)?.generation.let { it == null || it == current.generation }
            if (!stillSame) {
                abandonForAccountChange()
                return
            }
        }
        if (session is AccountSession.Authenticated) tryResume()
    }

    /** Resume an order recorded for this account (Activity recreation / process restore). */
    private fun tryResume() {
        if (state.value.phase != PurchasePhase.Idle || owner != null) return
        val resumeOwner = repository.currentOwner(requireAuthenticated = true) ?: return
        val record = repository.pendingFor(resumeOwner.userId) ?: return
        owner = resumeOwner
        val orderNo = record.orderNo
        if (orderNo == null) {
            // The create was never confirmed: offer a retry with the same clientRequestId.
            mutableState.update {
                it.copy(
                    selectedPlanCode = record.planCode,
                    phase = PurchasePhase.CreateFailed(PaymentError.Network(timeout = false), retryKept = true),
                )
            }
            return
        }
        // Ownership is re-verified by the server (40403 for a foreign order).
        mutableState.update { it.copy(phase = PurchasePhase.Resuming(orderNo)) }
        pollSessionStart = elapsedRealtime()
        consecutiveFailures = 0
        restartPolling()
    }

    private fun abandonForAccountChange() {
        val hadWork = state.value.phase != PurchasePhase.Idle
        cancelOrderWork()
        if (hadWork) {
            mutableState.update { it.copy(phase = PurchasePhase.Idle) }
            notice(PurchaseNotice.AccountChanged)
        }
    }

    private fun cancelOrderWork() {
        lastCashierLaunchAt = null
        pollJob?.cancel()
        syncJob?.cancel()
        pollJob = null
        syncJob = null
        queryEpoch++
        owner = null
    }

    // ---------- helpers ----------

    private fun currentKnownStatus(): PaymentOrderStatus =
        (state.value.phase as? PurchasePhase.Ordering)?.order?.status ?: PaymentOrderStatus.UNKNOWN

    private fun activeOrderNo(): String? = when (val phase = state.value.phase) {
        is PurchasePhase.Ordering -> phase.order.orderNo
        is PurchasePhase.Resuming -> phase.orderNo
        else -> null
    }

    private fun updateOrdering(transform: (PurchasePhase.Ordering) -> PurchasePhase.Ordering) {
        mutableState.update { current ->
            val ordering = current.phase as? PurchasePhase.Ordering ?: return@update current
            current.copy(phase = transform(ordering))
        }
    }

    private fun viewOf(order: PaymentOrder): OrderView {
        val plan = (state.value.plans as? PlansState.Ready)?.plans?.firstOrNull { it.planCode == order.planCode }
        val expires = order.expiresAt
        val server = order.serverTime
        val deadline = if (expires != null && server != null) {
            elapsedRealtime() + (expires.toEpochMilli() - server.toEpochMilli())
        } else {
            null
        }
        return OrderView(
            orderNo = order.orderNo,
            planCode = order.planCode,
            planName = plan?.displayName ?: order.planCode,
            amountDisplay = order.amountDisplay,
            status = order.status,
            payDecision = payLaunchPolicy.decide(order.pay),
            expiresAtElapsedMillis = deadline,
        )
    }

    private fun notice(notice: PurchaseNotice) {
        mutableEvents.tryEmit(VipPurchaseEvent.Notice(notice))
    }

    private companion object {
        const val DEFAULT_PLAN = "YEARLY"
        const val MAX_OFFLINE_SKIPS = 3
        const val CASHIER_DEBOUNCE_MS = 2_000L
        const val MEMBERSHIP_SYNC_ATTEMPTS = 3
        const val MEMBERSHIP_SYNC_RETRY_DELAY_MS = 1_500L
    }
}
