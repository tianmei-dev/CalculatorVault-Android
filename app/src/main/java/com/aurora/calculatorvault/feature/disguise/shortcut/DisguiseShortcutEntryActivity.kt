package com.aurora.calculatorvault.feature.disguise.shortcut

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.repeatOnLifecycle
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.app.MainActivity
import com.aurora.calculatorvault.core.designsystem.theme.CalculatorVaultTheme
import kotlinx.coroutines.launch

class DisguiseShortcutEntryActivity : ComponentActivity() {
    private val entryViewModel: DisguiseShortcutEntryViewModel by viewModels {
        EntryViewModelFactory(applicationContext as CalculatorVaultApp)
    }

    private val intentParser by lazy {
        DisguiseShortcutIntentParser(DisguiseShortcutEntryActivity::class.java.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = applicationContext as CalculatorVaultApp
        application.vaultSessionManager.lock()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            CalculatorVaultTheme {
                val state by entryViewModel.state.collectAsState()
                DisguiseShortcutEntryScreen(
                    state = state,
                    onDigit = entryViewModel::inputDigit,
                    onDelete = entryViewModel::deleteDigit,
                    onClear = entryViewModel::clearInput,
                    onConfirm = entryViewModel::confirmPassword,
                    onCancel = entryViewModel::cancel,
                    onOpenCalculator = entryViewModel::openCalculator,
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                entryViewModel.effects.collect { effect ->
                    when (effect) {
                        DisguiseShortcutEntryEffect.Finish -> finish()
                        DisguiseShortcutEntryEffect.OpenCalculator -> {
                            startActivity(
                                Intent(this@DisguiseShortcutEntryActivity, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                            )
                            finish()
                        }
                    }
                }
            }
        }
        entryViewModel.acceptIntent(parseIntent(intent))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        entryViewModel.acceptIntent(parseIntent(intent))
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing) {
            entryViewModel.expire()
            finish()
        }
    }

    private fun parseIntent(intent: Intent): DisguiseShortcutIntentResult {
        val payload = runCatching {
            DisguiseShortcutIntentPayload(
                action = intent.action,
                shortcutId = intent.getStringExtra(DisguiseShortcutContract.EXTRA_SHORTCUT_ID),
                extraKeys = intent.extras?.keySet().orEmpty(),
                isExplicit = intent.component != null,
                componentClassName = intent.component?.className,
            )
        }.getOrElse {
            return DisguiseShortcutIntentResult.Invalid
        }
        return intentParser.parse(payload)
    }

    private class EntryViewModelFactory(
        private val app: CalculatorVaultApp,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DisguiseShortcutEntryViewModel(
                resolveShortcut = app.resolveDisguiseShortcutUseCase,
                verifyPassword = app.verifyVaultPasswordUseCase,
                launchTarget = app.launchDisguisedTargetUseCase,
            ) as T
    }
}
