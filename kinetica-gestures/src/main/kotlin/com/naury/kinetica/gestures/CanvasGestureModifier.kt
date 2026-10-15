package com.naury.kinetica.gestures

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.naury.kinetica.core.geometry.Vec2
import kotlinx.coroutines.CancellationException

/**
 * 이 요소에 들어온 포인터 입력을 [controller]에 넘긴다. 요소의 좌표가 곧 viewport 픽셀 좌표다.
 * 입력을 소비하므로 바깥 스크롤과 충돌하지 않는다. 요소가 사라지거나 입력이 취소되면 [CanvasGestureController.onCancel]을 호출한다.
 */
public fun Modifier.canvasGestures(controller: CanvasGestureController): Modifier = pointerInput(controller) {
    try {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    event.changes.firstOrNull()?.let { controller.onScroll(it.position.toVec2(), it.scrollDelta.y.toDouble()) }
                }
                event.changes.forEach { change ->
                    val id = change.id.value
                    val point = change.position.toVec2()
                    when {
                        change.changedToDownIgnoreConsumed() -> controller.onDown(id, point)
                        change.changedToUpIgnoreConsumed() -> controller.onUp(id, change.uptimeMillis)
                        change.pressed && change.positionChanged() -> controller.onMove(id, point)
                    }
                    change.consume()
                }
            }
        }
    } catch (cancelled: CancellationException) {
        controller.onCancel()
        throw cancelled
    }
}

private fun androidx.compose.ui.geometry.Offset.toVec2(): Vec2 = Vec2(x.toDouble(), y.toDouble())
