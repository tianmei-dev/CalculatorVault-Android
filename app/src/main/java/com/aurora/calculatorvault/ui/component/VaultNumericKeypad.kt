package com.aurora.calculatorvault.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import androidx.compose.ui.res.stringResource

private data class NumericKey(
    val digit: Int,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
)

private val numericRows = listOf(
    listOf(
        NumericKey(1, R.string.password_key_1, R.string.password_cd_1),
        NumericKey(2, R.string.password_key_2, R.string.password_cd_2),
        NumericKey(3, R.string.password_key_3, R.string.password_cd_3),
    ),
    listOf(
        NumericKey(4, R.string.password_key_4, R.string.password_cd_4),
        NumericKey(5, R.string.password_key_5, R.string.password_cd_5),
        NumericKey(6, R.string.password_key_6, R.string.password_cd_6),
    ),
    listOf(
        NumericKey(7, R.string.password_key_7, R.string.password_cd_7),
        NumericKey(8, R.string.password_key_8, R.string.password_cd_8),
        NumericKey(9, R.string.password_key_9, R.string.password_cd_9),
    ),
)

@Composable
fun VaultNumericKeypad(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        numericRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                row.forEach { key ->
                    NumericKeyButton(
                        modifier = Modifier.weight(1f),
                        label = stringResource(key.labelRes),
                        description = stringResource(key.descriptionRes),
                        enabled = enabled,
                        onClick = { onDigit(key.digit) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Box(modifier = Modifier.weight(1f))
            NumericKeyButton(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.password_key_0),
                description = stringResource(R.string.password_cd_0),
                enabled = enabled,
                onClick = { onDigit(0) },
            )
            NumericKeyButton(
                modifier = Modifier.weight(1f),
                label = null,
                description = stringResource(R.string.password_cd_delete),
                enabled = enabled,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun NumericKeyButton(
    label: String?,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(AppDimensions.CalculatorKey)
            .semantics { contentDescription = description },
        shape = AppShapes.Medium,
        color = AppColors.SurfacePrimary,
        contentColor = if (enabled) AppColors.TextPrimary else AppColors.TextDisabled,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (label == null) {
                Icon(
                    imageVector = VaultIcons.Backspace,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Text(label, style = AppTextStyles.SectionTitle)
            }
        }
    }
}

