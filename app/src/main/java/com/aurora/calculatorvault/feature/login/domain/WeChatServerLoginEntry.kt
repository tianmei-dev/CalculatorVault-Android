package com.aurora.calculatorvault.feature.login.domain

/** One-shot in-memory handoff only. Never place this request in saved state or persistence. */
class WeChatServerLoginRequest(val code: String) {
    init { require(code.isNotBlank()) }
    override fun toString() = "WeChatServerLoginRequest(code=[REDACTED])"

    companion object {
        const val METHOD = "POST"
        const val PATH = "/api/v1/auth/wechat/login"
        const val APP_CODE_HEADER = "X-App-Code"
        const val APP_CODE = "calculator_vault"
    }
}

fun interface WeChatServerLoginEntry {
    /** I2.1 replaces this handoff with the real Server login flow; authorization is not login. */
    fun acceptAuthorization(request: WeChatServerLoginRequest)
}

/** A6.4 consumes the handoff without retaining the code, creating a session or sending a request. */
class PendingWeChatServerLoginEntry : WeChatServerLoginEntry {
    override fun acceptAuthorization(request: WeChatServerLoginRequest) = Unit
}
