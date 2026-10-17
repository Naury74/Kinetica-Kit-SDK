package com.example.consumer

import androidx.activity.BackEventCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.recent.DesignHostActivity
import com.example.consumer.recent.IslandKeypadHostActivity
import com.example.consumer.recent.NavigationHostActivity
import com.example.consumer.recent.SceneBundleHostActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** 시나리오 (a): Material 3 앱 안에서 디자인 언어를 바꾸면 Kinetica 컴포넌트 형태만 바뀌고, 직접 넘긴 shape는 그대로다. */
@RunWith(AndroidJUnit4::class)
class DesignHostTest {
    @get:Rule
    val rule = createAndroidComposeRule<DesignHostActivity>()

    /** 버튼 왼쪽 위 모서리 픽셀이 바탕(흰색)과 다른지. 둥근 버튼은 모서리가 비어 흰색이다. */
    private fun cornerFilled(tag: String): Boolean {
        val pixels = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
        val corner = pixels[1, 1]
        return corner.distanceTo(Color.White) > CORNER_TOLERANCE
    }

    @Test
    fun defaultDesignKeepsCapsuleAndBrutalSquaresButOverrideWins() {
        rule.onNodeWithText("형태 Shadow").assertExists()
        assertTrue("기본 디자인 버튼은 캡슐이라 모서리가 비어야 한다", !cornerFilled("button"))
        assertTrue("shape를 직접 넘긴 버튼은 각져야 한다", cornerFilled("square"))

        rule.onNodeWithTag("pick_Brutal").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("형태 Offset").assertExists()
        assertTrue("Brutal 버튼은 각진 모서리여야 한다", cornerFilled("button"))
        assertTrue(cornerFilled("square"))

        rule.onNodeWithTag("pick_Fluent").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("형태 Border").assertExists()
        rule.onNodeWithTag("avatar").assertExists()
        rule.onNodeWithContentDescription("장바구니").assertExists()
        rule.onNodeWithText("+12,000원").assertExists()
    }

    private fun Color.distanceTo(other: Color): Float = abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue)

    private companion object {
        const val CORNER_TOLERANCE = 0.15f
    }
}

/** 시나리오 (b): PushNavigation(CardStack), 예측 뒤로 가기 취소, 탭 전환, KExpandHost(listRow), NavHost + KNavTransitions. */
@RunWith(AndroidJUnit4::class)
class NavigationHostTest {
    @get:Rule
    val rule = createAndroidComposeRule<NavigationHostActivity>()

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun pushTabsExpandAndPredictiveBackCancel() {
        rule.onNodeWithTag("openDetail").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("page_detail").assertExists()

        // 예측 뒤로 가기를 절반쯤 끌다가 취소하면 상세 화면이 제자리로 완전히 돌아와야 한다.
        val dispatcher = rule.activity.onBackPressedDispatcher
        rule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(200f, 500f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        rule.waitForIdle()
        rule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        rule.waitForIdle()
        Thread.sleep(TestSupport.SETTLE_MS)
        rule.waitForIdle()
        val left = rule.onNodeWithTag("page_detail").fetchSemanticsNode().boundsInRoot.left
        assertEquals("취소 뒤 상세 화면이 반쯤 멈춰 있으면 안 된다", 0f, left, 1f)

        back()
        rule.onNodeWithTag("page_list").assertExists()
        rule.onNodeWithTag("page_detail").assertDoesNotExist()

        rule.onNodeWithText("보낸편지함").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tab_1").assertExists()
        rule.onNodeWithTag("tab_0").assertDoesNotExist()

        rule.onNodeWithTag("mailRow").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("expanded_mail").assertExists()
        back()
        rule.onNodeWithTag("expanded_mail").assertDoesNotExist()
    }

    @Test
    fun navHostWithKNavTransitions() {
        rule.onNodeWithTag("openNav").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("navA").assertExists()
        rule.onNodeWithTag("navNext").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("navB").assertExists()
        rule.onNodeWithTag("navA").assertDoesNotExist()
        // NavHost의 뒤로 가기 콜백이 PushNavigation보다 나중에 등록되어 먼저 받는다.
        back()
        rule.onNodeWithTag("navA").assertExists()
        back()
        rule.onNodeWithTag("page_list").assertExists()
    }
}

/** 시나리오 (c): 서버 번들 장면 두 개를 JSON transition으로 오가고, 장면 안 동작으로 앞뒤로 간다. */
@RunWith(AndroidJUnit4::class)
class SceneBundleHostTest {
    @get:Rule
    val rule = createAndroidComposeRule<SceneBundleHostActivity>()

    @Test
    fun scenesSwitchWithTransitionBothWays() {
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasContentDescription("상세 장면 열기")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasContentDescription("상세 장면 열기")).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("장면 detail").assertExists()
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasContentDescription("상세 장면 열기")).fetchSemanticsNodes().isEmpty() }

        rule.onNode(hasContentDescription("홈 장면으로")).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("장면 home").assertExists()
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasContentDescription("홈 장면으로")).fetchSemanticsNodes().isEmpty() }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}

/** 시나리오 (d): KIslandState.update, 전화 키패드의 0 길게 누르기(+), KIcons. */
@RunWith(AndroidJUnit4::class)
class IslandKeypadHostTest {
    @get:Rule
    val rule = createAndroidComposeRule<IslandKeypadHostActivity>()

    @Test
    fun islandUpdateAndPhoneKeypad() {
        rule.onNodeWithTag("updateIsland").performClick()
        rule.onNodeWithText("update false").assertExists()
        rule.onNodeWithTag("showIsland").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("updateIsland").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("update true").assertExists()

        // 숫자 키는 TalkBack 이름이 숫자 그대로다. 0을 길게 누르면 '+'가 들어간다.
        rule.onNodeWithContentDescription("1").performClick()
        rule.onNodeWithContentDescription("0").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag("number").assertExists()
        rule.onNodeWithText("1+").assertExists()
        rule.onNodeWithContentDescription("통화 끊기").assertExists()
    }
}
