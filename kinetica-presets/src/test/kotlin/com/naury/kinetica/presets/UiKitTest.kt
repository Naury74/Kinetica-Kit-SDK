package com.naury.kinetica.presets

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.command.SceneReducer
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneValidator
import org.junit.Test

class UiKitTest {
    private val kit = UiKit.starter(Vec2(1200.0, 0.0))
    private val document = SceneDocument(
        documentId = "kit",
        artboard = Artboard(1080.0, 1920.0),
        nodes = kit.nodes.associateBy { it.id },
        rootOrder = kit.roots,
        tokens = kit.tokens,
    )

    @Test
    fun `기본 키트는 규칙을 지키는 컴포넌트 원본 9개이고 artboard 밖 한 줄에 놓인다`() {
        assertThat(SceneValidator().validate(document)).isEmpty()
        val roots = kit.roots.map { document.node(it)!! }
        assertThat(roots.map { it.component }).containsExactly(
            "Button / Primary",
            "Button / Secondary",
            "Button / Disabled",
            "Chip",
            "Card",
            "Toggle",
            "Input",
            "List item",
            "Tab bar",
        ).inOrder()
        assertThat(roots.all { it.transform.translationX == 1200.0 }).isTrue()
        val buttons = com.naury.kinetica.core.scene.ComponentVariants.variantsOf(document, kit.roots.first())
        assertThat(buttons.map { com.naury.kinetica.core.scene.ComponentVariants.variantName(it.component!!) })
            .containsExactly("Primary", "Secondary", "Disabled").inOrder()
        assertThat(roots.zipWithNext().all { (a, b) -> a.transform.translationY < b.transform.translationY }).isTrue()
    }

    @Test
    fun `토큰을 바꾸면 키트 색이 따라가고 문서의 브랜드 색을 이어받는다`() {
        val reducer = SceneReducer()
        val red = ArgbColor(0xFFFF0000.toInt())
        val recolored = (reducer.apply(document, SceneCommand.SetToken(UiKit.Token.BRAND, red)) as CommandResult.Applied).document
        val inherited = UiKit.starter(Vec2.Zero, prefix = "k2", tokens = mapOf(UiKit.Token.BRAND to red))

        assertThat(recolored.node(kit.roots.first())!!.style.fill).isEqualTo(red)
        assertThat(inherited.tokens.getValue(UiKit.Token.BRAND)).isEqualTo(red)
        assertThat(inherited.nodes.first().style.fill).isEqualTo(red)
    }

    @Test
    fun `버튼 인스턴스를 넣으면 원본 글자를 복사한다`() {
        val reducer = SceneReducer()
        val button = document.node(kit.roots.first())!!
        val instance = button.copy(
            id = com.naury.kinetica.core.scene.NodeId("cta"),
            component = null,
            instanceOf = com.naury.kinetica.core.scene.InstanceOf(button.id),
            content = (button.content as NodeContent.Frame).copy(childIds = emptyList()),
        )

        val added = (reducer.apply(document, SceneCommand.AddNode(instance)) as CommandResult.Applied).document
        val label = added.node((added.node(instance.id)!!.content as NodeContent.Frame).childIds.single())!!

        assertThat((label.content as NodeContent.Text).text).isEqualTo("확인")
    }
}
