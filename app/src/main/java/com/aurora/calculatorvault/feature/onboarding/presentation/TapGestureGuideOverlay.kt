package com.aurora.calculatorvault.feature.onboarding.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles
import com.aurora.calculatorvault.ui.component.VaultMainColors
import kotlin.math.roundToInt

/** Intercepts the page's touch input while leaving system navigation to the host. */
@Composable
internal fun TapGestureGuideOverlay(
    targetBounds: Rect,
    targetCornerRadius: Dp,
    hint: String,
    onTargetTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overlayOrigin by remember { mutableStateOf<Offset?>(null) }
    val currentTargetTap by rememberUpdatedState(onTargetTap)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() },
    ) {
        val viewport = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val bounds = overlayOrigin?.let { targetBounds.translate(-it.x, -it.y).intersect(viewport) }
            ?.takeIf { it.width > 0f && it.height > 0f }
        val cornerRadius = bounds?.let {
            with(density) { targetCornerRadius.toPx() }.coerceAtMost(minOf(it.width, it.height) / 2f)
        } ?: 0f
        val spotlight = bounds?.let { RoundRect(it, CornerRadius(cornerRadius)) }

        Box(
            Modifier.matchParentSize().pointerInput(spotlight) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    var targetTap = spotlight?.contains(down.position) == true
                    down.consume()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val touch = event.changes.singleOrNull()?.takeIf { it.id == down.id && !it.isConsumed }
                        targetTap = targetTap && touch != null &&
                            (touch.position - down.position).getDistance() <= viewConfiguration.touchSlop
                        event.changes.forEach { it.consume() }
                        if (targetTap && touch != null && !touch.pressed &&
                            spotlight?.contains(touch.position) == true &&
                            touch.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                        ) {
                            currentTargetTap()
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        ) {
            if (bounds != null && spotlight != null) {
                val cutout = remember(spotlight) { Path().apply { addRoundRect(spotlight) } }
                val fingerSize = 48.dp
                val tipInset = with(density) { 12.dp.toPx() }
                val tip = Offset(
                    minOf(bounds.right - tipInset * 2, viewport.right - with(density) { fingerSize.toPx() })
                        .coerceAtLeast(bounds.center.x),
                    bounds.bottom - minOf(tipInset, bounds.height / 3f),
                )
                val animation = rememberInfiniteTransition(label = "Guide tap")
                val press by animation.animateFloat(
                    initialValue = 0f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = TAP_INTERVAL_MILLIS
                            1f at 240
                            0f at 540
                        },
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "Guide finger press",
                )
                val ripple by animation.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = TAP_INTERVAL_MILLIS
                            0f at 240
                            1f at 1010
                        },
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "Guide tap ripple",
                )
                val highlight = VaultMainColors.Highlight
                Canvas(Modifier.matchParentSize()) {
                    clipPath(cutout, ClipOp.Difference) {
                        drawRect(Color.Black.copy(alpha = 0.56f))
                        drawRoundRect(
                            highlight.copy(alpha = 0.07f), bounds.topLeft, bounds.size,
                            CornerRadius(cornerRadius), style = Stroke(14.dp.toPx()),
                        )
                        drawRoundRect(
                            highlight.copy(alpha = 0.12f), bounds.topLeft, bounds.size,
                            CornerRadius(cornerRadius), style = Stroke(8.dp.toPx()),
                        )
                        drawRoundRect(
                            highlight.copy(alpha = 0.75f), bounds.topLeft, bounds.size,
                            CornerRadius(cornerRadius), style = Stroke(2.dp.toPx()),
                        )
                    }
                    if (ripple > 0f && ripple < 1f) {
                        drawCircle(
                            color = highlight.copy(alpha = 0.62f * (1f - ripple)),
                            radius = (6.dp + 20.dp * ripple).toPx(),
                            center = tip,
                            style = Stroke(1.5.dp.toPx()),
                        )
                    }
                }
                Image(
                    painter = painterResource(R.drawable.ic_guide_tap_hand),
                    contentDescription = null,
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset {
                            IntOffset(
                                (tip.x - fingerSize.toPx() * FINGER_TIP_X).roundToInt(),
                                (tip.y - fingerSize.toPx() * FINGER_TIP_Y).roundToInt(),
                            )
                        }
                        .requiredSize(fingerSize)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(FINGER_TIP_X, FINGER_TIP_Y)
                            rotationZ = -37f
                            translationY = (1f - press) * 6.dp.toPx()
                        },
                )
                val hintPadding = 5.dp
                val hintHeight = with(density) {
                    AppTextStyles.BodySecondary.lineHeight.toPx() + 2 * hintPadding.toPx()
                }
                val hintY = with(density) {
                    val below = bounds.bottom + (35.dp - hintPadding).toPx()
                    if (below + hintHeight + 8.dp.toPx() <= viewport.bottom) below
                    else (bounds.top - hintHeight - 8.dp.toPx()).coerceAtLeast(0f)
                }
                Text(
                    text = hint,
                    color = Color.White,
                    style = AppTextStyles.BodySecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .offset { IntOffset(0, hintY.roundToInt()) }
                        .padding(horizontal = 12.dp)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(Color(0xDE554080), Color(0xE33B2A5A), Color(0xDC302343)),
                            ),
                            shape = CircleShape,
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                listOf(highlight.copy(alpha = 0.52f), highlight.copy(alpha = 0.19f)),
                            ),
                            shape = CircleShape,
                        )
                        .padding(horizontal = 14.dp, vertical = hintPadding)
                        .clearAndSetSemantics { },
                )
                Box(
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                        .requiredSize(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                        .semantics {
                            contentDescription = hint
                            role = Role.Button
                            onClick {
                                currentTargetTap()
                                true
                            }
                        },
                )
            }
        }
    }
}

private const val TAP_INTERVAL_MILLIS = 1500
// The fingertip in the drawable's 24×24 viewport.
private const val FINGER_TIP_X = 11.5f / 24f
private const val FINGER_TIP_Y = 3f / 24f
