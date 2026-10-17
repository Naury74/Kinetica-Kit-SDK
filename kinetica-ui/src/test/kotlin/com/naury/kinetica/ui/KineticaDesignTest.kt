package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 디자인 언어(형태 토큰, 깊이, 밀도, 인디케이터, 컨트롤 모양)가 컴포넌트에 적용되고 프리셋마다 모양이 실제로 다른지 본다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
class KineticaDesignTest {
    @get:Rule
    val compose = createComposeRule()

    private val density = Density(1f)

    private fun close(a: Color, b: Color, tolerance: Float = 0.06f): Boolean =
        abs(a.red - b.red) < tolerance && abs(a.green - b.green) < tolerance && abs(a.blue - b.blue) < tolerance

    @Test
    fun `기본 토큰은 지금 모양과 같다`() {
        val shapes = KineticaDesigns.Cupertino.shapes
        assertThat(shapes.shape(shapes.button)).isEqualTo(CircleShape)
        assertThat(shapes.shape(shapes.card)).isEqualTo(RoundedCornerShape(24.dp))
        assertThat(shapes.shape(shapes.field)).isEqualTo(RoundedCornerShape(14.dp))
        assertThat(shapes.radius(shapes.button)).isEqualTo(androidx.compose.ui.unit.Dp.Infinity)
    }

    @Test
    fun `모서리 체계와 역할 토큰이 모양을 정한다`() {
        val brutal = KineticaDesigns.Brutal.shapes
        assertThat(brutal.shape(brutal.button)).isEqualTo(RectangleShape)
        assertThat(brutal.shape(brutal.card)).isEqualTo(RectangleShape)
        // 역할별 모서리 모양은 앱 전체 체계보다 우선한다.
        assertThat(brutal.shape(brutal.avatar)).isEqualTo(CircleShape)

        val cut = KineticaShapes(corner = KCornerStyle.Cut)
        assertThat(cut.shape(cut.card)).isEqualTo(CutCornerShape(24.dp))
        val soft = KineticaDesigns.Soft.shapes
        assertThat(soft.shape(soft.card)).isInstanceOf(KSquircleShape::class.java)
        assertThat(soft.shape(soft.card)).isEqualTo(soft.shape(soft.card))
    }

    @Test
    fun `안쪽 모양은 동심원 반지름을 쓰고 캡슐은 캡슐로 남는다`() {
        val shapes = KineticaShapes()
        assertThat(shapes.inner(shapes.card, 10.dp)).isEqualTo(RoundedCornerShape(14.dp))
        assertThat(shapes.inner(shapes.card, 30.dp)).isEqualTo(RoundedCornerShape(KMetrics.MinInnerRadius))
        assertThat(shapes.inner(shapes.button, 4.dp)).isEqualTo(CircleShape)
    }

    @Test
    fun `스쿼클은 같은 반지름의 원호보다 모서리를 더 채우고 크기 밖으로 나가지 않는다`() {
        val size = Size(100f, 100f)
        val squircle = KSquircleShape(30.dp).createOutline(size, LayoutDirection.Ltr, density)
        val path = (squircle as Outline.Generic).path
        val bounds = path.getBounds()
        assertThat(bounds.left).isAtLeast(-0.5f)
        assertThat(bounds.top).isAtLeast(-0.5f)
        assertThat(bounds.right).isAtMost(100.5f)
        assertThat(bounds.bottom).isAtMost(100.5f)
        // 모서리 대각선 위의 점: 원호(반지름 30)는 (8.8, 8.8)을 지나므로 그보다 바깥(모서리 쪽)은 비어 있어야 한다.
        val probe = androidx.compose.ui.graphics.Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(Offset(4f, 4f), Size(1f, 1f)))
        }
        val hit = androidx.compose.ui.graphics.Path().apply { op(path, probe, androidx.compose.ui.graphics.PathOperation.Intersect) }
        assertThat(hit.isEmpty).isTrue()
        // 가운데와 변 가운데는 채워져 있다.
        val center = androidx.compose.ui.graphics.Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(Offset(50f, 0.5f), Size(1f, 1f)))
        }
        val inside = androidx.compose.ui.graphics.Path().apply { op(path, center, androidx.compose.ui.graphics.PathOperation.Intersect) }
        assertThat(inside.isEmpty).isFalse()
        // 짧은 변의 절반보다 큰 반지름도 캡슐로 줄여 그린다.
        val pill = KSquircleShape(200.dp).createOutline(Size(120f, 40f), LayoutDirection.Ltr, density) as Outline.Generic
        assertThat(pill.path.getBounds().height).isWithin(0.6f).of(40f)
    }

    @Test
    fun `잘못된 디자인 값은 생성할 때 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) { KineticaShapes(small = (-1).dp) }
        assertThrows(IllegalArgumentException::class.java) { KineticaDesign(borderWidth = (-1).dp) }
        assertThrows(IllegalArgumentException::class.java) { KineticaMotion(0f, 300f, 0.05f) }
        assertThrows(IllegalArgumentException::class.java) { KineticaMotion(0.8f, 300f, 0.5f) }
    }

    @Test
    fun `밀도는 높이와 여백을 바꾸고 보통 밀도는 그대로 둔다`() {
        assertThat(KDensity.Regular.height(44.dp)).isEqualTo(44.dp)
        assertThat(KDensity.Compact.height(44.dp)).isLessThan(44.dp)
        assertThat(KDensity.Spacious.height(44.dp)).isGreaterThan(44.dp)
        assertThat(KDensity.Spacious.padding(16.dp)).isEqualTo(20.dp)
    }

    @Test
    fun `글자 굵기는 기본 디자인에서 그대로이고 다른 디자인에서 함께 옮겨 간다`() {
        assertThat(KineticaDesigns.Cupertino.title(FontWeight.SemiBold)).isEqualTo(FontWeight.SemiBold)
        assertThat(KineticaDesigns.Brutal.title(FontWeight.SemiBold).weight).isGreaterThan(FontWeight.SemiBold.weight)
        assertThat(KineticaDesigns.Fluent.label(FontWeight.SemiBold)).isEqualTo(FontWeight.Normal)
    }

    @Test
    fun `깊이 표현은 표면의 그림자와 테두리를 바꾸고 떠 있는 판은 그림자를 남긴다`() {
        val clean = SurfaceStyles.CleanLight as SurfaceStyle.Solid
        assertThat(clean.withDepth(KineticaDesigns.Cupertino)).isEqualTo(clean)

        val bordered = clean.withDepth(KineticaDesigns.Fluent) as SurfaceStyle.Solid
        assertThat(bordered.elevation).isEqualTo(0.dp)
        assertThat(bordered.borderWidth).isEqualTo(1.dp)
        assertThat((clean.forPopup().withDepth(KineticaDesigns.Fluent) as SurfaceStyle.Solid).elevation).isGreaterThan(0.dp)

        val flat = clean.withDepth(KineticaDesigns.Material) as SurfaceStyle.Solid
        assertThat(flat.elevation).isEqualTo(0.dp)
        assertThat(flat.border.alpha).isEqualTo(0f)

        val brutal = SurfaceStyles.Tonal.withDepth(KineticaDesigns.Brutal) as SurfaceStyle.Solid
        assertThat(brutal.border).isEqualTo(SurfaceStyles.Tonal.contentColor)
        assertThat(brutal.borderWidth).isEqualTo(2.dp)

        val liquid = (SurfaceStyles.Liquid as SurfaceStyle.Glass).style
        val glass = (SurfaceStyles.Liquid.withDepth(KineticaDesigns.Material) as SurfaceStyle.Glass).style
        assertThat(glass.shadow).isLessThan(liquid.shadow)
        assertThat((SurfaceStyles.Liquid.withDepth(KineticaDesigns.Brutal) as SurfaceStyle.Glass).style.shadow).isEqualTo(0.dp)
    }

    @Test
    fun `테마로 넘긴 디자인 언어를 안쪽에서 읽는다`() {
        var seen: KineticaDesign? = null
        compose.setContent { KineticaTheme(design = KineticaDesigns.Fluent) { seen = KineticaTheme.design } }
        compose.waitForIdle()
        assertThat(seen).isEqualTo(KineticaDesigns.Fluent)
    }

    private val current = mutableStateOf(KineticaDesigns.Cupertino)
    private var shown = false

    /** [design]으로 버튼 하나를 그려 버튼 왼쪽 위 모서리 바로 안쪽 픽셀 색을 돌려준다. */
    private fun buttonCorner(design: KineticaDesign): Color {
        current.value = design
        if (!shown) {
            shown = true
            compose.setContent {
                KineticaTheme(surface = SurfaceStyles.CleanLight, design = current.value) {
                    Box(Modifier.fillMaxSize().background(Color.White).padding(40.dp)) {
                        KButton("저장", {}, Modifier.testTag("button"))
                    }
                }
            }
        }
        compose.waitForIdle()
        val map = compose.onNodeWithTag("button").captureToImage().toPixelMap()
        return map[2, 2]
    }

    @Test
    fun `프리셋마다 버튼 모서리 모양이 실제로 다르게 그려진다`() {
        val accent = KineticaColors().accent
        // 캡슐 버튼은 모서리가 비어 있고, 각진 버튼은 모서리까지 채운다.
        assertThat(close(buttonCorner(KineticaDesigns.Cupertino), Color.White)).isTrue()
        val brutal = buttonCorner(KineticaDesigns.Brutal)
        // 브루탈은 모서리에 테두리(글자 색)가 있다.
        assertThat(close(brutal, Color.White)).isFalse()
        assertThat(close(buttonCorner(KineticaDesigns.Fluent), accent, 0.15f)).isTrue()
    }

    @Test
    fun `밀도에 따라 버튼 높이가 바뀐다`() {
        val design = mutableStateOf(KineticaDesigns.Cupertino)
        compose.setContent {
            KineticaTheme(design = design.value) { Box { KButton("저장", {}, Modifier.testTag("button")) } }
        }
        val heights = listOf(KineticaDesigns.Fluent, KineticaDesigns.Cupertino, KineticaDesigns.OneUi).map {
            design.value = it
            compose.waitForIdle()
            compose.onNodeWithTag("button").fetchSemanticsNode().size.height
        }
        assertThat(heights[0]).isLessThan(heights[1])
        assertThat(heights[1]).isLessThan(heights[2])
        assertThat(heights[1]).isEqualTo(44)
    }

    @Test
    fun `오프셋 깊이는 카드 오른쪽 아래에 단단한 그림자를 깐다`() {
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight, design = KineticaDesigns.Brutal) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(40.dp)) {
                    KCard(Modifier.size(100.dp).testTag("card")) {}
                }
            }
        }
        compose.waitForIdle()
        val map = compose.onRoot().captureToImage().toPixelMap()
        // 카드는 (40,40)~(140,140). 그림자는 오른쪽 아래로 4dp 비켜 있다.
        assertThat(close(map[142, 142], SurfaceStyles.CleanLight.contentColor, 0.1f)).isTrue()
        // 왼쪽 위 바깥은 비어 있다.
        assertThat(close(map[38, 38], Color.White)).isTrue()
    }

    @Test
    fun `채운 인디케이터 디자인이면 세그먼트의 고른 칸을 강조색으로 칠한다`() {
        val accent = KineticaColors().accent
        val design = mutableStateOf(KineticaDesigns.Material)
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight, design = design.value) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    KSegmentedControl(listOf("일", "주"), 0, {}, Modifier.fillMaxWidth().testTag("segment"))
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        fun probe(): Color {
            val map = compose.onNodeWithTag("segment").captureToImage().toPixelMap()
            return map[12, map.height / 2]
        }
        assertThat(close(probe(), accent, 0.12f)).isTrue()
        design.value = KineticaDesigns.Cupertino
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertThat(close(probe(), accent, 0.12f)).isFalse()
    }

    @Test
    fun `틈 구분 디자인이면 목록 행 사이에 바탕이 보인다`() {
        val design = mutableStateOf(KineticaDesigns.Material)
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanDark, design = design.value) {
                Box(Modifier.fillMaxSize().background(Color.Red).padding(20.dp)) {
                    KListSection(Modifier.testTag("section")) {
                        item { Box(Modifier.fillMaxWidth().height(40.dp)) }
                        item { Box(Modifier.fillMaxWidth().height(40.dp)) }
                    }
                }
            }
        }
        fun gapColor(next: KineticaDesign): Color {
            design.value = next
            compose.waitForIdle()
            val map = compose.onNodeWithTag("section").captureToImage().toPixelMap()
            return map[map.width / 2, 41]
        }
        assertThat(close(gapColor(KineticaDesigns.Material), Color.Red, 0.1f)).isTrue()
        assertThat(close(gapColor(KineticaDesigns.Cupertino), Color.Red, 0.1f)).isFalse()
    }

    @Test
    fun `스위치와 슬라이더 기본 모양은 디자인 언어를 따른다`() {
        var switch: KSwitchVariant? = null
        var thumb: KSliderThumb? = null
        compose.setContent {
            KineticaTheme(design = KineticaDesigns.Brutal) {
                switch = KineticaTheme.design.controls.switch
                thumb = KineticaTheme.design.controls.sliderThumb
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KSwitch(true, {}, Modifier.testTag("switch"))
                    KSlider(0.5f, {})
                }
            }
        }
        compose.waitForIdle()
        assertThat(switch).isEqualTo(KSwitchVariant.Block)
        assertThat(thumb).isEqualTo(KSliderThumb.Square)
        val node = compose.onNodeWithTag("switch").fetchSemanticsNode()
        assertThat(node.size.width).isEqualTo(56)
        assertThat(node.size.height).isEqualTo(30)
    }
}
