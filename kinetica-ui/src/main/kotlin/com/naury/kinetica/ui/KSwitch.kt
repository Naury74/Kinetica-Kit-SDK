// 파일 이름은 대표 컴포저블인 KSwitch를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** [KSwitch]의 모양. */
public enum class KSwitchVariant(internal val width: Dp, internal val height: Dp) {
    /** 캡슐 트랙 안에 흰 손잡이가 들어 있다. 누르는 동안 손잡이가 옆으로 늘어나고, 켜고 끌 때 출렁이며 넘어간다. 56×32dp. */
    Capsule(56.dp, 32.dp),

    /** 꺼지면 테두리만 있는 트랙에 작은 손잡이, 켜지면 손잡이가 커지며 안에 체크가 그려진다. 누르면 손잡이가 더 커진다. 52×32dp. */
    Check(52.dp, 32.dp),

    /** 얇은 트랙 위에 테두리 두른 큰 손잡이가 걸쳐 있고, 누르는 동안 손잡이 둘레에 옅은 원이 퍼진다. 50×28dp. */
    Slim(50.dp, 28.dp),

    /** 꺼지면 테두리만 있는 트랙에 작은 글자 색 손잡이, 켜지면 채운 트랙에 흰 손잡이. 누르면 손잡이가 옆으로 늘어난다. 44×22dp. */
    Outline(44.dp, 22.dp),

    /** 글자 색 테두리를 두른 각진 트랙과 정사각형 손잡이. 손잡이 아래로 단단한 그림자를 깐다. 56×30dp. */
    Block(56.dp, 30.dp),
}

/**
 * 스위치. 모양은 [variant]로 고른다. 켜지면 트랙이 [accent]로 바뀐다. 오른쪽에서 왼쪽으로 쓰는 언어에서는 켜진 손잡이가 왼쪽에 간다.
 * '애니메이션 삭제' 접근성 설정이 켜져 있으면 손잡이가 바로 옮겨 간다.
 *
 * [onCheckedChange]가 `null`이면 누를 수 없는 표시만 그린다. [KListItem]이나 [KSwitchRow]처럼 행 전체를 누르게 할 때 쓴다.
 *
 * ```kotlin
 * KSwitch(wifi, { wifi = it })
 * KSwitch(sync, { sync = it }, variant = KSwitchVariant.Slim)
 * ```
 *
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 * @param accent 켜진 트랙 색. [Color.Unspecified]면 [KSwitchVariant.Capsule]는 [KineticaColors.positive],
 *   나머지는 [KineticaColors.accent]를 쓴다.
 * @param surface 꺼진 트랙과 손잡이 색을 정하는 표면
 */
@Composable
public fun KSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = Color.Unspecified,
    surface: SurfaceStyle = KineticaTheme.surface,
    variant: KSwitchVariant = KineticaTheme.design.controls.switch,
) {
    val interaction = remember { MutableInteractionSource() }
    val motion = rememberSwitchMotion(checked, variant, interaction)
    val border = KineticaTheme.design.borderWidth
    val colors = KineticaTheme.colors
    val paint = SwitchPaint(
        on = if (accent.isSpecified) {
            accent
        } else if (variant == KSwitchVariant.Capsule) {
            colors.positive
        } else {
            colors.accent
        },
        ink = surface.contentColor,
        surface = surface,
        dark = surface.isDark,
        fill = surface.fillColor.copy(alpha = 1f),
        border = border,
    )
    Box(
        modifier
            .size(variant.width, variant.height)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        checked,
                        interactionSource = interaction,
                        // 손잡이가 늘어나거나 커지는 것이 눌림 표시다.
                        indication = null,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            )
            .graphicsLayer { alpha = if (enabled) 1f else CHOICE_DISABLED_ALPHA }
            .drawBehind {
                val position = motion.position.value
                val press = motion.press.value
                when (variant) {
                    KSwitchVariant.Capsule -> drawCapsuleSwitch(position, press, paint)
                    KSwitchVariant.Check -> drawCheckSwitch(position, press, paint)
                    KSwitchVariant.Slim -> drawSlimSwitch(position, press, paint)
                    KSwitchVariant.Outline -> drawOutlineSwitch(position, press, paint)
                    KSwitchVariant.Block -> drawBlockSwitch(position, press, paint)
                }
            },
    )
}

/** 스위치 색. [on]은 켜진 트랙, [ink]는 표면 글자 색이다. */
private class SwitchPaint(val on: Color, val ink: Color, val surface: SurfaceStyle, val dark: Boolean, val fill: Color, val border: Dp)

/** 손잡이 위치(0..1, 출렁이면 범위를 조금 넘는다)와 눌림 정도(0..1) */
private class SwitchMotion(checked: Boolean) {
    val position = Animatable(if (checked) 1f else 0f)
    val press = Animatable(0f)
}

@Composable
private fun rememberSwitchMotion(checked: Boolean, variant: KSwitchVariant, interaction: MutableInteractionSource): SwitchMotion {
    val motion = remember { SwitchMotion(checked) }
    val still = reducedMotion()
    val feel = KineticaTheme.design.motion
    LaunchedEffect(checked, still, feel) {
        val target = if (checked) 1f else 0f
        if (still) {
            motion.position.snapTo(target)
        } else {
            // 캡슐 스위치만 넘어갈 때 출렁인다. 다른 둘은 바로 멈춰야 손잡이가 트랙 밖으로 나가 보이지 않는다.
            val damping = if (variant == KSwitchVariant.Capsule) feel.damping else Spring.DampingRatioNoBouncy
            val stiffness = if (variant ==
                KSwitchVariant.Capsule
            ) {
                Spring.StiffnessMediumLow
            } else {
                maxOf(Spring.StiffnessMediumLow, feel.stiffness)
            }
            motion.position.animateTo(target, spring(dampingRatio = damping, stiffness = stiffness))
        }
    }
    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            val down = it is PressInteraction.Press
            if (down || it is PressInteraction.Release || it is PressInteraction.Cancel) {
                launch { motion.press.animateTo(if (down) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
            }
        }
    }
    return motion
}

/** 왼쪽에서 잰 [x]를 레이아웃 방향에 맞춘다. 오른쪽에서 왼쪽 언어에서는 켜진 쪽이 왼쪽이다. */
private fun DrawScope.mirrored(x: Float): Float = if (layoutDirection == LayoutDirection.Rtl) size.width - x else x

private fun DrawScope.drawCapsuleSwitch(position: Float, press: Float, paint: SwitchPaint) {
    val t = position.coerceIn(0f, 1f)
    drawRoundRect(lerp(paint.surface.raisedFill(CAPSULE_OFF_ALPHA), paint.on, t), cornerRadius = CornerRadius(size.height / 2))
    val inset = CAPSULE_THUMB_INSET.toPx()
    val thumbHeight = size.height - inset * 2
    val thumbWidth = thumbHeight * (1f + CAPSULE_STRETCH * press)
    val travel = size.width - inset * 2 - thumbWidth
    val start = inset + travel * position
    val left = if (layoutDirection == LayoutDirection.Rtl) size.width - start - thumbWidth else start
    val corner = CornerRadius(thumbHeight / 2)
    drawRoundRect(Color.Black.copy(alpha = THUMB_SHADOW), Offset(left, inset + THUMB_DROP.toPx()), Size(thumbWidth, thumbHeight), corner)
    drawRoundRect(Color.White, Offset(left, inset), Size(thumbWidth, thumbHeight), corner)
}

private fun DrawScope.drawCheckSwitch(position: Float, press: Float, paint: SwitchPaint) {
    val t = position.coerceIn(0f, 1f)
    val radius = size.height / 2
    drawRoundRect(lerp(paint.surface.raisedFill(MARK_OFF_FILL), paint.on, t), cornerRadius = CornerRadius(radius))
    val border = MARK_BORDER.toPx()
    if (t < 1f) {
        drawRoundRect(
            paint.ink.copy(alpha = MARK_OFF_BORDER * (1f - t)),
            Offset(border / 2, border / 2),
            Size(size.width - border, size.height - border),
            CornerRadius(radius - border / 2),
            style = Stroke(border),
        )
    }
    val diameter = MARK_THUMB_OFF.toPx() + (MARK_THUMB_ON - MARK_THUMB_OFF).toPx() * t
    val thumb = diameter + (MARK_THUMB_PRESSED.toPx() - diameter) * press
    val center = Offset(mirrored(radius + (size.width - radius * 2) * t), size.height / 2)
    val thumbColor = lerp(paint.ink.copy(alpha = MARK_OFF_THUMB), if (paint.dark) Color(MARK_DARK_THUMB) else Color.White, t)
    drawCircle(thumbColor, thumb / 2, center)
    val draw = ((t - MARK_CHECK_FROM) / (1f - MARK_CHECK_FROM)).coerceIn(0f, 1f)
    if (draw > 0f) drawThumbCheck(center, MARK_CHECK.toPx(), draw, paint.on)
}

/** 손잡이 안 체크. 두 획을 [progress]만큼 앞에서부터 그린다. 매 프레임 Path를 만들지 않도록 선 두 개로 그린다. */
private fun DrawScope.drawThumbCheck(center: Offset, size: Float, progress: Float, color: Color) {
    val a = center + Offset(-size * CHECK_LEFT, 0f)
    val b = center + Offset(-size * CHECK_MID_X, size * CHECK_MID_Y)
    val c = center + Offset(size * CHECK_RIGHT_X, -size * CHECK_RIGHT_Y)
    val stroke = MARK_CHECK_STROKE.toPx()
    val first = (progress / CHECK_SPLIT).coerceAtMost(1f)
    drawLine(color, a, a + (b - a) * first, stroke, StrokeCap.Round)
    if (progress > CHECK_SPLIT) {
        val second = (progress - CHECK_SPLIT) / (1f - CHECK_SPLIT)
        drawLine(color, b, b + (c - b) * second, stroke, StrokeCap.Round)
    }
}

private fun DrawScope.drawSlimSwitch(position: Float, press: Float, paint: SwitchPaint) {
    val t = position.coerceIn(0f, 1f)
    val trackHeight = SLIM_TRACK.toPx()
    val inset = SLIM_TRACK_INSET.toPx()
    val trackColor = lerp(paint.surface.raisedFill(SLIM_OFF_TRACK), paint.on, t)
    drawRoundRect(
        trackColor,
        Offset(inset, (size.height - trackHeight) / 2),
        Size(size.width - inset * 2, trackHeight),
        CornerRadius(trackHeight / 2),
    )
    val radius = size.height / 2
    val center = Offset(mirrored(radius + (size.width - radius * 2) * t), size.height / 2)
    if (press > 0f) drawCircle(trackColor.copy(alpha = SLIM_HALO * press), radius * SLIM_HALO_SCALE, center)
    val ring = SLIM_RING.toPx()
    drawCircle(Color.Black.copy(alpha = THUMB_SHADOW), radius - ring / 2, center + Offset(0f, THUMB_DROP.toPx()))
    drawCircle(if (paint.dark) Color(SLIM_DARK_THUMB) else Color.White, radius - ring / 2, center)
    drawCircle(lerp(paint.ink.copy(alpha = SLIM_OFF_RING), paint.on, t), radius - ring / 2 - ring / 2, center, style = Stroke(ring))
}

private val CAPSULE_THUMB_INSET = 3.dp
private const val CAPSULE_STRETCH = 0.35f
private const val CAPSULE_OFF_ALPHA = 0.18f
private val THUMB_DROP = 1.dp
private const val THUMB_SHADOW = 0.15f

private const val MARK_OFF_FILL = 0.08f
private const val MARK_OFF_BORDER = 0.5f
private const val MARK_OFF_THUMB = 0.55f
private const val MARK_DARK_THUMB = 0xFF1C1B1F
private val MARK_BORDER = 2.dp
private val MARK_THUMB_OFF = 16.dp
private val MARK_THUMB_ON = 24.dp
private val MARK_THUMB_PRESSED = 28.dp
private val MARK_CHECK = 10.dp
private val MARK_CHECK_STROKE = 2.dp
private const val MARK_CHECK_FROM = 0.4f
private const val CHECK_LEFT = 0.5f
private const val CHECK_MID_X = 0.12f
private const val CHECK_MID_Y = 0.38f
private const val CHECK_RIGHT_X = 0.55f
private const val CHECK_RIGHT_Y = 0.42f
private const val CHECK_SPLIT = 0.35f

private val SLIM_TRACK = 16.dp
private val SLIM_TRACK_INSET = 3.dp
private val SLIM_RING = 2.dp
private const val SLIM_OFF_TRACK = 0.2f
private const val SLIM_OFF_RING = 0.4f
private const val SLIM_HALO = 0.18f
private const val SLIM_HALO_SCALE = 1.55f
private const val SLIM_DARK_THUMB = 0xFF2C2C2F

private fun DrawScope.drawOutlineSwitch(position: Float, press: Float, paint: SwitchPaint) {
    val t = position.coerceIn(0f, 1f)
    val radius = CornerRadius(size.height / 2)
    if (t > 0f) drawRoundRect(paint.on.copy(alpha = t), cornerRadius = radius)
    val stroke = OUTLINE_STROKE.toPx()
    if (t < 1f) {
        drawRoundRect(
            paint.ink.copy(alpha = OUTLINE_OFF_BORDER * (1f - t)),
            Offset(stroke / 2, stroke / 2),
            Size(size.width - stroke, size.height - stroke),
            CornerRadius(size.height / 2 - stroke / 2),
            style = Stroke(stroke),
        )
    }
    val thumb = OUTLINE_THUMB.toPx()
    val inset = (size.height - thumb) / 2
    val width = thumb * (1f + OUTLINE_STRETCH * press)
    val start = inset + (size.width - inset * 2 - width) * position
    val left = if (layoutDirection == LayoutDirection.Rtl) size.width - start - width else start
    val color = lerp(paint.ink.copy(alpha = OUTLINE_OFF_THUMB), if (paint.dark) Color.Black else Color.White, t)
    drawRoundRect(color, Offset(left, inset), Size(width, thumb), CornerRadius(thumb / 2))
}

private fun DrawScope.drawBlockSwitch(position: Float, press: Float, paint: SwitchPaint) {
    val t = position.coerceIn(0f, 1f)
    val stroke = paint.border.toPx().coerceAtLeast(1f)
    val drop = BLOCK_DROP.toPx()
    val track = Size(size.width - drop, size.height - drop)
    drawRect(paint.ink, Offset(drop, drop), track)
    drawRect(lerp(paint.fill, paint.on, t), Offset.Zero, track)
    drawRect(paint.ink, Offset(stroke / 2, stroke / 2), Size(track.width - stroke, track.height - stroke), style = Stroke(stroke))
    val inset = BLOCK_INSET.toPx() + stroke
    val thumb = track.height - inset * 2
    val width = thumb * (1f + BLOCK_STRETCH * press)
    val start = inset + (track.width - inset * 2 - width) * position
    val left = if (layoutDirection == LayoutDirection.Rtl) track.width - start - width else start
    drawRect(paint.fill, Offset(left, inset), Size(width, thumb))
    drawRect(paint.ink, Offset(left + stroke / 2, inset + stroke / 2), Size(width - stroke, thumb - stroke), style = Stroke(stroke))
}

private val OUTLINE_STROKE = 1.dp
private val OUTLINE_THUMB = 12.dp
private const val OUTLINE_STRETCH = 0.3f
private const val OUTLINE_OFF_BORDER = 0.62f
private const val OUTLINE_OFF_THUMB = 0.72f
private val BLOCK_DROP = 3.dp
private val BLOCK_INSET = 3.dp
private const val BLOCK_STRETCH = 0.2f
