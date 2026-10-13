package com.naury.kinetica.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
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
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.SelectionFrame
import com.naury.kinetica.gestures.CanvasGestureController
import com.naury.kinetica.gestures.GestureConfig
import com.naury.kinetica.gestures.canvasGestures
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.render.SceneCanvas
import com.naury.kinetica.render.SceneImageProvider

/**
 * 문서를 보고 고치는 편집 화면. 캔버스(고르기·옮기기·크기·회전·두 손가락 이동·확대)와 도구 줄을 담는다.
 *
 * 저장소에 접근하지 않는다. 저장 시점·위치는 호스트가 정하며, [toolbarEnd]에 저장 버튼 등을 넣을 수 있다.
 * 화면이 멈추면(ON_PAUSE) 진행 중인 끌기를 되돌린다.
 *
 * @param images 이미지 노드를 그릴 이미지. 미리 읽어 둔 것만 돌려줘야 한다.
 */
@Composable
public fun KineticaEditor(
    session: KineticaSession,
    modifier: Modifier = Modifier,
    images: SceneImageProvider = SceneImageProvider.None,
    theme: StudioTheme = StudioTheme(),
    toolbarEnd: @Composable RowScope.() -> Unit = {},
) {
    val state = remember(session) { EditorState(session) }
    val density = LocalDensity.current.density
    val controller = remember(state, density) {
        CanvasGestureController(state, GestureConfig.forDensity(density)).also { it.onChange = { state.gestureTick++ } }
    }
    CancelGestureOnPause(controller)
    val paddingPx = with(LocalDensity.current) { CANVAS_PADDING.toPx().toDouble() }
    Column(modifier.background(theme.canvasBackground)) {
        EditorToolbar(state, theme, paddingPx, toolbarEnd)
        EditorCanvas(state, controller, images, theme, paddingPx, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun EditorCanvas(
    state: EditorState,
    controller: CanvasGestureController,
    images: SceneImageProvider,
    theme: StudioTheme,
    paddingPx: Double,
    modifier: Modifier,
) {
    val document by state.session.document.collectAsStateWithLifecycle()
    val selection by state.session.selection.collectAsStateWithLifecycle()
    val count = selection.selectedIds.size
    val description = pluralStringResource(R.plurals.kinetica_canvas_description, count, count)
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { state.onCanvasSize(it.width, it.height, paddingPx) }
            .canvasGestures(controller)
            .semantics { contentDescription = description }
            .testTag(CANVAS_TAG),
    ) {
        val viewport = state.viewport
        SceneCanvas(document, viewport, Modifier.fillMaxSize(), images, artboardOutside = theme.canvasBackground)
        val frame = remember(document, selection) { SelectionFrame.of(SceneGraph(document), selection.selectedIds) }
        // gestureTick을 읽어 사각 선택 영역이 바뀔 때마다 표시를 다시 그린다.
        val marquee = state.gestureTick.let { controller.marquee }
        SelectionOverlay(frame, showHandles = selection.selectedIds.size == 1, marquee, viewport, theme, Modifier.fillMaxSize())
    }
}

@Composable
private fun EditorToolbar(state: EditorState, theme: StudioTheme, paddingPx: Double, toolbarEnd: @Composable RowScope.() -> Unit) {
    val history by state.session.history.state.collectAsStateWithLifecycle()
    val selection by state.session.selection.collectAsStateWithLifecycle()
    val defaultText = stringResource(R.string.kinetica_default_text)
    val colors = ButtonDefaults.textButtonColors(
        contentColor = theme.onPanel,
        disabledContentColor = theme.onPanel.copy(alpha = DISABLED_ALPHA),
    )
    fun add(node: SceneNode) {
        if (state.session.commands.execute(SceneCommand.AddNode(node)) is CommandResult.Applied) {
            state.select(listOf(node.id))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(theme.panel)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton(R.string.kinetica_add_rectangle, colors) { add(NodeFactory.rectangle(state.visibleCenter(), state.nextSequence())) }
        ToolButton(R.string.kinetica_add_ellipse, colors) { add(NodeFactory.ellipse(state.visibleCenter(), state.nextSequence())) }
        ToolButton(R.string.kinetica_add_text, colors) { add(NodeFactory.text(state.visibleCenter(), defaultText)) }
        ToolButton(R.string.kinetica_delete, colors, enabled = !selection.isEmpty) {
            state.session.commands.execute(SceneCommand.RemoveNodes(selection.selectedIds.toSet()))
        }
        ToolButton(R.string.kinetica_undo, colors, enabled = history.canUndo) { state.session.history.undo() }
        ToolButton(R.string.kinetica_redo, colors, enabled = history.canRedo) { state.session.history.redo() }
        ToolButton(R.string.kinetica_fit, colors) { state.fit(paddingPx) }
        toolbarEnd()
    }
}

@Composable
private fun ToolButton(label: Int, colors: ButtonColors, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, colors = colors, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
        Text(stringResource(label))
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
private const val DISABLED_ALPHA = 0.38f
