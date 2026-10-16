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
    /** iOS 26 스위치. 누르는 동안 손잡이가 옆으로 늘어나고, 켜고 끌 때 출렁이며 넘어간다. 56×32dp. */
    Ios(56.dp, 32.dp),

    /** Material 3 스위치. 꺼지면 테두리 트랙에 작은 손잡이, 켜지면 손잡이가 커지며 안에 체크가 그려진다. 누르면 손잡이가 더 커진다. 52×32dp. */
    Material(52.dp, 32.dp),

    /** One UI 스위치. 얇은 트랙 위에 테두리 두른 큰 손잡이가 걸쳐 있고, 누르는 동안 손잡이 둘레에 옅은 원이 퍼진다. 50×28dp. */
    OneUi(50.dp, 28.dp),
}

/**
 * 스위치. 모양은 [variant]로 고른다. 켜지면 트랙이 [accent]로 바뀐다. 오른쪽에서 왼쪽으로 쓰는 언어에서는 켜진 손잡이가 왼쪽에 간다.
 * '애니메이션 삭제' 접근성 설정이 켜져 있으면 손잡이가 바로 옮겨 간다.
 *
 * [onCheckedChange]가 `null`이면 누를 수 없는 표시만 그린다. [KListItem]이나 [KSwitchRow]처럼 행 전체를 누르게 할 때 쓴다.
 *
 * ```kotlin
 * KSwitch(wifi, { wifi = it })
 * KSwitch(sync, { sync = it }, variant = KSwitchVariant.OneUi)
 * ```
 *
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 * @param accent 켜진 트랙 색. [Color.Unspecified]면 [KSwitchVariant.Ios]는 [KineticaColors.positive],
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
    variant: KSwitchVariant = KSwitchVariant.Ios,
) {
    val interaction = remember { MutableInteractionSource() }
    val motion = rememberSwitchMotion(checked, variant, interaction)
    val colors = KineticaTheme.colors
    val paint = SwitchPaint(
        on = if (accent.isSpecified) {
            accent
        } else if (variant == KSwitchVariant.Ios) {
            colors.positive
        } else {
            colors.accent
        },
        ink = surface.contentColor,
        dark = surface.isDark,
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
                    KSwitchVariant.Ios -> drawIosSwitch(position, press, paint)
                    KSwitchVariant.Material -> drawMaterialSwitch(position, press, paint)
                    KSwitchVariant.OneUi -> drawOneUiSwitch(position, press, paint)
                }
            },
    )
}

/** 스위치 색. [on]은 켜진 트랙, [ink]는 표면 글자 색이다. */
private class SwitchPaint(val on: Color, val ink: Color, val dark: Boolean)

/** 손잡이 위치(0..1, 출렁이면 범위를 조금 넘는다)와 눌림 정도(0..1) */
private class SwitchMotion(checked: Boolean) {
    val position = Animatable(if (checked) 1f else 0f)
    val press = Animatable(0f)
}

@Composable
private fun rememberSwitchMotion(checked: Boolean, variant: KSwitchVariant, interaction: MutableInteractionSource): SwitchMotion {
    val motion = remember { SwitchMotion(checked) }
    val still = reducedMotion()
    LaunchedEffect(checked, still) {
        val target = if (checked) 1f else 0f
        if (still) {
            motion.position.snapTo(target)
        } else {
            // iOS 스위치만 넘어갈 때 출렁인다. 다른 둘은 바로 멈춰야 손잡이가 트랙 밖으로 나가 보이지 않는다.
            val damping = if (variant == KSwitchVariant.Ios) IOS_DAMPING else Spring.DampingRatioNoBouncy
            motion.position.animateTo(target, spring(dampingRatio = damping, stiffness = Spring.StiffnessMediumLow))
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

private fun DrawScope.drawIosSwitch(position: Float, press: Float, paint: SwitchPaint) {
    val t = position.coerceIn(0f, 1f)
    drawRoundRect(lerp(paint.ink.copy(alpha = IOS_OFF_ALPHA), paint.on, t), cornerRadius = CornerRadius(size.height / 2))
    val inset = IOS_THUMB_INSET.toPx()
    val thumbHeight = size.height - inset * 2
    val thumbWidth = thumbHeight * (1f + IOS_STRETCH * press)
    val travel = size.width - inset * 2 - thumbWidth
    val start = inset + travel * position
    val left = if (layoutDirection == LayoutDirection.Rtl) size.width - start - thumbWidth else start
    val corner = CornerRadius(thumbHeight / 2)
    drawRoundRect(Color.Black.copy(alpha = THUMB_SHADOW), Offset(left, inset + THUMB_DROP.toPx()), Size(thumbWidth, thumbHeight), corner)
    drawRoundRect(Color.White, Offset(left, inset), Size(thumbWidth, thumbHeight), corner)
}

private fun DrawScope.drawMaterialSwitch(position: Float, press: Float, paint: SwitchPaint) {
    val t = position.coerceIn(0f, 1f)
    val radius = size.height / 2
    drawRoundRect(lerp(paint.ink.copy(alpha = M3_OFF_FILL), paint.on, t), cornerRadius = CornerRadius(radius))
    val border = M3_BORDER.toPx()
    if (t < 1f) {
        drawRoundRect(
            paint.ink.copy(alpha = M3_OFF_BORDER * (1f - t)),
            Offset(border / 2, border / 2),
            Size(size.width - border, size.height - border),
            CornerRadius(radius - border / 2),
            style = Stroke(border),
        )
    }
    val diameter = M3_THUMB_OFF.toPx() + (M3_THUMB_ON - M3_THUMB_OFF).toPx() * t
    val thumb = diameter + (M3_THUMB_PRESSED.toPx() - diameter) * press
    val center = Offset(mirrored(radius + (size.width - radius * 2) * t), size.height / 2)
    val thumbColor = lerp(paint.ink.copy(alpha = M3_OFF_THUMB), if (paint.dark) Color(M3_DARK_THUMB) else Color.White, t)
    drawCircle(thumbColor, thumb / 2, center)
    val draw = ((t - M3_CHECK_FROM) / (1f - M3_CHECK_FROM)).coerceIn(0f, 1f)
    if (draw > 0f) drawThumbCheck(center, M3_CHECK.toPx(), draw, paint.on)
}

/** 손잡이 안 체크. 두 획을 [progress]만큼 앞에서부터 그린다. 매 프레임 Path를 만들지 않도록 선 두 개로 그린다. */
private fun DrawScope.drawThumbCheck(center: Offset, size: Float, progress: Float, color: Color) {
    val a = center + Offset(-size * CHECK_LEFT, 0f)
    val b = center + Offset(-size * CHECK_MID_X, size * CHECK_MID_Y)
    val c = center + Offset(size * CHECK_RIGHT_X, -size * CHECK_RIGHT_Y)
    val stroke = M3_CHECK_STROKE.toPx()
    val first = (progress / CHECK_SPLIT).coerceAtMost(1f)
    drawLine(color, a, a + (b - a) * first, stroke, StrokeCap.Round)
    if (progress > CHECK_SPLIT) {
        val second = (progress - CHECK_SPLIT) / (1f - CHECK_SPLIT)
        drawLine(color, b, b + (c - b) * second, stroke, StrokeCap.Round)
    }
}

private fun DrawScope.drawOneUiSwitch(position: Float, press: Float, paint: SwitchPaint) {
    val t = position.coerceIn(0f, 1f)
    val trackHeight = ONE_TRACK.toPx()
    val inset = ONE_TRACK_INSET.toPx()
    val trackColor = lerp(paint.ink.copy(alpha = ONE_OFF_TRACK), paint.on, t)
    drawRoundRect(
        trackColor,
        Offset(inset, (size.height - trackHeight) / 2),
        Size(size.width - inset * 2, trackHeight),
        CornerRadius(trackHeight / 2),
    )
    val radius = size.height / 2
    val center = Offset(mirrored(radius + (size.width - radius * 2) * t), size.height / 2)
    if (press > 0f) drawCircle(trackColor.copy(alpha = ONE_HALO * press), radius * ONE_HALO_SCALE, center)
    val ring = ONE_RING.toPx()
    drawCircle(Color.Black.copy(alpha = THUMB_SHADOW), radius - ring / 2, center + Offset(0f, THUMB_DROP.toPx()))
    drawCircle(if (paint.dark) Color(ONE_DARK_THUMB) else Color.White, radius - ring / 2, center)
    drawCircle(lerp(paint.ink.copy(alpha = ONE_OFF_RING), paint.on, t), radius - ring / 2 - ring / 2, center, style = Stroke(ring))
}

private const val IOS_DAMPING = 0.62f
private val IOS_THUMB_INSET = 3.dp
private const val IOS_STRETCH = 0.35f
private const val IOS_OFF_ALPHA = 0.18f
private val THUMB_DROP = 1.dp
private const val THUMB_SHADOW = 0.15f

private const val M3_OFF_FILL = 0.08f
private const val M3_OFF_BORDER = 0.5f
private const val M3_OFF_THUMB = 0.55f
private const val M3_DARK_THUMB = 0xFF1C1B1F
private val M3_BORDER = 2.dp
private val M3_THUMB_OFF = 16.dp
private val M3_THUMB_ON = 24.dp
private val M3_THUMB_PRESSED = 28.dp
private val M3_CHECK = 10.dp
private val M3_CHECK_STROKE = 2.dp
private const val M3_CHECK_FROM = 0.4f
private const val CHECK_LEFT = 0.5f
private const val CHECK_MID_X = 0.12f
private const val CHECK_MID_Y = 0.38f
private const val CHECK_RIGHT_X = 0.55f
private const val CHECK_RIGHT_Y = 0.42f
private const val CHECK_SPLIT = 0.35f

private val ONE_TRACK = 16.dp
private val ONE_TRACK_INSET = 3.dp
private val ONE_RING = 2.dp
private const val ONE_OFF_TRACK = 0.2f
private const val ONE_OFF_RING = 0.4f
private const val ONE_HALO = 0.18f
private const val ONE_HALO_SCALE = 1.55f
private const val ONE_DARK_THUMB = 0xFF2C2C2F
