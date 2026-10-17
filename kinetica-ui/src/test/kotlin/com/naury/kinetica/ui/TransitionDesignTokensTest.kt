package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 화면 전환과 확장 전환이 디자인 언어의 모양 토큰을 따르는지 확인한다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TransitionDesignTokensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun cornerAtStart(transition: KScreenTransition, design: KineticaDesign): Float {
        val frame = KTransitionFrame()
        frame.reset(Size(400f, 800f), DENSITY, 1f)
        frame.shapes = design.shapes
        transition.enter(frame, 0f)
        return frame.cornerRadius
    }

    @Test
    fun `시트 스택 전환 모서리는 sheet 토큰, 카드 스택은 card 토큰을 따르고 기본 디자인은 예전 20dp 그대로다`() {
        assertThat(cornerAtStart(KTransitions.SheetStack, KineticaDesigns.Cupertino)).isEqualTo(20f * DENSITY)
        assertThat(cornerAtStart(KTransitions.SheetStack, KineticaDesigns.Fluent)).isEqualTo(8f * DENSITY)
        assertThat(cornerAtStart(KTransitions.SheetStack, KineticaDesigns.Brutal)).isEqualTo(0f)
        assertThat(cornerAtStart(KTransitions.CardStack, KineticaDesigns.Cupertino)).isEqualTo(20f * DENSITY)
        assertThat(cornerAtStart(KTransitions.CardStack, KineticaDesigns.Material)).isEqualTo(12f * DENSITY)
    }

    @Test
    fun `화면 전환 컨테이너는 테마의 디자인 언어 모양을 전환 프레임에 넘긴다`() {
        var seen: KineticaShapes? = null
        val probe = object : KScreenTransition() {
            override fun enter(frame: KTransitionFrame, progress: Float) {
                seen = frame.shapes
            }

            override fun exit(frame: KTransitionFrame, progress: Float) = Unit
        }
        compose.setContent {
            KineticaTheme(design = KineticaDesigns.Brutal) {
                PushNavigation(listOf("a"), {}, Modifier.fillMaxSize(), transition = probe) { Box(Modifier.fillMaxSize().testTag(it)) }
            }
        }
        compose.onNodeWithTag("a").assertExists()
        compose.runOnIdle { assertThat(seen).isEqualTo(KineticaDesigns.Brutal.shapes) }
    }

    private companion object {
        const val DENSITY = 2f
    }
}
