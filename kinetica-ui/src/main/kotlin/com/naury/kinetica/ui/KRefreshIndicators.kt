// KRefreshBox가 쓰는 새로 고침 표시들을 모은 파일이다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** [KRefreshBox]가 당길 때 보여 주는 표시. */
public enum class KRefreshIndicator {
    /** 글래스 물방울이 차오르고 임계값을 넘으면 길쭉해진다. */
    Drop,

    /** 원 화살표. 당기는 만큼 원호가 자라며 돌고, 새로 고치는 동안 원호가 늘었다 줄며 돈다. */
    Arrow,

    /** 점 세 개가 당기는 만큼 하나씩 채워지고, 새로 고치는 동안 차례로 튄다. */
    Dots,
}

/** [indicator] 모양의 표시를 그린다. [level]은 당긴 정도(1이면 임계값)다. */
@Composable
internal fun RefreshIndicator(
    indicator: KRefreshIndicator,
    level: () -> Float,
    refreshing: Boolean,
    surface: SurfaceStyle,
    label: String,
    modifier: Modifier,
) {
    when (indicator) {
        KRefreshIndicator.Drop -> RefreshDrop(level(), refreshing, surface, label, modifier)
        KRefreshIndicator.Arrow -> RefreshArrow(level, refreshing, surface, label, modifier)
        KRefreshIndicator.Dots -> RefreshDots(level, refreshing, surface, label, modifier)
    }
}

/** 원 화살표 표시. [level]은 당긴 정도(1이면 임계값)이고 그리기 단계에서만 읽는다. */
@Composable
internal fun RefreshArrow(level: () -> Float, refreshing: Boolean, surface: SurfaceStyle, label: String, modifier: Modifier) {
    val still = reducedMotion()
    val clock = rememberLoopClock(running = refreshing && !still)
    val color = KineticaTheme.colors.accent
    Box(
        modifier
            .size(ARROW_SIZE)
            .loopVisibility(clock)
            .appearByPull(level, refreshing)
            .kSurface(surface, CircleShape)
            .semantics { if (refreshing) contentDescription = label }
            .drawWithCache {
                val stroke = Stroke(ARROW_STROKE.toPx(), cap = StrokeCap.Round)
                val head = Path()
                val inset = size.minDimension * ARROW_INSET
                val arc = Size(size.width - inset * 2, size.height - inset * 2)
                onDrawBehind {
                    val l = level().coerceAtLeast(0f)
                    val topLeft = Offset(inset, inset)
                    when {
                        refreshing && still -> drawArc(color, -QUARTER_TURN, STILL_SWEEP, false, topLeft, arc, style = stroke)

                        refreshing -> {
                            val f = clock.seconds / SPIN_SECONDS % 1f
                            val sweep = SPIN_MIN + SPIN_EXTRA * sin(f * PI.toFloat())
                            drawArc(color, f * FULL_TURN * 2 - QUARTER_TURN, sweep, false, topLeft, arc, style = stroke)
                        }

                        else -> {
                            val grow = l.coerceAtMost(1f)
                            val start = -QUARTER_TURN + l * PULL_TURN
                            val sweep = PULL_SWEEP * grow
                            val ink = color.copy(alpha = PULL_ALPHA + (1f - PULL_ALPHA) * grow)
                            drawArc(ink, start, sweep, false, topLeft, arc, style = stroke)
                            drawArrowHead(head, start + sweep, arc.width / 2, grow, ink)
                        }
                    }
                }
            },
    )
}

/** 원호 끝([angle]도)에 진행 방향을 가리키는 삼각형 화살촉을 그린다. 경로는 [head]를 비우고 다시 쓴다. */
private fun DrawScope.drawArrowHead(head: Path, angle: Float, radius: Float, grow: Float, color: Color) {
    if (grow <= 0f) return
    val a = angle * PI.toFloat() / HALF_TURN
    val p = Offset(center.x + radius * cos(a), center.y + radius * sin(a))
    val normal = Offset(cos(a), sin(a))
    val tangent = Offset(-sin(a), cos(a))
    val h = HEAD_LENGTH.toPx() * grow
    val w = HEAD_WIDTH.toPx() * grow
    head.reset()
    head.moveTo(p.x + tangent.x * h, p.y + tangent.y * h)
    head.lineTo(p.x + normal.x * w, p.y + normal.y * w)
    head.lineTo(p.x - normal.x * w, p.y - normal.y * w)
    head.close()
    drawPath(head, color)
}

/** 점 세 개 표시. 당긴 만큼 점이 하나씩 차오르고, 새로 고치는 동안 차례로 튄다('애니메이션 삭제'면 셋 다 찬 채 멈춘다). */
@Composable
internal fun RefreshDots(level: () -> Float, refreshing: Boolean, surface: SurfaceStyle, label: String, modifier: Modifier) {
    val still = reducedMotion()
    val clock = rememberLoopClock(running = refreshing && !still)
    val color = KineticaTheme.colors.accent
    Box(
        modifier
            .size(DOTS_WIDTH, DOTS_HEIGHT)
            .loopVisibility(clock)
            .appearByPull(level, refreshing)
            .kSurface(surface, CircleShape)
            .semantics { if (refreshing) contentDescription = label }
            .drawWithCache {
                val r = DOT_RADIUS.toPx()
                val gap = DOT_GAP.toPx()
                val hop = DOT_HOP.toPx()
                onDrawBehind {
                    val l = level().coerceAtLeast(0f)
                    repeat(DOT_COUNT) { i ->
                        val x = center.x + (i - 1) * gap
                        if (refreshing) {
                            val phase = if (still) 1f else (clock.seconds / BOUNCE_SECONDS - i * BOUNCE_DELAY).mod(1f)
                            val up = if (!still && phase < HALF) sin(phase / HALF * PI.toFloat()) else 0f
                            drawCircle(color, r, Offset(x, center.y - up * hop))
                        } else {
                            val fill = (l * DOT_COUNT - i).coerceIn(0f, 1f)
                            drawCircle(color.copy(alpha = EMPTY_ALPHA), r, Offset(x, center.y))
                            if (fill >
                                0f
                            ) {
                                drawCircle(color.copy(alpha = fill), r * (EMPTY_SCALE + (1f - EMPTY_SCALE) * fill), Offset(x, center.y))
                            }
                        }
                    }
                }
            },
    )
}

/** 당긴 만큼 커지며 나타나고, 새로 고치는 동안은 다 보인다. 위로 반만큼 올려 당김 거리의 가운데에 걸리게 한다. */
private fun Modifier.appearByPull(level: () -> Float, refreshing: Boolean): Modifier = graphicsLayer {
    val grow = level().coerceIn(0f, 1f)
    val shown = if (refreshing) 1f else grow
    translationY = -size.height / 2
    alpha = shown
    val scale = MIN_SCALE + (1f - MIN_SCALE) * shown
    scaleX = scale
    scaleY = scale
}

private val ARROW_SIZE = 40.dp
private val ARROW_STROKE = 2.5.dp
private val HEAD_LENGTH = 5.dp
private val HEAD_WIDTH = 4.dp
private const val ARROW_INSET = 0.27f
private const val QUARTER_TURN = 90f
private const val HALF_TURN = 180f
private const val FULL_TURN = 360f
private const val PULL_TURN = 150f
private const val PULL_SWEEP = 270f
private const val PULL_ALPHA = 0.4f
private const val STILL_SWEEP = 270f
private const val SPIN_SECONDS = 1.3f
private const val SPIN_MIN = 30f
private const val SPIN_EXTRA = 220f
private const val MIN_SCALE = 0.5f
private const val HALF = 0.5f

private val DOTS_WIDTH = 56.dp
private val DOTS_HEIGHT = 28.dp
private val DOT_RADIUS = 3.5.dp
private val DOT_GAP = 11.dp
private val DOT_HOP = 5.dp
private const val DOT_COUNT = 3
private const val EMPTY_ALPHA = 0.2f
private const val EMPTY_SCALE = 0.5f
private const val BOUNCE_SECONDS = 0.9f
private const val BOUNCE_DELAY = 0.15f
