package com.naury.kinetica.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.launch

/** [GlassResultMark]가 보여 줄 상태. */
public enum class GlassResult {
    /** 링이 돌며 기다리는 중임을 보인다. */
    Loading,

    /** 링이 닫히고 체크 표시가 그려지며 작은 파티클이 튄다. */
    Success,

    /** 링이 닫히고 × 표시가 그려진다. */
    Failure,

    /** 링이 닫히고 느낌표가 그려진다. */
    Warning,
}

/** [GlassResultMark]의 기본값. */
public object GlassResultMarkDefaults {
    /** [state]에 맞는 기본 색. 진행 중은 `accent`, 성공은 `positive`, 실패는 `destructive`, 주의는 주황색이다. */
    @Composable
    @ReadOnlyComposable
    public fun color(state: GlassResult): Color = when (state) {
        GlassResult.Loading -> GlassTheme.colors.accent
        GlassResult.Success -> GlassTheme.colors.positive
        GlassResult.Failure -> GlassTheme.colors.destructive
        GlassResult.Warning -> Color(WARNING_COLOR)
    }
}

/**
 * 작업 결과를 보여 주는 표시. [GlassResult.Loading]이면 원호가 돌고, 결과 상태로 바뀌면 원호가 닫혀 원이 된 뒤
 * 체크, ×, 느낌표가 획 순서대로 그려진다. 성공이면 원 둘레로 작은 파티클이 튄다. 결과에서 다시 진행 중으로 바꾸면 표시가 지워지고 원호가 다시 돈다.
 * 처음부터 결과 상태로 보여 줘도 그리는 애니메이션이 한 번 재생된다.
 *
 * TalkBack은 상태가 바뀔 때마다 상태 이름을 읽는다. '애니메이션 삭제' 접근성 설정이 켜져 있으면 원호가 돌지 않고 결과는 바로 완성된 모양으로 보인다.
 * 파티클은 [size] 안에 그리므로 링 지름은 [size]의 약 70%다.
 *
 * ```kotlin
 * GlassResultMark(if (saving) GlassResult.Loading else GlassResult.Success)
 * ```
 *
 * @param size 표시 전체 크기(파티클 여유 포함)
 * @param color 링과 표시 색. 바뀌면 부드럽게 섞인다.
 */
@Composable
public fun GlassResultMark(
    state: GlassResult,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    color: Color = GlassResultMarkDefaults.color(state),
) {
    val still = reducedMotion()
    val tint by animateColorAsState(color, tween(COLOR_MS), label = "result-color")
    val anim = remember { ResultAnimation() }
    LaunchedEffect(state, still) { anim.run(state, still) }
    val clock = rememberLoopClock(running = state == GlassResult.Loading && !still)
    val label = resultLabel(state)
    Spacer(
        modifier
            .size(size)
            .loopVisibility(clock)
            .semantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
                if (state == GlassResult.Loading) progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }
            .drawWithCache {
                val geometry = MarkGeometry(this.size)
                onDrawBehind { with(geometry) { draw(anim, clock.seconds, tint) } }
            },
    )
}

/**
 * 결과 표시를 가운데에 둔 글래스 다이얼로그. 진행 중일 때는 확인 버튼이 꺼지고 바깥 누르기나 뒤로 가기로 닫히지 않는다.
 * 같은 다이얼로그에서 [state]만 바꾸면 도는 링이 결과 표시로 이어진다.
 *
 * ```kotlin
 * GlassResultDialog(visible = open, state = result, title = "결제 완료", onDismissRequest = { open = false }, message = "영수증을 메일로 보냈어요")
 * ```
 *
 * @param title 다이얼로그 제목. TalkBack이 창 제목으로도 읽는다.
 * @param confirmText 확인 버튼 글자. `null`이면 '확인'이다.
 */
@Composable
public fun GlassResultDialog(
    visible: Boolean,
    state: GlassResult,
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmText: String? = null,
) {
    val busy = state == GlassResult.Loading
    GlassDialog(visible, onDismissRequest = { if (!busy) onDismissRequest() }, modifier = modifier, title = title) {
        val ink = LocalGlassContentColor.current
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DIALOG_GAP),
        ) {
            GlassResultMark(state, size = DIALOG_MARK)
            ThemedText(
                title,
                Modifier.semantics { heading() },
                style = TextStyle(
                    color = ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineBreak = PhraseBreak,
                ),
            )
            message?.let {
                ThemedText(
                    it,
                    style = TextStyle(
                        color = ink.copy(alpha = MESSAGE_ALPHA),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineBreak = PhraseBreak,
                    ),
                )
            }
            Spacer(Modifier.height(DIALOG_GAP / 2))
            GlassButton(
                confirmText ?: stringResource(R.string.kinetica_ui_result_ok),
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            )
        }
    }
}

@Composable
private fun resultLabel(state: GlassResult): String = stringResource(
    when (state) {
        GlassResult.Loading -> R.string.kinetica_ui_loading
        GlassResult.Success -> R.string.kinetica_ui_result_success
        GlassResult.Failure -> R.string.kinetica_ui_result_failure
        GlassResult.Warning -> R.string.kinetica_ui_result_warning
    },
)

/** 링이 닫힌 정도([ring]), 표시가 그려진 정도([glyph]), 파티클 진행([burst]), 튀는 크기([pop])와 지금 그리는 표시([shown]). */
private class ResultAnimation {
    val ring = Animatable(0f)
    val glyph = Animatable(0f)
    val burst = Animatable(1f)
    val pop = Animatable(1f)
    var shown by mutableStateOf(GlassResult.Loading)

    suspend fun run(state: GlassResult, still: Boolean) = kotlinx.coroutines.coroutineScope {
        if (state == GlassResult.Loading) {
            launch { glyph.animateTo(0f, tween(ERASE_MS)) }
            ring.animateTo(0f, tween(OPEN_MS))
            shown = state
            return@coroutineScope
        }
        if (glyph.value > 0f && shown != state) glyph.animateTo(0f, tween(ERASE_MS))
        shown = state
        if (still) {
            ring.snapTo(1f)
            glyph.snapTo(1f)
            return@coroutineScope
        }
        ring.animateTo(1f, tween(CLOSE_MS, easing = FastOutSlowInEasing))
        launch {
            pop.snapTo(POP_FROM)
            pop.animateTo(1f, spring(dampingRatio = POP_DAMPING, stiffness = POP_STIFFNESS))
        }
        if (state == GlassResult.Success) {
            launch {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(BURST_MS, easing = LinearOutSlowInEasing))
            }
        }
        glyph.animateTo(1f, tween(DRAW_MS, easing = FastOutSlowInEasing))
    }
}

/** 크기가 정해진 뒤 한 번 만드는 그리기 재료. [path]는 프레임마다 비우고 다시 채운다. */
private class MarkGeometry(size: Size) {
    private val center = Offset(size.width / 2, size.height / 2)
    private val radius = size.minDimension / 2 * RING_FRACTION
    private val strokeWidth = size.minDimension * STROKE_FRACTION
    private val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val path = Path()
    private val ringTopLeft = Offset(center.x - radius, center.y - radius)
    private val ringSize = Size(radius * 2, radius * 2)

    fun DrawScope.draw(anim: ResultAnimation, seconds: Float, tint: Color) {
        val closed = anim.ring.value
        scale(anim.pop.value, center) {
            drawCircle(tint.copy(alpha = DISC_ALPHA * closed), radius, center)
            // 진행 중에는 원호가 돌면서 길이가 숨 쉬듯 바뀐다. 닫힐 때는 멈춘 자리에서 360도까지 늘어난다.
            val spin = seconds * SPIN_DEGREES_PER_SECOND
            val breathing = SWEEP_MIN + SWEEP_RANGE * (1f + sin(seconds * BREATH_SPEED)) / 2f
            val sweep = breathing + (RESULT_FULL_TURN - breathing) * closed
            rotate(spin, center) {
                drawArc(tint, START_ANGLE, sweep, useCenter = false, topLeft = ringTopLeft, size = ringSize, style = stroke)
            }
            drawGlyph(anim.shown, anim.glyph.value, tint)
        }
        val burst = anim.burst.value
        if (burst < 1f) drawParticles(burst, tint)
    }

    private fun DrawScope.drawGlyph(state: GlassResult, progress: Float, tint: Color) {
        if (progress <= 0f) return
        val strokes = when (state) {
            GlassResult.Loading -> return
            GlassResult.Success -> CHECK
            GlassResult.Failure -> CROSS
            GlassResult.Warning -> EXCLAMATION
        }
        path.rewind()
        var left = strokes.sumOf { it.length().toDouble() }.toFloat() * progress
        for (line in strokes) {
            if (left <= 0f) break
            left = appendPartial(line, left)
        }
        drawPath(path, tint, style = stroke)
        if (state == GlassResult.Warning && progress > DOT_START) {
            val dot = (progress - DOT_START) / (1f - DOT_START)
            drawCircle(tint, strokeWidth * DOT_SCALE * dot, point(0f, WARNING_DOT_Y))
        }
    }

    /** [line]을 앞에서부터 [budget] 길이(단위 반경 기준)만큼 [path]에 잇고, 남은 길이를 돌려준다. */
    private fun appendPartial(line: FloatArray, budget: Float): Float {
        var left = budget
        path.moveTo(point(line[0], line[1]).x, point(line[0], line[1]).y)
        var i = 2
        while (i < line.size && left > 0f) {
            val ax = line[i - 2]
            val ay = line[i - 1]
            val segment = hypot(line[i] - ax, line[i + 1] - ay)
            val t = (left / segment).coerceAtMost(1f)
            val end = point(ax + (line[i] - ax) * t, ay + (line[i + 1] - ay) * t)
            path.lineTo(end.x, end.y)
            left -= segment
            i += 2
        }
        return left
    }

    private fun DrawScope.drawParticles(burst: Float, tint: Color) {
        val reach = radius * (PARTICLE_FROM + (PARTICLE_TO - PARTICLE_FROM) * burst)
        val alpha = 1f - burst
        for (i in 0 until PARTICLES) {
            val angle = (i * 2 * PI / PARTICLES + PARTICLE_PHASE).toFloat()
            val dotRadius = strokeWidth * PARTICLE_SIZE * (if (i % 2 == 0) 1f else SMALL_PARTICLE) * (1f - burst * PARTICLE_SHRINK)
            drawCircle(tint.copy(alpha = alpha), dotRadius, Offset(center.x + cos(angle) * reach, center.y + sin(angle) * reach))
        }
    }

    /** 반경을 1로 둔 좌표를 그리기 좌표(px)로 바꾼다. */
    private fun point(x: Float, y: Float): Offset = Offset(center.x + x * radius, center.y + y * radius)
}

/** 꺾은선 하나의 길이(단위 반경 기준). 점은 x, y 순서로 이어 둔다. */
private fun FloatArray.length(): Float {
    var total = 0f
    var i = 2
    while (i < size) {
        total += hypot(this[i] - this[i - 2], this[i + 1] - this[i - 1])
        i += 2
    }
    return total
}

private val CHECK = listOf(floatArrayOf(-0.42f, 0.02f, -0.12f, 0.32f, 0.45f, -0.3f))
private val CROSS = listOf(floatArrayOf(-0.32f, -0.32f, 0.32f, 0.32f), floatArrayOf(0.32f, -0.32f, -0.32f, 0.32f))
private val EXCLAMATION = listOf(floatArrayOf(0f, -0.44f, 0f, 0.12f))
private const val WARNING_DOT_Y = 0.4f
private const val DOT_START = 0.7f
private const val DOT_SCALE = 0.62f

private const val WARNING_COLOR = 0xFFFF9F0A
private const val RING_FRACTION = 0.7f
private const val STROKE_FRACTION = 0.062f
private const val DISC_ALPHA = 0.12f
private const val START_ANGLE = -90f
private const val RESULT_FULL_TURN = 360f
private const val SPIN_DEGREES_PER_SECOND = 300f
private const val SWEEP_MIN = 60f
private const val SWEEP_RANGE = 200f
private const val BREATH_SPEED = 3.2f
private const val PARTICLES = 10
private const val PARTICLE_PHASE = 0.3
private const val PARTICLE_FROM = 1.12f
private const val PARTICLE_TO = 1.4f
private const val PARTICLE_SIZE = 0.42f
private const val SMALL_PARTICLE = 0.6f
private const val PARTICLE_SHRINK = 0.6f

private const val COLOR_MS = 300
private const val ERASE_MS = 140
private const val OPEN_MS = 240
private const val CLOSE_MS = 360
private const val DRAW_MS = 380
private const val BURST_MS = 720
private const val POP_FROM = 0.86f
private const val POP_DAMPING = 0.42f
private const val POP_STIFFNESS = 500f

private val DIALOG_MARK = 88.dp
private val DIALOG_GAP = 12.dp
private const val MESSAGE_ALPHA = 0.72f
