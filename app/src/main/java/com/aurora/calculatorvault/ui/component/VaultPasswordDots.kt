package com.aurora.calculatorvault.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing

@Composable
fun VaultPasswordDots(
    enteredLength: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.password_dots_description, enteredLength)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.lg)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(8) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = AppSpacing.xxs)
                    .size(14.dp)
                    .background(
                        color = if (index < enteredLength) {
                            AppColors.AccentPrimary
                        } else {
                            AppColors.SurfaceElevated
                        },
                        shape = AppShapes.Full,
                    ),
            )
        }
    }
}
