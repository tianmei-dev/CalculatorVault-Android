package com.aurora.calculatorvault.feature.applock.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.theme.CalculatorVaultTheme
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.ui.component.VaultNumericKeypad
import com.aurora.calculatorvault.ui.component.VaultPasswordDots
import com.aurora.calculatorvault.ui.component.VaultPrimaryButton
import com.aurora.calculatorvault.ui.component.VaultSecondaryButton

data class AppLockOverlayState(
    val targetPackageName: String,
    val targetAppName: String? = null,
    val enteredLength: Int = 0,
    val verifying: Boolean = false,
    val passwordIncorrect: Boolean = false,
)

@Composable
fun AppLockOverlayContent(
    state: AppLockOverlayState,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CalculatorVaultTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppColors.BackgroundPrimary,
                            AppColors.BackgroundSecondary.copy(alpha = 0.96f),
                            AppColors.SurfacePrimary.copy(alpha = 0.98f),
                        ),
                    ),
                )
                .semantics {
                    contentDescription = state.targetAppName
                        ?: state.targetPackageName
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = AppSpacing.xl)
                    .padding(top = AppSpacing.xxl, bottom = AppSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(AppColors.SurfaceElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = VaultIcons.Lock,
                            contentDescription = null,
                            tint = AppColors.AccentPrimary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.height(AppSpacing.lg))
                    Text(
                        text = state.targetAppName ?: stringResource(R.string.app_lock_locked_app_fallback),
                        style = AppTextStyles.SectionTitle,
                        color = AppColors.TextPrimary,
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    Text(
                        text = stringResource(R.string.app_lock_overlay_title),
                        style = AppTextStyles.PageTitle,
                        color = AppColors.TextPrimary,
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(
                        text = stringResource(R.string.app_lock_overlay_subtitle),
                        style = AppTextStyles.BodySecondary,
                        color = AppColors.TextSecondary,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    VaultPasswordDots(enteredLength = state.enteredLength)
                    if (state.passwordIncorrect) {
                        Text(
                            text = stringResource(R.string.app_lock_password_incorrect),
                            style = AppTextStyles.Caption,
                            color = AppColors.Error,
                        )
                    } else {
                        Spacer(Modifier.height(20.dp))
                    }
                    if (state.verifying) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(top = AppSpacing.md)
                                .size(28.dp),
                            color = AppColors.AccentPrimary,
                            strokeWidth = 2.dp,
                        )
                    }
                }

                VaultNumericKeypad(
                    onDigit = onDigit,
                    onDelete = onDelete,
                    enabled = !state.verifying,
                )
                Spacer(Modifier.height(AppSpacing.md))
                VaultPrimaryButton(
                    text = stringResource(R.string.confirm),
                    enabled = !state.verifying && state.enteredLength in 4..8,
                    onClick = onConfirm,
                )
                VaultSecondaryButton(
                    text = stringResource(R.string.clear_action),
                    enabled = !state.verifying && state.enteredLength > 0,
                    onClick = onClear,
                )
            }
        }
    }
}
