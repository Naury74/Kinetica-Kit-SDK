package com.naury.kinetica.motion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransitionDriverTest {
    private var nanos = 0L
    private val ends = mutableListOf<TransitionEnd>()
    private val driver = TransitionDriver(clock = { nanos }, onEnd = ends::add)

    private fun advance(ms: Long) {
        nanos += ms * 1_000_000L
        driver.onFrame()
    }

    @Test
    fun `35퍼센트에서 놓으면 처음으로 돌아가고 끝을 알리지 않는다`() {
        driver.startDrag()
        driver.drag(0.35)
        driver.release(progressPerSecond = 0.0)
        advance(100)
        assertThat(driver.state.value.isSettling).isTrue()
        advance(500)

        assertThat(driver.state.value).isEqualTo(TransitionState(progress = 0.0))
        assertThat(ends).containsExactly(TransitionEnd.Canceled)
        assertThat(driver.isCompleted).isFalse()
    }

    @Test
    fun `80퍼센트에서 놓으면 끝까지 가고 끝을 한 번만 알린다`() {
        driver.startDrag()
        driver.drag(0.8)
        driver.release(0.0)
        repeat(10) { advance(100) }
        driver.animateTo(forward = true)
        driver.startDrag()
        driver.drag(0.1)

        assertThat(driver.state.value.progress).isEqualTo(1.0)
        assertThat(ends).containsExactly(TransitionEnd.Completed)
    }

    @Test
    fun `빠르게 튕기면 덜 끌었어도 끝까지 간다`() {
        driver.startDrag()
        driver.drag(0.2)
        driver.release(progressPerSecond = 3.0)
        advance(1_000)

        assertThat(ends).containsExactly(TransitionEnd.Completed)
    }

    @Test
    fun `되돌아가는 중에 다시 잡으면 그 자리에서 이어 잡는다`() {
        driver.startDrag()
        driver.drag(0.4)
        driver.release(0.0)
        advance(140)
        val caught = driver.state.value.progress

        driver.startDrag()

        assertThat(caught).isGreaterThan(0.0)
        assertThat(caught).isLessThan(0.4)
        assertThat(driver.state.value).isEqualTo(TransitionState(progress = caught, isDragging = true))
        advance(1_000)
        assertThat(driver.state.value.progress).isEqualTo(caught)
        assertThat(ends).isEmpty()
    }

    @Test
    fun `진행률은 0과 1 사이로 맞추고 잡지 않으면 끌기를 무시한다`() {
        driver.drag(0.5)
        assertThat(driver.state.value.progress).isEqualTo(0.0)
        driver.startDrag()
        driver.drag(4.0)
        assertThat(driver.state.value.progress).isEqualTo(1.0)
        driver.drag(Double.NaN)
        assertThat(driver.state.value.progress).isEqualTo(0.0)
    }

    @Test
    fun `속도를 끄는 방향으로 투영해 거리로 나눈다`() {
        assertThat(TransitionDriver.projectedVelocity(300.0, 1_000.0, 0.0, 1.0, 500.0)).isEqualTo(2.0)
        assertThat(TransitionDriver.projectedVelocity(300.0, -1_000.0, 0.0, 1.0, 500.0)).isEqualTo(-2.0)
        assertThat(TransitionDriver.projectedVelocity(300.0, 1_000.0, 0.0, 1.0, 0.0)).isEqualTo(0.0)
    }
}
