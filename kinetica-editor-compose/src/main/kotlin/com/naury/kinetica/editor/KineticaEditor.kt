package com.naury.kinetica.editor

import androidx.compose.foundation.LocalIndication
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naury.kinetica.core.scene.AlignMode
import com.naury.kinetica.core.scene.Bindings
import com.naury.kinetica.core.scene.DistributeAxis
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.scene.SelectionFrame
import com.naury.kinetica.gestures.CanvasGestureController
import com.naury.kinetica.gestures.GestureConfig
import com.naury.kinetica.gestures.canvasGestures
import com.naury.kinetica.guides.SnapConfig
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.render.LocalSceneFonts
import com.naury.kinetica.render.SceneImageProvider
import com.naury.kinetica.render.SceneLayout
import com.naury.kinetica.render.SceneSlots
import com.naury.kinetica.render.SceneTextMeasurer
import com.naury.kinetica.render.rememberSceneImages

/**
 * 장면 문서를 보고 편집하는 화면. 사용법은 docs/32-studio.md에 있다.
 *
 * - 상단 툴바: 추가(도형, 텍스트, 프레임), 실행 취소와 다시 실행, 보기(화면에 맞추기, 모션) 버튼을 캡슐로 묶어 한 줄에 둔다.
 *   폭이 모자라면 가로로 스크롤되고, [onExit]이 있으면 나가기 버튼이 스크롤되지 않고 끝에 고정된다.
 * - 선택 툴바: 무언가를 선택하면 나타난다. 삭제, 그룹, 순서, 정렬.
 * - 캔버스: 선택, 이동, 크기 조절, 회전, 두 손가락 이동과 확대, 가이드선 스냅.
 * - 레이어·속성 패널: 너비가 600dp 이상이면 오른쪽에 붙고, 그보다 좁으면 바텀 시트로 연다.
 * - 키보드(캔버스에 포커스가 있을 때): 방향키로 1씩(Shift와 함께 10씩) 이동, Delete 삭제, Ctrl+Z와 Ctrl+Shift+Z로 실행 취소와 다시 실행, Esc 선택 해제.
 *
 * 저장소에는 접근하지 않는다. 화면이 ON_PAUSE가 되면 진행 중인 드래그를 되돌린다.
 *
 * @param images 이미지 노드를 그릴 이미지. `null`이면 [rememberSceneImages]로 `content://` 이미지를 직접 읽는다.
 * @param config 스냅·격자·회전 스냅 설정
 * @param slots 호스트 뷰 노드에 그릴 Composable(키 → 내용). 편집 중에는 터치를 받지 않고 위치와 크기만 바뀐다.
 * @param hostViews "호스트 뷰" 메뉴에 보일 항목. 비어 있으면 메뉴를 숨긴다.
 * @param data 바인딩에 쓸 호스트 데이터. 값이 바뀌면 화면에 바로 반영되고 문서는 바뀌지 않는다.
 * @param onExit `null`이 아니면 도구 모음 끝에 나가기 버튼을 고정해 두고, 누르면 부른다.
 * @param toolbarEnd 기본 버튼 뒤에 이어 붙일 호스트 버튼. 스크롤되는 줄 안에 들어간다. [StudioToolbarButton]을 쓰면 모양이 같다.
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
    onExit: (() -> Unit)? = null,
    toolbarEnd: @Composable RowScope.() -> Unit = {},
) {
    // 입력란·칩·시트가 호스트 MaterialTheme이 아니라 편집기 색을 따르게 한다.
    // MaterialTheme은 LocalIndication을 리플로 되돌리므로, 호스트가 정한 눌림 표시(KineticaTheme의 KIndication 등)를 다시 넣어
    // toolbarEnd나 슬롯에 넣은 호스트 버튼이 편집기 밖과 같은 눌림 표시를 쓰게 한다.
    val hostIndication = LocalIndication.current
    MaterialTheme(colorScheme = remember(theme) { theme.colorScheme() }) {
        CompositionLocalProvider(LocalStudioTheme provides theme, LocalIndication provides hostIndication) {
            EditorContent(session, modifier, images, theme, config, slots, hostViews, data, onExit, toolbarEnd)
        }
    }
}

@Composable
@Suppress("LongParameterList", "LongMethod")
private fun EditorContent(
    session: KineticaSession,
    modifier: Modifier,
    images: SceneImageProvider?,
    theme: StudioTheme,
    config: StudioConfig,
    slots: SceneSlots,
    hostViews: List<HostViewOption>,
    data: SceneData,
    onExit: (() -> Unit)?,
    toolbarEnd: @Composable RowScope.() -> Unit,
) {
    val context = LocalContext.current
    val fonts = LocalSceneFonts.current
    // 호스트가 측정기를 주지 않으면 화면 폰트로 재는 측정기를 넣는다. 그래야 텍스트 자동 크기가 편집기에서도 동작한다.
    LaunchedEffect(session, fonts) { if (session.textMeasurer == null) session.textMeasurer = SceneTextMeasurer(context, fonts) }
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
        // 폴더블을 펴서 패널이 오른쪽에 붙으면 시트는 닫는다. 다시 접었을 때 예전 시트가 저절로 뜨지 않게 한다.
        LaunchedEffect(docked) { if (docked) sheet = null }
        Column(Modifier.fillMaxSize()) {
            EditorToolbar(state, theme, paddingPx, ToolbarSlots(!docked, hostViews, { sheet = it }, onExit, toolbarEnd))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                // 선택 툴바를 캔버스 위에 띄운다. 레이아웃에 넣으면 선택할 때마다 캔버스 크기가 바뀌어 장면이 밀린다.
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
    // 높이를 둘로 나눠, 아래에 timeline이 열려 패널이 낮아져도 레이어 목록이 사라지지 않게 한다. 두 영역은 각각 스크롤된다.
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
    // 모션 편집 중에는 재생 위치에서 화면에 보이는 값을 보여 준다. 고친 값도 그 시각의 keyframe이 된다.
    val document = remember(base, state.motionEnabled, state.playheadMs) { state.displayed(base) }
    val inspectorEdits = remember(state, edits) {
        InspectorEdits(edits, state)
    }
    InspectorPanel(
        selection.selectedIds.singleOrNull()?.let(document::node),
        document,
        inspectorEdits,
        theme,
        Modifier.fillMaxWidth(),
        onInsert = { main -> inspectorEdits.component.insertInstance(main.id, state.visibleCenter()) },
    )
}

@Composable
private fun PanelBottomSheet(sheet: PanelSheet, state: EditorState, edits: PropertyEdits, theme: StudioTheme, onDismiss: () -> Unit) {
    StudioSheet(onDismissRequest = onDismiss) {
        when (sheet) {
            PanelSheet.Layers -> {
                PanelTitle(R.string.kinetica_layers, theme)
                LayersFor(state, edits, theme, Modifier.heightIn(max = SHEET_MAX_HEIGHT))
            }

            PanelSheet.Properties -> {
                PanelTitle(R.string.kinetica_properties, theme)
                Box(Modifier.weight(1f, fill = false)) { PropertiesFor(state, edits, theme) }
            }
        }
    }
}

@Composable
private fun PanelTitle(label: Int, theme: StudioTheme) {
    Text(
        stringResource(label),
        color = theme.onPanel,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

// 캔버스가 그릴 때 쓰는 호스트 자원.
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
        remember(base, state.motionEnabled, state.playheadMs, sources.data) {
            Bindings.apply(state.displayed(Bindings.resolveVariants(base, sources.data)), sources.data)
        }
    val count = selection.selectedIds.size
    val description = pluralStringResource(R.plurals.kinetica_canvas_description, count, count)
    val focus = remember { FocusRequester() }
    Box(modifier.clipToBounds().background(theme.canvasBackground).onSizeChanged { state.onCanvasSize(it.width, it.height, paddingPx) }) {
        val viewport = state.viewport
        SceneLayout(document, viewport, Modifier.fillMaxSize(), sources.images, sources.slots, interactive = false)
        val frame = remember(document, selection) { SelectionFrame.of(SceneGraph(document), selection.selectedIds) }
        // gestureTick을 읽어 드래그 선택 영역이 바뀔 때마다 오버레이를 다시 그린다.
        val marquee = state.gestureTick.let { controller.marquee }
        SelectionOverlay(frame, showHandles = count >= 1, marquee, state.guides, viewport, theme, Modifier.fillMaxSize())
        // 맨 위 투명 레이어가 포인터를 모두 가져가므로, 편집 중에는 호스트 뷰가 눌리지 않고 위치와 크기만 바뀐다.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(focus) {
                    awaitPointerEventScope { while (true) if (awaitPointerEvent().changes.any { it.pressed }) focus.requestFocus() }
                }
                .canvasGestures(controller)
                .focusRequester(focus)
                .editorShortcuts(state, actions)
                .focusable()
                .semantics { contentDescription = description }
                .testTag(CANVAS_TAG),
        )
    }
}

@Composable
private fun SelectionBar(state: EditorState, actions: EditorActions, theme: StudioTheme, modifier: Modifier) {
    val selection by state.session.selection.collectAsStateWithLifecycle()
    // 문서가 바뀌면 그룹 해제를 할 수 있는지도 달라지므로 다시 그린다.
    val document by state.session.document.collectAsStateWithLifecycle()
    val count = selection.selectedIds.size
    if (count == 0) return
    ToolRow(theme, alpha = SELECTION_BAR_ALPHA, modifier = modifier) {
        ToolButton(R.string.kinetica_delete, onClick = actions::delete)
        val editableText = selection.selectedIds.singleOrNull()?.takeIf { document.node(it)?.content is NodeContent.Text }
        if (editableText != null) ToolButton(R.string.kinetica_edit_text) { state.editingText = editableText }
        ToolButton(R.string.kinetica_auto_layout, onClick = actions::wrapInAutoLayout)
        val linked =
            selection.selectedIds.singleOrNull()?.let(document::node)?.let { it.component != null || it.instanceOf != null } == true
        if (!linked) ToolButton(R.string.kinetica_make_component) { ComponentEdits(state).makeComponent(selection.selectedIds) }
        ToolButton(R.string.kinetica_group, enabled = count >= 2, onClick = actions::group)
        ToolButton(R.string.kinetica_ungroup, enabled = document.let { actions.canUngroup }, onClick = actions::ungroup)
        ToolButton(R.string.kinetica_bring_forward, onClick = actions::bringForward)
        ToolButton(R.string.kinetica_send_backward, onClick = actions::sendBackward)
        ArrangeMenu(actions)
    }
}

@Composable
private fun ToolRow(theme: StudioTheme, modifier: Modifier = Modifier, alpha: Float = 1f, content: @Composable RowScope.() -> Unit) {
    val scroll = rememberScrollState()
    Row(
        modifier
            .fillMaxWidth()
            .background(theme.panel.copy(alpha = alpha))
            .fadingEdges(scroll)
            .horizontalScroll(scroll)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ArrangeMenu(actions: EditorActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolButton(R.string.kinetica_arrange) { expanded = true }
        StudioMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                StudioMenuItem(
                    text = stringResource(label),
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
private fun ToolButton(label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    StudioTextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = TOUCH_TARGET)) {
        Text(stringResource(label), color = LocalStudioTheme.current.onPanel)
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

/** 테스트나 호스트가 캔버스를 찾을 때 쓰는 test tag. */
public const val CANVAS_TAG: String = "kinetica-canvas"
private val CANVAS_PADDING = 24.dp
private val DOCKED_PANEL_MIN_WIDTH = 600.dp
private val PANEL_WIDTH = 300.dp
private val SHEET_MAX_HEIGHT = 480.dp
private val TIMELINE_MAX_HEIGHT = 340.dp
private const val SELECTION_BAR_ALPHA = 0.92f
