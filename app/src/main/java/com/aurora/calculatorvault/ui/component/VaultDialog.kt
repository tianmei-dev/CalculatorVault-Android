package com.aurora.calculatorvault.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    val dark = useVaultDarkVisuals()
    val colors = MaterialTheme.colorScheme
    VaultVisualAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = AppTextStyles.SectionTitle) },
        text = { Text(message, style = AppTextStyles.Body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, style = AppTextStyles.Button, color = if (dark) VaultMainColors.Highlight else colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, style = AppTextStyles.Button, color = if (dark) VaultMainColors.TextSecondary else colors.onSurfaceVariant)
            }
        },
        shape = AppShapes.ExtraLarge,
    )
}
