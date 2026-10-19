package com.naury.kinetica.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 편집 SDK 3차 요청: 끌어 닫기 끄기, 다이얼로그 등장 배율, 가벼운 유리의 그라디언트 테두리와 그림자 진하기. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
class FrameKitRequests3Test {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(true)

    @Test
    fun `dragToDismiss가 false인 시트 알림은 끌어도 닫히지 않고 뒤로 가기로는 닫힌다`() {
        showAlertSheet(dragToDismiss = false)
        val rest = top("제목")
        compose.onNodeWithText("제목").performTouchInput {
            down(center)
            moveBy(Offset(0f, 150f))
            moveBy(Offset(0f, 150f))
            up()
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(open).isTrue()
        compose.onNodeWithText("제목").assertIsDisplayed()
        assertThat(top("제목")).isWithin(1f).of(rest)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertThat(open).isFalse()
    }

    @Test
    fun `dragToDismiss 기본값인 시트 알림은 끌어 닫힌다`() {
        showAlertSheet(dragToDismiss = true)
        compose.onNodeWithText("제목").performTouchInput {
            down(center)
            moveBy(Offset(0f, 150f))
            moveBy(Offset(0f, 150f))
            up()
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(open).isFalse()
    }

    @Test
    fun `dragToDismiss가 false인 시트는 안쪽 목록을 끝까지 끌어도 내려가지 않는다`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KSheet(open, { open = false }, dragToDismiss = false) {
                    Column(Modifier.fillMaxWidth().height(300.dp).testTag("list").verticalScroll(rememberScrollState())) {
                        repeat(30) { BasicText("줄 $it", Modifier.height(40.dp)) }
                    }
                }
            }
        }
        settle()
        val rest = compose.onNodeWithTag("list").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("list").performTouchInput {
            down(center)
            repeat(6) { moveBy(Offset(0f, 50f)) }
            up()
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(open).isTrue()
        assertThat(compose.onNodeWithTag("list").fetchSemanticsNode().boundsInRoot.top).isWithin(1f).of(rest)
    }

    @Test
    fun `끌어 닫기를 끄면 손잡이를 그리지 않고 내용 자리는 그대로다`() {
        val withHandle = handleProbe(dragToDismiss = true)
        val without = handleProbe(dragToDismiss = false)
        // 내용은 같은 자리에 놓인다.
        assertThat(without.first).isWithin(0.5f).of(withHandle.first)
        // 손잡이 자리는 흰 시트보다 어둡고, 끄면 시트 색 그대로다.
        assertThat(withHandle.second).isLessThan(0.95f)
        assertThat(without.second).isGreaterThan(0.98f)
    }

    @Test
    fun `appearScale을 1에 가깝게 주면 처음부터 거의 제 크기로 나타난다`() {
        val default = firstFrameRatio(KSheetDefaults.DialogAppearScale)
        val near = firstFrameRatio(0.94f)
        val clamped = firstFrameRatio(5f)
        assertThat(KSheetDefaults.DialogAppearScale).isEqualTo(0.6f)
        assertThat(default).isLessThan(0.8f)
        assertThat(near).isAtLeast(0.94f - 0.005f)
        assertThat(near).isLessThan(1f)
        // 0..1 밖은 맞춘다. 1이면 커지는 움직임이 없다.
        assertThat(clamped).isWithin(0.005f).of(1f)
    }

    @Test
    fun `KAlertDialog 카드는 appearScale을 KDialog에 넘긴다`() {
        compose.mainClock.autoAdvance = false
        open = false
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KAlertDialog(open, { open = false }, "제목", emptyList(), appearScale = 0.94f) {
                    Box(Modifier.fillMaxWidth().height(10.dp).testTag("card"))
                }
            }
        }
        settle()
        open = true
        compose.waitForIdle()
        val first = firstWidth("card")
        settle()
        val last = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.width
        assertThat(first / last).isAtLeast(0.94f - 0.005f)
    }

    @Test
    fun `borderEnd를 주면 위는 border 색이고 아래는 borderEnd 색인 테두리다`() {
        val surface = SurfaceStyle.Solid(
            color = Color(0xFF808080),
            contentColor = Color.Black,
            background = Color.White,
            border = Color.White,
            borderWidth = 4.dp,
            borderEnd = Color.Black,
        )
        val map = capture(surface)
        val x = map.width / 2
        val top = map[x, 1]
        val bottom = map[x, map.height - 2]
        val middle = map[x, map.height / 2]
        assertThat(top.red).isGreaterThan(0.85f)
        assertThat(bottom.red).isLessThan(0.15f)
        assertThat(middle.red).isWithin(0.03f).of(0.5f)
        // 지정하지 않으면 전처럼 위아래가 같은 한 색이다.
        val plain = capture(surface.copy(borderEnd = Color.Unspecified))
        assertThat(plain[x, plain.height - 2].red).isGreaterThan(0.85f)
    }

    @Test
    fun `밝은 가벼운 유리는 흰 바탕 위에서 아래 경계가 보인다`() {
        val light = SurfaceStyles.LiteGlass as SurfaceStyle.Solid
        assertThat(light.border).isEqualTo(Color.White.copy(alpha = 0.95f))
        assertThat(light.borderEnd).isEqualTo(Color.Black.copy(alpha = 0.08f))
        val dark = SurfaceStyles.LiteGlassDark as SurfaceStyle.Solid
        assertThat(dark.border).isEqualTo(Color.White.copy(alpha = 0.32f))
        assertThat(dark.borderEnd).isEqualTo(Color.White.copy(alpha = 0.06f))
        val map = capture(light.copy(borderWidth = 3.dp))
        val x = map.width / 2
        val edge = map[x, map.height - 1]
        val inside = map[x, map.height - 8]
        assertThat(edge.red).isLessThan(inside.red - 0.02f)
    }

    @Test
    fun `shadowColor 알파로 그림자 진하기를 바꾼다`() {
        val base = SurfaceStyle.Solid(Color.White, Color.Black, Color.White, elevation = 8.dp)
        val plain = shadowBelow(base)
        val strong = shadowBelow(base.copy(shadowColor = Color.Black.copy(alpha = 0.6f)))
        val none = shadowBelow(base.copy(shadowColor = Color.Transparent))
        assertThat(plain).isLessThan(0.99f)
        assertThat(strong).isLessThan(plain - 0.05f)
        assertThat(none).isGreaterThan(0.995f)
    }

    @Test
    fun `새 값은 팝업과 다른 깊이 디자인에서도 일관되게 쓰인다`() {
        val shadow = Color.Black.copy(alpha = 0.3f)
        val glass = (SurfaceStyles.LiteGlass as SurfaceStyle.Solid).copy(shadowColor = shadow)
        val popup = glass.forPopup() as SurfaceStyle.Solid
        assertThat(popup.borderEnd).isEqualTo(glass.borderEnd)
        assertThat(popup.shadowColor).isEqualTo(shadow)
        assertThat((glass.withoutShadow() as SurfaceStyle.Solid).borderEnd).isEqualTo(glass.borderEnd)
        // 깊이를 그림자로 그리는 디자인은 그대로, 테두리나 비낀 그림자로 그리는 디자인은 한 색 테두리로 바꾼다.
        assertThat(glass.withDepth(KineticaDesigns.Cupertino)).isEqualTo(glass)
        for (depth in listOf(KDepth.Border, KDepth.Offset)) {
            val styled = glass.withDepth(KineticaDesigns.Cupertino.copy(depth = depth)) as SurfaceStyle.Solid
            assertThat(styled.borderEnd).isEqualTo(Color.Unspecified)
        }
    }

    @Test
    fun `알파04 Solid 생성자와 copy는 숨긴 채 남아 같은 값을 만든다`() {
        val type = SurfaceStyle.Solid::class.java
        val long = Long::class.javaPrimitiveType
        val float = Float::class.javaPrimitiveType
        // Color는 long, Dp는 float로 내려간다. 값 클래스 파라미터가 있는 생성자는 끝에 표식 인자가 붙는다.
        val marker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        val old = type.getConstructor(long, long, long, long, float, float, long, long, long, marker)
        val made = old.newInstance(
            Color.White.value.toLong(),
            Color.Black.value.toLong(),
            Color.Gray.value.toLong(),
            Color.Red.value.toLong(),
            1f,
            2f,
            Color.Unspecified.value.toLong(),
            Color.Unspecified.value.toLong(),
            Color.Blue.value.toLong(),
            null,
        ) as SurfaceStyle.Solid
        assertThat(made).isEqualTo(
            SurfaceStyle.Solid(Color.White, Color.Black, Color.Gray, Color.Red, 1.dp, 2.dp, sheen = Color.Blue),
        )
        assertThat(made.borderEnd).isEqualTo(Color.Unspecified)
        assertThat(made.shadowColor).isEqualTo(Color.Unspecified)

        // 옛 copy는 새로 더한 값을 그대로 둔다.
        val copies = type.methods.filter { it.name.startsWith("copy") && !it.name.contains("default") }
        val oldCopy = copies.single { it.parameterCount == 9 }
        val source = SurfaceStyles.LiteGlass as SurfaceStyle.Solid
        val copied = oldCopy.invoke(
            source,
            source.color.value.toLong(),
            source.contentColor.value.toLong(),
            source.background.value.toLong(),
            source.border.value.toLong(),
            source.borderWidth.value,
            0f,
            source.contentMuted.value.toLong(),
            source.raised.value.toLong(),
            source.sheen.value.toLong(),
        ) as SurfaceStyle.Solid
        assertThat(copied).isEqualTo(source.copy(elevation = 0.dp))
        assertThat(copied.borderEnd).isEqualTo(source.borderEnd)
        // 알파03, 알파04, 지금의 copy가 모두 있다.
        assertThat(copies.map { it.parameterCount }).containsExactly(8, 9, 11)
    }

    @Test
    fun `알파04 다이얼로그와 시트 시그니처는 숨긴 오버로드로 남아 있다`() {
        val alert = Class.forName("com.naury.kinetica.ui.KAlertDialogKt").methods.filter { it.name.startsWith("KAlertDialog") }
        // 알파02, 알파03, 알파04, 지금 판
        assertThat(alert.map { it.name }.distinct()).hasSize(4)
        val components = Class.forName("com.naury.kinetica.ui.KComponentsKt").methods
        val sheets = components.filter { it.name == "KSheet" }.map { it.parameterTypes.toList() }
        assertThat(sheets).hasSize(3)
        // 알파04 판은 dragToDismiss 없이 onDismissed 뒤에 content가 온다.
        assertThat(sheets.any { it.contains(Boolean::class.javaPrimitiveType) }).isTrue()
        assertThat(components.count { it.name.startsWith("KDialog") }).isEqualTo(3)
    }

    private fun showAlertSheet(dragToDismiss: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KAlertDialog(
                    open,
                    { open = false },
                    "제목",
                    listOf(KDialogAction("확인", KDialogActionStyle.Preferred)),
                    presentation = KDialogPresentation.Sheet,
                    dragToDismiss = dragToDismiss,
                )
            }
        }
        settle()
    }

    private var handleOn by mutableStateOf(true)
    private var handleSet = false

    /** 시트 내용의 위 끝(px)과 손잡이 가운데 픽셀의 밝기. */
    private fun handleProbe(dragToDismiss: Boolean): Pair<Float, Float> {
        compose.mainClock.autoAdvance = false
        handleOn = dragToDismiss
        if (!handleSet) {
            handleSet = true
            compose.setContent {
                KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                    KSheet(true, {}, dragToDismiss = handleOn) { Box(Modifier.fillMaxWidth().height(120.dp).testTag("body")) }
                }
            }
        }
        settle()
        val body = compose.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot
        val map = compose.onRoot().captureToImage().toPixelMap()
        // 손잡이는 내용 위 12dp 여백 위에 놓인 5dp 높이 막대다(mdpi라 1dp = 1px).
        val y = (body.top - 12f - 2.5f).toInt()
        return body.top to map[body.center.x.toInt(), y].red
    }

    private var dialogShown by mutableStateOf(false)
    private var dialogScale by mutableStateOf(KSheetDefaults.DialogAppearScale)
    private var dialogSet = false

    /** 열기 시작한 첫 프레임의 패널 폭을 다 열린 폭으로 나눈 값 */
    private fun firstFrameRatio(scale: Float): Float {
        compose.mainClock.autoAdvance = false
        if (!dialogSet) {
            dialogSet = true
            compose.setContent {
                KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                    KDialog(dialogShown, { dialogShown = false }, appearScale = dialogScale) {
                        Box(Modifier.size(200.dp, 100.dp).testTag("panel"))
                    }
                }
            }
        }
        dialogShown = false
        settle()
        dialogScale = scale
        dialogShown = true
        compose.waitForIdle()
        val first = firstWidth("panel")
        settle()
        val last = compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot.width
        return first / last
    }

    private val styleState = mutableStateOf<SurfaceStyle>(SurfaceStyles.CleanLight)
    private var captureSet = false

    private fun capture(surface: SurfaceStyle): PixelMap {
        if (!captureSet) {
            captureSet = true
            compose.setContent {
                Box(Modifier.background(Color.White).padding(16.dp)) {
                    Box(Modifier.testTag("surface").size(120.dp, 60.dp).kSurface(styleState.value, RectangleShape))
                }
            }
        }
        styleState.value = surface
        compose.waitForIdle()
        return compose.onNodeWithTag("surface").captureToImage().toPixelMap()
    }

    private var shadowSet = false

    /** [surface] 바로 아래 바깥 픽셀의 밝기. 그림자가 진할수록 낮다. */
    private fun shadowBelow(surface: SurfaceStyle): Float {
        if (!shadowSet) {
            shadowSet = true
            compose.setContent {
                Box(Modifier.testTag("frame").background(Color.White).padding(24.dp)) {
                    Box(Modifier.size(120.dp, 60.dp).kSurface(styleState.value, RoundedCornerShape(8.dp)))
                }
            }
        }
        styleState.value = surface
        compose.waitForIdle()
        val map = compose.onNodeWithTag("frame").captureToImage().toPixelMap()
        // 그림자는 아래로 조금 비껴 드리우므로 표면 아래 끝 바로 밑이 진하다.
        return map[map.width / 2, SHADOW_PROBE_Y].red
    }

    /** 열기 시작해 [tag] 노드가 처음 생긴 프레임의 폭 */
    private fun firstWidth(tag: String): Float {
        repeat(MAX_OPEN_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            val nodes = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
            if (nodes.isNotEmpty()) return nodes.single().boundsInRoot.width
        }
        error("$tag 노드가 생기지 않았습니다.")
    }

    private fun top(text: String): Float = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }
}

private const val MAX_OPEN_FRAMES = 6
private const val SHADOW_PROBE_Y = 24 + 60 + 3
