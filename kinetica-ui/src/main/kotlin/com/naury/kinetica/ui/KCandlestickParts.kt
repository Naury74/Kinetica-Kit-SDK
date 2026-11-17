package com.naury.kinetica.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlinx.coroutines.Job

/** 기간 목록과 전체 최고가, 최저가 */
internal class CandleContent(val candles: List<Candle>, val hasVolume: Boolean) {
    val highest: Float = candles.maxOfOrNull { it.top } ?: 0f
    val lowest: Float = candles.minOfOrNull { it.bottom } ?: 0f
}

internal class CandleNames(val open: String, val high: String, val low: String, val close: String, val volume: String)

internal class CandleFormat(
    val price: (Float) -> String,
    val volume: (Float) -> String,
    val names: CandleNames,
    private val pairPattern: String,
) {
    fun pair(name: String, value: String): String = String.format(pairPattern, name, value)
}

internal class CandleStats(val open: Float, val high: Float, val low: Float, val close: Float) {
    fun parts(format: CandleFormat): List<String> = listOf(
        format.pair(format.names.open, format.price(open)),
        format.pair(format.names.high, format.price(high)),
        format.pair(format.names.low, format.price(low)),
        format.pair(format.names.close, format.price(close)),
    )
}

internal class CandleColors(val rising: Color, val falling: Color, val ink: Color)

internal class CandleFrame(
    val content: CandleContent,
    val state: KCandlestickState,
    val plot: CandlePlot,
    val cross: CandleCrosshair,
    val colors: CandleColors,
)

/** 길게 눌러 띄운 십자선. [y]가 NaN이면 가로선을 종가 높이에 둔다(TalkBack 동작으로 띄운 경우). */
internal class CandleCrosshair {
    var index by mutableIntStateOf(-1)
    var y by mutableFloatStateOf(Float.NaN)
    var fling: Job? = null

    fun show(at: Int, fingerY: Float = Float.NaN) {
        index = at
        y = fingerY
    }

    fun clear() {
        index = -1
        y = Float.NaN
    }
}

/** 차트 영역 나누기와 좌표 변환. 크기와 글자 폭은 그리기 준비 단계에서, 가격 범위는 그릴 때마다 보이는 기간으로 정한다. */
internal class CandlePlot {
    var size by mutableStateOf(IntSize.Zero)
    var axisWidth = 0f
    var timeHeight = 0f
    var timeLabelWidth = 0f
    var gap = 0f
    var top = 0f
    var bodyMax = 0f
    var minBody = 0f
    var volumeHeight = 0f
    var low = 0f
        private set
    var high = 1f
        private set

    /** 보이는 기간의 가장 큰 거래량. 0이면 거래량 막대를 그리지 않는다. */
    var peakVolume = 0f
        private set
    val ticks = ChartTicks()

    val plotWidth: Float get() = (size.width - axisWidth).coerceAtLeast(0f)

    fun priceBottom(hasVolume: Boolean): Float = size.height - timeHeight - if (hasVolume) volumeHeight + gap else 0f

    fun volumeBottom(): Float = size.height - timeHeight

    fun slot(state: KCandlestickState, total: Int): Float = plotWidth / state.count(total)

    fun centerX(index: Int, state: KCandlestickState, total: Int): Float = (index - state.first(total) + CHART_HALF) * slot(state, total)

    fun indexAt(x: Float, state: KCandlestickState, total: Int): Int {
        if (total <= 0) return -1
        val slot = slot(state, total)
        if (slot <= 0f) return -1
        return (state.first(total) + x.coerceIn(0f, plotWidth) / slot).toInt().coerceIn(state.visibleIndices(total))
    }

    /** 보이는 기간의 최저가와 최고가에 위아래 여유를 둔 가격 범위를 정한다. 비용은 보이는 기간 수에 비례한다. */
    fun updateRange(candles: List<Candle>, range: IntRange) {
        var lo = Float.POSITIVE_INFINITY
        var hi = Float.NEGATIVE_INFINITY
        var peak = 0f
        for (i in range) {
            lo = minOf(lo, candles[i].bottom)
            hi = maxOf(hi, candles[i].top)
            peak = maxOf(peak, candles[i].volume)
        }
        peakVolume = peak
        if (lo > hi) {
            lo = 0f
            hi = 1f
        }
        if (lo == hi) {
            lo -= 1f
            hi += 1f
        }
        val extra = (hi - lo) * PRICE_PADDING
        low = lo - extra
        high = hi + extra
    }

    fun priceY(value: Float, hasVolume: Boolean): Float = top + (high - value) / (high - low) * (priceBottom(hasVolume) - top)

    fun priceAt(y: Float, hasVolume: Boolean): Float {
        val span = (priceBottom(hasVolume) - top).coerceAtLeast(1f)
        return high - (y - top) / span * (high - low)
    }

    /** 십자선 기간의 꼬리 위아래에 붙이는 말풍선 자리 */
    fun anchorFor(frame: CandleFrame): ChartBubbleAnchor? {
        val candles = frame.content.candles
        val index = frame.cross.index
        if (index !in candles.indices || size == IntSize.Zero) return null
        updateRange(candles, frame.state.visibleIndices(candles.size))
        val x = centerX(index, frame.state, candles.size)
        val volume = frame.content.hasVolume
        return ChartBubbleAnchor(x, priceY(candles[index].top, volume), priceY(candles[index].bottom, volume), sideways = true)
    }
}

/**
 * 축 글자 배치 결과를 기억해 두는 작은 캐시. 옮기거나 확대할 때 같은 눈금과 기간 이름이 계속 다시 나오므로
 * 프레임마다 글자를 다시 재지 않는다. 가득 차면 가장 오래된 것부터 바꾼다.
 */
internal class CandleTextCache(private val measurer: TextMeasurer) {
    private val keys = LongArray(CACHE_SIZE)
    private val texts = arrayOfNulls<String>(CACHE_SIZE)
    private val values = arrayOfNulls<TextLayoutResult>(CACHE_SIZE)
    private var crossBits = 0L
    private var crossText: String? = null
    private var crossLayout: TextLayoutResult? = null
    private var next = 0
    var style: TextStyle = TextStyle.Default
        private set
    lateinit var format: CandleFormat
        private set

    /** 글자 모양이나 형식이 바뀌면 비운다. 그리기 준비 단계에서 부른다. */
    fun prepare(style: TextStyle, format: CandleFormat) {
        if (style != this.style || !this::format.isInitialized || format.price !== this.format.price) clear()
        this.style = style
        this.format = format
    }

    fun cached(key: Long): TextLayoutResult? {
        for (i in 0 until CACHE_SIZE) {
            val hit = values[i]
            if (hit != null && texts[i] == null && keys[i] == key) return hit
        }
        return null
    }

    fun put(key: Long, text: String): TextLayoutResult = store(key, null, measurer.measure(text, style))

    /**
     * 기간 이름 배치 결과. 데이터가 바뀌면 같은 순번에 다른 이름이 오므로 순번이 아니라 글 자체로 찾는다.
     */
    fun label(text: String): TextLayoutResult {
        for (i in 0 until CACHE_SIZE) {
            val hit = values[i]
            if (hit != null && texts[i] == text) return hit
        }
        return store(0L, text, measurer.measure(text, style))
    }

    /**
     * 십자선 가격. 손가락 y마다 가격이 달라 나눠 쓰는 칸을 밀어내지 않도록 전용 칸 하나에 두고,
     * 형식을 거친 글이 같으면 다시 재지 않는다.
     */
    fun crossLabel(price: Float): TextLayoutResult {
        val bits = price.toRawBits().toLong()
        val held = crossLayout
        if (held != null && bits == crossBits) return held
        crossBits = bits
        val text = format.price(price)
        if (held != null && text == crossText) return held
        return measurer.measure(text, style).also {
            crossText = text
            crossLayout = it
        }
    }

    private fun store(key: Long, text: String?, made: TextLayoutResult): TextLayoutResult {
        keys[next] = key
        texts[next] = text
        values[next] = made
        next = (next + 1) % CACHE_SIZE
        return made
    }

    fun measure(text: String): TextLayoutResult = measurer.measure(text, style)

    private fun clear() {
        values.fill(null)
        texts.fill(null)
        crossLayout = null
        crossText = null
        next = 0
    }
}

/** 크기와 글자 폭에 따라 정하는 값. 그리기 준비 단계에서 부른다. */
internal fun Density.prepareCandlePlot(plot: CandlePlot, content: CandleContent, texts: CandleTextCache, height: Float) {
    val format = texts.format
    val widest = maxOf(texts.measure(format.price(content.highest)).size.width, texts.measure(format.price(content.lowest)).size.width)
    plot.gap = AXIS_GAP.toPx()
    plot.axisWidth = widest + plot.gap * 2
    val sample = content.candles.lastOrNull()?.label ?: content.candles.size.toString()
    val time = texts.measure(sample)
    plot.timeLabelWidth = time.size.width.toFloat()
    plot.timeHeight = time.size.height + plot.gap
    plot.top = time.size.height / 2f
    plot.bodyMax = BODY_MAX.toPx()
    plot.minBody = MIN_BODY.toPx()
    plot.volumeHeight = if (content.hasVolume) height * VOLUME_SHARE else 0f
}

internal fun DrawScope.drawCandles(frame: CandleFrame, texts: CandleTextCache, reveal: Float) {
    val plot = frame.plot
    val candles = frame.content.candles
    val total = candles.size
    val range = frame.state.visibleIndices(total)
    plot.updateRange(candles, range)
    drawPriceGrid(frame, texts)
    if (total == 0) return
    clipRect(right = plot.plotWidth) {
        for (i in range) drawCandle(frame, i, reveal)
    }
    drawTimeLabels(frame, texts, range)
    drawCrosshair(frame, texts)
}

private fun DrawScope.drawPriceGrid(frame: CandleFrame, texts: CandleTextCache) {
    val plot = frame.plot
    val volume = frame.content.hasVolume
    val bottom = plot.priceBottom(volume)
    val target = ((bottom - plot.top) / TICK_SPACING.toPx()).toInt().coerceIn(MIN_TICKS, MAX_TICKS)
    val ticks = plot.ticks
    ticks.update(plot.low.toDouble(), plot.high.toDouble(), target)
    val ink = frame.colors.ink
    for (k in 0 until ticks.count) {
        val value = ticks.value(k).toFloat()
        val y = plot.priceY(value, volume)
        drawLine(ink.copy(alpha = CHART_GRID_ALPHA), Offset(0f, y), Offset(plot.plotWidth, y), GRID_WIDTH.toPx())
        val key = value.toRawBits().toLong() and PRICE_BITS
        val label = texts.cached(key) ?: texts.put(key, texts.format.price(value))
        val labelY = (y - label.size.height / 2f).coerceIn(0f, (size.height - label.size.height).coerceAtLeast(0f))
        drawText(label, topLeft = Offset(plot.plotWidth + plot.gap, labelY))
    }
}

private fun DrawScope.drawCandle(frame: CandleFrame, index: Int, reveal: Float) {
    val plot = frame.plot
    val state = frame.state
    val total = frame.content.candles.size
    val candle = frame.content.candles[index]
    val count = state.count(total)
    val along = ((index - state.first(total)) / count).coerceIn(0f, 1f) * REVEAL_STAGGER
    val grow = chartStage(reveal, along, along + REVEAL_SPAN)
    if (grow <= 0f) return
    val volume = frame.content.hasVolume
    val slot = plot.slot(state, total)
    val x = plot.centerX(index, state, total)
    val color = if (candle.isRising) frame.colors.rising else frame.colors.falling
    val mid = plot.priceY((candle.open + candle.close) / 2f, volume)
    fun grown(value: Float): Float = mid + (plot.priceY(value, volume) - mid) * grow
    val bodyWidth = (slot * BODY_SHARE).coerceAtMost(plot.bodyMax).coerceAtLeast(1f)
    val wick = minOf(WICK_WIDTH.toPx(), bodyWidth)
    drawLine(color, Offset(x, grown(candle.top)), Offset(x, grown(candle.bottom)), wick)
    val bodyTop = grown(maxOf(candle.open, candle.close))
    val bodyHeight = (grown(minOf(candle.open, candle.close)) - bodyTop).coerceAtLeast(plot.minBody)
    val corner = CornerRadius(minOf(BODY_CORNER.toPx(), bodyWidth / 2f, bodyHeight / 2f))
    drawRoundRect(color, Offset(x - bodyWidth / 2f, bodyTop), Size(bodyWidth, bodyHeight), corner)
    if (volume && candle.volume > 0f) {
        val share = if (plot.peakVolume > 0f) candle.volume / plot.peakVolume else 0f
        val height = plot.volumeHeight * share * grow
        drawRect(color, Offset(x - bodyWidth / 2f, plot.volumeBottom() - height), Size(bodyWidth, height), alpha = VOLUME_ALPHA)
    }
}

private fun DrawScope.drawTimeLabels(frame: CandleFrame, texts: CandleTextCache, range: IntRange) {
    val plot = frame.plot
    val candles = frame.content.candles
    val slot = plot.slot(frame.state, candles.size)
    if (slot <= 0f) return
    val stride = ceil((plot.timeLabelWidth + plot.gap * 2) / slot).toInt().coerceAtLeast(1)
    val y = size.height - plot.timeHeight + plot.gap / 2f
    var lastEnd = Float.NEGATIVE_INFINITY
    for (i in range) {
        val name = candles[i].label
        if (i % stride != 0 || name == null) continue
        val label = texts.label(name)
        val x = (plot.centerX(i, frame.state, candles.size) - label.size.width / 2f)
            .coerceIn(0f, (plot.plotWidth - label.size.width).coerceAtLeast(0f))
        if (x >= lastEnd + plot.gap) {
            drawText(label, topLeft = Offset(x, y))
            lastEnd = x + label.size.width
        }
    }
}

private fun DrawScope.drawCrosshair(frame: CandleFrame, texts: CandleTextCache) {
    val plot = frame.plot
    val candles = frame.content.candles
    val index = frame.cross.index
    if (index !in candles.indices) return
    val volume = frame.content.hasVolume
    val x = plot.centerX(index, frame.state, candles.size)
    val fingerY = frame.cross.y
    val y = (if (fingerY.isNaN()) plot.priceY(candles[index].close, volume) else fingerY).coerceIn(plot.top, plot.priceBottom(volume))
    val ink = frame.colors.ink
    val line = GRID_WIDTH.toPx()
    drawLine(ink.copy(alpha = CROSS_ALPHA), Offset(x, 0f), Offset(x, plot.volumeBottom()), line)
    drawLine(ink.copy(alpha = CROSS_ALPHA), Offset(0f, y), Offset(plot.plotWidth, y), line)
    val label = texts.crossLabel(plot.priceAt(y, volume))
    val pad = plot.gap / 2f
    val top = (y - label.size.height / 2f - pad).coerceIn(0f, (size.height - label.size.height - pad * 2).coerceAtLeast(0f))
    drawRoundRect(
        ink,
        Offset(plot.plotWidth + plot.gap - pad, top),
        Size(label.size.width + pad * 2, label.size.height + pad * 2),
        CornerRadius(pad * 2),
    )
    val text = if (ink.luminance() > CHART_HALF) Color.Black else Color.White
    drawText(label, color = text, topLeft = Offset(plot.plotWidth + plot.gap, top + pad))
}

private const val CACHE_SIZE = 48
private const val PRICE_PADDING = 0.06f
private const val MIN_TICKS = 2
private const val MAX_TICKS = 6
private const val BODY_SHARE = 0.68f
private const val REVEAL_STAGGER = 0.45f
private const val REVEAL_SPAN = 0.55f
private const val VOLUME_SHARE = 0.18f
private const val VOLUME_ALPHA = 0.35f
private const val CROSS_ALPHA = 0.5f
private const val PRICE_BITS = 0xFFFFFFFFL
private val AXIS_GAP = 6.dp
private val TICK_SPACING = 40.dp
private val GRID_WIDTH = 1.dp
private val WICK_WIDTH = 1.dp
private val BODY_MAX = 14.dp
private val MIN_BODY = 1.dp
private val BODY_CORNER = 1.5.dp
