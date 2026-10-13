package com.naury.kinetica.motion

import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.animation.TimelineMarker
import kotlin.math.floor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 단조 증가하는 시계. 테스트에서는 직접 움직이는 시계를 넣는다. */
public fun interface FrameClock {
    public fun timeNanos(): Long

    public companion object {
        public val System: FrameClock = FrameClock(java.lang.System::nanoTime)
    }
}

/**
 * @property timeMs 지금 보여 줄 timeline 시각(ms). 이 값으로 [TimelineEvaluator]를 부른다.
 * @property isFinished [RepeatMode.Once]에서 끝에 닿았다.
 * @property rate 배속, `[MIN_RATE, MAX_RATE]`
 */
public data class PlaybackState(
    val timeMs: Double = 0.0,
    val isPlaying: Boolean = false,
    val isFinished: Boolean = false,
    val rate: Double = 1.0,
    val repeat: RepeatMode = RepeatMode.Once,
    val reversed: Boolean = false,
) {
    public companion object {
        public const val MIN_RATE: Double = 0.25
        public const val MAX_RATE: Double = 4.0
    }
}

/**
 * timeline 하나의 재생 상태. 문서를 바꾸지 않는다.
 *
 * 재생 위치는 프레임마다 더해 가지 않고 "기준 시계 값 + 기준 위치"에서 계산하므로 프레임이 빠지거나 늦어도 어긋나지 않는다.
 * 화면은 프레임마다 [onFrame]을 부르고, 돌려받은 시각으로 문서를 평가한다. 스레드 안전하지 않으므로 한 스레드(보통 main)에서 쓴다.
 *
 * @param reducedMotion 시스템 동작 줄이기가 켜져 있으면 `true`. 재생하면 애니메이션 없이 바로 끝 상태로 간다.
 */
public class PlaybackController(
    private val timeline: Timeline,
    private val clock: FrameClock = FrameClock.System,
    private val reducedMotion: Boolean = false,
) {
    private val mutableState = MutableStateFlow(PlaybackState())
    private var anchorNanos = 0L
    private var anchorTravelMs = 0.0
    private var lastTravelMs = 0.0

    public val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    public fun play() {
        val current = mutableState.value
        if (current.isPlaying) return
        if (reducedMotion) {
            anchorTravelMs = endTravel(current)
            lastTravelMs = anchorTravelMs
            publish(isPlaying = false)
            return
        }
        if (current.isFinished) anchorTravelMs = 0.0
        lastTravelMs = anchorTravelMs
        anchorNanos = clock.timeNanos()
        publish(isPlaying = true)
    }

    public fun pause() {
        if (!mutableState.value.isPlaying) return
        anchorTravelMs = travelNow()
        lastTravelMs = anchorTravelMs
        publish(isPlaying = false)
    }

    /** 재생 중이든 아니든 [timelineTimeMs] 시각으로 옮긴다. 범위 밖은 0..길이로 맞추고, 지나친 marker는 알리지 않는다. */
    public fun seek(timelineTimeMs: Double) {
        val duration = timeline.durationMs.toDouble()
        val target = if (timelineTimeMs.isNaN()) 0.0 else timelineTimeMs.coerceIn(0.0, duration)
        val reversed = mutableState.value.reversed
        anchorTravelMs = if (reversed) duration - target else target
        lastTravelMs = anchorTravelMs
        anchorNanos = clock.timeNanos()
        publish(isPlaying = mutableState.value.isPlaying)
    }

    /** 배속을 바꾼다. 범위 밖은 `[PlaybackState.MIN_RATE, PlaybackState.MAX_RATE]`로 맞춘다. */
    public fun setRate(rate: Double) {
        reanchor()
        val clamped = if (rate.isNaN()) 1.0 else rate.coerceIn(PlaybackState.MIN_RATE, PlaybackState.MAX_RATE)
        mutableState.value = mutableState.value.copy(rate = clamped)
    }

    public fun setRepeat(repeat: RepeatMode) {
        reanchor()
        mutableState.value = mutableState.value.copy(repeat = repeat)
        publish(isPlaying = mutableState.value.isPlaying)
    }

    /** 방향을 바꾼다. 지금 보이는 시각은 그대로 두고 그 자리에서 반대로 진행한다. */
    public fun setReversed(reversed: Boolean) {
        val current = mutableState.value
        if (current.reversed == reversed) return
        reanchor()
        mutableState.value = current.copy(reversed = reversed)
        seek(current.timeMs)
    }

    /**
     * 프레임마다 부른다. 지금 상태와, 지난 호출 뒤 지나친 marker를 지나친 순서대로 돌려준다.
     * 한 번에 여러 바퀴를 건너뛴 경우 marker는 마지막 [MAX_REPORTED_CYCLES]바퀴 것만 알린다.
     */
    public fun onFrame(): FrameUpdate {
        val current = mutableState.value
        if (!current.isPlaying) return FrameUpdate(current, emptyList())
        val travel = travelNow()
        val crossed = crossedMarkers(lastTravelMs, travel, current)
        lastTravelMs = travel
        val finished = current.repeat == RepeatMode.Once && travel >= timeline.durationMs
        if (finished) anchorTravelMs = timeline.durationMs.toDouble()
        val state = stateAt(travel.coerceAtMost(endTravel(current)), isPlaying = !finished)
        mutableState.value = state
        return FrameUpdate(state, crossed)
    }

    private fun reanchor() {
        if (mutableState.value.isPlaying) {
            anchorTravelMs = travelNow()
            anchorNanos = clock.timeNanos()
        }
    }

    private fun travelNow(): Double {
        val current = mutableState.value
        if (!current.isPlaying) return anchorTravelMs
        val elapsedMs = (clock.timeNanos() - anchorNanos).coerceAtLeast(0L) / NANOS_PER_MILLI
        return anchorTravelMs + elapsedMs * current.rate
    }

    private fun endTravel(state: PlaybackState): Double =
        if (state.repeat == RepeatMode.Once) timeline.durationMs.toDouble() else Double.MAX_VALUE

    private fun publish(isPlaying: Boolean) {
        mutableState.value = stateAt(anchorTravelMs, isPlaying)
    }

    private fun stateAt(travelMs: Double, isPlaying: Boolean): PlaybackState {
        val current = mutableState.value
        val finished = current.repeat == RepeatMode.Once && travelMs >= timeline.durationMs
        return current.copy(
            timeMs = timelineTimeAt(travelMs, timeline.durationMs, current.repeat, current.reversed),
            isPlaying = isPlaying && !finished,
            isFinished = finished,
        )
    }

    private fun crossedMarkers(fromTravel: Double, toTravel: Double, state: PlaybackState): List<TimelineMarker> {
        val duration = timeline.durationMs.toDouble()
        if (timeline.markers.isEmpty() || duration <= 0.0 || toTravel <= fromTravel) return emptyList()
        val end = toTravel.coerceAtMost(endTravel(state))
        val firstCycle = floor(fromTravel / duration).toLong()
        val lastCycle = floor(end / duration).toLong()
        val startCycle = maxOf(firstCycle, lastCycle - MAX_REPORTED_CYCLES + 1)
        return (startCycle..lastCycle).flatMap { cycle ->
            val cycleStart = cycle * duration
            val from = (maxOf(fromTravel, cycleStart) - cycleStart)
            val to = (minOf(end, cycleStart + duration) - cycleStart)
            markersPassed(from, to, cycle, state, includeStart = fromTravel < cycleStart)
        }
    }

    /** 한 바퀴 안에서 진행 시간 [from]..[to]를 지날 때 지나친 marker. */
    private fun markersPassed(from: Double, to: Double, cycle: Long, state: PlaybackState, includeStart: Boolean): List<TimelineMarker> {
        val duration = timeline.durationMs.toDouble()
        val backward = state.reversed != (state.repeat == RepeatMode.PingPong && cycle % 2L == 1L)
        fun local(travel: Double) = if (backward) duration - travel else travel
        val a = local(from)
        val b = local(to)
        val passed = timeline.markers.filter { marker ->
            val time = marker.timeMs.toDouble()
            if (backward) {
                (time < a && time >= b) || (includeStart && time == a)
            } else {
                (time > a && time <= b) || (includeStart && time == a)
            }
        }
        return if (backward) passed.sortedByDescending { it.timeMs } else passed.sortedBy { it.timeMs }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0
        const val MAX_REPORTED_CYCLES = 2L
    }
}

/** [PlaybackController.onFrame] 결과. */
public data class FrameUpdate(val state: PlaybackState, val crossedMarkers: List<TimelineMarker>)
