package com.naury.kinetica.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSensor

/** 호스트 앱 개발자가 쓰는 방식 그대로 Material 컴포넌트와 목록에 효과를 붙여 본다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HostUsageTest {
    @get:Rule
    val compose = createComposeRule()

    private val oneLiners: List<Pair<String, @Composable () -> Modifier>> = listOf(
        "jellyPress" to { Modifier.jellyPress() },
        "magnetic" to { Modifier.magnetic() },
        "motionTilt" to { Modifier.motionTilt() },
        "glowBorder" to { Modifier.glowBorder(CircleShape) },
        "shake" to { Modifier.shake(0) },
        "sparkle" to { Modifier.sparkle() },
        "spotlight" to { Modifier.spotlight() },
        "glassBadge" to { Modifier.glassBadge(3) },
        "skeleton" to { Modifier.skeleton(visible = false) },
        "glass" to { Modifier.glass(shape = CircleShape) },
        "glassContextMenu" to { Modifier.glassContextMenu(listOf(GlassMenuItem("삭제") {})) },
        "tooltipOnLongPress" to { Modifier.tooltipOnLongPress("도움말") },
        "expandSource" to { Modifier.expandSource(rememberGlassExpandState(), "card") },
        "coachMarkTarget" to { Modifier.coachMarkTarget(rememberCoachMarkState(), "target") },
    )

    @Test
    fun `한 줄 수정자는 Material 버튼과 clickable 앞뒤 어디에 붙여도 탭과 버튼 역할을 지킨다`() {
        val index = mutableIntStateOf(0)
        val clicks = IntArray(3)
        compose.setContent {
            key(index.intValue) {
                val effect = oneLiners[index.intValue].second
                Column {
                    Button(onClick = { clicks[0]++ }, modifier = effect().testTag("material")) { Text("구매") }
                    Box(effect().clickable { clicks[1]++ }.size(80.dp, 48.dp).testTag("before"))
                    Box(Modifier.clickable { clicks[2]++ }.then(effect()).size(80.dp, 48.dp).testTag("after"))
                }
            }
        }
        oneLiners.forEachIndexed { i, (name, _) ->
            index.intValue = i
            compose.waitForIdle()
            listOf("material", "before", "after").forEachIndexed { host, tag ->
                val before = clicks[host]
                compose.onNodeWithTag(tag).performTouchInput { click() }
                compose.waitForIdle()
                assertWithMessage("$name / $tag").that(clicks[host]).isEqualTo(before + 1)
            }
            compose.onNodeWithTag("material")
                .assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
    }

    @Test
    fun `magnetic은 목록이 스크롤되기 시작하면 손가락을 따라가지 않는다`() {
        compose.setContent {
            LazyColumn(Modifier.size(300.dp, 400.dp).testTag("list")) {
                items(40) { i ->
                    Row {
                        Box(Modifier.size(100.dp, 60.dp).magnetic(1f).testTag("m$i"))
                        Box(Modifier.size(100.dp, 60.dp).testTag("p$i"))
                    }
                }
            }
        }
        compose.onNodeWithTag("m2").performTouchInput {
            down(center)
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(0f, -height * 0.1f)) }
        }
        compose.waitForIdle()
        val pulled = compose.onNodeWithTag("m2").getBoundsInRoot()
        val plain = compose.onNodeWithTag("p2").getBoundsInRoot()
        assertThat(pulled.top.value).isWithin(1f).of(plain.top.value)
        assertThat(pulled.left.value).isWithin(1f).of(0f)
        compose.onNodeWithTag("m2").performTouchInput { up() }
    }

    @Test
    fun `GlassTheme은 다크 스타일과 강조색과 글꼴을 컴포넌트 기본값으로 넘긴다`() {
        var style: GlassStyle? = null
        var ink = Color.Unspecified
        var scaffoldStyle: GlassStyle? = null
        compose.setContent {
            GlassTheme(
                darkTheme = true,
                colors = GlassColors(accent = Color.Red),
                // 줄 높이 같은 크기 값은 컴포넌트가 정하므로 무시되어야 한다.
                textStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 40.sp, lineHeight = 60.sp),
            ) {
                style = LocalGlassStyle.current
                ink = LocalGlassContentColor.current
                GlassScaffold {
                    scaffoldStyle = LocalGlassStyle.current
                    GlassButton("결제", {}, Modifier.testTag("pay"))
                }
            }
        }
        assertThat(style).isEqualTo(GlassStyles.LiquidDark)
        assertThat(scaffoldStyle).isEqualTo(GlassStyles.LiquidDark)
        assertThat(ink).isEqualTo(Color.White)

        val pixels = compose.onNodeWithTag("pay").captureToImage().toPixelMap()
        var red = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val c = pixels[x, y]
                if (c.red > 0.8f && c.green < 0.3f && c.blue < 0.3f) red++
            }
        }
        assertThat(red).isGreaterThan(pixels.width * pixels.height / 3)

        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("결제").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        val textStyle = layouts.single().layoutInput.style
        assertThat(textStyle.fontFamily).isEqualTo(FontFamily.Serif)
        assertThat(textStyle.fontSize).isNotEqualTo(40.sp)
        assertThat(textStyle.lineHeight).isNotEqualTo(60.sp)
    }

    @Test
    fun `효과와 글래스와 배지를 붙인 300개 목록은 애니메이션 중에 항목을 다시 구성하지 않는다`() {
        var compositions = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LazyColumn(Modifier.size(360.dp, 640.dp)) {
                items(300) { i ->
                    SideEffect { compositions++ }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .glowBorder()
                            .sparkle()
                            .jellyPress()
                            .motionTilt()
                            .glassBadge(i % 5)
                            .skeleton(visible = i % 2 == 0)
                            .glass(),
                    ) { ShimmerText("항목 $i") }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val settled = compositions
        assertThat(settled).isGreaterThan(0)

        compose.mainClock.advanceTimeBy(2_000)
        assertThat(compositions).isEqualTo(settled)
    }

    @Test
    fun `반복 애니메이션 시계는 창 밖에 있는 동안 멈춘다`() {
        lateinit var shown: LoopClock
        lateinit var hidden: LoopClock
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Column(Modifier.size(200.dp, 300.dp).verticalScroll(rememberScrollState())) {
                shown = rememberLoopClock(running = true)
                hidden = rememberLoopClock(running = true)
                Box(Modifier.size(50.dp).loopVisibility(shown))
                Spacer(Modifier.height(3_000.dp))
                Box(Modifier.size(50.dp).loopVisibility(hidden))
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(shown.seconds).isGreaterThan(0.5f)
        assertThat(hidden.seconds).isEqualTo(0f)
    }

    @Test
    fun `motionTilt는 컴포지션에서 빠지면 센서 리스너를 해제한다`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowOf(manager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_GRAVITY))
        val visible = mutableStateOf(true)
        compose.setContent { if (visible.value) Box(Modifier.size(40.dp).motionTilt()) }
        compose.waitForIdle()
        assertThat(shadowOf(manager).listeners).isNotEmpty()

        visible.value = false
        compose.waitForIdle()
        assertThat(shadowOf(manager).listeners).isEmpty()
    }
}
