package com.naury.kinetica.gestures

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.group
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import org.junit.Test

class CanvasGestureControllerTest {
    /** 실제 세션을 쓰는 host. viewport는 배율 2, 이동 (10, 20)이다. */
    private class SessionHost(document: SceneDocument) : GestureHost {
        val session = (KineticaSession.create(document) as SessionOpenResult.Opened).session
        override val document: SceneDocument get() = session.document.value
        override val selection: List<NodeId> get() = session.selection.value.selectedIds
        override var viewport: Viewport = Viewport(panX = 10.0, panY = 20.0, zoom = 2.0)

        override fun select(ids: List<NodeId>) = session.select(ids)

        override fun beginTransaction(label: String) = session.commands.beginTransaction(label)

        override fun execute(command: SceneCommand) = session.commands.execute(command) is CommandResult.Applied

        override fun commitTransaction() {
            session.commands.commitTransaction()
        }

        override fun rollbackTransaction() {
            session.commands.rollbackTransaction()
        }
    }

    private val scene = document(
        listOf(
            rect("a", size = Size(100.0, 50.0), transform = Transform2D(translationX = 100.0, translationY = 100.0)),
            group("g", listOf("c")),
            rect("c", parent = "g", size = Size(40.0, 40.0), transform = Transform2D(translationX = 300.0, translationY = 300.0)),
            rect(
                "locked",
                size = Size(40.0, 40.0),
                transform = Transform2D(translationX = 500.0, translationY = 100.0),
            ).copy(locked = true),
        ),
        rootOrder = listOf("a", "g", "locked"),
    )
    private val host = SessionHost(scene)
    private val controller = CanvasGestureController(
        host,
        GestureConfig(touchSlopPx = 8.0, handleTouchRadiusPx = 24.0, hitSlopPx = 8.0, rotateHandleOffsetPx = 40.0),
    )

    private fun screen(documentX: Double, documentY: Double): Vec2 = host.viewport.toViewport(Vec2(documentX, documentY))

    private fun drag(from: Vec2, to: Vec2, steps: Int = 30, pointer: Long = 1) {
        controller.onDown(pointer, from)
        (1..steps).forEach { controller.onMove(pointer, from + (to - from) * (it.toDouble() / steps)) }
        controller.onUp(pointer)
    }

    private fun translation(id: String) = host.document.node(NodeId(id))!!.transform

    @Test
    fun `노드를 끌면 손가락만큼 옮기고 실행 취소 한 단계가 된다`() {
        drag(screen(120.0, 120.0), screen(170.0, 140.0))

        assertThat(translation("a").translationX).isWithin(1e-9).of(150.0)
        assertThat(translation("a").translationY).isWithin(1e-9).of(120.0)
        assertThat(host.selection).containsExactly(id("a"))
        assertThat(host.session.history.undo()).isTrue()
        assertThat(host.document).isEqualTo(scene)
        assertThat(host.session.history.undo()).isFalse()
    }

    @Test
    fun `끌기 기준 거리 안에서 떼면 고르기만 하고 기록을 남기지 않는다`() {
        controller.onDown(1, screen(120.0, 120.0))
        controller.onMove(1, screen(120.0, 120.0) + Vec2(3.0, 3.0))
        controller.onUp(1)

        assertThat(host.selection).containsExactly(id("a"))
        assertThat(host.document).isEqualTo(scene)
        assertThat(host.session.history.state.value.canUndo).isFalse()
    }

    @Test
    fun `빈 곳을 누르면 선택을 지운다`() {
        host.select(listOf(id("a")))

        controller.onDown(1, screen(900.0, 900.0))
        controller.onUp(1)

        assertThat(host.selection).isEmpty()
    }

    @Test
    fun `그룹 안 노드를 누르면 최상위 그룹을 고르고 함께 옮긴다`() {
        drag(screen(310.0, 310.0), screen(330.0, 310.0))

        assertThat(host.selection).containsExactly(id("g"))
        assertThat(translation("g").translationX).isWithin(1e-9).of(20.0)
        assertThat(translation("c").translationX).isEqualTo(300.0)
    }

    @Test
    fun `잠긴 노드는 잡지 않는다`() {
        drag(screen(510.0, 110.0), screen(600.0, 110.0))

        assertThat(translation("locked").translationX).isEqualTo(500.0)
        assertThat(host.session.history.state.value.canUndo).isFalse()
    }

    @Test
    fun `취소되면 시작 전 문서로 되돌리고 기록을 남기지 않는다`() {
        controller.onDown(1, screen(120.0, 120.0))
        controller.onMove(1, screen(220.0, 120.0))
        assertThat(translation("a").translationX).isNotEqualTo(100.0)

        controller.onCancel()

        assertThat(host.document).isEqualTo(scene)
        assertThat(host.session.history.state.value.canUndo).isFalse()
        assertThat(controller.mode).isEqualTo(InteractionMode.Idle)
    }

    @Test
    fun `손잡이를 끌면 크기를 바꾸고 회전 손잡이를 끌면 돌린다`() {
        host.select(listOf(id("a")))

        drag(screen(200.0, 150.0), screen(250.0, 150.0))
        val resized = host.document.node(id("a"))!!.content as NodeContent.Rectangle
        assertThat(resized.size.width).isWithin(1e-9).of(150.0)
        assertThat(controller.mode).isEqualTo(InteractionMode.Idle)

        val center = Vec2(175.0, 125.0)
        val rotateHandle = screen(center.x, 100.0) + Vec2(0.0, -40.0)
        drag(rotateHandle, screen(center.x + 200.0, center.y), steps = 60)
        assertThat(host.document.node(id("a"))!!.transform.rotationDegrees).isWithin(1.0).of(90.0)
        assertThat(host.session.history.state.value.undoLabel).isEqualTo("rotate")
    }

    @Test
    fun `작은 도형은 손잡이 영역이 몸통을 덮어도 안쪽을 끌면 옮긴다`() {
        // a: 문서 100x50, 화면 배율 2라 200x100 px. 손잡이 터치 반경 24px.
        host.select(listOf(id("a")))
        val small = CanvasGestureController(host, GestureConfig(8.0, 60.0, 8.0, 40.0))

        small.onDown(1, screen(150.0, 125.0))
        small.onMove(1, screen(170.0, 125.0))
        small.onUp(1)

        assertThat(host.session.history.state.value.undoLabel).isEqualTo("move")
        assertThat((host.document.node(id("a"))!!.content as NodeContent.Rectangle).size.height).isEqualTo(50.0)
        assertThat(translation("a").translationX).isWithin(1e-9).of(120.0)
    }

    @Test
    fun `테두리 밖 가까운 곳은 손잡이를 잡는다`() {
        host.select(listOf(id("a")))

        drag(screen(150.0, 100.0) + Vec2(0.0, -20.0), screen(150.0, 80.0))

        assertThat(host.session.history.state.value.undoLabel).isEqualTo("resize")
    }

    @Test
    fun `빈 곳에서 끌면 사각 영역에 걸친 최상위 노드를 고른다`() {
        controller.onDown(1, screen(50.0, 50.0))
        controller.onMove(1, screen(320.0, 320.0))
        assertThat(controller.marquee).isNotNull()
        controller.onUp(1)

        assertThat(host.selection).containsExactly(id("a"), id("g")).inOrder()
        assertThat(controller.marquee).isNull()
        assertThat(host.document).isEqualTo(scene)
    }

    @Test
    fun `두 손가락은 중심 아래 문서 점을 유지하며 이동·확대한다`() {
        val first = Vec2(100.0, 100.0)
        val second = Vec2(300.0, 100.0)
        val anchor = host.viewport.toDocument(Vec2(200.0, 100.0))

        controller.onDown(1, first)
        controller.onDown(2, second)
        controller.onMove(1, Vec2(0.0, 150.0))
        controller.onMove(2, Vec2(400.0, 150.0))

        assertThat(host.viewport.zoom).isWithin(1e-9).of(4.0)
        assertThat(host.viewport.toViewport(anchor).distanceTo(Vec2(200.0, 150.0))).isLessThan(1e-9)
        controller.onUp(1)
        controller.onMove(2, Vec2(999.0, 999.0))
        controller.onUp(2)
        assertThat(controller.mode).isEqualTo(InteractionMode.Idle)
        assertThat(host.document).isEqualTo(scene)
    }

    @Test
    fun `끌던 중 두 번째 손가락이 닿으면 그때까지 옮긴 것을 확정한다`() {
        controller.onDown(1, screen(120.0, 120.0))
        controller.onMove(1, screen(140.0, 120.0))

        controller.onDown(2, Vec2(700.0, 700.0))

        assertThat(controller.mode).isEqualTo(InteractionMode.TransformCanvas)
        assertThat(translation("a").translationX).isWithin(1e-9).of(120.0)
        assertThat(host.session.history.state.value.canUndo).isTrue()
    }

    @Test
    fun `옮기기는 스냅이 고친 이동량을 쓰고 끝나면 스냅을 정리한다`() {
        var finished = 0
        val snapping = DragSnapFactory { _, moving ->
            assertThat(moving).containsExactly(id("a"))
            object : DragSnap {
                override fun adjust(documentDelta: Vec2, zoom: Double) = Vec2(Math.round(documentDelta.x / 50.0) * 50.0, 0.0)

                override fun finish() {
                    finished++
                }
            }
        }
        val snapped = CanvasGestureController(host, GestureConfig(8.0, 24.0, 8.0, 40.0), snapping)

        snapped.onDown(1, screen(120.0, 120.0))
        snapped.onMove(1, screen(180.0, 133.0))
        snapped.onUp(1)

        assertThat(translation("a").translationX).isEqualTo(150.0)
        assertThat(translation("a").translationY).isEqualTo(100.0)
        assertThat(finished).isEqualTo(1)
    }

    @Test
    fun `같은 노드를 빠르게 두 번 누르면 그룹 안이어도 그 노드를 알린다`() {
        var now = 1_000L
        val doubleTapped = mutableListOf<NodeId>()
        val timed = CanvasGestureController(host, GestureConfig(8.0, 24.0, 8.0, 40.0), uptimeMillis = { now })
        timed.onDoubleTap = { doubleTapped += it }
        fun tapAt(x: Double, y: Double) {
            timed.onDown(1, screen(x, y))
            timed.onUp(1)
        }

        tapAt(310.0, 310.0)
        now += 200
        tapAt(312.0, 311.0)
        now += 1_000
        tapAt(120.0, 120.0)
        now += 500
        tapAt(120.0, 120.0)

        assertThat(doubleTapped).containsExactly(id("c"))
        assertThat(host.selection).containsExactly(id("a"))
    }

    @Test
    fun `마우스 휠은 포인터 기준으로 확대한다`() {
        val point = Vec2(321.0, 123.0)
        val anchor = host.viewport.toDocument(point)

        controller.onScroll(point, -3.0)

        assertThat(host.viewport.zoom).isGreaterThan(2.0)
        assertThat(host.viewport.toViewport(anchor).distanceTo(point)).isLessThan(1e-9)
    }
}
