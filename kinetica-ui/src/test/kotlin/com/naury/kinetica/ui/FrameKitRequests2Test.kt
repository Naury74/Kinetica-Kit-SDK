package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 편집 SDK 요청: 블러 없는 가벼운 유리 표면, 공개 글자 단계, 세로 배치 동작 버튼. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FrameKitRequests2Test {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `가벼운 유리는 배경을 기록하지 않는 단색 표면이고 밝은 판과 어두운 판이 있다`() {
        val light = SurfaceStyles.LiteGlass as SurfaceStyle.Solid
        val dark = SurfaceStyles.LiteGlassDark as SurfaceStyle.Solid
        assertThat(light.isDark).isFalse()
        assertThat(dark.isDark).isTrue()
        assertThat(light.color.alpha).isLessThan(1f)
        assertThat(light.sheen.alpha).isGreaterThan(0f)
        assertThat(dark.sheen.alpha).isGreaterThan(0f)
        assertThat(light.elevation).isGreaterThan(0.dp)
        // 다른 단색 프리셋과 기본값은 광택이 없어 전과 같은 모양이다.
        assertThat((SurfaceStyles.CleanLight as SurfaceStyle.Solid).sheen).isEqualTo(Color.Transparent)
        assertThat(SurfaceStyle.Solid(Color.White, Color.Black, Color.White).sheen).isEqualTo(Color.Transparent)
        assertThat(light.copy(elevation = 0.dp).sheen).isEqualTo(light.sheen)
    }

    @Test
    fun `가벼운 유리는 위쪽이 광택으로 더 밝고 뒤 배경이 비친다`() {
        compose.setContent {
            Box(Modifier.background(Color(0xFF202020)).padding(16.dp)) {
                Box(Modifier.testTag("pill").size(120.dp, 60.dp).kSurface(SurfaceStyles.LiteGlass, RoundedCornerShape(8.dp)))
            }
        }
        val map = compose.onNodeWithTag("pill").captureToImage().toPixelMap()
        val x = map.width / 2
        val top = map[x, map.height / 8]
        val bottom = map[x, map.height * 7 / 8]
        assertThat(top.red).isGreaterThan(bottom.red)
        // 반투명 채움이라 어두운 바탕이 비쳐 흰색보다 어둡다.
        assertThat(bottom.red).isLessThan(0.95f)
        assertThat(bottom.red).isGreaterThan(0.5f)
    }

    @Test
    fun `글자 단계 기본값은 컴포넌트가 쓰는 크기와 굵기다`() {
        lateinit var type: KineticaTypography
        compose.setContent { KineticaTheme(surface = SurfaceStyles.CleanLight) { type = KineticaTheme.typography } }
        assertThat(type.largeTitle.fontSize).isEqualTo(34.sp)
        assertThat(type.largeTitle.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(type.title.fontSize).isEqualTo(20.sp)
        assertThat(type.title.fontWeight).isEqualTo(FontWeight.SemiBold)
        assertThat(type.body.fontSize).isEqualTo(16.sp)
        assertThat(type.bodySmall.lineHeight).isEqualTo(21.sp)
        assertThat(type.supporting.fontSize).isEqualTo(14.sp)
        assertThat(type.caption.fontSize).isEqualTo(13.sp)
        assertThat(type.micro.fontSize).isEqualTo(11.sp)
        // 버튼 글자 크기(KButtonSize)와 굵기(design.labelWeight)와 같다.
        assertThat(type.label.fontSize).isEqualTo(KButtonSize.Medium.font)
        assertThat(type.labelLarge.fontSize).isEqualTo(KButtonSize.Large.font)
        assertThat(type.labelSmall.fontSize).isEqualTo(KButtonSize.Small.font)
        assertThat(type.label.fontWeight).isEqualTo(KineticaDesigns.Cupertino.labelWeight)
        assertThat(type.body.color).isEqualTo(Color.Unspecified)
    }

    @Test
    fun `글자 단계는 호스트 글꼴과 디자인 언어의 굵기를 따른다`() {
        lateinit var type: KineticaTypography
        val design = KineticaDesigns.Cupertino.copy(titleWeight = FontWeight.Black, labelWeight = FontWeight.Medium)
        compose.setContent {
            KineticaTheme(textStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 40.sp), design = design) {
                type = KineticaTheme.typography
            }
        }
        assertThat(type.body.fontFamily).isEqualTo(FontFamily.Serif)
        // 호스트 글자 크기는 쓰지 않는다.
        assertThat(type.body.fontSize).isEqualTo(16.sp)
        assertThat(type.largeTitle.fontWeight).isEqualTo(FontWeight.Black)
        assertThat(type.label.fontWeight).isEqualTo(FontWeight.Medium)
    }

    @Test
    fun `세로 동작 버튼은 이름을 읽는 버튼이고 누르면 동작한다`() {
        var clicks = 0
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                KLabeledIconButton(KIcons.Crop, "자르기", onClick = { clicks++ }, modifier = Modifier.testTag("crop"))
            }
        }
        compose.onNodeWithTag("crop")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithText("자르기").performClick()
        assertThat(clicks).isEqualTo(1)
        val bounds = compose.onNodeWithTag("crop").fetchSemanticsNode().boundsInRoot
        assertThat(bounds.height).isAtLeast(with(compose.density) { KMetrics.Touch.toPx() } - 1f)
    }

    @Test
    fun `세로 동작 버튼은 Painter 아이콘과 비활성 상태를 받는다`() {
        var clicks = 0
        compose.setContent {
            KLabeledIconButton(ColorPainter(Color.Red), "삭제", { clicks++ }, Modifier.testTag("del"), tint = Color.Red, enabled = false)
        }
        compose.onNodeWithTag("del").assertIsNotEnabled()
        compose.onNodeWithText("삭제").performClick()
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `세로 동작 버튼은 큰 글자와 RTL에서도 이름이 아이콘 아래에 보인다`() {
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                KLabeledIconButton(KIcons.Undo, "되돌리기", {}, Modifier.testTag("undo"))
            }
        }
        val button = compose.onNodeWithTag("undo").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText("되돌리기", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertThat(label.width).isGreaterThan(0f)
        assertThat(button.contains(label.center)).isTrue()
        assertThat(label.bottom).isAtMost(button.bottom + 0.5f)
        // 아이콘(20dp)과 간격 아래에 놓인다.
        assertThat(label.top - button.top).isAtLeast(with(compose.density) { KMetrics.ButtonIcon.toPx() })
    }
}
