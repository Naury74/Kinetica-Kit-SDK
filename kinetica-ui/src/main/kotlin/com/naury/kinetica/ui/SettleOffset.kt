package com.naury.kinetica.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 손가락을 따라 움직이고 손을 떼면 정지 위치로 돌아가는 값(px).
 *
 * `Animatable.snapTo`를 끌기 이벤트마다 `launch`하면, 마지막 움직임과 손 떼기가 같은 프레임에 올 때 늦게 실행된 `snapTo`가
 * 방금 시작한 정착 애니메이션을 취소해 값이 중간에 멈춘다. 그래서 끌기는 상태에 바로 쓰고, 정착 애니메이션만 코루틴으로 돌린다.
 * 정착 중에 다시 끌면 애니메이션을 멈추고 손가락을 따른다.
 */
@Stable
internal class SettleOffset(initial: Float = 0f) {
    var value by mutableFloatStateOf(initial)
        private set

    private var settling: Job? = null

    /** 정착 애니메이션이 도는 중이면 true다. */
    val isSettling: Boolean get() = settling?.isActive == true

    /** [delta]만큼 옮기되 [min]..[max] 안으로 자른다. 실제로 옮긴 양을 돌려준다. */
    fun drag(delta: Float, min: Float = Float.NEGATIVE_INFINITY, max: Float = Float.POSITIVE_INFINITY): Float {
        stop()
        val next = (value + delta).coerceIn(min, max)
        val used = next - value
        value = next
        return used
    }

    /** 애니메이션 없이 [target]으로 옮긴다. */
    fun snap(target: Float) {
        stop()
        value = target
    }

    fun stop() {
        settling?.cancel()
        settling = null
    }

    /** [target]까지 [spec]으로 움직이고 도착할 때까지 일시 중단한다. [velocity]는 손을 뗀 속도(px/s)다. */
    suspend fun animateTo(target: Float, spec: AnimationSpec<Float>, velocity: Float = 0f) {
        stop()
        coroutineScope {
            settling = coroutineContext[Job]
            animate(value, target, velocity, spec) { v, _ -> value = v }
        }
    }

    /** [scope]에서 [animateTo]를 시작한다. 첫 프레임을 기다리지 않도록 바로 시작한다. */
    fun settle(scope: CoroutineScope, target: Float, spec: AnimationSpec<Float>, velocity: Float = 0f): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) { animateTo(target, spec, velocity) }
}

/**
 * 모든 손가락이 떨어지고 몇 프레임이 지났는데도 [needsSettle]이 true면 [settle]을 부른다.
 *
 * 끌기 정착은 보통 `onDragStopped`나 중첩 스크롤의 `onPreFling`에서 시작한다. 그런데 안쪽 스크롤이 플링을 보내지 않거나
 * 제스처가 중간에 취소되면 그 콜백이 오지 않아 값이 끌다 만 자리에 남는다. 이 수정자는 그런 경우를 잡는 안전장치다.
 * [needsSettle]은 정착 애니메이션이 이미 도는 중이면 false를 돌려줘야 한다.
 */
@Composable
internal fun Modifier.settleAfterRelease(needsSettle: () -> Boolean, settle: () -> Unit): Modifier {
    val latestNeeds by rememberUpdatedState(needsSettle)
    val latestSettle by rememberUpdatedState(settle)
    return pointerInput(Unit) { watchRelease({ latestNeeds() }, { latestSettle() }) }
}

private suspend fun PointerInputScope.watchRelease(needsSettle: () -> Boolean, settle: () -> Unit) {
    coroutineScope {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
            } while (event.changes.any { it.pressed })
            launch {
                // 손을 뗀 프레임에 시작하는 플링과 onDragStopped가 먼저 정착을 시작할 시간을 준다.
                repeat(RELEASE_GRACE_FRAMES) { withFrameNanos { } }
                if (needsSettle()) settle()
            }
        }
    }
}

private const val RELEASE_GRACE_FRAMES = 3
