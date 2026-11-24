package com.aurora.calculatorvault.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles

data class VaultNavigationItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
    val testTag: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val dark = useVaultDarkVisuals()
    val colors = MaterialTheme.colorScheme
    val titleLine = with(LocalDensity.current) { AppTextStyles.SectionTitle.lineHeight.toDp() }
    val barHeight = maxOf(AppDimensions.TopAppBar, titleLine * 2 + 12.dp)
    TopAppBar(
        modifier = modifier.heightIn(min = AppDimensions.TopAppBar)
            .then(if (dark) Modifier.drawWithContent {
                drawContent()
                drawLine(
                    color = VaultMainColors.Border.copy(alpha = 0.45f),
                    start = Offset(0f, size.height - 0.5.dp.toPx()),
                    end = Offset(size.width, size.height - 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            } else Modifier),
        title = {
            Text(
                title, style = AppTextStyles.SectionTitle,
                color = if (dark) VaultMainColors.TextPrimary else colors.onSurface,
                maxLines = 2,
            )
        },
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = if (dark) VaultMainColors.Background else colors.background),
        expandedHeight = barHeight,
        windowInsets = WindowInsets(0),
    )
}

@Composable
fun VaultBottomNavigation(
    items: List<VaultNavigationItem>,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    NavigationBar(
        modifier = modifier.heightIn(min = AppDimensions.BottomNavigation)
            .background(Brush.verticalGradient(listOf(Color(0xFF211C39), Color(0xFF151226))), barShape)
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color(0xFF4A3E68), Color(0xFF211A34))),
                barShape,
            ),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                modifier = if (item.testTag == null) Modifier else Modifier.testTag(item.testTag),
                selected = item.selected,
                onClick = item.onClick,
                icon = {
                    val capsule = if (item.selected) {
                        Modifier.background(
                            Brush.verticalGradient(listOf(Color(0xFF543B87), Color(0xFF2B2348))),
                            CircleShape,
                        ).border(
                            0.75.dp,
                            Brush.verticalGradient(listOf(Color(0xFF9371CE), Color(0xFF40315D))),
                            CircleShape,
                        )
                    } else Modifier
                    Box(Modifier.size(width = 56.dp, height = 30.dp).then(capsule), contentAlignment = Alignment.Center) {
                        VaultMainGlyph(item.icon, item.label, Modifier.size(23.dp), item.selected)
                    }
                },
                label = {
                    Text(
                        item.label,
                        style = AppTextStyles.Caption.copy(
                            fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VaultMainColors.Highlight,
                    selectedTextColor = VaultMainColors.Accent,
                    unselectedIconColor = VaultMainColors.TextSecondary,
                    unselectedTextColor = VaultMainColors.TextSecondary,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}
