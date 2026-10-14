package com.naury.kinetica.core.scene

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.command.SceneReducer
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.id
import org.junit.Test

class ComponentsTest {
    private val reducer = SceneReducer()
    private val blue = ArgbColor(0xFF2255FF.toInt())
    private val red = ArgbColor(0xFFFF0000.toInt())

    private val button = SceneNode(
        id("button"),
        NodeContent.Frame(Size(200.0, 60.0), listOf(id("label"))),
        component = "Button",
        style = NodeStyle(fill = blue),
    )
    private val label = SceneNode(
        id("label"),
        NodeContent.Text(Size(180.0, 40.0), "확인"),
        parentId = id("button"),
        transform = Transform2D(translationX = 10.0, translationY = 10.0),
    )
    private val scene = document(listOf(button, label))

    private fun SceneDocument.apply(command: SceneCommand) = (reducer.apply(this, command) as CommandResult.Applied).document

    private fun withInstance(): SceneDocument = scene.apply(
        SceneCommand.AddNode(
            SceneNode(
                id("buy"),
                NodeContent.Frame(Size(200.0, 60.0)),
                transform = Transform2D(translationX = 300.0),
                instanceOf = InstanceOf(id("button")),
            ),
        ),
    )

    private fun SceneDocument.copyOf(source: String) = node(Components.childId(id("buy"), id(source)))!!

    @Test
    fun `인스턴스를 넣으면 원본 자식이 복사되고 원본을 고치면 인스턴스도 바뀐다`() {
        val added = withInstance()
        assertThat((added.copyOf("label").content as NodeContent.Text).text).isEqualTo("확인")
        assertThat(added.copyOf("label").transform.translationX).isEqualTo(10.0)
        assertThat(added.node(id("buy"))!!.style.fill).isEqualTo(blue)

        val edited = added.apply(SceneCommand.UpdateProperties(label.copy(content = (label.content as NodeContent.Text).copy(text = "구매"))))

        assertThat((edited.copyOf("label").content as NodeContent.Text).text).isEqualTo("구매")
    }

    @Test
    fun `인스턴스 안 글자·색을 고치면 덮어쓴 값으로 남아 원본이 바뀌어도 유지된다`() {
        val added = withInstance()
        val copy = added.copyOf("label")
        val overridden = added.apply(
            SceneCommand.UpdateProperties(
                copy.copy(content = (copy.content as NodeContent.Text).copy(text = "지금 구매"), style = copy.style.copy(fill = red)),
            ),
        )
        val mainChanged = overridden.apply(
            SceneCommand.UpdateProperties(label.copy(transform = Transform2D(translationX = 30.0, translationY = 10.0))),
        )

        val instance = mainChanged.node(id("buy"))!!
        assertThat(instance.instanceOf!!.overrides.getValue(id("label"))).isEqualTo(Override(text = "지금 구매", fill = red))
        assertThat((mainChanged.copyOf("label").content as NodeContent.Text).text).isEqualTo("지금 구매")
        assertThat(mainChanged.copyOf("label").transform.translationX).isEqualTo(30.0)
    }

    @Test
    fun `덮어쓴 값을 비우면 원본을 따르고 원본을 지우면 인스턴스는 일반 Frame이 된다`() {
        val added = withInstance()
        val copy = added.copyOf("label")
        val overridden = added.apply(
            SceneCommand.UpdateProperties(copy.copy(content = (copy.content as NodeContent.Text).copy(text = "다른"))),
        )
        val instance = overridden.node(id("buy"))!!
        val reset = overridden.apply(
            SceneCommand.UpdateProperties(instance.copy(instanceOf = instance.instanceOf!!.copy(overrides = emptyMap()))),
        )
        val detached = reset.apply(SceneCommand.RemoveNodes(setOf(id("button"))))

        assertThat((reset.copyOf("label").content as NodeContent.Text).text).isEqualTo("확인")
        assertThat(detached.node(id("buy"))!!.instanceOf).isNull()
        assertThat((detached.copyOf("label").content as NodeContent.Text).text).isEqualTo("확인")
    }

    @Test
    fun `인스턴스를 넓히면 자식이 원본 크기 기준 제약을 따른다`() {
        val pinned = scene.apply(SceneCommand.UpdateProperties(label.copy(constraints = LayoutConstraints(Pin.Both))))
            .apply(SceneCommand.AddNode(SceneNode(id("buy"), NodeContent.Frame(Size(200.0, 60.0)), instanceOf = InstanceOf(id("button")))))
        val instance = pinned.node(id("buy"))!!

        val wide = pinned.apply(
            SceneCommand.UpdateProperties(instance.copy(content = (instance.content as NodeContent.Frame).copy(size = Size(400.0, 60.0)))),
        )
        val again = wide.apply(SceneCommand.UpdateProperties(button.copy(style = NodeStyle(fill = red))))

        assertThat((wide.copyOf("label").content as NodeContent.Text).size.width).isEqualTo(380.0)
        assertThat((again.copyOf("label").content as NodeContent.Text).size.width).isEqualTo(380.0)
        assertThat(again.node(id("buy"))!!.style.fill).isEqualTo(red)
    }

    @Test
    fun `토큰 색을 바꾸면 연결된 노드가 모두 바뀌고 직접 고친 노드는 연결이 끊긴다`() {
        val linked = scene.copy(tokens = mapOf("brand" to blue)).let { doc ->
            doc.apply(SceneCommand.UpdateProperties(button.copy(style = NodeStyle(fill = blue, fillToken = "brand"))))
        }
        val recolored = linked.apply(SceneCommand.SetToken("brand", red))
        val direct = recolored.apply(
            SceneCommand.UpdateProperties(
                recolored.node(id("button"))!!.let {
                    it.copy(style = it.style.copy(fill = blue))
                },
            ),
        )

        assertThat(recolored.node(id("button"))!!.style.fill).isEqualTo(red)
        assertThat(direct.node(id("button"))!!.style.fillToken).isNull()
        assertThat(direct.node(id("button"))!!.style.fill).isEqualTo(blue)
    }

    @Test
    fun `컴포넌트·인스턴스는 Frame이어야 하고 토큰 이름 형식을 지킨다`() {
        val bad = scene.copy(
            nodes = scene.nodes + (id("label") to label.copy(component = "Label")),
            tokens = mapOf("bad name" to blue),
        )

        assertThat(SceneValidator().validate(bad).map { it.nodeId }).containsAtLeast(id("label"), null)
    }
}
