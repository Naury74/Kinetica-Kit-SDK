package com.naury.kinetica.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChartKitMathTest {
    private fun area(rects: FloatArray, i: Int): Float = (rects[i * 4 + 2] - rects[i * 4]) * (rects[i * 4 + 3] - rects[i * 4 + 1])

    @Test
    fun `트리맵 넓이는 값에 비례하고 영역을 빈틈없이 덮는다`() {
        val values = floatArrayOf(6f, 6f, 4f, 3f, 2f, 2f, 1f)
        val rects = squarify(values, 600f, 400f)
        val total = values.sum()
        values.indices.forEach { i ->
            assertThat(area(rects, i)).isWithin(0.5f).of(values[i] / total * 600f * 400f)
            assertThat(rects[i * 4]).isAtLeast(-0.01f)
            assertThat(rects[i * 4 + 1]).isAtLeast(-0.01f)
            assertThat(rects[i * 4 + 2]).isAtMost(600.01f)
            assertThat(rects[i * 4 + 3]).isAtMost(400.01f)
        }
        assertThat(values.indices.sumOf { area(rects, it).toDouble() }).isWithin(1.0).of(240_000.0)
        // squarified는 길쭉한 칸을 피한다. 이 예는 원 논문 예와 같아 가장 길쭉한 칸도 3:1을 넘지 않는다.
        values.indices.forEach { i ->
            val w = rects[i * 4 + 2] - rects[i * 4]
            val h = rects[i * 4 + 3] - rects[i * 4 + 1]
            assertThat(maxOf(w / h, h / w)).isLessThan(3f)
        }
    }

    @Test
    fun `트리맵 칸은 서로 겹치지 않는다`() {
        val values = floatArrayOf(13f, 8f, 5f, 3f, 2f, 1f, 1f, 0.5f)
        val rects = squarify(values, 320f, 200f)
        for (a in values.indices) {
            for (b in values.indices) {
                if (a == b) continue
                val overlapW = minOf(rects[a * 4 + 2], rects[b * 4 + 2]) - maxOf(rects[a * 4], rects[b * 4])
                val overlapH = minOf(rects[a * 4 + 3], rects[b * 4 + 3]) - maxOf(rects[a * 4 + 1], rects[b * 4 + 1])
                assertThat(overlapW <= 0.01f || overlapH <= 0.01f).isTrue()
            }
        }
    }

    @Test
    fun `항목이 하나면 영역 전체를 차지한다`() {
        assertThat(squarify(floatArrayOf(42f), 300f, 120f).toList()).containsExactly(0f, 0f, 300f, 120f).inOrder()
    }

    @Test
    fun `합이 0이거나 영역이 비면 모든 칸이 넓이 0이다`() {
        assertThat(squarify(floatArrayOf(0f, 0f), 300f, 200f).all { it == 0f }).isTrue()
        assertThat(squarify(floatArrayOf(3f, 1f), 0f, 200f).all { it == 0f }).isTrue()
        assertThat(squarify(floatArrayOf(), 300f, 200f)).isEmpty()
    }

    @Test
    fun `음수와 NaN은 넓이 0이고 나머지가 영역을 채운다`() {
        val rects = squarify(floatArrayOf(-5f, 3f, Float.NaN, 1f, Float.POSITIVE_INFINITY), 400f, 100f)
        assertThat(area(rects, 0)).isEqualTo(0f)
        assertThat(area(rects, 2)).isEqualTo(0f)
        assertThat(area(rects, 4)).isEqualTo(0f)
        assertThat(area(rects, 1)).isWithin(0.5f).of(30_000f)
        assertThat(area(rects, 3)).isWithin(0.5f).of(10_000f)
    }

    @Test
    fun `아주 작은 값도 0보다 큰 칸을 받고 전체 넓이는 그대로다`() {
        val rects = squarify(floatArrayOf(1_000_000f, 1f, 500_000f), 300f, 300f)
        assertThat(area(rects, 1)).isGreaterThan(0f)
        assertThat((0..2).sumOf { area(rects, it).toDouble() }).isWithin(1.0).of(90_000.0)
    }

    @Test
    fun `비율은 가장 큰 나머지로 반올림해 합이 100이다`() {
        assertThat(roundedPercents(doubleArrayOf(1.0, 1.0, 1.0)).toList()).containsExactly(34, 33, 33).inOrder()
        assertThat(roundedPercents(doubleArrayOf(869.0, 181.0, 84.0, 36.0, 34.0)).toList()).containsExactly(72, 15, 7, 3, 3).inOrder()
        assertThat(roundedPercents(doubleArrayOf(0.0, 0.0)).toList()).containsExactly(0, 0)
        assertThat(roundedPercents(doubleArrayOf(-3.0, 1.0, Double.NaN)).toList()).containsExactly(0, 100, 0).inOrder()
    }

    @Test
    fun `눈금 간격은 1 2 2·5 5 배수에서 고르고 범위 안 눈금만 센다`() {
        assertThat(niceStep(0.3)).isWithin(1e-9).of(0.5)
        assertThat(niceStep(1.7)).isWithin(1e-9).of(2.0)
        assertThat(niceStep(2.2)).isWithin(1e-9).of(2.5)
        assertThat(niceStep(3625.0)).isWithin(1e-9).of(5000.0)
        val ticks = ChartTicks()
        ticks.update(48_900.0, 63_400.0, 4)
        assertThat(ticks.step).isEqualTo(5000.0)
        assertThat((0 until ticks.count).map { ticks.value(it) }).containsExactly(50_000.0, 55_000.0, 60_000.0).inOrder()
        ticks.update(5.0, 5.0, 4)
        assertThat(ticks.count).isEqualTo(1)
        ticks.update(Double.NaN, 1.0, 4)
        assertThat(ticks.count).isEqualTo(0)
    }

    @Test
    fun `캔들 상태는 끝에 붙어 시작하고 옮기기와 확대가 범위 안에 머문다`() {
        val state = KCandlestickState(40)
        assertThat(state.isAtEnd).isTrue()
        assertThat(state.visibleIndices(5_000)).isEqualTo(4_960..4_999)
        state.panBy(-100f, 5_000)
        assertThat(state.isAtEnd).isFalse()
        assertThat(state.first(5_000)).isEqualTo(4_860f)
        state.zoomBy(2f, 0.5f, 5_000)
        assertThat(state.count(5_000)).isEqualTo(20f)
        assertThat(state.first(5_000)).isEqualTo(4_870f)
        state.zoomBy(0.0001f, 0.5f, 5_000)
        assertThat(state.count(5_000)).isEqualTo(5_000f)
        assertThat(state.first(5_000)).isEqualTo(0f)
        state.zoomBy(1_000_000f, 0f, 5_000)
        assertThat(state.count(5_000)).isEqualTo(8f)
        assertThat(state.panBy(-1f, 5_000)).isFalse()
        state.panBy(10_000f, 5_000)
        assertThat(state.isAtEnd).isTrue()
    }

    @Test
    fun `캔들이 보이는 수보다 적거나 없으면 전체를 보인다`() {
        val state = KCandlestickState(40)
        assertThat(state.count(5)).isEqualTo(5f)
        assertThat(state.first(5)).isEqualTo(0f)
        assertThat(state.visibleIndices(5)).isEqualTo(0..4)
        assertThat(state.visibleIndices(0).isEmpty()).isTrue()
        state.reveal(1, 5)
        assertThat(state.visibleIndices(5)).isEqualTo(0..4)
    }

    @Test
    fun `보이지 않는 캔들로 옮기면 가장 적게 움직여 보이게 한다`() {
        val state = KCandlestickState(10)
        state.reveal(100, 1_000)
        assertThat(state.visibleIndices(1_000)).isEqualTo(100..109)
        state.reveal(115, 1_000)
        assertThat(state.visibleIndices(1_000)).isEqualTo(106..115)
    }
}
