// 파일 이름은 대표 컴포저블인 KCheckbox를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** [KCheckbox]의 모양. */
public enum class KCheckboxShape {
    /** 모서리가 둥근 사각형. 가장 흔한 체크박스 모양이다. */
    Square,

    /** 원. 목록의 선택 표시나 할 일 앱의 완료 표시에 어울린다. */
    Circle,
}

/**
 * 체크박스. 켜지면 상자가 [tone] 색으로 차오르고 체크 표시가 왼쪽 아래에서 오른쪽 위로 그려진다. 끄면 표시가 빠르게 지워진다.
 * '애니메이션 삭제' 접근성 설정이 켜져 있으면 바로 바뀐다.
 *
 * [onCheckedChange]가 `null`이면 누를 수 없는 표시만 그린다. [KListItem]이나 직접 만든 행 전체를 `toggleable`로 만들고
 * 그 안에 넣을 때 이렇게 쓴다. 그 밖에는 48dp 터치 영역을 갖고 TalkBack에 체크박스로 읽힌다.
 *
 * ```kotlin
 * KCheckbox(agreed, { agreed = it })
 * ```
 *
 * @param shape 상자 모양
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 * @param tone 켜졌을 때 채우는 색 역할. [KTone.Neutral]이면 표면 글자 색으로 채운다.
 * @param surface 꺼진 상자의 테두리 색을 정하는 표면. 상자 자체는 표면을 칠하지 않는다.
 */
@Composable
public fun KCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    shape: KCheckboxShape = KCheckboxShape.Square,
    enabled: Boolean = true,
    tone: KTone = KTone.Accent,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    KTriStateCheckbox(
        state = if (checked) ToggleableState.On else ToggleableState.Off,
        onClick = onCheckedChange?.let { change -> { change(!checked) } },
        modifier = modifier,
        shape = shape,
        enabled = enabled,
        tone = tone,
        surface = surface,
    )
}

/**
 * 세 상태 체크박스. [ToggleableState.Indeterminate]면 체크 대신 가로 막대를 그린다. 하위 항목 일부만 고른 '모두 선택' 칸에 쓴다.
 * 누를 때 다음 상태는 [onClick]에서 호출한 쪽이 정한다. 보통 미정이나 꺼짐에서는 모두 켜고, 켜짐에서는 모두 끈다.
 *
 * @param onClick 눌렸을 때. `null`이면 누를 수 없는 표시만 그린다.
 * @see KCheckbox
 */
@Composable
public fun KTriStateCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: KCheckboxShape = KCheckboxShape.Square,
    enabled: Boolean = true,
    tone: KTone = KTone.Accent,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val colors = tone.colors(surface)
    val motion = rememberCheckMotion(state)
    val interactive = onClick != null
    Box(
        modifier
            .size(if (interactive) KMetrics.Touch else CHOICE_BOX + CHOICE_PAD)
            .then(
                if (onClick != null) {
                    Modifier.triStateToggleable(
                        state,
                        interactionSource = null,
                        indication = KIndication(CenteredCircleShape(CHOICE_HALO_INSET), colors.color, CHOICE_HALO_ALPHA),
                        enabled = enabled,
                        role = Role.Checkbox,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .graphicsLayer { alpha = if (enabled) 1f else CHOICE_DISABLED_ALPHA }
            .checkDrawing(motion, shape, colors, surface.contentColor),
    )
}

/** 상자 채움과 표시 획 진행을 그리기 단계에서 읽어 그린다. Path는 크기가 바뀔 때만 새로 만든다. */
private fun Modifier.checkDrawing(motion: CheckMotion, shape: KCheckboxShape, colors: ToneColors, ink: Color): Modifier = drawWithCache {
    val box = CHOICE_BOX.toPx()
    val origin = Offset((size.width - box) / 2, (size.height - box) / 2)
    val corner = if (shape == KCheckboxShape.Circle) box / 2 else CHECK_CORNER.toPx()
    val border = CHOICE_BORDER.toPx()
    val check = Path().apply {
        moveTo(origin.x + box * CHECK_A_X, origin.y + box * CHECK_A_Y)
        lineTo(origin.x + box * CHECK_B_X, origin.y + box * CHECK_B_Y)
        lineTo(origin.x + box * CHECK_C_X, origin.y + box * CHECK_C_Y)
    }
    val dash = Path().apply {
        moveTo(origin.x + box * DASH_FROM, origin.y + box / 2)
        lineTo(origin.x + box * (1 - DASH_FROM), origin.y + box / 2)
    }
    val paths = MarkPaths(check, dash)
    val mark = Stroke(MARK_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val idle = ink.copy(alpha = CHOICE_IDLE_ALPHA)
    onDrawBehind {
        val fill = motion.fill.value
        if (fill < 1f) {
            drawRoundRect(
                lerp(idle, colors.color, fill),
                origin + Offset(border / 2, border / 2),
                Size(box - border, box - border),
                CornerRadius(corner - border / 2),
                style = Stroke(border),
            )
        }
        if (fill > 0f) {
            scale(FILL_FROM + (1 - FILL_FROM) * fill, origin + Offset(box / 2, box / 2)) {
                drawRoundRect(colors.color.copy(alpha = fill.coerceIn(0f, 1f)), origin, Size(box, box), CornerRadius(corner))
            }
        }
        drawMark(motion, paths, mark, colors.onColor)
    }
}

/** 체크와 미정 막대의 전체 경로, 그리고 그 일부를 잘라 담을 재사용 경로 */
private class MarkPaths(val check: Path, val dash: Path) {
    val measure = PathMeasure()
    val partial = Path()
}

private fun DrawScope.drawMark(motion: CheckMotion, paths: MarkPaths, stroke: Stroke, color: Color) {
    val progress = motion.mark.value.coerceIn(0f, 1f)
    if (progress <= 0f) return
    paths.measure.setPath(if (motion.dash) paths.dash else paths.check, false)
    paths.partial.rewind()
    paths.measure.getSegment(0f, paths.measure.length * progress, paths.partial, true)
    drawPath(paths.partial, color, style = stroke)
}

/** 체크박스 애니메이션 값. [dash]는 지금 그리는 표시가 미정 막대인지다. */
private class CheckMotion(state: ToggleableState) {
    val fill = Animatable(if (state == ToggleableState.Off) 0f else 1f)
    val mark = Animatable(if (state == ToggleableState.Off) 0f else 1f)
    var dash by mutableStateOf(state == ToggleableState.Indeterminate)
}

@Composable
private fun rememberCheckMotion(state: ToggleableState): CheckMotion {
    val motion = remember { CheckMotion(state) }
    val still = reducedMotion()
    LaunchedEffect(state, still) {
        val on = state != ToggleableState.Off
        if (still) {
            if (on) motion.dash = state == ToggleableState.Indeterminate
            motion.fill.snapTo(if (on) 1f else 0f)
            motion.mark.snapTo(if (on) 1f else 0f)
            return@LaunchedEffect
        }
        coroutineScope {
            if (on) {
                val dash = state == ToggleableState.Indeterminate
                // 체크와 막대 사이를 오가면 이전 표시를 지운 뒤 새 표시를 처음부터 그린다.
                if (motion.dash != dash && motion.mark.value > 0f) motion.mark.animateTo(0f, tween(ERASE_MS))
                motion.dash = dash
                launch { motion.fill.animateTo(1f, spring(dampingRatio = FILL_DAMPING, stiffness = Spring.StiffnessMedium)) }
                launch { motion.mark.animateTo(1f, tween(DRAW_MS, DRAW_DELAY_MS, FastOutSlowInEasing)) }
            } else {
                launch { motion.mark.animateTo(0f, tween(ERASE_MS)) }
                launch { motion.fill.animateTo(0f, tween(EMPTY_MS, ERASE_MS / 2)) }
            }
        }
    }
    return motion
}

/** 체크박스와 라디오가 함께 쓰는 치수와 값 */
internal val CHOICE_BOX: Dp = 20.dp
internal val CHOICE_PAD: Dp = 4.dp
internal val CHOICE_BORDER: Dp = 2.dp
internal val CHOICE_HALO_INSET: Dp = 4.dp
internal const val CHOICE_HALO_ALPHA: Float = 0.12f
internal const val CHOICE_IDLE_ALPHA: Float = 0.42f
internal const val CHOICE_DISABLED_ALPHA: Float = 0.38f

private val CHECK_CORNER = 6.dp
private val MARK_WIDTH = 2.2.dp
private const val CHECK_A_X = 0.26f
private const val CHECK_A_Y = 0.52f
private const val CHECK_B_X = 0.43f
private const val CHECK_B_Y = 0.68f
private const val CHECK_C_X = 0.75f
private const val CHECK_C_Y = 0.34f
private const val DASH_FROM = 0.28f
private const val FILL_FROM = 0.6f
private const val FILL_DAMPING = 0.55f
private const val DRAW_MS = 220
private const val DRAW_DELAY_MS = 40
private const val ERASE_MS = 90
private const val EMPTY_MS = 140
