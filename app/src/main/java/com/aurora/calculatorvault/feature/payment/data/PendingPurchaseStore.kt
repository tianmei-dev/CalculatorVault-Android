package com.aurora.calculatorvault.feature.payment.data

import android.content.Context

/**
 * Minimal non-sensitive recovery metadata for an in-progress purchase.
 * Never contains a Bearer token, a pay link, amounts used for ordering, or membership data.
 * [orderNo] is null while the create call has not been confirmed by the server.
 */
data class PendingPurchaseRecord(
    val ownerUserId: Long,
    val planCode: String,
    val clientRequestId: String,
    val orderNo: String?,
)

interface PendingPurchaseStore {
    fun read(): PendingPurchaseRecord?
    fun write(record: PendingPurchaseRecord)
    fun clear()
}

class InMemoryPendingPurchaseStore : PendingPurchaseStore {
    @Volatile private var value: PendingPurchaseRecord? = null
    override fun read() = value
    override fun write(record: PendingPurchaseRecord) { value = record }
    override fun clear() { value = null }
}

/** Private app preferences (not backed up per existing backup rules); metadata only. */
class SharedPreferencesPendingPurchaseStore(context: Context) : PendingPurchaseStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("payment_pending_purchase", Context.MODE_PRIVATE)

    @Synchronized
    override fun read(): PendingPurchaseRecord? {
        val userId = prefs.getLong(KEY_USER, -1L).takeIf { it > 0L } ?: return null
        val plan = prefs.getString(KEY_PLAN, null)?.takeIf { it.isNotBlank() } ?: return null
        val requestId = prefs.getString(KEY_REQUEST, null)?.takeIf { it.isNotBlank() } ?: return null
        return PendingPurchaseRecord(userId, plan, requestId, prefs.getString(KEY_ORDER, null)?.takeIf { it.isNotBlank() })
    }

    @Synchronized
    override fun write(record: PendingPurchaseRecord) {
        prefs.edit()
            .putLong(KEY_USER, record.ownerUserId)
            .putString(KEY_PLAN, record.planCode)
            .putString(KEY_REQUEST, record.clientRequestId)
            .putString(KEY_ORDER, record.orderNo)
            .commit()
    }

    @Synchronized
    override fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val KEY_USER = "owner_user_id"
        const val KEY_PLAN = "plan_code"
        const val KEY_REQUEST = "client_request_id"
        const val KEY_ORDER = "order_no"
    }
}
