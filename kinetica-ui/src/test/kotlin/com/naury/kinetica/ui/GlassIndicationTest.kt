package com.naury.kinetica.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GlassIndicationTest {
    @get:Rule
    val compose = createComposeRule()

    private fun centerColor(tag: String): Color {
        val map = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return map[map.width / 2, map.height / 2]
    }

    private fun cornerColor(tag: String): Color = compose.onNodeWithTag(tag).captureToImage().toPixelMap()[1, 1]

    @Test
    fun `빠르게 탭해도 눌림 표시가 잠깐 보였다가 사라지고 모양 밖은 칠하지 않는다`() {
        var clicks = 0
        compose.setContent {
            Box(
                Modifier
                    .testTag("target")
                    .size(80.dp)
                    .background(Color.White)
                    .glassClickable(CircleShape) { clicks++ },
            )
        }
        val idle = centerColor("target")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("target").performTouchInput {
            down(center)
            up()
        }
        compose.mainClock.advanceTimeBy(FRAME_MS * 4)
        val pressed = centerColor("target")
        assertThat(pressed).isNotEqualTo(idle)
        assertThat(pressed.red).isLessThan(idle.red)
        // 원 모양으로 잘리므로 모서리는 그대로다.
        assertThat(cornerColor("target")).isEqualTo(Color.White)

        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertThat(centerColor("target")).isEqualTo(idle)
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `밝은 글자색 위에서는 자동으로 흰 오버레이를 쓴다`() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalGlassContentColor provides Color.White) {
                Box(
                    Modifier
                        .testTag("target")
                        .size(80.dp)
                        .background(Color.Black)
                        .glassClickable {},
                )
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("target").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(FRAME_MS * 8)
        assertThat(centerColor("target").red).isGreaterThan(0f)
    }

    @Test
    fun `GlassTheme과 GlassScaffold는 LocalIndication을 GlassIndication으로 바꾸고 null이면 바깥 값을 둔다`() {
        val outer = GlassIndication(CircleShape, Color.Red)
        var inTheme: Indication? = null
        var keptInTheme: Indication? = null
        var inScaffold: Indication? = null
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalIndication provides outer) {
                GlassTheme { inTheme = LocalIndication.current }
                GlassTheme(indication = null) { keptInTheme = LocalIndication.current }
                GlassScaffold { inScaffold = LocalIndication.current }
            }
        }
        compose.waitForIdle()
        assertThat(inTheme).isEqualTo(GlassIndication())
        assertThat(keptInTheme).isSameInstanceAs(outer)
        assertThat(inScaffold).isEqualTo(GlassIndication())
    }

    @Test
    fun `glassClickable은 역할과 동작 이름을 알린다`() {
        var clicked = false
        compose.setContent {
            Box(Modifier.testTag("target").size(48.dp).glassClickable(CircleShape, onClickLabel = "열기") { clicked = true })
        }
        compose.onNodeWithTag("target")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher("동작 이름") { it.config[SemanticsActions.OnClick].label == "열기" })
            .performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun `같은 값으로 만든 GlassIndication은 같다`() {
        assertThat(GlassIndication(CircleShape)).isEqualTo(GlassIndication(CircleShape))
        assertThat(GlassIndication(CircleShape).hashCode()).isEqualTo(GlassIndication(CircleShape).hashCode())
        assertThat(GlassIndication(CircleShape)).isNotEqualTo(GlassIndication(CircleShape, pressedAlpha = 0.2f))
    }

    private companion object {
        const val FRAME_MS = 16L
        const val SETTLE_MS = 1_000L
    }
}
