package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
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
class InputPolishTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `입력란은 포커스를 받고 글자가 있을 때만 지우기 버튼을 보이고 누르면 비운다`() {
        var text by mutableStateOf("민지")
        compose.setContent { KTextField(text, { text = it }, "이름", Modifier.testTag("field")) }
        compose.onNodeWithContentDescription("Clear text").assertDoesNotExist()
        compose.onNodeWithTag("field").performClick()
        compose.onNodeWithContentDescription("Clear text").assertIsDisplayed().performClick()
        assertThat(text).isEmpty()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Clear text").assertDoesNotExist()
    }

    @Test
    fun `입력란은 도움말을 보이다 오류가 생기면 오류 문구로 바꾼다`() {
        var error by mutableStateOf<String?>(null)
        compose.setContent { KTextField("a", {}, "이메일", supportingText = "회사 메일을 적어 주세요", error = error) }
        compose.onNodeWithText("회사 메일을 적어 주세요").assertExists()
        error = "형식이 아닙니다"
        compose.onNodeWithText("형식이 아닙니다").assertExists()
        compose.onNodeWithText("회사 메일을 적어 주세요").assertDoesNotExist()
    }

    @Test
    fun `검색창은 showCancel이 false면 포커스를 받아도 취소 버튼을 띄우지 않는다`() {
        compose.setContent { KSearchBar("", {}, {}, Modifier.testTag("search"), showCancel = false) }
        compose.onNodeWithTag("search").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun `검색창은 포커스를 받으면 취소 버튼이 들어오고 누르면 검색어를 비운다`() {
        var query by mutableStateOf("카페")
        compose.setContent { KSearchBar(query, { query = it }, {}, Modifier.testTag("search")) }
        compose.onNodeWithTag("search").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Cancel").performClick()
        assertThat(query).isEmpty()
    }

    @Test
    @Config(qualifiers = "w1000dp-h800dp")
    fun `넓은 화면의 바텀 시트는 최대 폭을 넘지 않고 가운데에 놓인다`() {
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize()) {
                KSheet(true, {}) { Box(Modifier.fillMaxWidth().height(200.dp).testTag("body")) }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val body = compose.onNodeWithTag("body").getUnclippedBoundsInRoot()
        assertThat((body.right - body.left).value).isAtMost(KSheetDefaults.MaxWidth.value)
        val leftGap = body.left.value
        val rightGap = (root.right - body.right).value
        assertThat(leftGap).isWithin(1f).of(rightGap)
    }

    @Test
    fun `여러 높이 시트는 폴더블을 펴서 영역 크기가 바뀌어도 같은 높이 단계에 머문다`() {
        var big by mutableStateOf(false)
        lateinit var state: KDetentSheetState
        compose.setContent {
            state = rememberKDetentSheetState(initialDetent = Detent.Fraction(0.5f))
            Box(Modifier.size(if (big) 700.dp else 360.dp, if (big) 800.dp else 640.dp)) {
                KDetentSheet(state) { BasicText("목록") }
            }
        }
        compose.waitForIdle()
        big = true
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(state.currentDetent).isEqualTo(Detent.Fraction(0.5f))
        assertThat(state.isSettled).isTrue()
    }

    @Test
    fun `말풍선은 상태 표시줄과 내비게이션 바 영역을 피해 놓인다`() {
        val safe = IntRect(0, 80, 0, 120)
        val spacing = TooltipSpacing(margin = 10f, gap = 8f, arrowInset = 20f, safe = safe)
        // 화면 맨 위 요소의 위쪽에는 상태 표시줄 때문에 자리가 없으므로 아래에 놓는다.
        val top =
            placeTooltip(
                Rect(Offset(100f, 90f), Offset(200f, 130f)),
                IntSize(1000, 2000),
                IntSize(300, 100),
                KTooltipPlacement.Above,
                spacing,
            )
        assertThat(top.above).isFalse()
        // 맨 아래 요소의 말풍선은 내비게이션 바 위에서 멈춘다.
        val bottom =
            placeTooltip(
                Rect(Offset(100f, 1950f), Offset(200f, 1990f)),
                IntSize(1000, 2000),
                IntSize(300, 100),
                KTooltipPlacement.Below,
                spacing,
            )
        assertThat(bottom.y + 100).isAtMost(2000 - 120 - 10)
    }
}
