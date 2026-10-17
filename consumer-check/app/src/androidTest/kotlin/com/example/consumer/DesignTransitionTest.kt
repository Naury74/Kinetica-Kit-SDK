package com.example.consumer

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.recent.DesignTransitionActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 시나리오: 디자인 언어를 바꾸고, CardStack으로 상세에 들어가 장면 전환을 앞뒤로 재생한 뒤 뒤로 나온다.
 * 전환 중에도 화면이 멈추지 않고 끝나면 목적지 화면만 남는지 본다.
 */
@RunWith(AndroidJUnit4::class)
class DesignTransitionTest {
    @get:Rule
    val rule = createAndroidComposeRule<DesignTransitionActivity>()

    @Test
    fun designTabsStepsAndScenes() {
        rule.onNodeWithTag("designName").assertExists()
        rule.onNodeWithText("Material").performClick()
        rule.onNodeWithText("디자인 Material").assertExists()

        rule.onNodeWithText("이번 주").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tab_1").assertExists()
        rule.onNodeWithTag("nextStep").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("step_1").assertExists()

        rule.onNodeWithTag("openScene").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("screen_scene").assertExists()
        rule.onNodeWithTag("toggleScene").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("뒤로").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("frame").assertExists()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("screen_home").assertExists()
        rule.onNodeWithTag("screen_scene").assertDoesNotExist()
    }
}
