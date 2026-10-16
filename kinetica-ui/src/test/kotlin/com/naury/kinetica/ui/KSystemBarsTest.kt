package com.naury.kinetica.ui

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class KSystemBarsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun View.lightStatusBars(): Boolean {
        val window = (context as Activity).window
        return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
    }

    private fun View.setLightBars() {
        val window = (context as Activity).window
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    @Test
    fun `요청이 걸린 동안 호스트가 창 설정을 직접 바꾸면 요청이 빠져도 그 값을 지킨다`() {
        lateinit var view: View
        val open = mutableStateOf(false)
        compose.setContent {
            view = LocalView.current
            if (open.value) KSystemBarIcons(light = true)
        }
        // 어두운 화면(밝은 아이콘)에서 시작한다.
        compose.runOnIdle {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        }
        compose.runOnIdle { open.value = true }
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isFalse()

        // 앱 안에서 밝은 테마로 바꾸면서 호스트가 직접 어두운 아이콘(밝은 바탕용)으로 바꾼다.
        compose.runOnIdle { view.setLightBars() }
        compose.runOnIdle { open.value = false }
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isTrue()
    }

    @Test
    fun `단색 스캐폴드는 표면 밝기에 맞춰 아이콘을 고른다`() {
        lateinit var view: View
        val surface = mutableStateOf(SurfaceStyles.CleanDark)
        compose.setContent {
            view = LocalView.current
            KineticaScaffold(Modifier.fillMaxSize(), surface = surface.value) {}
        }
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isFalse()
        compose.runOnIdle { surface.value = SurfaceStyles.CleanLight }
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isTrue()
    }

    @Test
    fun `사진 뷰어가 열려 있는 동안 시스템 바 아이콘이 밝고 닫으면 되돌아온다`() {
        lateinit var view: View
        lateinit var state: KImageViewerState
        compose.setContent {
            view = LocalView.current
            state = rememberKImageViewerState()
            KineticaScaffold(Modifier.fillMaxSize()) {
                KImageViewer(state, listOf(ColorPainter(Color.Red), ColorPainter(Color.Blue)))
            }
        }
        compose.runOnIdle { view.setLightBars() }

        compose.runOnIdle { state.open(0) }
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isFalse()

        compose.runOnIdle { state.close() }
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isTrue()
    }

    @Test
    fun `겹친 요청은 나중 것이 이기고 모두 빠지면 처음 모양으로 돌아간다`() {
        lateinit var view: View
        val first = mutableStateOf(false)
        val second = mutableStateOf(false)
        compose.setContent {
            view = LocalView.current
            if (first.value) KSystemBarIcons(light = true)
            if (second.value) KSystemBarIcons(light = false)
        }
        compose.runOnIdle { view.setLightBars() }
        first.value = true
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isFalse()
        second.value = true
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isTrue()

        // 나중 요청이 빠지면 남은 요청의 모양으로, 모두 빠지면 처음 모양으로 돌아간다.
        second.value = false
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isFalse()
        first.value = false
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isTrue()
    }

    @Test
    fun `밝은 글자의 온보딩은 밝은 아이콘을 쓰고 빠지면 되돌린다`() {
        lateinit var view: View
        val shown = mutableStateOf(false)
        compose.setContent {
            view = LocalView.current
            if (shown.value) KOnboarding(listOf(OnboardingPage("하나", "설명")), onFinish = {})
        }
        compose.runOnIdle { view.setLightBars() }
        shown.value = true
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isFalse()

        shown.value = false
        compose.waitForIdle()
        assertThat(view.lightStatusBars()).isTrue()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w750dp-h600dp")
    fun `접힌 큰 제목 바는 아래로 옅어지고 탭 줄은 내용 폭에 맞는다`() {
        lateinit var state: KLargeTitleState
        compose.setContent {
            state = rememberKLargeTitleState()
            Box(Modifier.fillMaxSize()) {
                KineticaScaffold(
                    Modifier.fillMaxSize(),
                    background = { Box(Modifier.fillMaxSize().background(Color(0xFF6C8CFF))) },
                    topBar = {
                        KLargeTitleBar(state, "컴포넌트", contentMaxWidth = 500.dp, bottom = {
                            BasicText("탭", Modifier.fillMaxWidth().height(40.dp).testTag("tabs"))
                        })
                    },
                ) { padding ->
                    LazyColumn(Modifier.fillMaxSize().nestedScroll(state.connection).testTag("list"), contentPadding = padding) {
                        items(80) { BasicText("항목 $it", Modifier.fillMaxWidth().height(48.dp).background(Navy).padding(12.dp)) }
                    }
                }
            }
        }
        compose.onNodeWithTag("list").performTouchInput { swipeUp() }
        compose.onNodeWithTag("list").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val shot = capture()
        val tabs = compose.onNodeWithTag("tabs").getBoundsInRoot()
        assertThat(tabs.left.value).isWithin(0.5f).of(125f)
        assertThat((tabs.right - tabs.left).value).isWithin(0.5f).of(500f)
        // 바 바로 아래는 글래스가 옅게 남고, 페이드 길이를 지나면 콘텐츠 색 그대로다.
        val barBottom = with(compose.density) { tabs.bottom.toPx() }.toInt()
        val x = shot.width - 4
        assertThat(shot.getPixel(x, barBottom + 2)).isNotEqualTo(Navy.toArgb())
        assertThat(shot.getPixel(x, barBottom + with(compose.density) { 24.dp.toPx() }.toInt())).isEqualTo(Navy.toArgb())
    }

    private fun capture(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

    private companion object {
        val Navy = Color(0xFF2D3A8C)
    }
}
