package com.aurora.calculatorvault.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.calculatorvault.core.designsystem.theme.CalculatorVaultTheme
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.feature.membership.domain.VipPurchaseSource
import com.aurora.calculatorvault.feature.membership.presentation.VipPurchaseViewModel
import com.aurora.calculatorvault.feature.membership.presentation.VipPurchaseScreen
import com.aurora.calculatorvault.feature.legal.presentation.LegalDocumentScreen
import com.aurora.calculatorvault.feature.legal.presentation.LegalDocumentType
import com.aurora.calculatorvault.ui.message.*

/** Temporary layout harness, injected only by the preview Gradle init script. */
class VipPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val previewWidth = intent.getIntExtra("preview_width", 10000)
        val previewHeight = intent.getIntExtra("preview_height", 10000)
        val fontScale = intent.getFloatExtra("preview_font_scale", 1f)
        setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale)) {
                CalculatorVaultTheme {
                    val messages = rememberAppMessageController()
                    CompositionLocalProvider(LocalAppMessageController provides messages) {
                        Box(Modifier.fillMaxSize().background(AppColors.BackgroundPrimary), contentAlignment = Alignment.TopCenter) {
                            Scaffold(contentWindowInsets = WindowInsets.safeDrawing,
                                containerColor = AppColors.BackgroundPrimary) { padding ->
                                Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(),
                                    contentAlignment = Alignment.TopCenter) {
                                    Box(Modifier.widthIn(max = previewWidth.dp).heightIn(max = previewHeight.dp).fillMaxSize()) {
                                        val model: VipPurchaseViewModel = viewModel(factory = VipPurchaseViewModel.Factory(VipPurchaseSource.Settings))
                                        var showPrivacy by remember { mutableStateOf(false) }
                                        BackHandler(showPrivacy) { showPrivacy = false }
                                        if (showPrivacy) LegalDocumentScreen(LegalDocumentType.PrivacyPolicy, { showPrivacy = false })
                                        else VipPurchaseScreen(model, { finish() }, { showPrivacy = true })
                                    }
                                }
                            }
                            AppMessageHost(controller = messages)
                        }
                    }
                }
            }
        }
    }
}
