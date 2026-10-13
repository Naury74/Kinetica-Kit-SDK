package com.naury.kinetica.motion

import kotlin.math.floor

/** 끝에 닿았을 때의 동작. */
public enum class RepeatMode {
    /** 끝에서 멈춘다. */
    Once,

    /** 처음으로 돌아가 반복한다. */
    Loop,

    /** 방향을 바꿔 오가며 반복한다. */
    PingPong,
}

/**
 * 재생이 지나온 시간(진행 시간)을 timeline 안의 시각으로 바꾸는 순수 함수.
 *
 * @param travelMs 재생을 시작한 뒤 배속을 반영해 지나온 시간(ms). 0 이상으로 맞춘다.
 * @param durationMs timeline 길이. 0이면 항상 0이다.
 * @param reversed 끝에서 처음 쪽으로 재생한다.
 */
public fun timelineTimeAt(travelMs: Double, durationMs: Long, repeat: RepeatMode, reversed: Boolean): Double {
    if (durationMs <= 0L) return 0.0
    val duration = durationMs.toDouble()
    val travel = if (travelMs.isNaN()) 0.0 else travelMs.coerceAtLeast(0.0)
    val forward = when (repeat) {
        RepeatMode.Once -> travel.coerceAtMost(duration)

        RepeatMode.Loop -> travel % duration

        RepeatMode.PingPong -> {
            val cycle = floor(travel / duration)
            val within = travel - cycle * duration
            if (cycle % 2.0 == 0.0) within else duration - within
        }
    }
    return if (reversed) duration - forward else forward
}
