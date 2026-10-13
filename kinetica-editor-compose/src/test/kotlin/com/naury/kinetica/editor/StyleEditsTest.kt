package com.naury.kinetica.editor

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.CornerRadii
import com.naury.kinetica.core.scene.GradientType
import com.naury.kinetica.core.scene.LineCap
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import org.junit.Test

class StyleEditsTest {
    private val card =
        SceneNode(
            id("card"),
            NodeContent.Rectangle(Size(100.0, 60.0), cornerRadius = 12.0),
            style = NodeStyle(fill = ArgbColor(0xFF112233.toInt())),
        )
    private val star = SceneNode(id("star"), NodeContent.Polygon(Size(80.0, 80.0)))
    private val scene = document(listOf(card, star))
    private val session = (KineticaSession.create(scene) as SessionOpenResult.Opened).session
    private val editorState = EditorState(session)
    private val edits = StyleEdits(editorState)
    private val shapes = ShapeEdits(editorState)
    private val effects = EffectEdits(editorState)
    private fun node(name: String) = session.document.value.node(id(name))!!

    @Test
    fun `채우기를 그라디언트로 바꾸면 지금 색에서 투명으로 시작하고 색·각도를 고친다`() {
        assertThat(edits.setFillMode(id("card"), FillMode.Linear)).isTrue()
        assertThat(edits.setStopColor(id("card"), 1, "#FF00FF00")).isTrue()
        assertThat(edits.setAngle(id("card"), "45")).isTrue()
        assertThat(edits.setStopColor(id("card"), 5, "#FF00FF00")).isFalse()

        val gradient = node("card").style.gradient!!
        assertThat(gradient.type).isEqualTo(GradientType.Linear)
        assertThat(gradient.stops.first().color).isEqualTo(ArgbColor(0xFF112233.toInt()))
        assertThat(gradient.stops.last().color).isEqualTo(ArgbColor(0xFF00FF00.toInt()))
        assertThat(gradient.angleDegrees).isEqualTo(45.0)
        assertThat(edits.fillMode(node("card").style)).isEqualTo(FillMode.Linear)
        assertThat(edits.setFillMode(id("card"), FillMode.None)).isTrue()
        assertThat(node("card").style.fill).isNull()
    }

    @Test
    fun `그림자를 더하고 값을 고치고 빼며 잘못된 값은 거부한다`() {
        effects.addShadow(id("card"), inner = false)
        effects.addShadow(id("card"), inner = true)
        assertThat(effects.setShadow(id("card"), 1, ShadowField.Spread, "4")).isTrue()
        assertThat(effects.setShadow(id("card"), 0, ShadowField.Blur, "-3")).isFalse()
        assertThat(effects.setShadowColor(id("card"), 0, "zz")).isFalse()

        assertThat(node("card").style.shadows.map { it.inner }).containsExactly(false, true).inOrder()
        assertThat(node("card").style.shadows[1].spread).isEqualTo(4.0)
        assertThat(effects.removeShadow(id("card"), 0)).isTrue()
        assertThat(node("card").style.shadows.single().inner).isTrue()
    }

    @Test
    fun `모서리별 반지름·선·점선·흐림·별 비율`() {
        assertThat(shapes.toggleCorners(id("card"))).isTrue()
        assertThat(shapes.setCorner(id("card"), 2, "30")).isTrue()
        assertThat(edits.setStroke(id("card"), "#FF000000")).isTrue()
        assertThat(edits.toggleDash(id("card"))).isTrue()
        assertThat(effects.setBlur(id("card"), "2.5")).isTrue()
        assertThat(shapes.setInnerRatio(id("star"), "0.4")).isTrue()
        assertThat(shapes.setSides(id("star"), "2")).isFalse()
        assertThat(edits.setCap(id("star"), start = false, LineCap.Arrow)).isTrue()

        assertThat((node("card").content as NodeContent.Rectangle).corners).isEqualTo(CornerRadii(12.0, 12.0, 30.0, 12.0))
        assertThat(node("card").style.strokeWidth).isGreaterThan(0.0)
        assertThat(node("card").style.strokeDash).isNotEmpty()
        assertThat(node("card").style.blur).isEqualTo(2.5)
        assertThat((node("star").content as NodeContent.Polygon).innerRatio).isEqualTo(0.4)
        session.history.undo()
        assertThat(node("star").style.endCap).isEqualTo(LineCap.None)
    }
}
