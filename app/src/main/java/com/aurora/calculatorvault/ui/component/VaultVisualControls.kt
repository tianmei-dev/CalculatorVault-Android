package com.aurora.calculatorvault.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes

@Composable
internal fun useVaultDarkVisuals(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** Quiet backdrop for secondary content. The focused entry screens use vaultMainBackdrop. */
@Composable
fun Modifier.vaultSecondaryBackdrop(): Modifier {
    if (!useVaultDarkVisuals()) return this
    return background(
        Brush.verticalGradient(
            listOf(VaultMainColors.Background, lerp(VaultMainColors.Background, VaultMainColors.Card, 0.28f)),
        ),
    )
}

/** Decoration for an existing Material button with a transparent container. */
fun Modifier.vaultGradientAction(
    enabled: Boolean = true,
    secondary: Boolean = false,
    shape: Shape = AppShapes.Full,
): Modifier {
    if (!enabled || secondary) return vaultMainPanel(shape = shape)
    val base = VaultMainColors.Card
    val accent = VaultMainColors.Accent
    return background(
        Brush.linearGradient(listOf(lerp(base, accent, 0.55f), lerp(base, accent, 0.35f), lerp(base, accent, 0.52f))),
        shape,
    ).border(
        1.dp,
        Brush.linearGradient(listOf(VaultMainColors.Highlight, VaultMainColors.Border, accent)),
        shape,
    )
}

/** Material dialog slots, width, semantics, and outside/back handling with the vault surface. */
@Composable
fun VaultVisualAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AppShapes.ExtraLarge,
    properties: DialogProperties = DialogProperties(),
    emphasized: Boolean = false,
    containerColor: Color? = null,
    iconContentColor: Color? = null,
    titleContentColor: Color? = null,
    textContentColor: Color? = null,
    tonalElevation: Dp = 0.dp,
) {
    val colors = MaterialTheme.colorScheme
    val polished = useVaultDarkVisuals()
    MaterialTheme(
        colorScheme = if (polished) colors.copy(
            primary = VaultMainColors.Highlight,
            onSurface = VaultMainColors.TextPrimary,
            onSurfaceVariant = VaultMainColors.TextSecondary,
            outline = VaultMainColors.Border,
        ) else colors,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = if (polished) modifier.vaultMainPanel(emphasized, shape) else modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = if (polished) Color.Transparent else containerColor ?: colors.surface,
            iconContentColor = iconContentColor ?: if (polished) VaultMainColors.Highlight else colors.onSurfaceVariant,
            titleContentColor = titleContentColor ?: if (polished) VaultMainColors.TextPrimary else colors.onSurface,
            textContentColor = textContentColor ?: if (polished) VaultMainColors.TextSecondary else colors.onSurfaceVariant,
            tonalElevation = if (polished) 0.dp else tonalElevation,
            properties = properties,
        )
    }
}
