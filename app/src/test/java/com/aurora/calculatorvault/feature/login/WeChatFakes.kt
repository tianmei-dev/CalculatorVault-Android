package com.aurora.calculatorvault.feature.login

import com.aurora.calculatorvault.feature.login.domain.*

internal class FakeWeChatSdk(var availability: WeChatSdkAvailability = WeChatSdkAvailability.Ready) : WeChatSdkBridge {
    var prepareCount = 0
    var sendCount = 0
    var releaseCount = 0
    var sentState: String? = null
    var sentScope: String? = null
    var sendResult = true
    var throwOnPrepare = false
    var throwOnSend = false
    override fun prepare(): WeChatSdkAvailability {
        prepareCount++
        if (throwOnPrepare) error("SDK error")
        return availability
    }
    override fun sendAuthorization(scope: String, state: String): Boolean {
        sendCount++
        sentState = state
        sentScope = scope
        if (throwOnSend) error("SDK error")
        return sendResult
    }
    override fun release() { releaseCount++ }
}

internal class MemoryWeChatStore : PendingWeChatAuthorizationStore {
    var pending: PendingWeChatAuthorization? = null
    var saveResult = true
    var clearResult = true
    override fun read() = pending
    override fun save(pending: PendingWeChatAuthorization): Boolean {
        if (saveResult) this.pending = pending
        return saveResult
    }
    override fun clear(): Boolean {
        if (clearResult) pending = null
        return clearResult
    }
}

internal fun unconfiguredWeChatAuthorization() = WeChatAuthorizationCoordinator(
    FakeWeChatSdk(WeChatSdkAvailability.AppIdMissing), MemoryWeChatStore(), PendingWeChatServerLoginEntry(),
)

internal class PendingWeChatServerLoginEntry : WeChatServerLoginEntry {
    override fun acceptAuthorization(request: WeChatServerLoginRequest) = Unit
}
