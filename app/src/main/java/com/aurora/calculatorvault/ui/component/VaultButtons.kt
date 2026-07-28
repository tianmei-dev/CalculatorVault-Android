package com.aurora.calculatorvault.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(AppDimensions.LargeButton),
        shape = AppShapes.Medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.AccentPrimary,
            contentColor = AppColors.BackgroundPrimary,
            disabledContainerColor = AppColors.SurfaceSecondary,
            disabledContentColor = AppColors.TextDisabled,
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
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(AppDimensions.Button),
        shape = AppShapes.Medium,
        border = BorderStroke(1.dp, AppColors.BorderSubtle),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextPrimary),
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
