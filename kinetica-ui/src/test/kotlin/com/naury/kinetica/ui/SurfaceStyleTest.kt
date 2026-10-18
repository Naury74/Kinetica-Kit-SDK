package com.naury.kinetica.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 표면 스타일(글래스, 단색, 톤)마다 컴포넌트가 같은 API로 그려지고, 스타일을 바꿔도 상태가 남는지 본다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class SurfaceStyleTest {
    @get:Rule
    val compose = createComposeRule()

    private fun close(a: Color, b: Color, tolerance: Float = 0.03f): Boolean =
        abs(a.red - b.red) < tolerance && abs(a.green - b.green) < tolerance && abs(a.blue - b.blue) < tolerance

    private fun centerOf(tag: String): Color {
        val map = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return map[map.width / 2, map.height / 2]
    }

    @Test
    fun `단색과 톤 스캐폴드는 배경을 기록하지 않고 글래스만 기록한다`() {
        var surface by mutableStateOf<SurfaceStyle>(SurfaceStyles.Liquid)
        var content: GlassBackdrop? = null
        var bars: GlassBackdrop? = null
        compose.setContent {
            KineticaScaffold(surface = surface, topBar = { bars = LocalGlassBackdrop.current }) { content = LocalGlassBackdrop.current }
        }
        compose.waitForIdle()
        assertThat(content).isNotNull()
        assertThat(bars).isNotNull()

        surface = SurfaceStyles.CleanLight
        compose.waitForIdle()
        assertThat(content).isNull()
        assertThat(bars).isNull()

        surface = SurfaceStyles.Tonal
        compose.waitForIdle()
        assertThat(content).isNull()
    }

    @Test
    fun `테마는 표면과 글자 색과 블러 기본 스타일을 함께 정한다`() {
        var surface: SurfaceStyle? = null
        var ink = Color.Unspecified
        var glass: GlassStyle? = null
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanDark) {
                surface = KineticaTheme.surface
                ink = LocalKineticaContentColor.current
                glass = KineticaTheme.glassStyle
            }
        }
        compose.waitForIdle()
        assertThat(surface).isEqualTo(SurfaceStyles.CleanDark)
        assertThat(ink).isEqualTo(SurfaceStyles.CleanDark.contentColor)
        assertThat(SurfaceStyles.CleanDark.isDark).isTrue()
        // 글래스가 아닌 어두운 표면에서 Modifier.glass를 따로 쓰면 어두운 Liquid가 기본이다.
        assertThat(glass).isEqualTo(GlassStyles.LiquidDark)
    }

    @Test
    fun `단색 스캐폴드는 바탕색을 칠하고 카드는 표면 색으로 그린다`() {
        val solid = SurfaceStyles.CleanLight as SurfaceStyle.Solid
        compose.setContent {
            KineticaScaffold(Modifier.testTag("screen"), surface = solid) {
                Box(Modifier.padding(40.dp).size(120.dp).testTag("card").kSurface(shape = RoundedCornerShape(16.dp)))
            }
        }
        compose.waitForIdle()
        val screen = compose.onNodeWithTag("screen").captureToImage().toPixelMap()
        assertThat(close(screen[screen.width - 4, screen.height - 4], solid.background)).isTrue()
        assertThat(close(centerOf("card"), solid.color)).isTrue()
    }

    @Test
    fun `톤 표면은 강조색을 섞은 색으로 칠하고 같은 표면끼리 겹쳐도 테두리가 보인다`() {
        val tonal = SurfaceStyle.Tonal(Color(0xFF16A34A))
        assertThat(tonal.color).isNotEqualTo(Color.White)
        assertThat(tonal.contentColor.red).isLessThan(0.3f)
        compose.setContent {
            KineticaTheme(surface = tonal) {
                Box(Modifier.size(100.dp).background(tonal.color)) {
                    Box(Modifier.padding(20.dp).size(60.dp).testTag("inner").kSurface(shape = RoundedCornerShape(12.dp)))
                }
            }
        }
        compose.waitForIdle()
        val map = compose.onNodeWithTag("inner").captureToImage().toPixelMap()
        assertThat(close(map[map.width / 2, map.height / 2], tonal.color)).isTrue()
        // 가장자리 한 줄은 강조색 테두리라 표면 색과 다르다.
        assertThat(close(map[map.width / 2, 0], tonal.color, tolerance = 0.01f)).isFalse()
    }

    @Test
    fun `표면을 바꿔도 열린 시트와 다이얼로그가 그대로 남고 글자 색이 따라 바뀐다`() {
        var surface by mutableStateOf<SurfaceStyle>(SurfaceStyles.Liquid)
        var sheetInk = Color.Unspecified
        compose.setContent {
            KineticaScaffold(surface = surface) {
                KSheet(true, {}, title = "시트") {
                    sheetInk = LocalKineticaContentColor.current
                    BasicText("시트 내용")
                }
                KDialog(true, {}, title = "다이얼로그") { BasicText("다이얼로그 내용") }
            }
        }
        compose.waitForIdle()
        for (next in listOf(SurfaceStyles.CleanDark, SurfaceStyles.Tonal, SurfaceStyles.LiquidDark, SurfaceStyles.CleanLight)) {
            surface = next
            compose.waitForIdle()
            // 위에 다이얼로그가 열려 있으면 시트는 TalkBack에서 숨으므로 시트는 그려진 글자 색으로 확인한다.
            assertThat(compose.onAllNodesWithText("다이얼로그 내용").fetchSemanticsNodes()).hasSize(1)
            assertThat(sheetInk).isEqualTo(next.contentColor)
        }
    }

    @Test
    fun `테마에서 눌림 표시를 끄면 스캐폴드도 호스트 값을 그대로 둔다`() {
        val host = KIndication(color = Color.Red)
        var seen: Indication? = null
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalIndication provides host) {
                KineticaTheme(indication = null) {
                    KineticaScaffold { seen = LocalIndication.current }
                }
            }
        }
        compose.waitForIdle()
        assertThat(seen).isSameInstanceAs(host)
    }

    @Test
    fun `버튼의 모양 변형과 색 역할은 표면 스타일과 따로 조합된다`() {
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                Column {
                    KButton("삭제", {}, Modifier.testTag("destructive"), variant = KVariant.Filled, tone = KTone.Destructive)
                    KButton("저장", {}, Modifier.testTag("positive"), variant = KVariant.Filled, tone = KTone.Positive)
                    KButton("취소", {}, Modifier.testTag("outlined"), variant = KVariant.Outlined, tone = KTone.Neutral)
                }
            }
        }
        compose.waitForIdle()
        val colors = KineticaColors()
        val destructive = compose.onNodeWithTag("destructive").captureToImage().toPixelMap()
        // 가운데는 글자가 있을 수 있어 왼쪽 가장자리 쪽을 본다.
        // 채운 면은 흰 글자와 4.5:1이 되도록 조금 진해진 색이다.
        val red = ToneColors(colors.destructive, colors.onDestructive).fill
        val green = ToneColors(colors.positive, colors.onPositive).fill
        assertThat(close(destructive[destructive.width / 6, destructive.height / 2], red)).isTrue()
        val positive = compose.onNodeWithTag("positive").captureToImage().toPixelMap()
        assertThat(close(positive[positive.width / 6, positive.height / 2], green)).isTrue()
        // 테두리 모양은 안을 채우지 않는다.
        val outlined = compose.onNodeWithTag("outlined").captureToImage().toPixelMap()
        assertThat(close(outlined[outlined.width / 6, outlined.height / 2], colors.destructive)).isFalse()
        assertThat(close(outlined[outlined.width / 6, outlined.height / 2], colors.accent)).isFalse()
    }

    @Test
    fun `토스트는 색 역할이 있으면 글자 앞에 점을 찍는다`() {
        val state = KToastState()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) { KToastHost(state) }
            LaunchedEffect(Unit) { state.show("저장했어요", tone = KTone.Positive) }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        assertThat(compose.onAllNodesWithText("저장했어요").fetchSemanticsNodes()).hasSize(1)
        assertThat(state.tone).isEqualTo(KTone.Positive)
    }

    @Test
    fun `잘못된 단색과 톤 값은 만들 때 거부한다`() {
        assertThat(runCatching { SurfaceStyle.Solid(Color.White, Color.Black, Color.White, elevation = (-1).dp) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { SurfaceStyle.Tonal(Color.Blue, elevation = (-1).dp) }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(SurfaceStyles.CleanLight.forPopup()).isInstanceOf(SurfaceStyle.Solid::class.java)
        assertThat((SurfaceStyles.CleanLight.forPopup() as SurfaceStyle.Solid).elevation).isGreaterThan(6.dp)
    }

    @Test
    fun `색 바퀴는 넓은 화면에서도 320dp를 넘지 않는다`() {
        compose.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanLight) {
                Box(Modifier.requiredWidth(1248.dp)) { KColorPicker(Color.Red, {}, Modifier.fillMaxWidth()) }
            }
        }
        compose.waitForIdle()
        val title = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.kinetica_ui_color_picker)
        val wheel = compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(title), useUnmergedTree = true)[0]
        val bounds = wheel.getUnclippedBoundsInRoot()
        assertThat((bounds.right - bounds.left).value).isAtMost(320.5f)
    }

    /** `KINETICA_SHOTS` 환경 변수에 폴더를 주면 표면 스타일마다 주요 컴포넌트를 그린 화면을 PNG로 남긴다. 눈으로 확인할 때만 쓴다. */
    @Test
    fun `표면 스타일마다 주요 컴포넌트를 그린다`() {
        var surface by mutableStateOf<SurfaceStyle>(SurfaceStyles.Liquid)
        var popup by mutableStateOf(0)
        compose.setContent { Gallery(surface, popup) }
        val out = System.getenv("KINETICA_SHOTS")?.let { File(it).apply { mkdirs() } }
        val styles = listOf(
            "liquid" to SurfaceStyles.Liquid,
            "liquidDark" to SurfaceStyles.LiquidDark,
            "cleanLight" to SurfaceStyles.CleanLight,
            "cleanDark" to SurfaceStyles.CleanDark,
            "tonal" to SurfaceStyles.Tonal,
            "tonalDark" to SurfaceStyles.TonalDark,
        )
        for ((name, style) in styles) {
            for (mode in 0..2) {
                surface = style
                popup = mode
                compose.mainClock.advanceTimeBy(1_000)
                compose.waitForIdle()
                compose.onNodeWithTag("gallery-button", useUnmergedTree = true).assertExistsSafely()
                out?.let { save(File(it, "$name-$mode.png")) }
            }
        }
    }

    private fun save(file: File) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsSafely() {
        assertThat(fetchSemanticsNode()).isNotNull()
    }

    @Composable
    private fun Gallery(surface: SurfaceStyle, popup: Int) {
        var text by remember { mutableStateOf("hello@kinetica.dev") }
        KineticaScaffold(
            surface = surface,
            background = {
                if (surface is SurfaceStyle.Glass) {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(listOf(Color(0xFFFF8A65), Color(0xFF7E57C2), Color(0xFF26C6DA))),
                        ),
                    )
                }
            },
            bottomBar = { KTabBar(listOf(KTab("홈"), KTab("검색"), KTab("설정")), 0, {}) },
        ) { padding ->
            Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KButton("결제", {}, Modifier.testTag("gallery-button"))
                    KButton("담기", {}, variant = KVariant.Tonal)
                    KButton("취소", {}, variant = KVariant.Outlined, tone = KTone.Neutral)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KButton("더 보기", {}, variant = KVariant.Glass)
                    KButton("삭제", {}, variant = KVariant.Text, tone = KTone.Destructive)
                }
                KSurface({}, Modifier.fillMaxWidth(), variant = KVariant.Glass) {
                    Column(Modifier.padding(18.dp)) {
                        BasicText("카드 제목", color = { surface.contentColor })
                        BasicText("표면 스타일을 따르는 카드", color = { surface.contentColor.copy(alpha = 0.7f) })
                    }
                }
                KTextField(text, { text = it }, "이메일", Modifier.fillMaxWidth())
                KChipGroup(listOf("전체", "사진", "동영상"), setOf(0), {})
                repeat(2) { index ->
                    KSurface({}, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        BasicText("목록 행 ${index + 1}", Modifier.padding(16.dp), color = { surface.contentColor })
                    }
                }
            }
            KSheet(popup == 1, {}, title = "시트") {
                BasicText("바텀 시트", color = { surface.contentColor })
                KTextField("", {}, "메모", Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            KDialog(popup == 2, {}, title = "다이얼로그") {
                BasicText("삭제할까요?", color = { surface.contentColor })
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KButton("취소", {}, variant = KVariant.Tonal, tone = KTone.Neutral)
                    KButton("삭제", {}, tone = KTone.Destructive)
                }
            }
        }
    }
}
