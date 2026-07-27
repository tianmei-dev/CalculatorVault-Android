package com.aurora.calculatorvault.feature.calculator.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.common.DeveloperOptions
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.ui.component.VaultSecondaryButton

private data class CalculatorKey(
    val label: String,
    val style: CalculatorKeyStyle = CalculatorKeyStyle.Number,
)

private enum class CalculatorKeyStyle { Number, Utility, Operator, Equal }

private val calculatorKeys = listOf(
    CalculatorKey("C", CalculatorKeyStyle.Utility),
    CalculatorKey("±", CalculatorKeyStyle.Utility),
    CalculatorKey("%", CalculatorKeyStyle.Utility),
    CalculatorKey("÷", CalculatorKeyStyle.Operator),
    CalculatorKey("7"), CalculatorKey("8"), CalculatorKey("9"),
    CalculatorKey("×", CalculatorKeyStyle.Operator),
    CalculatorKey("4"), CalculatorKey("5"), CalculatorKey("6"),
    CalculatorKey("−", CalculatorKeyStyle.Operator),
    CalculatorKey("1"), CalculatorKey("2"), CalculatorKey("3"),
    CalculatorKey("+", CalculatorKeyStyle.Operator),
    CalculatorKey("0"), CalculatorKey("."), CalculatorKey("⌫", CalculatorKeyStyle.Utility),
    CalculatorKey("=", CalculatorKeyStyle.Equal),
)

@Composable
fun CalculatorScreen(onOpenVaultDebug: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(VaultIcons.Calculator, contentDescription = null, tint = AppColors.TextTertiary)
            Icon(VaultIcons.More, contentDescription = null, tint = AppColors.TextTertiary)
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = stringResource(R.string.calculator_expression),
                style = AppTextStyles.Body,
                color = AppColors.TextTertiary,
            )
            Text(
                text = stringResource(R.string.calculator_result),
                style = AppTextStyles.NumericLarge,
                color = AppColors.TextPrimary,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            userScrollEnabled = false,
        ) {
            items(calculatorKeys) { key -> CalculatorKeyCell(key) }
        }
        Text(
            text = stringResource(R.string.calculator_hint),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = AppTextStyles.Caption,
            color = AppColors.TextTertiary,
        )
        if (DeveloperOptions.ENABLE_VAULT_DEBUG_ENTRY) {
            VaultSecondaryButton(
                text = stringResource(R.string.debug_vault_entry),
                onClick = onOpenVaultDebug,
            )
        }
    }
}

@Composable
private fun CalculatorKeyCell(key: CalculatorKey) {
    val container = when (key.style) {
        CalculatorKeyStyle.Number -> AppColors.SurfacePrimary
        CalculatorKeyStyle.Utility -> AppColors.SurfaceSecondary
        CalculatorKeyStyle.Operator -> AppColors.AccentContainer
        CalculatorKeyStyle.Equal -> AppColors.AccentPrimary
    }
    val content = when (key.style) {
        CalculatorKeyStyle.Equal -> AppColors.BackgroundPrimary
        CalculatorKeyStyle.Operator -> AppColors.AccentPrimary
        CalculatorKeyStyle.Utility -> AppColors.TextSecondary
        CalculatorKeyStyle.Number -> AppColors.TextPrimary
    }
    Box(
        modifier = Modifier
            .aspectRatio(1.12f)
            .background(container, AppShapes.ExtraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Text(key.label, style = AppTextStyles.SectionTitle, color = content)
    }
}
