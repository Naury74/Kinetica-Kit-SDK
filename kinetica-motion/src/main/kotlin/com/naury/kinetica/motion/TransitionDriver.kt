package com.naury.kinetica.motion

import com.naury.kinetica.core.animation.EasingSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 전환이 어디서 멈췄는지. */
public enum class TransitionEnd {
    /** 끝(진행률 1)에 닿았다. 호스트는 이때 화면 이동(navigation)을 확정한다. */
    Completed,

    /** 처음(진행률 0)으로 돌아왔다. */
    Canceled,
}

/**
 * @property progress 0..1
 * @property isDragging 손가락이 잡고 있는 중
 * @property isSettling 손을 뗀 뒤 0이나 1로 가는 중
 */
public data class TransitionState(val progress: Double = 0.0, val isDragging: Boolean = false, val isSettling: Boolean = false)

/**
 * 손으로 끌 수 있는 전환의 진행률(docs 09 interactive dismiss). 화면·navigation과 무관한 순수 상태 기계다.
 *
 * - [drag]로 진행률을 직접 정하고, [release]하면 속도를 반영해 0이나 1 중 가까운 쪽으로 [settleDurationMs] 동안 간다.
 * - 가는 도중 다시 [startDrag]하면 그 자리에서 이어 잡는다.
 * - [onEnd]는 1에 닿거나 0으로 돌아왔을 때 불린다. 끝([TransitionEnd.Completed])은 이 객체가 살아 있는 동안 한 번만 알린다.
 *
 * 프레임마다 [onFrame]을 부른다. main 스레드에서만 쓴다.
 *
 * @param projectionSeconds 손을 뗄 때 속도를 이 시간만큼 앞으로 내다봐 결정한다.
 * @param threshold 내다본 진행률이 이 값 이상이면 끝까지 간다.
 */
public class TransitionDriver(
    private val clock: FrameClock = FrameClock.System,
    private val settleDurationMs: Double = DEFAULT_SETTLE_MS,
    private val easing: EasingSpec = EasingSpec.EaseOut,
    private val threshold: Double = DEFAULT_THRESHOLD,
    private val projectionSeconds: Double = DEFAULT_PROJECTION_SECONDS,
    private val onEnd: (TransitionEnd) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(TransitionState())
    private var settle: Settle? = null
    private var completed = false

    public val state: StateFlow<TransitionState> = mutableState.asStateFlow()

    /** 끝난 뒤에는 아무 동작도 하지 않는다. */
    public val isCompleted: Boolean get() = completed

    public fun startDrag() {
        if (completed) return
        settle = null
        mutableState.value = TransitionState(progress = mutableState.value.progress, isDragging = true)
    }

    /** 잡고 있는 동안 진행률을 정한다. 0..1로 맞춘다. */
    public fun drag(progress: Double) {
        if (completed || !mutableState.value.isDragging) return
        mutableState.value = mutableState.value.copy(progress = clamp(progress))
    }

    /** 손을 뗀다. [progressPerSecond]는 끄는 방향으로의 속도를 이동 거리로 나눈 값이다([projectedVelocity]). */
    public fun release(progressPerSecond: Double) {
        if (completed || !mutableState.value.isDragging) return
        val velocity = if (progressPerSecond.isFinite()) progressPerSecond else 0.0
        val projected = mutableState.value.progress + velocity * projectionSeconds
        settleTo(if (projected >= threshold) 1.0 else 0.0)
    }

    /** 끌지 않고 끝까지(예: 뒤로 버튼) 또는 처음으로 간다. */
    public fun animateTo(forward: Boolean) {
        if (completed) return
        settleTo(if (forward) 1.0 else 0.0)
    }

    /** 프레임마다 부른다. 가는 중이면 진행률을 갱신하고, 닿으면 [onEnd]를 부른다. */
    public fun onFrame(): TransitionState {
        val current = settle ?: return mutableState.value
        val elapsedMs = (clock.timeNanos() - current.startNanos).coerceAtLeast(0L) / NANOS_PER_MILLI
        val fraction = if (settleDurationMs <= 0.0) 1.0 else (elapsedMs / settleDurationMs).coerceIn(0.0, 1.0)
        val eased = Easing.evaluate(easing, fraction, settleDurationMs)
        val progress = current.from + (current.to - current.from) * eased
        if (fraction >= 1.0) {
            settle = null
            mutableState.value = TransitionState(progress = current.to)
            finish(if (current.to >= 1.0) TransitionEnd.Completed else TransitionEnd.Canceled)
        } else {
            mutableState.value = TransitionState(progress = progress, isSettling = true)
        }
        return mutableState.value
    }

    private fun settleTo(target: Double) {
        val from = mutableState.value.progress
        settle = Settle(from, target, clock.timeNanos())
        mutableState.value = TransitionState(progress = from, isSettling = true)
        if (from == target) onFrame()
    }

    private fun finish(end: TransitionEnd) {
        if (end == TransitionEnd.Completed) {
            if (completed) return
            completed = true
        }
        onEnd(end)
    }

    private class Settle(val from: Double, val to: Double, val startNanos: Long)

    public companion object {
        public const val DEFAULT_SETTLE_MS: Double = 280.0
        public const val DEFAULT_THRESHOLD: Double = 0.5
        public const val DEFAULT_PROJECTION_SECONDS: Double = 0.15
        private const val NANOS_PER_MILLI = 1_000_000.0

        /**
         * 손가락 속도([velocityX]·[velocityY], px/s)를 끄는 방향([axisX]·[axisY], 단위 벡터)으로 투영하고 이동 거리 [travelPx]로 나눈다.
         * 거리가 0 이하이면 0이다.
         */
        public fun projectedVelocity(velocityX: Double, velocityY: Double, axisX: Double, axisY: Double, travelPx: Double): Double =
            if (travelPx > 0.0) (velocityX * axisX + velocityY * axisY) / travelPx else 0.0

        private fun clamp(value: Double): Double = if (value.isNaN()) 0.0 else value.coerceIn(0.0, 1.0)
    }
}
