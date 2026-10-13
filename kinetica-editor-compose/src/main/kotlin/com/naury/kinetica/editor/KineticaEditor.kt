package com.naury.kinetica.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.AlignMode
import com.naury.kinetica.core.scene.Bindings
import com.naury.kinetica.core.scene.DistributeAxis
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.SelectionFrame
import com.naury.kinetica.gestures.CanvasGestureController
import com.naury.kinetica.gestures.GestureConfig
import com.naury.kinetica.gestures.canvasGestures
import com.naury.kinetica.guides.SnapConfig
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.render.SceneImageProvider
import com.naury.kinetica.render.SceneLayout
import com.naury.kinetica.render.SceneSlots
import com.naury.kinetica.render.rememberSceneImages

/**
 * 문서를 보고 고치는 편집 화면(docs 10).
 *
 * - 위 도구 줄: 도형·글자 추가, 실행 취소·다시 실행, 화면 맞춤, 호스트 슬롯([toolbarEnd]).
 * - 선택 줄: 무언가를 고르면 나타난다. 삭제, 그룹, 순서, 정렬.
 * - 캔버스: 고르기·옮기기·크기·회전·두 손가락 이동·확대, 안내선 스냅.
 * - 레이어·속성 패널: 너비 600dp 이상이면 오른쪽에 붙고, 그보다 좁으면 아래 시트로 연다.
 * - 키보드(캔버스에 포커스): 화살표 1(Shift 10) 이동, Delete 삭제, Ctrl+Z·Ctrl+Shift+Z 실행 취소·다시 실행, Esc 선택 해제.
 *
 * 저장소에 접근하지 않는다. 화면이 멈추면(ON_PAUSE) 진행 중인 끌기를 되돌린다.
 *
 * @param images 이미지 노드를 그릴 이미지. `null`이면 [rememberSceneImages]로 `content://` 이미지를 직접 읽는다.
 * @param config 스냅·격자·회전 스냅 설정
 * @param slots 호스트 뷰 노드에 그릴 Composable(키 → 내용). 편집 중에는 눌리지 않고 배치만 바뀐다.
 * @param hostViews "호스트 뷰" 메뉴에 보일 항목. 비어 있으면 메뉴를 숨긴다.
 * @param data 바인딩에 쓸 호스트 데이터. 바뀌면 화면에 바로 반영된다(문서는 바뀌지 않음).
 */
@Composable
public fun KineticaEditor(
    session: KineticaSession,
    modifier: Modifier = Modifier,
    images: SceneImageProvider? = null,
    theme: StudioTheme = StudioTheme(),
    config: StudioConfig = StudioConfig(),
    slots: SceneSlots = emptyMap(),
    hostViews: List<HostViewOption> = emptyList(),
    data: SceneData = SceneData.Empty,
    toolbarEnd: @Composable RowScope.() -> Unit = {},
) {
    val state = remember(session) { EditorState(session) }
    val actions = remember(state) { EditorActions(state) }
    val edits = remember(state) { PropertyEdits(state) }
    val density = LocalDensity.current.density
    val controller = remember(state, density, config) {
        val snapping = if (config.snapping) GuideSnapping(state, SnapConfig.forDensity(density, config.gridSize)) else null
        val gestureConfig = GestureConfig.forDensity(density).copy(rotationSnapDegrees = config.rotationSnapDegrees)
        CanvasGestureController(state, gestureConfig, snapping).also { it.onChange = { state.gestureTick++ } }
    }
    controller.onDoubleTap = { id -> if (state.document.node(id)?.content is NodeContent.Text) state.editingText = id }
    CancelGestureOnPause(controller)
    val paddingPx = with(LocalDensity.current) { CANVAS_PADDING.toPx().toDouble() }
    val document by session.document.collectAsStateWithLifecycle()
    val sceneImages = images ?: rememberSceneImages(document)
    BoxWithConstraints(modifier.background(theme.canvasBackground)) {
        val docked = maxWidth >= DOCKED_PANEL_MIN_WIDTH
        var sheet by remember { mutableStateOf<PanelSheet?>(null) }
        Column(Modifier.fillMaxSize()) {
            EditorToolbar(state, theme, paddingPx, !docked, hostViews, onOpenSheet = { sheet = it }, toolbarEnd = toolbarEnd)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                // 선택 줄은 캔버스 위에 띄워, 고를 때마다 캔버스 크기가 바뀌어 장면이 밀리지 않게 한다.
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    EditorCanvas(
                        state,
                        controller,
                        actions,
                        SceneSources(sceneImages, slots, data),
                        theme,
                        paddingPx,
                        Modifier.fillMaxSize(),
                    )
                    SelectionBar(state, actions, theme, Modifier.align(Alignment.BottomCenter))
                }
                if (docked) {
                    Panels(state, edits, theme, Modifier.width(PANEL_WIDTH).fillMaxHeight().background(theme.panel))
                }
            }
            if (state.motionEnabled) TimelinePanel(state, theme, Modifier.fillMaxWidth().heightIn(max = TIMELINE_MAX_HEIGHT))
        }
        if (!docked) sheet?.let { PanelBottomSheet(it, state, edits, theme) { sheet = null } }
        state.editingText?.let { id -> TextEditDialog(state, edits, id) { state.editingText = null } }
    }
}

internal enum class PanelSheet { Layers, Properties }

@Composable
private fun Panels(state: EditorState, edits: PropertyEdits, theme: StudioTheme, modifier: Modifier = Modifier) {
    // 높이를 둘로 나눠, 아래에 timeline이 열려 패널이 낮아져도 레이어 목록이 사라지지 않게 한다. 두 칸 모두 안에서 스크롤된다.
    Column(modifier) {
        PanelTitle(R.string.kinetica_layers, theme)
        LayersFor(state, edits, theme, Modifier.weight(1f))
        HorizontalDivider(color = theme.canvasBackground)
        PanelTitle(R.string.kinetica_properties, theme)
        Box(Modifier.weight(1f)) { PropertiesFor(state, edits, theme) }
    }
}

@Composable
private fun LayersFor(state: EditorState, edits: PropertyEdits, theme: StudioTheme, modifier: Modifier = Modifier) {
    val document by state.session.document.collectAsStateWithLifecycle()
    val selection by state.session.selection.collectAsStateWithLifecycle()
    val rows = remember(document) { layerRows(document) }
    LayerPanel(
        rows = rows,
        selected = selection.selectedIds,
        theme = theme,
        onSelect = { state.select(listOf(it)) },
        onToggleVisible = { edits.toggleVisible(it) },
        onToggleLocked = { edits.toggleLocked(it) },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun PropertiesFor(state: EditorState, edits: PropertyEdits, theme: StudioTheme) {
    val base by state.session.document.collectAsStateWithLifecycle()
    val selection by state.session.selection.collectAsStateWithLifecycle()
    // 모션 편집 중에는 화면에 보이는(재생 위치의) 값을 보여 준다. 고친 값도 그 기준으로 키가 된다.
    val document = remember(base, state.motionEnabled, state.playheadMs) { state.displayed(base) }
    val layoutEdits = remember(state) { LayoutEdits(state) }
    InspectorPanel(selection.selectedIds.singleOrNull()?.let(document::node), edits, layoutEdits, theme, Modifier.fillMaxWidth())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelBottomSheet(sheet: PanelSheet, state: EditorState, edits: PropertyEdits, theme: StudioTheme, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = theme.panel) {
        when (sheet) {
            PanelSheet.Layers -> {
                PanelTitle(R.string.kinetica_layers, theme)
                LayersFor(state, edits, theme, Modifier.heightIn(max = SHEET_MAX_HEIGHT))
            }

            PanelSheet.Properties -> {
                PanelTitle(R.string.kinetica_properties, theme)
                PropertiesFor(state, edits, theme)
            }
        }
    }
}

@Composable
private fun PanelTitle(label: Int, theme: StudioTheme) {
    Text(stringResource(label), color = theme.onPanel, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
}

/** 캔버스가 그릴 때 쓰는 호스트 자원. */
internal class SceneSources(val images: SceneImageProvider, val slots: SceneSlots, val data: SceneData)

@Composable
private fun EditorCanvas(
    state: EditorState,
    controller: CanvasGestureController,
    actions: EditorActions,
    sources: SceneSources,
    theme: StudioTheme,
    paddingPx: Double,
    modifier: Modifier,
) {
    val base by state.session.document.collectAsStateWithLifecycle()
    val selection by state.session.selection.collectAsStateWithLifecycle()
    val document =
        remember(base, state.motionEnabled, state.playheadMs, sources.data) { Bindings.apply(state.displayed(base), sources.data) }
    val count = selection.selectedIds.size
    val description = pluralStringResource(R.plurals.kinetica_canvas_description, count, count)
    val focus = remember { FocusRequester() }
    Box(modifier.clipToBounds().background(theme.canvasBackground).onSizeChanged { state.onCanvasSize(it.width, it.height, paddingPx) }) {
        val viewport = state.viewport
        SceneLayout(document, viewport, Modifier.fillMaxSize(), sources.images, sources.slots, interactive = false)
        val frame = remember(document, selection) { SelectionFrame.of(SceneGraph(document), selection.selectedIds) }
        // gestureTick을 읽어 사각 선택 영역이 바뀔 때마다 표시를 다시 그린다.
        val marquee = state.gestureTick.let { controller.marquee }
        SelectionOverlay(frame, showHandles = count == 1, marquee, state.guides, viewport, theme, Modifier.fillMaxSize())
        // 맨 위 투명 층이 포인터를 모두 가져가, 편집 중에는 호스트 뷰가 눌리지 않고 배치만 바뀐다.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(focus) {
                    awaitPointerEventScope { while (true) if (awaitPointerEvent().changes.any { it.pressed }) focus.requestFocus() }
                }
                .canvasGestures(controller)
                .focusRequester(focus)
                .editorShortcuts(state.session, actions)
                .focusable()
                .semantics { contentDescription = description }
                .testTag(CANVAS_TAG),
        )
    }
}

@Composable
private fun EditorToolbar(
    state: EditorState,
    theme: StudioTheme,
    paddingPx: Double,
    showPanelButtons: Boolean,
    hostViews: List<HostViewOption>,
    onOpenSheet: (PanelSheet) -> Unit,
    toolbarEnd: @Composable RowScope.() -> Unit,
) {
    val history by state.session.history.state.collectAsStateWithLifecycle()
    val selection by state.session.selection.collectAsStateWithLifecycle()
    val defaultText = stringResource(R.string.kinetica_default_text)
    val colors = toolColors(theme)
    fun add(node: SceneNode) {
        if (state.session.commands.execute(SceneCommand.AddNode(node)) is CommandResult.Applied) state.select(listOf(node.id))
    }
    ToolRow(theme) {
        ToolButton(R.string.kinetica_add_rectangle, colors) { add(NodeFactory.rectangle(state.visibleCenter(), state.nextSequence())) }
        ToolButton(R.string.kinetica_add_ellipse, colors) { add(NodeFactory.ellipse(state.visibleCenter(), state.nextSequence())) }
        ToolButton(R.string.kinetica_add_text, colors) { add(NodeFactory.text(state.visibleCenter(), defaultText)) }
        ToolButton(R.string.kinetica_add_frame, colors) { add(NodeFactory.frame(state.visibleCenter())) }
        if (hostViews.isNotEmpty()) HostViewMenu(hostViews, colors) { add(NodeFactory.hostView(state.visibleCenter(), it)) }
        ToolButton(R.string.kinetica_undo, colors, enabled = history.canUndo) { state.session.history.undo() }
        ToolButton(R.string.kinetica_redo, colors, enabled = history.canRedo) { state.session.history.redo() }
        ToolButton(R.string.kinetica_fit, colors) { state.fit(paddingPx) }
        ToolToggle(R.string.kinetica_motion, state.motionEnabled, colors, theme) { state.motionEnabled = !state.motionEnabled }
        if (showPanelButtons) {
            ToolButton(R.string.kinetica_layers, colors) { onOpenSheet(PanelSheet.Layers) }
            ToolButton(R.string.kinetica_properties, colors, enabled = selection.selectedIds.size == 1) {
                onOpenSheet(PanelSheet.Properties)
            }
        }
        toolbarEnd()
    }
}

@Composable
private fun SelectionBar(state: EditorState, actions: EditorActions, theme: StudioTheme, modifier: Modifier) {
    val selection by state.session.selection.collectAsStateWithLifecycle()
    // 문서가 바뀌면 그룹 해제 가능 여부도 바뀌므로 다시 그린다.
    val document by state.session.document.collectAsStateWithLifecycle()
    val count = selection.selectedIds.size
    if (count == 0) return
    val colors = toolColors(theme)
    ToolRow(theme, alpha = SELECTION_BAR_ALPHA, modifier = modifier) {
        ToolButton(R.string.kinetica_delete, colors, onClick = actions::delete)
        val editableText = selection.selectedIds.singleOrNull()?.takeIf { document.node(it)?.content is NodeContent.Text }
        if (editableText != null) ToolButton(R.string.kinetica_edit_text, colors) { state.editingText = editableText }
        ToolButton(R.string.kinetica_auto_layout, colors, onClick = actions::wrapInAutoLayout)
        ToolButton(R.string.kinetica_group, colors, enabled = count >= 2, onClick = actions::group)
        ToolButton(R.string.kinetica_ungroup, colors, enabled = document.let { actions.canUngroup }, onClick = actions::ungroup)
        ToolButton(R.string.kinetica_bring_forward, colors, onClick = actions::bringForward)
        ToolButton(R.string.kinetica_send_backward, colors, onClick = actions::sendBackward)
        ArrangeMenu(actions, colors)
    }
}

@Composable
private fun ToolRow(theme: StudioTheme, modifier: Modifier = Modifier, alpha: Float = 1f, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .background(theme.panel.copy(alpha = alpha))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun toolColors(theme: StudioTheme): ButtonColors =
    ButtonDefaults.textButtonColors(contentColor = theme.onPanel, disabledContentColor = theme.onPanel.copy(alpha = DISABLED_ALPHA))

@Composable
private fun HostViewMenu(options: List<HostViewOption>, colors: ButtonColors, onPick: (HostViewOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolButton(R.string.kinetica_add_host_view, colors) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onPick(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun ArrangeMenu(actions: EditorActions, colors: ButtonColors) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolButton(R.string.kinetica_arrange, colors) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val items = listOf(
                R.string.kinetica_align_left to { actions.align(AlignMode.Left) },
                R.string.kinetica_align_center_horizontal to { actions.align(AlignMode.CenterHorizontal) },
                R.string.kinetica_align_right to { actions.align(AlignMode.Right) },
                R.string.kinetica_align_top to { actions.align(AlignMode.Top) },
                R.string.kinetica_align_center_vertical to { actions.align(AlignMode.CenterVertical) },
                R.string.kinetica_align_bottom to { actions.align(AlignMode.Bottom) },
                R.string.kinetica_distribute_horizontal to { actions.distribute(DistributeAxis.Horizontal) },
                R.string.kinetica_distribute_vertical to { actions.distribute(DistributeAxis.Vertical) },
            )
            items.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolButton(label: Int, colors: ButtonColors, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, colors = colors, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
        Text(stringResource(label))
    }
}

@Composable
private fun ToolToggle(label: Int, checked: Boolean, colors: ButtonColors, theme: StudioTheme, onToggle: () -> Unit) {
    TextButton(
        onClick = onToggle,
        colors = colors,
        modifier = Modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .semantics { stateDescription = if (checked) "on" else "off" },
    ) {
        Text(stringResource(label), color = if (checked) theme.accent else theme.onPanel)
    }
}

@Composable
private fun CancelGestureOnPause(controller: CanvasGestureController) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) controller.onCancel() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/** 테스트와 호스트가 캔버스를 찾을 때 쓰는 tag. */
public const val CANVAS_TAG: String = "kinetica-canvas"
private val CANVAS_PADDING = 24.dp
private val MIN_TOUCH_TARGET = 48.dp
private val DOCKED_PANEL_MIN_WIDTH = 600.dp
private val PANEL_WIDTH = 300.dp
private val SHEET_MAX_HEIGHT = 480.dp
private val TIMELINE_MAX_HEIGHT = 340.dp
private const val DISABLED_ALPHA = 0.38f
private const val SELECTION_BAR_ALPHA = 0.92f
