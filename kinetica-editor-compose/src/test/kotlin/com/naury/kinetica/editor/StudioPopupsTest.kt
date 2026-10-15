package com.naury.kinetica.editor

import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp")
class StudioPopupsTest {
    @get:Rule
    val compose = createComposeRule()

    private val title = SceneNode(NodeId("title"), NodeContent.Text(Size(400.0, 80.0), "Hello", fontSize = 40.0))
    private val session = (
        KineticaSession.create(
            SceneDocument("popups", Artboard(600.0, 800.0), mapOf(title.id to title), listOf(title.id)),
        ) as SessionOpenResult.Opened
        ).session

    private fun text() = (session.document.value.node(NodeId("title"))!!.content as NodeContent.Text).text

    private fun openTextDialog() {
        compose.setContent { KineticaEditor(session) }
        session.select(listOf(NodeId("title")))
        compose.onNodeWithText("Edit text").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Suppress("DEPRECATION")
    private fun pressBackOnDialog() {
        compose.runOnUiThread { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.waitForIdle()
    }

    // 맨 위 창(메뉴 팝업)에 뒤로 가기 키를 보낸다.
    private fun pressBackOnTopWindow() {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<*>
        val top = views.last() as View
        compose.runOnUiThread {
            top.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            top.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        compose.waitForIdle()
    }

    @Test
    fun `텍스트 편집 다이얼로그에 입력하고 확인하면 바뀌고 취소하면 그대로다`() {
        openTextDialog()
        compose.onNodeWithTag(TEXT_EDIT_TAG).performTextReplacement("Bye")
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(TEXT_EDIT_TAG).assertDoesNotExist()
        assertThat(text()).isEqualTo("Hello")

        compose.onNodeWithText("Edit text").performScrollTo().performClick()
        compose.onNodeWithTag(TEXT_EDIT_TAG).performTextReplacement("Bye")
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        assertThat(text()).isEqualTo("Bye")
    }

    @Test
    fun `텍스트 편집 다이얼로그는 뒤로 가기로 닫히고 문서를 바꾸지 않는다`() {
        openTextDialog()
        compose.onNodeWithTag(TEXT_EDIT_TAG).assertIsDisplayed().performTextReplacement("Bye")

        pressBackOnDialog()

        compose.onNodeWithTag(TEXT_EDIT_TAG).assertDoesNotExist()
        assertThat(text()).isEqualTo("Hello")
    }

    @Test
    fun `잘못된 값을 넣으면 입력란 아래에 안내가 보이고 값은 되돌아간다`() {
        openTextDialog()
        val size = compose.onAllNodes(hasSetTextAction() and androidx.compose.ui.test.hasText("Font size")).onFirst()
        size.performTextReplacement("abc")
        compose.onNodeWithTag(TEXT_EDIT_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Invalid value. The previous value was kept.").assertExists()
    }

    @Test
    fun `메뉴는 항목을 누르면 닫히며 실행하고 뒤로 가기로도 닫힌다`() {
        compose.setContent { KineticaEditor(session) }
        compose.onNodeWithText("More shapes").performScrollTo().performClick()
        compose.onNodeWithText("Star").assertIsDisplayed()
        pressBackOnTopWindow()
        compose.onNodeWithText("Star").assertDoesNotExist()
        assertThat(session.document.value.nodes).hasSize(1)

        compose.onNodeWithText("More shapes").performScrollTo().performClick()
        compose.onNodeWithText("Star").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Star").assertDoesNotExist()
        assertThat(session.document.value.nodes).hasSize(2)
    }

    @Test
    fun `좁은 화면의 패널 시트는 손잡이를 끌어 내리거나 뒤로 가기로 닫힌다`() {
        compose.setContent { KineticaEditor(session) }
        compose.onNodeWithText("Layers").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        // 패널에 레이어 이름이 보인다.
        compose.onAllNodesWithText("Text").onFirst().assertExists()
        pressBackOnDialog()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()

        compose.onNodeWithText("Layers").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.onNodeWithContentDescription("Close").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 600f, durationMillis = 120)
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun `시트 손잡이를 조금만 끌었다 놓으면 닫히지 않고 제자리로 돌아간다`() {
        compose.setContent { KineticaEditor(session) }
        compose.onNodeWithText("Layers").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        val handle = compose.onNodeWithContentDescription("Close")
        val before = handle.fetchSemanticsNode().boundsInRoot.top

        handle.performTouchInput { swipeDown(startY = centerY, endY = centerY + 40f, durationMillis = 800) }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()

        handle.assertIsDisplayed()
        assertThat(handle.fetchSemanticsNode().boundsInRoot.top).isWithin(1f).of(before)
    }

    @Test
    @Config(qualifiers = "w900dp-h800dp")
    fun `시트를 연 채로 화면을 펴서 넓어지면 패널이 붙고 다시 접어도 시트가 저절로 뜨지 않으며 선택은 그대로다`() {
        val width = mutableStateOf(400.dp)
        compose.setContent { KineticaEditor(session, Modifier.width(width.value).fillMaxHeight()) }
        session.select(listOf(NodeId("title")))
        compose.onNodeWithText("Layers").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()

        width.value = 800.dp
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
        compose.onNodeWithText("Properties").assertIsDisplayed()

        width.value = 400.dp
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
        assertThat(session.selection.value.selectedIds).containsExactly(NodeId("title"))
    }

    private companion object {
        const val SETTLE_MS = 1_000L
    }
}
