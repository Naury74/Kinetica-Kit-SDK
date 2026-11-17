package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

/**
 * [KCandlestickChart]의 기간 하나(시가, 고가, 저가, 종가).
 *
 * 생성할 때 값이 NaN이나 무한대이거나, [high]가 [open], [close], [low]보다 작거나, [low]가 [open], [close]보다 크거나,
 * [volume]이 음수이면 [IllegalArgumentException]을 던진다.
 *
 * @property volume 거래량. 0이면 거래량 막대를 그리지 않는다.
 * @property label 기간 이름(예: "11/3"). 아래 축, 십자선 말풍선, TalkBack이 읽는다. `null`이면 순서로 읽는다.
 */
@Immutable
public data class Candle(
    val open: Float,
    val high: Float,
    val low: Float,
    val close: Float,
    val volume: Float = 0f,
    val label: String? = null,
) {
    init {
        require(open.isFinite() && high.isFinite() && low.isFinite() && close.isFinite() && volume.isFinite()) {
            "값에 NaN이나 무한대를 넣을 수 없습니다."
        }
        require(high >= maxOf(open, close, low) && low <= minOf(open, close)) { "high는 가장 크고 low는 가장 작아야 합니다." }
        require(volume >= 0f) { "volume은 0 이상이어야 합니다." }
    }

    /** 종가가 시가 이상이면 true(양봉) */
    val isRising: Boolean get() = close >= open
}

/**
 * [KCandlestickChart]가 보이는 기간 범위. 손가락으로 옮기거나 확대한 범위를 화면 회전 뒤에도 기억하려면 [rememberKCandlestickState]로 만든다.
 * 처음에는 가장 최근 기간이 오른쪽 끝에 붙어 있고, 끝에 붙어 있는 동안 기간이 늘면 따라간다.
 *
 * @param initialVisibleCount 처음에 한 화면에 보일 기간 수. 1 미만이면 [IllegalArgumentException]을 던진다.
 */
@Stable
public class KCandlestickState(initialVisibleCount: Int = DEFAULT_VISIBLE) {
    init {
        require(initialVisibleCount >= 1) { "initialVisibleCount는 1 이상이어야 합니다." }
    }

    internal var visible by mutableFloatStateOf(initialVisibleCount.toFloat())

    // 무한대면 끝에 붙어 있다는 뜻이다. 기간 수가 바뀌어도 끝을 따라간다.
    internal var start by mutableFloatStateOf(Float.POSITIVE_INFINITY)

    /** 가장 최근 기간이 오른쪽 끝에 붙어 있으면 true */
    public val isAtEnd: Boolean get() = start == Float.POSITIVE_INFINITY

    /** 가장 최근 기간이 오른쪽 끝에 오도록 옮긴다. */
    public fun scrollToEnd() {
        start = Float.POSITIVE_INFINITY
    }

    /** 기간 [total]개일 때 한 화면에 보이는 기간 수. 최소 [MIN_VISIBLE]개(전체가 그보다 적으면 전체), 최대 전체다. */
    internal fun count(total: Int): Float {
        val most = total.coerceAtLeast(1).toFloat()
        return visible.coerceIn(minOf(MIN_VISIBLE.toFloat(), most), most)
    }

    /** 왼쪽 끝 기간 위치. 소수면 그 기간의 일부가 잘려 보인다. */
    internal fun first(total: Int): Float {
        val last = (total - count(total)).coerceAtLeast(0f)
        return if (start.isNaN()) last else start.coerceIn(0f, last)
    }

    /** 기간 [candles]개만큼 오른쪽(양수)이나 왼쪽으로 옮긴다. 끝에 닿아 더 갈 수 없으면 false */
    internal fun panBy(candles: Float, total: Int): Boolean {
        if (candles == 0f || candles.isNaN()) return true
        val from = first(total)
        val last = (total - count(total)).coerceAtLeast(0f)
        val next = (from + candles).coerceIn(0f, last)
        start = if (next >= last - END_SNAP) Float.POSITIVE_INFINITY else next
        return next != from
    }

    /** [factor]배 확대한다(1보다 크면 확대). [anchor](0..1)는 고정할 가로 위치다. 끝에 붙어 있으면 끝을 고정한다. */
    internal fun zoomBy(factor: Float, anchor: Float, total: Int) {
        if (!(factor > 0f) || !factor.isFinite()) return
        val before = count(total)
        val from = first(total)
        visible =
            (before / factor).coerceIn(minOf(MIN_VISIBLE.toFloat(), total.coerceAtLeast(1).toFloat()), total.coerceAtLeast(1).toFloat())
        if (isAtEnd) return
        val after = count(total)
        val pivot = from + anchor.coerceIn(0f, 1f) * before
        val last = (total - after).coerceAtLeast(0f)
        start = (pivot - anchor.coerceIn(0f, 1f) * after).coerceIn(0f, last)
    }

    /** [index] 기간이 보이도록 가장 적게 옮긴다. */
    internal fun reveal(index: Int, total: Int) {
        val from = first(total)
        val count = count(total)
        when {
            index < floor(from) -> panBy(index - from, total)
            index + 1 > from + count -> panBy(index + 1 - (from + count), total)
        }
    }

    /** 보이는 첫 기간과 마지막 기간 번호(포함). 기간이 없으면 빈 범위다. */
    internal fun visibleIndices(total: Int): IntRange {
        if (total <= 0) return IntRange.EMPTY
        val from = first(total)
        return floor(from).toInt().coerceAtLeast(0)..(ceil(from + count(total)).toInt() - 1).coerceIn(0, total - 1)
    }

    internal companion object {
        val Saver: Saver<KCandlestickState, List<Float>> = Saver(
            save = { listOf(it.visible, it.start) },
            restore = { saved ->
                KCandlestickState(DEFAULT_VISIBLE).apply {
                    saved.getOrNull(0)?.takeIf { it.isFinite() && it >= 1f }?.let { visible = it }
                    saved.getOrNull(1)?.takeIf { !it.isNaN() }?.let { start = it }
                }
            },
        )
    }
}

/** 화면 회전 뒤에도 보이는 범위를 기억하는 [KCandlestickState]를 만든다. */
@Composable
public fun rememberKCandlestickState(initialVisibleCount: Int = DEFAULT_VISIBLE): KCandlestickState =
    rememberSaveable(saver = KCandlestickState.Saver) { KCandlestickState(initialVisibleCount) }

/**
 * 캔들 차트. 기간마다 시가와 종가 사이를 몸통으로, 고가와 저가를 꼬리로 그리고, 오르면 [risingColor], 내리면 [fallingColor]로 칠한다.
 * 아래에는 거래량 막대를, 끝 쪽에는 보이는 범위에 맞춰 자동으로 고른 가격 눈금을 둔다.
 *
 * 가로로 끌면 시간 축을 옮기고 놓은 속도만큼 미끄러진다. 두 손가락으로 벌리거나 오므리면 보이는 기간 수가 바뀐다.
 * 세로로 먼저 움직이면 바깥 스크롤에 넘긴다. 길게 누르면 십자선과 그 기간의 값 말풍선이 나타나 손가락을 따라가고, 손을 떼거나 터치가 취소되면 사라진다.
 * 그리기는 보이는 기간만 하므로 기간이 수천 개여도 한 프레임 비용은 보이는 기간 수에 비례한다.
 * 처음 보일 때 보이는 캔들이 왼쪽부터 차례로 자라고, '애니메이션 삭제' 설정에서는 바로 최종 모양을 그리며 미끄러지지 않는다.
 * 시간 축은 RTL에서도 왼쪽이 처음이다([KLineChart]와 같은 정책).
 *
 * TalkBack은 전체 요약(기간 수, 처음과 마지막 기간, 첫 시가, 최고 고가, 최저 저가, 마지막 종가)을 읽고,
 * 그다음 칸에서 기간 하나의 값을 읽는다. 이 칸의 '이전 항목', '다음 항목' 동작이 십자선을 대신해 기간을 하나씩 옮긴다.
 * 크기를 정하지 않으면 최소 280×200dp이다.
 *
 * ```kotlin
 * val state = rememberKCandlestickState(initialVisibleCount = 30)
 * KCandlestickChart(candles, Modifier.fillMaxWidth().height(260.dp), state = state)
 * ```
 *
 * @param candles 기간. 왼쪽부터 오래된 순서다. 비어 있으면 빈 눈금만 그린다.
 * @param state 보이는 범위
 * @param showVolume 거래량 막대를 그릴지. 거래량이 모두 0이면 그리지 않는다.
 * @param priceFormatter 가격 눈금, 말풍선, TalkBack에 쓸 가격 문자열
 * @param volumeFormatter 말풍선과 TalkBack에 쓸 거래량 문자열
 */
@Composable
public fun KCandlestickChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
    state: KCandlestickState = rememberKCandlestickState(),
    showVolume: Boolean = true,
    risingColor: Color = KChartPalettes.Good,
    fallingColor: Color = KChartPalettes.Critical,
    priceFormatter: (Float) -> String = KChartDefaults::formatValue,
    volumeFormatter: (Float) -> String = KChartDefaults::formatValue,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    // 실시간 시세처럼 같은 SnapshotStateList에 기간을 더해도 최고가, 최저가, 거래량 유무가 따라오도록 목록 읽기를 추적한다.
    val content by remember(candles, showVolume) {
        derivedStateOf { CandleContent(candles, showVolume && candles.any { it.volume > 0f }) }
    }
    val format = CandleFormat(priceFormatter, volumeFormatter, candleNames(), stringResource(R.string.kinetica_ui_data_pair))
    val measurer = rememberTextMeasurer()
    val ink = surface.contentColor
    val plot = remember { CandlePlot() }
    val reveal = rememberChartReveal(CANDLE_REVEAL_MS)
    val cross = remember { CandleCrosshair() }
    val scope = rememberCoroutineScope()
    val still = reducedMotion()
    val frame = CandleFrame(content, state, plot, cross, CandleColors(risingColor, fallingColor, ink))
    val texts = remember(measurer) { CandleTextCache(measurer) }
    val outer = LocalLayoutDirection.current
    val showing by remember(cross) { derivedStateOf { cross.index >= 0 } }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalChartTextDirection provides outer) {
        Box(
            modifier
                .defaultMinSize(CANDLE_MIN_WIDTH, CANDLE_MIN_HEIGHT)
                .chartSummary(candleSummary(content, format))
                .onSizeChanged { plot.size = it }
                .candleGestures(frame, still, scope)
                .drawWithCache {
                    texts.prepare(chartAxisText(ink), format)
                    prepareCandlePlot(plot, content, texts, size.height)
                    onDrawBehind { drawCandles(frame, texts, reveal.value) }
                },
        ) {
            CandleInspector(frame, format)
            ChartBubble(showing, { plot.anchorFor(frame) }, surface) { CandleBubble(content, cross, format) }
        }
    }
}

/**
 * TalkBack이 십자선 대신 쓰는 칸. 지금 기간의 값을 읽고, 사용자 정의 동작으로 이전 기간과 다음 기간으로 옮긴다.
 * 옮기면 화면에도 십자선을 띄워 TalkBack을 켠 채 화면을 보는 사람도 같은 기간을 본다.
 */
@Composable
private fun BoxScope.CandleInspector(frame: CandleFrame, format: CandleFormat) {
    val candles = frame.content.candles
    if (candles.isEmpty()) return
    var focus by rememberSaveable { mutableIntStateOf(-1) }
    val at = focus.takeIf { it in candles.indices } ?: candles.lastIndex
    val description = candleDescription(candles, at, format)
    // 시세가 바뀔 때마다 읽지 않도록, 동작으로 옮긴 직후의 설명일 때만 바뀐 내용을 알린다.
    var moved by remember { mutableStateOf(false) }
    var spoken by remember { mutableStateOf<String?>(null) }
    if (moved) {
        SideEffect {
            spoken = description
            moved = false
        }
    }
    val announce = moved || description == spoken
    val previous = stringResource(R.string.kinetica_ui_candle_previous)
    val next = stringResource(R.string.kinetica_ui_candle_next)
    val move: (Int) -> Boolean = { target ->
        if (target in candles.indices) {
            focus = target
            moved = true
            frame.state.reveal(target, candles.size)
            frame.cross.show(target)
            true
        } else {
            false
        }
    }
    Box(
        Modifier.matchParentSize().semantics(mergeDescendants = true) {
            contentDescription = description
            if (announce) liveRegion = LiveRegionMode.Polite
            customActions = listOf(
                CustomAccessibilityAction(previous) { move(at - 1) },
                CustomAccessibilityAction(next) { move(at + 1) },
            )
        },
    )
}

@Composable
private fun CandleBubble(content: CandleContent, cross: CandleCrosshair, format: CandleFormat) {
    val shown = remember { ChartIndexHolder() }
    if (cross.index >= 0) shown.value = cross.index
    val candle = content.candles.getOrNull(shown.value) ?: return
    ChartBubbleColumn {
        ChartBubbleTitle(candle.label ?: stringResource(R.string.kinetica_ui_chart_point, shown.value + 1))
        ChartBubbleRow(null, format.names.open, format.price(candle.open))
        ChartBubbleRow(null, format.names.high, format.price(candle.high))
        ChartBubbleRow(null, format.names.low, format.price(candle.low))
        ChartBubbleRow(null, format.names.close, format.price(candle.close))
        if (content.hasVolume) ChartBubbleRow(null, format.names.volume, format.volume(candle.volume))
    }
}

@Composable
private fun candleNames(): CandleNames = CandleNames(
    stringResource(R.string.kinetica_ui_candle_open),
    stringResource(R.string.kinetica_ui_candle_high),
    stringResource(R.string.kinetica_ui_candle_low),
    stringResource(R.string.kinetica_ui_candle_close),
    stringResource(R.string.kinetica_ui_candle_volume),
)

@Composable
private fun candleSummary(content: CandleContent, format: CandleFormat): String {
    val candles = content.candles
    if (candles.isEmpty()) return stringResource(R.string.kinetica_ui_heatmap_empty)
    val first = candles.first().label ?: stringResource(R.string.kinetica_ui_chart_point, 1)
    val last = candles.last().label ?: stringResource(R.string.kinetica_ui_chart_point, candles.size)
    val head = pluralStringResource(R.plurals.kinetica_ui_candle_summary, candles.size, candles.size, first, last)
    val range = CandleStats(candles.first().open, content.highest, content.lowest, candles.last().close)
    return head + ". " + joinChartParts(range.parts(format))
}

@Composable
private fun candleDescription(candles: List<Candle>, index: Int, format: CandleFormat): String {
    val candle = candles[index]
    val name = candle.label ?: stringResource(R.string.kinetica_ui_chart_point, index + 1)
    val parts = CandleStats(candle.open, candle.high, candle.low, candle.close).parts(format).toMutableList()
    if (candle.volume > 0f) parts += format.pair(format.names.volume, format.volume(candle.volume))
    return stringResource(R.string.kinetica_ui_chart_entry, name, joinChartParts(parts))
}

private const val DEFAULT_VISIBLE = 40
private const val MIN_VISIBLE = 8
private const val END_SNAP = 0.01f
private const val CANDLE_REVEAL_MS = 900
private val CANDLE_MIN_WIDTH = 280.dp
private val CANDLE_MIN_HEIGHT = 200.dp
