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

class ComponentVariantsTest {
    private val reducer = SceneReducer()
    private val blue = ArgbColor(0xFF2255FF.toInt())
    private val gray = ArgbColor(0xFF999999.toInt())

    private fun variant(key: String, name: String, fill: ArgbColor, labelX: Double): List<SceneNode> = listOf(
        SceneNode(
            id(key),
            NodeContent.Frame(Size(200.0, 60.0), listOf(id("$key-label"))),
            component = name,
            style = NodeStyle(fill = fill),
        ),
        SceneNode(
            id("$key-label"),
            NodeContent.Text(Size(180.0, 40.0), "확인"),
            parentId = id(key),
            name = "label",
            transform = Transform2D(translationX = labelX),
        ),
    )

    private val scene = document(
        variant("normal", "버튼/기본", blue, 10.0) + variant("off", "버튼 / 꺼짐", gray, 20.0) + variant("other", "카드", blue, 0.0),
    )

    private fun SceneDocument.apply(command: SceneCommand) = (reducer.apply(this, command) as CommandResult.Applied).document

    /** "구매"로 글자를 덮어쓴 기본 변형 인스턴스. */
    private fun withInstance(): SceneDocument {
        val added = scene.apply(
            SceneCommand.AddNode(
                SceneNode(
                    id("buy"),
                    NodeContent.Frame(Size(200.0, 60.0)),
                    instanceOf = InstanceOf(id("normal")),
                    bindings = mapOf(BoundProperty.Variant to "buy.state"),
                ),
            ),
        )
        val label = added.node(Components.childId(id("buy"), id("normal-label")))!!
        return added.apply(SceneCommand.UpdateProperties(label.copy(content = (label.content as NodeContent.Text).copy(text = "구매"))))
    }

    private fun SceneDocument.labelOf(instance: String, source: String) = node(Components.childId(id(instance), id(source)))!!

    @Test
    fun `이름의 마지막 슬래시로 세트와 변형을 나눈다`() {
        assertThat(ComponentVariants.setName("버튼 / 꺼짐")).isEqualTo("버튼")
        assertThat(ComponentVariants.variantName("버튼 / 꺼짐")).isEqualTo("꺼짐")
        assertThat(ComponentVariants.setName("카드")).isNull()
        assertThat(ComponentVariants.variantsOf(scene, id("normal")).map { it.id }).containsExactly(id("normal"), id("off")).inOrder()
        assertThat(ComponentVariants.variantsOf(scene, id("other")).map { it.id }).containsExactly(id("other"))
    }

    @Test
    fun `변형을 바꾸면 원본 모양을 따르고 덮어쓴 글자는 이름이 같은 레이어로 옮긴다`() {
        val document = withInstance()

        val link = ComponentVariants.swap(document, id("buy"), "꺼짐")!!
        val swapped = document.apply(SceneCommand.UpdateProperties(document.node(id("buy"))!!.copy(instanceOf = link)))

        assertThat(link.main).isEqualTo(id("off"))
        assertThat(swapped.node(id("buy"))!!.style.fill).isEqualTo(gray)
        val label = swapped.labelOf("buy", "off-label")
        assertThat((label.content as NodeContent.Text).text).isEqualTo("구매")
        assertThat(label.transform.translationX).isEqualTo(20.0)
        assertThat(swapped.node(Components.childId(id("buy"), id("normal-label")))).isNull()
        assertThat(ComponentVariants.swap(document, id("buy"), "없음")).isNull()
    }

    @Test
    fun `데이터로 변형을 고르면 그리기용 문서에서만 바뀐다`() {
        val document = withInstance()

        val shown = Bindings.apply(document, SceneData.of("buy.state" to DataValue.Text("꺼짐")))

        assertThat(shown.node(id("buy"))!!.instanceOf!!.main).isEqualTo(id("off"))
        assertThat((shown.labelOf("buy", "off-label").content as NodeContent.Text).text).isEqualTo("구매")
        assertThat(document.node(id("buy"))!!.instanceOf!!.main).isEqualTo(id("normal"))
        assertThat(SceneValidator().validate(shown)).isEmpty()
        // 없는 변형 이름이면 그대로 둔다.
        assertThat(Bindings.apply(document, SceneData.of("buy.state" to DataValue.Text("?")))).isEqualTo(document)
    }

    @Test
    fun `변형 바인딩은 인스턴스에만 붙일 수 있다`() {
        val bad = scene.copy(
            nodes = scene.nodes + (id("other") to scene.node(id("other"))!!.copy(bindings = mapOf(BoundProperty.Variant to "state"))),
        )

        assertThat(SceneValidator().validate(bad).map { it.code }).contains(ViolationCode.InvalidBinding)
    }
}
