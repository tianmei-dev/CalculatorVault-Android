package com.aurora.calculatorvault.ui.message

import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import kotlinx.coroutines.delay

@Composable
fun rememberAppMessageController(): AppMessageController =
    remember { AppMessageController() }

@Composable
fun AppMessageHost(
    controller: AppMessageController,
    modifier: Modifier = Modifier,
) {
    var displayedMessage by remember { mutableStateOf<AppMessage?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(controller) {
        controller.messages.collect { message ->
            displayedMessage = message
            visible = true
            delay(message.durationMillis())
            visible = false
            delay(MESSAGE_EXIT_SETTLE_MILLIS)
            displayedMessage = null
        }
    }

    val message = displayedMessage ?: return
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        ConfigureMessageDialogWindow()
        Box(
            modifier = modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 72.dp)
                .padding(horizontal = AppSpacing.xl),
            contentAlignment = Alignment.TopCenter,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    animationSpec = tween(MESSAGE_ANIMATION_MILLIS),
                    initialOffsetY = { -it },
                ) + fadeIn(animationSpec = tween(MESSAGE_ANIMATION_MILLIS)),
                exit = slideOutVertically(
                    animationSpec = tween(MESSAGE_ANIMATION_MILLIS),
                    targetOffsetY = { -it },
                ) + fadeOut(animationSpec = tween(MESSAGE_ANIMATION_MILLIS)),
            ) {
                AppMessageCard(message)
            }
        }
    }
}

@Composable
private fun ConfigureMessageDialogWindow() {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.apply {
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
        }
    }
}

@Composable
private fun AppMessageCard(message: AppMessage) {
    val style = message.type.style()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, AppShapes.Large)
            .semantics {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = message.message
            },
        shape = AppShapes.Large,
        color = style.containerColor,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .background(style.accentColor.copy(alpha = style.accentAlpha))
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.iconColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = message.message,
                style = AppTextStyles.BodySecondary,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private data class AppMessageStyle(
    val icon: ImageVector,
    val containerColor: Color,
    val iconColor: Color,
    val accentColor: Color,
    val accentAlpha: Float,
)

private fun AppMessageType.style(): AppMessageStyle = when (this) {
    AppMessageType.Success -> AppMessageStyle(
        icon = VaultIcons.Success,
        containerColor = AppColors.SurfaceElevated,
        iconColor = AppColors.AccentPrimary,
        accentColor = AppColors.AccentPrimary,
        accentAlpha = 0.16f,
    )
    AppMessageType.Info -> AppMessageStyle(
        icon = VaultIcons.Info,
        containerColor = AppColors.SurfaceElevated,
        iconColor = AppColors.TextSecondary,
        accentColor = AppColors.SurfaceSecondary,
        accentAlpha = 0.35f,
    )
    AppMessageType.Warning -> AppMessageStyle(
        icon = VaultIcons.Warning,
        containerColor = AppColors.SurfaceElevated,
        iconColor = AppColors.Warning,
        accentColor = AppColors.Warning,
        accentAlpha = 0.16f,
    )
    AppMessageType.Error -> AppMessageStyle(
        icon = VaultIcons.Error,
        containerColor = AppColors.SurfaceElevated,
        iconColor = AppColors.Error,
        accentColor = AppColors.Error,
        accentAlpha = 0.16f,
    )
}

private fun AppMessage.durationMillis(): Long = when (type) {
    AppMessageType.Success,
    AppMessageType.Info,
    -> 2_000L
    AppMessageType.Warning -> 2_500L
    AppMessageType.Error -> 3_000L
}

private const val MESSAGE_ANIMATION_MILLIS = 240
private const val MESSAGE_EXIT_SETTLE_MILLIS = 120L
