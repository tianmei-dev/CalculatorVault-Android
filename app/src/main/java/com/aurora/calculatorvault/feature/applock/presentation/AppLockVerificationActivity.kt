package com.aurora.calculatorvault.feature.applock.presentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.core.designsystem.theme.CalculatorVaultTheme
import kotlinx.coroutines.launch

class AppLockVerificationActivity : ComponentActivity() {
    private val verificationViewModel: AppLockVerificationViewModel by viewModels {
        Factory(
            app = applicationContext as CalculatorVaultApp,
            targetPackageName = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            CalculatorVaultTheme {
                val state by verificationViewModel.state.collectAsState()
                AppLockVerificationScreen(
                    state = state,
                    onDigit = verificationViewModel::inputDigit,
                    onDelete = verificationViewModel::deleteDigit,
                    onClear = verificationViewModel::clearInput,
                    onConfirm = verificationViewModel::confirmPassword,
                    onCancel = verificationViewModel::cancel,
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                verificationViewModel.effects.collect { effect ->
                    when (effect) {
                        AppLockVerificationEffect.Finish -> finish()
                    }
                }
            }
        }
    }

    private class Factory(
        private val app: CalculatorVaultApp,
        private val targetPackageName: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppLockVerificationViewModel(
                targetPackageName = targetPackageName,
                unlockUseCase = app.vaultUnlockUseCase,
                sessionManager = app.appLockSessionManager,
            ) as T
    }

    companion object {
        private const val EXTRA_TARGET_PACKAGE = "com.aurora.calculatorvault.extra.APP_LOCK_TARGET_PACKAGE"

        fun createIntent(context: Context, targetPackageName: String): Intent =
            Intent(context, AppLockVerificationActivity::class.java)
                .putExtra(EXTRA_TARGET_PACKAGE, targetPackageName)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
    }
}
