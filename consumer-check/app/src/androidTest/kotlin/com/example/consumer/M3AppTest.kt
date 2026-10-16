package com.example.consumer

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.TestSupport.back
import com.example.consumer.m3.M3EdgeActivity
import com.example.consumer.m3.M3NoEdgeActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 시나리오 1: Material3 + Navigation Compose 안에서 글래스 UI를 부른다. edge-to-edge 있음/없음 두 화면에서 같은 시험을 돌린다. */
abstract class M3AppTest<A : ComponentActivity>(type: Class<A>) {
    @get:Rule
    val rule = createAndroidComposeRule(type)

    @After
    fun restore() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    @Test
    fun kSheetClosesBeforeNavHostPops() {
        rule.onNodeWithTag("goDetail").performClick()
        rule.waitTag("detailScreen")
        rule.onNodeWithTag("detailOpenSheet").performClick()
        rule.waitTag("detailSheetBody")
        back(rule)
        rule.waitGone("detailSheetBody")
        rule.onNodeWithTag("detailScreen").assertIsDisplayed()
        back(rule)
        rule.waitTag("lastEvent")
        rule.onNodeWithTag("title").assertTextContains("home", substring = true)
    }

    @Test
    fun kSheetButtonReceivesClick() {
        rule.onNodeWithTag("openKSheet").performClick()
        rule.waitTag("kSheetOk")
        rule.onNodeWithTag("kSheetOk").performClick()
        rule.waitGone("kSheetBody")
        rule.onNodeWithTag("lastEvent").assertTextContains("sheet-ok", substring = true)
    }

    @Test
    fun kDialogOverMaterialSheetBackOrder() {
        rule.onNodeWithTag("openMaterialSheet").performClick()
        rule.waitTag("materialSheetOpensKSheet")
        rule.onNodeWithTag("materialSheetOpensKSheet").performClick()
        rule.waitTag("kDialogBody")
        back(rule)
        rule.waitGone("kDialogBody")
        rule.onNodeWithTag("materialSheetBody").assertIsDisplayed()
        back(rule)
        rule.waitGone("materialSheetBody")
    }

    @Test
    fun materialDialogOverKDialog() {
        rule.onNodeWithTag("openKDialog").performClick()
        rule.waitTag("dialogOpensMaterial")
        rule.onNodeWithTag("dialogOpensMaterial").performClick()
        rule.waitTag("materialDialogOk")
        rule.onNodeWithTag("materialDialogOk").performClick()
        rule.waitGone("materialDialogBody")
        rule.onNodeWithTag("kDialogBody").assertIsDisplayed()
        back(rule)
        rule.waitGone("kDialogBody")
        rule.onNodeWithTag("lastEvent").assertTextContains("material-ok", substring = true)
    }

    /**
     * 토스트와 스낵바. Compose 테스트 시계는 기다리는 동안 delay와 시간 제한을 곧바로 건너뛰므로(표시 시간이 순식간에 지나감)
     * 시계를 멈추고 직접 넘기며 확인한다.
     */
    @Test
    fun toastAndSnackbarShowAndActionReturns() {
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithTag("showToast").performClick()
            rule.mainClock.advanceTimeBy(1_000)
            rule.onNodeWithText("저장했어요").assertExists()
            rule.onNodeWithTag("showSnackbar").performClick()
            rule.mainClock.advanceTimeBy(2_000)
            rule.onNodeWithText("항목을 지웠어요", substring = true).assertExists()
            // 터치 주입 대신 접근성 클릭 동작으로 누른다(TalkBack 두 번 탭과 같은 경로).
            rule.onNodeWithText("되돌리기").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            repeat(20) { rule.mainClock.advanceTimeBy(100) }
            assertTrue("스낵바 동작 결과가 오지 않았다: ${lastEvent()}", lastEvent() == "snackbar-action")
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun contextMenuOnMaterialListItem() {
        rule.onNodeWithTag("contextItem").performTouchInput { longClick() }
        rule.waitText("삭제")
        rule.onNodeWithText("삭제").performClick()
        assertTrue("메뉴 항목 클릭이 오지 않았다", rule.becomes { lastEvent() == "menu-delete" })
    }

    @Test
    fun expandCardAndBack() {
        rule.onNodeWithTag("expandCard").performClick()
        rule.waitTag("expandDetail")
        back(rule)
        rule.waitGone("expandDetail")
        rule.onNodeWithTag("expandCard").assertIsDisplayed()
    }

    @Test
    fun miniPlayerExpandsAndBackCollapses() {
        rule.onNodeWithTag("goMedia").performClick()
        rule.waitTag("miniState")
        rule.onNodeWithTag("miniCollapsed", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("place0", useUnmergedTree = true).assertExists()
        // 접힌 상태에서는 전체 화면 플레이어가 접근성 트리에 없어야 한다(TalkBack이 안 보이는 내용을 읽지 않게).
        assertTrue(
            "접힌 미니 플레이어의 전체 화면 내용이 접근성 트리에 남아 있다",
            rule.onAllNodesWithTag("miniExpanded", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
        rule.onNodeWithTag("miniCollapsed", useUnmergedTree = true).performClick()
        assertTrue("미니 플레이어가 펼쳐지지 않았다", rule.becomes { miniExpanded() })
        back(rule)
        assertTrue("뒤로 가기로 미니 플레이어가 접히지 않았다", rule.becomes { !miniExpanded() })
        // 접힌 뒤에도 미디어 화면에 남아 있어야 한다(NavHost가 먼저 뒤로 가면 안 된다).
        rule.onNodeWithTag("title").assertTextContains("media", substring = true)
        back(rule)
        rule.waitTag("lastEvent")
    }

    @Test
    fun pushNavigationInsideNavHost() {
        rule.onNodeWithTag("goPush").performClick()
        rule.waitTag("push_root")
        rule.onNodeWithTag("pushNext_root").performClick()
        rule.waitTag("push_child1")
        back(rule)
        rule.waitGone("push_child1")
        rule.onNodeWithTag("push_root").assertIsDisplayed()
        rule.onNodeWithTag("title").assertTextContains("push", substring = true)
        back(rule)
        rule.waitTag("lastEvent")
    }

    @Test
    fun rotationKeepsOpenSheet() {
        rule.onNodeWithTag("openKSheet").performClick()
        rule.waitTag("kSheetBody")
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        Thread.sleep(1_500)
        rule.waitTag("kSheetBody")
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        Thread.sleep(1_500)
        rule.waitTag("kSheetOk")
        rule.onNodeWithTag("kSheetOk").performClick()
        rule.waitGone("kSheetBody")
    }

    @Test
    fun darkModeToggleWithOpenDialog() {
        rule.onNodeWithTag("openKDialog").performClick()
        rule.waitTag("kDialogBody")
        try {
            TestSupport.shell("cmd uimode night yes")
            Thread.sleep(2_000)
            rule.waitTag("kDialogBody")
            rule.onNodeWithTag("kDialogBody").assertIsDisplayed()
        } finally {
            TestSupport.shell("cmd uimode night no")
            Thread.sleep(2_000)
        }
        rule.waitTag("kDialogBody")
        back(rule)
        rule.waitGone("kDialogBody")
    }

    /** 토스트는 상태 표시줄 아래에, 시트 버튼은 내비게이션 바 위에 있어야 한다. */
    @Test
    fun overlaysAvoidSystemBars() {
        val bars = arrayOf(androidx.core.graphics.Insets.NONE)
        var screenHeight = 0
        rule.activityRule.scenario.onActivity {
            bars[0] = TestSupport.systemBars(it)
            screenHeight = it.window.decorView.height
        }
        rule.onNodeWithTag("showToast").performClick()
        rule.waitText("저장했어요")
        val toastTop = rule.onNodeWithText("저장했어요").fetchSemanticsNode().positionOnScreen.y
        assertTrue("토스트가 상태 표시줄(${bars[0].top}px)에 가린다: top=$toastTop", toastTop >= bars[0].top)

        rule.onNodeWithTag("openKSheet").performClick()
        rule.waitTag("kSheetOk")
        Thread.sleep(TestSupport.SETTLE_MS)
        val ok = rule.onNodeWithTag("kSheetOk").fetchSemanticsNode()
        val bottom = ok.positionOnScreen.y + ok.size.height
        assertTrue("시트 버튼이 내비게이션 바(${bars[0].bottom}px)에 가린다: bottom=$bottom, screen=$screenHeight", bottom <= screenHeight - bars[0].bottom)
    }

    private fun miniExpanded(): Boolean = textOf("miniState").endsWith("true")

    private fun textOf(tag: String): String =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }

    private fun lastEvent(): String {
        val node = rule.onNodeWithTag("lastEvent").fetchSemanticsNode()
        val text = node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        return text.substringAfter(": ")
    }
}

@RunWith(AndroidJUnit4::class)
class M3EdgeAppTest : M3AppTest<M3EdgeActivity>(M3EdgeActivity::class.java)

@RunWith(AndroidJUnit4::class)
class M3NoEdgeAppTest : M3AppTest<M3NoEdgeActivity>(M3NoEdgeActivity::class.java)
