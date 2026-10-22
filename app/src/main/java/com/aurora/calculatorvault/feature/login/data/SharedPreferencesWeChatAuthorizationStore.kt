package com.aurora.calculatorvault.feature.login.data

import android.content.Context
import com.aurora.calculatorvault.feature.login.domain.PendingWeChatAuthorization
import com.aurora.calculatorvault.feature.login.domain.PendingWeChatAuthorizationStore

class SharedPreferencesWeChatAuthorizationStore(context: Context) : PendingWeChatAuthorizationStore {
    private val preferences = context.applicationContext.getSharedPreferences("wechat_pending_authorization", Context.MODE_PRIVATE)
    override fun read(): PendingWeChatAuthorization? {
        val nonce = preferences.getString("state", null) ?: return null
        return PendingWeChatAuthorization(nonce, preferences.getLong("created_at_millis", 0))
    }
    // commit makes consumption durable before a code can be handed off; this file has only two tiny fields.
    override fun save(pending: PendingWeChatAuthorization): Boolean = preferences.edit()
        .putString("state", pending.state).putLong("created_at_millis", pending.createdAtMillis).commit()
    override fun clear(): Boolean = preferences.edit().clear().commit()
}
