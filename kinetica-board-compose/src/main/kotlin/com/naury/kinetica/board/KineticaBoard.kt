package com.naury.kinetica.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.render.SceneLayout
import com.naury.kinetica.render.SceneSlots
import kotlin.math.roundToInt

/**
 * 호스트 위젯을 사용자가 배치하는 보드. 호스트 화면 아무 곳에나 넣는다.
 *
 * - [editing]이 `false`면 위젯이 저장된 자리에서 평소처럼 동작한다. 화면보다 길면 세로로 스크롤된다.
 * - [editing]이 `true`면 위젯을 끌어 옮기고(다른 위젯·가장자리에 붙음), 오른쪽 아래 손잡이로 크기를 바꾸고, 빼거나 추가한다.
 *   자유 배치에서는 화면 왼쪽·오른쪽 가장자리로 끌면 반, 모서리로 끌면 4분의 1, 위쪽 가장자리로 끌면 화면 가득 분할 배치된다.
 *   편집 중에는 위젯 안이 눌리지 않는다. 변경은 [KineticaBoardState]에 실행 취소 한 단계씩 남는다.
 *
 * @param permissions 편집 모드에서 허용할 일
 * @param content 위젯 키마다 호스트가 그릴 내용. 보드가 정한 크기를 꽉 채우도록 그린다.
 */
@Composable
public fun KineticaBoard(
    state: KineticaBoardState,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    permissions: BoardPermissions = BoardPermissions(),
    colors: BoardColors = BoardColors(),
    content: @Composable (key: String) -> Unit,
) {
    val stored by state.document.collectAsState()
    val density = LocalDensity.current.density
    val zoom = (density / KineticaBoardState.UNITS).toFloat()
    val scroll = rememberScrollState()
    val controller = remember(state) { BoardDragController(state) }
    val slots: SceneSlots = remember(state.catalog, content) { state.catalog.associate { widget -> widget.key to { content(widget.key) } } }
    BoxWithConstraints(modifier.clipToBounds()) {
        val gap = gapDp(state.arrangement) * KineticaBoardState.UNITS
        val mapping = BoardMapping(state.arrangement, constraints.maxWidth / zoom.toDouble(), gap)
        val stable = stored.rootOrder.associate { id ->
            id.value to
                mapping.toDisplay(requireNotNull(KineticaBoardState.rectOf(stored.node(id))))
        }
        val drag = controller.drag
        val rects = drag?.let { stable + (it.key to it.rect) } ?: stable
        val contentBottom = (rects.values.maxOfOrNull { it.bottom } ?: 0.0) + gap
        val viewportHeight = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else (contentBottom * zoom).toFloat()
        val heightPx = maxOf((contentBottom * zoom).toFloat(), viewportHeight)
        controller.frame = BoardFrame(
            mapping = mapping,
            zoom = zoom,
            rects = stable,
            visible = Rect(0.0, scroll.value / zoom.toDouble(), mapping.displayWidth, (scroll.value + viewportHeight) / zoom.toDouble()),
            edge = EDGE_DP * KineticaBoardState.UNITS,
            snap = SNAP_DP * KineticaBoardState.UNITS,
            minSize = MIN_WIDGET_DP * KineticaBoardState.UNITS,
        )
        val shown = displayDocument(stored, rects, mapping.displayWidth, contentBottom)
        Box(Modifier.fillMaxWidth().verticalScroll(scroll, enabled = drag == null)) {
            Box(Modifier.fillMaxWidth().height(with(LocalDensity.current) { heightPx.toDp() })) {
                SceneLayout(shown, Viewport(0.0, 0.0, zoom.toDouble()), Modifier.matchParentSize(), slots = slots)
                if (editing) {
                    drag?.let {
                        TargetPreview(it.target, zoom, colors)
                        DragOutline(it.rect, zoom, colors)
                    }
                    stored.rootOrder.forEach { id ->
                        val key = id.value
                        val widget = state.catalog.firstOrNull { it.key == key }
                        WidgetEditFrame(
                            key = key,
                            label = widget?.label ?: key,
                            // 끄는 동안 틀은 제자리에 두어 손가락 위치의 기준이 흔들리지 않게 한다. 움직이는 모습은 DragOutline이 그린다.
                            rect = stable.getValue(key),
                            dragging = drag?.key == key,
                            zoom = zoom,
                            permissions = permissions.copy(remove = permissions.remove && widget?.removable != false),
                            colors = colors,
                            controller = controller,
                            onRemove = { state.remove(key) },
                        )
                    }
                }
            }
        }
        if (editing && permissions.add) AddWidgetButton(state, stored, colors, Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

private fun gapDp(arrangement: BoardArrangement): Float = when (arrangement) {
    is BoardArrangement.Free -> arrangement.gapDp
    is BoardArrangement.Grid -> arrangement.gapDp
}

/** 저장 문서를 지금 화면 폭 좌표로 바꾼 그리기용 문서. */
private fun displayDocument(stored: SceneDocument, rects: Map<String, Rect>, width: Double, height: Double): SceneDocument {
    val nodes = stored.nodes.mapValues { (id, node) -> rects[id.value]?.let { KineticaBoardState.withRect(node, it) } ?: node }
    return stored.copy(artboard = Artboard(width, height.coerceAtLeast(1.0)), nodes = nodes)
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
    onRemove: () -> Unit,
) {
    val outline = if (dragging) Color.Transparent else colors.outline
    Box(
        Modifier
            .rectPlacement(rect, zoom)
            .border(2.dp, outline, RoundedCornerShape(8.dp))
            .background(outline.copy(alpha = EDIT_TINT))
            .testTag("$BOARD_WIDGET_TAG$key")
            .pointerInput(key, permissions.move) {
                if (!permissions.move) return@pointerInput
                detectDragGestures(
                    onDragStart = { local -> controller.start(key, local) },
                    onDrag = { change, _ ->
                        change.consume()
                        controller.move(key, change.position)
                    },
                    onDragEnd = controller::end,
                    onDragCancel = controller::cancel,
                )
            },
    ) {
        if (permissions.remove) {
            val description = stringResource(R.string.kinetica_board_remove, label)
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(BADGE_SIZE)
                    .background(colors.accent, CircleShape)
                    .semantics { contentDescription = description }
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Text("−", color = colors.onAccent) }
        }
        if (permissions.resize) {
            val description = stringResource(R.string.kinetica_board_resize, label)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(BADGE_SIZE)
                    .background(colors.accent, RoundedCornerShape(topStart = 12.dp))
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
private fun AddWidgetButton(state: KineticaBoardState, document: SceneDocument, colors: BoardColors, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val placed = document.rootOrder.map(NodeId::value).toSet()
    val available = state.catalog.filter { it.key !in placed }
    Box(modifier) {
        TextButton(onClick = { open = true }, modifier = Modifier.background(colors.accent, RoundedCornerShape(24.dp))) {
            Text("+ " + stringResource(R.string.kinetica_board_add), color = colors.onAccent)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (available.isEmpty()) {
                DropdownMenuItem(text = {
                    Text(stringResource(R.string.kinetica_board_nothing_to_add))
                }, onClick = { open = false }, enabled = false)
            }
            available.forEach { widget ->
                DropdownMenuItem(text = { Text(widget.label) }, onClick = {
                    open = false
                    state.add(widget.key)
                })
            }
        }
    }
}

@Composable
private fun Modifier.rectPlacement(rect: Rect, zoom: Float): Modifier {
    val density = LocalDensity.current
    return offset { IntOffset((rect.left * zoom).roundToInt(), (rect.top * zoom).roundToInt()) }
        .size(with(density) { (rect.width * zoom).toFloat().toDp() }, with(density) { (rect.height * zoom).toFloat().toDp() })
}

/** 테스트에서 위젯 편집 틀을 찾을 때 쓰는 tag 앞부분. 뒤에 위젯 키가 붙는다. */
public const val BOARD_WIDGET_TAG: String = "kinetica-board-widget:"

private const val EDGE_DP = 28.0
private const val SNAP_DP = 8.0
private const val MIN_WIDGET_DP = 48.0
private const val EDIT_TINT = 0.08f
private val BADGE_SIZE: Dp = 28.dp
