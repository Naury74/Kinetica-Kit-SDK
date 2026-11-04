package com.naury.kinetica.ui

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 단어마다 흐림이 풀리며 살짝 떠오르듯 차례로 나타나는 글. AI 답변이나 요약처럼 글이 막 만들어졌다는 느낌을 줄 때 쓴다.
 *
 * 글이 스트리밍으로 계속 늘어나도 된다. 새 [text]가 앞의 글로 시작하면 이미 나타난 단어는 그대로 두고 새 단어만 이어서 나타난다.
 * 앞부분이 달라지면 처음부터 다시 나타난다. API 31 이상에서는 나타나는 중인 단어를 흐림 정도에 따라 몇 개의 층으로 묶어 블러를 건다.
 * 그 아래 버전에서는 블러 없이 투명도와 이동만 쓴다. '애니메이션 삭제' 설정이 켜져 있으면 바로 모두 보여 준다.
 * TalkBack은 나타나는 정도와 상관없이 전체 글을 한 번에 읽는다.
 *
 * ```kotlin
 * KBlurTextReveal(answer, style = TextStyle(fontSize = 17.sp, lineHeight = 26.sp))
 * ```
 *
 * @param wordDelayMs 다음 단어가 나타나기 시작하는 간격(ms). 0 이상이어야 하고 음수면 [IllegalArgumentException]을 던진다.
 * @param wordDurationMs 단어 하나가 다 나타나는 데 걸리는 시간(ms). 0보다 커야 하고 아니면 [IllegalArgumentException]을 던진다.
 * @param blur 나타나기 시작할 때의 블러 반지름. 0이면 블러 없이 나타난다.
 * @param rise 나타나기 시작할 때 아래로 내려가 있는 거리
 */
@Composable
public fun KBlurTextReveal(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    wordDelayMs: Int = 70,
    wordDurationMs: Int = 600,
    blur: Dp = 8.dp,
    rise: Dp = 6.dp,
) {
    require(wordDelayMs >= 0) { "wordDelayMs는 0 이상이어야 합니다." }
    require(wordDurationMs > 0) { "wordDurationMs는 0보다 커야 합니다." }
    val still = reducedMotion()
    val plan = remember { RevealSchedule() }
    val now = remember { mutableLongStateOf(0L) }
    LaunchedEffect(text, still, wordDelayMs) {
        if (still) return@LaunchedEffect
        val start = withFrameMillis { it }
        plan.update(text, start, wordDelayMs.toLong())
        now.longValue = start
        while (now.longValue < plan.finishAt(wordDurationMs)) {
            now.longValue = withFrameMillis { it }
        }
    }
    val measurer = rememberTextMeasurer()
    val textStyle = themed(style.copy(color = style.color.takeOrElse { LocalKineticaContentColor.current }))
    var laidOut by remember { mutableStateOf<TextLayoutResult?>(null) }
    val boxes = remember { WordBoxCache() }
    val layers = rememberBlurLayers()
    Box(
        modifier
            .clearAndSetSemantics { this.text = AnnotatedString(text) }
            .layout { measurable, constraints ->
                val result = measurer.measure(text, textStyle, constraints = Constraints(maxWidth = constraints.maxWidth))
                laidOut = result
                val width = constraints.constrainWidth(result.size.width)
                val height = constraints.constrainHeight(result.size.height)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .drawBehind {
                val result = laidOut ?: return@drawBehind
                if (still) {
                    drawText(result)
                    return@drawBehind
                }
                // 앞부분이 바뀐 새 글은 시각을 정하기 전 한 프레임 동안 옛 시각으로 다 보일 수 있어 그리지 않는다.
                if (!result.layoutInput.text.text.startsWith(plan.source)) return@drawBehind
                val look = RevealLook(wordDurationMs, blur.toPx(), rise.toPx(), EDGE_PAD.toPx())
                drawReveal(boxes.of(result), plan, now.longValue, look, layers)
            },
    )
}

/** 블러 층. API 31 미만이면 null이라 블러 없이 그린다. */
@Composable
private fun rememberBlurLayers(): BlurLayers? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val layers = List(BLUR_LEVELS) { rememberGraphicsLayer() }
    return remember(layers) { BlurLayers(layers) }
}

/** 단어 하나가 나타나는 모양. 픽셀 값은 그리기 단계에서 정한다. */
private class RevealLook(val durationMs: Int, val blurPx: Float, val risePx: Float, val padPx: Float)

/**
 * 단어마다 나타나기 시작하는 시각(프레임 시계 ms). 글이 앞부분을 유지한 채 늘어나면 기존 단어의 시각은 그대로 두고 새 단어만 뒤에 붙인다.
 */
internal class RevealSchedule {
    /** 마지막으로 받은 글. 그리는 글이 이것으로 시작하지 않으면 아직 새 글의 시각을 정하기 전이다. */
    var source: String = ""
        private set
    private val starts = ArrayList<Long>()

    /** [text]로 바꾼다. 새 단어는 [now]와 앞 단어 시작 뒤 [delayMs] 중 늦은 때에 나타나기 시작한다. */
    fun update(text: String, now: Long, delayMs: Long) {
        if (!text.startsWith(source)) starts.clear()
        source = text
        val count = WORD.findAll(text).count()
        while (starts.size > count) starts.removeAt(starts.lastIndex)
        while (starts.size < count) {
            val previous = starts.lastOrNull()
            starts += if (previous == null) now else maxOf(now, previous + delayMs)
        }
    }

    /** 단어 [index]가 [now]에 나타난 정도(0..1). 아직 시각을 정하지 않은 단어는 0이다. */
    fun progress(index: Int, now: Long, durationMs: Int): Float {
        val start = starts.getOrNull(index) ?: return 0f
        return ((now - start).toFloat() / durationMs).coerceIn(0f, 1f)
    }

    /** 마지막 단어까지 다 나타나는 시각 */
    fun finishAt(durationMs: Int): Long = (starts.lastOrNull() ?: Long.MIN_VALUE / 2) + durationMs
}

/** 배치된 글의 단어와 그 단어가 줄마다 차지하는 사각형. 글이나 배치가 바뀔 때만 다시 계산한다. */
private class WordBoxCache {
    private var layout: TextLayoutResult? = null
    var boxes: List<List<Rect>> = emptyList()
        private set
    var firstLine: IntArray = IntArray(0)
        private set

    fun of(result: TextLayoutResult): WordBoxCache {
        if (result === layout) return this
        layout = result
        val words = WORD.findAll(result.layoutInput.text).toList()
        val lines = IntArray(words.size)
        boxes = List(words.size) { i ->
            val start = words[i].range.first
            lines[i] = result.getLineForOffset(start)
            wordPieces(result, start, words[i].range.last + 1)
        }
        firstLine = lines
        return this
    }

    fun layoutResult(): TextLayoutResult = checkNotNull(layout)
}

/** 글자 [start]..[end] 범위가 줄마다 차지하는 사각형. 줄 위아래 끝까지 덮어야 글자의 윗부분과 아랫부분이 잘리지 않는다. */
private fun wordPieces(result: TextLayoutResult, start: Int, end: Int): List<Rect> {
    if (start >= end) return emptyList()
    val pieces = ArrayList<Rect>(1)
    var from = start
    while (from < end) {
        val line = result.getLineForOffset(from)
        val to = minOf(end, result.getLineEnd(line, visibleEnd = true)).coerceAtLeast(from + 1)
        val first = result.getBoundingBox(from)
        val last = result.getBoundingBox(to - 1)
        pieces += Rect(minOf(first.left, last.left), result.getLineTop(line), maxOf(first.right, last.right), result.getLineBottom(line))
        from = maxOf(to, result.getLineEnd(line))
    }
    return pieces
}

/**
 * 다 나타난 앞부분은 한두 번에 그리고, 나타나는 중인 몇 단어만 따로 그린다. 단어마다 글 전체를 그리면 긴 글에서 그리기 명령이
 * 단어 수만큼 늘어나기 때문이다. 시작 시각이 단어 순서대로라 나타난 정도는 뒤 단어일수록 작다.
 */
private fun DrawScope.drawReveal(boxes: WordBoxCache, plan: RevealSchedule, now: Long, look: RevealLook, layers: BlurLayers?) {
    val result = boxes.layoutResult()
    val count = boxes.boxes.size
    var done = 0
    while (done < count && plan.progress(done, now, look.durationMs) >= 1f) done++
    if (done == count) {
        drawText(result)
        return
    }
    drawSettled(result, boxes, done, look.padPx)
    layers?.begin()
    for (i in done until count) {
        val eased = FastOutSlowInEasing.transform(plan.progress(i, now, look.durationMs))
        if (eased <= 0f) break
        val haze = (1f - eased) * look.blurPx
        val level = if (layers == null || haze < MIN_BLUR_PX) -1 else ((1f - eased) * BLUR_LEVELS).toInt().coerceIn(0, BLUR_LEVELS - 1)
        if (level < 0) {
            boxes.boxes[i].forEach { drawPiece(result, it, eased, look) }
        } else {
            layers?.add(level, i, eased)
        }
    }
    layers?.flush(this, result, boxes, look)
}

/** 다 나타난 단어 [0, done)를 그린다. 처음 나타나는 중인 단어의 줄 위쪽은 한 번에, 그 줄의 나타난 단어들은 한 사각형으로 묶어 그린다. */
private fun DrawScope.drawSettled(result: TextLayoutResult, boxes: WordBoxCache, done: Int, pad: Float) {
    if (done == 0) return
    val line = boxes.firstLine[done]
    val top = result.getLineTop(line)
    if (top > 0f) clipRect(bottom = top) { drawText(result) }
    var left = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var i = done - 1
    while (i >= 0 && (boxes.boxes[i].lastOrNull()?.top ?: -1f) >= top) {
        val piece = boxes.boxes[i].last()
        left = minOf(left, piece.left)
        right = maxOf(right, piece.right)
        i--
    }
    if (left < right) clipRect(left - pad, top, right + pad, result.getLineBottom(line)) { drawText(result) }
}

/** 단어 조각 하나를 [eased]만큼 나타난 모습으로 그린다. 자르는 사각형도 함께 올라가야 글자가 잘려 보이지 않는다. */
private fun DrawScope.drawPiece(result: TextLayoutResult, piece: Rect, eased: Float, look: RevealLook) {
    translate(top = (1f - eased) * look.risePx) {
        clipRect(piece.left - look.padPx, piece.top, piece.right + look.padPx, piece.bottom) {
            drawText(result, alpha = eased)
        }
    }
}

/**
 * 블러 정도가 비슷한 단어끼리 묶어 그리는 몇 개의 층. 단어마다 블러 층을 만들면 나타나는 단어 수만큼 화면 밖 버퍼가 생기므로,
 * 흐림 정도를 [BLUR_LEVELS]단계로 나눠 층마다 반지름을 고정한다. 층 크기는 그 단계 단어들을 감싸는 사각형만큼이다.
 */
private class BlurLayers(private val layers: List<GraphicsLayer>) {
    private val clip = Path()
    private val radii = FloatArray(BLUR_LEVELS) { -1f }
    private val members = List(BLUR_LEVELS) { ArrayList<Int>() }
    private val amounts = List(BLUR_LEVELS) { ArrayList<Float>() }

    fun begin() {
        members.forEach { it.clear() }
        amounts.forEach { it.clear() }
    }

    fun add(level: Int, word: Int, eased: Float) {
        members[level] += word
        amounts[level] += eased
    }

    fun flush(scope: DrawScope, result: TextLayoutResult, boxes: WordBoxCache, look: RevealLook) {
        for (level in 0 until BLUR_LEVELS) {
            if (members[level].isEmpty()) continue
            val radius = look.blurPx * (level + 1) / BLUR_LEVELS
            val layer = layers[level]
            if (radius != radii[level]) {
                layer.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
                radii[level] = radius
            }
            scope.drawLevel(level, result, boxes, look)
        }
    }

    private fun DrawScope.drawLevel(level: Int, result: TextLayoutResult, boxes: WordBoxCache, look: RevealLook) {
        val layer = layers[level]
        val margin = radii[level] + look.padPx + look.risePx
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        members[level].forEach { word ->
            boxes.boxes[word].forEach { piece ->
                left = minOf(left, piece.left)
                top = minOf(top, piece.top)
                right = maxOf(right, piece.right)
                bottom = maxOf(bottom, piece.bottom)
            }
        }
        if (left >= right || top >= bottom) return
        val x = floor(left - margin).toInt()
        val y = floor(top - margin).toInt()
        layer.topLeft = IntOffset(x, y)
        val area = IntSize(ceil(right + margin).toInt() - x, ceil(bottom + margin).toInt() - y)
        // 같은 층 단어들은 나타난 정도가 비슷하므로 평균 하나로 한 번에 그린다. 단어마다 그리면 나타나는 단어 수만큼 문단 전체를 다시 그린다.
        val eased = amounts[level].average().toFloat()
        clip.reset()
        members[level].forEach { word ->
            boxes.boxes[word].forEach { piece ->
                clip.addRect(Rect(piece.left - look.padPx, piece.top, piece.right + look.padPx, piece.bottom))
            }
        }
        layer.record(area) {
            translate(-x.toFloat(), (1f - eased) * look.risePx - y) {
                clipPath(clip) { drawText(result, alpha = eased) }
            }
        }
        drawLayer(layer)
    }
}

private val WORD = Regex("\\S+")
private val EDGE_PAD = 1.5.dp
private const val BLUR_LEVELS = 3
private const val MIN_BLUR_PX = 0.6f
