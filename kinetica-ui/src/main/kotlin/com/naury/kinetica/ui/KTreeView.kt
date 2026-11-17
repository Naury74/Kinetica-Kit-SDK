package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 파일 탐색기 같은 계층 목록. 펼친 노드의 자식만 `LazyColumn`에 늘어놓아 그리므로 노드가 수천 개여도 괜찮다.
 * 늘어놓는 계산은 [roots]나 펼침 상태가 바뀔 때만 다시 한다.
 *
 * 자식이 있는 행을 누르면 고르면서 펼치거나 접는다. 화살표가 돌고 자식 행은 서서히 나타난다. [guides]면 수준마다 세로 안내선을 긋는다.
 * [checkable]이면 행 앞에 체크박스를 두고, 부모 체크박스는 아래 잎이 모두 켜지면 켜짐, 일부만 켜지면 일부 상태로 그린다.
 * 부모를 누르면 아래 잎을 모두 켜거나 모두 끈다.
 *
 * 하드웨어 키보드로 목록에 초점이 오면 위아래 방향키로 행을 옮기고, 오른쪽 방향키로 펼치거나 첫 자식으로, 왼쪽 방향키로 접거나
 * 부모로 간다. RTL에서는 좌우가 바뀐다. Enter는 그 행을 고르고, 스페이스는 체크를 바꾼다. TalkBack은 행마다 수준과 형제 가운데 순번,
 * 펼침 상태를 읽고 펼치기와 접기 동작을 제공한다.
 *
 * 세로 높이가 정해진 곳에 둔다. 세로로 스크롤되는 부모 안에서는 `Modifier.height`로 높이를 준다.
 *
 * ```kotlin
 * KTreeView(
 *     roots = files,
 *     key = { it.path },
 *     children = { it.children },
 *     label = { it.name },
 *     onSelect = { open(it) },
 * )
 * ```
 *
 * @param roots 맨 위 노드. 새 목록을 넘기거나 `SnapshotStateList`를 제자리에서 고쳐도 반영한다. 자식 목록도 마찬가지다.
 * @param key 노드를 가리는 키. 트리 전체에서 달라야 하며, 같은 키가 다시 나오면 두 번째부터는 건너뛴다.
 *   Bundle에 담을 수 없는 타입이어도 되지만, 그런 키의 펼침, 선택, 체크는 회전 뒤에 남지 않는다.
 * @param children 노드의 자식. 빈 목록이면 잎이다. [roots]가 바뀌면 사라진 키를 정리하려고 트리 전체에 대해 부른다.
 * @param label 행 글. TalkBack도 이 글을 읽는다.
 * @param state 펼침, 선택, 체크 상태
 * @param checkable 행 앞에 세 상태 체크박스를 둘지
 * @param guides 수준마다 세로 안내선을 그을지
 * @param toggleOnClick 자식이 있는 행을 누를 때 펼침도 바꿀지. `false`면 행을 누르면 고르기만 하고, 펼침은 화살표를 눌러 바꾼다.
 * @param onSelect 행을 고르면 부른다.
 * @param indent 수준 하나의 들여쓰기 폭
 * @param leading 화살표 뒤, 글 앞에 놓을 아이콘. 두 번째 인자는 펼쳤는지다.
 * @param trailing 행 끝에 놓을 내용
 */
@Composable
public fun <T> KTreeView(
    roots: List<T>,
    key: (T) -> Any,
    children: (T) -> List<T>,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    state: KTreeState = rememberKTreeState(),
    checkable: Boolean = false,
    guides: Boolean = true,
    toggleOnClick: Boolean = true,
    onSelect: ((T) -> Unit)? = null,
    indent: Dp = 20.dp,
    listState: LazyListState = rememberLazyListState(),
    surface: SurfaceStyle = KineticaTheme.surface,
    leading: (@Composable (item: T, expanded: Boolean) -> Unit)? = null,
    trailing: (@Composable (item: T) -> Unit)? = null,
) {
    val access = remember(key, children) { TreeAccess(key, children) }
    val currentAccess by rememberUpdatedState(access)
    // 호스트가 같은 SnapshotStateList를 제자리에서 고쳐도 다시 계산하도록 목록 읽기를 derivedStateOf로 추적한다.
    val present by remember(roots) { derivedStateOf { allTreeKeys(roots, currentAccess) } }
    DisposableEffect(state, present) {
        state.retain(present)
        onDispose {}
    }
    val rows by remember(roots, state) { derivedStateOf { flattenTree(roots, currentAccess, state.expanded) } }
    val checkStates by remember(roots, state) { derivedStateOf { treeCheckStates(roots, currentAccess, state.checked) } }
    val checks = if (checkable) checkStates else emptyMap()
    val itemKeys = rememberLazyItemKeys()
    val select by rememberUpdatedState(onSelect)
    var focused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val actions = TreeActions(
        state,
        onSelect = { row ->
            state.selected = row.key
            state.active = row.key
            select?.invoke(row.item)
        },
        onToggleCheck = { row -> state.checked = toggledChecks(row.item, currentAccess, state.checked) },
    )
    val look = TreeLook(indent, guides, checkable, toggleOnClick, surface, TreeSlots(leading, trailing))
    LazyColumn(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event -> treeKey(event, TreeKeyContext(rows, actions, listState, scope, rtl, checkable)) }
            .focusable(),
        state = listState,
    ) {
        itemsIndexed(rows, key = { _, row -> itemKeys.of(row.key) }) { index, row ->
            Box(Modifier.animateItem(fadeInSpec = rowFade(), placementSpec = rowPlacement(), fadeOutSpec = rowFade())) {
                TreeRowView(row, TreeRowStatus(index, checks[row.key], focused && state.active == row.key), label(row.item), look, actions)
            }
        }
    }
}

/** 행이 할 수 있는 일. 행과 키보드 처리가 함께 쓴다. */
internal class TreeActions<T>(val state: KTreeState, val onSelect: (TreeRow<T>) -> Unit, val onToggleCheck: (TreeRow<T>) -> Unit)

private class TreeLook<T>(
    val indent: Dp,
    val guides: Boolean,
    val checkable: Boolean,
    val toggleOnClick: Boolean,
    val surface: SurfaceStyle,
    val slots: TreeSlots<T>,
)

private class TreeSlots<T>(val leading: (@Composable (T, Boolean) -> Unit)?, val trailing: (@Composable (T) -> Unit)?)

private class TreeRowStatus(val index: Int, val check: ToggleableState?, val keyboardActive: Boolean)

@Composable
private fun <T> TreeRowView(row: TreeRow<T>, status: TreeRowStatus, text: String, look: TreeLook<T>, actions: TreeActions<T>) {
    val state = actions.state
    val open = row.hasChildren && state.isExpanded(row.key)
    val selected = state.selected == row.key
    val accent = KineticaTheme.colors.accent
    val ink = look.surface.contentColor
    val shape = shapeFor { KShapeToken.Small }
    val description = treeRowDescription(row, open)
    val selectLabel = stringResource(R.string.kinetica_ui_tree_select)
    val choose = {
        actions.onSelect(row)
        if (row.hasChildren && look.toggleOnClick) state.toggle(row.key)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .treeGuides(row.depth, look.indent, if (look.guides) ink.copy(alpha = GUIDE_ALPHA) else Color.Transparent)
            // 키보드 초점은 목록 하나가 받고 행 사이는 방향키로 옮기므로 행은 Tab 순서에 넣지 않는다.
            .focusProperties { canFocus = false }
            // 같은 노드에서는 바깥 수정자의 클릭 동작이 남으므로 체크 동작을 고르기보다 먼저 둔다.
            .then(
                if (look.checkable) {
                    Modifier.checkSemantics(status.check, selectLabel, choose) {
                        actions.onToggleCheck(row)
                    }
                } else {
                    Modifier
                },
            )
            .kClickable(shape, onClickLabel = selectLabel, role = null, onClick = choose)
            .treeRowSemantics(row, status, selected, description, state)
            .treeHighlight(selected, status.keyboardActive, accent, shape)
            .padding(end = ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(look.indent * row.depth))
        Chevron(row.hasChildren, open, ink, if (look.toggleOnClick) null else ({ state.toggle(row.key) }))
        if (look.checkable) {
            KTriStateCheckbox(
                status.check ?: ToggleableState.Off,
                onClick = { actions.onToggleCheck(row) },
                // 체크 상태와 바꾸기는 행이 읽으므로 체크박스는 따로 읽지 않는다.
                modifier = Modifier.focusProperties { canFocus = false }.clearAndSetSemantics {},
                surface = look.surface,
            )
        }
        look.slots.leading?.let { icon ->
            Box(Modifier.padding(end = ICON_GAP)) { icon(row.item, open) }
        }
        ThemedText(
            text,
            Modifier.weight(1f).padding(vertical = TEXT_PADDING),
            style = TextStyle(fontSize = 15.sp, color = ink, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        look.slots.trailing?.invoke(row.item)
    }
}

/** 행 하나를 TalkBack에 묶어 읽게 하고, 자식이 있으면 펼침 상태에 맞는 펼치기나 접기 동작을 단다. */
private fun Modifier.treeRowSemantics(row: TreeRow<*>, status: TreeRowStatus, selected: Boolean, description: String, state: KTreeState) =
    semantics(mergeDescendants = true) {
        this.selected = selected
        stateDescription = description
        collectionItemInfo = CollectionItemInfo(status.index, 1, 0, 1)
        if (!row.hasChildren) return@semantics
        if (state.isExpanded(row.key)) {
            collapse {
                state.collapse(row.key)
                true
            }
        } else {
            expand {
                state.expand(row.key)
                true
            }
        }
    }

/**
 * 체크할 수 있는 행은 TalkBack에서 체크 상태를 읽고 두 번 탭하면 체크를 바꾼다. 고르기는 사용자 정의 동작으로 옮긴다.
 * 행을 누르는 손가락 동작은 그대로 고르기다.
 */
private fun Modifier.checkSemantics(check: ToggleableState?, selectLabel: String, onSelect: () -> Unit, onToggle: () -> Unit) = semantics {
    toggleableState = check ?: ToggleableState.Off
    onClick {
        onToggle()
        true
    }
    customActions = listOf(
        CustomAccessibilityAction(selectLabel) {
            onSelect()
            true
        },
    )
}

@Composable
private fun treeRowDescription(row: TreeRow<*>, open: Boolean): String {
    val position = stringResource(R.string.kinetica_ui_tree_position, row.depth + 1, row.position, row.siblings)
    if (!row.hasChildren) return position
    val expansion = stringResource(if (open) R.string.kinetica_ui_tree_expanded else R.string.kinetica_ui_tree_collapsed)
    return "$expansion, $position"
}

/** 펼침 화살표. 접히면 끝 쪽을, 펼치면 아래를 가리킨다. 아이콘이 RTL에서 뒤집히므로 그때는 반대로 돈다. */
@Composable
private fun Chevron(visible: Boolean, open: Boolean, color: Color, onToggle: (() -> Unit)?) {
    val still = reducedMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val turn by animateFloatAsState(if (open) QUARTER_TURN else 0f, tween(if (still) 0 else CHEVRON_MS), label = "tree-chevron")
    val tap = if (visible && onToggle != null) Modifier.kClickable(CircleShape, role = null, onClick = onToggle) else Modifier
    // 펼치기와 접기는 행의 TalkBack 동작으로 이미 있으므로 화살표는 따로 읽지 않는다.
    Box(
        Modifier.size(CHEVRON_BOX).focusProperties {
            canFocus = false
        }.then(tap).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            Glyph(
                KIcons.ChevronForward,
                color.copy(alpha = CHEVRON_ALPHA),
                Modifier.graphicsLayer { rotationZ = if (rtl) -turn else turn },
                size = CHEVRON_SIZE,
            )
        }
    }
}

/** 수준마다 들여쓰기 칸 가운데에 세로선을 긋는다. */
private fun Modifier.treeGuides(depth: Int, indent: Dp, color: Color): Modifier {
    if (depth == 0 || color.alpha == 0f) return this
    return drawBehind {
        val step = indent.toPx()
        val width = GUIDE_WIDTH.toPx()
        // 안내선은 부모 화살표 가운데 아래로 내려온다.
        val first = CHEVRON_BOX.toPx() / 2
        for (level in 0 until depth) {
            val x = first + step * level
            val drawX = if (layoutDirection == LayoutDirection.Rtl) size.width - x else x
            drawLine(color, Offset(drawX, 0f), Offset(drawX, size.height), width)
        }
    }
}

/** 고른 행은 옅은 강조색으로 칠하고, 키보드 초점이 있는 행은 강조색 테두리를 두른다. 모양은 크기가 바뀔 때만 다시 만든다. */
private fun Modifier.treeHighlight(selected: Boolean, active: Boolean, accent: Color, shape: Shape): Modifier {
    if (!selected && !active) return this
    return drawWithCache {
        val w = RING_WIDTH.toPx()
        val fill = shape.createOutline(size, layoutDirection, this)
        val ring = shape.createOutline(Size(size.width - w, size.height - w), layoutDirection, this)
        val stroke = Stroke(w)
        onDrawBehind {
            if (selected) drawOutline(fill, accent.copy(alpha = SELECTED_ALPHA))
            if (active) translate(w / 2, w / 2) { drawOutline(ring, accent, style = stroke) }
        }
    }
}

@Composable
private fun rowFade() = if (reducedMotion()) null else tween<Float>(ROW_FADE_MS)

@Composable
private fun rowPlacement() = if (reducedMotion()) null else tween<IntOffset>(ROW_MOVE_MS)

private val ROW_HEIGHT = 44.dp
private val ROW_PADDING = 12.dp
private val TEXT_PADDING = 8.dp
private val ICON_GAP = 8.dp
private val CHEVRON_BOX = 32.dp
private val CHEVRON_SIZE = 16.dp
private val GUIDE_WIDTH = 1.dp
private val RING_WIDTH = 1.5.dp
private const val GUIDE_ALPHA = 0.12f
private const val SELECTED_ALPHA = 0.12f
private const val CHEVRON_ALPHA = 0.6f
private const val QUARTER_TURN = 90f
private const val CHEVRON_MS = 180
private const val ROW_FADE_MS = 180
private const val ROW_MOVE_MS = 220
