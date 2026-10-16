package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [active]인 동안 빛 한 줄기가 꼬리를 끌며 요소 테두리를 따라 돈다. 카드나 버튼, 입력란이 일을 처리하는 중임을 알릴 때 붙인다.
 * 테두리 길이를 따라 같은 속도로 움직이므로 긴 카드에서도 모서리에서 빨라지지 않는다. 켜고 끌 때 서서히 나타나고 사라지며,
 * 꺼져 있으면 아무것도 그리지 않고 시계도 멈춘다. 요소가 화면 밖으로 스크롤되어도 멈춘다.
 *
 * '애니메이션 삭제' 설정이 켜져 있으면 돌지 않고 테두리 전체를 [color]로 고르게 칠한다.
 * 진행 상태를 TalkBack에 알리지는 않으므로 요소 쪽에서 `stateDescription` 등으로 알린다.
 *
 * ```kotlin
 * KCard(Modifier.borderSpinner(syncing, RoundedCornerShape(24.dp))) { … }
 * ```
 *
 * @param shape 요소의 모양. 테두리는 이 모양의 윤곽을 따라 그리므로 요소 배경 모양과 같게 넘긴다.
 * @param width 선 두께
 * @param periodMs 한 바퀴 도는 시간(ms). 0 이하면 [IllegalArgumentException]을 던진다.
 * @param tail 꼬리 길이. 테두리 전체 길이에 대한 비율(0.05..0.9)이고, 범위를 벗어나면 가까운 끝 값으로 맞춘다.
 */
@Composable
public fun Modifier.borderSpinner(
    active: Boolean,
    shape: Shape = RectangleShape,
    color: Color = KineticaTheme.colors.accent,
    width: Dp = 2.dp,
    periodMs: Int = 1_600,
    tail: Float = 0.3f,
): Modifier {
    require(periodMs > 0) { "periodMs는 0보다 커야 합니다." }
    val still = reducedMotion()
    val level = animateFloatAsState(if (active) 1f else 0f, if (still) snap() else tween(FADE_MS), label = "border-spinner")
    val clock = rememberLoopClock(running = active && !still)
    val tailFraction = tail.coerceIn(MIN_TAIL, MAX_TAIL)
    return this
        .loopVisibility(clock)
        .drawWithCache {
            val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val measure = PathMeasure().apply { setPath(outline, false) }
            val length = measure.length
            val track = Perimeter(measure, Path(), length)
            val stroke = Stroke(width.toPx(), cap = StrokeCap.Round)
            // 꼬리 조각끼리 둥근 끝이 겹치면 구슬처럼 진해지므로 꼬리는 평평한 끝으로 잇고 머리만 둥글게 한다.
            val tailStroke = Stroke(width.toPx(), cap = StrokeCap.Butt)
            onDrawWithContent {
                drawContent()
                val l = level.value
                if (l <= 0f || length <= 0f) return@onDrawWithContent
                if (still) {
                    drawPath(outline, color.copy(alpha = color.alpha * l), style = stroke)
                    return@onDrawWithContent
                }
                drawPath(outline, color.copy(alpha = color.alpha * TRACK_ALPHA * l), style = stroke)
                val head = (clock.seconds * MS_PER_SECOND / periodMs % 1f) * length
                val piece = length * tailFraction / TAIL_PIECES
                for (k in 0 until TAIL_PIECES) {
                    val alpha = color.alpha * l * (1f - k.toFloat() / TAIL_PIECES)
                    drawSpan(track, head - piece * (k + 1), head - piece * k, color.copy(alpha = alpha), if (k == 0) stroke else tailStroke)
                }
            }
        }
}

/** 테두리 둘레. [segment]는 구간을 잘라 담을 때마다 비우고 다시 쓰는 경로다. */
private class Perimeter(val measure: PathMeasure, val segment: Path, val length: Float)

/** 둘레 위 [from]..[to] 구간을 그린다. 시작점(0)을 지나는 구간은 두 번에 나눠 그린다. */
private fun DrawScope.drawSpan(track: Perimeter, from: Float, to: Float, color: Color, stroke: Stroke) {
    if (from < 0f) {
        drawPiece(track, track.length + from, track.length, color, stroke)
        drawPiece(track, 0f, to.coerceAtLeast(0f), color, stroke)
    } else {
        drawPiece(track, from, to, color, stroke)
    }
}

private fun DrawScope.drawPiece(track: Perimeter, from: Float, to: Float, color: Color, stroke: Stroke) {
    if (to <= from) return
    track.segment.reset()
    if (track.measure.getSegment(from, to, track.segment, true)) drawPath(track.segment, color, style = stroke)
}

private const val FADE_MS = 260
private const val MIN_TAIL = 0.05f
private const val MAX_TAIL = 0.9f
private const val TAIL_PIECES = 10
private const val TRACK_ALPHA = 0.14f
private const val MS_PER_SECOND = 1_000f
