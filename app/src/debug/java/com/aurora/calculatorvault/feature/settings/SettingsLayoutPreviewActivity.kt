package com.aurora.calculatorvault.feature.settings

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import com.aurora.calculatorvault.core.designsystem.theme.CalculatorVaultTheme
import com.aurora.calculatorvault.feature.settings.presentation.*
import com.aurora.calculatorvault.feature.membership.domain.VipPlan
import com.aurora.calculatorvault.ui.component.*
import com.aurora.calculatorvault.ui.message.*
import java.time.Duration
import java.time.LocalDate

/** Debug-only, in-memory visual fixtures. No login, membership or persistence calls. */
class SettingsLayoutPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind=intent.getStringExtra("fixture")?:"trial"
        val membership=when(kind) {
            "trial" -> MembershipStatus.TrialActive(Duration.ofHours(71))
            "expired" -> MembershipStatus.TrialExpired
            "monthly" -> MembershipStatus.VipActive(VipPlan.Monthly,LocalDate.of(2027,12,31))
            "annual" -> MembershipStatus.VipActive(VipPlan.Annual,LocalDate.of(2027,12,31))
            "lifetime" -> MembershipStatus.VipPermanent
            else -> MembershipStatus.NotSubscribed
        }
        val guest=kind=="guest"
        val state=AccountUiState(account=if(guest) AccountStatus.Guest else AccountStatus.WeChatLoggedIn(),
            accountMembership=if(membership is MembershipStatus.VipActive || membership==MembershipStatus.VipPermanent) membership else MembershipStatus.NotSubscribed,
            deviceTrial=if(membership is MembershipStatus.TrialActive || membership==MembershipStatus.TrialExpired) membership else MembershipStatus.NotSubscribed)
        setContent { CalculatorVaultTheme {
            CompositionLocalProvider(LocalAppMessageController provides remember { AppMessageController() }) {
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
                    val width=if(intent.getBooleanExtra("narrow",false)) Modifier.width(320.dp) else Modifier.fillMaxWidth()
                    Scaffold(modifier=width.fillMaxHeight(), bottomBar={
                        VaultBottomNavigation(listOf(
                            VaultNavigationItem("应用管理",Icons.Rounded.Apps,false,{}),
                            VaultNavigationItem("应用锁",Icons.Rounded.Lock,false,{}),
                            VaultNavigationItem("私密相册",Icons.Rounded.PhotoLibrary,false,{}),
                            VaultNavigationItem("设置",Icons.Rounded.Settings,true,{})))
                    }) { padding -> Box(Modifier.padding(padding)) {
                        SettingsScreen(onChangePassword={},onAbout={},onContactUs={},onPrivacyDocuments={},
                            onPermissionSettingsClick={},onLogin={},onVipMembership={},onLogoutConfirmed={},
                            accountState=state,onRefreshMembership={})
                    } }
                }
            }
        } }
    }
}
