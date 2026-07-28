package com.aurora.calculatorvault.feature.onboarding.domain

import com.aurora.calculatorvault.core.datastore.security.SecurityPreferences
import com.aurora.calculatorvault.core.security.StoredPasswordMaterialValidator

sealed interface StartupDestination {
    data object PrivacyConsent : StartupDestination
    data object CreatePassword : StartupDestination
    data object Calculator : StartupDestination
}

object StartupDestinationResolver {
    fun resolve(preferences: SecurityPreferences): StartupDestination = when {
        !preferences.privacyAccepted -> StartupDestination.PrivacyConsent
        StoredPasswordMaterialValidator.validate(preferences) != null ->
            StartupDestination.Calculator
        else -> StartupDestination.CreatePassword
    }
}
