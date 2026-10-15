package com.example.consumer

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.m3.M3EdgeActivity
import com.example.consumer.m3.Routes
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 시나리오 8: Material 버튼과 카드에 rippleDistortion, liquidMorph, holographic을 붙이고, 입자 배경과 뒤집기 카드를 쓴다. */
@RunWith(AndroidJUnit4::class)
class Effects2Test {
    @get:Rule
    val rule = createAndroidComposeRule<M3EdgeActivity>()

    @Before
    fun open() {
        rule.openExtra(Routes.EFFECTS2)
        rule.waitTag("rippleButton")
    }

    @After
    fun restore() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    private fun realTap(tag: String) {
        val node = rule.onNodeWithTag(tag).performScrollTo().fetchSemanticsNode()
        TestSupport.device.click((node.positionOnScreen.x + node.size.width / 2).toInt(), (node.positionOnScreen.y + node.size.height / 2).toInt())
        Thread.sleep(250)
        rule.waitForIdle()
    }

    @Test
    fun clicksPassThroughNewEffects() {
        repeat(3) { rule.onNodeWithTag("rippleButton").performClick() }
        repeat(2) { realTap("rippleButton") }
        rule.onNodeWithTag("rippleText", useUnmergedTree = true).assertTextContains("물결 5")

        rule.onNodeWithTag("morphCard").performClick()
        realTap("morphCard")
        rule.onNodeWithTag("morphText", useUnmergedTree = true).assertTextContains("액체 카드 2")

        rule.onNodeWithTag("holoCard").performClick()
        realTap("holoCard")
        rule.onNodeWithTag("holoText", useUnmergedTree = true).assertTextContains("홀로그램 2")

        // 입자 배경 위 버튼. 배경이 터치를 가져가지 않아야 한다.
        rule.onNodeWithTag("particleButton").performScrollTo().performClick()
        realTap("particleButton")
        rule.onNodeWithTag("particleText", useUnmergedTree = true).assertTextContains("축하 2")

        rule.onNodeWithTag("blobButton").performScrollTo().performClick()
        realTap("blobButton")
        rule.onNodeWithTag("blobButton").assertTextContains("방울 2", substring = true)
    }

    private fun faceShown(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun flipCardByButtonAndDrag() {
        rule.onNodeWithTag("flipToggle").performScrollTo().performClick()
        // FlipCard는 두 면을 카드 하나로 합쳐 읽히므로(mergeDescendants) 면 안 태그는 합치지 않은 트리에서 찾는다.
        assertTrue("뒷면이 나오지 않았다", rule.becomes { faceShown("flipBack") && !faceShown("flipFront") })
        rule.onNodeWithTag("flipState").assertTextContains("뒤집힘 true")
        // 손가락으로 끌어 다시 앞면으로.
        rule.onNodeWithTag("flipCard").performTouchInput { swipeLeft(startX = width * 0.9f, endX = width * 0.05f, durationMillis = 200) }
        assertTrue("끌어서 앞면으로 돌아오지 않았다", rule.becomes { faceShown("flipFront") && !faceShown("flipBack") })
        rule.onNodeWithTag("flipState").assertTextContains("뒤집힘 false")
    }

    /** holographic(useSensor = true)은 화면이 STARTED일 때만 센서를 받고, 백그라운드로 가거나 화면을 떠나면 놓아야 한다. */
    @Test
    fun leavingScreenReleasesSensorAndAnimations() {
        assertTrue("holographic 센서가 등록되지 않았다", rule.becomes(3_000) { appHasActiveSensorConnection() })

        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue("백그라운드로 갔는데 센서가 남았다", rule.becomes(3_000) { !appHasActiveSensorConnection() })
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitTag("rippleButton")
        assertTrue("돌아왔는데 센서가 다시 등록되지 않았다", rule.becomes(3_000) { appHasActiveSensorConnection() })

        // 회전으로 다시 만들어져도 크래시 없이 상태가 남는다.
        rule.onNodeWithTag("rippleButton").performClick()
        rotateAndSettle(rule, rule.activityRule.scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        rule.waitTag("rippleButton", 8_000)
        rule.onNodeWithTag("rippleText", useUnmergedTree = true).assertTextContains("물결 1")
        rotateAndSettle(rule, rule.activityRule.scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        rule.waitTag("effects2Leave", 8_000)

        rule.onNodeWithTag("effects2Leave").performScrollTo().performClick()
        rule.waitTag("lastEvent")
        assertTrue("화면을 떠났는데 센서가 남았다", rule.becomes(3_000) { !appHasActiveSensorConnection() })
        // 떠난 뒤 프레임이 계속 돌아도(입자·물결 정리 실패) 홈 화면은 정상으로 눌려야 한다.
        Thread.sleep(1_000)
        rule.onNodeWithTag("openGlassSheet").performClick()
        rule.waitTag("glassSheetOk")
    }
}
