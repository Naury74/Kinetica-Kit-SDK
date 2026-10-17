package com.naury.kinetica.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 통화 시간 문구. 1시간 미만은 `4:05`, 넘으면 `1:04:05`처럼 쓴다. 음수는 0으로 본다.
 *
 * @param millis 지난 시간(ms)
 */
public fun formatCallDuration(millis: Long): String {
    val total = (millis.coerceAtLeast(0L) / MS_PER_SECOND)
    val hours = total / SECONDS_PER_HOUR
    val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * [startedAtMillis]부터 지난 시간을 [formatCallDuration] 문구로 돌려준다. 초가 바뀔 때만 값이 바뀐다.
 *
 * @param startedAtMillis 통화가 연결된 시각. [now]와 같은 시계 기준 ms다.
 * @param now 지금 시각. 기본값은 `SystemClock.elapsedRealtime()`이고, 호스트가 다른 단조 시계를 쓰면 그 함수를 넘긴다.
 */
@Composable
public fun rememberKCallElapsed(startedAtMillis: Long, now: () -> Long = SystemClock::elapsedRealtime): String {
    val current by produceState(now(), startedAtMillis) {
        while (true) {
            value = now()
            delay(MS_PER_SECOND - (value - startedAtMillis).mod(MS_PER_SECOND))
        }
    }
    return formatCallDuration(current - startedAtMillis)
}

/**
 * 통화 시간. 바뀐 자리 숫자만 위로 굴러 올라간다([RollingText]). 1초마다 한 번만 다시 그린다.
 *
 * ```kotlin
 * KCallTimer(call.connectedAt, style = TextStyle(fontSize = 15.sp))
 * ```
 *
 * @param startedAtMillis 통화가 연결된 시각. [now]와 같은 시계 기준 ms다.
 * @param style 글자 모양. 색을 정하지 않으면 표면 글자 색이다.
 * @param now 지금 시각. 기본값은 `SystemClock.elapsedRealtime()`이다.
 */
@Composable
public fun KCallTimer(
    startedAtMillis: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    now: () -> Long = SystemClock::elapsedRealtime,
) {
    val ink = LocalKineticaContentColor.current
    val text = rememberKCallElapsed(startedAtMillis, now)
    RollingText(text, modifier, themed(style.copy(color = if (style.color == Color.Unspecified) ink else style.color)))
}

/**
 * 연결 품질 막대 셋. 품질만큼 막대가 차고, 약하면 주황, 끊기면 빨강으로 칠한다. [KNetworkQuality.Unknown]이면 빈 막대다.
 * TalkBack은 '연결 약함'처럼 읽는다.
 *
 * @param size 막대 셋이 들어갈 정사각형 한 변
 * @param contentDescription TalkBack이 읽을 글. 기본값은 품질 이름이고, null이면 읽지 않는다(옆에 같은 내용을 글자로 쓸 때).
 */
@Composable
public fun KNetworkQualityIndicator(
    quality: KNetworkQuality,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    contentDescription: String? = quality.label(),
) {
    val ink = LocalKineticaContentColor.current
    val fill = quality.color(ink)
    val empty = ink.copy(alpha = EMPTY_BAR_ALPHA)
    Canvas(
        modifier.size(size).clearAndSetSemantics { contentDescription?.let { this.contentDescription = it } },
    ) {
        val bar = this.size.width / BAR_SLOTS
        repeat(BAR_COUNT) { index ->
            val h = this.size.height * (index + 1) / BAR_COUNT
            // 신호 막대는 어느 언어에서나 왼쪽부터 높아지는 모양으로 읽으므로 RTL에서도 뒤집지 않는다.
            val x = index * bar * 2
            drawRoundRect(
                if (index < quality.bars) fill else empty,
                topLeft = Offset(x, this.size.height - h),
                size = Size(bar, h),
                cornerRadius = CornerRadius(bar / 2),
            )
        }
    }
}

/** [KAudioLevelIndicator] 모양 */
public enum class KAudioLevelStyle {
    /** 세로 막대 셋. 이름표 옆에 놓는다. */
    Bars,

    /** 둘레가 소리 크기만큼 두꺼워지는 고리. 아바타나 마이크 아이콘을 감싼다. */
    Ring,
}

/**
 * 마이크 소리 크기. [level]은 그리기 단계에서 읽으므로 초당 수십 번 바뀌어도 다시 구성하지 않고 그리기만 다시 한다.
 * 장식이라 TalkBack은 읽지 않는다. 말하는 중인지는 옆 글자나 상위 요소의 설명으로 전한다.
 *
 * ```kotlin
 * KAudioLevelIndicator({ levels.value[id] ?: 0f })
 * ```
 *
 * @param level 소리 크기(0..1)를 돌려주는 함수. 범위를 벗어난 값은 0..1로 맞춘다.
 * @param color 막대와 고리 색. 기본값은 강조색이다.
 * @param size 지름이나 막대 셋이 들어갈 정사각형 한 변
 */
@Composable
public fun KAudioLevelIndicator(
    level: () -> Float,
    modifier: Modifier = Modifier,
    style: KAudioLevelStyle = KAudioLevelStyle.Bars,
    color: Color = KineticaTheme.colors.accent,
    size: Dp = 16.dp,
) {
    Canvas(modifier.size(size).semantics { }) {
        val value = level().coerceIn(0f, 1f)
        when (style) {
            KAudioLevelStyle.Bars -> {
                val bar = this.size.width / BAR_SLOTS
                LEVEL_WEIGHTS.forEachIndexed { index, weight ->
                    val h = this.size.height * (BAR_FLOOR + (1f - BAR_FLOOR) * value * weight)
                    drawRoundRect(
                        color,
                        topLeft = Offset(index * bar * 2, (this.size.height - h) / 2),
                        size = Size(bar, h),
                        cornerRadius = CornerRadius(bar / 2),
                    )
                }
            }

            KAudioLevelStyle.Ring -> {
                val stroke = this.size.minDimension * (RING_MIN + RING_RANGE * value)
                drawCircle(
                    color.copy(alpha = RING_ALPHA + (1f - RING_ALPHA) * value),
                    radius = (this.size.minDimension - stroke) / 2,
                    style = Stroke(stroke),
                )
            }
        }
    }
}

/**
 * 말하는 동안 요소 뒤로 고리 두 개가 퍼져 나간다. 퍼지는 세기는 [level]을 그리기 단계에서 읽는다. [speaking]이 false면 그리지 않고
 * 시계도 멈춘다. '애니메이션 삭제'면 퍼지지 않고 소리 크기만큼 옅은 고리 하나를 둔다. 자리를 차지하지 않고 요소 밖으로 넘쳐 그린다.
 */
@Composable
internal fun Modifier.speakingRipple(speaking: Boolean, level: () -> Float, color: Color): Modifier {
    val still = reducedMotion()
    val clock = rememberLoopClock(running = speaking && !still)
    return this.loopVisibility(clock).drawBehind {
        if (!speaking) return@drawBehind
        val strength = RIPPLE_FLOOR + (1f - RIPPLE_FLOOR) * level().coerceIn(0f, 1f)
        val base = size.minDimension / 2
        if (still) {
            drawCircle(color.copy(alpha = RIPPLE_ALPHA * strength), base * (1f + RIPPLE_SPREAD / 2))
            return@drawBehind
        }
        val phase = (clock.seconds / RIPPLE_PERIOD_S) % 1f
        listOf(phase, (phase + HALF) % 1f).forEach { p ->
            drawCircle(color.copy(alpha = (1f - p) * RIPPLE_ALPHA * strength), base * (1f + RIPPLE_SPREAD * p))
        }
    }
}

@Composable
internal fun KNetworkQuality.label(): String = stringResource(
    when (this) {
        KNetworkQuality.Excellent -> R.string.kinetica_ui_call_quality_excellent
        KNetworkQuality.Good -> R.string.kinetica_ui_call_quality_good
        KNetworkQuality.Poor -> R.string.kinetica_ui_call_quality_poor
        KNetworkQuality.Lost -> R.string.kinetica_ui_call_quality_lost
        KNetworkQuality.Unknown -> R.string.kinetica_ui_call_quality_unknown
    },
)

/** 품질 색. 좋으면 글자 색, 약하면 주황, 끊기면 빨강이다. */
@Composable
internal fun KNetworkQuality.color(ink: Color): Color = when (this) {
    KNetworkQuality.Excellent, KNetworkQuality.Good -> KineticaTheme.colors.positive
    KNetworkQuality.Poor -> Color(CALL_WARNING)
    KNetworkQuality.Lost -> KineticaTheme.colors.destructive
    KNetworkQuality.Unknown -> ink.copy(alpha = EMPTY_BAR_ALPHA)
}

internal const val CALL_WARNING = 0xFFFF9F0A
private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val BAR_COUNT = 3
private const val BAR_SLOTS = 5f
private const val EMPTY_BAR_ALPHA = 0.3f
private const val BAR_FLOOR = 0.3f
private val LEVEL_WEIGHTS = listOf(0.55f, 1f, 0.7f)
private const val RING_MIN = 0.06f
private const val RING_RANGE = 0.12f
private const val RING_ALPHA = 0.35f
private const val RIPPLE_FLOOR = 0.35f
private const val RIPPLE_ALPHA = 0.3f
private const val RIPPLE_SPREAD = 0.7f
private const val RIPPLE_PERIOD_S = 1.8f
private const val HALF = 0.5f
