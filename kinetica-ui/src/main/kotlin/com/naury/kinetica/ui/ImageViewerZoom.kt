package com.naury.kinetica.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 사진 한 장의 확대 상태. [offset]은 화면 가운데 기준 이동(px)이다. 손을 떼면 1..[VIEWER_MAX_ZOOM] 배율과 가장자리 안쪽으로 튕겨 돌아간다.
 * 보이는 사진 크기([content])와 페이지 크기([viewport])는 페이지가 측정될 때 채운다.
 */
@Stable
internal class ViewerZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewport = Size.Zero
    var content = Size.Zero
    private var job: Job? = null

    val zoomed: Boolean get() = scale > ZOOMED_AT

    fun reset() {
        job?.cancel()
        scale = 1f
        offset = Offset.Zero
    }

    /** 두 손가락 제스처. [centroid]는 화면 가운데 기준 좌표다. 손가락 아래 지점이 그대로 손가락을 따라가도록 이동을 맞춘다. */
    fun transform(centroid: Offset, zoomChange: Float, pan: Offset) {
        job?.cancel()
        val next = (scale * zoomChange).coerceIn(MIN_OVERSHOOT, MAX_OVERSHOOT)
        val k = next / scale
        offset = (offset - centroid) * k + centroid + pan
        scale = next
    }

    /** 한 손가락 이동. 가장자리를 넘어선 방향으로는 저항을 주어 고무줄처럼 늘어난다. */
    fun panBy(delta: Offset) {
        job?.cancel()
        val limit = limit(scale)
        offset = Offset(resist(offset.x, delta.x, limit.x), resist(offset.y, delta.y, limit.y))
    }

    /** 손을 뗀 뒤 배율을 범위 안으로, 이동을 가장자리 안으로 돌린다. 빠르게 밀었으면 그 방향으로 조금 더 미끄러진다. */
    fun settle(scope: CoroutineScope, velocity: Velocity) {
        val target = scale.coerceIn(1f, VIEWER_MAX_ZOOM)
        val thrown = offset + Offset(velocity.x, velocity.y) * FLING_SECONDS
        animateTo(scope, target, clamp(if (scale in 1f..VIEWER_MAX_ZOOM) thrown else offset * (target / scale), target))
    }

    /** 두 번 탭. 확대되어 있으면 원래대로, 아니면 [at](화면 가운데 기준) 지점을 중심으로 [VIEWER_DOUBLE_TAP_ZOOM]배 확대한다. */
    fun toggle(scope: CoroutineScope, at: Offset) {
        if (zoomed) animateTo(scope, 1f, Offset.Zero) else zoomTo(scope, VIEWER_DOUBLE_TAP_ZOOM, at)
    }

    /** [at] 지점을 고정한 채 [target]배로 애니메이션한다. */
    fun zoomTo(scope: CoroutineScope, target: Float, at: Offset = Offset.Zero) {
        val t = target.coerceIn(1f, VIEWER_MAX_ZOOM)
        animateTo(scope, t, clamp((offset - at) * (t / scale) + at, t))
    }

    private fun animateTo(scope: CoroutineScope, targetScale: Float, targetOffset: Offset) {
        job?.cancel()
        val fromScale = scale
        val fromOffset = offset
        job = scope.launch {
            animate(0f, 1f, animationSpec = spring(dampingRatio = SETTLE_DAMPING, stiffness = Spring.StiffnessMediumLow)) { t, _ ->
                scale = fromScale + (targetScale - fromScale) * t
                offset = fromOffset + (targetOffset - fromOffset) * t
            }
        }
    }

    /** [s]배일 때 움직일 수 있는 최대 이동(px). 사진이 페이지보다 작은 축은 0이다. */
    private fun limit(s: Float): Offset {
        val c = if (content.isEmpty()) viewport else content
        return Offset(max(0f, (c.width * s - viewport.width) / 2), max(0f, (c.height * s - viewport.height) / 2))
    }

    private fun clamp(o: Offset, s: Float): Offset {
        val l = limit(s)
        return Offset(o.x.coerceIn(-l.x, l.x), o.y.coerceIn(-l.y, l.y))
    }

    private fun resist(value: Float, delta: Float, limit: Float): Float {
        val outward = abs(value) >= limit && sign(delta) == sign(value)
        return value + if (outward) delta * EDGE_RESISTANCE else delta
    }
}

/**
 * 두 손가락은 확대와 이동, 확대된 상태의 한 손가락은 이동으로 받는다. 확대되지 않은 한 손가락 드래그는 소비하지 않으므로
 * 바깥의 페이저(좌우)와 끌어서 닫기(위아래)가 받는다.
 */
internal fun Modifier.viewerZoomGestures(zoom: ViewerZoom, scope: CoroutineScope): Modifier = pointerInput(zoom) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        var handled = false
        do {
            val event = awaitPointerEvent()
            val fingers = event.changes.count { it.pressed }
            val center = Offset(size.width / 2f, size.height / 2f)
            if (fingers >= 2) {
                zoom.transform(event.calculateCentroid(useCurrent = false) - center, event.calculateZoom(), event.calculatePan())
                event.changes.forEach { it.consume() }
                handled = true
            } else if (fingers == 1 && zoom.zoomed && event.changes.none { it.isConsumed }) {
                val change = event.changes.first { it.pressed }
                tracker.addPosition(change.uptimeMillis, change.position)
                zoom.panBy(event.calculatePan())
                change.consume()
                handled = true
            }
        } while (event.changes.any { it.pressed })
        if (handled) zoom.settle(scope, if (zoom.zoomed) tracker.calculateVelocity() else Velocity.Zero)
    }
}

/** 끌어서 닫기의 세로 이동(px, 위가 음수). 손가락 이동은 상태에 바로 더하고, 손을 뗀 뒤의 이동만 애니메이션으로 처리한다. */
@Stable
internal class ViewerDrag {
    var value by mutableFloatStateOf(0f)
        private set
    private var job: Job? = null

    fun drag(delta: Float) {
        job?.cancel()
        value += delta
    }

    fun settle(scope: CoroutineScope, target: Float) {
        job?.cancel()
        job = scope.launch {
            animate(value, target, animationSpec = spring(dampingRatio = SETTLE_DAMPING, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                value =
                    v
            }
        }
    }
}

/** [from]을 [into] 크기로 맞추는 배율 중 큰 쪽(가득 채우기). */
internal fun viewerCoverScale(from: Rect, into: Rect): Float =
    max(into.width / from.width.coerceAtLeast(1f), into.height / from.height.coerceAtLeast(1f))

/** [bounds] 가운데에 [aspect](가로/세로) 비율로 꼭 맞게 들어가는 사각형. [aspect]가 없으면 [bounds] 그대로다. */
internal fun viewerFitRect(bounds: Rect, aspect: Float?): Rect {
    if (aspect == null || aspect <= 0f || !aspect.isFinite()) return bounds
    val w = min(bounds.width, bounds.height * aspect)
    val h = w / aspect
    return Rect(Offset(bounds.center.x - w / 2, bounds.center.y - h / 2), Size(w, h))
}

internal fun Rect.viewerScaled(scale: Float, center: Offset = this.center): Rect =
    Rect(center.x - width * scale / 2, center.y - height * scale / 2, center.x + width * scale / 2, center.y + height * scale / 2)

internal const val VIEWER_MAX_ZOOM = 5f
internal const val VIEWER_DOUBLE_TAP_ZOOM = 2.5f
private const val ZOOMED_AT = 1.01f
private const val MIN_OVERSHOOT = 0.7f
private const val MAX_OVERSHOOT = 6f
private const val EDGE_RESISTANCE = 0.35f
private const val FLING_SECONDS = 0.18f
private const val SETTLE_DAMPING = 0.82f
