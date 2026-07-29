package com.aurora.calculatorvault.feature.hiddenapp.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.feature.hiddenapp.data.AppIconProvider

@Composable
fun HiddenAppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClear: () -> Unit = {},
) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimensions.SearchBar)
            .background(AppColors.SurfacePrimary, AppShapes.Medium)
            .padding(horizontal = AppSpacing.md),
        enabled = enabled,
        singleLine = true,
        textStyle = AppTextStyles.Body.copy(color = AppColors.TextPrimary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
        cursorBrush = SolidColor(AppColors.AccentPrimary),
        decorationBox = { innerField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(VaultIcons.Search, contentDescription = null, tint = AppColors.TextTertiary)
                Spacer(Modifier.width(AppSpacing.sm))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        androidx.compose.material3.Text(
                            text = placeholder,
                            style = AppTextStyles.Body,
                            color = AppColors.TextTertiary,
                        )
                    }
                    innerField()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear, enabled = enabled) {
                        Icon(
                            VaultIcons.Close,
                            contentDescription = stringResource(R.string.hidden_app_clear_search),
                            tint = AppColors.TextSecondary,
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun VaultInstalledAppIcon(
    packageName: String,
    appName: String,
    iconProvider: AppIconProvider,
    modifier: Modifier = Modifier,
) {
    val drawable by produceState<android.graphics.drawable.Drawable?>(
        initialValue = null,
        packageName,
        iconProvider,
    ) {
        value = iconProvider.load(packageName)
    }
    val image = drawable?.let {
        runCatching { it.toBitmap(ICON_BITMAP_SIZE, ICON_BITMAP_SIZE).asImageBitmap() }.getOrNull()
    }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = stringResource(R.string.hidden_app_icon_description, appName),
            modifier = modifier.size(AppIconDefaultSize).clip(AppShapes.Medium),
        )
    } else {
        Box(
            modifier = modifier
                .size(AppIconDefaultSize)
                .background(AppColors.SurfaceSecondary, AppShapes.Medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = VaultIcons.Apps,
                contentDescription = stringResource(R.string.hidden_app_icon_description, appName),
                tint = AppColors.AccentPrimary,
            )
        }
    }
}

private val AppIconDefaultSize = 64.dp
private const val ICON_BITMAP_SIZE = 128
