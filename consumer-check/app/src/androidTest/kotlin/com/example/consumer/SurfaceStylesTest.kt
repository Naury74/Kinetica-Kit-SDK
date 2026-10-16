package com.example.consumer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.naury.kinetica.ui.GlassBackdrop
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KColorPicker
import com.naury.kinetica.ui.KDialog
import com.naury.kinetica.ui.KIndication
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KTextField
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalGlassBackdrop
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.view.KSurfaceView
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 시나리오 11: 호스트 앱이 글래스 대신 단색이나 톤 표면을 고른다. 배포 artifact만으로 표면 스타일을 바꾸고,
 * 바꿔도 열린 시트가 남으며, 시스템 바 아이콘과 눌림 표시가 표면 스타일을 따르는지 본다.
 */
@RunWith(AndroidJUnit4::class)
class SurfaceStylesTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var surface by mutableStateOf<SurfaceStyle>(SurfaceStyles.Liquid)

    private fun close(a: Color, b: Color): Boolean = abs(a.red - b.red) < 0.03f && abs(a.green - b.green) < 0.03f && abs(a.blue - b.blue) < 0.03f

    private fun lightStatusIcons(): Boolean {
        var light = false
        rule.runOnIdle {
            light = WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars
        }
        return light
    }

    private fun setHostIcons(lightBackground: Boolean) = rule.runOnIdle {
        WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars = lightBackground
    }

    /** 시트와 다이얼로그를 여는 버튼, 입력란이 있는 호스트 화면. */
    private fun setHost() {
        rule.setContent {
            var sheet by remember { mutableStateOf(false) }
            var dialog by remember { mutableStateOf(false) }
            var text by remember { mutableStateOf("") }
            KineticaScaffold(Modifier.fillMaxSize().testTag("screen"), surface = surface) {
                Column {
                    KButton("시트 열기", { sheet = true }, Modifier.testTag("openSheet"))
                    KButton("다이얼로그 열기", { dialog = true }, Modifier.testTag("openDialog"), variant = KVariant.Outlined)
                    KTextField(text, { text = it }, "메모", Modifier.fillMaxWidth().testTag("field"))
                }
                KSheet(sheet, { sheet = false }, title = "시트") { Text("시트 내용") }
                KDialog(dialog, { dialog = false }, title = "다이얼로그") { Text("다이얼로그 내용") }
            }
        }
    }

    @Test
    fun solidScaffoldPaintsSurfaceBackground() {
        surface = SurfaceStyles.CleanLight
        setHost()
        rule.waitForIdle()
        val map = rule.onNodeWithTag("screen").captureToImage().toPixelMap()
        val corner = map[map.width - 8, map.height / 2]
        assertTrue("단색 스캐폴드 바탕은 표면의 background 색이어야 한다: $corner", close(corner, SurfaceStyles.CleanLight.background))
    }

    @Test
    fun onlyGlassScaffoldRecordsBackdrop() {
        var backdrop: GlassBackdrop? = null
        rule.setContent { KineticaScaffold(surface = surface) { backdrop = LocalGlassBackdrop.current } }
        rule.waitForIdle()
        assertNotNull("글래스 스캐폴드는 배경을 기록해야 한다", backdrop)
        rule.runOnIdle { surface = SurfaceStyles.CleanDark }
        rule.waitForIdle()
        assertNull("단색 스캐폴드는 배경 기록을 만들지 않아야 한다", backdrop)
    }

    @Test
    fun switchingSurfaceKeepsOpenSheet() {
        setHost()
        rule.onNodeWithTag("openSheet").performClick()
        rule.waitText("시트 내용")
        for (next in listOf(SurfaceStyles.CleanDark, SurfaceStyles.Tonal, SurfaceStyles.LiquidDark)) {
            rule.runOnIdle { surface = next }
            rule.waitForIdle()
            rule.onNodeWithText("시트 내용").assertExistsCompat()
        }
        TestSupport.back(rule)
        assertTrue("뒤로 가기로 시트가 닫혀야 한다", rule.becomes { rule.onAllNodesText("시트 내용") == 0 })
    }

    @Test
    fun dialogOpensAndClosesOnEverySurface() {
        setHost()
        for (next in listOf(SurfaceStyles.Liquid, SurfaceStyles.CleanLight, SurfaceStyles.Tonal)) {
            rule.runOnIdle { surface = next }
            rule.onNodeWithTag("openDialog").performClick()
            rule.waitText("다이얼로그 내용")
            TestSupport.back(rule)
            assertTrue("$next 에서 다이얼로그가 닫혀야 한다", rule.becomes { rule.onAllNodesText("다이얼로그 내용") == 0 })
        }
    }

    @Test
    fun solidScaffoldFollowsSurfaceBrightnessForBarIcons() {
        surface = SurfaceStyles.CleanDark
        setHost()
        assertTrue("어두운 단색 표면에서는 밝은 아이콘이어야 한다", rule.becomes { !lightStatusIcons() })
        rule.runOnIdle { surface = SurfaceStyles.CleanLight }
        assertTrue("밝은 단색 표면에서는 어두운 아이콘이어야 한다", rule.becomes { lightStatusIcons() })
    }

    @Test
    fun glassScaffoldLeavesHostBarIcons() {
        setHost()
        setHostIcons(lightBackground = false)
        rule.runOnIdle { surface = SurfaceStyles.Liquid }
        rule.waitForIdle()
        assertFalse("글래스 스캐폴드는 호스트가 정한 아이콘을 건드리지 않아야 한다", lightStatusIcons())
    }

    @Test
    fun everyVariantAndToneIsClickable() {
        var clicks by mutableIntStateOf(0)
        rule.setContent {
            KineticaScaffold(surface = SurfaceStyles.Tonal) {
                Column {
                    KVariant.entries.forEach { variant -> KButton(variant.name, { clicks++ }, Modifier.testTag("v-$variant"), variant = variant) }
                    KTone.entries.forEach { tone -> KButton(tone.name, { clicks++ }, Modifier.testTag("t-$tone"), tone = tone) }
                }
            }
        }
        KVariant.entries.forEach { rule.onNodeWithTag("v-$it").performClick() }
        KTone.entries.forEach { rule.onNodeWithTag("t-$it").performClick() }
        rule.runOnIdle { assertEquals(KVariant.entries.size + KTone.entries.size, clicks) }
    }

    @Test
    fun textFieldTypesOnTonalSurface() {
        surface = SurfaceStyles.TonalDark
        setHost()
        val input = rule.onNode(androidx.compose.ui.test.hasSetTextAction())
        input.performClick()
        input.performTextInput("톤 표면")
        rule.waitText("톤 표면")
    }

    @Test
    fun themeIndicationNullKeepsMaterialRippleInScaffold() {
        var seen: Indication? = null
        rule.setContent {
            MaterialTheme {
                KineticaTheme(surface = SurfaceStyles.CleanLight, indication = null) {
                    KineticaScaffold { seen = LocalIndication.current }
                }
            }
        }
        rule.waitForIdle()
        assertFalse("테마에서 끈 눌림 표시를 스캐폴드가 다시 켜면 안 된다: $seen", seen is KIndication)
    }

    @Test
    fun themeSurfaceReachesDialogWindowOutsideScaffold() {
        var ink = Color.Unspecified
        rule.setContent {
            KineticaTheme(surface = SurfaceStyles.CleanDark) {
                Box(Modifier.fillMaxSize()) {
                    KDialog(true, {}, title = "밖") {
                        ink = LocalKineticaContentColor.current
                        Text("창 다이얼로그")
                    }
                }
            }
        }
        rule.waitText("창 다이얼로그")
        rule.runOnIdle { assertEquals(SurfaceStyles.CleanDark.contentColor, ink) }
    }

    @Test
    fun colorWheelStaysCompactOnWideWindow() {
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(1280.dp, 800.dp))) {
                KineticaTheme(surface = SurfaceStyles.CleanLight) { KColorPicker(Color.Red, {}, Modifier.fillMaxWidth()) }
            }
        }
        rule.waitForIdle()
        val title = rule.activity.getString(com.naury.kinetica.ui.R.string.kinetica_ui_color_picker)
        val wheel = rule.onAllNodes(hasContentDescription(title), useUnmergedTree = true).fetchSemanticsNodes().first()
        val widthDp = wheel.boundsInRoot.width / rule.activity.resources.displayMetrics.density
        assertTrue("색 바퀴는 320dp를 넘지 않아야 한다: $widthDp", widthDp <= 320.5f)
    }

    @Test
    fun xmlSurfaceViewTakesSolidStyle() {
        rule.runOnUiThread {
            val view = KSurfaceView(rule.activity)
            view.surfaceStyle = SurfaceStyles.CleanDark
            assertEquals(SurfaceStyles.CleanDark.contentColor.toArgb(), view.contentColor)
            view.surfaceStyle = SurfaceStyles.Tonal
            assertEquals(SurfaceStyles.Tonal.contentColor.toArgb(), view.contentColor)
        }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() {
    fetchSemanticsNode()
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesText(text: String): Int =
    onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
