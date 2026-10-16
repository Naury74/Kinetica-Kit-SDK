package com.naury.kinetica.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.SceneNode

/**
 * 편집기 도구 모음 모양의 버튼. [KineticaEditor]의 `toolbarEnd`에 넣으면 기본 버튼과 같은 캡슐 모양과 눌림 표시로 보인다.
 * 보이는 높이는 36dp, 터치 영역은 48dp이고, [icon]이 있으면 글자 앞에 18dp로 그린다. 색은 편집기의 [StudioTheme]을 따른다.
 *
 * @param label 화면에 보이는 이름이자 접근성 이름
 * @param enabled `false`면 흐리게 그리고 누를 수 없다
 */
@Composable
public fun StudioToolbarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    ToolItem(ToolSpec(label, icon, showLabel = true, enabled = enabled), onClick, modifier)
}

/**
 * 도구 모음 버튼을 하나의 옅은 캡슐 배경으로 묶는다. 관련된 버튼끼리 묶어 기본 그룹(추가, 실행 취소, 보기)과 구분할 때 쓴다.
 */
@Composable
public fun StudioToolbarGroup(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val fill = LocalStudioTheme.current.onPanel.copy(alpha = GROUP_FILL_ALPHA)
    Row(
        modifier
            .drawBehind {
                // 터치 영역(48dp)은 그대로 두고 배경만 위아래로 줄여 버튼 높이(40dp)에 맞춘다.
                val inset = GROUP_INSET.toPx()
                val height = size.height - inset * 2
                drawRoundRect(fill, Offset(0f, inset), Size(size.width, height), CornerRadius(height / 2))
            }
            .padding(horizontal = GROUP_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// 도구 모음 버튼 하나의 모양. showLabel이 false면 아이콘만 보이고 label은 접근성 이름으로만 쓴다.
internal data class ToolSpec(
    val label: String,
    val icon: ImageVector?,
    val showLabel: Boolean = true,
    val enabled: Boolean = true,
    val selected: Boolean = false,
)

@Composable
internal fun ToolItem(spec: ToolSpec, onClick: () -> Unit, modifier: Modifier = Modifier, toggle: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val action = if (toggle) {
        Modifier.toggleable(spec.selected, source, indication = null, enabled = spec.enabled, role = Role.Switch) { onClick() }
    } else {
        Modifier.clickable(source, indication = null, enabled = spec.enabled, role = Role.Button, onClick = onClick)
    }
    val iconOnly = !spec.showLabel && spec.icon != null
    Box(
        modifier
            .heightIn(min = TOUCH_TARGET)
            .then(if (iconOnly) Modifier.widthIn(min = ICON_TOUCH_WIDTH) else Modifier)
            .then(action)
            .then(if (iconOnly) Modifier.semantics { contentDescription = spec.label } else Modifier),
        contentAlignment = Alignment.Center,
    ) { ToolFace(spec, source, iconOnly) }
}

@Composable
private fun ToolFace(spec: ToolSpec, source: MutableInteractionSource, iconOnly: Boolean) {
    val theme = LocalStudioTheme.current
    val color = if (spec.selected) theme.accent else theme.onPanel
    val face = Modifier
        .heightIn(min = FACE_HEIGHT)
        .background(if (spec.selected) theme.accent.copy(alpha = SELECTED_FILL_ALPHA) else Color.Transparent, CapsuleShape)
        .indication(source, studioIndication(CapsuleShape))
        .alpha(if (spec.enabled) 1f else DISABLED_ALPHA)
    if (iconOnly) {
        Box(face.widthIn(min = FACE_HEIGHT), contentAlignment = Alignment.Center) {
            Icon(spec.icon!!, contentDescription = null, tint = color, modifier = Modifier.size(ICON_ONLY_SIZE))
        }
        return
    }
    Row(
        face.padding(start = if (spec.icon != null) LABEL_START_WITH_ICON else LABEL_END, end = LABEL_END),
        horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        spec.icon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(LABEL_ICON_SIZE)) }
        Text(spec.label, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 상단 도구 모음에 넣는 것. 편집기 화면에서 받은 값을 그대로 묶어 넘긴다. */
internal class ToolbarSlots(
    val showPanelButtons: Boolean,
    val hostViews: List<HostViewOption>,
    val onOpenSheet: (PanelSheet) -> Unit,
    val onExit: (() -> Unit)?,
    val toolbarEnd: @Composable RowScope.() -> Unit,
)

/**
 * 상단 도구 모음. 추가, 실행 취소, 보기 버튼을 캡슐로 묶어 한 줄에 둔다. 나가기 버튼은 스크롤되지 않고 끝에 고정된다.
 * 한 줄에 다 들어가지 않으면 먼저 메뉴 버튼과 모션 버튼의 이름을, 그다음 추가 버튼의 이름을 숨겨 아이콘만 남긴다.
 * 그래도 넘치는 좁은 화면에서는 이름을 모두 보이고 그 줄만 가로로 스크롤하며 양 끝을 흐리게 한다.
 */
@Composable
internal fun EditorToolbar(state: EditorState, theme: StudioTheme, paddingPx: Double, slots: ToolbarSlots) {
    val scroll = rememberScrollState()
    val divider = theme.onPanel.copy(alpha = DIVIDER_ALPHA)
    var level by remember(slots.showPanelButtons, slots.hostViews.isEmpty()) { mutableIntStateOf(LEVEL_FULL) }
    // [0]은 스크롤 줄의 보이는 폭, [1]은 그 안 내용의 원래 폭(px). 측정 순서에 기대지 않으려고 두 콜백이 각자 값을 남기고 비교한다.
    val widths = remember { intArrayOf(0, 0) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(theme.panel)
            .drawBehind { drawRect(divider, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) }
            .padding(horizontal = BAR_PADDING, vertical = BAR_VERTICAL_PADDING)
            .testTag(TOOLBAR_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .onSizeChanged {
                    // 창 폭이 바뀌면 이름을 모두 보이는 단계부터 다시 맞춰 본다. 처음 배치 때는 내용 콜백이 먼저 불려 값만 남겨 두었다.
                    if (it.width != widths[0]) {
                        if (widths[0] != 0) level = LEVEL_FULL
                        widths[0] = it.width
                        if (level == LEVEL_FULL && widths[1] > it.width) level = LEVEL_FULL + 1
                    }
                }
                .fadingEdges(scroll)
                .horizontalScroll(scroll)
                // 스크롤 안쪽이라 내용의 원래 폭을 받는다. 넘치면 한 단계씩 줄인다.
                .onSizeChanged {
                    widths[1] = it.width
                    if (widths[0] > 0 && it.width > widths[0] && level < LEVEL_SCROLL) level++
                },
            horizontalArrangement = Arrangement.spacedBy(GROUP_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AddGroup(state, slots.hostViews, level)
            HistoryGroup(state)
            ViewGroup(state, paddingPx, slots, menuLabeled = level == LEVEL_FULL || level == LEVEL_SCROLL)
            slots.toolbarEnd(this)
        }
        slots.onExit?.let { exit ->
            StudioToolbarGroup(Modifier.padding(start = GROUP_GAP)) {
                ToolItem(ToolSpec(stringResource(R.string.kinetica_exit), StudioIcons.Exit), exit)
            }
        }
    }
}

@Composable
private fun AddGroup(state: EditorState, hostViews: List<HostViewOption>, level: Int) {
    val defaultText = stringResource(R.string.kinetica_default_text)
    val labeled = level != LEVEL_ICONS
    val menuLabeled = level == LEVEL_FULL || level == LEVEL_SCROLL
    fun add(node: SceneNode) {
        if (state.session.commands.execute(SceneCommand.AddNode(node)) is CommandResult.Applied) state.select(listOf(node.id))
    }
    StudioToolbarGroup {
        ToolItem(ToolSpec(stringResource(R.string.kinetica_add_rectangle), StudioIcons.Rectangle, labeled), {
            add(NodeFactory.rectangle(state.visibleCenter(), state.nextSequence()))
        })
        ToolItem(ToolSpec(stringResource(R.string.kinetica_add_ellipse), StudioIcons.Ellipse, labeled), {
            add(NodeFactory.ellipse(state.visibleCenter(), state.nextSequence()))
        })
        ShapeMenu(menuLabeled) { make -> add(make(state.visibleCenter(), state.nextSequence())) }
        ToolItem(ToolSpec(stringResource(R.string.kinetica_add_text), StudioIcons.Text, labeled), {
            add(NodeFactory.text(state.visibleCenter(), defaultText))
        })
        ToolItem(ToolSpec(stringResource(R.string.kinetica_add_frame), StudioIcons.Frame, labeled), {
            add(NodeFactory.frame(state.visibleCenter()))
        })
        if (hostViews.isNotEmpty()) HostViewMenu(hostViews, menuLabeled) { add(NodeFactory.hostView(state.visibleCenter(), it)) }
    }
}

@Composable
private fun HistoryGroup(state: EditorState) {
    val history by state.session.history.state.collectAsStateWithLifecycle()
    StudioToolbarGroup {
        ToolItem(ToolSpec(stringResource(R.string.kinetica_undo), StudioIcons.Undo, showLabel = false, enabled = history.canUndo), {
            state.session.history.undo()
        })
        ToolItem(ToolSpec(stringResource(R.string.kinetica_redo), StudioIcons.Redo, showLabel = false, enabled = history.canRedo), {
            state.session.history.redo()
        })
    }
}

@Composable
private fun ViewGroup(state: EditorState, paddingPx: Double, slots: ToolbarSlots, menuLabeled: Boolean) {
    val selection by state.session.selection.collectAsStateWithLifecycle()
    StudioToolbarGroup {
        ToolItem(ToolSpec(stringResource(R.string.kinetica_fit), StudioIcons.Fit, showLabel = false), { state.fit(paddingPx) })
        ToolItem(
            ToolSpec(stringResource(R.string.kinetica_motion), StudioIcons.Motion, menuLabeled, selected = state.motionEnabled),
            { state.motionEnabled = !state.motionEnabled },
            toggle = true,
        )
        if (slots.showPanelButtons) {
            ToolItem(ToolSpec(stringResource(R.string.kinetica_layers), StudioIcons.Layers), { slots.onOpenSheet(PanelSheet.Layers) })
            ToolItem(
                ToolSpec(stringResource(R.string.kinetica_properties), StudioIcons.Properties, enabled = selection.selectedIds.size == 1),
                { slots.onOpenSheet(PanelSheet.Properties) },
            )
        }
    }
}

@Composable
private fun HostViewMenu(options: List<HostViewOption>, labeled: Boolean, onPick: (HostViewOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolItem(ToolSpec(stringResource(R.string.kinetica_add_host_view), StudioIcons.HostView, labeled), { expanded = true })
        StudioMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                StudioMenuItem(
                    text = option.label,
                    onClick = {
                        expanded = false
                        onPick(option)
                    },
                )
            }
        }
    }
}

// 다각형, 별, 선, 화살표 메뉴.
@Composable
private fun ShapeMenu(labeled: Boolean, onPick: (make: (Vec2, Int) -> SceneNode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val items = listOf<Pair<Int, (Vec2, Int) -> SceneNode>>(
        R.string.kinetica_add_polygon to NodeFactory::polygon,
        R.string.kinetica_add_star to NodeFactory::star,
        R.string.kinetica_add_line to { center, sequence -> NodeFactory.line(center, sequence, arrow = false) },
        R.string.kinetica_add_arrow to { center, sequence -> NodeFactory.line(center, sequence, arrow = true) },
    )
    Box {
        ToolItem(ToolSpec(stringResource(R.string.kinetica_more_shapes), StudioIcons.Star, labeled), { expanded = true })
        StudioMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (label, make) ->
                StudioMenuItem(
                    text = stringResource(label),
                    onClick = {
                        expanded = false
                        onPick(make)
                    },
                )
            }
        }
    }
}

/** 상단 도구 모음을 찾을 때 쓰는 test tag. */
internal const val TOOLBAR_TAG: String = "kinetica-toolbar"

// 도구 모음이 넘칠 때 줄이는 단계. 이름 모두 → 메뉴와 모션 버튼만 아이콘 → 추가 버튼도 모두 아이콘 → 이름을 모두 보이고 스크롤.
private const val LEVEL_FULL = 0
private const val LEVEL_ICONS = 2
private const val LEVEL_SCROLL = 3
private val FACE_HEIGHT = 36.dp
private val ICON_TOUCH_WIDTH = 40.dp
private val ICON_ONLY_SIZE = 20.dp
private val LABEL_ICON_SIZE = 18.dp
private val ICON_GAP = 6.dp
private val LABEL_START_WITH_ICON = 10.dp
private val LABEL_END = 12.dp
private val GROUP_INSET = 4.dp
private val GROUP_PADDING = 2.dp
private val GROUP_GAP = 8.dp
private val BAR_PADDING = 8.dp
private val BAR_VERTICAL_PADDING = 2.dp
private const val GROUP_FILL_ALPHA = 0.06f
private const val SELECTED_FILL_ALPHA = 0.18f
private const val DIVIDER_ALPHA = 0.08f
private const val DISABLED_ALPHA = 0.38f
