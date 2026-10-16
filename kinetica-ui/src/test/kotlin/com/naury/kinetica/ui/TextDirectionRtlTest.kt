package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RTL 화면에서 한국어처럼 LTR인 문구는 문단 방향을 내용으로 정해야 끝의 마침표가 앞으로 가지 않는다.
 * 별도 대화 상자나 팝업 창은 Robolectric에서 RTL이 풀리지 않으므로(supportsRtl 없음) 모두 GlassScaffold 안 같은 창에 그린다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xxhdpi")
class TextDirectionRtlTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var scope: CoroutineScope

    private fun showRtl(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                GlassScaffold(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.White))
                    content()
                }
            }
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
    }

    private fun assertPeriodAtEnd(text: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        // 팝업 위 글자는 노드만 캡처하지 못할 때가 있어 화면 전체를 캡처하고 글자 영역만 본다.
        val bounds = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val area = IntRect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        assertWithMessage(text).that(periodSide(pixels, area)).isEqualTo(Side.Right)
    }

    @Test
    fun `기능 안내 제목과 설명`() {
        val state = CoachMarkState()
        showRtl {
            Box(Modifier.size(40.dp).coachMarkTarget(state, "a"))
            CoachMarkHost(state, listOf(CoachStep("a", "검색.", "여기서 찾아요.")))
        }
        state.start()

        assertPeriodAtEnd("검색.")
        assertPeriodAtEnd("여기서 찾아요.")
    }

    @Test
    fun `토스트와 스낵바`() {
        val toast = GlassToastState()
        val snackbar = GlassSnackbarState()
        showRtl {
            GlassToastHost(toast)
            GlassSnackbarHost(snackbar)
        }
        scope.launch { toast.show("저장했어요.", durationMs = 60_000) }
        scope.launch { snackbar.show("사진을 지웠어요.", actionLabel = "되돌리기", durationMs = 60_000) }

        assertPeriodAtEnd("저장했어요.")
        assertPeriodAtEnd("사진을 지웠어요.")
    }

    @Test
    fun `툴팁`() {
        showRtl {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.Center).size(48.dp).glassTooltip("필터를 바꿔요.", visible = true, onDismiss = {}))
            }
        }

        assertPeriodAtEnd("필터를 바꿔요.")
    }

    @Test
    fun `온보딩`() {
        showRtl { GlassOnboarding(listOf(OnboardingPage("환영해요.", "시작해 볼까요.")), onFinish = {}) }

        assertPeriodAtEnd("환영해요.")
        assertPeriodAtEnd("시작해 볼까요.")
    }

    @Test
    fun `빈 화면`() {
        showRtl { GlassEmptyState("결과 없음.", message = "다시 찾아보세요.") }

        assertPeriodAtEnd("결과 없음.")
        assertPeriodAtEnd("다시 찾아보세요.")
    }

    @Test
    fun `방향을 직접 정한 글자는 그대로 둔다`() {
        assertThat(TextStyle().withContentDirection().textDirection).isEqualTo(TextDirection.Content)
        assertThat(TextStyle(textDirection = TextDirection.Ltr).withContentDirection().textDirection).isEqualTo(TextDirection.Ltr)
    }

    private enum class Side { Left, Right, Unknown }

    /**
     * 글자 잉크의 왼쪽 끝 묶음과 오른쪽 끝 묶음 가운데 아래쪽에만 잉크가 있는 쪽(마침표)을 찾는다. 배경은 맨 윗줄에서 가장 많은 색으로 본다.
     * 한글 글자는 위아래로 잉크가 퍼져 있으므로 마침표와 구분된다.
     */
    private fun periodSide(pixels: PixelMap, area: IntRect): Side {
        val background = (area.left until area.right).map { pixels[it, area.top] }.groupingBy { it }.eachCount().maxBy { it.value }.key
        fun ink(x: Int, y: Int): Boolean {
            val c = pixels[x, y]
            return abs(c.red - background.red) + abs(c.green - background.green) + abs(c.blue - background.blue) > INK_THRESHOLD
        }
        val ys = area.top until area.bottom
        val columns = (area.left until area.right).filter { x -> ys.any { y -> ink(x, y) } }
        if (columns.isEmpty()) return Side.Unknown
        val rows = ys.filter { y -> columns.any { x -> ink(x, y) } }
        val middle = (rows.first() + rows.last()) / 2

        // 끝에서부터 잉크가 이어지는 열 묶음. 그 안의 잉크가 모두 가운데보다 아래면 마침표다.
        fun cluster(from: List<Int>) = listOf(from.first()) + from.zipWithNext().takeWhile { (a, b) -> abs(a - b) == 1 }.map { it.second }
        fun lowOnly(cluster: List<Int>) = cluster.all { x -> (area.top..middle).none { y -> ink(x, y) } }
        val right = lowOnly(cluster(columns.reversed()))
        val left = lowOnly(cluster(columns))
        return when {
            right && !left -> Side.Right
            left && !right -> Side.Left
            else -> Side.Unknown
        }
    }

    private companion object {
        const val SETTLE_MS = 1_000L
        const val INK_THRESHOLD = 0.3f
    }
}
