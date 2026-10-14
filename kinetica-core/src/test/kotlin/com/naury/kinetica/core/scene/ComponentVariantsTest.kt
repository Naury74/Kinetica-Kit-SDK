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

    @Test
    fun `이미 고른 변형이면 다시 만들지 않아 애니메이션 값이 그대로다`() {
        val data = SceneData.of("buy.state" to DataValue.Text("꺼짐"))
        val resolved = Bindings.resolveVariants(withInstance(), data)
        // 타임라인이 인스턴스 불투명도를 바꾼 상태라고 본다.
        val animated = resolved.copy(
            nodes = resolved.nodes + (id("buy") to resolved.node(id("buy"))!!.let { it.copy(style = it.style.copy(opacity = 0.3)) }),
        )

        assertThat(Bindings.resolveVariants(resolved, data)).isSameInstanceAs(resolved)
        assertThat(Bindings.apply(animated, data).node(id("buy"))!!.style.opacity).isEqualTo(0.3)
    }

    @Test
    fun `이름이 같은 레이어가 여럿이어도 덮어쓴 값이 제자리로 간다`() {
        fun twoTexts(key: String, name: String) = listOf(
            SceneNode(id(key), NodeContent.Frame(Size(200.0, 60.0), listOf(id("$key-a"), id("$key-b"))), component = name),
            SceneNode(id("$key-a"), NodeContent.Text(Size(90.0, 40.0), "A"), parentId = id(key), name = "Text"),
            SceneNode(id("$key-b"), NodeContent.Text(Size(90.0, 40.0), "B"), parentId = id(key), name = "Text"),
        )
        val scene = document(twoTexts("x", "칩/켜짐") + twoTexts("y", "칩/꺼짐"))
        val link = InstanceOf(id("x"), mapOf(id("x-a") to Override(text = "첫째"), id("x-b") to Override(text = "둘째")))
        val withLink = scene.copy(
            nodes = scene.nodes + (id("i") to SceneNode(id("i"), NodeContent.Frame(Size(200.0, 60.0)), instanceOf = link)),
            rootOrder = scene.rootOrder + id("i"),
        )

        val swapped = ComponentVariants.swapTo(withLink, link, id("y"))

        assertThat(swapped.overrides).containsExactly(id("y-a"), Override(text = "첫째"), id("y-b"), Override(text = "둘째"))
    }

    @Test
    fun `다른 컴포넌트 안에 든 인스턴스도 데이터로 변형을 고른다`() {
        val card = SceneNode(id("card"), NodeContent.Frame(Size(300.0, 200.0), listOf(id("inner"))), component = "Card")
        val inner = SceneNode(
            id("inner"),
            NodeContent.Frame(Size(200.0, 60.0)),
            parentId = id("card"),
            instanceOf = InstanceOf(id("normal")),
            bindings = mapOf(BoundProperty.Variant to "buy.state"),
        )
        val outer = SceneNode(id("outer"), NodeContent.Frame(Size(300.0, 200.0)), instanceOf = InstanceOf(id("card")))
        val base = scene.copy(
            nodes = scene.nodes + listOf(card, inner, outer).associateBy { it.id },
            rootOrder = scene.rootOrder + listOf(id("card"), id("outer")),
        )
        val synced = Components.sync(base)

        val shown = Bindings.resolveVariants(synced, SceneData.of("buy.state" to DataValue.Text("꺼짐")))

        val copied = shown.node(Components.childId(id("outer"), id("inner")))!!
        assertThat(copied.instanceOf!!.main).isEqualTo(id("off"))
        assertThat(copied.style.fill).isEqualTo(gray)
    }

    @Test
    fun `변형에 묶인 인스턴스를 분리해도 문서가 올바르다`() {
        val document = withInstance()
        val detached = document.node(id("buy"))!!.let { it.copy(instanceOf = null, bindings = it.bindings - BoundProperty.Variant) }

        assertThat(SceneValidator().validate(document.copy(nodes = document.nodes + (id("buy") to detached)))).isEmpty()
        val withoutMain = document.apply(SceneCommand.RemoveNodes(setOf(id("normal"))))
        assertThat(withoutMain.node(id("buy"))!!.bindings).doesNotContainKey(BoundProperty.Variant)
    }
}
