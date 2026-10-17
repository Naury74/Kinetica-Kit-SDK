package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 콘텐츠 글래스가 블러 기록을 함께 쓰는지, 함께 써도 혼자 블러를 걸 때와 같은 모습인지 본다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GlassSharedBlurTest {
    @get:Rule
    val compose = createComposeRule()

    private val gradient = Brush.linearGradient(listOf(Color(0xFFFF4F6D), Color(0xFF3B6CFF), Color(0xFF29D3A0)))

    private var lastBackdrop: GlassBackdrop? = null

    @Composable
    private fun Cards(shared: Boolean, style: GlassStyle) {
        val backdrop = rememberGlassBackdrop()
        lastBackdrop = backdrop
        if (shared) ShareBlurs(backdrop)
        Box(Modifier.size(360.dp, 480.dp)) {
            // 블러와 굴절이 드러나도록 또렷한 줄무늬를 깐다.
            Row(Modifier.fillMaxSize().glassBackdrop(backdrop).background(gradient)) {
                repeat(STRIPES) { i ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().background(
                            if (i % 2 ==
                                0
                            ) {
                                Color.White
                            } else {
                                Color.Transparent
                            },
                        ),
                    )
                }
            }
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                listOf(120.dp, 60.dp, 160.dp).forEachIndexed { i, height ->
                    Box(
                        Modifier
                            .padding(bottom = 16.dp)
                            .fillMaxWidth()
                            .height(height)
                            .testTag("card$i")
                            .glass(style, RoundedCornerShape(24.dp), backdrop),
                    )
                }
            }
        }
    }

    private fun meanDifference(a: ImageBitmap, b: ImageBitmap): Float {
        val pa = a.toPixelMap()
        val pb = b.toPixelMap()
        var sum = 0f
        for (y in 0 until pa.height) {
            for (x in 0 until pa.width) {
                val ca = pa[x, y]
                val cb = pb[x, y]
                sum += (abs(ca.red - cb.red) + abs(ca.green - cb.green) + abs(ca.blue - cb.blue)) / 3f
            }
        }
        return sum / (pa.width * pa.height)
    }

    @Test
    fun `함께 쓰는 블러는 혼자 거는 블러와 거의 같은 모습이다`() {
        val styles = listOf(GlassStyles.Acrylic, GlassStyles.Liquid, GlassStyles.LiquidDark)
        // setContent는 한 번만 부를 수 있으므로 같은 화면 안에서 스타일과 공유 여부를 바꿔 가며 새로 만든다.
        val mode = mutableIntStateOf(0)
        compose.setContent { key(mode.intValue) { Cards(mode.intValue % 2 == 1, styles[mode.intValue / 2]) } }
        for (i in styles.indices) {
            mode.intValue = i * 2
            compose.waitForIdle()
            val alone = compose.onRoot().captureToImage()
            mode.intValue = i * 2 + 1
            compose.waitForIdle()
            val together = compose.onRoot().captureToImage()
            assertThat(lastBackdrop?.shared?.count).isEqualTo(1)
            assertThat(meanDifference(alone, together)).isLessThan(MAX_DIFFERENCE)
        }
    }

    @Test
    fun `스캐폴드 콘텐츠의 글래스 카드는 블러 기록 하나를 함께 쓴다`() {
        var backdrop: GlassBackdrop? = null
        compose.setContent {
            KineticaScaffold(
                Modifier.size(360.dp, 640.dp),
                surface = SurfaceStyles.Liquid,
                background = { Box(Modifier.fillMaxSize().background(gradient)) },
            ) { padding ->
                backdrop = LocalGlassBackdrop.current
                LazyColumn(contentPadding = padding) {
                    items(12) { i ->
                        KSurface(Modifier.padding(12.dp).fillMaxWidth().height(80.dp)) {
                            Row { KButton("버튼 $i", {}, variant = KVariant.Glass) }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureToImage()

        val shared = requireNotNull(backdrop?.shared)
        // 카드와 그 안 버튼은 같은 스타일이라 블러는 하나다. 카드 수와 상관없다.
        assertThat(shared.count).isEqualTo(1)
    }

    @Test
    fun `글래스 여럿이 함께 쓰는 블러는 한 번만 계산되도록 효과 없는 레이어만 그린다`() {
        // HWUI는 RenderEffect가 걸린 레이어를 그리는 잘림 영역마다 효과를 다시 계산한다. 글래스가 효과 걸린 레이어를 바로 그리면
        // 배경이 바뀌는 프레임마다 글래스 수만큼 블러를 하므로, 글래스가 그리는 레이어에는 효과가 없어야 한다.
        for (style in listOf(GlassStyles.Liquid.copy(refraction = 0f), GlassStyles.Liquid)) {
            val shared = requireNotNull(callScreen(style).shared)
            assertThat(shared.count).isEqualTo(1)
            val blur = shared.all.single()
            assertThat(blur.filtered.renderEffect).isNotNull()
            assertThat(blur.layer.renderEffect).isNull()
            assertThat(blur.layer.colorFilter).isNotNull()
            assertThat(blur.layer.compositingStrategy).isEqualTo(CompositingStrategy.Offscreen)
            if (style.refraction > 0f) {
                // Robolectric은 RuntimeShader를 만들지 못해 굴절 효과 자체는 확인하지 않는다. 자리 등록과 레이어 구성만 본다.
                assertThat(shared.regions).isEqualTo(CALL_GLASSES)
                assertThat(blur.bent.renderEffect).isNull()
                assertThat(blur.bent.compositingStrategy).isEqualTo(CompositingStrategy.Offscreen)
            }
        }
    }

    private val callStyle = mutableIntStateOf(0)
    private var callStyles: List<GlassStyle> = emptyList()
    private var callBackdrop: GlassBackdrop? = null

    /** 영상 통화처럼 위쪽 바와 버튼 셋, 아래 도크를 둔 화면을 [style]로 그리고 그 배경을 돌려준다. */
    private fun callScreen(style: GlassStyle): GlassBackdrop {
        if (callStyles.isEmpty()) {
            callStyles = listOf(style)
            compose.setContent { key(callStyle.intValue) { CallScreen(callStyles[callStyle.intValue]) { callBackdrop = it } } }
        } else {
            callStyles = callStyles + style
            callStyle.intValue = callStyles.lastIndex
        }
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        return requireNotNull(callBackdrop)
    }

    @Composable
    private fun CallScreen(style: GlassStyle, onBackdrop: (GlassBackdrop) -> Unit) {
        KineticaScaffold(
            Modifier.size(360.dp, 640.dp),
            surface = SurfaceStyle.Glass(style),
            background = { Box(Modifier.fillMaxSize().background(gradient)) },
        ) { _ ->
            LocalGlassBackdrop.current?.let(onBackdrop)
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).height(48.dp).glass(style, RoundedCornerShape(24.dp)))
                    repeat(CALL_GLASSES - 2) { Box(Modifier.size(48.dp).glass(style, CircleShape)) }
                }
                Box(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(68.dp).glass(style, RoundedCornerShape(34.dp)))
            }
        }
    }

    private fun assertFollowsShift(style: GlassStyle) {
        // 처음에는 오른쪽(파란 쪽)에 그렸다가 다시 그리지 않고 레이어 이동만으로 왼쪽(빨간 쪽)에 들어온다.
        val shift = mutableFloatStateOf(280f)
        compose.setContent { ShiftScene(style) { shift.floatValue } }
        compose.onRoot().captureToImage()
        shift.floatValue = 0f
        compose.waitForIdle()
        compose.onRoot().captureToImage()

        val image = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        val center = image[image.width / 2, image.height / 2]
        assertThat(center.red).isGreaterThan(0.8f)
        assertThat(center.blue).isLessThan(0.2f)
    }

    @Test
    fun `함께 쓰는 블러도 레이어만 옮긴 글래스가 놓인 자리의 배경을 비춘다`() {
        assertFollowsShift(CLEAR)
    }

    @Test
    fun `굴절이 있어도 레이어만 옮긴 글래스가 놓인 자리의 배경을 비춘다`() {
        assertFollowsShift(CLEAR.copy(refraction = 0.6f))
    }

    private var draws = 0

    private fun assertFollowsBackdrop(shared: Boolean, style: GlassStyle) {
        // 배경만 다시 그리고 글래스는 다시 그리지 않는다. 움직이는 메시 배경 위에 가만히 있는 카드가 이 경우다.
        val red = mutableFloatStateOf(1f)
        compose.setContent {
            val backdrop = rememberGlassBackdrop()
            if (shared) ShareBlurs(backdrop)
            Box(Modifier.size(200.dp)) {
                Box(Modifier.fillMaxSize().glassBackdrop(backdrop).drawBehind { drawRect(Color(red.floatValue, 0f, 1f - red.floatValue)) })
                // 글래스를 따로 레이어에 두어 배경이 다시 그려져도 글래스의 그리기 내용은 다시 기록되지 않게 한다.
                Box(Modifier.graphicsLayer { }) {
                    Box(Modifier.padding(40.dp).size(80.dp).testTag("glass").glass(style, backdrop = backdrop).drawBehind { draws++ })
                }
            }
        }
        compose.onRoot().captureToImage()
        val before = draws
        red.floatValue = 0f
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        // 전제: 글래스는 다시 그리지 않았다.
        assertThat(draws).isEqualTo(before)

        val image = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        val center = image[image.width / 2, image.height / 2]
        assertThat(center.blue).isGreaterThan(0.8f)
        assertThat(center.red).isLessThan(0.2f)
    }

    @Test
    fun `배경만 다시 그려도 혼자 거는 글래스가 바뀐 배경을 비춘다`() = assertFollowsBackdrop(shared = false, CLEAR.copy(blur = 12.dp))

    @Test
    fun `배경만 다시 그려도 함께 쓰는 블러가 바뀐 배경을 비춘다`() = assertFollowsBackdrop(shared = true, CLEAR.copy(blur = 12.dp))

    @Test
    fun `배경만 다시 그려도 함께 쓰는 블러 위 굴절이 바뀐 배경을 비춘다`() = assertFollowsBackdrop(shared = true, CLEAR.copy(blur = 12.dp, refraction = 0.6f))

    @Composable
    private fun ShiftScene(style: GlassStyle, shiftDp: () -> Float) {
        val backdrop = rememberGlassBackdrop()
        ShareBlurs(backdrop)
        Box(Modifier.size(400.dp, 200.dp)) {
            Row(Modifier.fillMaxSize().glassBackdrop(backdrop)) {
                Box(Modifier.weight(1f).fillMaxHeight().background(Color.Red))
                Box(Modifier.weight(1f).fillMaxHeight().background(Color.Blue))
            }
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = shiftDp() * density }) {
                Box(Modifier.padding(20.dp).size(40.dp).testTag("glass").glass(style, backdrop = backdrop))
            }
        }
    }

    @Test
    fun `큰 제목 바가 다 접히면 가장자리 흐림을 만들지 않는다`() {
        var host: KOverlayHost? = null
        val list = mutableListOf<() -> Unit>()
        compose.setContent {
            val listState = rememberLazyListState()
            val title = rememberKLargeTitleState(listState)
            list += { runBlocking { listState.scrollToItem(8) } }
            KineticaScaffold(
                Modifier.size(360.dp, 640.dp),
                surface = SurfaceStyles.Liquid,
                background = { Box(Modifier.fillMaxSize().background(gradient)) },
                topBar = { KLargeTitleBar(title, "제목") },
            ) { padding ->
                host = LocalKineticaHost.current
                LazyColumn(Modifier.fillMaxSize(), listState, contentPadding = padding) {
                    items(30) { Box(Modifier.fillMaxWidth().height(80.dp)) }
                }
            }
        }
        compose.waitForIdle()
        assertThat(requireNotNull(host).statusBarCovered).isFalse()

        compose.runOnIdle { list.first().invoke() }
        compose.waitForIdle()
        assertThat(requireNotNull(host).statusBarCovered).isTrue()
    }

    private companion object {
        const val MAX_DIFFERENCE = 0.02f
        const val STRIPES = 18
        const val CALL_GLASSES = 5
        val CLEAR = GlassStyle(blur = 0.dp, saturation = 1f, tint = Color.Transparent, edgeLight = Color.Transparent, shadow = 0.dp)
    }
}
