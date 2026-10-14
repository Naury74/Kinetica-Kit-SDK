package com.naury.kinetica.editor

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.LoopEffect
import com.naury.kinetica.core.scene.LoopKind
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeEffects
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class EffectPreviewTest {
    @get:Rule
    val compose = createComposeRule()

    private val plain = SceneNode(NodeId("a"), NodeContent.Rectangle(Size(100.0, 50.0)), transform = Transform2D(translationX = 300.0))
    private val moving = plain.copy(id = NodeId("b"), effects = NodeEffects(loop = LoopEffect(LoopKind.Pulse)))
    private val document =
        SceneDocument("p", Artboard(500.0, 500.0), listOf(plain, moving).associateBy { it.id }, listOf(plain.id, moving.id))

    @Test
    fun `효과가 있는 노드만 미리보기를 보여 주고 다시 보기를 누를 수 있다`() {
        val node = mutableStateOf(plain)
        compose.setContent { EffectPreview(document, node.value, StudioTheme()) }
        compose.onNodeWithTag("kinetica-effect-preview").assertDoesNotExist()

        node.value = moving
        compose.onNodeWithTag("kinetica-effect-preview").assertExists()
        compose.onNodeWithText("Replay").performClick()
        compose.onNodeWithTag("kinetica-effect-preview").assertExists()
    }
}
