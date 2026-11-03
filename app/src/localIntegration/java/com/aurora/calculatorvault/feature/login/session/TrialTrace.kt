package com.aurora.calculatorvault.feature.login.session

internal object TrialTrace {
    fun event(stage: Int, value: Int = 0) { try { android.util.Log.i("CV_TRIAL_DIAG", "stage=$stage value=$value") } catch (_: Exception) { } }
}
