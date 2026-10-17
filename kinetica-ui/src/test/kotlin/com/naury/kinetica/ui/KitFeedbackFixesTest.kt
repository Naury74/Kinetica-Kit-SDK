package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 앱 화면 예를 만들며 나온 키트 불편 사항의 회귀 테스트 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class KitFeedbackFixesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `전화 키패드는 별표와 우물 정을 넘기고 0을 길게 누르면 더하기를 넘긴다`() {
        val typed = StringBuilder()
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                KKeypad({ typed.append(it) }, {}, mode = KKeypadMode.Phone, extraKey = { Box(Modifier.size(40.dp)) })
            }
        }
        compose.onNodeWithContentDescription("Star").performClick()
        compose.onNodeWithContentDescription("Pound").performClick()
        compose.onNodeWithContentDescription("0").performClick()
        compose.onNodeWithContentDescription("0").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Delete").assertExists()
        compose.runOnIdle { assertThat(typed.toString()).isEqualTo("*#0+") }
        val labels = compose.onNodeWithContentDescription("0").fetchSemanticsNode().config[SemanticsActions.OnLongClick].label
        assertThat(labels).isEqualTo("Enter plus")
    }

    @Test
    fun `글래스 위 선택 칩은 강조색으로 채우고 흰 글자로 쓴다`() {
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.Liquid) {
                KineticaScaffold(Modifier.width(320.dp), background = { MeshGradientBackground(Modifier.fillMaxSize()) }) { _ ->
                    KChipGroup(listOf("사진", "동영상"), setOf(0), {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        val chip = compose.onNodeWithText("사진", useUnmergedTree = true).captureToImage().asAndroidBitmap()
        var white = 0
        for (x in 0 until chip.width) {
            for (y in 0 until chip.height) if (Color(chip.getPixel(x, y)).luminance() > WHITE) white++
        }
        // 글자 획이 흰색이어야 한다. 옅은 강조색 바탕에 강조색 글자를 쓰던 때는 흰 픽셀이 거의 없었다.
        assertThat(white).isGreaterThan(chip.width * chip.height / 20)
    }

    @Test
    fun `큰 글자에서 토글 그룹 이름은 말줄임 대신 줄을 넘기고 칸 높이는 모두 같다`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                KineticaTheme(surface = SurfaceStyles.CleanLight) {
                    KToggleGroup(
                        listOf(KGroupItem("최신순"), KGroupItem("인기순"), KGroupItem("가격 낮은순")),
                        setOf(0),
                        {},
                        Modifier.width(320.dp),
                    )
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("가격 낮은순", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertThat(layouts.single().lineCount).isEqualTo(2)
        assertThat(layouts.single().hasVisualOverflow).isFalse()
        val short = compose.onNodeWithText("최신순").fetchSemanticsNode().size.height
        val long = compose.onNodeWithText("가격 낮은순").fetchSemanticsNode().size.height
        assertThat(long).isEqualTo(short)
    }

    @Test
    fun `목록 값은 지정한 색으로 쓴다`() {
        val green = Color(0xFF16A34A)
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                Column(Modifier.fillMaxWidth()) {
                    KListItem("급여", trailing = KListTrailing.Value("+3,200,000원", green))
                    KListItem("카페", trailing = KListTrailing.Value("−5,600원"))
                }
            }
        }
        fun colorOf(text: String): Color {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            return layouts.single().layoutInput.style.color
        }
        assertThat(colorOf("+3,200,000원")).isEqualTo(green)
        assertThat(colorOf("−5,600원").alpha).isLessThan(1f)
    }

    @Test
    fun `지도 핀 앵커는 꼬리 끝을 좌표에 맞춘다`() {
        assertPinAnchored(LayoutDirection.Ltr)
    }

    @Test
    fun `지도 핀 앵커는 RTL에서도 좌표를 뒤집지 않는다`() {
        assertPinAnchored(LayoutDirection.Rtl)
    }

    private fun assertPinAnchored(direction: LayoutDirection) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                KineticaTheme(surface = SurfaceStyles.CleanLight) {
                    Box(Modifier.size(300.dp, 200.dp)) {
                        KMapPin(Modifier.mapPinAnchor { Offset(120f, 150f) }, label = "가게", dropIn = false, contentDescription = "가게 핀")
                    }
                }
            }
        }
        val bounds = compose.onNodeWithContentDescription("가게 핀").fetchSemanticsNode().boundsInRoot
        assertThat(bounds.bottom).isWithin(1f).of(150f)
        assertThat(bounds.center.x).isWithin(1f).of(120f)
    }

    private companion object {
        const val WHITE = 0.85f
    }
}
