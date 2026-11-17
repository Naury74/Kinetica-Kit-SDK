package com.naury.kinetica.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize

@Composable
internal fun <T> KanbanColumnView(column: KKanbanColumn<T>, index: Int, context: KanbanContext<T>, width: Dp) {
    val drag = context.drag
    val list = rememberLazyListState()
    val id = column.id
    DisposableEffect(drag, id, list) {
        drag.lists[id] = list
        onDispose {
            drag.lists.remove(id)
            drag.listBounds.remove(id)
        }
    }
    val accent = KineticaTheme.colors.accent
    val shape = shapeFor { listSection }
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(context.surface.columnFill(), shape)
            .drawBehind { if (drag.target?.column == id) drawRect(accent.copy(alpha = TARGET_TINT)) },
    ) {
        ColumnHeader(column, context.surface)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .onGloballyPositioned { drag.listBounds[id] = Rect(it.positionInRoot(), it.size.toSize()) }
                    .dropIndicator(drag, id, list, accent),
                state = list,
                contentPadding = PaddingValues(start = KANBAN_COLUMN_PADDING, end = KANBAN_COLUMN_PADDING, bottom = KANBAN_COLUMN_PADDING),
                verticalArrangement = Arrangement.spacedBy(KANBAN_CARD_GAP),
            ) {
                itemsIndexed(column.cards, key = { _, item -> context.itemKeys.of(context.cards.key(item)) }) { position, item ->
                    KanbanCard(item, CardPlace(index, position), context)
                }
            }
            if (column.cards.isEmpty()) EmptyColumn(context.surface)
        }
    }
}

@Composable
private fun <T> ColumnHeader(column: KKanbanColumn<T>, surface: SurfaceStyle) {
    val ink = surface.contentColor
    val description = pluralStringResource(R.plurals.kinetica_ui_kanban_count, column.cards.size, column.title, column.cards.size)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HEADER_HEIGHT)
            .clearAndSetSemantics {
                heading()
                contentDescription = description
            }
            .padding(horizontal = HEADER_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HEADER_GAP),
    ) {
        if (column.color != Color.Unspecified) Box(Modifier.size(DOT_SIZE).background(column.color, CircleShape))
        ThemedText(
            column.title,
            Modifier.weight(1f, fill = false),
            style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ThemedText(
            column.cards.size.toString(),
            Modifier.background(ink.copy(alpha = COUNT_FILL), CircleShape).padding(horizontal = COUNT_PADDING, vertical = 2.dp),
            style = TextStyle(color = ink.copy(alpha = COUNT_INK), fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyColumn(surface: SurfaceStyle) {
    Box(Modifier.fillMaxWidth().padding(vertical = EMPTY_PADDING), contentAlignment = Alignment.TopCenter) {
        ThemedText(
            stringResource(R.string.kinetica_ui_kanban_empty),
            style = TextStyle(color = surface.muted(EMPTY_ALPHA), fontSize = 13.sp),
        )
    }
}

/** 카드가 놓인 자리. 열 번호와 그 열 안 번호다. */
internal class CardPlace(val column: Int, val index: Int)

@Composable
private fun <T> LazyItemScope.KanbanCard(item: T, place: CardPlace, context: KanbanContext<T>) {
    val drag = context.drag
    val key = context.cards.key(item)
    val dragging = drag.phase == KanbanPhase.Dragging && drag.grab?.key == key
    KeepComposedWhile(dragging)
    // 카드가 끄는 도중 데이터에서 빠지면 손가락 처리도 함께 사라지므로 여기서 끌기를 끝낸다.
    DisposableEffect(drag, key) {
        onDispose { if (drag.phase == KanbanPhase.Dragging && drag.grab?.key == key) drag.cancel() }
    }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val currentItem by rememberUpdatedState(item)
    val from by rememberUpdatedState(KanbanTarget(context.columns[place.column].id, place.index))
    val haptic = kHapticFeedback()
    val moves = cardMoves(key, place, context)
    val position = stringResource(
        R.string.kinetica_ui_kanban_position,
        context.columns[place.column].title,
        place.index + 1,
        context.columns[place.column].cards.size,
    )
    val click = context.cards.onClick
    Box(
        Modifier
            .animateItem(placementSpec = if (drag.isDragged(key)) null else PLACEMENT_SPEC)
            .onGloballyPositioned { coords[0] = it }
            .graphicsLayer {
                alpha = when {
                    !drag.isDragged(key) -> 1f
                    drag.phase == KanbanPhase.Dragging -> GHOST_ALPHA
                    else -> 0f
                }
            }
            .semantics(mergeDescendants = true) {
                stateDescription = position
                customActions = moves.map { it.action }
            }
            .then(if (click != null) Modifier.kClickable(shapeFor { card }) { click(item) } else Modifier.focusable())
            .onKeyEvent { event ->
                if (!event.isAltPressed) return@onKeyEvent false
                val move = moves.firstOrNull { it.key == event.key } ?: return@onKeyEvent false
                if (event.type == KeyEventType.KeyDown) move.action.action()
                true
            }
            .kanbanDrag(drag, key, coords) { at ->
                val c = coords[0] ?: return@kanbanDrag false
                val topLeft = c.positionInRoot()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                drag.start(KanbanGrab(currentItem, key, from, c.size, topLeft, topLeft + at))
                true
            },
    ) { context.cards.content(item, false) }
}

/**
 * 길게 눌러 끈다. 카드가 자리를 옮기거나 목록이 스크롤되어도 이 노드가 따라 움직이므로 손가락 위치는 루트 좌표로 바꿔 넘긴다.
 * 터치가 취소되면 놓기가 아니라 취소로 처리해 원래 자리로 돌려보낸다. [onStart]가 `false`면 끌기를 시작하지 않은 것이다.
 */
private fun Modifier.kanbanDrag(
    drag: KanbanDragState<*>,
    key: Any,
    coords: Array<LayoutCoordinates?>,
    onStart: (Offset) -> Boolean,
): Modifier = pointerInput(drag, key) {
    var started = false
    detectDragGesturesAfterLongPress(
        onDragStart = { at -> started = onStart(at) },
        onDragEnd = { if (started) drag.drop() },
        onDragCancel = { if (started) drag.cancel(coords[0]?.positionInRoot()) },
    ) { change, _ ->
        change.consume()
        val c = coords[0]
        if (started && c != null) drag.moveTo(c.positionInRoot() + change.position)
    }
}

/** [keep]인 동안 이 목록 항목을 화면 밖으로 스크롤되어도 구성에 남겨 손가락 처리가 끊기지 않게 한다. */
@Composable
private fun KeepComposedWhile(keep: Boolean) {
    val pinnable = LocalPinnableContainer.current
    DisposableEffect(pinnable, keep) {
        val handle = if (keep) pinnable?.pin() else null
        onDispose { handle?.release() }
    }
}

/** 카드 하나를 옮기는 동작과 그 동작을 부르는 키(Alt와 함께). */
private class CardMove(val key: Key, val action: CustomAccessibilityAction)

/** 지금 자리에서 할 수 있는 이동만 담는다. 왼쪽과 오른쪽은 화면에서 보이는 방향이라 RTL에서는 앞 열이 오른쪽이다. */
@Composable
private fun <T> cardMoves(key: Any, place: CardPlace, context: KanbanContext<T>): List<CardMove> {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val leftLabel = stringResource(R.string.kinetica_ui_kanban_to_left)
    val rightLabel = stringResource(R.string.kinetica_ui_kanban_to_right)
    val upLabel = stringResource(R.string.kinetica_ui_move_up)
    val downLabel = stringResource(R.string.kinetica_ui_move_down)
    val columns = context.columns
    val column = columns[place.column]
    val move = context.onMove
    fun toColumn(target: Int, label: String, keyCode: Key): CardMove? {
        val other = columns.getOrNull(target) ?: return null
        return CardMove(keyCode, action(label) { move(key, other.id, minOf(place.index, other.cards.size)) })
    }
    val left = if (rtl) place.column + 1 else place.column - 1
    val right = if (rtl) place.column - 1 else place.column + 1
    return listOfNotNull(
        if (place.index > 0) CardMove(Key.DirectionUp, action(upLabel) { move(key, column.id, place.index - 1) }) else null,
        if (place.index < column.cards.lastIndex) {
            CardMove(Key.DirectionDown, action(downLabel) { move(key, column.id, place.index + 1) })
        } else {
            null
        },
        toColumn(left, leftLabel, Key.DirectionLeft),
        toColumn(right, rightLabel, Key.DirectionRight),
    )
}

private fun action(label: String, run: () -> Unit) = CustomAccessibilityAction(label) {
    run()
    true
}

/** 놓을 자리에 강조색 선을 긋는다. 자리와 목록 스크롤은 그리기 단계에서만 읽는다. */
private fun Modifier.dropIndicator(drag: KanbanDragState<*>, id: Any, list: LazyListState, color: Color): Modifier = drawWithContent {
    drawContent()
    val y = drag.indicatorY(id, list) ?: return@drawWithContent
    val thickness = INDICATOR_HEIGHT.toPx()
    val inset = KANBAN_COLUMN_PADDING.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(inset, y - thickness / 2),
        size = Size(size.width - inset * 2, thickness),
        cornerRadius = CornerRadius(thickness / 2),
    )
}

private val PLACEMENT_SPEC = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
private val HEADER_HEIGHT = 48.dp
private val HEADER_PADDING = 14.dp
private val HEADER_GAP = 8.dp
private val DOT_SIZE = 8.dp
private val COUNT_PADDING = 8.dp
private val EMPTY_PADDING = 24.dp
private val INDICATOR_HEIGHT = 3.dp
private const val COUNT_FILL = 0.08f
private const val COUNT_INK = 0.7f
private const val EMPTY_ALPHA = 0.45f
private const val TARGET_TINT = 0.06f
private const val GHOST_ALPHA = 0.3f
