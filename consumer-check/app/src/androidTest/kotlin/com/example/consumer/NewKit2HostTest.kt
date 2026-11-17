package com.example.consumer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.recent.NewKit2HeaderActivity
import com.example.consumer.recent.NewKit2M3Activity
import com.example.consumer.recent.NewKit2ScaffoldActivity
import com.example.consumer.recent.NewKit2XmlActivity
import com.naury.kinetica.ui.KDayEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/** 시나리오 (라): Kinetica 테마 없는 Material 3 앱의 AI 화면 부품과 멘션 입력 */
@RunWith(AndroidJUnit4::class)
class NewKit2M3Test {
    @get:Rule
    val rule = createAndroidComposeRule<NewKit2M3Activity>()

    @Test
    fun markdownAndCodeRenderAndCopy() {
        rule.onNodeWithText("굵게", substring = true, useUnmergedTree = true).assertExists()
        rule.onNodeWithText("val x = 1", substring = true, useUnmergedTree = true).assertExists()
        // 복사는 TalkBack 사용자 정의 동작으로도 할 수 있다.
        val actions = rule.onNodeWithTag("code").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnUiThread { actions.first().action() }
        rule.waitForIdle()
        assertTrue(rule.activity.copied)
    }

    @Test
    fun mentionPopupOpensInItsOwnWindowAndInsertsToken() {
        val field = rule.onNode(hasSetTextAction() and hasTestTag("mention"))
        field.performClick()
        field.performTextInput("@김")
        rule.waitForIdle()
        rule.onNodeWithText("김민지", useUnmergedTree = true).assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("@김민지", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun promptSendsWithHardwareEnterAndTurnsIntoStop() {
        val field = rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("prompt")))
        field.performClick()
        field.performTextInput("안녕")
        field.performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        assertEquals("안녕", rule.activity.sent)
    }
}

/** 시나리오 (마): 글래스 스캐폴드의 업무 도구, 차트, 연출 부품. 호스트는 mutableStateListOf를 제자리에서 바꾸고 키는 일반 클래스다. */
@RunWith(AndroidJUnit4::class)
class NewKit2ScaffoldTest {
    @get:Rule
    val rule = createAndroidComposeRule<NewKit2ScaffoldActivity>()

    private fun scrollTo(tag: String) {
        rule.onNodeWithTag("list").performScrollToNode(hasTestTag(tag))
        rule.waitForIdle()
    }

    @Test
    fun everythingComposesAndIdlesWithPlainClassKeys() {
        listOf("stack", "tree", "kanban", "timeline", "treemap", "candle", "shine").forEach { scrollTo(it) }
    }

    @Test
    fun notificationSwipeDismissesWholeStackWithoutCrash() {
        rule.onNodeWithTag("stack").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        Thread.sleep(800)
        rule.waitForIdle()
        assertTrue("호스트 목록에서 모두 빠져야 한다: ${rule.activity.notes.size}", rule.activity.notes.isEmpty())
    }

    @Test
    fun treeExpandsAndSelects() {
        scrollTo("tree")
        rule.onNodeWithText("app", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("src", useUnmergedTree = true).assertExists().performClick()
        rule.waitForIdle()
        assertEquals("src", rule.activity.selected)
    }

    @Test
    fun kanbanMovesCardToNextColumnByLongPressDrag() {
        scrollTo("kanban")
        val card = rule.onNodeWithText("디자인 리뷰", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val target = rule.onNodeWithText("완료", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val board = rule.onNodeWithTag("kanban").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("kanban").performTouchInput {
            val start = Offset(card.center.x - board.left, card.center.y - board.top)
            val end = Offset(target.center.x - board.left, target.bottom - board.top + 60f)
            down(start)
            advanceEventTime(800)
            val steps = 20
            for (i in 1..steps) {
                moveTo(start + (end - start) * (i / steps.toFloat()))
                advanceEventTime(16)
            }
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("디자인 리뷰"), rule.activity.done.map { it.title })
    }

    @Test
    fun inPlaceListChangesShowUp() {
        scrollTo("timeline")
        rule.runOnUiThread { rule.activity.events.add(KDayEvent("e2", "점심 약속", LocalTime.of(11, 0), LocalTime.of(11, 30))) }
        rule.waitForIdle()
        rule.onNodeWithText("점심 약속", substring = true, useUnmergedTree = true).assertExists()
        // 자정을 넘는 일정도 호스트가 그대로 넘겨도 죽지 않는다.
        rule.runOnUiThread { rule.activity.events.add(KDayEvent("e3", "야간 배포", LocalTime.of(23, 0), LocalTime.of(1, 0))) }
        rule.waitForIdle()
    }

    @Test
    fun chartsAndShineRespondToTouch() {
        scrollTo("treemap")
        rule.onNodeWithText("주거", substring = true, useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals("주거", rule.activity.treemapClicked)
        scrollTo("candle")
        rule.onNodeWithTag("candle").performTouchInput { swipe(Offset(width * 0.2f, centerY), Offset(width * 0.8f, centerY), 400) }
        rule.waitForIdle()
        scrollTo("shine")
        rule.onNodeWithTag("shine").performClick()
        rule.waitForIdle()
        assertEquals(1, rule.activity.shine)
    }

    @Test
    fun recreateKeepsWorkingWithUnsaveableKeys() {
        scrollTo("tree")
        rule.onNodeWithText("app", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        scrollTo("kanban")
        rule.onNodeWithText("디자인 리뷰", useUnmergedTree = true).assertExists()
    }
}

/** 시나리오 (바): 화면 전체 늘어나는 헤더 */
@RunWith(AndroidJUnit4::class)
class NewKit2HeaderTest {
    @get:Rule
    val rule = createAndroidComposeRule<NewKit2HeaderActivity>()

    @Test
    fun headerCollapsesIntoBarAndListScrolls() {
        rule.onNodeWithTag("headerList").performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithTag("headerList").performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithTag("headerBar").assertIsDisplayed()
        rule.onNodeWithTag("headerList").performScrollToNode(hasText("장소 39"))
        rule.onNodeWithText("장소 39").assertIsDisplayed()
        // 맨 위로 돌아와 더 당겨도 죽지 않는다.
        rule.onNodeWithTag("headerList").performScrollToNode(hasText("장소 0"))
        rule.onNodeWithTag("headerList").performTouchInput { swipe(Offset(centerX, 200f), Offset(centerX, height * 0.8f), 400) }
        rule.waitForIdle()
    }
}

/** 시나리오 (사): XML 스크롤 화면 안 캔들과 칸반 */
@RunWith(AndroidJUnit4::class)
class NewKit2XmlTest {
    @get:Rule
    val rule = createAndroidComposeRule<NewKit2XmlActivity>()

    @Test
    fun candleDragDoesNotScrollParent() {
        val top = rule.onNodeWithTag("xmlCandle").fetchSemanticsNode().boundsInRoot.top
        rule.onNodeWithTag("xmlCandle").performTouchInput { swipe(Offset(width * 0.8f, centerY), Offset(width * 0.2f, centerY + 30f), 400) }
        rule.waitForIdle()
        assertEquals(top, rule.onNodeWithTag("xmlCandle").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test
    fun kanbanDragInsideNestedScrollMovesCard() {
        val card = rule.onNodeWithText("알림 설정", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val target = rule.onNodeWithText("완료", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val board = rule.onNodeWithTag("xmlKanban").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("xmlKanban").performTouchInput {
            val start = Offset(card.center.x - board.left, card.center.y - board.top)
            val end = Offset(target.center.x - board.left, target.bottom - board.top + 60f)
            down(start)
            advanceEventTime(800)
            for (i in 1..20) {
                moveTo(start + (end - start) * (i / 20f))
                advanceEventTime(16)
            }
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("알림 설정"), rule.activity.done.map { it.title })
    }
}
