package com.aurora.calculatorvault.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.calculatorvault.core.designsystem.typography.AppTextStyles

/** Palette and drawing layers for the four vault tabs. */
object VaultMainColors {
    val Background = Color(0xFF100E1D)
    val Card = Color(0xFF1B182E)
    val Border = Color(0xFF494063)
    val TextPrimary = Color(0xFFF5F3FF)
    val TextSecondary = Color(0xFFBEB5D8)
    val Accent = Color(0xFFAA8BFF)
    val Highlight = Color(0xFFD1C2FF)
}

fun Modifier.vaultMainBackdrop(): Modifier = drawWithCache {
    val width = size.width
    val height = minOf(size.height, 168.dp.toPx())
    fun Path.traceSweep() {
        moveTo(width * 0.41f, height)
        cubicTo(width * 0.64f, height * 0.52f, width * 0.94f, height * 0.65f, width * 1.015f, height * 0.06f)
    }
    val sweep = Path().apply { traceSweep() }
    val sweepFill = Path().apply {
        traceSweep()
        lineTo(width * 1.015f, 0f)
        lineTo(width * 0.41f, 0f)
        close()
    }
    val background = Brush.verticalGradient(listOf(VaultMainColors.Background, Color(0xFF110E22)))
    val ambient = Brush.radialGradient(
        listOf(Color(0x493D2378), Color.Transparent),
        center = Offset(width * 0.92f, height * 0.27f),
        radius = maxOf(width * 0.65f, 1f),
    )
    val reflection = Brush.radialGradient(
        0f to Color(0x845F39AA), 0.42f to Color(0x4554319C), 1f to Color.Transparent,
        center = Offset(width * 0.89f, height * 0.48f),
        radius = maxOf(width * 0.45f, 1f),
    )
    val edge = Brush.horizontalGradient(
        0f to Color.Transparent, 0.53f to Color.Transparent, 0.84f to Color(0x9E9871F5),
        1f to Color(0x248358E3),
    )
    onDrawBehind {
        drawRect(background)
        drawRect(ambient)
        drawPath(sweepFill, reflection)
        drawPath(sweep, edge, style = Stroke(0.75.dp.toPx()))
    }
}

fun Modifier.vaultMainPanel(
    emphasized: Boolean = false,
    shape: Shape = RoundedCornerShape(16.dp),
    accentSweep: Boolean = false,
): Modifier {
    val border = if (emphasized) {
        Brush.linearGradient(listOf(Color(0xFFA489E6), Color(0xFF554475), Color(0xFF9E73FF)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF51466F), Color(0xFF302947), Color(0xFF514175)))
    }
    return clip(shape).drawWithCache {
        val fill = if (emphasized) {
            Brush.linearGradient(listOf(Color(0xFF332759), Color(0xFF1D1934), Color(0xFF241B40)))
        } else {
            Brush.linearGradient(listOf(Color(0xFF241F3D), VaultMainColors.Card, Color(0xFF19162A)))
        }
        val gleam = Brush.radialGradient(
            listOf(if (emphasized) Color(0x8E8251E8) else Color(0x454E348A), Color.Transparent),
            center = Offset(size.width * 0.99f, size.height * 1.13f),
            radius = maxOf(size.width * 0.52f, 1f),
        )
        val facet = Path().apply {
            moveTo(size.width * 0.48f, size.height)
            quadraticTo(size.width * 0.8f, size.height * 0.62f, size.width, size.height * 0.57f)
            lineTo(size.width, size.height)
            close()
        }
        onDrawBehind {
            drawRect(fill)
            if (emphasized) drawRect(gleam)
            if (accentSweep) drawPath(facet, gleam)
        }
    }.border(1.dp, border, shape)
}

@Composable
fun VaultMainHeader(title: String, description: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            title,
            style = AppTextStyles.PageTitle.copy(fontWeight = FontWeight.Bold, lineHeight = 37.sp),
            color = VaultMainColors.TextPrimary,
        )
        if (description != null) {
            Text(
                description,
                style = AppTextStyles.Body.copy(fontSize = AppTextStyles.BodySecondary.fontSize),
                color = VaultMainColors.TextSecondary,
            )
        }
    }
}

@Composable
fun VaultMainIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    size: Dp = 40.dp,
    iconSize: Dp = 23.dp,
) {
    val shape = RoundedCornerShape(size * 0.27f)
    Box(
        modifier = modifier
            .size(size)
            .background(
                Brush.linearGradient(
                    listOf(
                        if (emphasized) Color(0xFF584293) else Color(0xFF443766),
                        Color(0xFF292243),
                        if (emphasized) Color(0xFF3D2A68) else Color(0xFF332A50),
                    ),
                ),
                shape,
            )
            .border(
                0.75.dp,
                Brush.linearGradient(listOf(Color(0x559B80E3), Color(0x193B2A60), Color(0x50785AB7))),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        VaultMainGlyph(icon, null, Modifier.size(iconSize), emphasized)
    }
}

@Composable
internal fun VaultMainGlyph(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = Color.White,
        modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val tint = Brush.verticalGradient(
                    listOf(Color(0xFFE7DDFF), if (emphasized) Color(0xFFAA8DFF) else Color(0xFFB4A4EB)),
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(tint, blendMode = BlendMode.SrcIn)
                }
            },
    )
}
