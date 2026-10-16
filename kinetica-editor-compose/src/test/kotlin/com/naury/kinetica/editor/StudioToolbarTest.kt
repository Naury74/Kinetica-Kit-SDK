package com.naury.kinetica.editor

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w750dp-h900dp")
class StudioToolbarTest {
    @get:Rule
    val compose = createComposeRule()

    private val session = (KineticaSession.create(SceneDocument.empty(Artboard(1080.0, 1920.0))) as SessionOpenResult.Opened).session

    private val hostViews = listOf(HostViewOption("widget.chart", "Chart", 600.0, 300.0))

    @Test
    fun `넓은 화면에서는 기본 버튼과 나가기가 스크롤 없이 한 줄에 모두 보인다`() {
        var exited = false
        compose.setContent { KineticaEditor(session, hostViews = hostViews, onExit = { exited = true }) }
        val root = compose.onRoot().getBoundsInRoot()
        val exit = compose.onNodeWithText("Exit").getBoundsInRoot()
        assertThat(exit.right).isAtMost(root.right)
        assertThat(exit.left).isAtLeast(root.left)
        // 도구 줄이 스크롤되지 않아야 모든 버튼이 화면 안에 있다.
        val range = compose.onNode(hasScrollRange()).fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertThat(range.maxValue()).isEqualTo(0f)
        listOf("Rectangle", "Host view", "Motion", "Fit", "Undo").forEach { label ->
            compose.onNode(hasText(label) or hasContentDescription(label)).assertIsDisplayed()
        }
        compose.onNodeWithText("Exit").performClick()
        assertThat(exited).isTrue()
    }

    @Test
    @Config(qualifiers = "ko-w750dp-h900dp")
    fun `넓은 화면에서 한국어 이름도 한 줄에 들어간다`() {
        compose.setContent { KineticaEditor(session, hostViews = hostViews, onExit = {}) }
        val range = compose.onNode(hasScrollRange()).fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertThat(range.maxValue()).isEqualTo(0f)
    }

    @Test
    @Config(qualifiers = "w360dp-h700dp")
    fun `좁은 화면에서는 이름을 보이고 가로로 스크롤한다`() {
        compose.setContent { KineticaEditor(session, onExit = {}) }
        val range = compose.onNode(hasScrollRange()).fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertThat(range.maxValue()).isGreaterThan(0f)
        compose.onNodeWithText("Rectangle").assertIsDisplayed()
        compose.onNodeWithText("Exit").assertIsDisplayed()
    }

    private fun tool(label: String) = compose.onNode(hasText(label) or hasContentDescription(label))

    private fun hasScrollRange() =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyAncestor(hasTestTag(TOOLBAR_TAG))

    @Test
    fun `호스트 버튼이 많아 스크롤되어도 나가기는 끝에 고정된다`() {
        compose.setContent {
            KineticaEditor(session, onExit = {}) {
                repeat(EXTRA_BUTTONS) { StudioToolbarButton("Extra $it", onClick = {}) }
            }
        }
        val bar = compose.onNodeWithTag(TOOLBAR_TAG).getBoundsInRoot()
        val exit = compose.onNodeWithText("Exit").getBoundsInRoot()
        assertThat(bar.right - exit.right).isAtMost(12.dp)
        assertThat(exit.left).isGreaterThan(bar.left)
    }

    @Test
    fun `실행 취소와 다시 실행은 아이콘 버튼이고 접근성 이름으로 찾는다`() {
        compose.setContent { KineticaEditor(session) }
        tool("Rectangle").performClick()
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.waitForIdle()
        assertThat(session.document.value.nodes).isEmpty()
        compose.onNodeWithContentDescription("Redo").performClick()
        compose.waitForIdle()
        assertThat(session.document.value.nodes).hasSize(1)
    }

    @Test
    fun `레이어 표시와 잠금은 아이콘 토글이고 이름과 상태를 알린다`() {
        compose.setContent { KineticaEditor(session) }
        tool("Ellipse").performClick()
        compose.waitForIdle()
        val visible = compose.onAllNodes(isToggleable() and hasContentDescription("Visible")).onFirst()
        val locked = compose.onAllNodes(isToggleable() and hasContentDescription("Locked")).onFirst()
        visible.assertIsOn()
        locked.assertIsOff()
        locked.performClick()
        visible.performClick()
        compose.waitForIdle()
        visible.assertIsOff()
        locked.assertIsOn()
    }

    @Test
    fun `모션 버튼은 켜고 끄는 토글로 알린다`() {
        compose.setContent { KineticaEditor(session, theme = StudioTheme.light()) }
        val motion = tool("Motion")
        motion.assertIsOff()
        motion.performClick()
        motion.assertIsOn()
    }

    private companion object {
        const val EXTRA_BUTTONS = 12
    }
}
