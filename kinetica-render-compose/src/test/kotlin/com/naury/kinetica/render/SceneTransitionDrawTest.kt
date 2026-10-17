package com.naury.kinetica.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.scene.SceneTransitionKind
import com.naury.kinetica.core.scene.SceneTransitionSpec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SceneTransitionDrawTest {
    @get:Rule
    val compose = createComposeRule()

    private var spec by mutableStateOf(SceneTransitionSpec(SceneTransitionKind.Wipe))
    private var progress by mutableFloatStateOf(0f)
    private var forward by mutableStateOf(true)
    private var rtl by mutableStateOf(false)

    private fun pixels(kind: SceneTransitionKind, p: Float, isForward: Boolean = true, isRtl: Boolean = false) = run {
        spec = SceneTransitionSpec(kind)
        progress = p
        forward = isForward
        rtl = isRtl
        compose.waitForIdle()
        compose.onNodeWithTag("t").captureToImage().toPixelMap()
    }

    private fun show() = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            Box(Modifier.size(100.dp).clipToBounds().testTag("t")) {
                val order = if (spec.incomingOnTop(forward)) listOf(false, true) else listOf(true, false)
                order.forEach { incoming ->
                    key(incoming) {
                        val role = if (incoming) SceneTransitionRole.Incoming else SceneTransitionRole.Outgoing
                        Box(
                            Modifier.fillMaxSize().sceneTransitionLayer(spec, role, forward, rtl) {
                                progress
                            }.background(if (incoming) Color.Blue else Color.Red),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `와이프와 되감기와 RTL을 그린다`() {
        show()
        val ltr = pixels(SceneTransitionKind.Wipe, 0.3f)
        assertThat(ltr[10, 50]).isEqualTo(Color.Red)
        assertThat(ltr[90, 50]).isEqualTo(Color.Blue)
        val back = pixels(SceneTransitionKind.Wipe, 0.3f, isForward = false)
        assertThat(back[10, 50]).isEqualTo(Color.Blue)
        assertThat(back[90, 50]).isEqualTo(Color.Red)
        val rtlForward = pixels(SceneTransitionKind.Wipe, 0.3f, isRtl = true)
        assertThat(rtlForward[10, 50]).isEqualTo(Color.Blue)
        assertThat(rtlForward[90, 50]).isEqualTo(Color.Red)
    }

    @Test
    fun `아이리스는 가운데부터, 디졸브는 끝에서 모두 드러난다`() {
        show()
        val iris = pixels(SceneTransitionKind.Iris, 0.3f)
        assertThat(iris[50, 50]).isEqualTo(Color.Blue)
        assertThat(iris[2, 2]).isEqualTo(Color.Red)
        assertThat(pixels(SceneTransitionKind.Dissolve, 0f)[50, 50]).isEqualTo(Color.Red)
        val half = pixels(SceneTransitionKind.Dissolve, 0.5f)
        val blue = (0 until 100 step 5).sumOf { x -> (0 until 100 step 5).count { y -> half[x, y].blue > 0.5f } }
        assertThat(blue).isIn(100..300)
        assertThat(pixels(SceneTransitionKind.Dissolve, 1f)[50, 50]).isEqualTo(Color.Blue)
    }

    @Test
    fun `모든 전환은 처음에 나가는 장면만, 끝에 들어오는 장면만 보인다`() {
        show()
        SceneTransitionKind.entries.forEach { kind ->
            assertThat(pixels(kind, 1f)[50, 50]).isEqualTo(Color.Blue)
            assertThat(pixels(kind, 0f)[50, 50]).isEqualTo(Color.Red)
        }
    }
}
