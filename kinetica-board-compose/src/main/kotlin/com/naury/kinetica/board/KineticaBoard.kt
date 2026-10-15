package com.naury.kinetica.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.render.SceneLayout
import com.naury.kinetica.render.SceneSlots
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 사용자가 호스트 위젯을 직접 배치하는 보드. 호스트 화면 어디에나 넣을 수 있다.
 *
 * - [editing]이 `false`면 위젯이 저장된 위치에서 평소처럼 동작한다. 내용이 화면보다 길면 세로로 스크롤된다.
 * - [editing]이 `true`면 위젯을 드래그해 옮기고, 오른쪽 아래 핸들로 크기를 바꾸고, 삭제하거나 추가할 수 있다. 옮길 때는 다른 위젯과
 *   가장자리에 스냅된다. 자유 배치에서는 화면 왼쪽이나 오른쪽 가장자리로 끌면 절반, 모서리로 끌면 4분의 1, 위쪽 가장자리로 끌면
 *   전체 화면을 차지한다. 편집 중에는 위젯 내부를 누를 수 없다. 변경할 때마다 [KineticaBoardState]에 실행 취소 한 단계가 쌓인다.
 *
 * @param permissions 편집 모드에서 허용할 작업
 * @param colors 편집 화면의 색. 기본값은 현재 MaterialTheme의 primary를 따른다.
 * @param stackAutoAdvanceMs 0보다 크면 사용 모드에서 위젯 스택이 이 간격(ms)마다 다음 위젯으로 넘어간다. 사용자가 직접 넘기면 그때부터
 *   다시 잰다. 편집 중이거나 시스템 애니메이션이 꺼져 있으면 넘어가지 않는다.
 * @param onLongPress 사용 모드에서 위젯을 길게 누르면 호출된다. 보통 여기서 편집 모드로 바꾼다. 이 누름 이벤트는 위젯에 전달하지 않는다.
 * @param content 슬롯마다 호스트가 그릴 내용. 인자는 슬롯 id다. 한 번만 놓는 위젯은 위젯 키와 같고, 여러 개 놓는 위젯([BoardWidget.multiple])은
 *   슬롯마다 다르다. 위젯 키는 [KineticaBoardState.widgetKeyOf]로 얻는다. 보드가 정한 크기를 꽉 채우도록 그린다.
 */
@Composable
public fun KineticaBoard(
    state: KineticaBoardState,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    permissions: BoardPermissions = BoardPermissions(),
    colors: BoardColors = BoardColors.fromMaterialTheme(),
    onLongPress: (() -> Unit)? = null,
    stackAutoAdvanceMs: Long = 0L,
    content: @Composable (id: String) -> Unit,
) {
    val stored by state.document.collectAsState()
    val density = LocalDensity.current.density
    val zoom = (density / KineticaBoardState.UNITS).toFloat()
    val scroll = rememberScrollState()
    val controller = remember(state) { BoardDragController(state) }
    // RTL 언어면 화면 좌표를 뒤집는다. 보드 내부는 왼쪽 기준 좌표로 그리고, 위젯 내용에는 호스트의 레이아웃 방향을 다시 넘긴다.
    val hostDirection = LocalLayoutDirection.current
    val mirrored = hostDirection == LayoutDirection.Rtl
    val slots = rememberBoardSlots(stored, content, colors, StackAdvance(stackAutoAdvanceMs, editing), hostDirection)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalBoardMirrored provides mirrored) {
        BoardBody(state, stored, modifier, editing, permissions, colors, onLongPress, scroll, controller, slots, hostDirection, zoom)
    }
}

@Composable
@Suppress("LongParameterList", "LongMethod")
private fun BoardBody(
    state: KineticaBoardState,
    stored: SceneDocument,
    modifier: Modifier,
    editing: Boolean,
    permissions: BoardPermissions,
    colors: BoardColors,
    onLongPress: (() -> Unit)?,
    scroll: androidx.compose.foundation.ScrollState,
    controller: BoardDragController,
    slots: SceneSlots,
    hostDirection: LayoutDirection,
    zoom: Float,
) {
    val mirrored = LocalBoardMirrored.current
    BoxWithConstraints(modifier.clipToBounds()) {
        val gap = gapDp(state.arrangement) * KineticaBoardState.UNITS
        val mapping = BoardMapping(state.arrangement, constraints.maxWidth / zoom.toDouble(), gap, mirrored)
        val stable = stored.rootOrder.associate { id ->
            id.value to
                mapping.toDisplay(requireNotNull(KineticaBoardState.rectOf(stored.node(id))))
        }
        val drag = controller.drag
        val rects = drag?.let { stable + (it.key to it.rect) } ?: stable
        // 편집 중에는 맨 아래 위젯이 위젯 추가 버튼에 가리지 않도록 스크롤 여유를 둔다.
        val editRoom = if (editing && permissions.add) EDIT_BOTTOM_ROOM_DP * KineticaBoardState.UNITS else 0.0
        val stableBottom = stable.values.maxOfOrNull { it.bottom } ?: 0.0
        val viewportHeight = if (constraints.hasBoundedHeight) {
            constraints.maxHeight.toFloat()
        } else {
            ((stableBottom + gap + editRoom) * zoom).toFloat()
        }
        // 드래그 중인 위젯을 따라 보드를 늘리되 현재 내용 아래로 한 화면까지만 늘려서 자동 스크롤이 끝없이 이어지지 않게 한다.
        val dragBottom = drag?.rect?.bottom?.coerceAtMost(stableBottom + viewportHeight / zoom) ?: 0.0
        val contentBottom = maxOf(stableBottom, dragBottom) + gap + editRoom
        val heightPx = maxOf((contentBottom * zoom).toFloat(), viewportHeight)
        controller.frame = BoardFrame(
            mapping = mapping,
            zoom = zoom,
            rects = stable,
            // 분할 영역은 위젯 추가 버튼 위까지만 써서, 놓은 위젯의 크기 조절 핸들이 버튼에 가리지 않게 한다.
            viewportHeight = viewportHeight / zoom.toDouble(),
            bottomInset = editRoom,
            scrollTop = { scroll.value / zoom.toDouble() },
            edge = EDGE_DP * KineticaBoardState.UNITS,
            snap = SNAP_DP * KineticaBoardState.UNITS,
            minSize = MIN_WIDGET_DP * KineticaBoardState.UNITS,
            scrollBand = SCROLL_BAND_DP * KineticaBoardState.UNITS,
        )
        AutoScroll(controller, scroll, drag?.key)
        // 120Hz에서 프레임을 놓치지 않도록, 드래그하는 동안 다른 위젯 노드는 같은 객체로 유지해 렌더러가 재구성하지 않게 한다.
        val base = remember(state, stored, stable, mapping.displayWidth) { displayDocument(state, stored, stable, mapping.displayWidth) }
        val shown = withDrag(base, drag, contentBottom)
        val longPress = if (!editing && onLongPress != null) Modifier.longPressToEdit(onLongPress) else Modifier
        Box(Modifier.fillMaxWidth().verticalScroll(scroll, enabled = drag == null).then(longPress)) {
            Box(Modifier.fillMaxWidth().height(with(LocalDensity.current) { heightPx.toDp() })) {
                SceneLayout(shown, Viewport(0.0, 0.0, zoom.toDouble()), Modifier.matchParentSize(), slots = slots, interactive = !editing)
                if (editing) EditLayer(state, stored, stable, drag, zoom, permissions, colors, controller)
            }
        }
        if (editing && permissions.add) {
            AddWidgetCorner(hostDirection, mirrored) { AddWidgetButton(state, stored, colors, it) }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun EditLayer(
    state: KineticaBoardState,
    stored: SceneDocument,
    stable: Map<String, Rect>,
    drag: BoardDrag?,
    zoom: Float,
    permissions: BoardPermissions,
    colors: BoardColors,
    controller: BoardDragController,
) {
    drag?.let {
        TargetPreview(it.target, zoom, colors)
        DragOutline(it.rect, zoom, colors)
    }
    val picker = drag?.picker?.let { hover -> controller.picker()?.let { it to hover } }
    stored.rootOrder.forEach { id ->
        val key = id.value
        val widget = state.widgetKeyOf(key)?.let { widgetKey -> state.catalog.firstOrNull { it.key == widgetKey } }
        val members = state.stackMembers(key)
        val label = if (members.isEmpty()) widget?.label ?: key else stackLabel(state, members)
        WidgetEditFrame(
            key = key,
            label = label,
            // 손가락 위치의 기준이 흔들리지 않도록 드래그하는 동안 테두리는 제자리에 둔다. 움직이는 모습은 DragOutline이 그린다.
            rect = stable.getValue(key),
            dragging = drag?.key == key,
            zoom = zoom,
            permissions = permissions.copy(remove = permissions.remove && widget?.removable != false),
            colors = colors,
            controller = controller,
            actions = accessibilityActions(state, key, permissions.copy(remove = permissions.remove && widget?.removable != false)) +
                unstackActions(state, key, members),
            modifier = Modifier.keyboardEditing(
                state,
                key,
                permissions.copy(remove = permissions.remove && widget?.removable != false),
                LocalBoardMirrored.current,
            ),
            onRemove = { state.remove(key) },
        )
        if (members.isNotEmpty()) StackBadge(stable.getValue(key), zoom, members.size, colors) { state.unstack(key) }
    }
    // 위쪽 가장자리로 끌면 레이아웃 선택 패널이 위젯 테두리 위에 뜬다.
    picker?.let { (geometry, hover) -> SnapPickerView(geometry, hover, zoom, colors) }
}

/** 드래그 중 손가락이 위나 아래 자동 스크롤 영역에 있으면 매 프레임 보드를 스크롤하고 드래그 위치를 보정한다. */
@Composable
private fun AutoScroll(controller: BoardDragController, scroll: androidx.compose.foundation.ScrollState, dragging: String?) {
    val speed = with(LocalDensity.current) { AUTO_SCROLL_DP_PER_FRAME.dp.toPx() }
    androidx.compose.runtime.LaunchedEffect(dragging) {
        val key = dragging ?: return@LaunchedEffect
        // 자동 스크롤 영역 안에 있을 때만 프레임마다 스크롤한다. 끝에 닿으면 프레임을 요청하지 않고 보드가 늘어날 때까지 기다린다.
        // 손가락이 영역에 막 들어온 순간에는 보드가 아직 늘어나기 전이라 끝에 닿아 있을 수 있다. 이때 멈추면 다시 시작하지 않는다.
        androidx.compose.runtime.snapshotFlow { controller.scrollDirection }.collectLatest { direction ->
            while (direction != 0) {
                androidx.compose.runtime.withFrameNanos { }
                val moved = scroll.scrollBy(direction * speed)
                if (moved == 0f) {
                    if (direction < 0) break
                    val end = scroll.value
                    androidx.compose.runtime.snapshotFlow { scroll.maxValue }.first { it > end }
                } else {
                    controller.scrolled(key, moved)
                }
            }
        }
    }
}

/**
 * 길게 누르면 [onLongPress]를 호출하고 그 뒤의 터치 이벤트는 가로챈다. 짧게 누르거나 움직이면 아무것도 하지 않으므로 위젯과 스크롤이 평소대로 받는다.
 */
private fun Modifier.longPressToEdit(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var ended = false
            while (!ended) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                ended = change == null || !change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            }
            true
        }
        if (released == null) {
            onLongPress()
            // 손을 뗄 때까지 위젯이 누르기와 떼기 이벤트를 받지 않도록 가로챈다.
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

private fun gapDp(arrangement: BoardArrangement): Float = when (arrangement) {
    is BoardArrangement.Free -> arrangement.gapDp
    is BoardArrangement.Grid -> arrangement.gapDp
}

/** 저장된 문서를 현재 화면 폭 좌표로 바꾼 그리기용 문서. */
private fun displayDocument(state: KineticaBoardState, stored: SceneDocument, rects: Map<String, Rect>, width: Double): SceneDocument {
    val effects = state.catalog.associate { it.key to it.effects }
    val nodes = stored.nodes.mapValues { (id, node) ->
        val placed = rects[id.value]?.let { KineticaBoardState.withRect(node, it) } ?: node
        // 효과는 저장하지 않으므로 위젯 목록에서 가져온다.
        state.widgetKeyOf(id.value)?.let(effects::get)?.let { placed.copy(effects = it) } ?: placed
    }
    return stored.copy(artboard = Artboard(width, stored.artboard.height), nodes = nodes)
}

/** 드래그 중인 위젯 하나만 바꾸고 artboard 높이를 맞춘다. 나머지 노드는 [base]의 객체를 그대로 쓴다. */
private fun withDrag(base: SceneDocument, drag: BoardDrag?, height: Double): SceneDocument {
    val artboard = base.artboard.copy(height = height.coerceAtLeast(1.0))
    val moved = drag?.let { current -> base.node(NodeId(current.key))?.let { KineticaBoardState.withRect(it, current.rect) } }
    val nodes = if (moved == null) base.nodes else base.nodes + (moved.id to moved)
    return base.copy(artboard = artboard, nodes = nodes)
}

@Composable
private fun TargetPreview(target: Rect, zoom: Float, colors: BoardColors) {
    Box(
        Modifier
            .rectPlacement(target, zoom)
            .background(colors.zone, RoundedCornerShape(12.dp))
            .border(2.dp, colors.accent, RoundedCornerShape(12.dp)),
    )
}

@Composable
private fun DragOutline(rect: Rect, zoom: Float, colors: BoardColors) {
    Box(Modifier.rectPlacement(rect, zoom).border(2.dp, colors.outline, RoundedCornerShape(8.dp)))
}

@Composable
@Suppress("LongParameterList")
private fun WidgetEditFrame(
    key: String,
    label: String,
    rect: Rect,
    zoom: Float,
    dragging: Boolean,
    permissions: BoardPermissions,
    colors: BoardColors,
    controller: BoardDragController,
    actions: List<CustomAccessibilityAction>,
    modifier: Modifier,
    onRemove: () -> Unit,
) {
    val outline = if (dragging) Color.Transparent else colors.outline
    var focused by remember { mutableStateOf(false) }
    // 삭제 버튼은 시작쪽 위, 크기 조절 핸들은 끝쪽 아래에 둔다. 보드 내부는 왼쪽 기준이므로 RTL 화면이면 직접 바꾼다.
    val mirrored = LocalBoardMirrored.current
    Box(
        Modifier
            .rectPlacement(rect, zoom)
            .editOutline(focused, outline, colors.accent)
            .background(outline.copy(alpha = EDIT_TINT))
            .testTag("$BOARD_WIDGET_TAG$key")
            .semantics {
                contentDescription = label
                customActions = actions
            }
            .onFocusChanged { focused = it.isFocused }
            .then(modifier)
            .dragToMove(key, permissions.move, controller),
    ) {
        if (permissions.remove) {
            val description = stringResource(R.string.kinetica_board_remove, label)
            Box(
                Modifier
                    .align(if (mirrored) Alignment.TopEnd else Alignment.TopStart)
                    .offset(if (mirrored) BADGE_OUTSET else -BADGE_OUTSET, -BADGE_OUTSET)
                    .size(BADGE_SIZE)
                    .background(colors.accent, CircleShape)
                    .semantics { contentDescription = description }
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { RemoveMark(colors.onAccent) }
        }
        if (permissions.resize) {
            val description = stringResource(R.string.kinetica_board_resize, label)
            Box(
                Modifier
                    .align(if (mirrored) Alignment.BottomStart else Alignment.BottomEnd)
                    .size(BADGE_SIZE)
                    .background(colors.accent, if (mirrored) RoundedCornerShape(topEnd = 12.dp) else RoundedCornerShape(topStart = 12.dp))
                    .semantics { contentDescription = description }
                    .pointerInput(key) {
                        detectDragGestures(
                            onDragStart = { local -> controller.start(key, local) },
                            onDrag = { change, _ ->
                                change.consume()
                                controller.resize(key, change.position)
                            },
                            onDragEnd = controller::end,
                            onDragCancel = controller::cancel,
                        )
                    },
            )
        }
    }
}

@Composable
internal fun Modifier.rectPlacement(rect: Rect, zoom: Float): Modifier {
    val density = LocalDensity.current
    return offset { IntOffset((rect.left * zoom).roundToInt(), (rect.top * zoom).roundToInt()) }
        .size(with(density) { (rect.width * zoom).toFloat().toDp() }, with(density) { (rect.height * zoom).toFloat().toDp() })
}

/** 테스트에서 위젯 편집 테두리를 찾을 때 쓰는 tag 접두사. 뒤에 위젯 키가 붙는다. */
public const val BOARD_WIDGET_TAG: String = "kinetica-board-widget:"

private const val EDGE_DP = 40.0
private const val SNAP_DP = 8.0
private const val MIN_WIDGET_DP = 48.0
private const val EDIT_TINT = 0.08f
private const val EDIT_BOTTOM_ROOM_DP = 88.0
private const val SCROLL_BAND_DP = 48.0
private const val AUTO_SCROLL_DP_PER_FRAME = 8
internal const val NUDGE_DP = 8f
private val FOCUS_BORDER = 4.dp

private fun Modifier.dragToMove(key: String, enabled: Boolean, controller: BoardDragController): Modifier = pointerInput(key, enabled) {
    if (!enabled) return@pointerInput
    detectDragGestures(
        onDragStart = { local -> controller.start(key, local) },
        onDrag = { change, _ ->
            change.consume()
            controller.move(key, change.position)
        },
        onDragEnd = controller::end,
        onDragCancel = controller::cancel,
    )
}

// 키보드로 선택한 위젯이 어느 것인지 알 수 있도록 포커스가 있으면 테두리를 굵고 진하게 그린다.
private fun Modifier.editOutline(focused: Boolean, outline: Color, accent: Color): Modifier =
    border(if (focused) FOCUS_BORDER else 2.dp, if (focused) accent else outline, RoundedCornerShape(8.dp))
private val BADGE_SIZE: Dp = 28.dp
private val BADGE_OUTSET: Dp = 9.dp

/** 슬롯마다 그릴 호스트 내용. 스택이면 넘겨 보는 페이저를 그린다. */
@Composable
private fun rememberBoardSlots(
    stored: SceneDocument,
    content: @Composable (String) -> Unit,
    colors: BoardColors,
    advance: StackAdvance,
    direction: LayoutDirection,
): SceneSlots {
    // 편집 중에는 스택이 자동으로 넘어가지 않는다.
    val autoAdvanceMs = if (advance.editing) 0L else advance.intervalMs
    return remember(stored.rootOrder, stored, content, autoAdvanceMs, direction) {
        stored.rootOrder.associate { id ->
            val members = stored.node(id)?.stackMembers()?.map { it.id }.orEmpty()
            id.value to {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    if (members.isEmpty()) content(id.value) else StackPager(members, content, colors, autoAdvanceMs)
                }
            }
        }
    }
}

private class StackAdvance(val intervalMs: Long, val editing: Boolean)

/** RTL 언어 때문에 보드 화면 좌표가 좌우로 뒤집혀 있는지. */
internal val LocalBoardMirrored = staticCompositionLocalOf { false }
