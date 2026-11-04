package com.naury.kinetica.ui

import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 렌즈 손잡이, 인디케이터 끌기, 묶음 글래스 값, 점진 블러, 떠 있는 글래스 시트.
 * Robolectric은 런타임 셰이더 효과를 만들지 못해 셰이더는 문법과 uniform만 확인하고, 모양은 기기에서 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassFxTest {
    @get:Rule
    val compose = createComposeRule()

    private val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun `렌즈 셰이더는 렌즈 크기와 광학 값을 받아 컴파일된다`() {
        // 테스트 환경의 Skia는 eval 대신 옛 sample 문법만 알아 그 부분만 바꿔 나머지 문법을 확인한다.
        val shader = RuntimeShader(LENS_SHADER.replace("content.eval(", "sample(content, "))
        shader.setFloatUniform("size", 120f, 80f)
        shader.setFloatUniform("radius", 40f)
        shader.setFloatUniform("zoom", 1.3f)
        shader.setFloatUniform("bend", 12f)
        shader.setFloatUniform("tint", 0.1f, 0.1f, 0.1f, 0.1f)
    }

    @Test
    fun `묶음 셰이더는 판 배열을 받아 컴파일된다`() {
        val shader = RuntimeShader(GROUP_SHADER.replace("content.eval(", "sample(content, "))
        val uniforms = GroupUniforms()
        uniforms.update(listOf(Rect(0f, 0f, 40f, 40f)), listOf(20f), 10f, Size(200f, 100f), 16f)
        shader.setFloatUniform("rects", uniforms.rects)
        shader.setFloatUniform("extra", uniforms.extra)
        shader.setFloatUniform("count", 1f)
        shader.setFloatUniform("rim", 1f, 1f, 1f, 0.5f)
    }

    @Test
    fun `묶음 글래스 값은 판이 그대로면 다시 올리지 않고 움직이면 올린다`() {
        val uniforms = GroupUniforms()
        val plates = listOf(Rect(0f, 0f, 40f, 40f), Rect(50f, 0f, 90f, 40f))
        val size = Size(200f, 100f)
        assertThat(uniforms.update(plates, listOf(20f, 20f), 8f, size, 16f)).isTrue()
        assertThat(uniforms.update(plates, listOf(20f, 20f), 8f, size, 16f)).isFalse()
        val moved = listOf(Rect(0f, 0f, 40f, 40f), Rect(60f, 0f, 100f, 40f))
        assertThat(uniforms.update(moved, listOf(20f, 20f), 8f, size, 16f)).isTrue()
        // 레이어 좌표는 사방 여백만큼 밀리고, 모서리는 짧은 변 절반을 넘지 않는다.
        assertThat(uniforms.rects[4]).isEqualTo(80f + 8f)
        assertThat(uniforms.update(moved, listOf(99f, 10f), 8f, size, 16f)).isTrue()
        assertThat(uniforms.extra[0]).isEqualTo(20f)
    }

    @Test
    fun `묶음 글래스는 판을 8개까지만 담고 배치되지 않은 판은 뺀다`() {
        val uniforms = GroupUniforms()
        val plates = List(11) { Rect(it * 10f, 0f, it * 10f + 8f, 8f) } + Rect.Zero
        uniforms.update(plates, List(plates.size) { 4f }, 0f, Size(200f, 20f), 4f)
        assertThat(uniforms.count).isEqualTo(MAX_GLASS_PLATES)
        uniforms.update(listOf(Rect.Zero, Rect(0f, 0f, 8f, 8f)), listOf(4f, 4f), 0f, Size(200f, 20f), 4f)
        assertThat(uniforms.count).isEqualTo(1)
    }

    @Test
    fun `묶음 글래스 안 판 위 내용은 그대로 눌린다`() {
        var taps = 0
        val spread = mutableIntStateOf(0)
        compose.setContent {
            KineticaScaffold(Modifier.size(360.dp, 400.dp), surface = SurfaceStyles.Liquid, edgeFade = false, background = {
                Box(Modifier.fillMaxSize().background(Color(0xFF3B6CFF)))
            }) {
                KGlassGroup(Modifier.size(200.dp)) {
                    repeat(MAX_GLASS_PLATES + 2) { i ->
                        Box(Modifier.offset { IntOffset(i * spread.intValue, 0) }.glassId(i).size(40.dp))
                    }
                    KButton("열기", { taps++ }, Modifier.offset(0.dp, 120.dp).glassId("button"))
                }
            }
        }
        compose.onRoot().captureToImage()
        spread.intValue = 30
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        compose.onNodeWithText("열기").performClick()
        assertThat(taps).isEqualTo(1)
    }

    @Test
    fun `가까운 칸 고르기는 가운데가 가장 가까운 칸이다`() {
        val cells = floatArrayOf(0f, 100f, 100f, 180f, 180f, 300f)
        assertThat(nearestCell(cells, 30f)).isEqualTo(0)
        assertThat(nearestCell(cells, 150f)).isEqualTo(1)
        assertThat(nearestCell(cells, 290f)).isEqualTo(2)
        assertThat(nearestCell(FloatArray(0), 10f)).isEqualTo(-1)
    }

    @Test
    fun `렌즈 슬라이더는 값과 동작을 그대로 내보내고 잡지 않으면 렌즈를 만들지 않는다`() {
        var value by mutableStateOf(0.2f)
        compose.setContent { Box(Modifier.width(300.dp)) { KSlider(value, { value = it }, label = "볼륨", lens = true) } }
        val builds = GlassFxStats.lensBuilds
        repeat(3) {
            value = it / 4f
            compose.waitForIdle()
            compose.onRoot().captureToImage()
        }
        assertThat(GlassFxStats.lensBuilds).isEqualTo(builds)
        compose.onNode(slider).performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }
        assertThat(value).isWithin(0.001f).of(0.8f)
    }

    @Test
    fun `렌즈 슬라이더를 끌면 값이 손가락을 따른다`() {
        var value by mutableStateOf(0f)
        compose.setContent { Box(Modifier.width(300.dp).testTag("box")) { KSlider(value, { value = it }, lens = true) } }
        compose.onNode(slider).performTouchInput {
            down(Offset(width * 0.1f, centerY))
            moveTo(Offset(width * 0.5f, centerY))
            moveTo(Offset(width * 0.9f, centerY))
        }
        compose.onRoot().captureToImage()
        compose.onNode(slider).performTouchInput { up() }
        assertThat(value).isGreaterThan(0.8f)
    }

    @Test
    fun `렌즈 세그먼트는 인디케이터를 끌어 놓은 칸을 고르고 탭도 그대로 고른다`() {
        var selected by mutableIntStateOf(0)
        compose.setContent {
            Box(Modifier.width(300.dp)) {
                KSegmentedControl(listOf("하루", "한 주", "한 달"), selected, { selected = it }, Modifier.testTag("seg"), lens = true)
            }
        }
        compose.onNodeWithTag("seg").performTouchInput {
            down(Offset(width / 6f, centerY))
            repeat(10) { moveBy(Offset(width / 15f, 0f)) }
            up()
        }
        compose.waitForIdle()
        assertThat(selected).isEqualTo(2)
        compose.onNodeWithText("한 주").performClick()
        assertThat(selected).isEqualTo(1)
    }

    @Test
    fun `렌즈를 끈 세그먼트는 인디케이터를 끌어도 칸이 바뀌지 않는다`() {
        var selected by mutableIntStateOf(0)
        compose.setContent {
            Box(Modifier.width(300.dp)) {
                KSegmentedControl(listOf("하루", "한 주", "한 달"), selected, { selected = it }, Modifier.testTag("seg"))
            }
        }
        compose.onNodeWithTag("seg").performTouchInput {
            down(Offset(width / 6f, centerY))
            repeat(10) { moveBy(Offset(width / 15f, 0f)) }
            up()
        }
        compose.waitForIdle()
        assertThat(selected).isEqualTo(0)
    }

    @Test
    fun `렌즈 탭 바는 인디케이터를 끌어 다른 탭을 고른다`() {
        var selected by mutableIntStateOf(0)
        val tabs = listOf(KTab("홈"), KTab("검색"), KTab("보관함"), KTab("설정"))
        compose.setContent {
            Box(Modifier.width(400.dp)) { KFloatingTabBar(tabs, selected, { selected = it }, Modifier.testTag("bar"), lens = true) }
        }
        compose.onNodeWithTag("bar").performTouchInput {
            down(Offset(width * 0.14f, centerY))
            repeat(9) { moveBy(Offset(width / 20f, 0f)) }
            up()
        }
        compose.waitForIdle()
        assertThat(selected).isEqualTo(2)
    }

    @Test
    fun `렌즈 스위치도 역할과 켜고 끄기는 그대로다`() {
        var on by mutableStateOf(false)
        compose.setContent { KSwitch(on, { on = it }, Modifier.testTag("sw"), lens = true) }
        compose.onNodeWithTag("sw")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
            .performClick()
        compose.onNodeWithTag("sw").assertIsOn()
    }

    @Test
    fun `점진 블러는 스크롤하는 동안 띠를 다시 기록하지 않고 맨 위에서는 위쪽 띠를 숨긴다`() {
        var list: LazyListState? = null
        compose.setContent {
            val state = rememberLazyListState().also { list = it }
            LazyColumn(Modifier.size(300.dp, 400.dp).scrollEdgeBlur(state, top = 40.dp, bottom = 40.dp), state = state) {
                items(80) { Text("행 $it", Modifier.fillMaxWidth().height(48.dp)) }
            }
        }
        compose.onRoot().captureToImage()
        val state = requireNotNull(list)
        assertThat(state.canScrollBackward).isFalse()
        compose.runOnIdle { runBlocking { state.scrollToItem(20) } }
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        val builds = GlassFxStats.edgeBuilds
        repeat(4) {
            compose.runOnIdle { runBlocking { state.scrollBy(37f) } }
            compose.waitForIdle()
            compose.onRoot().captureToImage()
        }
        assertThat(GlassFxStats.edgeBuilds).isEqualTo(builds)
    }

    @Test
    fun `떠 있는 글래스 시트는 가장 높은 높이에서 붙고 그 아래 높이에서 떠 있다`() {
        val state = KDetentSheetState(listOf(Detent.Peek(120.dp), Detent.Fraction(0.5f), Detent.Full), Detent.Peek(120.dp), false)
        state.updateLayout(1000f, 40f, Density(1f))
        assertThat(state.attachLevel()).isEqualTo(0f)
        state.snapTo(Detent.Fraction(0.5f))
        assertThat(state.attachLevel()).isEqualTo(0f)
        state.dragBy((state.positionOf(Detent.Full) - state.positionOf(Detent.Fraction(0.5f))) / 2f)
        assertThat(state.attachLevel()).isWithin(0.01f).of(0.5f)
        state.snapTo(Detent.Full)
        assertThat(state.attachLevel()).isEqualTo(1f)
    }

    @Test
    fun `떠 있는 글래스 시트도 접근성 상태와 동작은 그대로다`() {
        compose.setContent {
            KineticaScaffold(Modifier.size(360.dp, 640.dp), surface = SurfaceStyles.Liquid, edgeFade = false) {
                val sheet = rememberKDetentSheetState()
                KDetentSheet(sheet, title = "장소", floatingGlass = true) { Column { Text("본문") } }
            }
        }
        compose.onRoot().captureToImage()
        compose.onNodeWithText("본문").assertExists()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "장소"))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
    }
}
