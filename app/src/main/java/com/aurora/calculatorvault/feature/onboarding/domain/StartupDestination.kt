package com.aurora.calculatorvault.feature.onboarding.domain

import com.aurora.calculatorvault.core.datastore.security.SecurityPreferences
import com.aurora.calculatorvault.core.privacy.PrivacyPolicy
import com.aurora.calculatorvault.core.security.StoredPasswordMaterialValidator

sealed interface StartupDestination {
    data object PrivacyConsent : StartupDestination
    data object CreatePassword : StartupDestination
    data object Calculator : StartupDestination
    data object CalculatorOnly : StartupDestination
}

object StartupDestinationResolver {
    fun resolve(preferences: SecurityPreferences): StartupDestination = when {
        preferences.privacyDeclined -> StartupDestination.CalculatorOnly
        !PrivacyPolicy.hasValidConsent(preferences) -> StartupDestination.PrivacyConsent
        StoredPasswordMaterialValidator.validate(preferences) != null ->
            StartupDestination.Calculator
        else -> StartupDestination.CreatePassword
    }
}
