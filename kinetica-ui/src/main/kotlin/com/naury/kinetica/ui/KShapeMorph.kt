package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.flow.collectLatest

/**
 * 누르는 동안 모양이 바뀌는 아이콘 버튼(Material 3 Expressive 방식). 기본으로 원이었다가 누르면 둥근 사각형으로 이어서 변하고,
 * 떼면 용수철처럼 돌아온다. 빠르게 탭해도 모양이 바뀌는 것이 보이도록 끝까지 한 번은 변했다가 돌아온다.
 * 모양은 그리기 단계에서만 바뀌어 다시 구성하지 않는다. 누르면 햅틱을 주고, TalkBack은 버튼으로 읽는다.
 * 키보드 초점이 오면 테두리에 고리를 두른다. '애니메이션 삭제' 설정에서는 바로 바뀐다.
 *
 * ```kotlin
 * KShapeMorphButton(KIcons.Send, "보내기", onClick = ::send, pressedShape = KMorphShape.Cookie9)
 * ```
 *
 * @param contentDescription TalkBack이 읽을 이름
 * @param shape 평소 모양
 * @param pressedShape 눌렀을 때 모양
 * @param size 버튼 한 변. 32dp보다 작으면 32dp로 맞춘다. 아이콘은 한 변의 40% 크기로 그린다.
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 */
@Composable
public fun KShapeMorphButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: KMorphShape = KMorphShape.Circle,
    pressedShape: KMorphShape = KMorphShape.Square,
    tone: KTone = KTone.Accent,
    size: Dp = 56.dp,
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val side = size.coerceAtLeast(MIN_SIDE)
    val haptic = kHapticFeedback()
    val colors = tone.colors(surface)
    val interaction = remember { MutableInteractionSource() }
    val morph = rememberPressMorph(interaction)
    val look = remember(shape, pressedShape) { MorphLook(shape, pressedShape) }
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .size(side)
            .onFocusChanged { focused = it.isFocused }
            .drawBehind {
                look.fraction = morph.value
                drawMorphShape(look, colors.color)
                if (focused) drawMorphFocus(look, colors.color)
            }
            .clickable(interaction, null, enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(rememberVectorPainter(icon), null, Modifier.size(side * ICON_SHARE), colorFilter = ColorFilter.tint(colors.onColor))
    }
}

/**
 * 켜고 끄는 모양 바뀜 버튼. 꺼져 있을 때는 [uncheckedShape] 모양에 옅은 색이고, 켜면 [tone] 색으로 채워지며 [checkedShape] 모양으로
 * 이어서 변한다. 누르는 동안 살짝 줄어든다. 아이콘은 [checkedIcon]으로 바뀐다. 모양과 색은 그리기 단계에서만 바뀐다.
 * TalkBack은 체크박스처럼 '선택됨'과 '선택 안 됨'을 읽는다. '애니메이션 삭제' 설정에서는 바로 바뀐다.
 *
 * ```kotlin
 * KShapeToggle(liked, { liked = it }, KIcons.Heart, "좋아요", checkedShape = KMorphShape.Cookie9)
 * ```
 *
 * @param contentDescription TalkBack이 읽을 이름. 상태는 따로 읽으므로 동작 이름만 쓴다.
 * @param checkedIcon 켜졌을 때 아이콘. null이면 [icon]을 그대로 쓴다.
 * @param uncheckedShape 꺼졌을 때 모양
 * @param checkedShape 켜졌을 때 모양
 * @param size 버튼 한 변. 32dp보다 작으면 32dp로 맞춘다.
 */
@Composable
public fun KShapeToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    checkedIcon: ImageVector? = null,
    uncheckedShape: KMorphShape = KMorphShape.Circle,
    checkedShape: KMorphShape = KMorphShape.Cookie4,
    tone: KTone = KTone.Accent,
    size: Dp = 56.dp,
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val side = size.coerceAtLeast(MIN_SIDE)
    val haptic = kHapticFeedback()
    val on = tone.colors(surface)
    val offFill = surface.raisedFill(OFF_FILL_ALPHA)
    val offInk = surface.contentColor
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressMorph(interaction)
    val level = rememberCheckMorph(checked)
    val look = remember(uncheckedShape, checkedShape) { MorphLook(uncheckedShape, checkedShape) }
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .graphicsLayer {
                alpha = if (enabled) 1f else DISABLED_ALPHA
                val squeeze = 1f - PRESS_SQUEEZE * press.value
                scaleX = squeeze
                scaleY = squeeze
            }
            .size(side)
            .onFocusChanged { focused = it.isFocused }
            .drawBehind {
                val v = level.value
                look.fraction = v
                val fill = lerp(offFill, on.color, v.coerceIn(0f, 1f))
                drawMorphShape(look, fill)
                if (focused) drawMorphFocus(look, on.color)
            }
            .toggleable(checked, interaction, null, enabled, Role.Checkbox) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onCheckedChange(it)
            }
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val iconSize = Modifier.size(side * ICON_SHARE)
        Image(
            rememberVectorPainter(icon),
            null,
            iconSize.graphicsLayer { alpha = (1f - level.value).coerceIn(0f, 1f) },
            colorFilter = ColorFilter.tint(offInk),
        )
        Image(
            rememberVectorPainter(checkedIcon ?: icon),
            null,
            iconSize.graphicsLayer { alpha = level.value.coerceIn(0f, 1f) },
            colorFilter = ColorFilter.tint(on.onColor),
        )
    }
}

/** 누름에 따라 0(평소)과 1(눌림) 사이를 오가는 값. 짧게 탭해도 [MIN_PEAK]까지는 갔다가 돌아온다. */
@Composable
private fun rememberPressMorph(interaction: MutableInteractionSource): Animatable<Float, *> {
    val morph = remember { Animatable(0f) }
    val still = reducedMotion()
    LaunchedEffect(interaction, still) {
        interaction.interactions.collectLatest { event ->
            when (event) {
                is PressInteraction.Press -> if (still) morph.snapTo(1f) else morph.animateTo(1f, spring(PRESS_DAMPING, PRESS_STIFFNESS))

                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    if (still) {
                        morph.snapTo(0f)
                    } else {
                        if (morph.value < MIN_PEAK) morph.animateTo(MIN_PEAK, tween(PEAK_MS))
                        morph.animateTo(0f, spring(RELEASE_DAMPING, RELEASE_STIFFNESS))
                    }
                }
            }
        }
    }
    return morph
}

@Composable
private fun rememberCheckMorph(checked: Boolean): Animatable<Float, *> {
    val level = remember { Animatable(if (checked) 1f else 0f) }
    val still = reducedMotion()
    LaunchedEffect(checked, still) {
        val target = if (checked) 1f else 0f
        if (still) level.snapTo(target) else level.animateTo(target, spring(RELEASE_DAMPING, RELEASE_STIFFNESS))
    }
    return level
}

/**
 * 두 모양과 그 사이 위치, 그것을 그릴 경로와 초점 선. [fraction]은 용수철이 넘친 값(0 미만, 1 초과)도 받아 살짝 지나쳤다 돌아오는 모양을 그린다.
 * 그리기 단계에서 [fraction]만 바꿔 다시 쓴다.
 */
private class MorphLook(val from: KMorphShape, val to: KMorphShape) {
    var fraction = 0f
    val path = Path()
    var focusStroke = Stroke(1f)
}

/** [look] 모양으로 [MorphLook.path]를 비워 다시 긋는다. */
private fun DrawScope.traceMorph(look: MorphLook) {
    val path = look.path
    val f = look.fraction
    val half = size.minDimension / 2
    path.reset()
    for (k in 0..MORPH_SAMPLES) {
        val a = k * TWO_PI / MORPH_SAMPLES
        val r = half * (look.from.radius(a) + (look.to.radius(a) - look.from.radius(a)) * f)
        val x = center.x + r * cos(a)
        val y = center.y + r * sin(a)
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}

private fun DrawScope.drawMorphShape(look: MorphLook, color: Color) {
    traceMorph(look)
    val path = look.path
    val turn = look.from.rotation + (look.to.rotation - look.from.rotation) * look.fraction
    rotate(turn) { drawPath(path, color) }
}

/** 키보드 초점 고리. 모양 바깥에 틈을 두고 같은 모양 선을 긋는다. */
private fun DrawScope.drawMorphFocus(look: MorphLook, color: Color) {
    val width = FOCUS_WIDTH.toPx()
    val grow = 1f + (FOCUS_GAP.toPx() + width / 2) * 2 / size.minDimension
    val turn = look.from.rotation + (look.to.rotation - look.from.rotation) * look.fraction
    if (look.focusStroke.width != width / grow) look.focusStroke = Stroke(width / grow)
    rotate(turn) { scale(grow) { drawPath(look.path, color, style = look.focusStroke) } }
}

private val MIN_SIDE = 32.dp
private val FOCUS_GAP = 3.dp
private val FOCUS_WIDTH = 2.dp
private const val ICON_SHARE = 0.4f
private const val DISABLED_ALPHA = 0.4f
private const val OFF_FILL_ALPHA = 0.12f
private const val PRESS_SQUEEZE = 0.08f
private const val MORPH_SAMPLES = 72
private const val TWO_PI = 6.2831855f
private const val MIN_PEAK = 0.85f
private const val PEAK_MS = 90
private const val PRESS_DAMPING = 0.7f
private const val PRESS_STIFFNESS = 900f
private const val RELEASE_DAMPING = 0.55f
private const val RELEASE_STIFFNESS = 420f
