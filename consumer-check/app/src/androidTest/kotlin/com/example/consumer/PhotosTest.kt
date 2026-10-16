package com.example.consumer

import android.content.pm.ActivityInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.TestSupport.back
import com.example.consumer.m3.M3EdgeActivity
import com.example.consumer.m3.Routes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** 시나리오 6: 사진 격자에서 KImageViewer(창 모드)를 열고, 세로 스크롤 안 BeforeAfterSlider를 쓴다. */
@RunWith(AndroidJUnit4::class)
class PhotosTest {
    @get:Rule
    val rule = createAndroidComposeRule<M3EdgeActivity>()

    @After
    fun restore() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    private fun roots() = rule.onAllNodes(isRoot()).fetchSemanticsNodes().size

    private fun openPhoto(index: Int) {
        rule.openExtra(Routes.PHOTOS)
        rule.waitTag("photoGrid")
        // 넓은 화면에서는 칸이 커서 뒤쪽 썸네일이 아직 만들어지지 않았을 수 있다.
        rule.onNodeWithTag("photoGrid").performScrollToIndex(index)
        rule.waitTag("thumb$index")
        rule.onNodeWithTag("thumb$index").performClick()
        rule.waitText("뷰어 열림 $index")
        Thread.sleep(TestSupport.SETTLE_MS)
        rule.waitForIdle()
    }

    @Test
    fun thumbnailOpensWindowViewerAndBackClosesBeforeNavHost() {
        val before = roots()
        openPhoto(4)
        assertTrue("Scaffold 밖 뷰어는 별도 창(루트)으로 떠야 한다: $before → ${roots()}", roots() > before)
        back(rule)
        rule.waitText("뷰어 닫힘")
        assertTrue("닫힌 뒤 뷰어 창이 남았다", rule.becomes(3_000) { roots() == before })
        rule.onNodeWithTag("photoGrid").assertExistsCompat()
        back(rule)
        rule.waitTag("lastEvent")
    }

    @Test
    fun swipeChangesPageAndClosesOnThatThumbnail() {
        openPhoto(2)
        // 뷰어 창의 루트를 왼쪽으로 밀면 다음 사진이다.
        rule.onAllNodes(isRoot())[roots() - 1].performTouchInput { swipeLeft(startX = width * 0.85f, endX = width * 0.15f, durationMillis = 250) }
        rule.waitText("뷰어 열림 3")
        back(rule)
        rule.waitText("뷰어 닫힘")
    }

    @Test
    fun rotationKeepsViewerOpenOnSamePage() {
        openPhoto(7)
        rotateAndSettle(rule, rule.activityRule.scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        rule.waitText("뷰어 열림 7", 8_000)
        assertTrue("회전 뒤 뷰어 창이 사라졌다", roots() >= 2)
        back(rule)
        rule.waitText("뷰어 닫힘")
        rotateAndSettle(rule, rule.activityRule.scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        rule.waitTag("photoGrid", 8_000)
    }

    private fun scrollValue(): Int = Regex("스크롤 (\\d+)").find(
        rule.onNodeWithTag("compareScroll").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(),
    )!!.groupValues[1].toInt()

    /** 호스트가 들고 있는 손잡이 위치(0..1). 화면 위 글자에서 읽는다. */
    private fun split(): Float = Regex("위치 (\\d+)").find(
        rule.onNodeWithTag("compareScroll").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(),
    )!!.groupValues[1].toInt() / 100f

    @Test
    fun compareSliderInsideVerticalScroll() {
        rule.openExtra(Routes.COMPARE)
        rule.waitTag("compareSlider")
        rule.onNodeWithTag("compareSlider").performScrollTo()
        rule.waitForIdle()
        val scroll0 = scrollValue()
        assertEquals(0.5f, split(), 0.01f)

        // 가로로 끌면 손잡이만 움직이고 바깥 스크롤은 그대로다.
        rule.onNodeWithTag("compareSlider").performTouchInput {
            down(Offset(width * 0.5f, height * 0.5f))
            repeat(10) { moveBy(Offset(width * 0.035f, 0f), delayMillis = 16) }
            up()
        }
        rule.waitForIdle()
        val moved = split()
        assertTrue("가로로 끌었는데 손잡이가 움직이지 않았다: $moved", moved > 0.75f)
        assertEquals("가로로 끌었는데 바깥이 스크롤됐다", scroll0, scrollValue())

        // 슬라이더 위에서 시작한 세로 끌기는 바깥 스크롤로 간다.
        rule.onNodeWithTag("compareSlider").performTouchInput { swipeUp(startY = height * 0.9f, endY = height * 0.1f, durationMillis = 300) }
        rule.waitForIdle()
        assertTrue("슬라이더 위 세로 끌기가 바깥을 스크롤하지 않았다", scrollValue() > scroll0 + 50)
        assertTrue("세로 끌기에 손잡이가 움직였다", abs(split() - moved) < 0.02f)

        // 호스트가 위치를 바꾸면 슬라이더가 따른다.
        rule.onNodeWithTag("compareReset").performClick()
        rule.waitForIdle()
        assertEquals(0.2f, split(), 0.01f)
        val info = rule.onNodeWithTag("compareSlider").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0.2f, info.current, 0.01f)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()
}
