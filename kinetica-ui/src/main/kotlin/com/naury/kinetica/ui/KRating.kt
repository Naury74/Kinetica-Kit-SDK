package com.naury.kinetica.ui

import android.graphics.CornerPathEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 별점. 별을 누르거나 가로로 끌어 점수를 고르고, 새로 채워지는 별은 앞에서부터 차례로 톡 튀며 채워진다.
 * 누르면 손을 뗄 때 고르고, 가로로 끌기 시작한 뒤에는 끄는 대로 바뀐다. 세로로 먼저 움직이면 부모 스크롤에 넘긴다.
 * RTL에서는 오른쪽 별이 첫 번째다. TalkBack에서는 값 조절 컨트롤로 읽히고 볼륨 키나 스와이프로 [allowHalf]에 맞는 단위씩 바꾼다.
 *
 * ```kotlin
 * var score by remember { mutableFloatStateOf(3f) }
 * KRating(score, onRatingChange = { score = it }, allowHalf = true)
 * ```
 *
 * @param rating 현재 점수(0..[max]). 범위를 벗어나면 그 안으로 맞춰 그리고, NaN은 0으로 본다.
 * @param onRatingChange 사용자가 고른 점수를 받는다. `null`이면 읽기 전용으로 그리고 입력을 받지 않는다.
 *   누르거나 끌어서는 최소 한 단위(별 반 개 또는 한 개)를 고르며, 0은 TalkBack 값 조절로만 고를 수 있다.
 * @param max 별 개수. 1 미만이면 [IllegalArgumentException]을 던진다.
 * @param allowHalf true면 별 반 개 단위로 고른다.
 * @param starSize 별 하나의 크기. 별 사이 간격은 크기의 1/8이다.
 * @param color 채운 별 색. 빈 별은 텍스트 색을 옅게 칠한다.
 */
@Composable
public fun KRating(
    rating: Float,
    onRatingChange: ((Float) -> Unit)?,
    modifier: Modifier = Modifier,
    max: Int = 5,
    allowHalf: Boolean = false,
    starSize: Dp = 32.dp,
    color: Color = Color(STAR_GOLD),
) {
    require(max >= 1) { "max는 1 이상이어야 합니다." }
    val shown = if (rating.isNaN()) 0f else rating.coerceIn(0f, max.toFloat())
    val step = if (allowHalf) HALF else 1f
    val motion = remember(max) { RatingMotion(max, shown) }
    LaunchedEffect(motion, shown) { motion.animateTo(shown, this) }
    val empty = LocalKineticaContentColor.current.copy(alpha = EMPTY_ALPHA)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val gap = starSize / GAP_DIVISOR
    val description = ratingDescription(shown, max)
    val label = stringResource(R.string.kinetica_ui_rating)
    val input = if (onRatingChange == null) Modifier else Modifier.ratingInput(shown, onRatingChange, RatingGrid(max, step, rtl, gap))
    Spacer(
        modifier
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = description
                if (onRatingChange != null) {
                    progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..max.toFloat(), steps = (max / step).roundToInt() - 1)
                    setProgress { target ->
                        if (target.isNaN()) return@setProgress false
                        val next = (Math.round(target / step) * step).coerceIn(0f, max.toFloat())
                        if (next != shown) onRatingChange(next)
                        true
                    }
                }
            }
            .then(input)
            .size(starSize * max + gap * (max - 1), starSize)
            .drawWithCache {
                val starPx = size.height
                val cell = starPx + gap.toPx()
                val look = StarLook(starPath(starPx), starPx, rtl, empty, color)
                onDrawBehind {
                    for (i in 0 until max) {
                        val left = if (rtl) size.width - starPx - i * cell else i * cell
                        translate(left = left) { drawStar(look, motion, i) }
                    }
                }
            },
    )
}

private class StarLook(val star: Path, val starPx: Float, val rtl: Boolean, val empty: Color, val color: Color)

private fun DrawScope.drawStar(look: StarLook, motion: RatingMotion, index: Int) {
    val starPx = look.starPx
    val pop = motion.pops[index].value
    val fill = motion.fills[index].value.coerceIn(0f, 1f)
    scale(pop, pivot = Offset(starPx / 2f, starPx / 2f)) {
        drawPath(look.star, look.empty)
        if (fill <= 0f) return@scale
        val from = if (look.rtl) starPx * (1f - fill) else 0f
        clipRect(left = from, right = from + starPx * fill) { drawPath(look.star, look.color) }
    }
}

/** 별 배치. 누른 위치(px)를 점수로 바꾼다. */
private class RatingGrid(val max: Int, val step: Float, val rtl: Boolean, val gap: Dp) {
    fun valueAt(x: Float, width: Float, starPx: Float, gapPx: Float): Float {
        val fromStart = if (rtl) width - x else x
        val cell = starPx + gapPx
        val index = floor(fromStart / cell).coerceIn(0f, max - 1f)
        val within = ((fromStart - index * cell) / starPx).coerceIn(0f, 1f)
        return (ceil((index + within) / step) * step).coerceIn(step, max.toFloat())
    }
}

@Composable
private fun Modifier.ratingInput(rating: Float, onChange: (Float) -> Unit, grid: RatingGrid): Modifier {
    val latest by rememberUpdatedState(rating)
    val callback by rememberUpdatedState(onChange)
    val haptic = LocalHapticFeedback.current
    return pointerInput(grid.max, grid.step, grid.rtl, grid.gap) {
        val gapPx = grid.gap.toPx()
        var picked = latest
        fun pick(x: Float) {
            val next = grid.valueAt(x, size.width.toFloat(), size.height.toFloat(), gapPx)
            if (next == picked) return
            picked = next
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            callback(next)
        }
        awaitEachGesture {
            val down = awaitFirstDown()
            picked = latest
            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            if (start == null) {
                // 움직이지 않고 뗐을 때만 탭이다. 세로 스크롤이 가져갔으면(소비됨) 고르지 않는다.
                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                if (up != null && !up.pressed && !up.isConsumed) {
                    pick(up.position.x)
                    up.consume()
                }
                return@awaitEachGesture
            }
            pick(start.position.x)
            horizontalDrag(start.id) { change ->
                pick(change.position.x)
                change.consume()
            }
        }
    }
}

/** 별마다 채움 정도와 튀는 크기. 점수가 오르면 새로 채워지는 별을 앞에서부터 차례로 튀게 한다. */
private class RatingMotion(max: Int, initial: Float) {
    val fills = List(max) { Animatable(fillOf(initial, it)) }
    val pops = List(max) { Animatable(1f) }
    private var last = initial

    fun animateTo(rating: Float, scope: kotlinx.coroutines.CoroutineScope) {
        val from = last
        last = rating
        val rising = rating > from
        var order = 0
        fills.forEachIndexed { i, fill ->
            val target = fillOf(rating, i)
            if (target == fill.targetValue) return@forEachIndexed
            val wait = if (rising && target > fillOf(from, i)) STAGGER_MS * order++ else 0L
            scope.launch {
                delay(wait)
                if (rising && target > fill.value) {
                    pops[i].snapTo(POP_FROM)
                    launch { pops[i].animateTo(1f, spring(dampingRatio = POP_DAMPING, stiffness = POP_STIFFNESS)) }
                }
                fill.animateTo(target, tween(FILL_MS))
            }
        }
    }
}

private fun fillOf(rating: Float, index: Int): Float = (rating - index).coerceIn(0f, 1f)

@Composable
private fun ratingDescription(rating: Float, max: Int): String {
    val number = remember { NumberFormat.getInstance().apply { maximumFractionDigits = 1 } }
    return pluralStringResource(R.plurals.kinetica_ui_rating_value, max, number.format(rating), max)
}

/** 모서리가 살짝 둥근 오각별. 크기가 바뀔 때만 만든다. */
private fun starPath(sizePx: Float): Path {
    val sharp = android.graphics.Path()
    val c = sizePx / 2f
    val outer = sizePx * OUTER_RATIO
    val inner = outer * INNER_RATIO
    for (i in 0 until STAR_POINTS * 2) {
        val r = if (i % 2 == 0) outer else inner
        val angle = -PI / 2 + i * PI / STAR_POINTS
        val x = c + (cos(angle) * r).toFloat()
        val y = c + STAR_DROP * sizePx + (sin(angle) * r).toFloat()
        if (i == 0) sharp.moveTo(x, y) else sharp.lineTo(x, y)
    }
    sharp.close()
    val paint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
        pathEffect = CornerPathEffect(sizePx * STAR_CORNER)
    }
    val rounded = android.graphics.Path()
    paint.getFillPath(sharp, rounded)
    return rounded.asComposePath()
}

private const val STAR_GOLD = 0xFFFFB400
private const val EMPTY_ALPHA = 0.2f
private const val HALF = 0.5f
private const val GAP_DIVISOR = 8
private const val STAR_POINTS = 5
private const val OUTER_RATIO = 0.5f
private const val INNER_RATIO = 0.46f
private const val STAR_DROP = 0.04f
private const val STAR_CORNER = 0.08f
private const val STAGGER_MS = 55L
private const val FILL_MS = 140
private const val POP_FROM = 0.6f
private const val POP_DAMPING = 0.35f
private const val POP_STIFFNESS = 500f
