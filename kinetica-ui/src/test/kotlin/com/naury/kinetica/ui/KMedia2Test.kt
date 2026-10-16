package com.naury.kinetica.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.LayoutDirection
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
class KMedia2Test {
    @get:Rule
    val compose = createComposeRule()

    private class Photo(private val color: Color) : Painter() {
        override val intrinsicSize: Size get() = Size(400f, 300f)

        override fun DrawScope.onDraw() {
            drawRect(color)
        }
    }

    private val photos = listOf(Photo(Color.Red), Photo(Color.Green), Photo(Color.Blue))

    private fun hasState(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private fun hasCustomAction(label: String) = SemanticsMatcher("동작 $label") { node ->
        node.config.getOrElseNullable(SemanticsActions.CustomActions) { null }?.any { it.label == label } == true
    }

    private fun setViewer(): KImageViewerState {
        lateinit var state: KImageViewerState
        compose.setContent {
            state = rememberKImageViewerState()
            KineticaScaffold { KImageViewer(state, photos) }
        }
        compose.runOnIdle { state.open(0) }
        compose.waitForIdle()
        return state
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun `사진 뷰어는 TalkBack 동작으로 넘기고 확대하며 닫기 버튼으로 닫힌다`() {
        val state = setViewer()
        compose.onNode(hasState("Photo 1 of 3")).performCustomAccessibilityActionWithLabel("Next photo")
        compose.waitForIdle()
        assertThat(state.page).isEqualTo(1)

        val page = compose.onNode(hasState("Photo 2 of 3"))
        page.assert(hasCustomAction("Previous photo")).performCustomAccessibilityActionWithLabel("Zoom in")
        compose.waitForIdle()
        compose.onNode(hasState("Photo 2 of 3, zoomed to 250%")).performCustomAccessibilityActionWithLabel("Zoom out")
        compose.waitForIdle()
        compose.onNode(hasState("Photo 2 of 3")).assert(hasCustomAction("Zoom in"))

        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitForIdle()
        assertThat(state.isOpen).isFalse()
        assertThat(state.isVisible).isFalse()
    }

    @Test
    fun `확대하면 넘겨지지 않고 두 번 탭으로 돌아오면 다시 넘겨진다`() {
        val state = setViewer()
        compose.onRoot().performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        compose.onNode(hasState("Photo 1 of 3, zoomed to 250%")).assertExists()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(state.page).isEqualTo(0)

        compose.onRoot().performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(state.page).isEqualTo(1)
    }

    @Test
    fun `확대한 사진을 가장자리까지 옮긴 뒤 한 번 더 밀면 다음 사진으로 넘어간다`() {
        val state = setViewer()
        compose.onRoot().performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(state.page).isEqualTo(0)

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(state.page).isEqualTo(1)
        compose.onNode(hasState("Photo 2 of 3")).assertExists()
    }

    @Test
    fun `끌어서 닫는 도중에 다시 열면 사진이 제자리로 돌아온다`() {
        val state = setViewer()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { swipeDown(startY = centerY, endY = bottom) }
        compose.mainClock.advanceTimeBy(FRAME_MS * 3)
        assertThat(state.isOpen).isFalse()
        compose.runOnIdle { state.open(0) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertThat(state.isVisible).isTrue()
        val bounds = compose.onNode(hasState("Photo 1 of 3")).fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertThat(bounds.center.y).isWithin(2f).of(root.center.y)
    }

    @Test
    fun `아래로 조금 끌면 돌아오고 충분히 끌면 닫힌다`() {
        val state = setViewer()
        compose.onRoot().performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 20f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, 20f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, 0.5f), delayMillis = 300)
            up()
        }
        compose.waitForIdle()
        assertThat(state.isOpen).isTrue()

        compose.onRoot().performTouchInput { swipeDown(startY = centerY, endY = bottom) }
        compose.waitForIdle()
        assertThat(state.isVisible).isFalse()
    }

    @Test
    fun `상태 없이 띄운 뷰어는 KineticaScaffold 밖에서도 창으로 뜨고 닫힘이 끝나면 알린다`() {
        var dismissed = false
        compose.setContent { KImageViewer(photos, startIndex = 2, onDismiss = { dismissed = true }) }
        compose.onNode(hasState("Photo 3 of 3")).assertExists()
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Photo viewer"),
        ).performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitForIdle()
        assertThat(dismissed).isTrue()
    }

    private fun sliderState(before: Int) = hasState("$before% before, ${100 - before}% after")

    @Test
    fun `전후 비교는 누르고 끈 위치로 손잡이가 가고 방향키와 TalkBack으로도 움직인다`() {
        compose.setContent {
            BeforeAfterSlider(
                before = { Box(Modifier.fillMaxSize().background(Color.Gray)) },
                after = { Box(Modifier.fillMaxSize().background(Color.Yellow)) },
                modifier = Modifier.size(200.dp, 150.dp).testTag("cmp"),
            )
        }
        val node = compose.onNodeWithTag("cmp")
        node.assert(sliderState(50))
        node.performTouchInput { click(percentOffset(0.8f, 0.5f)) }
        compose.waitForIdle()
        node.assert(sliderState(80))
        node.performTouchInput { swipe(percentOffset(0.8f, 0.5f), percentOffset(0.3f, 0.5f)) }
        compose.waitForIdle()
        node.assert(sliderState(30))

        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionRight) }
        node.assert(sliderState(35))
        node.performSemanticsAction(SemanticsActions.SetProgress) { it(0.9f) }
        node.assert(sliderState(90))
        // 손잡이 왼쪽(전)은 회색, 오른쪽(후)은 노란색이다.
        val pixels = node.captureToImage().toPixelMap()
        assertThat(pixels[20, pixels.height - 4].blue).isGreaterThan(0.3f)
        assertThat(pixels[pixels.width - 4, pixels.height - 4].blue).isLessThan(0.1f)
    }

    @Test
    fun `전후 비교는 RTL에서 오른쪽이 전이고 세로 스크롤을 막지 않는다`() {
        val scroll = androidx.compose.foundation.ScrollState(0)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(Modifier.height(300.dp).verticalScroll(scroll)) {
                    BeforeAfterSlider(
                        before = { Box(Modifier.fillMaxSize()) },
                        after = { Box(Modifier.fillMaxSize()) },
                        modifier = Modifier.size(200.dp, 200.dp).testTag("cmp"),
                    )
                    Spacer(Modifier.width(10.dp).height(600.dp))
                }
            }
        }
        val node = compose.onNodeWithTag("cmp")
        node.performTouchInput { click(percentOffset(0.2f, 0.5f)) }
        compose.waitForIdle()
        node.assert(sliderState(80))
        node.performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertThat(scroll.value).isGreaterThan(0)
        node.assert(sliderState(80))
    }

    @Test
    fun `결과 표시는 상태 이름을 읽고 진행 중은 불확정 진행 표시이며 성공하면 링을 성공 색으로 닫는다`() {
        compose.mainClock.autoAdvance = false
        val state = mutableStateOf(KResult.Loading)
        compose.setContent { KResultMark(state.value, Modifier.testTag("mark").background(Color.White), size = 80.dp) }
        compose.mainClock.advanceTimeBy(100)
        val node = compose.onNodeWithTag("mark")
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Loading")))
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
        node.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))

        state.value = KResult.Success
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Done")))
        node.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
        val pixels = node.captureToImage().toPixelMap()
        // 링은 지름의 70%이므로 맨 위, 맨 아래, 왼쪽 끝이 모두 칠해져 있어야 닫힌 원이다.
        val r = pixels.width / 2f * 0.7f
        val c = pixels.width / 2
        for ((x, y) in listOf(c to (c - r).toInt(), c to (c + r).toInt(), (c - r).toInt() to c)) {
            val p = pixels[x, y]
            assertThat(p.green).isGreaterThan(p.red + 0.2f)
        }
    }

    @Test
    fun `결과 다이얼로그는 진행 중에 확인을 막고 끝나면 확인으로 닫는다`() {
        val state = mutableStateOf(KResult.Loading)
        var dismissed = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KineticaScaffold { KResultDialog(true, state.value, "Paying", { dismissed++ }, message = "Wait") }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("OK").assertIsNotEnabled()
        compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Paying"),
        ).performSemanticsAction(SemanticsActions.Dismiss)
        assertThat(dismissed).isEqualTo(0)

        state.value = KResult.Failure
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithContentDescription("Failed").assertExists()
        compose.onNodeWithText("OK").assertIsEnabled().performClick()
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun `빈 화면은 제목을 제목으로 읽고 그림이 둥실거리며 애니메이션 삭제면 멈춘다`() {
        var clicked = false
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.background(Color.White).testTag("empty")) {
                KEmptyState(
                    "Nothing here",
                    message = "Try again",
                    art = { KEmptyIcon(KEmptyIconKind.EmptyInbox) },
                    action = { KButton("Retry", onClick = { clicked = true }) },
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Nothing here").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("Retry").performClick()
        assertThat(clicked).isTrue()

        val first = compose.onNodeWithTag("empty").captureToImage().toPixelMap().buffer.toList()
        compose.mainClock.advanceTimeBy(800)
        val later = compose.onNodeWithTag("empty").captureToImage().toPixelMap().buffer.toList()
        assertThat(later).isNotEqualTo(first)
    }

    @Test
    fun `애니메이션 삭제면 빈 화면 그림이 움직이지 않는다`() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent { Box(Modifier.background(Color.White).testTag("empty")) { KEmptyState("Offline") } }
            compose.mainClock.advanceTimeByFrame()
            val first = compose.onNodeWithTag("empty").captureToImage().toPixelMap().buffer.toList()
            compose.mainClock.advanceTimeBy(800)
            val later = compose.onNodeWithTag("empty").captureToImage().toPixelMap().buffer.toList()
            assertThat(later).isEqualTo(first)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test
    fun `온보딩은 다음으로 넘기고 마지막에 시작하기로 바뀌며 건너뛰기와 시작하기가 끝을 알린다`() {
        var finished = 0
        lateinit var pager: PagerState
        val pages = listOf(OnboardingPage("One", "First"), OnboardingPage("Two", "Second", background = Color.Black))
        compose.setContent {
            pager = rememberPagerState { pages.size }
            KOnboarding(pages, onFinish = { finished++ }, state = pager)
        }
        compose.onNodeWithText("One").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithContentDescription("Next").performClick()
        compose.waitForIdle()
        assertThat(pager.currentPage).isEqualTo(1)
        assertThat(finished).isEqualTo(0)

        compose.onNodeWithContentDescription("Get started").performClick()
        assertThat(finished).isEqualTo(1)
        // 마지막 페이지에서는 건너뛰기가 꺼진다.
        compose.onNodeWithText("Skip").assertIsNotEnabled()
    }

    @Test
    fun `온보딩 첫 페이지에서 건너뛰기를 누르면 끝을 알린다`() {
        var finished = false
        compose.setContent {
            KOnboarding(listOf(OnboardingPage("One", "First"), OnboardingPage("Two", "Second")), onFinish = {
                finished =
                    true
            })
        }
        compose.onNodeWithText("Skip").performClick()
        assertThat(finished).isTrue()
    }

    @Test
    fun `리소스 기본 문구는 한국어로도 있다`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.KOREAN) }
        val ko = context.createConfigurationContext(config)
        assertThat(ko.getString(R.string.kinetica_ui_viewer_photo, 2, 5)).isEqualTo("사진 5장 중 2번째")
        assertThat(ko.getString(R.string.kinetica_ui_compare_state, 30, 70)).isEqualTo("전 30%, 후 70%")
    }
}

private const val FRAME_MS = 16L
