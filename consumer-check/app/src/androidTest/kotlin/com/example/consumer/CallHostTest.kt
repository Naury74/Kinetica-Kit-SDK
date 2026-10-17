package com.example.consumer

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.recent.CallHostActivity
import com.example.consumer.recent.CallPartsActivity
import com.naury.kinetica.ui.KCallPhase
import com.naury.kinetica.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 완성 통화 화면 `KCallScreen`을 호스트 StateFlow와 TextureView 영상으로 쓰는 경우. 문구는 기기 언어를 따르므로 리소스로 찾는다. */
@RunWith(AndroidJUnit4::class)
class CallHostTest {
    @get:Rule
    val rule = createAndroidComposeRule<CallHostActivity>()

    private fun text(id: Int) = rule.activity.getString(id)

    private fun connect() {
        rule.runOnUiThread { rule.activity.model.phase.value = KCallPhase.Connected }
        rule.waitForIdle()
    }

    @Test
    fun connectingShowsCancelThenCallShowsHostVideoAndHidesMissingButtons() {
        rule.onNodeWithContentDescription(text(R.string.kinetica_ui_call_cancel)).assertExists()
        connect()
        // 영상 슬롯에 넣은 호스트 TextureView가 붙었다(카메라를 켠 세 사람).
        rule.onNodeWithTag("video-ana", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("video-me", useUnmergedTree = true).assertExists()
        // 넘기지 않은 콜백의 버튼은 없다.
        rule.onAllNodesWithContentDescription(text(R.string.kinetica_ui_call_share)).assertCountEquals(0)
        rule.onNodeWithContentDescription(text(R.string.kinetica_ui_call_connected))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun micButtonReadsStateAndUpdatesHostModel() {
        connect()
        val mic = rule.onNodeWithContentDescription(text(R.string.kinetica_ui_call_mic))
        mic.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.kinetica_ui_call_state_off)))
        mic.performClick()
        rule.waitForIdle()
        assertEquals(false, rule.activity.model.people.value.first { it.local }.micOn)
        rule.onNodeWithContentDescription(text(R.string.kinetica_ui_call_mic))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.kinetica_ui_call_state_on)))
    }

    @Test
    fun controlsHideAfterIdleAndComeBackOnTap() {
        connect()
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(6_000)
        rule.onAllNodesWithContentDescription(text(R.string.kinetica_ui_call_end)).assertCountEquals(0)
        rule.mainClock.autoAdvance = true
        // 숨은 동안 타일을 누르면 고정하지 않고 막대만 꺼낸다.
        rule.onNodeWithContentDescription("Ana", substring = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(text(R.string.kinetica_ui_call_end)).performClick()
        rule.waitForIdle()
        assertEquals(1, rule.activity.model.hangUps)
    }
}

/** 부품을 직접 조합한 통화 화면. 호스트 데이터 그대로 격자에 넘기고, 좁은 도크가 접히는지 본다. */
@RunWith(AndroidJUnit4::class)
class CallPartsTest {
    @get:Rule
    val rule = createAndroidComposeRule<CallPartsActivity>()

    @Test
    fun narrowDockFoldsIntoMoreWithBadgeSumAndReportsAnchor() {
        rule.onAllNodesWithContentDescription("Share").assertCountEquals(0)
        val more = rule.activity.getString(R.string.kinetica_ui_call_more)
        rule.onNodeWithContentDescription(more).assertExists()
        rule.waitForIdle()
        // 접힌 오디오 버튼의 위치는 '더보기' 위치로 알린다.
        assertNotNull(rule.activity.audioAnchor)
        rule.onNodeWithContentDescription(more).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Share").performClick()
        rule.waitForIdle()
        assertEquals(1, rule.activity.clicks)
    }

    @Test
    fun tappingGridTileSpotlightsIntoFocusAndStrip() {
        rule.onNodeWithTag("tile-ana-Grid").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tile-ana-Focus").assertExists()
        rule.onNodeWithTag("tile-ben-Strip").assertExists()
    }
}
