package com.aurora.calculatorvault.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles

@Composable
fun VaultSecondaryTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backModifier: Modifier = Modifier,
    backContentDescription: String = stringResource(R.string.back),
    overlayActions: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (overlayActions) {
        Box(
            modifier = modifier.fillMaxWidth().heightIn(min = SecondaryTopBarMinHeight),
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = backModifier) {
                    Icon(
                        imageVector = VaultIcons.Back,
                        contentDescription = backContentDescription,
                        tint = AppColors.TextSecondary,
                    )
                }
                Spacer(Modifier.width(AppSpacing.xs))
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                    Text(
                        text = title,
                        style = AppTextStyles.PageTitle,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = AppTextStyles.BodySecondary,
                            color = AppColors.TextTertiary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        return
    }
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = SecondaryTopBarMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = backModifier) {
            Icon(
                imageVector = VaultIcons.Back,
                contentDescription = backContentDescription,
                tint = AppColors.TextSecondary,
            )
        }
        Spacer(Modifier.width(AppSpacing.xs))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
        ) {
            Text(
                text = title,
                style = AppTextStyles.PageTitle,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = AppTextStyles.BodySecondary,
                    color = AppColors.TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

private val SecondaryTopBarMinHeight = 88.dp
