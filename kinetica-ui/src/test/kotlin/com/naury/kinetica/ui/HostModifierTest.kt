package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 호스트가 컴포넌트에 넘긴 수정자(testTag, semantics 등)가 조작 동작을 가진 노드에 붙는지. 바깥 틀에만 붙으면 호스트의 UI 테스트와
 * 접근성 설정이 동작 없는 노드를 가리킨다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HostModifierTest {
    @get:Rule
    val compose = createComposeRule()

    private val clickable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
    private val adjustable = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)

    @Test
    fun `새 조작 컴포넌트는 호스트 수정자를 동작 노드에 붙인다`() {
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                KVoiceRecorderButton({}, {}, Modifier.testTag("voice"))
                KShapeMorphButton(KIcons.Heart, "좋아요", {}, Modifier.testTag("morphButton"))
                KShapeToggle(false, {}, KIcons.Star, "즐겨찾기", Modifier.testTag("toggle"))
                KCircularSlider(10f, {}, "타이머", Modifier.size(200.dp).testTag("circular"))
                KWaveform(FloatArray(10) { 0.5f }, { 0.2f }, Modifier.height(40.dp).testTag("wave"), onSeek = {}, label = "재생")
                KScratchCard(Modifier.size(200.dp, 100.dp).testTag("scratch")) { BasicText("쿠폰") }
                KMorphMenu("정렬", false, {}, listOf(KMenuEntry.Action("최신순") {}), Modifier.testTag("menu"))
                KBentoGrid(columns = 2) { KBentoCell(Modifier.testTag("cell"), onClick = {}) { BasicText("칸") } }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("voice").assert(clickable)
        compose.onNodeWithTag("morphButton").assert(clickable)
        compose.onNodeWithTag("toggle").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        compose.onNodeWithTag("circular").assert(adjustable)
        compose.onNodeWithTag("wave").assert(adjustable)
        compose.onNodeWithTag("scratch").assert(clickable)
        compose.onNodeWithTag("menu").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
        compose.onNodeWithTag("cell").assert(clickable)
    }
}
