package com.naury.kinetica.ui

import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
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
 * 굴절 있는 콘텐츠 글래스가 요소마다 화면 밖 레이어를 두지 않고 함께 쓰는 굴절 지도 하나를 쓰는지 본다.
 * 요소 레이어는 배경이 바뀌는 프레임마다 렌더 패스가 되므로 그 수를 비용의 대리 지표로 쓴다.
 * Robolectric은 런타임 셰이더 효과를 만들지 못해 굴절 모양 자체는 기기에서 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GlassRefractionMapTest {
    @get:Rule
    val compose = createComposeRule()

    private val gradient = Brush.linearGradient(listOf(Color(0xFFFF4F6D), Color(0xFF3B6CFF), Color(0xFF29D3A0)))
    private var backdrop: GlassBackdrop? = null
    private var listState: LazyListState? = null

    private fun scaffold(items: Int, red: () -> Float = { 1f }) {
        compose.setContent {
            // 가장자리 흐림은 바와 같은 겹을 비추는 별도 글래스라 이 시험에서는 끈다.
            KineticaScaffold(
                Modifier.size(360.dp, 640.dp),
                surface = SurfaceStyles.Liquid,
                edgeFade = false,
                background = { Box(Modifier.fillMaxSize().background(gradient).drawBehind { drawRect(Color(red(), 0f, 0f, 0.3f)) }) },
            ) { padding ->
                backdrop = LocalGlassBackdrop.current
                val state = rememberLazyListState().also { listState = it }
                LazyColumn(state = state, contentPadding = padding) {
                    items(items) { i ->
                        KSurface(Modifier.padding(12.dp).fillMaxWidth().height(80.dp)) {
                            Row { KButton("버튼 $i", {}, variant = KVariant.Glass) }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureToImage()
    }

    @Test
    fun `굴절 있는 카드와 버튼은 요소 레이어 없이 지도 하나를 함께 쓴다`() {
        val before = GlassStats.elementLayers
        scaffold(3)
        val shared = requireNotNull(backdrop?.shared)
        assertThat(shared.count).isEqualTo(1)
        // 카드 3장과 그 안 버튼 3개가 모두 자리를 잡는다.
        assertThat(shared.regions).isEqualTo(6)
        assertThat(GlassStats.elementLayers - before).isEqualTo(0)
    }

    @Test
    fun `배경만 바뀌면 굴절 지도도 요소 레이어도 새로 만들지 않는다`() {
        val red = mutableFloatStateOf(1f)
        scaffold(4) { red.floatValue }
        val builds = GlassStats.refractionBuilds
        val layers = GlassStats.elementLayers
        repeat(3) {
            red.floatValue = it / 3f
            compose.waitForIdle()
            compose.onRoot().captureToImage()
        }
        assertThat(GlassStats.refractionBuilds).isEqualTo(builds)
        assertThat(GlassStats.elementLayers).isEqualTo(layers)
    }

    @Test
    fun `스크롤로 빠진 글래스는 자리를 비우고 새로 들어온 글래스가 이어 쓴다`() {
        scaffold(60)
        val shared = requireNotNull(backdrop?.shared)
        val visible = shared.regions
        assertThat(visible).isGreaterThan(0)
        compose.runOnIdle { runBlocking { requireNotNull(listState).scrollToItem(50) } }
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        // 지연 목록이 미리 만들어 두는 항목 몇 개를 빼면 화면에 보이는 수만큼만 남는다.
        assertThat(shared.regions).isAtMost(visible + 4)
        assertThat(shared.regions).isAtMost(MAX_REGIONS)
    }

    @Test
    fun `자리가 꽉 차면 넘친 글래스만 혼자 굴절 레이어를 쓴다`() {
        val before = GlassStats.elementLayers
        compose.setContent {
            KineticaScaffold(Modifier.fillMaxSize(), surface = SurfaceStyles.Liquid, edgeFade = false, background = {
                Box(Modifier.fillMaxSize().background(gradient))
            }) {
                backdrop = LocalGlassBackdrop.current
                Box(Modifier.fillMaxSize()) {
                    repeat(MAX_REGIONS + 3) { i ->
                        Box(Modifier.padding(start = (i * 4).dp, top = (i * 8).dp).height(20.dp).fillMaxWidth(0.3f).kSurface())
                    }
                }
            }
        }
        compose.onRoot().captureToImage()
        assertThat(requireNotNull(backdrop?.shared).regions).isEqualTo(MAX_REGIONS)
        assertThat(GlassStats.elementLayers - before).isEqualTo(3)
    }

    @Test
    fun `굴절 지도 셰이더는 자리 배열을 받아 컴파일된다`() {
        // 테스트 환경의 Skia는 eval 대신 옛 sample 문법만 알아 그 부분만 바꿔 나머지 문법과 배열 uniform을 확인한다.
        val shader = RuntimeShader(MAP_SHADER.replace("content.eval(", "sample(content, "))
        shader.setFloatUniform("bounds", 100f, 100f)
        shader.setFloatUniform("count", 2f)
        shader.setFloatUniform("boxes", FloatArray(MAX_REGIONS * 4))
        shader.setFloatUniform("shapes", FloatArray(MAX_REGIONS * 4))
    }
}
