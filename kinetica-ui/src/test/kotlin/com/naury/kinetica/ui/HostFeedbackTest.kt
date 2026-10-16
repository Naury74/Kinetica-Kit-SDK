package com.naury.kinetica.ui

import android.app.Activity
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 호스트 앱(영상 통화 앱)이 붙여 보고 보낸 피드백: 세그먼트 줄 바꿈과 색, 직접 만든 판 안의 글래스, 시트가 밀어낼 때 상태 표시줄 아이콘. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HostFeedbackTest {
    @get:Rule
    val compose = createComposeRule()

    private val longLabels = listOf("Dev token server", "Existing token", "Token endpoint")

    @Composable
    private fun FontScale(scale: Float, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, scale), content = content)
    }

    @Test
    fun `큰 글꼴에서 긴 세그먼트 이름은 줄을 바꾸고 컨트롤이 높아진다`() {
        compose.setContent {
            FontScale(1.5f) {
                Column(Modifier.width(360.dp).background(Color(0xFF8E9AAF)).padding(12.dp)) {
                    KSegmentedControl(longLabels, 0, {}, Modifier.fillMaxWidth().testTag("two"), surface = SurfaceStyles.CleanLight)
                    KSegmentedControl(
                        longLabels,
                        0,
                        {},
                        Modifier.fillMaxWidth().padding(top = 12.dp).testTag("three"),
                        surface = SurfaceStyles.CleanLight,
                        maxLines = 3,
                    )
                    KSegmentedControl(
                        longLabels,
                        0,
                        {},
                        Modifier.fillMaxWidth().padding(top = 12.dp).testTag("one"),
                        surface = SurfaceStyles.CleanLight,
                        maxLines = 1,
                    )
                }
            }
        }
        val two = compose.onNodeWithTag("two").getBoundsInRoot()
        assertThat(two.bottom - two.top).isGreaterThan(60.dp)
        val one = compose.onNodeWithTag("one").getBoundsInRoot()
        assertThat(one.bottom - one.top).isEqualTo(44.dp)

        // 두 번째 컨트롤(maxLines = 3)의 이름은 잘리지 않는다.
        longLabels.forEach { label ->
            val layout = layoutOf(label, index = 1)
            assertThat(layout.hasVisualOverflow).isFalse()
        }
        assertThat(layoutOf("Existing token", index = 0).lineCount).isEqualTo(2)
        // 기본 두 줄에서도 단어 중간에서 끊기지 않는다.
        assertThat(layoutOf("Token endpoint", index = 0).lineCount).isEqualTo(2)
        assertThat(layoutOf("Token endpoint", index = 0).getLineEnd(0, visibleEnd = true)).isEqualTo("Token".length)
    }

    private fun layoutOf(text: String, index: Int): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onAllNodesWithText(text, useUnmergedTree = true)[index]
            .fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    @Test
    fun `비례 칸 폭은 남는 폭을 고르게 더하고 모자라면 내용 비율로 줄인다`() {
        assertThat(segmentWidths(intArrayOf(100, 50, 50), 300, KSegmentSizing.Proportional).toList()).containsExactly(134, 83, 83).inOrder()
        assertThat(segmentWidths(intArrayOf(300, 100), 200, KSegmentSizing.Proportional).toList()).containsExactly(150, 50).inOrder()
        assertThat(segmentWidths(intArrayOf(300, 100, 10), 200, KSegmentSizing.Equal).toList()).containsExactly(67, 67, 66).inOrder()
        assertThat(segmentWidths(intArrayOf(7, 3, 1), 101, KSegmentSizing.Proportional).sum()).isEqualTo(101)
        // 가운데 칸의 가장 긴 단어가 같은 폭 나누기보다 넓으면 여유 있는 칸에서 덜어 준다.
        val fitted = segmentWidths(intArrayOf(200, 200, 200), 300, KSegmentSizing.Equal, minimum = intArrayOf(40, 130, 60))
        assertThat(fitted.sum()).isEqualTo(300)
        assertThat(fitted[1]).isAtLeast(130)
        assertThat(fitted[0]).isAtLeast(40)
        assertThat(fitted[2]).isAtLeast(60)
        // 단어 폭 합이 전체보다 크면 그대로 같은 폭으로 둔다.
        assertThat(
            segmentWidths(intArrayOf(1, 1), 100, KSegmentSizing.Equal, minimum = intArrayOf(80, 80)).toList(),
        ).containsExactly(50, 50)
    }

    @Test
    fun `비례 칸 폭이면 긴 이름 칸이 넓고 RTL에서도 고른 칸 위에 인디케이터가 놓인다`() {
        var picked = 0
        val rtl = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.width(360.dp).background(Color.White).padding(12.dp)) {
                    KSegmentedControl(
                        listOf("A", "Very long option name", "B"),
                        picked,
                        { picked = it },
                        Modifier.fillMaxWidth().testTag("seg"),
                        surface = SurfaceStyles.CleanLight,
                        tone = KTone.Accent,
                        sizing = KSegmentSizing.Proportional,
                    )
                }
            }
        }
        val short = compose.onNodeWithText("A").getBoundsInRoot()
        val long = compose.onNodeWithText("Very long option name").getBoundsInRoot()
        assertThat(long.right - long.left).isGreaterThan((short.right - short.left) * 3)

        compose.onNodeWithText("Very long option name").performClick()
        assertThat(picked).isEqualTo(1)
        compose.runOnIdle { rtl.value = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        val pixels = compose.onNodeWithTag("seg").captureToImage().toPixelMap()
        // RTL이면 1번 칸은 가운데 그대로이고, 칸 가운데는 강조색으로 채워진다.
        val accent = KineticaColors().accent
        // 글자를 피해 인디케이터 위쪽 가장자리 근처를 본다.
        val y = pixels.height / 5
        val middle = pixels[pixels.width / 2, y]
        val edge = pixels[pixels.width / 10, y]
        assertThat(distance(middle, accent)).isLessThan(0.25f)
        assertThat(distance(edge, accent)).isGreaterThan(0.4f)
    }

    @Test
    fun `세그먼트 역할 기본값은 라디오 버튼이고 탭으로 바꿀 수 있다`() {
        compose.setContent {
            Column {
                KSegmentedControl(listOf("Light", "Dark"), 0, {})
                KSegmentedControl(listOf("Feed", "Map"), 1, {}, role = Role.Tab)
            }
        }
        compose.onNodeWithText("Light").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithText("Map").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    }

    /** 빨간 배경 위에 거의 흰 글래스 판을 놓고, 그 안에 옅은 글래스 카드를 둔다. 카드 가운데 색을 돌려준다. */
    private fun cardCenter(layered: Boolean): Color {
        val panel = GlassStyles.Liquid.copy(tint = Color.White.copy(alpha = 0.92f), shadow = 0.dp)
        val card = GlassStyles.Liquid.copy(tint = Color.White.copy(alpha = 0.1f), shadow = 0.dp)
        compose.setContent {
            KineticaScaffold(
                Modifier.size(320.dp, 320.dp),
                surface = SurfaceStyle.Glass(GlassStyles.Liquid),
                background = { Box(Modifier.fillMaxSize().background(Color.Red)) },
                edgeFade = false,
            ) {
                Box(Modifier.padding(24.dp).size(260.dp).glass(panel, RoundedCornerShape(28.dp)).padding(40.dp)) {
                    KGlassLayer(enabled = layered) {
                        Box(Modifier.size(180.dp).glass(card, RoundedCornerShape(16.dp)).testTag("card")) {
                            BasicText("card", Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("card").captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height * 3 / 4]
    }

    @Test
    fun `직접 만든 판 안의 글래스 카드는 KGlassLayer로 감싸면 판을 뚫고 배경을 비추지 않는다`() {
        val through = cardCenter(layered = false)
        // 감싸지 않으면 카드가 판을 건너뛰고 빨간 배경을 그대로 비춘다.
        assertThat(through.red).isGreaterThan(0.8f)
        assertThat(through.green).isLessThan(0.4f)
    }

    @Test
    fun `KGlassLayer 안의 글래스 카드는 판 위 한 겹으로 그린다`() {
        val layered = cardCenter(layered = true)
        assertThat(layered.green).isGreaterThan(0.75f)
        assertThat(layered.blue).isGreaterThan(0.75f)
    }

    private fun View.lightStatusBars(): Boolean {
        val window = (context as Activity).window
        return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
    }

    private fun View.lightNavigationBars(): Boolean {
        val window = (context as Activity).window
        return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars
    }

    @Test
    fun `시트가 콘텐츠를 밀어내는 동안 상태 표시줄 아이콘을 검은 배경에 맞추고 닫으면 되돌린다`() {
        lateinit var view: View
        val open = mutableStateOf(false)
        compose.setContent {
            view = LocalView.current
            // 밝은 글래스 화면. 아이콘은 호스트가 정한다(systemBarIcons = false).
            KineticaScaffold(Modifier.fillMaxSize(), background = { Box(Modifier.fillMaxSize().background(Color.White)) }) {
                KSheet(open.value, { open.value = false }) { BasicText("sheet", Modifier.padding(24.dp)) }
            }
        }
        compose.runOnIdle {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
        compose.runOnIdle { open.value = true }
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isFalse()
        // 내비게이션 바는 시트 판 뒤에 있으므로 건드리지 않는다.
        assertThat(view.lightNavigationBars()).isTrue()

        compose.runOnIdle { open.value = false }
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isTrue()
    }

    @Test
    fun `밝은 backdropColor면 시트가 밀어내도 어두운 아이콘을 쓴다`() {
        lateinit var view: View
        val open = mutableStateOf(false)
        compose.setContent {
            view = LocalView.current
            KineticaScaffold(Modifier.fillMaxSize(), surface = SurfaceStyles.CleanDark, backdropColor = Color(0xFFF2F2F7)) {
                KSheet(open.value, { open.value = false }) { BasicText("sheet", Modifier.padding(24.dp)) }
            }
        }
        compose.waitForIdle()
        // 어두운 단색 표면이라 처음에는 밝은 아이콘이다.
        assertThat(view.lightStatusBars()).isFalse()
        compose.runOnIdle { open.value = true }
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isTrue()
        compose.runOnIdle { open.value = false }
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isFalse()
    }

    private fun distance(a: Color, b: Color): Float = kotlin.math.sqrt(
        (a.red - b.red) * (a.red - b.red) + (a.green - b.green) * (a.green - b.green) + (a.blue - b.blue) * (a.blue - b.blue),
    )
}
