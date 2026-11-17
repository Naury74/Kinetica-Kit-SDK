package com.naury.kinetica.ui

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * [values] 각각이 합에서 차지하는 비율(%)을 정수로. 가장 큰 나머지 방식이라 합이 정확히 100이 된다.
 * 0 이하, NaN, 무한대 값은 0%로 본다. 양수 합이 0이면 모두 0이다.
 */
internal fun roundedPercents(values: DoubleArray): IntArray {
    val clean = DoubleArray(values.size) { if (values[it].isFinite() && values[it] > 0.0) values[it] else 0.0 }
    val total = clean.sum()
    val out = IntArray(values.size)
    if (total <= 0.0) return out
    val exact = DoubleArray(values.size) { clean[it] / total * PERCENT_TOTAL }
    var given = 0
    for (i in exact.indices) {
        out[i] = floor(exact[i]).toInt()
        given += out[i]
    }
    // 나머지가 큰 순서로 1%씩 더한다. 나머지가 같으면 앞 항목이 먼저 받는다.
    val byRemainder = exact.indices.sortedByDescending { exact[it] - out[it] }
    var k = 0
    while (given < PERCENT_TOTAL && k < byRemainder.size) {
        if (clean[byRemainder[k]] > 0.0) {
            out[byRemainder[k]]++
            given++
        }
        k++
    }
    return out
}

/**
 * 값 축 눈금. [update]로 범위를 주면 1, 2, 2.5, 5 × 10ⁿ 간격 가운데 [target]개 안팎이 되는 간격을 고른다.
 * 그리기 단계에서 매 프레임 불러도 새 객체를 만들지 않도록 결과를 필드에 담는다.
 */
internal class ChartTicks {
    /** 범위 안 첫 눈금 값 */
    var first: Double = 0.0
        private set

    /** 눈금 간격. 범위가 비었으면 0이다. */
    var step: Double = 0.0
        private set

    /** 범위 안 눈금 수 */
    var count: Int = 0
        private set

    fun update(low: Double, high: Double, target: Int) {
        val span = high - low
        if (!(span > 0.0) || !span.isFinite() || target < 1) {
            first = low
            step = 0.0
            count = if (low.isFinite()) 1 else 0
            return
        }
        step = niceStep(span / target)
        first = ceil(low / step - EPSILON) * step
        count = (floor((high - first) / step + EPSILON).toInt() + 1).coerceIn(0, MAX_TICKS)
    }

    fun value(index: Int): Double = first + step * index
}

/** [raw] 이상인 가장 작은 1, 2, 2.5, 5 × 10ⁿ */
internal fun niceStep(raw: Double): Double {
    if (!(raw > 0.0) || !raw.isFinite()) return 1.0
    val magnitude = 10.0.pow(floor(log10(raw)))
    val fraction = raw / magnitude
    val nice = NICE_FRACTIONS.firstOrNull { fraction <= it + EPSILON } ?: NICE_FRACTIONS.last()
    return nice * magnitude
}

private val NICE_FRACTIONS = doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0)
private const val PERCENT_TOTAL = 100
private const val EPSILON = 1e-9
private const val MAX_TICKS = 64
