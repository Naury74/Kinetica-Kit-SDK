package com.naury.kinetica.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 칸으로 나뉜 진행 막대. 인스타그램 스토리처럼 여러 장을 차례로 넘기는 화면 위쪽에 둔다. 앞 칸은 다 차 있고, 지금 칸이 [progress]만큼 차며,
 * 뒤 칸은 비어 있다. 값을 따라가는 애니메이션은 없으므로 호출한 쪽이 매 프레임 값을 넘겨 부드럽게 채운다. RTL에서는 오른쪽부터 찬다.
 *
 * ```kotlin
 * KSegmentedProgressBar((page + pageProgress) / pages, segments = pages)
 * ```
 *
 * @param progress 전체 진행 비율(0..1). 범위를 벗어나면 가까운 끝 값으로, NaN은 0으로 본다.
 * @param segments 칸 수. 1 미만이면 [IllegalArgumentException]을 던진다.
 * @param color 찬 부분 색. 사진 위에 놓는다면 흰색을 넘긴다.
 * @param gap 칸 사이 간격
 */
@Composable
public fun KSegmentedProgressBar(
    progress: Float,
    segments: Int,
    modifier: Modifier = Modifier,
    color: Color = KineticaTheme.colors.accent,
    thickness: Dp = 4.dp,
    gap: Dp = 4.dp,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(segments >= 1) { "segments는 1 이상이어야 합니다." }
    val level = fraction(progress)
    val track = trackColor(color, surface)
    Spacer(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(level, 0f..1f) }
            .drawBehind {
                val g = gap.toPx()
                val w = ((size.width - g * (segments - 1)) / segments).coerceAtLeast(0f)
                val r = CornerRadius(size.height / 2)
                mirror {
                    repeat(segments) { i ->
                        val x = i * (w + g)
                        drawRoundRect(track, Offset(x, 0f), Size(w, size.height), r)
                        val fill = (level * segments - i).coerceIn(0f, 1f)
                        if (fill > 0f) drawRoundRect(color, Offset(x, 0f), Size(w * fill, size.height), r)
                    }
                }
            },
    )
}

/**
 * 버퍼가 있는 진행 막대. 동영상이나 음악처럼 재생 위치([progress]) 앞에 미리 받아 둔 구간([buffer])이 있을 때 쓴다.
 * 버퍼 구간은 찬 색을 옅게 그린다. 두 값 모두 용수철처럼 따라가며('애니메이션 삭제'면 바로 바뀐다) RTL에서는 오른쪽부터 찬다.
 * TalkBack은 재생 위치 비율을 읽는다.
 *
 * @param progress 재생 위치 비율(0..1). 범위를 벗어나면 가까운 끝 값으로, NaN은 0으로 본다.
 * @param buffer 받아 둔 구간 끝 비율(0..1). [progress]보다 작으면 [progress]로 본다.
 */
@Composable
public fun KBufferProgressBar(
    progress: Float,
    buffer: Float,
    modifier: Modifier = Modifier,
    color: Color = KineticaTheme.colors.accent,
    thickness: Dp = 6.dp,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val level = fraction(progress)
    val still = reducedMotion()
    val shown = animateFloatAsState(level, if (still) snap() else spring(FOLLOW_DAMPING, Spring.StiffnessLow), label = "buffer-progress")
    val loaded =
        animateFloatAsState(
            maxOf(fraction(buffer), level),
            if (still) snap() else spring(FOLLOW_DAMPING, Spring.StiffnessLow),
            label = "buffer",
        )
    val track = trackColor(color, surface)
    val bufferColor = color.copy(alpha = color.alpha * BUFFER_ALPHA)
    Spacer(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(level, 0f..1f) }
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                mirror {
                    drawRoundRect(track, cornerRadius = r)
                    drawRoundRect(bufferColor, size = Size(size.width * maxOf(loaded.value, shown.value), size.height), cornerRadius = r)
                    drawRoundRect(color, size = Size(size.width * shown.value, size.height), cornerRadius = r)
                }
            },
    )
}

/**
 * 단계 진행 막대. 가입이나 결제처럼 단계가 정해진 흐름에서 지금 몇 단계인지 막대와 점으로 보여 준다. 단계 이름까지 보여야 하면
 * 단계 표시 컴포넌트를 쓰고, 이 막대는 화면 위쪽에 얇게 둘 때 쓴다. 지난 단계 점은 채우고 지금 단계 점은 테두리를 둘러 키운다.
 * 단계를 옮기면 막대가 용수철처럼 늘어난다('애니메이션 삭제'면 바로 바뀐다). TalkBack은 "5단계 중 2단계"처럼 읽는다.
 *
 * @param step 지금 단계(1부터). 1..[steps] 밖이면 가까운 끝 값으로 본다.
 * @param steps 전체 단계 수. 2 미만이면 [IllegalArgumentException]을 던진다.
 * @param thickness 막대 두께. 점 지름은 두께의 2.5배다.
 */
@Composable
public fun KStepProgressBar(
    step: Int,
    steps: Int,
    modifier: Modifier = Modifier,
    color: Color = KineticaTheme.colors.accent,
    thickness: Dp = 4.dp,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(steps >= 2) { "steps는 2 이상이어야 합니다." }
    val current = step.coerceIn(1, steps)
    val still = reducedMotion()
    val shown = animateFloatAsState(
        (current - 1f) / (steps - 1),
        if (still) snap() else spring(STEP_DAMPING, Spring.StiffnessMediumLow),
        label = "step-progress",
    )
    val track = trackColor(color, surface)
    val hole = surface.inkBackground()
    val description = stringResource(R.string.kinetica_ui_feedback_step, current, steps)
    Spacer(
        modifier
            .fillMaxWidth()
            .height(thickness * DOT_SCALE + RING_ROOM)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), 1f..steps.toFloat(), steps - 2)
                stateDescription = description
            }
            .drawBehind { mirror { drawSteps(StepLook(current, steps, shown.value, color, track, hole), thickness.toPx()) } },
    )
}

private class StepLook(val current: Int, val steps: Int, val level: Float, val color: Color, val track: Color, val hole: Color)

private fun DrawScope.drawSteps(look: StepLook, bar: Float) {
    val dot = bar * DOT_SCALE / 2
    val cy = size.height / 2
    val start = dot + RING_ROOM.toPx() / 2
    val length = (size.width - start * 2).coerceAtLeast(0f)
    val r = CornerRadius(bar / 2)
    drawRoundRect(look.track, Offset(start, cy - bar / 2), Size(length, bar), r)
    drawRoundRect(look.color, Offset(start, cy - bar / 2), Size(length * look.level, bar), r)
    repeat(look.steps) { i ->
        val center = Offset(start + length * i / (look.steps - 1), cy)
        val reached = i.toFloat() / (look.steps - 1) <= look.level + REACH_EPSILON
        when {
            i + 1 == look.current -> {
                drawCircle(look.color, dot + RING_ROOM.toPx() / 2, center)
                drawCircle(look.hole, dot * HOLE, center)
            }

            reached -> drawCircle(look.color, dot, center)

            else -> {
                drawCircle(look.hole, dot, center)
                drawCircle(look.track, dot - bar * RING_WIDTH / 2, center, style = Stroke(bar * RING_WIDTH))
            }
        }
    }
}

/** 진행 비율을 0..1로 맞춘다. NaN은 0으로 본다. */
private fun fraction(value: Float): Float = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)

/** 트랙 색. 찬 색이 흰색처럼 밝은데 표면이 밝으면 트랙이 안 보이므로 표면 글자 색 쪽으로 옅게 깐다. */
private fun trackColor(color: Color, surface: SurfaceStyle): Color = if (contrast(color, surface.inkBackground()) <
    TRACK_CONTRAST
) {
    surface.contentColor.copy(alpha = TRACK_ALPHA)
} else {
    color.copy(alpha = TRACK_ALPHA)
}

private inline fun DrawScope.mirror(block: DrawScope.() -> Unit) {
    if (layoutDirection == LayoutDirection.Rtl) scale(-1f, 1f) { block() } else block()
}

private const val TRACK_ALPHA = 0.18f
private const val TRACK_CONTRAST = 1.5f
private const val BUFFER_ALPHA = 0.35f
private const val FOLLOW_DAMPING = 0.85f
private const val STEP_DAMPING = 0.75f
private const val DOT_SCALE = 2.5f
private val RING_ROOM = 4.dp
private const val HOLE = 0.45f
private const val RING_WIDTH = 0.5f
private const val REACH_EPSILON = 0.001f
