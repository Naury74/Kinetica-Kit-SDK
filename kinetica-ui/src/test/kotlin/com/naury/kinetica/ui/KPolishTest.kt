package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class KPolishTest {
    @get:Rule
    val compose = createComposeRule()

    private val dot: ImageVector = ImageVector.Builder("dot", 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes("M6,6h12v12h-12z"), fill = SolidColor(androidx.compose.ui.graphics.Color.Black)).build()

    @Test
    fun `큰 제목 아래 줄은 접히면 작은 바 바로 밑으로 따라 올라간다`() {
        lateinit var scrollTo: () -> Unit
        compose.setContent {
            val scroll = rememberScrollState()
            val state = rememberKLargeTitleState(scroll)
            val scope = rememberCoroutineScope()
            scrollTo = { scope.launch { scroll.scrollTo(scroll.maxValue) } }
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) { Box(Modifier.height(3000.dp)) }
                KLargeTitleBar(state, "제목", bottom = { BasicText("아래 줄", Modifier.testTag("bottom")) })
            }
        }
        val before = compose.onNodeWithTag("bottom").getUnclippedBoundsInRoot().top
        compose.runOnIdle { scrollTo() }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("bottom").getUnclippedBoundsInRoot().top
        assertThat(after).isLessThan(before)
        assertThat(after.value).isAtLeast(0f)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1200dp-h800dp")
    fun `넓은 화면에서 온보딩 버튼은 가운데 폭을 넘지 않는다`() {
        compose.setContent { KOnboarding(listOf(OnboardingPage("One", "First")), onFinish = {}) }
        val bounds = compose.onNodeWithContentDescription("Get started").getUnclippedBoundsInRoot()
        assertThat((bounds.right - bounds.left).value).isAtMost(600f)
        assertThat(bounds.left.value).isGreaterThan(300f)
    }

    @Test
    fun `아이콘 버튼은 44dp 원이고 이름과 역할을 알린다`() {
        var clicked = false
        compose.setContent { KIconButton(dot, "공유", { clicked = true }, Modifier.testTag("icon")) }
        compose.onNodeWithTag("icon")
            .assertWidthIsEqualTo(44.dp)
            .assertHeightIsEqualTo(44.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithContentDescription("공유").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun `누를 수 있는 KSurface는 버튼으로 동작한다`() {
        var clicks = 0
        compose.setContent { KSurface(onClick = { clicks++ }) { BasicText("카드") } }
        compose.onNodeWithText("카드").performClick()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `스테퍼는 최소 터치 높이를 지킨다`() {
        compose.setContent { KStepper(1, {}, Modifier.testTag("stepper")) }
        compose.onNodeWithTag("stepper").assertHeightIsEqualTo(44.dp)
    }

    @Test
    fun `토스트는 KineticaScaffold 상단 바 아래에 뜬다`() {
        val toast = KToastState()
        compose.setContent {
            KineticaScaffold(topBar = { Box(Modifier.testTag("bar").fillMaxWidth().height(100.dp)) }) {
                KToastHost(toast)
            }
            androidx.compose.runtime.LaunchedEffect(Unit) { toast.show("저장했습니다") }
        }
        compose.mainClock.advanceTimeBy(600)
        val barBottom = compose.onNodeWithTag("bar").getUnclippedBoundsInRoot().bottom
        val top = compose.onNodeWithText("저장했습니다").getUnclippedBoundsInRoot().top
        assertThat(top).isAtLeast(barBottom)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h360dp")
    fun `세로가 낮은 가로 화면에서는 탭 바가 낮아진다`() {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                KTabBar(listOf(KTab("Home", dot), KTab("Star", dot)), 0, {}, Modifier.testTag("tabs"))
            }
        }
        val bounds = compose.onNodeWithTag("tabs").getUnclippedBoundsInRoot()
        assertThat((bounds.bottom - bounds.top).value).isAtMost(64f)
    }
}
