// 파일 이름은 대표 컴포저블인 KSelfView를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** [KSelfView]가 붙은 모서리. 화면을 돌리거나 접었다 펴도 남는다. */
@Stable
public class KSelfViewState internal constructor(corner: KCorner) {
    /** 붙은 모서리. 바꾸면 그 모서리로 미끄러져 간다. */
    public var corner: KCorner by mutableStateOf(corner)

    internal companion object {
        val Saver: Saver<KSelfViewState, Int> = Saver({ it.corner.ordinal }, { KSelfViewState(KCorner.entries[it]) })
    }
}

/** [KSelfViewState]를 만든다. */
@Composable
public fun rememberKSelfViewState(initialCorner: KCorner = KCorner.BottomEnd): KSelfViewState =
    rememberSaveable(saver = KSelfViewState.Saver) { KSelfViewState(initialCorner) }

/**
 * 화면 모서리에 떠 있는 내 영상 작은 창. 끌어서 놓으면 창 가운데에서 가장 가까운 모서리로 스프링처럼 붙는다.
 * 부모 상자를 꽉 채워 놓되 창 밖의 터치는 막지 않는다. RTL에서는 시작과 끝 모서리가 바뀐다.
 * TalkBack은 '내 화면'으로 읽고 사용자 지정 동작 '다른 모서리로 옮기기'로 시계 방향 다음 모서리로 옮긴다.
 *
 * [KParticipantGrid]의 1:1 배치는 이 창과 같은 규칙으로 내 화면을 놓는다. 격자 없이 상대 영상 하나만 깔 때 이것을 쓴다.
 *
 * ```kotlin
 * Box(Modifier.fillMaxSize()) {
 *     RemoteVideo()
 *     KSelfView(contentPadding = PaddingValues(top = 72.dp, bottom = 120.dp)) { LocalPreview() }
 * }
 * ```
 *
 * @param size 창 크기
 * @param contentPadding 창이 들어가지 않을 가장자리 여백. 상단 바와 조작 막대, 시스템 바를 피할 때 넣는다.
 * @param onClick 창을 눌렀을 때. 상대와 내 화면을 바꿀 때 쓴다. null이면 누를 수 없다.
 * @param content 창 안 내용. 창 모양으로 잘린다.
 */
@Composable
public fun KSelfView(
    modifier: Modifier = Modifier,
    state: KSelfViewState = rememberKSelfViewState(),
    size: DpSize = DpSize(104.dp, 139.dp),
    contentPadding: PaddingValues = PaddingValues(12.dp),
    shape: Shape = shapeFor { card },
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val rtl = direction == LayoutDirection.Rtl
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val bounds = with(density) {
            Rect(
                contentPadding.calculateStartPadding(direction).toPx(),
                contentPadding.calculateTopPadding().toPx(),
                constraints.maxWidth - contentPadding.calculateEndPadding(direction).toPx() - size.width.toPx(),
                constraints.maxHeight - contentPadding.calculateBottomPadding().toPx() - size.height.toPx(),
            )
        }
        val target = cornerPoint(bounds, state.corner)
        val position = remember { Animatable(target, Offset.VectorConverter) }
        var dragging by remember { mutableStateOf(false) }
        val still = reducedMotion()
        LaunchedEffect(target, still) {
            if (dragging) return@LaunchedEffect
            if (still) {
                position.snapTo(
                    target,
                )
            } else {
                position.animateTo(target, spring(dampingRatio = SNAP_DAMPING, stiffness = Spring.StiffnessMediumLow))
            }
        }
        val scope = rememberCoroutineScope()
        Box(
            Modifier
                .offset { IntOffset(position.value.x.roundToInt(), position.value.y.roundToInt()) }
                .size(size)
                .clip(shape)
                .pointerInput(bounds, rtl) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            state.corner =
                                nearestCorner(
                                    position.value + Offset(size.width.toPx() / 2, size.height.toPx() / 2),
                                    bounds.inflateToFull(size, this),
                                )
                            scope.launch { position.animateTo(cornerPoint(bounds, state.corner), spring(dampingRatio = SNAP_DAMPING)) }
                        },
                        onDragCancel = { dragging = false },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { position.snapTo(position.value + Offset(if (rtl) -amount.x else amount.x, amount.y)) }
                    }
                }
                .selfViewActions(state, onClick),
            content = content,
        )
    }
}

/** 누르기와 TalkBack 동작. '다른 모서리로 옮기기'는 시계 방향 다음 모서리로 옮긴다. */
@Composable
private fun Modifier.selfViewActions(state: KSelfViewState, onClick: (() -> Unit)?): Modifier {
    val label = stringResource(R.string.kinetica_ui_call_self_view)
    val move = stringResource(R.string.kinetica_ui_call_move_corner)
    return then(if (onClick != null) Modifier.pointerInput(onClick) { detectTapGestures { onClick() } } else Modifier).semantics {
        contentDescription = label
        customActions = listOf(
            CustomAccessibilityAction(move) {
                state.corner = state.corner.clockwise()
                true
            },
        )
        if (onClick != null) {
            role = Role.Button
            onClick {
                onClick()
                true
            }
        }
    }
}

/** [bounds](창 왼쪽 위가 갈 수 있는 범위) 안에서 [corner]에 해당하는 창 왼쪽 위 */
internal fun cornerPoint(bounds: Rect, corner: KCorner): Offset = when (corner) {
    KCorner.TopStart -> Offset(bounds.left, bounds.top)
    KCorner.TopEnd -> Offset(bounds.right, bounds.top)
    KCorner.BottomStart -> Offset(bounds.left, bounds.bottom)
    KCorner.BottomEnd -> Offset(bounds.right, bounds.bottom)
}

/** 시계 방향 다음 모서리(LTR 기준) */
internal fun KCorner.clockwise(): KCorner = when (this) {
    KCorner.TopStart -> KCorner.TopEnd
    KCorner.TopEnd -> KCorner.BottomEnd
    KCorner.BottomEnd -> KCorner.BottomStart
    KCorner.BottomStart -> KCorner.TopStart
}

/** 창 왼쪽 위 범위를 창 크기만큼 넓혀, 창 가운데로 가까운 모서리를 잴 수 있는 영역으로 바꾼다. */
private fun Rect.inflateToFull(size: DpSize, density: androidx.compose.ui.unit.Density): Rect =
    with(density) { Rect(left, top, right + size.width.toPx(), bottom + size.height.toPx()) }

private const val SNAP_DAMPING = 0.78f
