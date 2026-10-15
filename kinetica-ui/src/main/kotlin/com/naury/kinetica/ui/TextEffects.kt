package com.naury.kinetica.ui

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * 글자 위로 빛이 한 줄기씩 지나가는 텍스트. 로딩 중 안내나 '밀어서 잠금 해제' 같은 문구에 어울린다.
 * 빛은 읽는 방향을 따라 흐르므로 RTL에서는 오른쪽에서 왼쪽으로 지나간다. '애니메이션 삭제' 접근성 설정이 켜져 있으면 빛 없이 글자만 그린다.
 *
 * ```kotlin
 * ShimmerText("밀어서 잠금 해제", style = TextStyle(fontSize = 20.sp))
 * ```
 *
 * @param color 글자 색. [style]에 색이 지정되어 있으면 그 색을 쓴다.
 * @param highlight 지나가는 빛의 색
 * @param periodMs 빛이 한 번 지나가는 데 걸리는 시간(ms). 0 이하이면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun ShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = LocalGlassContentColor.current,
    highlight: Color = Color.White,
    periodMs: Int = 2_200,
) {
    require(periodMs > 0) { "periodMs는 0보다 커야 합니다." }
    val still = reducedMotion()
    val progress = rememberLoopProgress(still, periodMs, PAUSE_MS, FastOutSlowInEasing)
    ThemedText(
        text,
        modifier
            .loopVisibility(progress.clock)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val band = maxOf(size.width * BAND_RATIO, size.height * 2f)
                val brush = Brush.linearGradient(
                    listOf(Color.Transparent, highlight, Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(band, size.height * BAND_SLANT),
                )
                val bandSize = Size(band, size.height)
                onDrawWithContent {
                    drawContent()
                    if (!still) {
                        val travel = (size.width + band * 2f) * progress.value - band
                        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - band - travel else travel
                        translate(left = x) { drawRect(brush, size = bandSize, blendMode = BlendMode.SrcAtop) }
                    }
                }
            },
        style = style.copy(color = style.color.takeOrElse { color }),
    )
}

/**
 * 그라디언트로 칠한 글자. 색이 글자를 따라 천천히 흘러간다. '애니메이션 삭제' 접근성 설정이 켜져 있거나 [periodMs]가 0이면 멈춘 그라디언트로 그린다.
 *
 * ```kotlin
 * GradientText("Kinetica", style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Black))
 * ```
 *
 * @param colors 그라디언트 색. 두 개 미만이면 [IllegalArgumentException]을 던진다.
 * @param periodMs 색이 한 바퀴 흐르는 시간(ms). 음수면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun GradientText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    colors: List<Color> = GlowColors,
    periodMs: Int = 4_000,
) {
    require(colors.size >= 2) { "colors는 두 개 이상이어야 합니다." }
    require(periodMs >= 0) { "periodMs는 0 이상이어야 합니다." }
    val still = reducedMotion() || periodMs == 0
    val progress = rememberLoopProgress(still, maxOf(periodMs, 1), 0, LinearEasing)
    // 끝 색을 처음 색으로 이어 붙여야 반복되는 경계가 보이지 않는다.
    val loop = remember(colors) { colors + colors.first() }
    ThemedText(
        text,
        modifier
            .loopVisibility(progress.clock)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val span = maxOf(size.width, 1f)
                val brush = Brush.linearGradient(loop, Offset.Zero, Offset(span, size.height * BAND_SLANT), TileMode.Repeated)
                onDrawWithContent {
                    drawContent()
                    val direction = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                    val shift = span * progress.value * direction
                    translate(left = shift) {
                        drawRect(brush, topLeft = Offset(-shift, 0f), size = size, blendMode = BlendMode.SrcIn)
                    }
                }
            },
        style = style.copy(color = Color.Black),
    )
}

/**
 * 글자가 무작위 문자로 뒤섞여 있다가 앞에서부터 하나씩 제자리를 찾는 텍스트. 해커 영화의 화면처럼 보인다.
 * [text]가 바뀔 때마다 다시 시작한다. 공백은 섞지 않는다. TalkBack은 처음부터 최종 텍스트를 읽는다.
 * '애니메이션 삭제' 접근성 설정이 켜져 있으면 바로 최종 텍스트를 보여 준다.
 *
 * ```kotlin
 * ScrambleText("ACCESS GRANTED", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 24.sp))
 * ```
 *
 * 글자 폭이 달라 줄이 흔들릴 수 있으므로 고정폭 글꼴을 권한다.
 *
 * @param durationMs 마지막 글자가 제자리를 찾을 때까지 걸리는 시간(ms). 0 이하이면 [IllegalArgumentException]을 던진다.
 * @param glyphs 섞을 때 쓰는 문자. 비어 있으면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun ScrambleText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    durationMs: Int = 900,
    glyphs: String = SCRAMBLE_GLYPHS,
) {
    require(durationMs > 0) { "durationMs는 0보다 커야 합니다." }
    require(glyphs.isNotEmpty()) { "glyphs는 비어 있으면 안 됩니다." }
    val still = reducedMotion()
    var shown by remember { mutableStateOf(text) }
    LaunchedEffect(text, still, durationMs, glyphs) {
        if (still || text.isEmpty()) {
            shown = text
            return@LaunchedEffect
        }
        val random = Random(text.hashCode())
        val start = withFrameMillis { it }
        var settled = 0
        while (settled < text.length) {
            val elapsed = withFrameMillis { it } - start
            settled = (elapsed * text.length / durationMs).toInt().coerceAtMost(text.length)
            shown = scrambleFrame(text, settled, glyphs, random)
            if (settled < text.length) delay(SCRAMBLE_TICK_MS)
        }
    }
    ThemedText(
        shown,
        modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        style = style.copy(color = style.color.takeOrElse { LocalGlassContentColor.current }),
    )
}

/** 앞의 [settled]글자는 제자리에 두고 나머지는 [glyphs]에서 무작위로 고른 문자로 바꾼다. 공백은 바꾸지 않는다. */
internal fun scrambleFrame(text: String, settled: Int, glyphs: String, random: Random): String {
    val chars = CharArray(text.length) { i ->
        if (i < settled || text[i].isWhitespace()) text[i] else glyphs[random.nextInt(glyphs.length)]
    }
    return String(chars)
}

/** 쉬는 시간 [pauseMs] 뒤 [periodMs] 동안 0에서 1로 가기를 반복하는 진행률. 멈춰야 하면 0이다. 값은 그리기 단계에서 읽는다. */
private class LoopProgress(val clock: LoopClock, private val periodMs: Int, private val pauseMs: Int, private val easing: Easing) {
    val value: Float
        get() {
            val ms = clock.seconds * MS_PER_SECOND % (periodMs + pauseMs)
            return if (ms < pauseMs) 0f else easing.transform((ms - pauseMs) / periodMs)
        }
}

@Composable
private fun rememberLoopProgress(still: Boolean, periodMs: Int, pauseMs: Int, easing: Easing): LoopProgress {
    val clock = rememberLoopClock(running = !still)
    return remember(clock, periodMs, pauseMs, easing) { LoopProgress(clock, periodMs, pauseMs, easing) }
}

private const val SCRAMBLE_GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&*+=<>/\\"
private const val SCRAMBLE_TICK_MS = 45L
private const val MS_PER_SECOND = 1_000f
private const val PAUSE_MS = 700
private const val BAND_RATIO = 0.3f
private const val BAND_SLANT = 0.6f
