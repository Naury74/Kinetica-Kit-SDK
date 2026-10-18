// 파일 이름은 대표 API인 KIndexBar을(를) 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** [KIndexBar] 색인 글자 묶음과 이름에서 색인 글자를 구하는 도우미 */
public object KIndexBarDefaults {
    /** 한글 초성 14자. 된소리는 예사소리 칸에 넣는다. */
    public val Hangul: List<String> = listOf("ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")

    /** 로마자 A~Z와 그 밖의 글자를 모으는 `#` */
    public val Latin: List<String> = ('A'..'Z').map { it.toString() } + OTHER

    /** 한글 다음 로마자. 한국어 연락처 앱 순서다. */
    public val HangulAndLatin: List<String> = Hangul + Latin

    /**
     * [text] 첫 글자가 들어갈 색인 글자. 한글 음절은 초성(된소리는 예사소리), 로마자는 대문자, 그 밖의 글자와 빈 문자열은 `#`이다.
     *
     * ```kotlin
     * KIndexBarDefaults.keyOf("김민지") // "ㄱ"
     * KIndexBarDefaults.keyOf("anna")   // "A"
     * ```
     */
    public fun keyOf(text: String): String {
        val c = text.trimStart().firstOrNull() ?: return OTHER
        return when {
            c in SYLLABLE_FIRST..SYLLABLE_LAST -> CHOSEONG[(c - SYLLABLE_FIRST) / SYLLABLES_PER_INITIAL]
            c.toString() in Hangul -> c.toString()
            c.uppercaseChar() in 'A'..'Z' -> c.uppercaseChar().toString()
            else -> OTHER
        }
    }

    private const val OTHER = "#"
    private const val SYLLABLE_FIRST = '가'
    private const val SYLLABLE_LAST = '힣'
    private const val SYLLABLES_PER_INITIAL = 588

    // 초성 19자 순서. 된소리는 바로 앞 예사소리로 합친다.
    private val CHOSEONG = listOf(
        "ㄱ", "ㄱ", "ㄴ", "ㄷ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅂ", "ㅅ", "ㅅ", "ㅇ", "ㅈ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ",
    )
}

/**
 * 연락처 목록 옆에 세우는 색인 막대. 글자를 누르거나 막대를 위아래로 끌면 손가락 아래 글자가 바뀔 때마다 [onSelect]가 불리고 햅틱이 온다.
 * 끄는 동안 막대 시작 쪽에 큰 글자 말풍선이 손가락을 따라다닌다. RTL에서는 말풍선이 오른쪽에 뜬다.
 * 세로 공간이 모자라면(큰 글자, 가로 화면) 글자를 건너뛰고 사이에 점을 찍는다. 끌기는 그때도 모든 글자를 고를 수 있다.
 * TalkBack은 막대를 '색인' 하나로 읽고 [selected] 글자를 상태로 알린다. 위아래로 쓸면 글자를 하나씩 옮기고, 글자가 32개 이하면
 * 글자마다 '…(으)로 이동' 사용자 지정 동작도 준다(Android는 노드 하나에 동작을 32개까지만 받는다).
 *
 * ```kotlin
 * KIndexBar(KIndexBarDefaults.Hangul, onSelect = { i -> scope.launch { list.scrollToItem(firstIndexOf(KIndexBarDefaults.Hangul[i])) } })
 * ```
 *
 * @param labels 색인 글자. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param onSelect 고른 글자의 [labels] 순번
 * @param selected 지금 목록 맨 위 항목이 속한 글자 순번. 주면 그 글자를 강조색 원으로 표시한다.
 */
@Composable
public fun KIndexBar(
    labels: List<String>,
    onSelect: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(labels.isNotEmpty()) { "labels가 비어 있으면 안 됩니다." }
    val touch = remember { IndexTouch() }
    val haptic = kHapticFeedback()
    val latest = rememberUpdatedState(onSelect)
    val accent = KineticaTheme.colors.accent
    val ink = surface.contentColor
    val name = stringResource(R.string.kinetica_ui_index_bar)
    val jumpFormat = stringResource(R.string.kinetica_ui_index_jump)
    val actions = remember(labels, jumpFormat) {
        labels.mapIndexed { i, label ->
            CustomAccessibilityAction(jumpFormat.format(label)) {
                latest.value(i)
                true
            }
        }
    }
    val cell = with(LocalDensity.current) { (LABEL_SP.sp.toDp() * CELL_LINE).coerceAtLeast(MIN_CELL) }
    BoxWithConstraints(modifier.widthIn(min = BAR_WIDTH), contentAlignment = Alignment.Center) {
        val fit = ((maxHeight - BAR_PADDING * 2) / cell).toInt().coerceAtLeast(MIN_SLOTS)
        val slots = remember(labels.size, fit) { indexSlots(labels.size, fit) }
        Box(contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .clearAndSetSemantics {
                        contentDescription = name
                        selected?.let { stateDescription = labels[it] }
                        if (labels.size > 1) {
                            val at = selected ?: 0
                            progressBarRangeInfo = ProgressBarRangeInfo(at.toFloat(), 0f..labels.lastIndex.toFloat(), labels.size - 2)
                            setProgress { target ->
                                val next = target.roundToInt().coerceIn(labels.indices)
                                if (next == at) return@setProgress false
                                latest.value(next)
                                true
                            }
                        }
                        if (actions.size <= MAX_CUSTOM_ACTIONS) customActions = actions
                    }
                    .background(ink.copy(alpha = if (touch.active != null) TOUCH_BACK else 0f), RoundedCornerShape(BAR_WIDTH / 2))
                    .indexGestures(labels.size, touch) { i ->
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        latest.value(i)
                    }
                    .padding(vertical = BAR_PADDING),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                slots.forEach { slot ->
                    IndexCell(slot?.let { labels[it] }, slot != null && slot == selected, cell, accent, surface.muted(DOT_ALPHA))
                }
            }
            touch.active?.let { IndexBubble(labels[it], touch, surface, accent) }
        }
    }
}

/** 손가락이 닿아 있는 동안의 순번과 세로 위치(px). 위치는 배치 단계에서만 읽는다. */
private class IndexTouch {
    var active by mutableStateOf<Int?>(null)
    var y by mutableFloatStateOf(0f)
}

private fun Modifier.indexGestures(count: Int, touch: IndexTouch, onChange: (Int) -> Unit): Modifier = pointerInput(count) {
    // 위아래 여백까지 눌리게 하고, 고르는 글자는 여백을 뺀 글자 줄 안의 위치로 정한다.
    fun pick(y: Float) {
        val pad = BAR_PADDING.toPx()
        val span = (size.height - pad * 2).coerceAtLeast(1f)
        touch.y = y.coerceIn(pad, size.height - pad)
        val index = ((y - pad) / span * count).toInt().coerceIn(0, count - 1)
        if (index != touch.active) {
            touch.active = index
            onChange(index)
        }
    }
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        pick(down.position.y)
        var change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
        while (change != null && change.pressed) {
            change.consume()
            pick(change.position.y)
            change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
        }
        touch.active = null
    }
}

/** 칸마다 보여 줄 글자 순번. null은 건너뛴 글자 자리의 점이다. 첫 글자와 끝 글자는 늘 보인다. */
internal fun indexSlots(count: Int, fit: Int): List<Int?> {
    if (count <= fit) return List(count) { it }
    val odd = if (fit % 2 == 0) fit - 1 else fit
    val shown = (odd + 1) / 2
    return List(odd) { slot ->
        if (slot % 2 == 1) null else ((slot / 2) * (count - 1).toFloat() / (shown - 1)).roundToInt()
    }
}

@Composable
private fun IndexCell(label: String?, selected: Boolean, cell: Dp, accent: Color, dot: Color) {
    Box(
        Modifier
            .size(BAR_WIDTH, cell)
            .drawBehind { if (selected) drawCircle(accent, size.minDimension / 2 * SELECTED_SHARE) },
        contentAlignment = Alignment.Center,
    ) {
        if (label == null) {
            Box(Modifier.size(DOT).background(dot, CircleShape))
        } else {
            ThemedText(
                label,
                style = TextStyle(
                    color = if (selected) KineticaTheme.colors.onAccent else accent,
                    fontSize = LABEL_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

/** 막대 시작 쪽에 떠서 손가락 높이를 따라다니는 큰 글자 말풍선. */
@Composable
private fun IndexBubble(label: String, touch: IndexTouch, surface: SurfaceStyle, accent: Color) {
    Box(
        Modifier
            // 크기 0인 자리에서 막대 가운데 기준으로 시작 쪽에 놓는다. 막대 폭이 말풍선 때문에 넓어지지 않게 하려는 것이다.
            .layout { measurable, _ ->
                val bubble = measurable.measure(Constraints())
                layout(0, 0) {
                    val y = touch.y.roundToInt() - bubble.height / 2
                    bubble.placeRelative(-(BUBBLE_GAP.roundToPx() + bubble.width), y)
                }
            }
            .size(BUBBLE)
            .kSurface(surface.forPopup(), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        ThemedText(label, style = TextStyle(color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
    }
}

private val BAR_WIDTH = 22.dp
private val BAR_PADDING = 6.dp
private val MIN_CELL = 14.dp
private val DOT = 4.dp
private val BUBBLE = 56.dp
private val BUBBLE_GAP = 30.dp
private const val LABEL_SP = 11
private const val CELL_LINE = 1.4f
private const val MIN_SLOTS = 3
private const val TOUCH_BACK = 0.08f
private const val DOT_ALPHA = 0.4f
private const val SELECTED_SHARE = 0.9f
