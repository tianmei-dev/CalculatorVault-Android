package com.aurora.calculatorvault.core.datastore.security

data class SecurityPreferences(
    val privacyAccepted: Boolean = false,
    val privacyVersion: String? = null,
    val privacyAcceptedAt: Long? = null,
    val passwordConfigured: Boolean = false,
    val passwordHash: String? = null,
    val passwordSalt: String? = null,
    val passwordAlgorithm: String? = null,
    val passwordIterations: Int? = null,
    val passwordCreatedAt: Long? = null,
    val passwordUpdatedAt: Long? = null,
    val onboardingCompleted: Boolean = false,
) {
    fun hasAnyPasswordState(): Boolean =
        passwordConfigured ||
            onboardingCompleted ||
            passwordHash != null ||
            passwordSalt != null ||
            passwordAlgorithm != null ||
            passwordIterations != null ||
            passwordCreatedAt != null ||
            passwordUpdatedAt != null
}
