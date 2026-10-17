package com.naury.kinetica.showcase

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 첫 화면 → UI 키트 허브 → 데모로 들어가는 경로, 뒤로 가기, 다시 만들어질 때 남는 위치와 모양 설정을 본다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class ShowcaseNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `첫 화면에서 UI 키트 허브를 거쳐 컴포넌트 탭으로 들어가고 뒤로 가기로 한 단계씩 나온다`() {
        compose.setContent { Showcase() }
        compose.waitForIdle()
        compose.onNodeWithText("고급 기능").assertExists()
        openKit()
        compose.onNodeWithText("분류를 골라 데모로 들어갑니다").assertExists()

        list().performScrollToNode(hasText("입력"))
        compose.onNodeWithText("입력").performClick()
        compose.waitForIdle()
        // 입력 탭으로 바로 열려 그 탭의 카드가 보인다.
        compose.onNodeWithText("폼과 목록").assertExists()

        pressBack()
        compose.onNodeWithText("분류를 골라 데모로 들어갑니다").assertExists()
        pressBack()
        compose.onNodeWithText("고급 기능").assertExists()
        assertThat(compose.onAllNodesWithText("분류를 골라 데모로 들어갑니다").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `허브 스크롤 위치는 데모에 들어갔다 나와도 남는다`() {
        compose.setContent { Showcase() }
        compose.waitForIdle()
        openKit()
        list().performScrollToNode(hasText("장면 전환"))
        compose.onNodeWithText("장면 전환").performClick()
        compose.waitForIdle()
        pressBack()
        // 처음 위치였다면 맨 아래 분류는 화면 밖이다.
        compose.onNodeWithText("장면 전환").assertIsDisplayed()
    }

    @Test
    fun `다시 만들어져도 허브에 머물고 고른 모양이 남는다`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Showcase() }
        compose.waitForIdle()
        compose.onNodeWithText("글래스").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("단색")[0].performClick()
        compose.waitForIdle()
        pressBack()
        openKit()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithText("분류를 골라 데모로 들어갑니다").assertExists()
        pressBack()
        compose.onNodeWithText("단색").assertExists()
    }

    @Test
    fun `모양은 한 곳에서만 고르고 표면과 디자인 화면의 프리셋을 누르면 전역 설정이 바뀐다`() {
        val look = ShowcaseLook()
        compose.setContent { Showcase(look) }
        compose.waitForIdle()
        openKit()
        compose.onNodeWithText(Demo.Surfaces.summary).performClick()
        compose.waitForIdle()
        // 이 화면에는 표면 종류나 밝기를 따로 고르는 칩이 없다.
        assertThat(compose.onAllNodesWithText("라이트").fetchSemanticsNodes()).isEmpty()
        list().performScrollToNode(hasText("Acrylic"))
        compose.onNodeWithText("Acrylic").performClick()
        compose.waitForIdle()
        assertThat(look.family).isEqualTo(SurfaceFamily.Glass)
        assertThat(look.selectedPreset?.name).isEqualTo("Acrylic")
        compose.onNodeWithContentDescription("모양").assertExists()
    }

    @Test
    fun `허브 열 수는 폭과 칸 수에 맞춘다`() {
        // 폰에서도 작은 칸은 두 열, 넓은 화면은 최대 열 수까지, 큰 칸은 칸 수보다 많은 열을 만들지 않는다.
        assertThat(columnsFor(320.dp, 150.dp, 4)).isEqualTo(2)
        assertThat(columnsFor(320.dp, 240.dp, 4)).isEqualTo(1)
        assertThat(columnsFor(800.dp, 150.dp, 4)).isEqualTo(4)
        assertThat(columnsFor(1080.dp, 340.dp, 2)).isEqualTo(2)
        assertThat(columnsFor(600.dp, 340.dp, 2)).isEqualTo(1)
    }

    @Test
    fun `경로는 데모와 컴포넌트 탭을 되살린다`() {
        assertThat(ShowcaseRoutes.demoOf(ShowcaseRoutes.componentsTab(3))).isEqualTo(Demo.Components)
        assertThat(ShowcaseRoutes.tabOf(ShowcaseRoutes.componentsTab(3))).isEqualTo(3)
        assertThat(ShowcaseRoutes.demoOf(ShowcaseRoutes.KIT)).isNull()
        assertThat(ShowcaseRoutes.depth(ShowcaseRoutes.demo(Demo.Board))).isEqualTo(1)
        assertThat(ShowcaseRoutes.depth(ShowcaseRoutes.demo(Demo.Motion))).isEqualTo(2)
    }

    private fun openKit() {
        compose.onNodeWithText("둘러보기").performClick()
        compose.waitForIdle()
    }

    private fun list() = compose.onAllNodes(hasScrollAction())[0]

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
