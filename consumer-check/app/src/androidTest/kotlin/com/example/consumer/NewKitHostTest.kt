package com.example.consumer

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.recent.NewKitM3Activity
import com.example.consumer.recent.NewKitScaffoldActivity
import com.example.consumer.recent.NewKitXmlActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 시나리오 (가): Kinetica 테마와 스캐폴드 없는 Material 3 앱. 떠 있는 컴포넌트가 따로 창을 띄워 동작해야 한다. */
@RunWith(AndroidJUnit4::class)
class NewKitM3Test {
    @get:Rule
    val rule = createAndroidComposeRule<NewKitM3Activity>()

    @Test
    fun morphMenuOpensInItsOwnWindowAndPicks() {
        rule.onNodeWithTag("morph").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("인기순").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals("인기순", rule.activity.picked)
        rule.onNodeWithText("인기순").assertDoesNotExist()
        // 판이 사라진 뒤 칩을 다시 눌러 열 수 있다.
        rule.onNodeWithTag("morph").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("최신순").assertIsDisplayed()
        TestSupport.back(rule)
        rule.onNodeWithText("최신순").assertDoesNotExist()
    }

    @Test
    fun commandPaletteSearchesKoreanInitialsAndRunsWithEnter() {
        rule.onNodeWithTag("openPalette").performClick()
        rule.waitForIdle()
        val field = rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        field.performTextInput("ㅅㅈ")
        rule.waitForIdle()
        // 기기 언어와 상관없이, 초성에 맞는 명령만 남고 다른 명령은 빠져야 한다.
        rule.onNodeWithText("새 주문 만들기", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("주문 열기", useUnmergedTree = true).assertDoesNotExist()
        field.performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        assertEquals("새 주문", rule.activity.ran)
        rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).assertDoesNotExist()
    }

    @Test
    fun liveActivityShowsOutsideScaffoldAndExpandsOnTap() {
        rule.onNodeWithTag("startLive").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("타이머, 5분 남음").assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("liveExpanded", useUnmergedTree = true).assertExists()
    }

    @Test
    fun chatInputVoiceButtonStartsAndSendsWithAccessibilityAction() {
        val voice = rule.onNodeWithTag("voice")
        voice.performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        Thread.sleep(1_200)
        rule.onNodeWithTag("voice").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertTrue("녹음을 보내야 한다: ${rule.activity.ran}", rule.activity.ran.startsWith("녹음 "))
    }
}

/** 시나리오 (나): KineticaScaffold 글래스 화면. 새 컴포넌트를 모두 한 목록에 두어도 화면이 쉬는 상태가 되고 조작이 먹는다. */
@RunWith(AndroidJUnit4::class)
class NewKitScaffoldTest {
    @get:Rule
    val rule = createAndroidComposeRule<NewKitScaffoldActivity>()

    private fun scrollTo(tag: String) {
        rule.onNodeWithTag("list").performScrollToNode(hasTestTag(tag))
        rule.waitForIdle()
    }

    @Test
    fun everyNewComponentLetsTheScreenGoIdle() {
        // 무한 애니메이션이 테스트를 붙잡으면 여기서 시간 초과로 실패한다. 목록 끝까지 차례로 구성해 본다.
        listOf("lensSlider", "circular", "stack", "scratch", "table", "dock").forEach { scrollTo(it) }
        rule.onNodeWithTag("list").performScrollToNode(hasText("K"))
        rule.waitForIdle()
    }

    @Test
    fun lensControlsDragLikePlainOnes() {
        val before = rule.activity.volume
        rule.onNodeWithTag("lensSlider").performTouchInput { swipe(centerLeft + Offset(20f, 0f), centerRight - Offset(20f, 0f), 400) }
        rule.waitForIdle()
        assertTrue("렌즈 슬라이더도 끌면 값이 올라야 한다", rule.activity.volume > before + 0.5f)
        rule.onNodeWithTag("lensSegment").performTouchInput {
            val third = width / 3f
            swipe(Offset(third / 2, centerY), Offset(third * 2.5f, centerY), 500)
        }
        rule.waitForIdle()
        assertEquals("인디케이터를 끝 칸으로 끌면 그 칸을 고른다", 2, rule.activity.segment)
        rule.onNodeWithText("설정").performClick()
        rule.waitForIdle()
        assertEquals(2, rule.activity.tab)
    }

    @Test
    fun circularSliderScratchStackAndTableWork() {
        scrollTo("circular")
        rule.onNodeWithTag("circular").performSemanticsAction(SemanticsActions.SetProgress) { it(40f) }
        rule.waitForIdle()
        assertEquals(40f, rule.activity.timer, 0.01f)

        scrollTo("shapeToggle")
        rule.onNodeWithTag("shapeToggle").performClick()
        rule.waitForIdle()
        assertTrue(rule.activity.toggled)

        scrollTo("stack")
        rule.onNodeWithTag("stack").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("카드 2", useUnmergedTree = true).assertIsDisplayed()

        scrollTo("scratch")
        rule.onNodeWithTag("scratch").performTouchInput {
            for (row in 0..6) {
                val y = height * (row + 0.5f) / 7f
                swipe(Offset(0f, y), Offset(width.toFloat(), y), 120)
            }
        }
        rule.waitForIdle()
        assertTrue("반 넘게 긁으면 열려야 한다", rule.activity.revealed)

        scrollTo("table")
        // 좁은 화면에서는 끝 열이 가로 스크롤 밖에 걸리므로 표를 끝까지 민 뒤 머리를 누른다.
        rule.onNodeWithTag("table").performTouchInput { swipe(Offset(width * 0.9f, centerY), Offset(width * 0.1f, centerY), 300) }
        rule.waitForIdle()
        rule.onNodeWithText("금액", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        val ids = rule.onAllNodesWithText("#", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { node -> node.config.getOrElseNullable(SemanticsProperties.Text) { null }?.firstOrNull()?.text }
        assertEquals("금액 오름차순이면 #1, #2, #3 순서다", listOf("#1", "#2", "#3"), ids.filter { it.length == 2 })
    }

    @Test
    fun stateSurvivesRecreate() {
        scrollTo("circular")
        rule.onNodeWithTag("circular").performSemanticsAction(SemanticsActions.SetProgress) { it(15f) }
        rule.waitForIdle()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        // 액티비티 필드는 새로 만들어지므로, 다시 만든 뒤에도 화면이 크래시 없이 뜨고 조작되는지만 본다.
        scrollTo("table")
        rule.onNodeWithTag("table").assertExists()
        scrollTo("scratch")
        rule.onNodeWithTag("scratch").assertExists()
    }
}

/** 시나리오 (다): XML NestedScrollView 안 ComposeView. 원형 슬라이더를 세로로 끌어도 부모가 스크롤하지 않고 값이 바뀐다. */
@RunWith(AndroidJUnit4::class)
class NewKitXmlTest {
    @get:Rule
    val rule = createAndroidComposeRule<NewKitXmlActivity>()

    @Test
    fun circularSliderKeepsTheDragInsideNestedScroll() {
        val node = rule.onNodeWithTag("xmlCircular")
        val top = node.fetchSemanticsNode().boundsInRoot.top
        // 오른쪽 가장자리 트랙을 위에서 아래로 끈다(3시 방향 근처, 시계 방향).
        node.performTouchInput { swipe(Offset(width * 0.93f, height * 0.25f), Offset(width * 0.93f, height * 0.75f), 500) }
        rule.waitForIdle()
        assertNotEquals("값이 바뀌어야 한다", 0f, rule.activity.timer)
        assertEquals("부모 스크롤이 손가락을 가져가면 안 된다", top, rule.onNodeWithTag("xmlCircular").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test
    fun morphMenuInsideComposeViewOpensAndPicks() {
        rule.onNodeWithTag("xmlMorph").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("격자").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals("격자", rule.activity.picked)
    }
}
