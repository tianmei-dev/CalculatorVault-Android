package com.aurora.calculatorvault.feature.login.presentation

import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.feature.login.domain.WeChatAuthorizationStatus

internal fun wechatAuthorizationMessage(status: WeChatAuthorizationStatus): Int? = when (status) {
    WeChatAuthorizationStatus.Idle, WeChatAuthorizationStatus.Authorizing -> null
    WeChatAuthorizationStatus.AppIdMissing -> R.string.wechat_auth_app_id_missing
    WeChatAuthorizationStatus.WeChatUnavailable -> R.string.wechat_auth_unavailable
    WeChatAuthorizationStatus.InitializationFailed -> R.string.wechat_auth_initialization_failed
    WeChatAuthorizationStatus.LaunchFailed -> R.string.wechat_auth_launch_failed
    WeChatAuthorizationStatus.Cancelled -> R.string.wechat_auth_cancelled
    WeChatAuthorizationStatus.InvalidCallback -> R.string.wechat_auth_invalid_callback
    WeChatAuthorizationStatus.Expired -> R.string.wechat_auth_expired
    WeChatAuthorizationStatus.StorageFailed -> R.string.wechat_auth_storage_failed
    WeChatAuthorizationStatus.CodeReceived -> R.string.wechat_auth_code_received
    WeChatAuthorizationStatus.PrivacyRequired -> R.string.wechat_auth_privacy_required
    WeChatAuthorizationStatus.Failed, WeChatAuthorizationStatus.HandoffFailed -> R.string.wechat_auth_failed
}
