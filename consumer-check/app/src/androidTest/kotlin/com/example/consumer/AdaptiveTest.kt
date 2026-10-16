package com.example.consumer

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.TestSupport.back
import com.example.consumer.m3.M3EdgeActivity
import com.example.consumer.m3.MailScreen
import com.example.consumer.m3.Routes
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 시나리오 7: 호스트 앱 안에서 GlassListDetail, GlassAdaptiveNavigation, GlassFloatingTabBar를 화면 회전과 함께 쓴다. */
@RunWith(AndroidJUnit4::class)
class AdaptiveAppTest {
    @get:Rule
    val rule = createAndroidComposeRule<M3EdgeActivity>()

    @After
    fun restore() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    private fun rotate(orientation: Int) = rotateAndSettle(rule, rule.activityRule.scenario, orientation)

    private fun screenWidthDp(): Int = rule.activity.resources.configuration.screenWidthDp

    private fun narrow(): Boolean = screenWidthDp() < 600

    /** 지금 폭에 맞는 배치인지 본다. 좁으면 상세만, 넓으면 목록과 상세가 함께 보인다. */
    private fun assertPanesFor(body: String) {
        rule.waitTag("mailBody", 8_000)
        rule.onNodeWithTag("mailBody").assertTextContains(body)
        if (narrow()) {
            assertTrue("좁은 화면(${screenWidthDp()}dp)에서 상세를 연 뒤에도 목록이 보인다", rule.becomes { rule.onAllNodesWithTag("mailList").fetchSemanticsNodes().isEmpty() })
        } else {
            rule.waitTag("mailList", 8_000)
            rule.onNodeWithTag("mailList").assertIsDisplayed()
        }
    }

    private fun opposite(): Int =
        if (rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

    /**
     * 고른 메일이 회전 뒤에도 상세로 남는다. 폰은 세로가 좁고 가로가 넓어 한 칸과 두 칸 배치를 오가고, 태블릿은 둘 다 넓어 두 칸으로 남는다.
     * 좁은 화면의 뒤로 가기는 상세를 먼저 닫고, 넓은 화면은 가로채지 않는다.
     */
    @Test
    fun listDetailFollowsRotation() {
        rule.openExtra(Routes.MAIL)
        rule.waitTag("mail5")
        rule.onNodeWithTag("mail5").performClick()
        assertPanesFor("본문 m5")

        rotate(opposite())
        assertPanesFor("본문 m5")
        if (!narrow()) {
            rule.onNodeWithTag("mailList").performScrollToIndex(9)
            rule.onNodeWithTag("mail9").performClick()
            rule.waitText("본문 m9")
        }
        val body = if (narrow()) "본문 m5" else "본문 m9"

        rotate(opposite())
        assertPanesFor(body)
        if (narrow()) {
            // 좁은 화면에서 뒤로 가기는 상세를 닫고 목록으로 돌아간다(NavHost보다 먼저).
            back(rule)
            rule.waitGone("mailBody")
            rule.waitTag("mailList")
        }
        back(rule)
        rule.waitTag("lastEvent")
    }

    /** 폭에 따라 아래 탭 바와 시작 쪽 레일이 바뀌어도 선택한 탭과 목록 스크롤이 남는다. */
    @Test
    fun adaptiveNavigationSwitchesBarAndRail() {
        rule.openExtra(Routes.ADAPTIVE_TABS)
        rule.waitTag("adaptiveList")
        val height = rule.activity.window.decorView.height
        val tab = rule.onNode(hasText("보관함") and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
        assertTrue("세로에서는 탭 바가 아래에 있어야 한다", tab.fetchSemanticsNode().positionOnScreen.y > height / 2)
        tab.performClick()
        rule.waitText("탭 2")
        rule.onNodeWithTag("adaptiveList").performScrollToIndex(30)
        rule.waitText("첫 항목 30")

        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        rule.waitTag("adaptiveList", 8_000)
        rule.onNodeWithTag("adaptiveState").assertTextContains("탭 2", substring = true)
        rule.onNodeWithTag("adaptiveState").assertTextContains("첫 항목 30", substring = true)
        val rail = rule.onNode(hasText("보관함") and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).fetchSemanticsNode()
        val density = rule.activity.resources.displayMetrics.density
        assertTrue("가로에서는 레일이 시작 쪽에 있어야 한다: x=${rail.positionOnScreen.x}", rail.positionOnScreen.x < 160 * density)
        rule.onNode(hasText("검색") and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performClick()
        rule.waitText("탭 1")
    }

    /** 목록을 아래로 스크롤하면 떠 있는 탭 바가 접히고, 위로 스크롤하면 펼쳐지며, 펼친 탭은 누를 수 있다. */
    @Test
    fun floatingTabBarCollapsesOnScroll() {
        rule.openExtra(Routes.FLOATING)
        rule.waitTag("floatingList")
        rule.onNodeWithTag("floatingState").assertTextContains("접힘 false", substring = true)
        rule.onNodeWithTag("floatingList").performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.2f) }
        assertTrue("아래로 스크롤했는데 접히지 않았다", rule.becomes { rule.onAllNodesWithText("접힘 true", substring = true).fetchSemanticsNodes().isNotEmpty() })
        rule.onNodeWithTag("floatingList").performTouchInput { swipeDown(startY = height * 0.2f, endY = height * 0.7f) }
        assertTrue("위로 스크롤했는데 펼쳐지지 않았다", rule.becomes { rule.onAllNodesWithText("접힘 false", substring = true).fetchSemanticsNodes().isNotEmpty() })
        rule.onNodeWithText("검색").performClick()
        rule.waitText("탭 1 접힘 false")
        // 접은 뒤 회전해도 접힘 상태와 탭이 남는다.
        rule.onNodeWithTag("floatingList").performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.2f) }
        rule.waitText("접힘 true")
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        rule.waitText("탭 1 접힘 true", 8_000)
    }
}

/** 시나리오 7(이어서): 창 크기를 바꾸기 어려운 시험 환경에서 DeviceConfigurationOverride.ForcedSize로 좁은 폭과 넓은 폭을 강제한다. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ListDetailForcedSizeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var size by mutableStateOf(DpSize(400.dp, 800.dp))

    private fun show(initial: DpSize) {
        size = initial
        rule.setContent {
            MaterialTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) { MailScreen() }
            }
        }
    }

    @Test
    fun narrowPushesDetailAndBackReturns() {
        show(DpSize(400.dp, 800.dp))
        rule.onNodeWithTag("mail3").performClick()
        rule.waitTag("mailBody")
        assertTrue("상세가 밀려 들어온 뒤에도 목록이 남았다", rule.becomes { rule.onAllNodesWithText("메일 3").fetchSemanticsNodes().isEmpty() })
        assertTrue("좁은 화면 상세는 뒤로 가기를 받아야 한다", rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitGone("mailBody")
        rule.onNodeWithTag("mail3").assertIsDisplayed()
    }

    @Test
    fun wideShowsBothPanesAndKeepsSelectionAcrossResize() {
        show(DpSize(900.dp, 600.dp))
        rule.waitTag("emptyDetail")
        rule.onNodeWithTag("mailList").performScrollToIndex(25)
        rule.onNodeWithTag("mail25").performClick()
        rule.waitText("본문 m25")
        rule.onNodeWithTag("mail25").assertIsDisplayed()
        assertFalse("넓은 화면에서는 뒤로 가기를 가로채지 않아야 한다", rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())

        rule.runOnIdle { size = DpSize(400.dp, 800.dp) }
        rule.waitText("본문 m25")
        assertTrue("좁아진 뒤 뒤로 가기를 받지 않는다", rule.becomes { rule.activity.onBackPressedDispatcher.hasEnabledCallbacks() })

        rule.runOnIdle { size = DpSize(900.dp, 600.dp) }
        rule.waitTag("mail25")
        rule.onNodeWithTag("mail25").assertIsDisplayed()
        rule.onNodeWithTag("mailBody").assertTextContains("본문 m25")
    }
}
