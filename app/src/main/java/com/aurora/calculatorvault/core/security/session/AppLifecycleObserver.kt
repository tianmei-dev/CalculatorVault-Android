package com.aurora.calculatorvault.core.security.session

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class AppLifecycleObserver(
    private val sessionManager: VaultSessionManager,
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        sessionManager.onAppForegrounded()
    }

    override fun onStop(owner: LifecycleOwner) {
        sessionManager.onAppBackgrounded()
    }
}
