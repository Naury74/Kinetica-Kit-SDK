package com.naury.kinetica.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
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
class Effects2Test {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `물결 왜곡은 클릭을 막지 않고 물결은 4개까지만 겹친다`() {
        var clicks = 0
        compose.setContent {
            Box(Modifier.size(200.dp).testTag("ripple").rippleDistortion().clickable { clicks++ }.background(Color.DarkGray))
        }
        repeat(5) { compose.onNodeWithTag("ripple").performClick() }
        compose.waitForIdle()
        assertThat(clicks).isEqualTo(5)

        val waves = RippleWaves()
        repeat(6) { waves.add(Offset(it * 10f, 0f)) }
        waves.tick(1f)
        assertThat(waves.activeCount(duration = 1.4f)).isEqualTo(4)
        waves.tick(3f)
        assertThat(waves.activeCount(duration = 1.4f)).isEqualTo(0)
        // 가장 오래된 물결부터 지우므로 마지막 네 번 누른 자리가 남는다.
        assertThat(waves.x.sorted()).containsExactly(20f, 30f, 40f, 50f).inOrder()
    }

    @Test
    fun `물결 왜곡은 누를 때마다 부르는 쪽을 다시 구성하지 않는다`() {
        var compositions = 0
        compose.setContent {
            compositions++
            Box(Modifier.size(200.dp).testTag("ripple").rippleDistortion())
        }
        compose.waitForIdle()
        val before = compositions
        repeat(3) { compose.onNodeWithTag("ripple").performClick() }
        compose.waitForIdle()
        assertThat(compositions).isEqualTo(before)
    }

    @Test
    fun `입자 배경은 프리셋마다 그려지고 화면 읽기에 나타나지 않는다`() {
        var preset by mutableStateOf(ParticlePreset.Snow)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(200.dp).background(Color.Black).testTag("field")) { ParticleField(preset, Modifier.testTag("particles")) }
        }
        for (p in ParticlePreset.entries.filter { it != ParticlePreset.Fireworks }) {
            preset = p
            compose.mainClock.advanceTimeBy(300)
            assertThat(litPixels("field")).isGreaterThan(0)
        }
        val node = compose.onNodeWithTag("particles").fetchSemanticsNode()
        assertThat(node.config.getOrNull(SemanticsProperties.ContentDescription)).isNull()
    }

    @Test
    fun `불꽃놀이는 코드로 터뜨리고 넘치는 요청은 버린다`() {
        lateinit var state: ParticleFieldState
        compose.mainClock.autoAdvance = false
        compose.setContent {
            state = rememberParticleFieldState(autoLaunch = false)
            Box(Modifier.size(200.dp).background(Color.Black).testTag("field")) { ParticleField(ParticlePreset.Fireworks, state = state) }
        }
        compose.mainClock.advanceTimeBy(200)
        assertThat(litPixels("field")).isEqualTo(0)
        state.launch(Offset(100f, 100f))
        repeat(4) {
            compose.mainClock.advanceTimeBy(100)
            compose.onNodeWithTag("field").captureToImage()
        }
        assertThat(litPixels("field")).isGreaterThan(0)

        val queue = ParticleFieldState(autoLaunch = true)
        repeat(12) { queue.launch() }
        assertThat(queue.queued).isEqualTo(8)
    }

    @Test
    fun `애니메이션 삭제면 입자가 멈춘 그림으로 남는다`() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent {
                Box(Modifier.size(160.dp).background(Color.Black).testTag("field")) { ParticleField(ParticlePreset.Snow) }
            }
            compose.mainClock.advanceTimeByFrame()
            val first = compose.onNodeWithTag("field").captureToImage().toPixelMap().buffer.toList()
            compose.mainClock.advanceTimeBy(800)
            val later = compose.onNodeWithTag("field").captureToImage().toPixelMap().buffer.toList()
            assertThat(later).isEqualTo(first)
            assertThat(first.count { it != Color.Black.toArgbInt() }).isGreaterThan(0)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `뒤집기 카드는 면을 바꾸고 화면 읽기에 면과 뒤집기 동작을 준다`() {
        var flipped by mutableStateOf(false)
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            FlipCard(
                flipped,
                {
                    changes += it
                    flipped = it
                },
                Modifier.size(240.dp, 150.dp).testTag("card"),
                front = { Box(Modifier.background(Color.Blue)) { BasicText("앞면 글자") } },
                back = { Box(Modifier.background(Color.Red)) { BasicText("뒷면 글자") } },
            )
        }
        compose.onNodeWithText("앞면 글자", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("card").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Front side"))

        compose.onNode(hasFlipAction("Flip")).performCustomAccessibilityActionWithLabel("Flip")
        compose.waitForIdle()
        assertThat(changes).containsExactly(true)
        compose.onNodeWithText("뒷면 글자", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("앞면 글자", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("card").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Back side"))

        compose.onNodeWithTag("card").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(changes).containsExactly(true, false).inOrder()
        compose.onNodeWithText("앞면 글자", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `두 면 유지를 켜면 뒤집었다 돌아와도 앞면 상태가 남고 숨은 면은 화면 읽기에 없다`() {
        var flipped by mutableStateOf(false)
        compose.setContent {
            FlipCard(flipped, { flipped = it }, Modifier.size(240.dp, 150.dp), keepBothFaces = true, front = {
                var count by remember { mutableIntStateOf(0) }
                BasicText("앞 $count", Modifier.clickable { count++ })
            }, back = { BasicText("뒷면 글자") })
        }
        compose.onNodeWithText("앞 0", useUnmergedTree = true).performClick()
        // 화면 읽기가 쓰는 병합 트리에는 보이는 면 글자만 있다.
        compose.onNodeWithText("뒷면 글자").assertDoesNotExist()

        compose.runOnIdle { flipped = true }
        compose.waitForIdle()
        compose.onNodeWithText("앞 1").assertDoesNotExist()
        compose.onNodeWithText("뒷면 글자").assertExists()

        compose.runOnIdle { flipped = false }
        compose.waitForIdle()
        compose.onNodeWithText("앞 1").assertExists()
    }

    @Test
    fun `끌어서 뒤집어도 호스트가 받아들이지 않으면 원래 면으로 돌아간다`() {
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            FlipCard(
                false,
                { changes += it },
                Modifier.size(240.dp, 150.dp).testTag("card"),
                front = { BasicText("앞면 글자") },
                back = { BasicText("뒷면 글자") },
            )
        }
        compose.onNodeWithTag("card").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(changes).containsExactly(true)
        compose.onNodeWithText("앞면 글자", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("뒷면 글자", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `뒤집기 각도 계산은 가까운 면을 고른다`() {
        assertThat(isBackAngle(0f)).isFalse()
        assertThat(isBackAngle(91f)).isTrue()
        assertThat(isBackAngle(269f)).isTrue()
        assertThat(isBackAngle(-100f)).isTrue()
        assertThat(isBackAngle(360f)).isFalse()
        assertThat(nearestFace(30f, back = true)).isEqualTo(180f)
        assertThat(nearestFace(-30f, back = true)).isEqualTo(-180f)
        assertThat(nearestFace(200f, back = false)).isEqualTo(360f)
        assertThat(nearestFace(170f, back = false)).isEqualTo(0f)
        assertThat(nearestFace(540f, back = true)).isEqualTo(540f)
    }

    @Test
    fun `액체 버튼은 버튼 역할로 눌리고 쉴 때 모양은 요소 크기에 맞다`() {
        var clicks = 0
        compose.setContent { KBlobButton("시작하기", onClick = { clicks++ }, Modifier.testTag("blob")) }
        compose.onNodeWithTag("blob")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertThat(clicks).isEqualTo(1)

        val path = BlobOutline(Size(200f, 60f), corner = 999f).build(BlobPose(0f, Offset.Zero, 0f, 0f))
        val bounds = path.getBounds()
        assertThat(bounds.left).isWithin(1f).of(0f)
        assertThat(bounds.top).isWithin(1f).of(0f)
        assertThat(bounds.right).isWithin(1f).of(200f)
        assertThat(bounds.bottom).isWithin(1f).of(60f)
        // 누르면 가로로 퍼지고 세로로 눌린다.
        val pressed = BlobOutline(Size(200f, 60f), corner = 999f).build(BlobPose(1f, Offset(100f, 30f), 0f, 0f)).getBounds()
        assertThat(pressed.width).isGreaterThan(200f)
        assertThat(pressed.height).isLessThan(60f)
    }

    @Test
    fun `방울 그룹은 표시한 요소 자리에 방울을 그린다`() {
        compose.setContent {
            Box(Modifier.background(Color.White).testTag("group")) {
                KBlobGroup(Modifier.padding(20.dp), color = Color.Red) {
                    Row {
                        Box(Modifier.size(40.dp).blob())
                        Box(Modifier.size(40.dp))
                        Box(Modifier.size(40.dp).blob())
                    }
                }
            }
        }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("group").captureToImage().toPixelMap()
        val unit = pixels.width / 160f
        val first = pixels[(40 * unit).toInt(), (40 * unit).toInt()]
        val gap = pixels[(80 * unit).toInt(), (40 * unit).toInt()]
        assertThat(first.red).isGreaterThan(0.8f)
        assertThat(first.green).isLessThan(0.3f)
        assertThat(gap.green).isGreaterThan(0.8f)
    }

    @Test
    fun `홀로그램 기울기는 가로 화면에서 화면 축으로 돌린다`() {
        fun tilt(x: Float, y: Float, rotation: Int) = screenTilt(x, y, rotation).let { listOf(it.x + 0f, it.y + 0f) }
        assertThat(tilt(5f, 0f, android.view.Surface.ROTATION_90)).containsExactly(0f, 5f).inOrder()
        assertThat(tilt(0f, 5f, android.view.Surface.ROTATION_270)).containsExactly(5f, 0f).inOrder()
        assertThat(tilt(1f, 2f, android.view.Surface.ROTATION_180)).containsExactly(-1f, -2f).inOrder()
        assertThat(tilt(1f, 2f, android.view.Surface.ROTATION_0)).containsExactly(1f, 2f).inOrder()
    }

    @Test
    fun `홀로그램은 클릭을 막지 않고 카드 위에 광택을 더한다`() {
        var clicks = 0
        var holo by mutableStateOf(false)
        compose.setContent {
            val effect = if (holo) Modifier.holographic(RoundedCornerShape(12.dp)) else Modifier
            Box(
                Modifier.size(160.dp, 220.dp).testTag("card").tiltOnPress().then(effect).background(Color(0xFF22265A)).clickable {
                    clicks++
                },
            )
        }
        val plain = compose.onNodeWithTag("card").captureToImage().toPixelMap().buffer.toList()
        holo = true
        compose.waitForIdle()
        val shiny = compose.onNodeWithTag("card").captureToImage().toPixelMap().buffer.toList()
        assertThat(shiny).isNotEqualTo(plain)
        compose.onNodeWithTag("card").assertIsDisplayed().performClick()
        assertThat(clicks).isEqualTo(1)
    }

    private fun litPixels(tag: String): Int {
        val map = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        var lit = 0
        for (x in 0 until map.width) {
            for (y in 0 until map.height) {
                val c = map[x, y]
                if (c.red + c.green + c.blue > LIT) lit++
            }
        }
        return lit
    }

    private fun hasFlipAction(label: String) = SemanticsMatcher("동작 $label") { node ->
        node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
    }

    private fun Color.toArgbInt(): Int = android.graphics.Color.argb(alpha, red, green, blue)

    private companion object {
        const val LIT = 0.3f
    }
}
