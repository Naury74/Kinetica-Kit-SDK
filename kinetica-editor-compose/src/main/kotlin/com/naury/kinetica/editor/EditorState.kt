package com.naury.kinetica.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.gestures.GestureHost
import com.naury.kinetica.history.KineticaSession

/**
 * 편집 화면의 보기 상태(viewport·캔버스 크기)와 세션을 잇는다. 보기 상태는 문서가 아니므로 실행 취소되지 않는다.
 */
internal class EditorState(val session: KineticaSession) : GestureHost {
    var canvasWidth by mutableIntStateOf(0)
        private set
    var canvasHeight by mutableIntStateOf(0)
        private set
    private var viewportState by mutableStateOf<Viewport?>(null)
    private var created = 0

    /** 상태 기계가 바뀔 때마다 늘어난다. 표시(사각 선택 등)를 다시 그리는 신호다. */
    var gestureTick by mutableIntStateOf(0)

    override val document: SceneDocument get() = session.document.value
    override val selection: List<NodeId> get() = session.selection.value.selectedIds
    override var viewport: Viewport
        get() = viewportState ?: Viewport()
        set(value) {
            viewportState = value
        }

    /** 캔버스 크기가 처음 정해지면 artboard가 꽉 차게 보이도록 맞춘다. */
    fun onCanvasSize(width: Int, height: Int, paddingPx: Double) {
        canvasWidth = width
        canvasHeight = height
        if (viewportState == null && width > 0 && height > 0) fit(paddingPx)
    }

    fun fit(paddingPx: Double) {
        val artboard = document.artboard
        viewportState =
            Viewport.fitting(Rect(0.0, 0.0, artboard.width, artboard.height), canvasWidth.toDouble(), canvasHeight.toDouble(), paddingPx)
    }

    /** 지금 보이는 영역의 가운데(문서 좌표). */
    fun visibleCenter(): Vec2 = viewport.toDocument(Vec2(canvasWidth / 2.0, canvasHeight / 2.0))

    fun nextSequence(): Int = created++

    override fun select(ids: List<NodeId>) = session.select(ids)

    override fun beginTransaction(label: String): Boolean = session.commands.beginTransaction(label)

    override fun execute(command: SceneCommand): Boolean = session.commands.execute(command) is CommandResult.Applied

    override fun commitTransaction() {
        session.commands.commitTransaction()
    }

    override fun rollbackTransaction() {
        session.commands.rollbackTransaction()
    }
}
