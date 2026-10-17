package com.naury.kinetica.showcase

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.ui.KTransitions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 화면 이동 데모와 장면 전환 데모의 핵심 흐름 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class TransitionDemosTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `빠지는 화면은 사라질 때까지 들어올 때의 전환을 쓰고 그 뒤 맨 위 화면의 전환으로 돌아간다`() {
        val nav = NavDemoState()
        nav.push("settings")
        nav.push("modal")
        assertThat(nav.active).isEqualTo("modal")
        nav.pop()
        // 아직 사라지는 중이라 덮을 때 쓴 전환을 되감는다.
        assertThat(nav.active).isEqualTo("modal")
        assertThat(nav.stack).containsExactly(ROOT, "settings").inOrder()
        nav.onScreenGone("modal")
        assertThat(nav.active).isEqualTo("settings")
        assertThat(nav.transitionFor(nav.active)).isSameInstanceAs(KTransitions.CardStack)
        // 아래에 남은 화면이 잠시 빠지는 것은 무시한다.
        nav.onScreenGone(ROOT)
        assertThat(nav.active).isEqualTo("settings")
    }

    @Test
    fun `이미 스택에 있는 화면은 다시 넣지 않는다`() {
        val nav = NavDemoState()
        nav.push("story/1")
        nav.push("story/1")
        assertThat(nav.stack).containsExactly(ROOT, "story/1").inOrder()
    }

    @Test
    fun `예시 화면의 다음 화면은 모두 정의되어 있다`() {
        EXAMPLES.forEach { assertThat(EXAMPLE_PAGES).containsKey(it.entry) }
        EXAMPLE_PAGES.values.flatMap {
            it.actions
        }.filter { it.target != "back" }.forEach { assertThat(EXAMPLE_PAGES).containsKey(it.target) }
    }

    @Test
    fun `고른 전환으로 목록 화면에 들어가고 뒤로 가기로 돌아온다`() {
        compose.setContent { NavigationDemo {} }
        compose.waitForIdle()
        compose.onNodeWithText("Cube").performClick()
        compose.onNodeWithText("알림").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("1단계, Cube").assertExists()
        pressBack()
        compose.onNodeWithText("전환 고르기").assertExists()
        assertThat(compose.onAllNodesWithText("1단계, Cube").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `예시 칸에서 두 단계 들어가면 각 화면의 전환 이름이 보인다`() {
        compose.setContent { NavigationDemo {} }
        compose.waitForIdle()
        compose.onAllNodes(hasScrollAction())[0].performScrollToKey("examples")
        compose.onNodeWithText("새 글 작성").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("사진 첨부").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("SheetStack 전환으로 열렸습니다").assertExists()
        pressBack()
        compose.onNodeWithText("사진 첨부").assertExists()
    }

    @Test
    fun `번들 장면의 버튼으로 다음 화면에 가고 뒤로 가기로 이전 화면에 돌아온다`() {
        compose.setContent { TransitionDemo {} }
        compose.waitForIdle()
        compose.onNodeWithText("홈").assertExists()
        compose.onNodeWithContentDescription("에어 러너 보기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("홈 → 상품").assertExists()
        compose.onNodeWithContentDescription("구매하기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("홈 → 상품 → 결제").assertExists()
        pressBack()
        compose.onNodeWithText("홈 → 상품").assertExists()
    }

    @Test
    fun `번들 세 화면은 읽히고 상품 그림 노드를 함께 가진다`() {
        assertThat(TransitionScenes.documents.keys).containsExactlyElementsIn(TransitionScenes.order)
        TransitionScenes.documents.values.forEach { assertThat(it.nodes).containsKey(NodeId("hero")) }
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
