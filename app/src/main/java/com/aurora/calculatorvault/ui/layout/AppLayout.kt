package com.aurora.calculatorvault.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing

object AppLayout {
    val PageHorizontalPadding: Dp = AppSpacing.xl
    val PageVerticalPadding: Dp = AppSpacing.xl
    val HeaderToSearchSpacing: Dp = AppSpacing.md
    val SearchToContentSpacing: Dp = AppSpacing.xl
    val BottomSafeSpace: Dp = AppSpacing.xl
    val CtaBottomSpacing: Dp = AppSpacing.xl
    val FabEndSpacing: Dp = AppSpacing.md
    val FabBottomSpacing: Dp = AppSpacing.xl
    val FabSize: Dp = AppDimensions.LargeButton
}

@Composable
fun appPagePadding(
    horizontal: Dp = AppLayout.PageHorizontalPadding,
    top: Dp = AppLayout.PageVerticalPadding,
    bottom: Dp = AppLayout.BottomSafeSpace,
): PaddingValues = PaddingValues(
    start = horizontal,
    top = top,
    end = horizontal,
    bottom = bottom,
)

@Composable
fun appScrollContentPadding(
    bottom: Dp = AppLayout.BottomSafeSpace,
): PaddingValues = PaddingValues(bottom = bottom)

@Composable
fun appFabScrollContentPadding(): PaddingValues = PaddingValues(
    bottom = AppLayout.FabSize + AppLayout.FabBottomSpacing + AppLayout.BottomSafeSpace,
)
