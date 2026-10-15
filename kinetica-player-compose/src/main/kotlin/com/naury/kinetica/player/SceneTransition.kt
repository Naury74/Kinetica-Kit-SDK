package com.naury.kinetica.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.motion.SceneMorph
import com.naury.kinetica.motion.TransitionDriver
import com.naury.kinetica.motion.TransitionEnd
import com.naury.kinetica.render.SceneImageProvider
import com.naury.kinetica.render.SceneLayout
import com.naury.kinetica.render.SceneSlots

/**
 * 두 장면 사이의 전환([SceneMorph])을 진행도 [progress](0..1) 시점으로 그린다. 영역에는 대상 장면의 artboard를 맞춘다.
 */
@Composable
public fun SceneTransition(
    morph: SceneMorph,
    progress: Double,
    modifier: Modifier = Modifier,
    images: SceneImageProvider = SceneImageProvider.None,
    background: Color = Color.Transparent,
    slots: SceneSlots = emptyMap(),
) {
    val frame = remember(morph, progress) { morph.frame(progress) }
    var size by remember { mutableStateOf(0 to 0) }
    val viewport = remember(frame.artboard, size) {
        Viewport.fitting(Rect(0.0, 0.0, frame.artboard.width, frame.artboard.height), size.first.toDouble(), size.second.toDouble())
    }
    Box(modifier.onSizeChanged { size = it.width to it.height }) {
        SceneLayout(frame, viewport, Modifier.matchParentSize().background(background), images, slots)
    }
}

/**
 * 프레임마다 진행되는 [TransitionDriver]. [onEnd]는 진행도가 1에 닿아 navigation이 확정되거나 0으로 돌아왔을 때 호출되며,
 * [TransitionEnd.Completed]는 한 번만 전달된다.
 */
@Composable
public fun rememberTransitionDriver(onEnd: (TransitionEnd) -> Unit = {}): TransitionDriver {
    val latest by rememberUpdatedState(onEnd)
    val driver = remember { TransitionDriver(onEnd = { latest(it) }) }
    val state by driver.state.collectAsState()
    if (state.isSettling) {
        LaunchedEffect(driver) {
            while (driver.state.value.isSettling) withFrameNanos { driver.onFrame() }
        }
    }
    return driver
}

/** 드래그해서 닫는 방향. */
public enum class DismissAxis(internal val x: Double, internal val y: Double) {
    Down(0.0, 1.0),
    Right(1.0, 0.0),
}

/**
 * 이 요소를 [axis] 방향으로 [travelPx]만큼 드래그하면 [driver] 진행도가 0에서 1이 된다. 반대 방향으로는 0에서 멈춘다.
 * 손을 떼면 속도를 반영해 끝까지 가거나 되돌아간다. 실제 화면 이동은 이 함수가 하지 않고, driver의 종료 알림을 받은 호스트가 한다.
 */
public fun Modifier.dragToDismiss(driver: TransitionDriver, travelPx: Float, axis: DismissAxis = DismissAxis.Down): Modifier =
    pointerInput(driver, travelPx, axis) {
        val tracker = VelocityTracker()
        var dragged = 0f
        detectDragGestures(
            onDragStart = {
                tracker.resetTracking()
                dragged = (driver.state.value.progress * travelPx).toFloat()
                driver.startDrag()
            },
            onDragEnd = {
                val velocity = tracker.calculateVelocity()
                driver.release(
                    TransitionDriver.projectedVelocity(velocity.x.toDouble(), velocity.y.toDouble(), axis.x, axis.y, travelPx.toDouble()),
                )
            },
            onDragCancel = { driver.release(0.0) },
        ) { change, amount ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            dragged += (amount.x * axis.x + amount.y * axis.y).toFloat()
            driver.drag((dragged / travelPx).toDouble())
        }
    }
