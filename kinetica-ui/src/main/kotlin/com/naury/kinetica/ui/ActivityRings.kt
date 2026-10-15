package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Apple Watch의 활동 링처럼 겹친 동심원 진행 링. 첫 링이 가장 바깥이다. 처음 보일 때 링이 바깥부터 차례로 차오르고,
 * 값이 바뀌면 지금 위치에서 새 값까지 이어서 돈다. 1(100%)을 넘으면 한 바퀴를 넘어 겹쳐 돌고, 겹친 끝에는 그림자를 드리운 머리를 그린다.
 * '애니메이션 삭제' 접근성 설정이 켜져 있으면 처음부터 최종 모양을 그린다.
 *
 * 링은 오른쪽에서 왼쪽으로 쓰는 언어에서도 시계 방향으로 찬다. 진행 방향이 읽는 방향이 아니라 시계를 따르기 때문이다.
 * TalkBack은 링마다 이름과 비율을 이어서 읽는다. 크기를 정하지 않으면 지름 120dp인 정사각형이다.
 *
 * ```kotlin
 * ActivityRings(
 *     listOf(ActivityRing(0.8f, GlassChartDefaults.Move, "움직이기"), ActivityRing(1.3f, GlassChartDefaults.Exercise, "운동하기")),
 *     Modifier.size(140.dp),
 * )
 * ```
 *
 * @param rings 바깥부터 차례로 그릴 링. 링이 너무 많아 안쪽 링의 반지름이 두께보다 작아지면 그 링은 그리지 않는다.
 * @param thickness 링 두께. 0 이하이면 [IllegalArgumentException]을 던진다.
 * @param gap 링 사이 간격. 음수이면 [IllegalArgumentException]을 던진다.
 * @param content 가장 안쪽 링 안에 둘 내용
 */
@Composable
public fun ActivityRings(
    rings: List<ActivityRing>,
    modifier: Modifier = Modifier,
    thickness: Dp = 14.dp,
    gap: Dp = 3.dp,
    style: GlassStyle = LocalGlassStyle.current,
    content: @Composable BoxScope.() -> Unit = {},
) {
    require(thickness > 0.dp && gap >= 0.dp) { "thickness는 0보다 크고 gap은 0 이상이어야 합니다." }
    val shown = rememberRingProgress(rings)
    val summary = ringsSummary(rings)
    Box(
        modifier
            .defaultMinSize(RINGS_SIZE, RINGS_SIZE)
            .aspectRatio(1f)
            .semantics { contentDescription = summary }
            .drawWithCache {
                val stroke = Stroke(thickness.toPx(), cap = StrokeCap.Round)
                val step = thickness.toPx() + gap.toPx()
                val looks = rings.mapIndexed { i, ring ->
                    val radius = size.minDimension / 2 - stroke.width / 2 - step * i
                    RingLook(ring.color, radius, stroke, Offset(size.width / 2, size.height / 2))
                }
                onDrawBehind {
                    looks.forEachIndexed { i, look ->
                        if (look.radius > stroke.width / 2) drawActivityRing(look, shown.getOrNull(i)?.value ?: 0f)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalGlassContentColor provides style.onGlass) { content() }
    }
}

/** 링 하나를 그리는 데 쓰는 브러시와 위치. 크기나 색이 바뀔 때만 새로 만든다. */
private class RingLook(val color: Color, val radius: Float, val stroke: Stroke, val center: Offset) {
    val start = lerp(color, Color.Black, RING_START_DARKEN)
    val track = color.copy(alpha = RING_TRACK_ALPHA)

    // 0도에서 어두운 색으로 시작해 한 바퀴 끝에서 원래 색이 된다. 그릴 때 회전해 12시와 머리 위치에 맞춘다.
    val sweep = Brush.sweepGradient(listOf(start, color), center)
    val shadow = Brush.radialGradient(
        listOf(Color.Black.copy(alpha = HEAD_SHADOW_ALPHA), Color.Transparent),
        Offset.Zero,
        stroke.width * HEAD_SHADOW,
    )
    val topLeft = center - Offset(radius, radius)
    val box = Size(radius * 2, radius * 2)

    fun pointAt(degrees: Float): Offset {
        val radians = degrees / HALF_TURN * PI.toFloat()
        return center + Offset(cos(radians), sin(radians)) * radius
    }
}

private fun DrawScope.drawActivityRing(look: RingLook, progress: Float) {
    drawCircle(look.track, look.radius, look.center, style = look.stroke)
    val sweep = progress * CHART_FULL_TURN
    if (sweep <= 0f) return
    if (sweep < CHART_FULL_TURN) {
        rotate(-CHART_QUARTER_TURN, look.center) {
            drawArc(look.sweep, 0f, sweep, false, look.topLeft, look.box, style = look.stroke)
        }
        // 둥근 시작 끝은 기울기 색의 이음매(끝 색)에 걸치므로 시작 색으로 덮는다.
        drawCircle(look.start, look.stroke.width / 2, look.pointAt(-CHART_QUARTER_TURN))
        return
    }
    val head = sweep % CHART_FULL_TURN
    val headAngle = head - CHART_QUARTER_TURN
    rotate(headAngle, look.center) { drawCircle(look.sweep, look.radius, look.center, style = look.stroke) }
    // 머리 앞쪽 아래 링에 그림자를 드리워 겹쳐 지나간 것처럼 보이게 한다.
    val forward = headAngle + SHADOW_LEAD_DEGREES
    translate(look.pointAt(forward).x, look.pointAt(forward).y) {
        drawCircle(look.shadow, look.stroke.width * HEAD_SHADOW, Offset.Zero)
    }
    drawArc(look.color, headAngle - HEAD_DEGREES, HEAD_DEGREES, false, look.topLeft, look.box, style = look.stroke)
}

/** 링마다 보이는 진행 값. 처음에는 바깥 링부터 조금씩 늦게 시작한다. */
@Composable
private fun rememberRingProgress(rings: List<ActivityRing>): List<Animatable<Float, *>> {
    val still = reducedMotion()
    val shown = remember(rings.size) { List(rings.size) { Animatable(if (still) rings[it].progress else 0f) } }
    val first = remember(shown) { BooleanArray(1) { true } }
    LaunchedEffect(shown, rings.map { it.progress }) {
        val opening = first[0]
        first[0] = false
        shown.forEachIndexed { i, value ->
            launch {
                val target = rings[i].progress
                if (still) {
                    value.snapTo(target)
                    return@launch
                }
                if (opening) delay(RING_STAGGER_MS * i)
                val distance = kotlin.math.abs(target - value.value)
                val duration = (RING_MIN_MS + distance * RING_MS_PER_TURN).roundToInt().coerceAtMost(RING_MAX_MS)
                value.animateTo(target, tween(duration, easing = FastOutSlowInEasing))
            }
        }
    }
    return shown
}

@Composable
private fun ringsSummary(rings: List<ActivityRing>): String {
    val parts = mutableListOf(stringResource(R.string.kinetica_ui_chart_rings))
    rings.forEachIndexed { i, ring ->
        val name = ring.label ?: stringResource(R.string.kinetica_ui_chart_ring_unnamed, i + 1)
        parts += stringResource(R.string.kinetica_ui_chart_ring, name, (ring.progress * PERCENT_SCALE).roundToInt())
    }
    return parts.joinToString(", ")
}

private const val RING_START_DARKEN = 0.22f
private const val RING_TRACK_ALPHA = 0.2f
private const val HEAD_SHADOW_ALPHA = 0.5f
private const val HEAD_SHADOW = 0.7f
private const val HEAD_DEGREES = 2f
private const val SHADOW_LEAD_DEGREES = 3f
private const val HALF_TURN = 180f
private const val PERCENT_SCALE = 100f
private const val RING_STAGGER_MS = 120L
private const val RING_MIN_MS = 500f
private const val RING_MS_PER_TURN = 900f
private const val RING_MAX_MS = 1_800
private val RINGS_SIZE = 120.dp
