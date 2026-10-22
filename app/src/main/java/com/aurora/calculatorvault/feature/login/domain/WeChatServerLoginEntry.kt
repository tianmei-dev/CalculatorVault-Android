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
    /** One-shot handoff to the app-scoped Server session owner; authorization is not login. */
    fun acceptAuthorization(request: WeChatServerLoginRequest)
}
