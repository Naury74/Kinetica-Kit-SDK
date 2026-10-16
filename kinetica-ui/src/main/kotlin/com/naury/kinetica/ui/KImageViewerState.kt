package com.naury.kinetica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/**
 * [KImageViewer]가 열려 있는지와 지금 보는 사진 번호. [Modifier.imageViewerSource]로 썸네일 위치를 등록하면
 * 그 썸네일에서 커지며 열리고, 닫을 때는 지금 보는 사진의 썸네일로 줄어든다.
 */
@Stable
public class KImageViewerState internal constructor(openAt: Int?, restored: Boolean) {
    internal val sources = mutableStateMapOf<Int, Rect>()
    internal var hostOrigin by mutableStateOf(Offset.Zero)
    internal var restoredOpen = restored
    internal var openCount by mutableIntStateOf(0)

    /** 열려 있거나 열리는 중이면 `true`. 닫히는 애니메이션 중에는 이미 `false`다. */
    public var isOpen: Boolean by mutableStateOf(openAt != null)
        private set

    /** 뷰어가 화면에 그려지는 중이면 `true`. 닫힘 애니메이션이 끝나야 `false`가 된다. */
    public var isVisible: Boolean by mutableStateOf(openAt != null)
        internal set

    /** 지금 보는 사진 번호(0부터). 뷰어에서 넘기면 바뀐다. */
    public var page: Int by mutableIntStateOf(openAt ?: 0)
        internal set

    /** [page]번 사진으로 뷰어를 연다. 이미 열려 있으면 그 사진으로 넘긴다. [page]가 음수이면 [IllegalArgumentException]을 던진다. */
    public fun open(page: Int) {
        require(page >= 0) { "page는 0 이상이어야 합니다." }
        this.page = page
        isOpen = true
        isVisible = true
        openCount++
    }

    /** 뷰어를 닫는다. 지금 사진이 썸네일 위치(없으면 제자리)로 줄어들며 사라진다. */
    public fun close() {
        isOpen = false
    }
}

/** 화면 회전이나 프로세스 재시작 뒤에도 열린 상태와 사진 번호를 기억하는 [KImageViewerState]를 만든다. */
@Composable
public fun rememberKImageViewerState(): KImageViewerState =
    rememberSaveable(saver = ViewerSaver) { KImageViewerState(openAt = null, restored = false) }

internal val ViewerSaver = Saver<KImageViewerState, Int>(
    save = { if (it.isOpen) it.page else CLOSED },
    restore = { KImageViewerState(openAt = it.takeIf { page -> page != CLOSED }, restored = true) },
)

/**
 * 이 요소를 [index]번 사진의 썸네일로 등록한다. 뷰어가 이 사진을 보여 주는 동안 썸네일은 숨긴다.
 * 썸네일이 `ContentScale.Crop`으로 그려져 있으면 열고 닫는 전환이 자연스럽게 이어진다.
 */
public fun Modifier.imageViewerSource(state: KImageViewerState, index: Int): Modifier =
    this then ViewerSourceElement(state, index) then Modifier.graphicsLayer {
        alpha = if (state.isVisible && state.page == index) 0f else 1f
    }

private data class ViewerSourceElement(val state: KImageViewerState, val index: Int) : ModifierNodeElement<ViewerSourceNode>() {
    override fun create(): ViewerSourceNode = ViewerSourceNode(state, index)

    override fun update(node: ViewerSourceNode) {
        if (node.index != index || node.state !== state) node.forget()
        node.state = state
        node.index = index
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "imageViewerSource"
    }
}

private class ViewerSourceNode(var state: KImageViewerState, var index: Int) :
    Modifier.Node(),
    GlobalPositionAwareModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val bounds = coordinates.boundsOnScreen()
        if (state.sources[index] != bounds) state.sources[index] = bounds
    }

    override fun onDetach() = forget()

    fun forget() {
        state.sources.remove(index)
    }
}

private const val CLOSED = -1
