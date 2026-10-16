package com.example.consumer

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.window.layout.FoldingFeature as WindowFoldingFeature
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import com.example.consumer.recent.WideHostActivity
import com.example.consumer.recent.WideHostScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * 시나리오 10: 넓은 화면 도구(rememberGlassWindowLayout, glassReadableWidth, GlassTabletopLayout)를 쓰는 호스트 화면을
 * 좁은 크기와 넓은 크기로 강제해 본다. ForcedSize는 배치 폭만 바꾸고 창 정보(LocalWindowInfo)는 그대로 두므로,
 * 창 크기로 정하는 rememberGlassWindowLayout은 WindowSize로 시험한다.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WideScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var size by mutableStateOf(DpSize(400.dp, 800.dp))

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun windowInfo(): String = rule.onNodeWithTag("windowInfo").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()

    @Test
    fun readableWidthUsesFullWidthWhenNarrowAndCentersWhenWide() {
        rule.setContent { MaterialTheme { DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) { WideHostScreen() } } }
        rule.waitForIdle()
        val narrowBox = bounds("controls")
        val narrowText = bounds("readable")
        assertEquals("좁은 화면에서는 글이 받은 폭을 다 써야 한다", narrowBox.width, narrowText.width, 1.5f)

        rule.runOnIdle { size = DpSize(1200.dp, 800.dp) }
        rule.waitForIdle()
        val box = bounds("controls")
        val text = bounds("readable")
        val ratio = text.width / box.width
        assertEquals("넓은 화면에서는 640dp/1200dp 폭이어야 한다: $ratio", 640f / 1200f, ratio, 0.01f)
        val leftGap = text.left - box.left
        val rightGap = box.right - text.right
        assertTrue("가운데에 놓여야 한다: $leftGap / $rightGap", abs(leftGap - rightGap) <= 2f)
    }

    @Test
    fun tabletopLayoutWithoutHingeStacksTopThenFillsRest() {
        rule.setContent { MaterialTheme { DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) { WideHostScreen() } } }
        rule.waitForIdle()
        for (forced in listOf(DpSize(400.dp, 800.dp), DpSize(1200.dp, 700.dp))) {
            rule.runOnIdle { size = forced }
            rule.waitForIdle()
            val video = bounds("video")
            val controls = bounds("controls")
            val whole = bounds("tabletop")
            assertEquals("$forced: 접힌 선이 없으면 아래 칸은 위 칸 바로 아래에서 시작한다", video.bottom, controls.top, 1.5f)
            assertEquals("$forced: 아래 칸이 남은 높이를 채운다", whole.bottom, controls.bottom, 1.5f)
        }
    }

    @Test
    fun windowLayoutFollowsWindowSizeOverride() {
        rule.setContent { MaterialTheme { DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(size)) { WideHostScreen() } } }
        rule.waitForIdle()
        assertTrue("400dp는 Compact여야 한다: ${windowInfo()}", windowInfo().contains("구간 Compact 열 1"))
        rule.runOnIdle { size = DpSize(700.dp, 400.dp) }
        rule.waitForIdle()
        assertTrue("700dp는 Medium이고 높이 400dp는 짧다: ${windowInfo()}", windowInfo().contains("구간 Medium 열 2") && windowInfo().contains("짧음 true"))
        rule.runOnIdle { size = DpSize(1200.dp, 800.dp) }
        rule.waitForIdle()
        assertTrue("1200dp는 Expanded, 280dp 칸 4열이어야 한다: ${windowInfo()}", windowInfo().contains("구간 Expanded 열 4") && windowInfo().contains("자세 Flat"))
    }
}

/** 시나리오 10(이어서): 폴더블을 반쯤 접어 탁자 모드가 되면 영상은 접힌 선 위, 조작부는 아래에 놓인다. 접힌 선 정보는 window-testing으로 흘려 넣는다. */
@RunWith(AndroidJUnit4::class)
class TabletopHostTest {
    private val publisher = WindowLayoutInfoPublisherRule()
    private val compose = createAndroidComposeRule<WideHostActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(publisher).around(compose)

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow

    private fun windowInfo(): String = compose.onNodeWithTag("windowInfo").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()

    @Test
    fun halfOpenedHorizontalFoldSplitsAtHinge() {
        compose.waitTag("controls")
        val fold = FoldingFeature(
            compose.activity,
            size = 40,
            state = WindowFoldingFeature.State.HALF_OPENED,
            orientation = WindowFoldingFeature.Orientation.HORIZONTAL,
        )
        publisher.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(fold)))
        assertTrue("탁자 모드로 읽히지 않았다: ${windowInfo()}", compose.becomes { windowInfo().contains("자세 Tabletop") })
        compose.waitForIdle()
        val hinge = fold.bounds
        val controls = bounds("controls")
        assertEquals("조작부는 접힌 선 바로 아래에서 시작해야 한다", hinge.bottom.toFloat(), controls.top, 1.5f)
        assertTrue("영상 칸은 접힌 선 위에 있어야 한다", bounds("video").bottom <= hinge.top + 1f)

        // 다시 펴면 위 칸은 내용 높이로 돌아간다.
        publisher.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(FoldingFeature(compose.activity, state = WindowFoldingFeature.State.FLAT))))
        assertTrue(compose.becomes { windowInfo().contains("자세 Flat") })
        compose.waitForIdle()
        assertEquals(bounds("video").bottom, bounds("controls").top, 1.5f)
    }
}
