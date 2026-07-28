package com.aurora.calculatorvault.core.navigation

sealed interface AppRoute {
    val path: String

    data object Splash : AppRoute { override val path = "splash" }
    data object PrivacyConsent : AppRoute { override val path = "privacy_consent" }
    data object CreatePassword : AppRoute { override val path = "create_password" }
    data object ConfirmPassword : AppRoute { override val path = "confirm_password" }
    data object UserAgreement : AppRoute { override val path = "user_agreement" }
    data object PrivacyPolicy : AppRoute { override val path = "privacy_policy" }
    data object Calculator : AppRoute { override val path = "calculator" }
    data object VaultMain : AppRoute { override val path = "vault_main" }
}

sealed interface VaultTabRoute {
    val path: String

    data object Disguise : VaultTabRoute { override val path = "vault/disguise" }
    data object HiddenApp : VaultTabRoute { override val path = "vault/hidden_app" }
    data object HiddenAppPicker : VaultTabRoute { override val path = "vault/hidden_app/picker" }
    data object PrivateMedia : VaultTabRoute { override val path = "vault/private_media" }
    data object Settings : VaultTabRoute { override val path = "vault/settings" }
    data object ChangePassword : VaultTabRoute { override val path = "vault/settings/change_password" }
    data object ForgotPassword : VaultTabRoute { override val path = "vault/settings/forgot_password" }
}
