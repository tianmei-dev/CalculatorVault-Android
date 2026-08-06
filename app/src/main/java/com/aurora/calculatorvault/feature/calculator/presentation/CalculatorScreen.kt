package com.aurora.calculatorvault.feature.calculator.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.calculator.domain.CalculatorAction
import com.aurora.calculatorvault.feature.calculator.domain.CalculatorError
import com.aurora.calculatorvault.feature.calculator.domain.CalculatorOperator

private data class CalculatorKey(
    @StringRes val labelRes: Int,
    @StringRes val contentDescriptionRes: Int,
    val action: CalculatorAction,
    val style: CalculatorKeyStyle = CalculatorKeyStyle.Number,
    val icon: ImageVector? = null,
)

private enum class CalculatorKeyStyle { Number, Utility, Operator, Equal }

private val calculatorKeys = listOf(
    CalculatorKey(
        R.string.calculator_key_clear,
        R.string.calculator_cd_clear,
        CalculatorAction.Clear,
        CalculatorKeyStyle.Utility,
    ),
    CalculatorKey(
        R.string.calculator_key_toggle_sign,
        R.string.calculator_cd_toggle_sign,
        CalculatorAction.ToggleSign,
        CalculatorKeyStyle.Utility,
    ),
    CalculatorKey(
        R.string.calculator_key_percent,
        R.string.calculator_cd_percent,
        CalculatorAction.Percent,
        CalculatorKeyStyle.Utility,
    ),
    CalculatorKey(
        R.string.calculator_key_divide,
        R.string.calculator_cd_divide,
        CalculatorAction.Operator(CalculatorOperator.Divide),
        CalculatorKeyStyle.Operator,
    ),
    CalculatorKey(R.string.calculator_key_7, R.string.calculator_cd_7, CalculatorAction.Number(7)),
    CalculatorKey(R.string.calculator_key_8, R.string.calculator_cd_8, CalculatorAction.Number(8)),
    CalculatorKey(R.string.calculator_key_9, R.string.calculator_cd_9, CalculatorAction.Number(9)),
    CalculatorKey(
        R.string.calculator_key_multiply,
        R.string.calculator_cd_multiply,
        CalculatorAction.Operator(CalculatorOperator.Multiply),
        CalculatorKeyStyle.Operator,
    ),
    CalculatorKey(R.string.calculator_key_4, R.string.calculator_cd_4, CalculatorAction.Number(4)),
    CalculatorKey(R.string.calculator_key_5, R.string.calculator_cd_5, CalculatorAction.Number(5)),
    CalculatorKey(R.string.calculator_key_6, R.string.calculator_cd_6, CalculatorAction.Number(6)),
    CalculatorKey(
        R.string.calculator_key_subtract,
        R.string.calculator_cd_subtract,
        CalculatorAction.Operator(CalculatorOperator.Subtract),
        CalculatorKeyStyle.Operator,
    ),
    CalculatorKey(R.string.calculator_key_1, R.string.calculator_cd_1, CalculatorAction.Number(1)),
    CalculatorKey(R.string.calculator_key_2, R.string.calculator_cd_2, CalculatorAction.Number(2)),
    CalculatorKey(R.string.calculator_key_3, R.string.calculator_cd_3, CalculatorAction.Number(3)),
    CalculatorKey(
        R.string.calculator_key_add,
        R.string.calculator_cd_add,
        CalculatorAction.Operator(CalculatorOperator.Add),
        CalculatorKeyStyle.Operator,
    ),
    CalculatorKey(R.string.calculator_key_0, R.string.calculator_cd_0, CalculatorAction.Number(0)),
    CalculatorKey(
        R.string.calculator_key_decimal,
        R.string.calculator_cd_decimal,
        CalculatorAction.Decimal,
    ),
    CalculatorKey(
        R.string.calculator_key_delete,
        R.string.calculator_cd_delete,
        CalculatorAction.Delete,
        CalculatorKeyStyle.Utility,
        VaultIcons.Backspace,
    ),
    CalculatorKey(
        R.string.calculator_key_equals,
        R.string.calculator_cd_equals,
        CalculatorAction.Equals,
        CalculatorKeyStyle.Equal,
    ),
)

@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onOpenVault: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect == CalculatorEffect.OpenVault) onOpenVault()
        }
    }
    CalculatorContent(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
private fun CalculatorContent(
    state: CalculatorUiState,
    onAction: (CalculatorAction) -> Unit,
) {
    val displayText = when (state.error) {
        CalculatorError.DivisionByZero -> stringResource(R.string.calculator_error_division_by_zero)
        CalculatorError.InvalidOperation -> stringResource(R.string.calculator_error_invalid_operation)
        null -> state.displayValue
    }
    val displayDescription = stringResource(R.string.calculator_display_description, displayText)
    val expressionDescription = if (state.expression.isEmpty()) {
        ""
    } else {
        stringResource(R.string.calculator_expression_description, state.expression)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = state.expression,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = expressionDescription
                    },
                style = AppTextStyles.Body,
                color = AppColors.TextTertiary,
                textAlign = TextAlign.End,
                maxLines = 2,
            )
            Text(
                text = displayText,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = displayDescription },
                style = resultTextStyle(displayText.length, state.error != null),
                color = if (state.error == null) AppColors.TextPrimary else AppColors.Error,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            userScrollEnabled = false,
        ) {
            items(calculatorKeys, key = { it.labelRes }) { key ->
                CalculatorKeyCell(key = key, onClick = { onAction(key.action) })
            }
        }
    }
}

@Composable
private fun CalculatorKeyCell(
    key: CalculatorKey,
    onClick: () -> Unit,
) {
    val label = stringResource(key.labelRes)
    val description = stringResource(key.contentDescriptionRes)
    val container = when (key.style) {
        CalculatorKeyStyle.Number -> AppColors.SurfacePrimary
        CalculatorKeyStyle.Utility -> AppColors.SurfaceSecondary
        CalculatorKeyStyle.Operator -> AppColors.AccentContainer
        CalculatorKeyStyle.Equal -> AppColors.AccentPrimary
    }
    val content = when (key.style) {
        CalculatorKeyStyle.Equal -> AppColors.SurfacePrimary
        CalculatorKeyStyle.Operator -> AppColors.AccentPrimary
        CalculatorKeyStyle.Utility -> AppColors.TextSecondary
        CalculatorKeyStyle.Number -> AppColors.TextPrimary
    }
    val shape = when (key.style) {
        CalculatorKeyStyle.Equal -> AppShapes.Large
        else -> AppShapes.Medium
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimensions.CalculatorKey)
            .semantics { contentDescription = description },
        shape = shape,
        color = container,
        contentColor = content,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (key.icon != null) {
                Icon(
                    imageVector = key.icon,
                    contentDescription = null,
                    tint = content,
                )
            } else {
                Text(label, style = AppTextStyles.SectionTitle)
            }
        }
    }
}

private fun resultTextStyle(
    length: Int,
    hasError: Boolean,
): TextStyle = when {
    hasError -> AppTextStyles.NumericCompact
    length <= 7 -> AppTextStyles.NumericLarge
    length <= 10 -> AppTextStyles.NumericMedium
    else -> AppTextStyles.NumericCompact
}
