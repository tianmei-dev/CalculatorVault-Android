package com.aurora.calculatorvault.feature.payment.data

import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Account identity an order is bound to: server user id + entitlement session generation. */
data class PurchaseOwner(val userId: Long, val generation: Long)

/** Bridge to the existing account session; implementations never expose the token elsewhere. */
interface PaymentSessionPort {
    /**
     * @param requireAuthenticated true for creating orders (strict Authenticated only);
     * false for querying, which also accepts the same trusted account under re-validation.
     */
    fun currentOwner(requireAuthenticated: Boolean): PurchaseOwner?

    fun accessToken(): String?

    /** Server reported the session invalid for [userId]; must ignore non-current accounts. */
    fun onUnauthorized(userId: Long)
}

/**
 * App-scoped payment data owner.
 *
 * - Plans are always fetched from the server; there is no local price fallback.
 * - One create call at a time. A clientRequestId is generated for each new purchase and reused
 *   only when retrying that same purchase after a failure that may not have reached the server.
 * - Every result is dropped (returned as [PaymentError] AccountChanged) once the owner is no
 *   longer the current account, so a previous account cannot update the new one.
 */
class PaymentRepository(
    private val api: PaymentApi,
    private val store: PendingPurchaseStore,
    private val session: PaymentSessionPort,
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
) {
    private val createMutex = Mutex()

    private val networkRestoredMutable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val networkRestored: SharedFlow<Unit> = networkRestoredMutable.asSharedFlow()

    sealed interface CreateOutcome {
        data class Created(val order: PaymentOrder) : CreateOutcome
        data class Failed(val error: PaymentError, val retryKept: Boolean) : CreateOutcome
        data object AccountChanged : CreateOutcome
        data object Busy : CreateOutcome
    }

    sealed interface QueryOutcome {
        data class Updated(val order: PaymentOrder) : QueryOutcome
        data class Failed(val error: PaymentError) : QueryOutcome
        data object AccountChanged : QueryOutcome
    }

    suspend fun loadPlans(): PaymentResult<List<MembershipPlanOffer>> = api.plans()

    fun currentOwner(requireAuthenticated: Boolean): PurchaseOwner? = session.currentOwner(requireAuthenticated)

    /** Recovery metadata for [userId] only; another account's record is never surfaced. */
    fun pendingFor(userId: Long): PendingPurchaseRecord? = store.read()?.takeIf { it.ownerUserId == userId }

    /**
     * @param retrySamePurchase reuse the stored clientRequestId for the same owner + plan when the
     * previous create for it was not confirmed; otherwise a new UUID starts a new purchase.
     */
    suspend fun createOrder(owner: PurchaseOwner, planCode: String, retrySamePurchase: Boolean): CreateOutcome {
        if (createMutex.isLocked) return CreateOutcome.Busy
        return createMutex.withLock {
            if (session.currentOwner(requireAuthenticated = true) != owner) return@withLock CreateOutcome.AccountChanged
            val token = session.accessToken() ?: return@withLock CreateOutcome.AccountChanged
            val existing = store.read()
            val requestId = if (
                retrySamePurchase && existing != null && existing.ownerUserId == owner.userId &&
                existing.planCode == planCode && existing.orderNo == null
            ) {
                existing.clientRequestId
            } else {
                newRequestId().also {
                    store.write(PendingPurchaseRecord(owner.userId, planCode, it, orderNo = null))
                }
            }
            val result = api.createOrder(token, planCode, requestId)
            if (session.currentOwner(requireAuthenticated = false) != owner) {
                return@withLock CreateOutcome.AccountChanged
            }
            when (result) {
                is PaymentResult.Success -> {
                    store.write(PendingPurchaseRecord(owner.userId, planCode, requestId, result.value.orderNo))
                    CreateOutcome.Created(result.value)
                }
                is PaymentResult.Failure -> {
                    val keep = when (result.error) {
                        PaymentError.Unauthorized -> {
                            session.onUnauthorized(owner.userId)
                            false
                        }
                        PaymentError.ClientRequestConflict,
                        PaymentError.LifetimeAlreadyActive,
                        PaymentError.PlanDisabled,
                        PaymentError.PlanNotFound,
                        -> false
                        // The server is idempotent per clientRequestId: keep it for a safe retry.
                        else -> true
                    }
                    if (!keep) store.clear()
                    CreateOutcome.Failed(result.error, retryKept = keep)
                }
            }
        }
    }

    suspend fun queryOrder(owner: PurchaseOwner, orderNo: String): QueryOutcome {
        if (session.currentOwner(requireAuthenticated = false) != owner) return QueryOutcome.AccountChanged
        val token = session.accessToken() ?: return QueryOutcome.AccountChanged
        val result = api.queryOrder(token, orderNo)
        if (session.currentOwner(requireAuthenticated = false) != owner) return QueryOutcome.AccountChanged
        return when (result) {
            is PaymentResult.Success -> {
                if (result.value.orderNo != orderNo) return QueryOutcome.Failed(PaymentError.Malformed)
                if (result.value.status == PaymentOrderStatus.FULFILLED) clearIfOrder(owner.userId, orderNo)
                QueryOutcome.Updated(result.value)
            }
            is PaymentResult.Failure -> {
                when (result.error) {
                    PaymentError.OrderNotFound -> clearIfOrder(owner.userId, orderNo)
                    PaymentError.Unauthorized -> session.onUnauthorized(owner.userId)
                    else -> Unit
                }
                QueryOutcome.Failed(result.error)
            }
        }
    }

    /** User abandoned the current purchase context (e.g. picked another plan). */
    fun discardPending(userId: Long) {
        if (store.read()?.ownerUserId == userId) store.clear()
    }

    /** Called from the app's network callback; pages waiting on an order re-query immediately. */
    fun onNetworkAvailable() {
        networkRestoredMutable.tryEmit(Unit)
    }

    private fun clearIfOrder(userId: Long, orderNo: String) {
        val current = store.read() ?: return
        if (current.ownerUserId == userId && current.orderNo == orderNo) store.clear()
    }
}
