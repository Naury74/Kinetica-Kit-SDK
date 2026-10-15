package com.naury.kinetica.ui

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 시스템 바, 화면 컷아웃, 키보드 인셋을 Compose 화면에 직접 보내 여백이 한 번씩만 들어가는지 확인한다.
 * Robolectric은 기본 인셋이 0이므로 인셋을 보내지 않은 경우가 edge-to-edge를 쓰지 않는 호스트(인셋이 Compose까지 오지 않음)와 같다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-mdpi")
class HostInsetsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var activityView: View? = null
    private var overlayView: View? = null

    /** mdpi라 px과 dp가 같다. */
    private fun send(view: View?, nav: Int = 0, ime: Int = 0, status: Int = 0, cutoutLeft: Int = 0) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, status, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, nav))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(cutoutLeft, 0, 0, 0))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, ime))
            .setVisible(WindowInsetsCompat.Type.ime(), ime > 0)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(requireNotNull(view), insets) }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    private fun gapBelow(tag: String): Dp = SCREEN - bounds(tag).bottom

    @Composable
    private fun Screen(hosted: Boolean, content: @Composable () -> Unit) {
        activityView = LocalView.current
        if (hosted) GlassScaffold(Modifier.fillMaxSize()) { content() } else Box(Modifier.fillMaxSize()) { content() }
    }

    @Composable
    private fun SheetWithField() {
        GlassSheet(true, {}) {
            overlayView = LocalView.current
            GlassTextField("", {}, "이름", Modifier.testTag("field"))
        }
    }

    private fun checkSheet(hosted: Boolean) {
        compose.setContent { Screen(hosted) { SheetWithField() } }
        compose.waitForIdle()
        // edge-to-edge를 쓰지 않는 호스트: 인셋이 오지 않으므로 시트 아래 여백(16dp)만 남는다.
        assertThat(gapBelow("field").value).isWithin(1f).of(SHEET_BOTTOM)

        val target = if (hosted) activityView else overlayView
        send(target, nav = NAV)
        assertThat(gapBelow("field").value).isWithin(1f).of(NAV + SHEET_BOTTOM)

        // 키보드가 올라오면 내비게이션 바와 겹치는 부분은 한 번만 더한다.
        send(target, nav = NAV, ime = IME)
        assertThat(gapBelow("field").value).isWithin(1f).of(IME + SHEET_BOTTOM)

        send(target, nav = NAV, cutoutLeft = CUTOUT)
        assertThat(bounds("field").left.value).isWithin(1f).of(CUTOUT + SHEET_SIDE)
    }

    @Test
    fun `GlassScaffold 안 시트는 내비게이션 바와 키보드 위로 올라오고 여백이 겹치지 않는다`() = checkSheet(hosted = true)

    @Test
    fun `밖에서 연 시트는 대화 상자 창의 인셋으로 같은 여백을 낸다`() = checkSheet(hosted = false)

    @Test
    fun `다이얼로그는 키보드와 상태 표시줄을 피한다`() {
        compose.setContent {
            Screen(hosted = true) {
                GlassDialog(true, {}, Modifier.testTag("panel")) { GlassTextField("", {}, "메모") }
            }
        }
        send(activityView, status = STATUS, nav = NAV, ime = IME)
        val panel = bounds("panel")
        assertThat(panel.bottom.value).isAtMost(SCREEN.value - IME - DIALOG_MARGIN + 1f)
        assertThat(panel.top.value).isAtLeast(STATUS + DIALOG_MARGIN - 1f)
    }

    @Test
    fun `스낵바는 내비게이션 바와 키보드 위에 뜬다`() {
        lateinit var scope: CoroutineScope
        val snackbar = GlassSnackbarState()
        compose.setContent {
            scope = rememberCoroutineScope()
            Screen(hosted = true) { GlassSnackbarHost(snackbar, Modifier.testTag("pill")) }
        }
        compose.mainClock.autoAdvance = false
        scope.launch { snackbar.show("저장했어요", durationMs = 10_000) }
        compose.mainClock.advanceTimeBy(1_000)
        send(activityView, nav = NAV)
        compose.mainClock.advanceTimeBy(100)
        val withNav = gapBelow("pill").value
        send(activityView, nav = NAV, ime = IME)
        compose.mainClock.advanceTimeBy(100)
        assertThat(gapBelow("pill").value).isWithin(1f).of(withNav - NAV + IME)
        assertThat(withNav).isAtLeast(NAV.toFloat())
    }

    @Test
    fun `여러 높이 시트는 키보드가 올라오면 그만큼 올라가고 내비게이션 바만큼 낮은 높이를 띄운다`() {
        var peekTop by mutableStateOf(0f)
        compose.setContent {
            Screen(hosted = false) {
                GlassDetentSheet(rememberGlassDetentSheetState(listOf(Detent.Peek(PEEK.dp), Detent.Full)), Modifier.testTag("container")) {
                    BasicText("내용", Modifier.testTag("body"))
                }
            }
        }
        compose.waitForIdle()
        peekTop = handleTop()
        assertThat(peekTop).isWithin(1f).of(SCREEN.value - PEEK)
        send(activityView, nav = NAV)
        assertThat(handleTop()).isWithin(1f).of(SCREEN.value - PEEK - NAV)
        send(activityView, nav = NAV, ime = IME)
        assertThat(handleTop()).isWithin(1f).of(SCREEN.value - PEEK - IME)
    }

    /** 시트 윗변. 머리 영역이 비어 있으므로 본문의 윗변에서 핸들 높이를 뺀다. */
    private fun handleTop(): Float = compose.onNodeWithText("내용").getBoundsInRoot().top.value - HANDLE_AREA

    private companion object {
        val SCREEN = 640.dp
        const val NAV = 48
        const val IME = 300
        const val STATUS = 24
        const val CUTOUT = 40
        const val PEEK = 120
        const val SHEET_BOTTOM = 16f
        const val SHEET_SIDE = 24f
        const val DIALOG_MARGIN = 28f
        const val HANDLE_AREA = 25f
    }
}
