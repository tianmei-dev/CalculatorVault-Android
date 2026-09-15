package com.aurora.calculatorvault.core.privacy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.RemoteException
import com.aurora.calculatorvault.app.MainActivity

/** Internal, SDK-free process. The binder death signal prevents relaunching a dying process. */
class PrivacyRestartActivity : Activity() {
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent.getBundleExtra(EXTRA_PROCESS)?.getBinder(EXTRA_TOKEN)
        val oldPid = intent.getIntExtra(EXTRA_PID, -1)
        if (token == null || oldPid <= 0 || oldPid == Process.myPid()) {
            finish()
            return
        }
        val handler = Handler(Looper.getMainLooper())
        try {
            token.linkToDeath({ handler.post { openPrivacyFlow() } }, 0)
            Process.killProcess(oldPid)
        } catch (_: RemoteException) {
            openPrivacyFlow()
        }
    }

    private fun openPrivacyFlow() {
        if (launched) return
        launched = true
        startActivity(Intent.makeRestartActivityTask(
            android.content.ComponentName(this, MainActivity::class.java),
        ))
        finish()
    }

    companion object {
        private val restarting = java.util.concurrent.atomic.AtomicBoolean(false)
        private const val EXTRA_PROCESS = "privacy_restart_process"
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_PID = "privacy_restart_pid"

        fun restart(context: Context) {
            if (!restarting.compareAndSet(false, true)) return
            try {
                context.startActivity(Intent(context, PrivacyRestartActivity::class.java).apply {
                    putExtra(EXTRA_PROCESS, Bundle().apply { putBinder(EXTRA_TOKEN, Binder()) })
                    putExtra(EXTRA_PID, Process.myPid())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
            } catch (error: RuntimeException) {
                restarting.set(false)
                throw error
            }
        }
    }
}
