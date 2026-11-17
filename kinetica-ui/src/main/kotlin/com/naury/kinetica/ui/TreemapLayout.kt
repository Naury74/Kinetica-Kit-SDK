package com.naury.kinetica.ui

/**
 * squarified 트리맵 배치. [values] 순서대로 사각형 하나씩 `left, top, right, bottom`을 이어 담은 배열(크기 values.size × 4)을 돌려준다.
 * 사각형 넓이는 값에 비례하고 모두 합치면 [width] × [height]를 빈틈없이 덮는다. 큰 값이 앞(왼쪽 위)에 온다.
 *
 * 0, 음수, NaN, 무한대 값과 크기가 0 이하인 영역은 넓이 0인 사각형(모두 0)이 된다. 양수 값의 합이 0이면 모두 넓이 0이다.
 */
internal fun squarify(values: FloatArray, width: Float, height: Float): FloatArray {
    val out = FloatArray(values.size * RECT_FIELDS)
    if (!(width > 0f && height > 0f)) return out
    val order = values.indices.filter { values[it].isFinite() && values[it] > 0f }.sortedByDescending { values[it] }
    val total = order.sumOf { values[it].toDouble() }
    if (order.isEmpty() || total <= 0.0) return out
    val scale = width.toDouble() * height / total
    val areas = DoubleArray(order.size) { values[order[it]] * scale }
    val box = TreemapBox(width.toDouble(), height.toDouble())
    var start = 0
    while (start < order.size) {
        val end = rowEnd(areas, start, box.shortSide())
        box.place(areas, start, end, last = end == order.size) { slot, l, t, r, b ->
            val at = order[slot] * RECT_FIELDS
            out[at + RECT_LEFT] = l.toFloat()
            out[at + RECT_TOP] = t.toFloat()
            out[at + RECT_RIGHT] = r.toFloat()
            out[at + RECT_BOTTOM] = b.toFloat()
        }
        start = end
    }
    return out
}

/** [start]부터 한 줄에 넣을 끝 번호(제외). 다음 항목을 넣어 가장 길쭉한 비율이 나빠지면 멈춘다. */
private fun rowEnd(areas: DoubleArray, start: Int, side: Double): Int {
    if (side <= 0.0) return areas.size
    var end = start + 1
    var sum = areas[start]
    var worst = worstRatio(areas[start], areas[start], sum, side)
    while (end < areas.size) {
        val nextSum = sum + areas[end]
        val nextWorst = worstRatio(areas[start], areas[end], nextSum, side)
        if (nextWorst > worst) break
        sum = nextSum
        worst = nextWorst
        end++
    }
    return end
}

// 내림차순으로 넣으므로 줄의 가장 큰 넓이는 첫 항목, 가장 작은 넓이는 마지막 항목이다.
private fun worstRatio(largest: Double, smallest: Double, sum: Double, side: Double): Double {
    val side2 = side * side
    val sum2 = sum * sum
    return maxOf(side2 * largest / sum2, sum2 / (side2 * smallest))
}

/** 아직 채우지 않은 남은 영역. 줄을 하나 놓을 때마다 짧은 변 쪽에서 줄 두께만큼 줄어든다. */
private class TreemapBox(private var width: Double, private var height: Double) {
    private var left = 0.0
    private var top = 0.0
    private val right = width
    private val bottom = height

    fun shortSide(): Double = minOf(width, height)

    fun place(areas: DoubleArray, start: Int, end: Int, last: Boolean, emit: (Int, Double, Double, Double, Double) -> Unit) {
        val sum = (start until end).sumOf { areas[it] }
        val vertical = width >= height
        val side = if (vertical) height else width
        // 마지막 줄은 남은 영역을 모두 채워 부동소수 오차로 생긴 틈을 없앤다.
        val thickness = if (last) (if (vertical) width else height) else (if (side > 0.0) sum / side else 0.0)
        var cursor = if (vertical) top else left
        for (k in start until end) {
            val length = if (thickness > 0.0) areas[k] / thickness else 0.0
            val endCursor = if (k == end - 1) (if (vertical) bottom else right) else cursor + length
            if (vertical) emit(k, left, cursor, left + thickness, endCursor) else emit(k, cursor, top, endCursor, top + thickness)
            cursor = endCursor
        }
        if (vertical) {
            left += thickness
            width = (width - thickness).coerceAtLeast(0.0)
        } else {
            top += thickness
            height = (height - thickness).coerceAtLeast(0.0)
        }
    }
}

internal const val RECT_FIELDS = 4
internal const val RECT_LEFT = 0
internal const val RECT_TOP = 1
internal const val RECT_RIGHT = 2
internal const val RECT_BOTTOM = 3
