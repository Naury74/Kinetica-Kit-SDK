package com.naury.kinetica.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 문장 속 한 단어가 [interval]마다 다음 단어로 바뀌는 글. 새 단어는 아래에서 올라오며 흐림이 풀리고, 앞 단어는 위로 빠지며 흐려진다.
 * 단어 자리의 폭은 두 단어 폭 사이를 부드럽게 오가므로 뒤 글자가 튀지 않는다. 랜딩 화면 머리글에 쓴다.
 *
 * 화면에 보일 때만 돌고, 화면 밖이면 멈췄다가 돌아오면 이어 간다. '애니메이션 삭제'가 켜져 있으면 첫 단어에 멈춘다.
 * 흐림은 Android 12 이상에서만 보이고, 그 아래에서는 올라오며 나타나기만 한다. 줄이 좁으면 앞 글, 단어, 뒤 글 사이에서 줄을 바꾼다.
 *
 * TalkBack은 '오늘은 산책, 독서, 캠핑 하기 좋은 날'처럼 모든 단어를 한 번에 읽는다. 단어가 바뀔 때 다시 읽지 않도록 live region은 쓰지 않는다.
 *
 * ```kotlin
 * KWordRotate(listOf("산책", "독서", "캠핑"), prefix = "오늘은", suffix = "하기 좋은 날")
 * ```
 *
 * @param words 돌아가며 보일 단어. 비어 있으면 [IllegalArgumentException]을 던진다. 하나면 바뀌지 않는다.
 * @param prefix 단어 앞 글. 비어 있으면 놓지 않는다.
 * @param suffix 단어 뒤 글. 비어 있으면 놓지 않는다.
 * @param style 글 전체의 모양
 * @param wordColor 단어 색. [Color.Unspecified]면 [KineticaColors.accent]를 쓴다.
 * @param interval 한 단어가 머무는 시간(ms). 바뀌는 움직임도 이 안에 든다. 800 이상이어야 하며 아니면 [IllegalArgumentException]을 던진다.
 * @param description TalkBack이 읽을 글. null이면 앞 글, 단어 목록, 뒤 글을 이어 만든다.
 */
@Composable
public fun KWordRotate(
    words: List<String>,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    style: TextStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    wordColor: Color = Color.Unspecified,
    interval: Int = DEFAULT_INTERVAL_MS,
    description: String? = null,
) {
    require(words.isNotEmpty()) { "words가 비어 있습니다." }
    require(interval >= MIN_INTERVAL_MS) { "interval은 $MIN_INTERVAL_MS 이상이어야 합니다." }
    val ink = LocalKineticaContentColor.current
    val base = if (style.color.isSpecified) style else style.copy(color = ink)
    val wordStyle = base.copy(color = if (wordColor.isSpecified) wordColor else KineticaTheme.colors.accent)
    val still = isReducedMotion()
    val clock = rememberLoopClock(running = !still && words.size > 1)
    val timeline = WordTimeline(clock, words.size, interval / MS_PER_SECOND, still)
    val spoken = description ?: listOf(prefix, words.joinToString(", "), suffix).filter { it.isNotEmpty() }.joinToString(" ")
    val gap = with(LocalDensity.current) { if (base.fontSize.isSpecified) base.fontSize.toDp() * GAP_EM else FALLBACK_GAP }
    FlowRow(
        modifier.loopVisibility(clock).clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (prefix.isNotEmpty()) ThemedText(prefix, style = base)
        WordSlot(words, wordStyle, timeline)
        if (suffix.isNotEmpty()) ThemedText(suffix, style = base)
    }
}

/**
 * 시계로 지금 단어와 다음 단어, 바뀌는 정도를 정한다. 한 주기의 끝 [CHANGE_SHARE] 동안만 바뀐다.
 * 그리기와 배치 단계에서만 읽는다.
 */
private class WordTimeline(
    private val clock: LoopClock,
    private val count: Int,
    private val periodSeconds: Float,
    private val still: Boolean,
) {
    private fun cycles(): Float = if (still || count < 2) 0f else clock.seconds / periodSeconds

    fun current(): Int = (floor(cycles()).toLong() % count).toInt()

    fun next(): Int = (current() + 1) % count

    /** 바뀌는 정도(0..1). 0이면 [current]만 보인다. */
    fun change(): Float {
        val c = cycles()
        val phase = c - floor(c)
        val t = ((phase - (1f - CHANGE_SHARE)) / CHANGE_SHARE).coerceIn(0f, 1f)
        return FastOutSlowInEasing.transform(t)
    }
}

/**
 * 모든 단어를 같은 자리에 겹쳐 두고 지금 단어와 다음 단어만 보이게 한다. 단어는 한 번만 측정하고, 폭은 바깥 배치 수정자가
 * 시계를 읽어 두 단어 폭 사이로 정한다. 흐림 효과는 몇 단계로 미리 만들어 프레임마다 새로 만들지 않는다.
 */
@Composable
private fun WordSlot(words: List<String>, style: TextStyle, timeline: WordTimeline) {
    val density = LocalDensity.current
    val blurs: Array<RenderEffect?> = remember(density) {
        val max = with(density) { MAX_BLUR.toPx() }
        Array(BLUR_LEVELS + 1) { level ->
            if (level ==
                0
            ) {
                null
            } else {
                BlurEffect(max * level / BLUR_LEVELS, max * level / BLUR_LEVELS, TileMode.Decal)
            }
        }
    }
    val widths = remember(words) { IntArray(words.size) }
    Layout(
        content = {
            words.forEachIndexed { index, word ->
                ThemedText(
                    word,
                    Modifier.graphicsLayer {
                        val change = timeline.change()
                        val shown = when (index) {
                            timeline.current() -> 1f - change
                            timeline.next() -> if (words.size > 1) change else 0f
                            else -> 0f
                        }
                        val entering = index == timeline.next() && words.size > 1
                        alpha = shown
                        translationY = if (entering) (1f - change) * size.height * RISE else -change * size.height * RISE
                        renderEffect = blurs[((1f - shown) * BLUR_LEVELS).roundToInt().coerceIn(0, BLUR_LEVELS)]
                    },
                    style = style,
                    maxLines = 1,
                )
            }
        },
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0))
                val from = widths[timeline.current()]
                val to = widths[timeline.next()]
                val width = (from + (to - from) * timeline.change()).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
                layout(width, placeable.height) { placeable.placeRelative(0, 0) }
            }
            .clipToBounds(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        placeables.forEachIndexed { i, p -> widths[i] = p.width }
        val width = placeables.maxOf { it.width }
        val height = placeables.maxOf { it.height }
        layout(width, height) { placeables.forEach { it.placeRelative(0, 0) } }
    }
}

private const val DEFAULT_INTERVAL_MS = 2400
private const val MIN_INTERVAL_MS = 800
private const val MS_PER_SECOND = 1000f
private const val CHANGE_SHARE = 0.22f
private const val RISE = 0.55f
private const val BLUR_LEVELS = 4
private const val GAP_EM = 0.28f
private val MAX_BLUR = 6.dp
private val FALLBACK_GAP = 6.dp
