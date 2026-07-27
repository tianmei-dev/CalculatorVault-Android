package com.aurora.calculatorvault.core.designsystem.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.typography.VaultTypography

private val VaultDarkColors = darkColorScheme(
    primary = AppColors.AccentPrimary,
    onPrimary = AppColors.BackgroundPrimary,
    primaryContainer = AppColors.AccentContainer,
    onPrimaryContainer = AppColors.TextPrimary,
    secondary = AppColors.AccentSecondary,
    background = AppColors.BackgroundPrimary,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.SurfacePrimary,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceSecondary,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.BorderSubtle,
    error = AppColors.Error,
)

@Composable
fun CalculatorVaultTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = VaultDarkColors,
        typography = VaultTypography,
        shapes = androidx.compose.material3.Shapes(
            small = AppShapes.Small,
            medium = AppShapes.Medium,
            large = AppShapes.Large,
        ),
        content = content,
    )
}
