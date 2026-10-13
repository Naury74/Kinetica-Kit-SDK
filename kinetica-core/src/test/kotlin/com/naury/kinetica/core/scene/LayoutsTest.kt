package com.naury.kinetica.core.scene

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.command.SceneReducer
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import org.junit.Test

class LayoutsTest {
    private fun frame(
        id: String,
        children: List<String>,
        layout: AutoLayout?,
        size: Size = Size(400.0, 300.0),
        parent: String? = null,
        transform: Transform2D = Transform2D(translationX = 100.0, translationY = 100.0),
        constraints: LayoutConstraints = LayoutConstraints.Default,
    ) = SceneNode(
        id(id),
        NodeContent.Frame(size, children.map(::NodeId), layout = layout),
        parentId = parent?.let(::NodeId),
        transform = transform,
        constraints = constraints,
    )

    private fun SceneDocument.x(id: String) = nodes.getValue(id(id)).transform.translationX
    private fun SceneDocument.y(id: String) = nodes.getValue(id(id)).transform.translationY
    private fun SceneDocument.size(id: String) = nodes.getValue(id(id)).content.sizeOrNull()!!

    @Test
    fun `세로 배치는 여백·간격대로 쌓는다`() {
        val layout = AutoLayout(LayoutDirection.Vertical, gap = 10.0, padding = Insets.all(20.0))
        val scene = document(
            listOf(
                frame("f", listOf("a", "b"), layout),
                rect("a", parent = "f", transform = Transform2D(translationX = 300.0, translationY = 250.0)),
                rect("b", parent = "f", size = Size(80.0, 40.0)),
            ),
        )

        val laid = Layouts.apply(scene)

        assertThat(laid.x("a")).isEqualTo(20.0)
        assertThat(laid.y("a")).isEqualTo(20.0)
        assertThat(laid.y("b")).isEqualTo(80.0)
        assertThat(Layouts.apply(laid)).isSameInstanceAs(laid)
    }

    @Test
    fun `가로 배치의 가운데·끝·사이 정렬과 교차 가운데`() {
        fun laid(main: MainAlign) = Layouts.apply(
            document(
                listOf(
                    frame(
                        "f",
                        listOf("a", "b"),
                        AutoLayout(LayoutDirection.Horizontal, gap = 20.0, mainAlign = main, crossAlign = CrossAlign.Center),
                    ),
                    rect("a", parent = "f"),
                    rect("b", parent = "f", size = Size(100.0, 100.0)),
                ),
            ),
        )

        assertThat(laid(MainAlign.Center).x("a")).isEqualTo(90.0)
        assertThat(laid(MainAlign.End).x("b")).isEqualTo(300.0)
        assertThat(laid(MainAlign.SpaceBetween).x("b")).isEqualTo(300.0)
        assertThat(laid(MainAlign.Center).y("a")).isEqualTo(125.0)
        assertThat(laid(MainAlign.Center).y("b")).isEqualTo(100.0)
    }

    @Test
    fun `내용 맞춤은 Frame 크기를 자식에 맞추고 숨긴 자식은 빠진다`() {
        val layout = AutoLayout(LayoutDirection.Vertical, gap = 10.0, padding = Insets.all(5.0), hugWidth = true, hugHeight = true)
        val scene = document(
            listOf(
                frame("f", listOf("a", "hidden", "b"), layout),
                rect("a", parent = "f"),
                rect("hidden", parent = "f").copy(visible = false),
                rect("b", parent = "f", size = Size(200.0, 30.0)),
            ),
        )

        val laid = Layouts.apply(scene)

        assertThat(laid.size("f")).isEqualTo(Size(210.0, 100.0))
        assertThat(laid.y("b")).isEqualTo(65.0)
    }

    @Test
    fun `늘이기는 교차 방향을 안쪽 폭까지 늘이고 안쪽 Frame 배치도 다시 한다`() {
        val outer = AutoLayout(LayoutDirection.Vertical, padding = Insets.all(10.0), crossAlign = CrossAlign.Stretch)
        val inner = AutoLayout(LayoutDirection.Horizontal, mainAlign = MainAlign.End)
        val scene = document(
            listOf(
                frame("outer", listOf("inner"), outer),
                frame("inner", listOf("btn"), inner, size = Size(100.0, 60.0), parent = "outer", transform = Transform2D.Identity),
                rect("btn", parent = "inner"),
            ),
        )

        val laid = Layouts.apply(scene)

        assertThat(laid.size("inner")).isEqualTo(Size(380.0, 60.0))
        assertThat(laid.x("btn")).isEqualTo(280.0)
    }

    @Test
    fun `늘이기는 내용에 맞추는 자식 Frame을 늘이지 않는다`() {
        val outer = AutoLayout(LayoutDirection.Vertical, crossAlign = CrossAlign.Stretch)
        val chip = AutoLayout(LayoutDirection.Horizontal, padding = Insets.all(4.0), hugWidth = true, hugHeight = true)
        val scene = document(
            listOf(
                frame("outer", listOf("chip"), outer),
                frame("chip", listOf("label"), chip, size = Size(10.0, 10.0), parent = "outer", transform = Transform2D.Identity),
                rect("label", parent = "chip"),
            ),
        )

        val laid = Layouts.apply(scene)

        assertThat(laid.size("chip")).isEqualTo(Size(108.0, 58.0))
        assertThat(Layouts.apply(laid)).isSameInstanceAs(laid)
    }

    @Test
    fun `채움 자식은 남는 공간을 똑같이 나눈다`() {
        val row = AutoLayout(LayoutDirection.Horizontal, gap = 20.0, padding = Insets.all(10.0))
        val scene = document(
            listOf(
                frame("row", listOf("fixed", "a", "b"), row),
                rect("fixed", parent = "row", size = Size(60.0, 40.0)),
                rect("a", parent = "row").copy(layoutFill = true),
                rect("b", parent = "row").copy(layoutFill = true),
            ),
        )

        val laid = Layouts.apply(scene)

        assertThat(laid.size("a")).isEqualTo(Size(140.0, 50.0))
        assertThat(laid.x("b")).isEqualTo(250.0)
    }

    @Test
    fun `artboard 크기를 바꾸면 최상위 노드가 제약대로 움직인다`() {
        val bottomBar = rect("bar", size = Size(1080.0, 120.0), transform = Transform2D(translationY = 1800.0))
            .copy(constraints = LayoutConstraints(Pin.Both, Pin.End))
        val centered = rect("logo", transform = Transform2D(translationX = 490.0, translationY = 935.0))
            .copy(constraints = LayoutConstraints(Pin.Center, Pin.Center))
        val scene = document(listOf(bottomBar, centered))

        val tall = Layouts.resizeArtboard(scene, 1440.0, 2400.0)

        assertThat(tall.y("bar")).isEqualTo(2280.0)
        assertThat(tall.size("bar")).isEqualTo(Size(1440.0, 120.0))
        assertThat(tall.x("logo")).isEqualTo(670.0)
        assertThat(tall.y("logo")).isEqualTo(1175.0)
    }

    @Test
    fun `명령으로 Frame 크기를 바꾸면 자식이 제약을 따르고 자동 배치는 명령 뒤에 다시 계산된다`() {
        val card = frame("card", listOf("close", "list"), layout = null)
        val close = rect(
            "close",
            parent = "card",
            size = Size(40.0, 40.0),
            transform = Transform2D(translationX = 340.0, translationY = 20.0),
        )
            .copy(constraints = LayoutConstraints(Pin.End, Pin.Start))
        val list = frame(
            "list",
            listOf("a"),
            AutoLayout(LayoutDirection.Vertical, padding = Insets.all(8.0)),
            size = Size(200.0, 200.0),
            parent = "card",
            transform = Transform2D(translationY = 60.0),
        )
        val scene = document(listOf(card, close, list, rect("a", parent = "list")))
        val reducer = SceneReducer()

        val wider = card.copy(content = (card.content as NodeContent.Frame).copy(size = Size(600.0, 300.0)))
        val result = reducer.apply(scene, SceneCommand.UpdateProperties(wider)) as CommandResult.Applied
        val moved = rect("a", parent = "list", transform = Transform2D(translationX = 999.0))
        val relaid = reducer.apply(result.document, SceneCommand.UpdateProperties(moved)) as CommandResult.Applied

        assertThat(result.document.x("close")).isEqualTo(540.0)
        assertThat(result.document.x("a")).isEqualTo(8.0)
        assertThat(relaid.document.x("a")).isEqualTo(8.0)
    }

    @Test
    fun `여러 노드를 자동 배치 Frame으로 감싸면 위치 순서대로 쌓이고 실행 취소용 이전 문서는 그대로다`() {
        val scene = document(
            listOf(
                rect("low", transform = Transform2D(translationX = 100.0, translationY = 400.0)),
                rect("high", transform = Transform2D(translationX = 120.0, translationY = 200.0)),
            ),
        )
        val layout = AutoLayout(LayoutDirection.Vertical, gap = 12.0, padding = Insets.all(16.0), hugWidth = true, hugHeight = true)

        val result = SceneReducer().apply(scene, SceneCommand.WrapInFrame(listOf(id("low"), id("high")), id("card"), layout))
        val wrapped = (result as CommandResult.Applied).document
        val card = wrapped.nodes.getValue(id("card"))

        assertThat((card.content as NodeContent.Frame).childIds).containsExactly(id("high"), id("low")).inOrder()
        assertThat(card.transform.translationX).isEqualTo(84.0)
        assertThat(card.transform.translationY).isEqualTo(184.0)
        assertThat(wrapped.y("high")).isEqualTo(16.0)
        assertThat(wrapped.y("low")).isEqualTo(78.0)
        assertThat(wrapped.size("card")).isEqualTo(Size(132.0, 144.0))
        assertThat(scene.nodes).doesNotContainKey(id("card"))
    }

    @Test
    fun `음수 간격은 거부한다`() {
        val bad = frame("f", emptyList(), AutoLayout(gap = -1.0))

        assertThat(SceneValidator().validate(document(listOf(bad)))).isNotEmpty()
    }
}
