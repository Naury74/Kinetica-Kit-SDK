package com.naury.kinetica.core.scene

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import org.junit.Test

class BindingsTest {
    private val label = SceneNode(
        id("label"),
        NodeContent.Text(Size(200.0, 40.0), "기본"),
        bindings = mapOf(BoundProperty.Text to "order.total", BoundProperty.Visible to "order.show"),
    )
    private val badge = rect("badge").copy(bindings = mapOf(BoundProperty.Fill to "status.color", BoundProperty.Opacity to "status.alpha"))
    private val photo =
        SceneNode(
            id("photo"),
            NodeContent.Image(Size(10.0, 10.0), "content://a/1"),
            bindings = mapOf(BoundProperty.ImageUri to "user.avatar"),
        )
    private val scene = document(listOf(label, badge, photo))

    private fun SceneDocument.text(id: String) = (node(id(id))!!.content as NodeContent.Text).text

    @Test
    fun `데이터 값으로 글자·보임·색·불투명도·이미지를 바꾼다`() {
        val data = SceneData.of(
            "order.total" to DataValue.Number(12_500.0),
            "order.show" to DataValue.Bool(false),
            "status.color" to DataValue.Color(ArgbColor(0xFF00FF00.toInt())),
            "status.alpha" to DataValue.Number(2.0),
            "user.avatar" to DataValue.Text("content://a/2"),
        )

        val shown = Bindings.apply(scene, data)

        assertThat(shown.text("label")).isEqualTo("12500")
        assertThat(shown.node(id("label"))!!.visible).isFalse()
        assertThat(shown.node(id("badge"))!!.style.fill).isEqualTo(ArgbColor(0xFF00FF00.toInt()))
        assertThat(shown.node(id("badge"))!!.style.opacity).isEqualTo(1.0)
        assertThat((shown.node(id("photo"))!!.content as NodeContent.Image).uri).isEqualTo("content://a/2")
        assertThat(scene.text("label")).isEqualTo("기본")
    }

    @Test
    fun `값이 없거나 종류가 맞지 않거나 위험하면 저장된 값을 쓴다`() {
        val data = SceneData.of(
            "order.total" to DataValue.Bool(true),
            "status.color" to DataValue.Text("#00FF00"),
            "user.avatar" to DataValue.Text("https://evil.example/a.png"),
            "status.alpha" to DataValue.Number(Double.NaN),
        )

        val shown = Bindings.apply(scene, data)

        assertThat(shown).isEqualTo(scene)
        assertThat(Bindings.apply(scene, SceneData.Empty)).isSameInstanceAs(scene)
    }

    @Test
    fun `너무 긴 글자는 바이트 상한으로 자른다`() {
        val long = "가".repeat(30_000)

        val shown = Bindings.apply(scene, SceneData.of("order.total" to DataValue.Text(long)), SceneLimits(maxTextBytes = 300))

        assertThat(shown.text("label").toByteArray().size).isAtMost(300)
        assertThat(shown.text("label")).startsWith("가가")
    }

    @Test
    fun `바인딩 키·동작 이름 형식과 맞지 않는 속성을 검증에서 거부한다`() {
        val badKey = rect("k").copy(bindings = mapOf(BoundProperty.Fill to "bad key"))
        val badAction = rect("a").copy(action = "../x")
        val textOnRect = rect("t").copy(bindings = mapOf(BoundProperty.Text to "x"))
        val validator = SceneValidator()

        listOf(badKey, badAction, textOnRect).forEach { node ->
            assertThat(validator.validate(document(listOf(node))).map { it.code }).containsExactly(ViolationCode.InvalidBinding)
        }
        assertThat(validator.validate(scene)).isEmpty()
    }

    @Test
    fun `Frame은 자식을 담고 HostSlot은 키를 검증한다`() {
        val frame = SceneNode(id("f"), NodeContent.Frame(Size(300.0, 200.0), listOf(id("c")), cornerRadius = 16.0))
        val child = rect("c", parent = "f")
        val slot = SceneNode(id("s"), NodeContent.HostSlot(Size(100.0, 100.0), "widget.weather"))
        val badSlot = SceneNode(id("b"), NodeContent.HostSlot(Size(100.0, 100.0), "bad key"))

        assertThat(SceneValidator().validate(document(listOf(frame, child, slot)))).isEmpty()
        assertThat(SceneValidator().validate(document(listOf(badSlot))).map { it.code }).containsExactly(ViolationCode.InvalidBinding)
    }
}
