package com.example.consumer

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.m3.M3EdgeActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 시나리오 2: Material 컴포넌트에 Kinetica 효과를 붙여도 클릭과 스크롤이 그대로인지 본다. */
@RunWith(AndroidJUnit4::class)
class EffectsTest {
    @get:Rule
    val rule = createAndroidComposeRule<M3EdgeActivity>()

    @Before
    fun openTab() {
        rule.onNodeWithTag("tab_effects").performClick()
        rule.waitTag("effectButton")
    }

    @Test
    fun clicksPassThroughEffects() {
        repeat(3) { rule.onNodeWithTag("effectButton").performClick() }
        rule.onNodeWithTag("effectButtonText", useUnmergedTree = true).assertTextContains("효과 버튼 3")
        rule.onNodeWithTag("jellyCard").performClick()
        rule.onNodeWithTag("jellyCardText", useUnmergedTree = true).assertTextContains("젤리 카드 1")
        rule.onNodeWithTag("effectListItem").performClick()
        rule.onNodeWithTag("listItemText", useUnmergedTree = true).assertTextContains("목록 항목 1")
        rule.onNodeWithTag("skeletonCard").performClick()
    }

    /** 실제 탭(손가락 누름과 뗌 사이 간격)에서도 클릭이 한 번씩만 가는지 본다. */
    @Test
    fun realTapsOnEffectButton() {
        val node = rule.onNodeWithTag("effectButton").fetchSemanticsNode()
        val x = (node.positionOnScreen.x + node.size.width / 2).toInt()
        val y = (node.positionOnScreen.y + node.size.height / 2).toInt()
        repeat(4) {
            TestSupport.device.click(x, y)
            Thread.sleep(250)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("effectButtonText", useUnmergedTree = true).assertTextContains("효과 버튼 4")
    }

    @Test
    fun list500ScrollAndRecycle() {
        rule.onNodeWithTag("openList500").performClick()
        rule.waitTag("list500")
        rule.onNodeWithTag("list500").performScrollToIndex(250)
        rule.waitTag("row250")
        rule.onNodeWithTag("row250").performClick()
        rule.onNodeWithTag("clickedIndex").assertTextContains("누른 항목: 250")
        repeat(15) { rule.onNodeWithTag("list500").performTouchInput { swipeUp(durationMillis = 120) } }
        repeat(15) { rule.onNodeWithTag("list500").performTouchInput { swipeDown(durationMillis = 120) } }
        rule.onNodeWithTag("list500").performScrollToIndex(499)
        rule.waitTag("row499")
        rule.onNodeWithTag("row499").performClick()
        rule.onNodeWithTag("clickedIndex").assertTextContains("누른 항목: 499")
        rule.onNodeWithTag("list500").performScrollToIndex(3)
        rule.waitTag("row3")
        rule.onNodeWithTag("row3").performClick()
        rule.onNodeWithTag("clickedIndex").assertTextContains("누른 항목: 3")
    }
}
