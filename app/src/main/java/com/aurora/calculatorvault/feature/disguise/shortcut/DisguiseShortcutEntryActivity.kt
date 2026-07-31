package com.aurora.calculatorvault.feature.disguise.shortcut

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.app.MainActivity
import kotlinx.coroutines.launch

class DisguiseShortcutEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = applicationContext as CalculatorVaultApp
        application.vaultSessionManager.lock()
        val shortcutId = intent
            .takeIf { it.action == DisguiseShortcutContract.ACTION_OPEN_DISGUISE_SHORTCUT }
            ?.getStringExtra(DisguiseShortcutContract.EXTRA_SHORTCUT_ID)
            ?.takeIf { it.startsWith("cv_disguise_") }

        lifecycleScope.launch {
            if (shortcutId != null) {
                application.disguiseEntryRepository.findByShortcutId(shortcutId)
            }
            startActivity(
                Intent(this@DisguiseShortcutEntryActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
            )
            finish()
        }
    }
}
