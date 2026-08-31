package com.aurora.calculatorvault.feature.onboarding.presentation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.color.AppColors
import com.aurora.calculatorvault.core.designsystem.icon.VaultIcons
import com.aurora.calculatorvault.core.designsystem.shape.AppShapes
import com.aurora.calculatorvault.core.designsystem.spacing.AppDimensions
import com.aurora.calculatorvault.core.designsystem.spacing.AppSpacing
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.core.guide.GuideType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class GuideTargetShape {
    Circle,
    RoundedRectangle,
}

@Composable
fun ProgressiveGuideOverlay(
    type: GuideType,
    onDismiss: () -> Unit,
    targetBounds: Rect? = null,
    targetShape: GuideTargetShape = GuideTargetShape.RoundedRectangle,
    avoidBottomNavigation: Boolean = false,
) {
    var visible by remember(type) { mutableStateOf(false) }
    var showOrganizationSteps by remember(type) { mutableStateOf(false) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var isClosing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val content = if (type == GuideType.APP_DISGUISE_CREATED && showOrganizationSteps) {
        GuideContent(
            title = R.string.guide_disguise_organize_title,
            body = R.string.guide_disguise_organize_steps,
            support = R.string.guide_disguise_organize_support,
            icon = VaultIcons.Files,
        )
    } else {
        guideContent(type)
    }

    fun requestDismiss() {
        if (isClosing) return
        isClosing = true
        visible = false
        scope.launch {
            delay(EXIT_ANIMATION_MILLIS.toLong())
            onDismiss()
        }
    }

    LaunchedEffect(type) { visible = true }
    BackHandler(onBack = ::requestDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(ENTER_ANIMATION_MILLIS)),
        exit = fadeOut(tween(EXIT_ANIMATION_MILLIS)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { overlayOrigin = it.positionInRoot() },
        ) {
            val localTarget = targetBounds?.translate(-overlayOrigin.x, -overlayOrigin.y)
            SpotlightScrim(
                targetBounds = localTarget,
                targetShape = targetShape,
                onOutsideClick = ::requestDismiss,
            )

            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(ENTER_ANIMATION_MILLIS)) +
                    slideInVertically(tween(ENTER_ANIMATION_MILLIS)) { it / 3 },
                exit = fadeOut(tween(EXIT_ANIMATION_MILLIS)) +
                    slideOutVertically(tween(EXIT_ANIMATION_MILLIS)) { it / 4 },
            ) {
                val targetAvoidance = if (type == GuideType.APP_DISGUISE_INTRO) 92.dp else 0.dp
                val navigationAvoidance = if (avoidBottomNavigation) {
                    AppDimensions.BottomNavigation
                } else {
                    0.dp
                }
                GuideBottomCard(
                    content = content,
                    showVaultEntryVisual = type == GuideType.VAULT_ENTRY,
                    maxCardHeight = maxHeight * 0.42f,
                    bottomPadding = 16.dp + navigationAvoidance + targetAvoidance,
                    secondaryAction = if (
                        type == GuideType.APP_DISGUISE_CREATED && !showOrganizationSteps
                    ) {
                        { showOrganizationSteps = true }
                    } else {
                        null
                    },
                    onDismiss = ::requestDismiss,
                )
            }
        }
    }
}

@Composable
private fun SpotlightScrim(
    targetBounds: Rect?,
    targetShape: GuideTargetShape,
    onOutsideClick: () -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onOutsideClick) {
                detectTapGestures { onOutsideClick() }
            },
    ) {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        }
        drawRect(Color.Black.copy(alpha = 0.54f))
        targetBounds?.let { bounds ->
            val padding = 12.dp.toPx()
            val spotlight = Rect(
                left = bounds.left - padding,
                top = bounds.top - padding,
                right = bounds.right + padding,
                bottom = bounds.bottom + padding,
            )
            when (targetShape) {
                GuideTargetShape.Circle -> {
                    drawCircle(
                        color = Color.Transparent,
                        radius = maxOf(spotlight.width, spotlight.height) / 2f,
                        center = spotlight.center,
                        blendMode = BlendMode.Clear,
                    )
                }
                GuideTargetShape.RoundedRectangle -> {
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = spotlight.topLeft,
                        size = spotlight.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                        blendMode = BlendMode.Clear,
                    )
                }
            }
        }
        drawIntoCanvas { it.restore() }
        targetBounds?.let { bounds ->
            val padding = 12.dp.toPx()
            val spotlight = Rect(
                bounds.left - padding,
                bounds.top - padding,
                bounds.right + padding,
                bounds.bottom + padding,
            )
            when (targetShape) {
                GuideTargetShape.Circle -> drawCircle(
                    color = AppColors.AccentPrimary,
                    radius = maxOf(spotlight.width, spotlight.height) / 2f,
                    center = spotlight.center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
                GuideTargetShape.RoundedRectangle -> drawRoundRect(
                    color = AppColors.AccentPrimary,
                    topLeft = spotlight.topLeft,
                    size = spotlight.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun GuideBottomCard(
    content: GuideContent,
    showVaultEntryVisual: Boolean,
    maxCardHeight: Dp,
    bottomPadding: Dp,
    secondaryAction: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(bottom = bottomPadding)
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
            .heightIn(max = maxCardHeight)
            .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        shape = AppShapes.ExtraLarge,
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(AppColors.AccentContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = content.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = AppColors.AccentPrimary,
                    )
                }
                Text(
                    text = stringResource(content.title),
                    modifier = Modifier.weight(1f),
                    style = AppTextStyles.SectionTitle,
                    color = AppColors.TextPrimary,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                Text(
                    text = stringResource(content.body),
                    style = AppTextStyles.Body,
                    color = AppColors.TextSecondary,
                )
                if (showVaultEntryVisual) {
                    Text(
                        text = stringResource(R.string.guide_vault_entry_visual),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = AppTextStyles.SectionTitle,
                        color = AppColors.AccentPrimary,
                    )
                }
                Text(
                    text = stringResource(content.support),
                    style = AppTextStyles.Caption,
                    color = AppColors.TextTertiary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (secondaryAction != null) {
                    TextButton(onClick = secondaryAction) {
                        Text(
                            text = stringResource(R.string.guide_disguise_view_organization),
                            style = AppTextStyles.Button,
                            color = AppColors.TextSecondary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 46.dp),
                    shape = AppShapes.Medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.AccentPrimary,
                        contentColor = AppColors.TextPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.guide_acknowledge),
                        style = AppTextStyles.Button,
                    )
                }
            }
        }
    }
}

private data class GuideContent(
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
    @param:StringRes val support: Int,
    val icon: ImageVector,
)

private fun guideContent(type: GuideType): GuideContent = when (type) {
    GuideType.VAULT_ENTRY -> GuideContent(
        R.string.guide_vault_entry_title,
        R.string.guide_vault_entry_body,
        R.string.guide_vault_entry_support,
        VaultIcons.Calculator,
    )
    GuideType.APP_LOCK -> GuideContent(
        R.string.guide_app_lock_title,
        R.string.guide_app_lock_body,
        R.string.guide_app_lock_support,
        VaultIcons.Lock,
    )
    GuideType.APP_DISGUISE_INTRO -> GuideContent(
        R.string.guide_disguise_intro_title,
        R.string.guide_disguise_intro_body,
        R.string.guide_disguise_intro_support,
        VaultIcons.Disguise,
    )
    GuideType.APP_DISGUISE_CREATED -> GuideContent(
        R.string.guide_disguise_created_title,
        R.string.guide_disguise_created_body,
        R.string.guide_disguise_created_support,
        VaultIcons.Success,
    )
    GuideType.PRIVATE_ALBUM -> GuideContent(
        R.string.guide_private_album_title,
        R.string.guide_private_album_body,
        R.string.guide_private_album_support,
        VaultIcons.Photos,
    )
}

private const val ENTER_ANIMATION_MILLIS = 220
private const val EXIT_ANIMATION_MILLIS = 170
