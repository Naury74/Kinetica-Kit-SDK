package com.naury.kinetica.editor

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.GradientType
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h900dp-mdpi")
class StyleInspectorTest {
    @get:Rule
    val compose = createComposeRule()

    private val session = (KineticaSession.create(SceneDocument.empty(Artboard(600.0, 800.0))) as SessionOpenResult.Opened).session

    @Test
    fun `도형 더보기로 별을 넣고 속성 패널에서 그라디언트·안쪽 그림자를 준다`() {
        compose.setContent { KineticaEditor(session) }

        compose.onNodeWithText("More shapes").performScrollTo().performClick()
        compose.onNodeWithText("Star").performClick()
        compose.waitForIdle()
        val star = session.document.value.nodes.values.single()
        assertThat((star.content as NodeContent.Polygon).innerRatio).isLessThan(1.0)

        compose.onNodeWithText("Linear").performScrollTo().performClick()
        compose.onNodeWithText("+ Inner shadow").performScrollTo().performClick()
        compose.waitForIdle()

        val styled = session.document.value.node(star.id)!!.style
        assertThat(styled.gradient?.type).isEqualTo(GradientType.Linear)
        assertThat(styled.shadows.single().inner).isTrue()
        session.history.undo()
        assertThat(session.document.value.node(star.id)!!.style.shadows).isEmpty()
    }
}
