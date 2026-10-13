package com.naury.kinetica.motion

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.animation.TimelineMarker
import org.junit.Test

class PlaybackControllerTest {
    private class ManualClock : FrameClock {
        var nanos = 1_000_000_000L

        fun advance(ms: Long) {
            nanos += ms * 1_000_000L
        }

        override fun timeNanos(): Long = nanos
    }

    private val clock = ManualClock()
    private val markers = listOf(TimelineMarker("quarter", 250), TimelineMarker("half", 500))
    private val timeline = Timeline("t", durationMs = 1_000, markers = markers)

    private fun controller(reducedMotion: Boolean = false) = PlaybackController(timeline, clock, reducedMotion)

    private fun PlaybackController.advance(ms: Long): FrameUpdate {
        clock.advance(ms)
        return onFrame()
    }

    @Test
    fun `재생 위치는 프레임 수가 아니라 시계로 정해진다`() {
        val smooth = controller()
        smooth.play()
        repeat(30) { smooth.advance(10) }
        val stuttered = PlaybackController(timeline, clock)
        stuttered.play()
        clock.advance(300)

        assertThat(smooth.state.value.timeMs).isEqualTo(300.0)
        assertThat(stuttered.onFrame().state.timeMs).isEqualTo(300.0)
    }

    @Test
    fun `일시 정지하면 시계가 가도 위치가 그대로다`() {
        val playback = controller()
        playback.play()
        playback.advance(200)

        playback.pause()
        clock.advance(500)
        playback.onFrame()
        playback.play()
        playback.advance(100)

        assertThat(playback.state.value.timeMs).isEqualTo(300.0)
    }

    @Test
    fun `한 번 재생은 끝에서 멈추고 다시 재생하면 처음부터다`() {
        val playback = controller()
        playback.play()

        val end = playback.advance(5_000).state

        assertThat(end.timeMs).isEqualTo(1_000.0)
        assertThat(end.isFinished).isTrue()
        assertThat(end.isPlaying).isFalse()
        playback.play()
        assertThat(playback.advance(100).state.timeMs).isEqualTo(100.0)
    }

    @Test
    fun `seek는 재생 여부와 상관없이 위치를 옮기고 범위로 맞춘다`() {
        val playback = controller()
        playback.seek(700.0)
        assertThat(playback.state.value.timeMs).isEqualTo(700.0)
        playback.seek(-5.0)
        assertThat(playback.state.value.timeMs).isEqualTo(0.0)

        playback.play()
        playback.advance(100)
        playback.seek(900.0)
        assertThat(playback.advance(50).state.timeMs).isEqualTo(950.0)
    }

    @Test
    fun `배속은 바꾼 시점부터 적용되고 범위로 맞춘다`() {
        val playback = controller()
        playback.play()
        playback.advance(100)

        playback.setRate(2.0)
        assertThat(playback.advance(100).state.timeMs).isEqualTo(300.0)
        playback.setRate(100.0)
        assertThat(playback.state.value.rate).isEqualTo(PlaybackState.MAX_RATE)
        playback.setRate(0.0)
        assertThat(playback.state.value.rate).isEqualTo(PlaybackState.MIN_RATE)
    }

    @Test
    fun `반복과 왕복 재생 시각을 계산한다`() {
        assertThat(timelineTimeAt(2_300.0, 1_000, RepeatMode.Loop, reversed = false)).isEqualTo(300.0)
        assertThat(timelineTimeAt(1_300.0, 1_000, RepeatMode.PingPong, reversed = false)).isEqualTo(700.0)
        assertThat(timelineTimeAt(2_300.0, 1_000, RepeatMode.PingPong, reversed = false)).isEqualTo(300.0)
        assertThat(timelineTimeAt(300.0, 1_000, RepeatMode.Once, reversed = true)).isEqualTo(700.0)
        assertThat(timelineTimeAt(1e12, 1_000, RepeatMode.Once, reversed = false)).isEqualTo(1_000.0)
        assertThat(timelineTimeAt(500.0, 0, RepeatMode.Loop, reversed = false)).isEqualTo(0.0)
    }

    @Test
    fun `방향을 바꾸면 보이는 시각에서 반대로 진행한다`() {
        val playback = controller()
        playback.play()
        playback.advance(400)

        playback.setReversed(true)

        assertThat(playback.state.value.timeMs).isEqualTo(400.0)
        assertThat(playback.advance(100).state.timeMs).isEqualTo(300.0)
    }

    @Test
    fun `marker는 지날 때 한 번만 알린다`() {
        val playback = controller()
        playback.play()

        assertThat(playback.advance(200).crossedMarkers).isEmpty()
        assertThat(playback.advance(100).crossedMarkers.map { it.name }).containsExactly("quarter")
        assertThat(playback.advance(100).crossedMarkers).isEmpty()
        assertThat(playback.advance(100).crossedMarkers.map { it.name }).containsExactly("half")
        assertThat(playback.advance(1_000).crossedMarkers).isEmpty()
    }

    @Test
    fun `반복 재생은 바퀴마다 marker를 알리고 역방향은 반대 순서로 알린다`() {
        val looping = controller()
        looping.setRepeat(RepeatMode.Loop)
        looping.play()
        looping.advance(600)

        assertThat(looping.advance(1_000).crossedMarkers.map { it.name }).containsExactly("quarter", "half").inOrder()

        val reversed = controller()
        reversed.setReversed(true)
        reversed.play()
        assertThat(reversed.advance(1_000).crossedMarkers.map { it.name }).containsExactly("half", "quarter").inOrder()
    }

    @Test
    fun `seek로 지나친 marker는 알리지 않는다`() {
        val playback = controller()
        playback.play()
        playback.seek(800.0)

        assertThat(playback.advance(10).crossedMarkers).isEmpty()
    }

    @Test
    fun `동작 줄이기에서는 재생하면 바로 끝 상태가 된다`() {
        val playback = controller(reducedMotion = true)

        playback.play()

        assertThat(playback.state.value.timeMs).isEqualTo(1_000.0)
        assertThat(playback.state.value.isPlaying).isFalse()
        assertThat(playback.state.value.isFinished).isTrue()
    }
}
