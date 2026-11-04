package com.aurora.calculatorvault.app

import com.aurora.calculatorvault.BuildConfig
import com.aurora.calculatorvault.feature.login.session.SessionCredential
import java.time.Instant

/**
 * Debug-only entry for I3.4.3-D2-A isolated Session injection.
 *
 * Present solely in the `debug` source set (Release / localIntegration DEX omit this type).
 * Requires `-PISOLATED_MEMBERSHIP_TEST=true` so [BuildConfig.TEST_SESSION_INSTALL_ENABLED] is true
 * and API hosts point at the s32it Base URL. Not an exported component — androidTest / same-process
 * Debug callers only.
 */
object IsolatedTestSessionAccess {

    fun isInstallEnabled(): Boolean =
        BuildConfig.DEBUG && BuildConfig.TEST_SESSION_INSTALL_ENABLED

    /**
     * Install a server-issued Bearer into [KeystoreTokenStore] via the real session state machine.
     * Does not grant VIP; membership still comes from `/api/v1/membership/me`.
     */
    fun install(app: CalculatorVaultApp, credential: SessionCredential) {
        check(isInstallEnabled()) {
            "Isolated test session install disabled (need Debug + ISOLATED_MEMBERSHIP_TEST)"
        }
        require(BuildConfig.API_BASE_URL.contains(":8081")) {
            "Isolated session install requires API_BASE_URL on :8081, was host-only check"
        }
        app.accountSession.installServerIssuedSession(credential)
    }

    fun install(
        app: CalculatorVaultApp,
        accessToken: String,
        expiresAt: Instant = Instant.now().plusSeconds(DEFAULT_CLIENT_TTL_SECONDS),
    ) {
        install(app, SessionCredential(accessToken, expiresAt))
    }

    private const val DEFAULT_CLIENT_TTL_SECONDS: Long = 24L * 60L * 60L
}
