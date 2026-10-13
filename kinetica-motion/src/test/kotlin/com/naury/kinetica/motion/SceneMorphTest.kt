package com.naury.kinetica.motion

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.group
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import org.junit.Test

class SceneMorphTest {
    // 카드(A): 작은 사각형. 상세(B): 같은 morphKey의 큰 사각형, 회전한 그룹 안.
    private val card = SceneNode(
        id("card"),
        NodeContent.Rectangle(Size(100.0, 60.0), cornerRadius = 8.0),
        transform = Transform2D(translationX = 50.0, translationY = 400.0),
        style = NodeStyle(fill = ArgbColor(0xFF000000.toInt())),
        metadata = mapOf(SceneMorph.MORPH_KEY to "hero"),
    )
    private val sceneA = document(listOf(card, rect("onlyA", transform = Transform2D(translationX = 700.0))))
    private val hero = SceneNode(
        id("hero"),
        NodeContent.Rectangle(Size(1080.0, 600.0), cornerRadius = 0.0),
        parentId = id("g"),
        transform = Transform2D(translationX = 0.0, translationY = 0.0),
        style = NodeStyle(fill = ArgbColor(0xFFFFFFFF.toInt())),
        metadata = mapOf(SceneMorph.MORPH_KEY to "hero"),
    )
    private val sceneB = document(
        listOf(group("g", listOf("hero"), transform = Transform2D(translationY = 100.0, rotationDegrees = 10.0)), hero, rect("onlyB")),
        rootOrder = listOf("g", "onlyB"),
    )

    private fun morph(a: SceneDocument = sceneA, b: SceneDocument = sceneB) = (SceneMorph.create(a, b) as MorphResult.Ready).morph

    private fun corners(document: SceneDocument, node: SceneNode): List<Vec2> {
        val graph = SceneGraph(document)
        return node.localBounds!!.corners().map(graph.worldMatrix(node.id)!!::map)
    }

    private fun SceneDocument.withFill(color: Long) = nodes.values.firstOrNull { it.style.fill == ArgbColor(color.toInt()) }

    @Test
    fun `진행률 0은 원본, 1은 대상과 같은 모습이다`() {
        val start = morph().frame(0.0)
        val end = morph().frame(1.0)

        val startCard = start.nodes.values.single {
            it.content is NodeContent.Rectangle &&
                (it.content as NodeContent.Rectangle).cornerRadius == 8.0
        }
        corners(start, startCard).zip(corners(sceneA, card)).forEach { (a, b) -> assertThat(a.distanceTo(b)).isLessThan(1e-6) }
        val endHero = end.withFill(0xFFFFFFFF)!!
        corners(end, endHero).zip(corners(sceneB, hero)).forEach { (a, b) -> assertThat(a.distanceTo(b)).isLessThan(1e-6) }
    }

    @Test
    fun `짝지은 노드는 크기·모서리·색을 보간한다`() {
        val middle = morph().frame(0.5).nodes.values.single { (it.content as? NodeContent.Rectangle)?.size?.width == 590.0 }

        assertThat((middle.content as NodeContent.Rectangle).cornerRadius).isEqualTo(4.0)
        assertThat(middle.style.fill?.toHex()).isEqualTo("#808080")
        assertThat(middle.transform.rotationDegrees).isWithin(1e-9).of(5.0)
    }

    @Test
    fun `짝이 없는 노드는 사라지거나 나타난다`() {
        val frame = morph().frame(0.25)
        val opacities = frame.nodes.values.map { it.style.opacity }.sorted()

        assertThat(opacities).containsExactly(0.25, 0.75, 1.0).inOrder()
    }

    @Test
    fun `종류가 다른 짝은 dissolve로 바뀐다`() {
        val circle = card.copy(content = NodeContent.Ellipse(Size(100.0, 60.0)))
        val dissolve = morph(a = document(listOf(circle))).frame(0.4)

        val byKind = dissolve.nodes.values.groupBy { it.content::class }
        assertThat(byKind.getValue(NodeContent.Ellipse::class).single().style.opacity).isWithin(1e-9).of(0.6)
        assertThat(byKind.getValue(NodeContent.Rectangle::class).map { it.style.opacity }).contains(0.4)
    }

    @Test
    fun `일대다 짝짓기와 없는 노드는 거부한다`() {
        val many = listOf(TransitionMapping(id("card"), id("hero")), TransitionMapping(id("card"), id("onlyB")))

        assertThat(SceneMorph.create(sceneA, sceneB, many)).isEqualTo(MorphResult.AmbiguousMapping(id("card")))
        assertThat(
            SceneMorph.create(sceneA, sceneB, listOf(TransitionMapping(id("ghost"), id("hero")))),
        ).isEqualTo(MorphResult.UnknownNode(id("ghost")))
    }

    @Test
    fun `진행률은 0과 1 사이로 맞추고 원본 문서를 바꾸지 않는다`() {
        val before = sceneA
        assertThat(morph().frame(-1.0)).isEqualTo(morph().frame(0.0))
        assertThat(morph().frame(Double.NaN)).isEqualTo(morph().frame(0.0))
        assertThat(morph().frame(3.0)).isEqualTo(morph().frame(1.0))
        assertThat(sceneA).isEqualTo(before)
    }
}
