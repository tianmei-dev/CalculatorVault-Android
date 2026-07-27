package com.aurora.calculatorvault.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles

@Composable
fun VaultDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = AppTextStyles.SectionTitle, color = AppColors.TextPrimary) },
        text = { Text(message, style = AppTextStyles.Body, color = AppColors.TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, style = AppTextStyles.Button, color = AppColors.AccentPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, style = AppTextStyles.Button, color = AppColors.TextSecondary)
            }
        },
        shape = AppShapes.ExtraLarge,
        containerColor = AppColors.SurfaceElevated,
        tonalElevation = androidx.compose.ui.unit.Dp.Unspecified,
    )
}
