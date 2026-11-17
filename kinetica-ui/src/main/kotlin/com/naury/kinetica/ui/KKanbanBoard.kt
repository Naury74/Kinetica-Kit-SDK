package com.naury.kinetica.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/**
 * [KKanbanBoard]의 열 하나.
 *
 * @property id 열을 가리는 키. 열마다 달라야 하며 [KKanbanBoard]의 `onMove`가 이 값을 돌려준다.
 * @property title 머리 글. TalkBack은 카드 수와 함께 읽는다.
 * @property cards 위에서 아래 순서의 카드
 * @property color 머리 앞 점의 색. 지정하지 않으면 점을 그리지 않는다.
 */
@Immutable
public data class KKanbanColumn<T>(val id: Any, val title: String, val cards: List<T>, val color: Color = Color.Unspecified)

/**
 * 칸반 보드. 열을 가로로 늘어놓고, 카드를 길게 눌러 들어 올린 뒤 같은 열 안 다른 자리나 다른 열로 끌어 옮긴다.
 * 끄는 동안 카드는 그림자와 함께 살짝 기울어 떠 있고, 놓을 자리에는 강조색 선이 그어진다. 보드나 열 가장자리 가까이 끌면
 * 그쪽으로 자동 스크롤한다. 놓으면 [onMove]를 한 번 부르고, 자리가 그대로이거나 터치가 취소되면 부르지 않고 원래 자리로 돌아간다.
 * 보드는 데이터를 직접 바꾸지 않으므로 [onMove]에서 [columns]를 고쳐 넘겨야 한다.
 *
 * 열은 [columnWidth] 폭이고 넘치면 가로로 스크롤한다. 펼친 폴더블이나 태블릿처럼 모든 열이 [columnWidth] 이상으로 들어가면
 * 남는 폭을 나눠 열을 나란히 다 보인다. TalkBack과 하드웨어 키보드에서는 카드마다 왼쪽 열로, 오른쪽 열로, 위로, 아래로 이동하는
 * 동작을 쓸 수 있고, 키보드는 카드에 초점을 두고 Alt와 방향키를 누른다.
 *
 * 높이가 정해진 곳에 둔다. 높이가 무한하면(세로 스크롤 부모 안) 480dp로 그린다.
 *
 * ```kotlin
 * KKanbanBoard(
 *     columns = board,
 *     cardKey = { it.id },
 *     onMove = { id, toColumn, toIndex -> viewModel.move(id, toColumn, toIndex) },
 * ) { task, dragging -> TaskCard(task) }
 * ```
 *
 * @param cardKey 카드를 가리는 키. 보드 전체에서 달라야 한다.
 * @param onMove 카드를 옮길 때. `toIndex`는 옮긴 뒤 [toColumn] 열 목록에서의 번호(0부터)다.
 * @param columnWidth 열의 기본 폭. 0보다 커야 하며 아니면 [IllegalArgumentException]을 던진다.
 * @param onCardClick 카드를 눌렀을 때. `null`이면 카드는 키보드 초점만 받는다.
 * @param cardShape 떠 있는 카드의 그림자 모양. [card]가 그리는 카드 모양과 맞춘다.
 * @param card 카드 내용. 두 번째 인자는 지금 떠 있는 사본인지다.
 */
@Composable
public fun <T> KKanbanBoard(
    columns: List<KKanbanColumn<T>>,
    cardKey: (T) -> Any,
    onMove: (cardKey: Any, toColumn: Any, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    columnWidth: Dp = 280.dp,
    onCardClick: ((T) -> Unit)? = null,
    cardShape: Shape = shapeFor { this.card },
    surface: SurfaceStyle = KineticaTheme.surface,
    card: @Composable (item: T, dragging: Boolean) -> Unit,
) {
    require(columnWidth > 0.dp) { "columnWidth는 0보다 커야 합니다." }
    val scope = rememberCoroutineScope()
    val drag = remember(scope) { KanbanDragState<T>(scope) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    SideEffect {
        drag.onMove = onMove
        drag.columnIds = columns.map { it.id }
        drag.horizontal = scroll
        drag.horizontalSign = if (rtl) -1f else 1f
        with(density) {
            drag.edgePx = AUTO_SCROLL_EDGE.toPx()
            drag.maxSpeedPx = AUTO_SCROLL_SPEED.toPx()
            drag.gapPx = KANBAN_CARD_GAP.toPx()
        }
    }
    DisposableEffect(drag) { onDispose { drag.reset() } }
    val context = KanbanContext(drag, columns, KanbanCards(cardKey, onCardClick, card), onMove, surface)
    BoxWithConstraints(modifier) {
        val bounded = if (constraints.hasBoundedHeight) Modifier else Modifier.height(DEFAULT_HEIGHT)
        val width = kanbanColumnWidth(maxWidth, columns.size, columnWidth)
        Box(
            bounded.fillMaxWidth().onGloballyPositioned { drag.boardBounds = Rect(it.positionInRoot(), it.size.toSize()) },
        ) {
            BoardColumns(context, scroll, width)
            FloatingCard(drag, cardShape, card)
        }
    }
}

/** 보드 전체에서 열과 카드가 함께 쓰는 값. */
internal class KanbanContext<T>(
    val drag: KanbanDragState<T>,
    val columns: List<KKanbanColumn<T>>,
    val cards: KanbanCards<T>,
    val onMove: (Any, Any, Int) -> Unit,
    val surface: SurfaceStyle,
)

/** 카드 키, 누르기, 내용. */
internal class KanbanCards<T>(val key: (T) -> Any, val onClick: ((T) -> Unit)?, val content: @Composable (T, Boolean) -> Unit)

@Composable
private fun <T> BoardColumns(context: KanbanContext<T>, scroll: ScrollState, width: Dp) {
    Row(
        Modifier.fillMaxHeight().horizontalScroll(scroll).padding(horizontal = BOARD_PADDING),
        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
    ) {
        context.columns.forEachIndexed { index, column ->
            key(column.id) { KanbanColumnView(column, index, context, width) }
        }
    }
}

/** 손가락을 따라 떠 있는 카드 사본. 루트 좌표를 보드 좌표로 바꿔 놓고, 위치와 들림 정도는 graphicsLayer에서만 읽는다. */
@Composable
private fun <T> FloatingCard(drag: KanbanDragState<T>, shape: Shape, card: @Composable (T, Boolean) -> Unit) {
    val grab = drag.grab ?: return
    Layout(
        content = {
            Box(
                Modifier
                    .graphicsLayer {
                        val at = drag.floatTopLeft() - drag.boardBounds.topLeft
                        val level = drag.lift.value
                        translationX = at.x
                        translationY = at.y
                        scaleX = 1f + LIFT_SCALE * level
                        scaleY = scaleX
                        rotationZ = LIFT_TILT * level
                        shadowElevation = LIFT_SHADOW.toPx() * level
                        this.shape = shape
                    }
                    // 사본은 TalkBack에 읽히지 않는다. 원래 카드가 같은 내용을 갖고 있다.
                    .clearAndSetSemantics {},
            ) { card(grab.item, true) }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints.fixed(grab.size.width, grab.size.height))
        // 루트 좌표에서 온 위치이므로 RTL에서도 뒤집지 않고 왼쪽 위 기준으로 놓는다.
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
    }
}

/** 열 폭. 모든 열이 [preferred] 이상으로 들어가면 남는 폭을 나눠 주고, 아니면 [preferred]를 쓰되 화면 폭의 86%를 넘지 않는다. */
internal fun kanbanColumnWidth(maxWidth: Dp, count: Int, preferred: Dp): Dp {
    if (count <= 0 || maxWidth == Dp.Infinity) return preferred
    val available = maxWidth - BOARD_PADDING * 2 - COLUMN_GAP * (count - 1)
    val share = available / count
    return if (share >= preferred) share else minOf(preferred, maxWidth * NARROW_SHARE)
}

internal val KANBAN_CARD_GAP = 10.dp
internal val KANBAN_COLUMN_PADDING = 10.dp
private val BOARD_PADDING = 16.dp
private val COLUMN_GAP = 12.dp
private val DEFAULT_HEIGHT = 480.dp
private val AUTO_SCROLL_EDGE = 56.dp
private val AUTO_SCROLL_SPEED = 14.dp
private val LIFT_SHADOW = 18.dp
private const val LIFT_SCALE = 0.04f
private const val LIFT_TILT = 2.5f
private const val NARROW_SHARE = 0.86f

/** 열 바탕 색. [SurfaceStyle.raised]가 있으면 그것을, 없으면 글자 색을 옅게 깐다. */
internal fun SurfaceStyle.columnFill(): Color = if (raised != Color.Unspecified) raised else contentColor.copy(alpha = COLUMN_FILL_ALPHA)

private const val COLUMN_FILL_ALPHA = 0.05f
