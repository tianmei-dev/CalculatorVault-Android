package com.aurora.calculatorvault.core.privacy

import android.app.Activity
import android.content.Intent
import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.app.MainActivity
import kotlinx.coroutines.CancellationException

/** Also guard restored tasks and existing launcher shortcuts after withdrawal. */
suspend fun Activity.requirePrivacyConsent(): Boolean {
    val app = applicationContext as CalculatorVaultApp
    val allowed = try {
        app.hasPrivacyConsent()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }
    if (allowed) return true
    startActivity(Intent.makeRestartActivityTask(
        android.content.ComponentName(this, MainActivity::class.java),
    ))
    finish()
    return false
}
