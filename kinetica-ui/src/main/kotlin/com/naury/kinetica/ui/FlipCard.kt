// 파일 이름은 대표 컴포저블을 따른다. 함께 쓰는 공개 타입이 하나뿐이라 detekt가 그 이름을 요구한다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** [FlipCard]가 도는 축. */
public enum class FlipAxis {
    /** 가로축을 중심으로 위아래로 뒤집는다. 위아래로 끌어 뒤집는다. */
    X,

    /** 세로축을 중심으로 좌우로 뒤집는다. 좌우로 끌어 뒤집는다. */
    Y,
}

/**
 * 앞면과 뒷면이 있는 카드를 3D로 뒤집는다. [flipped]가 바뀌면 스프링으로 돌고, 뒷면의 글자는 거울처럼 뒤집히지 않게 바로 세워 그린다.
 * [onFlippedChange]를 주면 손가락으로 끌어 돌릴 수 있다. 끄는 동안 카드가 손을 따라 돌고, 놓으면 속도와 각도를 보고 가까운 면으로 멈춘다.
 * TalkBack에는 지금 보이는 면을 상태로 알리고 '뒤집기' 동작을 제공한다.
 * '애니메이션 삭제' 접근성 설정이 켜져 있으면 [flipped]가 바뀔 때 돌지 않고 바로 바뀐다.
 *
 * 보이지 않는 면은 컴포지션에서 빠지므로 그 면의 상태는 유지되지 않는다. 두 면은 같은 크기로 맞추는 것이 좋고,
 * 크기가 다르면 카드가 옆으로 선 순간에 크기가 바뀐다.
 *
 * ```kotlin
 * var flipped by remember { mutableStateOf(false) }
 * FlipCard(flipped, { flipped = it }, Modifier.size(240.dp, 150.dp), front = { CardFront() }, back = { CardBack() })
 * ```
 *
 * @param flipped 뒷면이 보이는지
 * @param onFlippedChange 사용자가 끌거나 접근성 동작으로 뒤집었을 때 새 값을 받는다. null이면 끌어서 뒤집을 수 없고 '뒤집기' 동작도 없다.
 * @param axis 도는 축
 * @param front 앞면
 * @param back 뒷면
 */
@Composable
public fun FlipCard(
    flipped: Boolean,
    onFlippedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    axis: FlipAxis = FlipAxis.Y,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
) {
    val still = reducedMotion()
    val angle = remember { Animatable(if (flipped) HALF_TURN else 0f) }
    LaunchedEffect(flipped, still) {
        val target = nearestFace(angle.value, flipped)
        if (still) angle.snapTo(target) else angle.animateTo(target, spring(FLIP_DAMPING, FLIP_STIFFNESS))
    }
    val showBack by remember { derivedStateOf { isBackAngle(angle.value) } }
    val scope = rememberCoroutineScope()
    val currentFlipped by rememberUpdatedState(flipped)
    val currentChange by rememberUpdatedState(onFlippedChange)
    val actionLabel = stringResource(R.string.kinetica_ui_flip_action)
    val side = stringResource(if (flipped) R.string.kinetica_ui_flip_back else R.string.kinetica_ui_flip_front)
    val drag = if (onFlippedChange == null) {
        Modifier
    } else {
        Modifier.pointerInput(axis) {
            flipDrag(axis, scope, angle) { landed -> if (landed != currentFlipped) currentChange?.invoke(landed) }
        }
    }
    Box(
        modifier
            .semantics(mergeDescendants = true) {
                stateDescription = side
                if (onFlippedChange != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(actionLabel) {
                            onFlippedChange(!flipped)
                            true
                        },
                    )
                }
            }
            .then(drag)
            .graphicsLayer {
                if (axis == FlipAxis.Y) rotationY = angle.value else rotationX = angle.value
                cameraDistance = CAMERA_DISTANCE * density
            },
        propagateMinConstraints = true,
    ) {
        if (showBack) {
            Box(
                Modifier.graphicsLayer { if (axis == FlipAxis.Y) rotationY = HALF_TURN else rotationX = HALF_TURN },
                propagateMinConstraints = true,
            ) { back() }
        } else {
            front()
        }
    }
}

/** 끄는 동안 카드를 손에 맞춰 돌리고, 놓으면 던진 속도를 더해 가까운 면으로 보낸 뒤 [onLanded]에 그 면을 알린다. */
private suspend fun PointerInputScope.flipDrag(
    axis: FlipAxis,
    scope: CoroutineScope,
    angle: Animatable<Float, *>,
    onLanded: (Boolean) -> Unit,
) {
    val tracker = VelocityTracker()
    // 카드 폭만큼 끌면 반 바퀴 돈다. 위아래로 끌 때는 아래로 끌수록 윗면이 앞으로 넘어오게 부호를 뒤집는다.
    val sign = if (axis == FlipAxis.Y) 1f else -1f
    val extent = { (if (axis == FlipAxis.Y) size.width else size.height).coerceAtLeast(1).toFloat() }
    // snapTo는 다음 프레임 전에 반영되지 않을 수 있어 끈 각도를 따로 쌓는다.
    var dragged = angle.value
    var startTurns = 0
    val start: (androidx.compose.ui.geometry.Offset) -> Unit = {
        tracker.resetTracking()
        dragged = angle.value
        startTurns = (dragged / HALF_TURN).roundToInt()
        scope.launch { angle.stop() }
    }
    val end: () -> Unit = {
        val v = tracker.calculateVelocity()
        val degreesPerSecond = sign * (if (axis == FlipAxis.Y) v.x else v.y) / extent() * HALF_TURN
        val projected = dragged + degreesPerSecond * FLING_LOOKAHEAD
        // 세게 던져도 한 번에 한 면만 넘어가게 한다.
        val turns = (projected / HALF_TURN).roundToInt().coerceIn(startTurns - 1, startTurns + 1)
        scope.launch { angle.animateTo(turns * HALF_TURN, spring(FLIP_DAMPING, FLIP_STIFFNESS), degreesPerSecond) }
        onLanded(Math.floorMod(turns, 2) == 1)
    }
    val move: (androidx.compose.ui.input.pointer.PointerInputChange, Float) -> Unit = { change, delta ->
        tracker.addPosition(change.uptimeMillis, change.position)
        change.consume()
        dragged += sign * delta / extent() * HALF_TURN
        val next = dragged
        scope.launch { angle.snapTo(next) }
    }
    if (axis == FlipAxis.Y) {
        detectHorizontalDragGestures(onDragStart = start, onDragEnd = end, onDragCancel = end, onHorizontalDrag = move)
    } else {
        detectVerticalDragGestures(onDragStart = start, onDragEnd = end, onDragCancel = end, onVerticalDrag = move)
    }
}

/** [current]에서 가장 가까운, [back] 면이 보이는 각도(반 바퀴의 배수). */
internal fun nearestFace(current: Float, back: Boolean): Float {
    val turns = (current / HALF_TURN).roundToInt()
    if ((Math.floorMod(turns, 2) == 1) == back) return turns * HALF_TURN
    val below = (turns - 1) * HALF_TURN
    val above = (turns + 1) * HALF_TURN
    return if (current - below < above - current) below else above
}

/** [angle]도로 돌았을 때 뒷면이 앞을 보는지. 옆으로 선 90도를 넘으면 뒷면이다. */
internal fun isBackAngle(angle: Float): Boolean = Math.floorMod(floor((angle + QUARTER_TURN) / HALF_TURN).toInt(), 2) == 1

private const val HALF_TURN = 180f
private const val QUARTER_TURN = 90f
private const val CAMERA_DISTANCE = 16f
private const val FLIP_DAMPING = 0.72f
private const val FLIP_STIFFNESS = 260f
private const val FLING_LOOKAHEAD = 0.12f
