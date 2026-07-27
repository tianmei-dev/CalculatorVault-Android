package com.aurora.calculatorvault.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles

data class VaultNavigationItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier.height(AppDimensions.TopAppBar),
        title = { Text(title, style = AppTextStyles.PageTitle, color = AppColors.TextPrimary) },
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.BackgroundPrimary),
        windowInsets = WindowInsets(0),
    )
}

@Composable
fun VaultBottomNavigation(
    items: List<VaultNavigationItem>,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(AppDimensions.BottomNavigation),
        containerColor = AppColors.SurfacePrimary,
        tonalElevation = androidx.compose.ui.unit.Dp.Unspecified,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.selected,
                onClick = item.onClick,
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label, style = AppTextStyles.Caption) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppColors.AccentPrimary,
                    selectedTextColor = AppColors.AccentPrimary,
                    unselectedIconColor = AppColors.TextTertiary,
                    unselectedTextColor = AppColors.TextTertiary,
                    indicatorColor = AppColors.SurfaceSecondary,
                ),
            )
        }
    }
}

