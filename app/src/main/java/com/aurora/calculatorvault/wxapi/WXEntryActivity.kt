package com.aurora.calculatorvault.wxapi

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.feature.login.presentation.wechatAuthorizationMessage
import com.tencent.mm.opensdk.constants.ConstantsAPI
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.modelmsg.SendAuth
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** Required package/class name. No content, code logging, code saved-state or vault unlocking. */
class WXEntryActivity : ComponentActivity(), IWXAPIEventHandler {
    private val app get() = application as CalculatorVaultApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        processCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processCallback(intent)
    }

    private fun processCallback(callback: Intent) {
        lifecycleScope.launch {
            try {
                if (!app.hasPrivacyConsent()) {
                    app.wechatAuthorization.cancel(privacyWithdrawn = true)
                    showResult()
                } else if (app.wechatAuthorization.hasPendingRequest()) {
                    if (!app.wechatSdk.handleIntent(callback, this@WXEntryActivity)) {
                        app.wechatAuthorization.rejectIntent()
                        showResult()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                app.wechatAuthorization.rejectIntent()
                showResult()
            } finally {
                // Do not keep incoming authorization extras in an Activity intent/history.
                callback.replaceExtras(null as Bundle?)
                finish()
            }
        }
    }

    override fun onReq(req: BaseReq) = Unit

    override fun onResp(resp: BaseResp) {
        if (resp is SendAuth.Resp && resp.type == ConstantsAPI.COMMAND_SENDAUTH && !app.privacyWithdrawn) {
            app.wechatAuthorization.onAuthorizationResponse(resp.errCode, resp.state, resp.code)
        } else {
            app.wechatAuthorization.rejectIntent()
        }
        showResult()
    }

    private fun showResult() {
        wechatAuthorizationMessage(app.wechatAuthorization.status.value)?.let {
            Toast.makeText(applicationContext, it, Toast.LENGTH_LONG).show()
        }
    }
}
