package com.naury.kinetica.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naury.kinetica.core.animation.Timeline

/**
 * 타임라인 시간 축의 보기 상태(확대 배율, 가로 스크롤). 문서에 속하지 않으므로 실행 취소되지 않는다.
 * 길이는 모두 px이고, "보기 x"는 화면에 보이는 영역의 왼쪽 끝부터 잰 값이다.
 *
 * @param defaultPxPerMs 처음 배율(1ms의 px). 확대 범위는 이 값의 [MIN_ZOOM]..[MAX_ZOOM]배다.
 * @param startPx 0ms 앞의 여백.
 * @param endPx 끝 시각 뒤의 여백.
 */
internal class TimelineView(private val defaultPxPerMs: Float, val startPx: Float, val endPx: Float) {
    var pxPerMs by mutableFloatStateOf(defaultPxPerMs)
        private set
    private var rawScroll by mutableFloatStateOf(0f)

    /** 보이는 영역의 너비. 그려질 때 정해진다. */
    var viewportPx by mutableFloatStateOf(0f)

    var durationMs by mutableLongStateOf(0L)

    /** 켜져 있으면 재생 위치가 화면 밖으로 나갈 때 따라 스크롤한다. */
    var followPlayhead by mutableStateOf(true)

    val minPxPerMs: Float get() = defaultPxPerMs * MIN_ZOOM
    val maxPxPerMs: Float get() = defaultPxPerMs * MAX_ZOOM
    val contentPx: Float get() = startPx + durationMs * pxPerMs + endPx
    val maxScrollPx: Float get() = (contentPx - viewportPx).coerceAtLeast(0f)

    /** 가로 스크롤 위치. 배율이나 화면 너비가 바뀌어도 범위 안으로 맞춰 읽는다. */
    val scrollPx: Float get() = rawScroll.coerceIn(0f, maxScrollPx)

    /** [timeMs]의 보기 x. */
    fun viewX(timeMs: Double): Float = startPx + timeMs.toFloat() * pxPerMs - scrollPx

    /** 보기 x [x]의 시각(ms). 범위로 자르지 않는다. */
    fun timeAt(x: Float): Double = ((x + scrollPx - startPx) / pxPerMs).toDouble()

    fun scrollBy(dx: Float) {
        rawScroll = (scrollPx + dx).coerceIn(0f, maxScrollPx)
    }

    /** 배율을 [factor]배 한다. 보기 x [anchorX]에 있던 시각은 그 자리에 남는다. */
    fun zoomBy(factor: Float, anchorX: Float = viewportPx / 2) {
        if (!factor.isFinite() || factor <= 0f) return
        val time = timeAt(anchorX)
        pxPerMs = (pxPerMs * factor).coerceIn(minPxPerMs, maxPxPerMs)
        rawScroll = (startPx + time.toFloat() * pxPerMs - anchorX).coerceIn(0f, maxScrollPx)
    }

    /** timeline 전체가 보이는 영역에 들어가도록 배율을 맞추고 처음으로 스크롤한다. */
    fun fit() {
        if (durationMs <= 0L || viewportPx <= startPx + endPx) return
        pxPerMs = ((viewportPx - startPx - endPx) / durationMs).coerceIn(minPxPerMs, maxPxPerMs)
        rawScroll = 0f
    }

    /** [timeMs]가 보이는 영역 밖이면, 그 시각이 왼쪽에서 [REVEAL_LEAD]만큼 떨어진 곳에 오도록 스크롤한다. */
    fun reveal(timeMs: Double) {
        val x = viewX(timeMs)
        if (viewportPx <= 0f || x in 0f..viewportPx) return
        rawScroll = (startPx + timeMs.toFloat() * pxPerMs - viewportPx * REVEAL_LEAD).coerceIn(0f, maxScrollPx)
    }

    /** 지금 배율에서 눈금 간격. 작은 눈금은 [minMinorPx], 큰 눈금(시각 표시)은 [minMajorPx] 이상 떨어진다. */
    fun ticks(minMinorPx: Float, minMajorPx: Float): TickSteps = tickSteps(pxPerMs, minMinorPx, minMajorPx)

    companion object {
        const val MIN_ZOOM = 0.125f
        const val MAX_ZOOM = 25f
        const val REVEAL_LEAD = 0.2f
    }
}

/** 눈금자 간격(ms). [majorMs]는 [minorMs]의 배수다. */
internal data class TickSteps(val minorMs: Long, val majorMs: Long)

private val TICK_CANDIDATES = longArrayOf(10, 25, 50, 100, 250, 500, 1_000, 2_000, 5_000, 10_000, 30_000, 60_000)

/** [pxPerMs] 배율에서 [minMinorPx], [minMajorPx] 이상 떨어지는 가장 촘촘한 눈금 간격. */
internal fun tickSteps(pxPerMs: Float, minMinorPx: Float, minMajorPx: Float): TickSteps {
    val minor = TICK_CANDIDATES.firstOrNull { it * pxPerMs >= minMinorPx } ?: TICK_CANDIDATES.last()
    val major = TICK_CANDIDATES.firstOrNull { it >= minor && it % minor == 0L && it * pxPerMs >= minMajorPx } ?: (minor * MAJOR_FALLBACK)
    return TickSteps(minor, major)
}

private const val MAJOR_FALLBACK = 4

/** 타임라인에서 고른 키들과 선택 모드. 문서가 바뀌어 없어진 키는 [retain]으로 뺀다. */
internal class KeySelection {
    var keys by mutableStateOf<Set<SelectedKey>>(emptySet())

    /** 켜져 있으면 탭이 선택을 바꾸지 않고 키를 더하거나 뺀다. 길게 누르면 켜진다. */
    var selectMode by mutableStateOf(false)

    /** [key]를 누른다. [additive]이면 선택에 넣거나 빼고, 아니면 그 키만 고른다. */
    fun tap(key: SelectedKey, additive: Boolean) {
        keys = when {
            !additive -> setOf(key)
            key in keys -> keys - key
            else -> keys + key
        }
    }

    /** 사각 선택 결과 [found]로 선택을 바꾼다. [additive]이면 [base]에 더한다. */
    fun marquee(found: Set<SelectedKey>, additive: Boolean, base: Set<SelectedKey>) {
        keys = if (additive) base + found else found
    }

    fun clear() {
        keys = emptySet()
        selectMode = false
    }

    /** [timeline]에 없는 키를 선택에서 뺀다. */
    fun retain(timeline: Timeline?) {
        val live = keys.filterTo(LinkedHashSet()) { key ->
            timeline?.track(key.nodeId, key.property)?.keyframes?.any { it.timeMs == key.timeMs } == true
        }
        if (live.size != keys.size) keys = live
    }
}
