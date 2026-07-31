package com.aurora.calculatorvault.feature.disguise.shortcut

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aurora.calculatorvault.app.CalculatorVaultApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShortcutPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DisguiseShortcutContract.ACTION_SHORTCUT_PINNED) return
        val shortcutId = intent.getStringExtra(DisguiseShortcutContract.EXTRA_SHORTCUT_ID)
            ?.takeIf { it.startsWith("cv_disguise_") }
            ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as CalculatorVaultApp
                application.disguiseEntryRepository.markShortcutAccepted(
                    shortcutId = shortcutId,
                    callbackAt = System.currentTimeMillis(),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

