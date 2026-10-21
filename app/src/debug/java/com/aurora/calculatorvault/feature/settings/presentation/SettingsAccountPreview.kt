package com.aurora.calculatorvault.feature.settings.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.theme.CalculatorVaultTheme
import com.aurora.calculatorvault.ui.message.AppMessageController
import com.aurora.calculatorvault.ui.message.LocalAppMessageController

class AccountSceneProvider : PreviewParameterProvider<AccountPreviewScenes.Scene> {
    override val values = AccountPreviewScenes.scenes.asSequence()
}

@Preview(name = "账号六种状态", widthDp = 360, heightDp = 740, showBackground = true)
@Preview(name = "账号大字体", widthDp = 360, heightDp = 640, fontScale = 1.6f, showBackground = true)
@Composable
fun SettingsAccountPreview(@PreviewParameter(AccountSceneProvider::class) scene: AccountPreviewScenes.Scene) {
    val messages = remember { AppMessageController() }
    CalculatorVaultTheme {
        CompositionLocalProvider(LocalAppMessageController provides messages) {
            Surface(color = AppColors.BackgroundPrimary) {
                SettingsScreen(
                    onChangePassword = {}, onAbout = {}, onContactUs = {},
                    onPrivacyDocuments = {}, onPermissionSettingsClick = {},
                    onLogin = {}, onVipMembership = {}, onLogoutConfirmed = {},
                    accountState = scene.state,
                )
            }
        }
    }
}
