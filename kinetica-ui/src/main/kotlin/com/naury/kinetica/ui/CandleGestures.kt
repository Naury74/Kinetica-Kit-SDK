package com.naury.kinetica.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private enum class CandleGesture { Pan, Pinch, Crosshair, None }

/**
 * 캔들 차트 손동작. 가로로 먼저 움직이면 옮기기, 두 손가락이면 확대, 움직이지 않고 길게 누르면 십자선이다.
 * 세로로 먼저 움직이면 아무것도 소비하지 않아 바깥 스크롤이 받는다. 손을 떼면 옮기던 속도만큼 미끄러지고,
 * 터치가 취소되면(소비된 놓기 사건) 미끄러지지 않고 십자선도 지운다.
 */
@Composable
internal fun Modifier.candleGestures(frame: CandleFrame, still: Boolean, scope: CoroutineScope): Modifier {
    val haptic = kHapticFeedback()
    val latest = rememberUpdatedState(frame)
    val latestStill = rememberUpdatedState(still)
    return pointerInput(frame.plot, frame.cross) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val cross = latest.value.cross
            cross.fling?.cancel()
            cross.fling = null
            cross.clear()
            when (decideGesture(down)) {
                CandleGesture.Pan -> panCandles(latest, down.id)?.let { velocity ->
                    if (!latestStill.value) cross.fling = scope.launch { flingCandles(latest, velocity) }
                }

                CandleGesture.Pinch -> pinchCandles(latest)

                CandleGesture.Crosshair -> trackCrosshair(latest, down, haptic)

                CandleGesture.None -> Unit
            }
        }
    }
}

/** 길게 누르기 시간 안에 손이 어디로 움직였는지 보고 손동작을 정한다. 시간이 다 되도록 가만히 있으면 십자선이다. */
private suspend fun AwaitPointerEventScope.decideGesture(down: PointerInputChange): CandleGesture {
    val slop = viewConfiguration.touchSlop
    return withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var decided: CandleGesture? = null
        while (decided == null) decided = classify(awaitPointerEvent(), down, slop)
        decided
    } ?: CandleGesture.Crosshair
}

/** 사건 하나로 손동작을 정할 수 있으면 돌려주고, 아직 모르면 `null` */
private fun classify(event: PointerEvent, down: PointerInputChange, slop: Float): CandleGesture? {
    if (event.changes.count { it.pressed } >= 2) return CandleGesture.Pinch
    val change = event.changes.firstOrNull { it.id == down.id }
    val moved = change?.let { it.position - down.position }
    return when {
        change == null || !change.pressed || change.isConsumed || moved == null -> CandleGesture.None
        abs(moved.x) > slop && abs(moved.x) > abs(moved.y) -> CandleGesture.Pan
        abs(moved.y) > slop -> CandleGesture.None
        else -> null
    }
}

/** 손가락을 따라 옮긴다. 정상으로 손을 떼면 가로 속도(px/s)를, 두 손가락이 되거나 취소되면 `null`을 돌려준다. */
private suspend fun AwaitPointerEventScope.panCandles(frame: State<CandleFrame>, id: PointerId): Float? {
    val tracker = VelocityTracker()
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.count { it.pressed } >= 2) {
            pinchCandles(frame)
            return null
        }
        val change = event.changes.firstOrNull { it.id == id }
        // 소비된 사건은 다른 쪽이 가져갔거나 터치가 취소된 것이므로 미끄러지지 않는다.
        if (change == null || change.isConsumed || !change.pressed) {
            return if (change != null && !change.isConsumed) tracker.calculateVelocity().x else null
        }
        tracker.addPointerInputChange(change)
        val current = frame.value
        val total = current.content.candles.size
        val slot = current.plot.slot(current.state, total)
        if (slot > 0f) current.state.panBy(-change.positionChange().x / slot, total)
        change.consume()
    }
}

private suspend fun AwaitPointerEventScope.pinchCandles(frame: State<CandleFrame>) {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.none { it.pressed }) return
        val current = frame.value
        val total = current.content.candles.size
        val plot = current.plot
        val zoom = event.calculateZoom()
        val centroid = event.calculateCentroid(useCurrent = false)
        if (zoom != 1f && centroid.isSpecified && plot.plotWidth > 0f) current.state.zoomBy(zoom, centroid.x / plot.plotWidth, total)
        val slot = plot.slot(current.state, total)
        if (slot > 0f) current.state.panBy(-event.calculatePan().x / slot, total)
        event.changes.forEach { if (it.positionChanged()) it.consume() }
    }
}

/** 손가락이 닿은 동안 십자선을 따라 옮기고, 떼거나 취소되면 지운다. 놓는 것으로는 아무것도 실행하지 않는다. */
private suspend fun AwaitPointerEventScope.trackCrosshair(frame: State<CandleFrame>, down: PointerInputChange, haptic: HapticFeedback) {
    fun place(x: Float, y: Float): Int {
        val current = frame.value
        val index = current.plot.indexAt(x, current.state, current.content.candles.size)
        if (index >= 0) current.cross.show(index, y)
        return index
    }
    var last = place(down.position.x, down.position.y)
    if (last < 0) return
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    try {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) break
            change.consume()
            val index = place(change.position.x, change.position.y)
            if (index != last && index >= 0) {
                last = index
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
        }
    } finally {
        frame.value.cross.clear()
    }
}

/** 놓은 속도만큼 이어서 옮기다 서서히 멈춘다. 끝에 닿으면 바로 멈춘다. */
private suspend fun flingCandles(frame: State<CandleFrame>, velocity: Float) {
    if (abs(velocity) < MIN_FLING) return
    var previous = 0f
    AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(exponentialDecay(FLING_FRICTION)) {
        val current = frame.value
        val total = current.content.candles.size
        val slot = current.plot.slot(current.state, total)
        val moved = value - previous
        previous = value
        if (slot <= 0f || !current.state.panBy(-moved / slot, total)) cancelAnimation()
    }
}

private const val MIN_FLING = 200f
private const val FLING_FRICTION = 1.6f
