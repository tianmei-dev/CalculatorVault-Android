package com.aurora.calculatorvault.feature.login.session

import com.aurora.calculatorvault.app.CalculatorVaultApp
import com.aurora.calculatorvault.app.IsolatedTestSessionAccess
import com.aurora.calculatorvault.feature.entitlement.EntitlementInstrumentationBridge
import java.io.File
import java.time.Instant
import org.json.JSONObject

/**
 * androidTest-only helper: loads D1 session fixtures from a device-local JSON file and installs
 * them through [IsolatedTestSessionAccess] (real KeystoreTokenStore + AccountSessionRepository).
 *
 * Never logs or stringifies access tokens. Clears Fake entitlement overrides so VIP comes from `/me`.
 */
object IsolatedTestSessionBridge {

    /** Emulator path used by D2-A host push scripts (not app-private storage). */
    const val DEFAULT_DEVICE_SESSION_FILE: String = "/data/local/tmp/cv_i343d1_sessions.local.json"

    data class FixtureAccount(
        val label: String,
        val userId: Long?,
        val expectedStatus: String?,
        val expectedHttp: Int?,
        private val accessToken: String,
    ) {
        fun credential(expiresAt: Instant = Instant.now().plusSeconds(24L * 3600L)): SessionCredential =
            SessionCredential(accessToken, expiresAt)

        override fun toString(): String =
            "FixtureAccount(label=$label, userId=$userId, expectedStatus=$expectedStatus, expectedHttp=$expectedHttp, accessToken=[REDACTED])"
    }

    fun loadCatalog(deviceFilePath: String = DEFAULT_DEVICE_SESSION_FILE): Map<String, FixtureAccount> {
        val file = File(deviceFilePath)
        require(file.isFile) { "session fixture file missing on device (host must adb push)" }
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val accounts = root.getJSONArray("accounts")
        val map = linkedMapOf<String, FixtureAccount>()
        for (i in 0 until accounts.length()) {
            val row = accounts.getJSONObject(i)
            val label = row.getString("label")
            val token = row.getString("accessToken")
            require(token.isNotBlank()) { "blank token for label=$label" }
            map[label] = FixtureAccount(
                label = label,
                userId = if (row.has("userId") && !row.isNull("userId")) row.getLong("userId") else null,
                expectedStatus = if (row.has("expectedStatus") && !row.isNull("expectedStatus")) {
                    row.getString("expectedStatus").takeIf { it.isNotBlank() }
                } else {
                    null
                },
                expectedHttp = if (row.has("expectedHttp") && !row.isNull("expectedHttp")) {
                    row.getInt("expectedHttp")
                } else {
                    null
                },
                accessToken = token,
            )
        }
        require(map.isNotEmpty()) { "empty session catalog" }
        return map
    }

    fun install(app: CalculatorVaultApp, account: FixtureAccount) {
        EntitlementInstrumentationBridge.clear(app)
        IsolatedTestSessionAccess.install(app, account.credential())
    }

    fun installLabel(app: CalculatorVaultApp, catalog: Map<String, FixtureAccount>, label: String) {
        val account = catalog[label] ?: error("unknown fixture label=$label")
        install(app, account)
    }
}
