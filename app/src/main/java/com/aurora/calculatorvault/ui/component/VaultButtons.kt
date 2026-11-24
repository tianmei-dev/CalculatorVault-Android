package com.aurora.calculatorvault.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles

@Composable
fun VaultPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    polished: Boolean = false,
) {
    val styled = polished && useVaultDarkVisuals()
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().then(
            if (styled) Modifier.heightIn(min = AppDimensions.LargeButton).vaultGradientAction(enabled, shape = AppShapes.Medium)
            else Modifier.height(AppDimensions.LargeButton),
        ),
        shape = AppShapes.Medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (styled) Color.Transparent else AppColors.AccentPrimary,
            contentColor = if (styled) VaultMainColors.TextPrimary else AppColors.BackgroundPrimary,
            disabledContainerColor = if (styled) Color.Transparent else AppColors.SurfaceSecondary,
            disabledContentColor = if (styled) VaultMainColors.TextSecondary else AppColors.TextDisabled,
        ),
    ) {
        Text(text, style = AppTextStyles.Button)
    }
}

@Composable
fun VaultSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    polished: Boolean = false,
) {
    val styled = polished && useVaultDarkVisuals()
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().then(
            if (styled) Modifier.heightIn(min = AppDimensions.Button).vaultGradientAction(enabled, secondary = true, shape = AppShapes.Medium)
            else Modifier.height(AppDimensions.Button),
        ),
        shape = AppShapes.Medium,
        border = if (styled) null else BorderStroke(1.dp, AppColors.BorderSubtle),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (styled) VaultMainColors.TextPrimary else AppColors.TextPrimary,
        ),
    ) {
        Text(text, style = AppTextStyles.Button)
    }
}

@Composable
fun VaultIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(icon, contentDescription, tint = AppColors.TextSecondary)
    }
}
