package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** KSlider의 접근성 이름과 상태, 위 줄 배치, 높이와 굵기, 잘못된 범위. KDialSlider의 바뀜 기준, 값 글자 스타일, 잘못된 범위. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SliderOptionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
    private val disabled = SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)

    @Test
    fun `KSlider는 label과 stateDescription을 TalkBack에 내보낸다`() {
        compose.setContent {
            KSlider(0.25f, {}, label = "밝기", stateDescription = { "${(it * 100).toInt()} 럭스" })
        }
        compose.onNodeWithContentDescription("밝기")
            .assert(slider)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "25 럭스"))
    }

    @Test
    fun `stateDescription이 null을 돌려주거나 없으면 상태 글자를 두지 않는다`() {
        compose.setContent { KSlider(0.25f, {}, label = "밝기", stateDescription = { null }) }
        val config = compose.onNode(slider).fetchSemanticsNode().config
        assertThat(config.contains(SemanticsProperties.StateDescription)).isFalse()
        assertThat(config.contains(SemanticsProperties.ContentDescription)).isTrue()
    }

    @Test
    fun `기본 인자면 이름 없이 높이 44dp다`() {
        compose.setContent { Box(Modifier.width(300.dp)) { KSlider(0.5f, {}) } }
        val node = compose.onNode(slider)
        node.assertHeightIsEqualTo(44.dp)
        assertThat(node.fetchSemanticsNode().config.contains(SemanticsProperties.ContentDescription)).isFalse()
    }

    @Test
    fun `minHeight로 트랙 줄 높이를 정한다`() {
        compose.setContent { Box(Modifier.width(300.dp)) { KSlider(0.5f, {}, minHeight = 48.dp, trackThickness = 4.dp) } }
        compose.onNode(slider).assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun `트랙 굵기가 0이면 IllegalArgumentException으로 막는다`() {
        assertThrows(IllegalArgumentException::class.java) {
            compose.setContent { KSlider(0.5f, {}, trackThickness = 0.dp) }
        }
    }

    @Test
    fun `Top이면 라벨과 배지를 트랙 위 한 줄에 두고 배지는 끝 쪽이며 TalkBack은 슬라이더만 읽는다`() {
        var badge = Rect.Zero
        compose.setContent {
            Box(Modifier.width(300.dp).testTag("box")) {
                KSlider(
                    0.1f,
                    {},
                    label = "노출",
                    valueBadge = { Text("배지", Modifier.onGloballyPositioned { badge = it.boundsInRoot() }) },
                    badgePosition = KSliderBadgePosition.Top,
                )
            }
        }
        val track = compose.onNode(slider).getUnclippedBoundsInRoot()
        val box = compose.onNodeWithTag("box").getUnclippedBoundsInRoot()
        assertThat(track.top).isGreaterThan(box.top)
        assertThat(track.height).isEqualTo(44.dp)
        with(compose.density) {
            assertThat(badge.bottom).isAtMost(track.top.toPx())
            // 값이 앞쪽이어도 배지는 손잡이를 따르지 않고 끝에 붙는다.
            assertThat(badge.right).isWithin(1f).of(box.right.toPx())
        }
        compose.onAllNodesWithText("배지").assertCountEquals(0)
        compose.onAllNodesWithText("노출").assertCountEquals(0)
        compose.onNodeWithContentDescription("노출").assert(slider)
    }

    @Test
    fun `KSlider는 빈 범위나 유한하지 않은 범위면 예외 없이 입력을 받지 않는다`() {
        var value by mutableFloatStateOf(0.5f)
        var range by mutableStateOf(2f..2f)
        compose.setContent {
            Box(Modifier.width(300.dp)) {
                KSlider(value, { value = it }, valueRange = range, resetValue = 5f)
            }
        }
        compose.onNode(slider).assert(disabled)
        compose.onNode(slider).performTouchInput { click(Offset(width * 0.9f, centerY)) }
        compose.waitForIdle()
        assertThat(value).isEqualTo(0.5f)
        val config = compose.onNode(slider).fetchSemanticsNode().config
        assertThat(config.contains(SemanticsActions.CustomActions)).isFalse()
        compose.runOnIdle { assertThat(config[SemanticsActions.SetProgress].action!!.invoke(1f)).isFalse() }

        range = 0f..Float.POSITIVE_INFINITY
        compose.waitForIdle()
        compose.onNode(slider).assert(disabled)
    }

    @Test
    fun `KDialSlider는 changedThreshold보다 덜 바뀌면 강조색으로 칠하지 않는다`() {
        var value by mutableFloatStateOf(0.4f)
        compose.setContent { Dial(value, changedThreshold = 0.5f) }
        assertThat(accentPixels()).isEqualTo(0)
        value = 0.5f
        compose.waitForIdle()
        assertThat(accentPixels()).isGreaterThan(0)
    }

    @Test
    fun `KDialSlider는 changedThreshold가 0이면 조금만 달라도 칠한다`() {
        compose.setContent { Dial(0.1f) }
        assertThat(accentPixels()).isGreaterThan(0)
    }

    @Test
    fun `KDialSlider는 changedThreshold가 음수면 IllegalArgumentException으로 막는다`() {
        assertThrows(IllegalArgumentException::class.java) {
            compose.setContent { KDialSlider(0f, {}, "노출", changedThreshold = -1f) }
        }
    }

    @Test
    fun `KDialSlider의 valueTextStyle은 기본 스타일 위에 덮어쓰고 색은 바뀜 여부를 따른다`() {
        var style by mutableStateOf<TextStyle?>(null)
        compose.setContent { Dial(0f, valueTextStyle = style) }
        val plain = compose.onNodeWithTag(DIAL).getUnclippedBoundsInRoot().height
        assertThat(accentPixels()).isEqualTo(0)
        style = TextStyle(fontSize = 30.sp)
        compose.waitForIdle()
        assertThat(compose.onNodeWithTag(DIAL).getUnclippedBoundsInRoot().height).isGreaterThan(plain)
        assertThat(accentPixels()).isEqualTo(0)
    }

    @Test
    fun `KDialSlider는 빈 범위나 범위 밖 기준값이면 예외 없이 입력을 받지 않는다`() {
        var range by mutableStateOf(0f..10f)
        compose.setContent { KDialSlider(3f, {}, "노출", valueRange = range, resetValue = 50f) }
        compose.onNodeWithContentDescription("노출").assert(disabled)
        val config = compose.onNodeWithContentDescription("노출").fetchSemanticsNode().config
        assertThat(config.contains(SemanticsActions.SetProgress)).isFalse()

        range = Float.NaN..1f
        compose.waitForIdle()
        compose.onNodeWithContentDescription("노출").assert(disabled)
    }

    @Composable
    private fun Dial(value: Float, changedThreshold: Float = 0f, valueTextStyle: TextStyle? = null) {
        Box(Modifier.width(200.dp).background(Color.White).testTag(DIAL)) {
            KDialSlider(
                value,
                {},
                "노출",
                accent = ACCENT,
                surface = SurfaceStyles.CleanLight,
                changedThreshold = changedThreshold,
                valueTextStyle = valueTextStyle,
            )
        }
    }

    /** 다이얼에서 강조색(빨강)에 가까운 픽셀 수. */
    private fun accentPixels(): Int {
        val bitmap = compose.onNodeWithTag(DIAL).captureToImage().asAndroidBitmap()
        var count = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val c = bitmap.getPixel(x, y)
                val r = c shr 16 and 0xFF
                val g = c shr 8 and 0xFF
                val b = c and 0xFF
                if (r > 180 && g < 120 && b < 120) count++
            }
        }
        return count
    }

    private companion object {
        const val DIAL = "dial"
        val ACCENT = Color(0xFFFF0000)
    }
}
