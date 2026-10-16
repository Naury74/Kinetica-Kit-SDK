package com.example.consumer

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.recent.EditorHostActivity
import com.example.consumer.recent.EditorHostScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 편집기 나가기 버튼의 글자. 기기 언어를 따르므로 리소스에서 읽는다. */
private fun exitLabel(activity: ComponentActivity): String =
    activity.getString(activity.resources.getIdentifier("kinetica_exit", "string", activity.packageName))

/** 시나리오 12: 호스트가 KineticaEditor에 나가기 콜백과 자기 도구 모음 버튼을 넣는다. */
@RunWith(AndroidJUnit4::class)
class EditorHostTest {
    @get:Rule
    val rule = createAndroidComposeRule<EditorHostActivity>()

    private fun exitButton() = rule.onNode(hasText(exitLabel(rule.activity)) and hasClickAction())

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun exitPinnedAtEndCallsHostAndHostButtonsWork() {
        rule.waitTag("hostExport")
        val exit = exitButton().fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val density = rule.activity.resources.displayMetrics.density
        // 도구 모음 오른쪽 여백(8dp)과 그룹 안쪽 여백만큼만 떨어져 있어야 한다. 시스템 바 인셋은 호스트가 safeDrawingPadding으로 뺐다.
        val insetRight = TestSupport.systemBars(rule.activity).right
        assertTrue("나가기 버튼이 오른쪽 끝에 있지 않다: ${exit.right} / ${root.right}", root.right - insetRight - exit.right <= 16 * density)
        // 폭이 좁은 기기에서는 도구 모음이 가로로 스크롤되어 호스트 버튼이 처음에 화면 밖에 있을 수 있으므로 스크롤해서 찾는다.
        rule.onNodeWithTag("hostShare").performScrollTo()
        rule.waitForIdle()
        assertTrue("호스트 버튼은 나가기 앞에 있어야 한다", bounds("hostShare").right <= exitButton().fetchSemanticsNode().boundsInRoot.left + 1f)

        exitButton().performClick()
        rule.waitText("나가기 1")
        rule.onNodeWithTag("hostExport").performScrollTo().performClick()
        rule.onNodeWithTag("hostShare").performScrollTo().performClick()
        rule.waitText("내보내기 1 공유 1")
        assertEquals(1, rule.activity.exits)
    }
}

/** 시나리오 12(이어서): 좁은 폭에서는 도구 모음이 가로로 스크롤되고 나가기 버튼은 고정된다. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EditorToolbarNarrowTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var exits by mutableIntStateOf(0)

    // boundsInRoot는 부모 밖으로 나간 부분을 잘라 내므로 스크롤로 가려진 위치는 위치와 크기로 직접 만든다.
    private fun SemanticsNode.unclipped(): Rect = Rect(positionInRoot, size.toSize())

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().unclipped()

    private fun exitBounds(): Rect = rule.onNode(hasText(exitLabel(rule.activity)) and hasClickAction()).fetchSemanticsNode().unclipped()

    @Test
    fun narrowToolbarScrollsAndExitStaysPinned() {
        rule.setContent {
            MaterialTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(340.dp, 700.dp))) {
                    EditorHostScreen(onExit = { exits++ })
                }
            }
        }
        rule.waitTag("hostShare")
        val exit0 = exitBounds()
        val share0 = bounds("hostShare")
        assertTrue("좁은 폭에서 호스트 버튼이 처음부터 다 보이면 스크롤 시험이 의미 없다: $share0 / $exit0", share0.right > exit0.left)

        // 도구 모음 줄을 왼쪽으로 밀면 내용이 따라오고 나가기 버튼은 제자리다.
        val toolbar = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyDescendant(hasTestTag("hostShare")))
        toolbar.performTouchInput { swipeLeft(startX = width * 0.8f, endX = width * 0.1f, durationMillis = 300) }
        rule.waitForIdle()
        val share1 = bounds("hostShare")
        assertTrue("밀었는데 도구 모음이 스크롤되지 않았다: ${share0.left} → ${share1.left}", share1.left < share0.left - 20f)
        assertEquals("나가기 버튼이 스크롤에 따라 움직였다", exit0.left, exitBounds().left, 1f)

        rule.onNodeWithTag("hostShare").performScrollTo()
        rule.waitForIdle()
        val share2 = bounds("hostShare")
        assertTrue("끝까지 스크롤하면 공유 버튼이 나가기 버튼 앞에 보여야 한다: $share2 / $exit0", share2.right <= exit0.left + 1f)
        assertEquals(exit0.left, exitBounds().left, 1f)
        rule.onNodeWithTag("hostShare").performClick()
        rule.waitText("공유 1")
        rule.onNode(hasText(exitLabel(rule.activity)) and hasClickAction()).performClick()
        rule.runOnIdle { assertEquals(1, exits) }
    }
}
