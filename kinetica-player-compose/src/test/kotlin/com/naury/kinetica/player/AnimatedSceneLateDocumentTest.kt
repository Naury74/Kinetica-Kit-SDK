package com.naury.kinetica.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneTransitionKind
import com.naury.kinetica.core.scene.SceneTransitionSpec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * 장면 키가 먼저 바뀌고 문서가 한 프레임 늦게 들어오는 경우(번들 재생기가 새 화면 상태를 받기 전 프레임). 늦게 들어온 문서로 그 장면을 기억해야
 * 다음 전환에서 나가는 장면이 이전 화면의 문서로 그려지지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnimatedSceneLateDocumentTest {
    @get:Rule
    val compose = createComposeRule()

    private val red = SceneDocument(documentId = "red", artboard = Artboard(100.0, 100.0))
    private val blue = SceneDocument(documentId = "blue", artboard = Artboard(100.0, 100.0))
    private val wipe = SceneTransitionSpec(SceneTransitionKind.Wipe, durationMs = 400, easing = EasingSpec.Linear)

    @Test
    fun `늦게 들어온 문서가 다음 전환의 나가는 장면이 된다`() {
        var key by mutableStateOf("home")
        var document by mutableStateOf(red)
        var forward by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                AnimatedScene(document, Modifier.size(100.dp).testTag("scene"), wipe, forward, key, reducedMotion = false) { scene ->
                    Box(Modifier.fillMaxSize().background(if (scene.documentId == "red") Color.Red else Color.Blue))
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        step { key = "product" }
        step { document = blue }
        compose.mainClock.advanceTimeBy(1_000)

        // 돌아갈 때도 키가 먼저 바뀌고 문서는 다음 프레임에 들어온다.
        step {
            forward = false
            key = "home"
        }
        step { document = red }
        compose.mainClock.advanceTimeBy(200)

        // 경계선이 가운데쯤 있으므로 양 끝은 서로 다른 장면이어야 한다. 나가는 장면을 옛 문서로 기억하면 둘 다 빨강이다.
        val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
        val colors = setOf(pixels[5, 50], pixels[94, 50])
        assertThat(colors).containsExactly(Color.Red, Color.Blue)
    }

    /** 시계를 멈춘 채 상태를 바꾸면 재구성을 먼저 돌리고 한 프레임 진행한다. */
    private fun step(change: () -> Unit) {
        change()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(FRAME_MS)
    }
}

private const val FRAME_MS = 16L
