package com.naury.kinetica.showcase

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 창이 넓어지고 좁아지거나(폴더블 접기·펴기) 액티비티가 다시 만들어져도 쇼케이스 화면 상태가 남는지 본다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-mdpi")
class WideNarrowStateTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `두 칸의 목록 칸 여백은 창 폭이 아니라 칸 폭을 따른다`() {
        var window = 0.dp
        var pane = 0.dp
        compose.setContent {
            window = readingPadding(16.dp)
            Box(Modifier.width(360.dp)) { ReadingContainer { pane = readingPadding(16.dp) } }
        }
        compose.waitForIdle()
        assertThat(window).isEqualTo(320.dp)
        assertThat(pane).isEqualTo(16.dp)
    }

    @Test
    fun `메일을 고른 채 넓은 창과 좁은 창을 오가도 본문이 남고 두 칸에서는 뒤로 버튼이 없다`() {
        var size by mutableStateOf(DpSize(400.dp, 800.dp))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) { MailApp {} }
        }
        compose.waitForIdle()
        // 처음 열면 코치 마크가 뜬다. 기기 언어에 따라 문구가 다르다.
        listOf("건너뛰기", "Skip").firstOrNull { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
            ?.let { compose.onNodeWithText(it).performClick() }
        compose.waitForIdle()
        compose.onNodeWithText("주말에 등산 갈래?").performClick()
        compose.waitForIdle()
        assertThat(backButtons()).isEqualTo(1)

        size = DpSize(900.dp, 800.dp)
        compose.waitForIdle()
        assertThat(compose.onAllNodesWithText("주말에 등산 갈래?").fetchSemanticsNodes()).hasSize(2)
        assertThat(backButtons()).isEqualTo(0)

        size = DpSize(400.dp, 800.dp)
        compose.waitForIdle()
        assertThat(backButtons()).isEqualTo(1)
    }

    @Test
    fun `글래스 데모는 다시 만들어져도 열린 다이얼로그를 유지한다`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { GlassDemo {} }
        compose.waitForIdle()
        compose.onNodeWithText("팝업").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("주문을 보낼까요?").assertExists()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithText("주문을 보낼까요?").assertExists()
    }

    @Test
    fun `색과 범위 값은 저장했다가 그대로 되살린다`() {
        val restoration = StateRestorationTester(compose)
        var color by mutableStateOf(Color.Unspecified)
        var range by mutableStateOf(0f..0f)
        var setColor: (Color) -> Unit = {}
        var setRange: (ClosedFloatingPointRange<Float>) -> Unit = {}
        restoration.setContent {
            var c by rememberSaveable(saver = colorStateSaver) { mutableStateOf(Color(0xFF5B6CFF)) }
            var r by rememberSaveable(saver = rangeStateSaver) { mutableStateOf(100f..150f) }
            color = c
            range = r
            setColor = { c = it }
            setRange = { r = it }
        }
        compose.runOnIdle {
            setColor(Color(0.2f, 0.4f, 0.6f, 0.8f))
            setRange(60f..170f)
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertThat(color).isEqualTo(Color(0.2f, 0.4f, 0.6f, 0.8f))
        assertThat(range).isEqualTo(60f..170f)
    }

    @Test
    fun `넓은 창에서는 데모 카드를 두 열로 놓는다`() {
        compose.setContent { MotionDemo {} }
        compose.waitForIdle()
        val glow = compose.onNodeWithText("글로우 테두리").getUnclippedBoundsInRoot()
        val jelly = compose.onNodeWithText("젤리 버튼과 자석 버튼").getUnclippedBoundsInRoot()
        assertThat(jelly.top).isEqualTo(glow.top)
        assertThat(jelly.left).isGreaterThan(glow.left)
    }

    private fun backButtons(): Int = compose.onAllNodesWithContentDescription("뒤로").fetchSemanticsNodes().size
}
