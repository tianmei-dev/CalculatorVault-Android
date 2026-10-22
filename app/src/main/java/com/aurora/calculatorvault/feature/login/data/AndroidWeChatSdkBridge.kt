package com.aurora.calculatorvault.feature.login.data

import android.content.Context
import android.content.Intent
import com.aurora.calculatorvault.feature.login.domain.WeChatSdkAvailability
import com.aurora.calculatorvault.feature.login.domain.WeChatSdkBridge
import com.tencent.mm.opensdk.modelmsg.SendAuth
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import com.tencent.mm.opensdk.utils.ILog
import com.tencent.mm.opensdk.utils.Log

/** Created once per Application; SDK initialization is deferred until explicit consent. */
class AndroidWeChatSdkBridge(context: Context, private val appId: String) : WeChatSdkBridge {
    private val applicationContext = context.applicationContext
    private var api: IWXAPI? = null
    private var registered = false

    @Synchronized
    override fun prepare(): WeChatSdkAvailability {
        if (appId.isBlank()) return WeChatSdkAvailability.AppIdMissing
        return try {
            // SDK diagnostics may include authorization payloads. Never send them to logcat.
            Log.setLogImpl(SilentWeChatLog)
            val instance = api ?: WXAPIFactory.createWXAPI(applicationContext, appId, true).also { api = it }
            if (!instance.isWXAppInstalled || instance.wxAppSupportAPI <= 0) {
                WeChatSdkAvailability.WeChatUnavailable
            } else if (registered || instance.registerApp(appId).also { registered = it }) {
                WeChatSdkAvailability.Ready
            } else {
                release()
                WeChatSdkAvailability.InitializationFailed
            }
        } catch (_: Exception) {
            release()
            WeChatSdkAvailability.InitializationFailed
        }
    }

    @Synchronized
    override fun sendAuthorization(scope: String, state: String): Boolean = api?.sendReq(SendAuth.Req().apply {
        this.scope = scope
        this.state = state
    }) ?: false

    fun handleIntent(intent: Intent, handler: IWXAPIEventHandler): Boolean {
        val instance = synchronized(this) { if (prepare() == WeChatSdkAvailability.Ready) api else null }
        // handleIntent can synchronously invoke the domain callback; do not hold the SDK lock.
        return instance?.handleIntent(intent, handler) == true
    }

    @Synchronized
    override fun release() {
        try { api?.detach() } catch (_: Exception) { /* No exception text: SDK payloads may contain secrets. */ }
        api = null
        registered = false
    }
}

private object SilentWeChatLog : ILog {
    override fun v(tag: String?, message: String?) = Unit
    override fun d(tag: String?, message: String?) = Unit
    override fun i(tag: String?, message: String?) = Unit
    override fun w(tag: String?, message: String?) = Unit
    override fun e(tag: String?, message: String?) = Unit
}
