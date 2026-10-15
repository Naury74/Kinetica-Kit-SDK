package com.naury.kinetica.motion

import com.naury.kinetica.core.animation.EasingSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 전환이 멈춘 위치. */
public enum class TransitionEnd {
    /** 진행률 1, 즉 끝에 닿았다. 호스트는 이때 화면 이동을 확정한다. */
    Completed,

    /** 진행률 0, 즉 처음으로 돌아왔다. */
    Canceled,
}

/**
 * @property progress 진행률. 0..1
 * @property isDragging 사용자가 드래그하는 중이면 `true`
 * @property isSettling 손을 뗀 뒤 0이나 1로 이동하는 중이면 `true`
 */
public data class TransitionState(val progress: Double = 0.0, val isDragging: Boolean = false, val isSettling: Boolean = false)

/**
 * 드래그로 조작할 수 있는 전환의 진행률을 관리한다(docs 09 interactive dismiss). 화면이나 내비게이션과 무관한 순수 상태 기계다.
 *
 * - [drag]로 진행률을 직접 정하고, [release]하면 속도를 반영해 0과 1 중 가까운 쪽으로 [settleDurationMs] 동안 이동한다.
 * - 이동하는 도중에 다시 [startDrag]하면 그 위치에서 이어서 잡는다.
 * - [onEnd]는 1에 닿거나 0으로 돌아왔을 때 호출된다. [TransitionEnd.Completed]는 이 객체가 살아 있는 동안 한 번만 알린다.
 *
 * 프레임마다 [onFrame]을 호출해야 하며, 메인 스레드에서만 쓴다.
 *
 * @param projectionSeconds 손을 뗄 때 현재 속도로 이 시간만큼 더 움직였다고 가정해 도착 지점을 예측한다.
 * @param threshold 예측한 진행률이 이 값 이상이면 끝까지 간다.
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

    /** 완료된 뒤에는 아무것도 하지 않는다. */
    public val isCompleted: Boolean get() = completed

    public fun startDrag() {
        if (completed) return
        settle = null
        mutableState.value = TransitionState(progress = mutableState.value.progress, isDragging = true)
    }

    /** 드래그하는 동안 진행률을 정한다. 값은 0..1로 맞춘다. */
    public fun drag(progress: Double) {
        if (completed || !mutableState.value.isDragging) return
        mutableState.value = mutableState.value.copy(progress = clamp(progress))
    }

    /** 드래그를 끝낸다. [progressPerSecond]는 드래그 방향의 속도를 이동 거리로 나눈 값이며 [projectedVelocity]로 구한다. */
    public fun release(progressPerSecond: Double) {
        if (completed || !mutableState.value.isDragging) return
        val velocity = if (progressPerSecond.isFinite()) progressPerSecond else 0.0
        val projected = mutableState.value.progress + velocity * projectionSeconds
        settleTo(if (projected >= threshold) 1.0 else 0.0)
    }

    /** 드래그 없이 끝이나 처음으로 이동한다. 뒤로 버튼을 눌렀을 때 등에 쓴다. */
    public fun animateTo(forward: Boolean) {
        if (completed) return
        settleTo(if (forward) 1.0 else 0.0)
    }

    /** 프레임마다 호출한다. 이동 중이면 진행률을 갱신하고, 끝이나 처음에 닿으면 [onEnd]를 호출한다. */
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
         * 손가락 속도([velocityX], [velocityY], px/s)를 드래그 방향의 단위 벡터([axisX], [axisY])에 투영한 뒤 이동 거리 [travelPx]로 나눈다.
         * 거리가 0 이하이면 0을 돌려준다.
         */
        public fun projectedVelocity(velocityX: Double, velocityY: Double, axisX: Double, axisY: Double, travelPx: Double): Double =
            if (travelPx > 0.0) (velocityX * axisX + velocityY * axisY) / travelPx else 0.0

        private fun clamp(value: Double): Double = if (value.isNaN()) 0.0 else value.coerceIn(0.0, 1.0)
    }
}
