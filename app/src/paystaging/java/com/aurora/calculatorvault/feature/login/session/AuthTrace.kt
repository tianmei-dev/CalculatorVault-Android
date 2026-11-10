package com.aurora.calculatorvault.feature.login.session

internal object AuthTrace {
    fun event(stage: Int, value: Int = 0) { /* No diagnostic output in this variant. */ }
    fun failure(error: Exception) {
        val kind = when (error) {
            is java.net.SocketTimeoutException -> 1
            is java.net.ConnectException -> 2
            is java.net.UnknownHostException -> 3
            is javax.net.ssl.SSLException -> 4
            is org.json.JSONException -> 5
            is IllegalArgumentException -> 6
            is java.io.IOException -> 7
            is kotlinx.coroutines.CancellationException -> 8
            is AuthFailure -> 9
            is SecurityException -> 10
            else -> 0
        }
        event(9, kind)
    }
}
