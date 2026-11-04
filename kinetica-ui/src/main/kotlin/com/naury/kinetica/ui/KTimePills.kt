package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

/** [KCountdownPill]이 남은 시간을 그림으로 보여 주는 방법. */
public enum class KPillProgress {
    /** 글자 앞의 작은 링이 줄어든다. */
    Ring,

    /** 글자 아래 가는 막대가 줄어든다. */
    Bar,

    /** 글자만 보인다. */
    None,
}

/**
 * 남은 시간이 줄어드는 캡슐. 초가 바뀔 때 [RollingText]처럼 숫자가 굴러가고, 링이나 막대가 남은 몫만큼 부드럽게 줄어든다.
 * [KLiveActivity]의 compact 슬롯에 넣기 좋게 기본은 배경 없이 글자와 진행 표시만 그린다. 따로 띄우려면 [surface]를 준다.
 *
 * 링과 막대는 그리기 단계에서만 다시 그리고, 전체 시간을 600번쯤 나눈 간격으로만 갱신한다(16ms 이상 1초 이하).
 * 숫자는 초가 바뀔 때만 다시 구성한다. '애니메이션 삭제' 설정에서는 숫자가 바로 바뀌고 진행 표시도 초 단위로만 움직인다.
 * TalkBack은 [label]과 남은 시간을 한 번에 읽는다.
 *
 * ```kotlin
 * KCountdownPill(targetMillis = endsAt, totalMillis = 5 * 60_000L, label = "타이머", onFinished = ::ring)
 * ```
 *
 * @param targetMillis 끝나는 시각. [System.currentTimeMillis]와 같은 에포크 기준 ms다.
 * @param totalMillis 전체 시간(ms). 진행 표시는 `남은 시간 / totalMillis`로 그리고, 0 이하이면 진행 표시를 비운다.
 * @param label TalkBack이 남은 시간 앞에 읽을 이름. 화면에는 보이지 않는다.
 * @param accent 링과 막대 색. 지정하지 않으면 테마 강조색이다.
 * @param surface 캡슐 배경. null이면 배경 없이 그린다.
 * @param onFinished 남은 시간이 0이 되는 순간 한 번 부른다. 처음부터 0이면 부르지 않는다.
 */
@Composable
public fun KCountdownPill(
    targetMillis: Long,
    totalMillis: Long,
    modifier: Modifier = Modifier,
    label: String? = null,
    progress: KPillProgress = KPillProgress.Ring,
    accent: Color = Color.Unspecified,
    textStyle: TextStyle = PillTextStyle,
    surface: SurfaceStyle? = null,
    onFinished: (() -> Unit)? = null,
) {
    CountdownPill(
        CountdownSpec(targetMillis, totalMillis, System::currentTimeMillis),
        TimePillLook(progress, accent, textStyle, surface),
        label,
        modifier,
        onFinished,
    )
}

/**
 * 시작한 뒤 흐른 시간이 올라가는 캡슐. 통화 중, 녹음 중, 운동처럼 끝이 정해지지 않은 활동에 쓴다. 초가 바뀔 때 숫자가 굴러가고,
 * [dotColor]를 주면 글자 앞에 천천히 깜박이는 점을 그린다(녹음 중 표시). 점은 화면에 보일 때만 움직이고 '애니메이션 삭제' 설정에서는 멈춘다.
 * 기본은 배경 없이 그려 [KLiveActivity] 슬롯에 넣기 좋다.
 *
 * @param startMillis 시작한 시각. [System.currentTimeMillis]와 같은 에포크 기준 ms다. 미래 시각이면 00:00으로 보인다.
 * @param label TalkBack이 흐른 시간 앞에 읽을 이름
 * @param dotColor 앞에 그릴 점 색. [Color.Unspecified]면 점을 그리지 않는다.
 * @param surface 캡슐 배경. null이면 배경 없이 그린다.
 */
@Composable
public fun KTimePill(
    startMillis: Long,
    modifier: Modifier = Modifier,
    label: String? = null,
    dotColor: Color = Color.Unspecified,
    textStyle: TextStyle = PillTextStyle,
    surface: SurfaceStyle? = null,
) {
    TimePill(rememberElapsed(startMillis, System::currentTimeMillis), label, dotColor, textStyle, surface, modifier)
}

/** 진행 표시 모양과 색. */
internal class TimePillLook(val progress: KPillProgress, val accent: Color, val textStyle: TextStyle, val surface: SurfaceStyle?)

/** 카운트다운 시각과 시계. 시계는 테스트에서 바꿔 넣는다. */
internal class CountdownSpec(val targetMillis: Long, val totalMillis: Long, val now: () -> Long)

@Composable
internal fun CountdownPill(spec: CountdownSpec, look: TimePillLook, label: String?, modifier: Modifier, onFinished: (() -> Unit)?) {
    val left by rememberCountdown(spec.targetMillis, spec.now)
    FinishEffect(left, onFinished)
    val still = reducedMotion()
    val fraction = rememberPillFraction(spec, still)
    val accent = if (look.accent.isSpecified) look.accent else KineticaTheme.colors.accent
    val text = countdownText(left)
    PillFrame(look.surface, label, text, modifier) {
        // 캡슐 표면이 있으면 그 글자색이 안쪽에 들어오므로 여기서 읽는다.
        val ink = LocalKineticaContentColor.current
        val style = look.textStyle.merge(TextStyle(color = look.textStyle.color.takeIf { it.isSpecified } ?: ink))
        when (look.progress) {
            KPillProgress.Ring -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(RING_SIZE).drawBehind { drawPillRing(fraction.value, accent, ink) })
                Spacer(Modifier.width(PILL_GAP))
                RollingText(text, style = style)
            }

            KPillProgress.Bar -> PillBarColumn(fraction, accent, ink) { RollingText(text, style = style) }

            KPillProgress.None -> RollingText(text, style = style)
        }
    }
}

/**
 * 글자 아래에 남은 몫 막대를 그린다. 막대 폭은 글자 폭을 따른다. 굴러가는 글자는 고유 폭을 알려 주지 않으므로
 * `IntrinsicSize`로 맞추지 않고, 글자 아래 여백에 막대를 그린다.
 */
@Composable
private fun PillBarColumn(fraction: PillFraction, accent: Color, ink: Color, text: @Composable () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        Modifier
            .drawBehind {
                val bar = BAR_HEIGHT.toPx()
                val top = size.height - bar
                val radius = CornerRadius(bar / 2)
                drawRoundRect(ink.copy(alpha = TRACK_ALPHA), Offset(0f, top), Size(size.width, bar), radius)
                val w = size.width * fraction.value.coerceIn(0f, 1f)
                if (w > 0f) {
                    // 남은 몫은 시작 쪽에 붙여 끝 쪽부터 줄어든다.
                    val left = if (rtl) size.width - w else 0f
                    drawRoundRect(accent, Offset(left, top), Size(w, bar), radius)
                }
            }
            .padding(bottom = BAR_HEIGHT + BAR_GAP),
    ) { text() }
}

@Composable
private fun TimePill(
    elapsed: State<Long>,
    label: String?,
    dotColor: Color,
    textStyle: TextStyle,
    surface: SurfaceStyle?,
    modifier: Modifier,
) {
    val text = elapsedText(elapsed.value)
    PillFrame(surface, label, text, modifier) {
        val ink = LocalKineticaContentColor.current
        val style = textStyle.merge(TextStyle(color = textStyle.color.takeIf { it.isSpecified } ?: ink))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dotColor.isSpecified) {
                BlinkDot(dotColor)
                Spacer(Modifier.width(PILL_GAP))
            }
            RollingText(text, style = style)
        }
    }
}

/** 녹음 중 점. 2초 주기로 옅어졌다 짙어진다. */
@Composable
internal fun BlinkDot(color: Color, modifier: Modifier = Modifier) {
    val clock = rememberLoopClock(running = !reducedMotion())
    Box(
        modifier
            .size(DOT_SIZE)
            .loopVisibility(clock)
            .drawBehind {
                val wave = (sin(clock.seconds * PI * 2 / BLINK_PERIOD_S).toFloat() + 1f) / 2f
                drawCircle(color.copy(alpha = color.alpha * (DOT_MIN_ALPHA + (1f - DOT_MIN_ALPHA) * wave)))
            },
    )
}

@Composable
private fun PillFrame(surface: SurfaceStyle?, label: String?, text: String, modifier: Modifier, content: @Composable () -> Unit) {
    val spoken = if (label.isNullOrEmpty()) text else "$label, $text"
    val framed = if (surface != null) {
        modifier
            .kSurface(surface, CircleShape)
            .heightIn(min = PILL_MIN_HEIGHT)
            .padding(horizontal = PILL_PADDING)
    } else {
        modifier
    }
    Box(framed.clearAndSetSemantics { contentDescription = spoken }, contentAlignment = Alignment.Center) {
        if (surface != null) {
            CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor) { content() }
        } else {
            content()
        }
    }
}

/** 0이 된 순간 한 번 [onFinished]를 부른다. */
@Composable
private fun FinishEffect(left: Long, onFinished: (() -> Unit)?) {
    val latest by rememberUpdatedState(onFinished)
    val armed = remember { booleanArrayOf(false) }
    if (left > 0L) armed[0] = true
    LaunchedEffect(left == 0L) {
        if (left == 0L && armed[0]) {
            armed[0] = false
            latest?.invoke()
        }
    }
}

/** 남은 몫(0..1). 그리기 단계에서만 읽는다. */
@Stable
internal class PillFraction {
    var value by mutableFloatStateOf(1f)
}

@Composable
private fun rememberPillFraction(spec: CountdownSpec, still: Boolean): PillFraction {
    val fraction = remember { PillFraction() }
    LaunchedEffect(spec.targetMillis, spec.totalMillis, still) {
        val total = spec.totalMillis
        if (total <= 0L) {
            fraction.value = 0f
            return@LaunchedEffect
        }
        // 링 둘레가 수십 px이라 전체를 600번쯤 나눠 갱신하면 매 프레임 그린 것과 눈으로 구별되지 않는다.
        val step = if (still) MILLIS_PER_SECOND else (total / FRACTION_STEPS).coerceIn(MIN_STEP_MS, MILLIS_PER_SECOND)
        while (true) {
            val left = (spec.targetMillis - spec.now()).coerceAtLeast(0L)
            fraction.value = if (still) countdownSeconds(left) * MILLIS_PER_SECOND / total.toFloat() else left / total.toFloat()
            if (left == 0L) break
            delay(if (still) (left - 1) % MILLIS_PER_SECOND + 1 else step)
        }
    }
    return fraction
}

/** 흐른 시간(ms)을 매초 갱신한다. */
@Composable
internal fun rememberElapsed(startMillis: Long, now: () -> Long): State<Long> =
    produceState((now() - startMillis).coerceAtLeast(0L), startMillis) {
        while (true) {
            val elapsed = (now() - startMillis).coerceAtLeast(0L)
            value = elapsed
            delay(MILLIS_PER_SECOND - elapsed % MILLIS_PER_SECOND)
        }
    }

/** 흐른 시간(ms)을 'MM:SS'로, 1시간 이상이면 'HH:MM:SS'로. 초는 버린다. */
internal fun elapsedText(elapsedMillis: Long): String =
    countdownText(elapsedMillis.coerceAtLeast(0L) / MILLIS_PER_SECOND * MILLIS_PER_SECOND)

private fun countdownSeconds(left: Long): Long = (left + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND

private fun DrawScope.drawPillRing(fraction: Float, accent: Color, ink: Color) {
    val stroke = RING_STROKE.toPx()
    val inset = stroke / 2
    val arc = Size(size.width - stroke, size.height - stroke)
    drawCircle(ink.copy(alpha = TRACK_ALPHA), radius = size.minDimension / 2 - inset, style = Stroke(stroke))
    val sweep = FULL_TURN * fraction.coerceIn(0f, 1f)
    if (sweep > 0f) {
        drawArc(
            accent,
            TWELVE_OCLOCK,
            sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arc,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** 캡슐 글자 기본 스타일. 숫자 폭이 같은 글꼴 기능을 켠다. */
private val PillTextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

private const val MILLIS_PER_SECOND = 1_000L
private const val FULL_TURN = 360f
private const val TWELVE_OCLOCK = -90f
private const val FRACTION_STEPS = 600L
private const val MIN_STEP_MS = 16L
private const val TRACK_ALPHA = 0.22f
private const val DOT_MIN_ALPHA = 0.35f
private const val BLINK_PERIOD_S = 2.0
private val RING_SIZE = 16.dp
private val RING_STROKE = 2.5.dp
private val BAR_HEIGHT = 3.dp
private val BAR_GAP = 3.dp
private val DOT_SIZE = 8.dp
private val PILL_GAP = 6.dp
private val PILL_PADDING = 12.dp
private val PILL_MIN_HEIGHT = 32.dp
