package com.example.consumer

import android.graphics.Bitmap
import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.naury.kinetica.ui.GlassIndication
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * 시나리오 8: Material3 앱이 GlassTheme을 함께 쓸 때 눌림 표시가 어느 쪽으로 정해지는지.
 * 값 시험은 LocalIndication을, 화면 시험은 누르는 동안 찍은 화면에서 누른 곳과 먼 모서리의 밝기를 비교한다.
 * 리플은 누른 곳에서 퍼지므로 처음 몇 프레임은 먼 모서리가 그대로이고, GlassIndication은 요소 전체가 한꺼번에 옅게 덮인다.
 */
@RunWith(AndroidJUnit4::class)
class IndicationTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var seen: Indication? = null
    private var outer: Indication? = null

    @Composable
    private fun Probe() {
        val current = LocalIndication.current
        SideEffect { seen = current }
    }

    @Composable
    private fun OuterProbe() {
        val current = LocalIndication.current
        SideEffect { outer = current }
    }

    @Test
    fun materialOutsideGlassInsideUsesGlassIndication() {
        rule.setContent { MaterialTheme { OuterProbe(); GlassTheme { Probe() } } }
        rule.waitForIdle()
        assertTrue("MaterialTheme 안 GlassTheme은 GlassIndication이어야 한다: $seen", seen is GlassIndication)
        assertFalse("바깥 MaterialTheme은 Material 리플이어야 한다: $outer", outer is GlassIndication)
    }

    @Test
    fun materialInsideGlassWinsWithRipple() {
        // MaterialTheme은 LocalIndication에 자기 리플을 다시 넣으므로, 안쪽에 둔 MaterialTheme이 이긴다.
        rule.setContent { GlassTheme { MaterialTheme { Probe() } } }
        rule.waitForIdle()
        assertFalse("GlassTheme 안에 MaterialTheme을 다시 두면 Material 리플로 돌아가야 한다: $seen", seen is GlassIndication)
    }

    @Test
    fun nullIndicationKeepsHostValue() {
        rule.setContent { MaterialTheme { OuterProbe(); GlassTheme(indication = null) { Probe() } } }
        rule.waitForIdle()
        assertSame("indication = null이면 호스트 값을 그대로 둬야 한다", outer, seen)
    }

    @Test
    fun scaffoldInsideOptedOutThemeStillUsesGlass() {
        // GlassScaffold는 자기 indication 인자(기본 GlassIndication)를 따로 받으므로 GlassTheme(indication = null)을 따르지 않는다.
        var scaffoldOptOut by mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                OuterProbe()
                GlassTheme(indication = null) {
                    if (scaffoldOptOut) GlassScaffold(indication = null) { Probe() } else GlassScaffold { Probe() }
                }
            }
        }
        rule.waitForIdle()
        assertTrue("GlassScaffold 기본값은 GlassIndication이다: $seen", seen is GlassIndication)
        rule.runOnIdle { scaffoldOptOut = true }
        rule.waitForIdle()
        assertSame("GlassScaffold(indication = null)이면 호스트 값을 둔다", outer, seen)
    }

    /** 흰 배경의 큰 누를 수 있는 요소 셋. 일반 clickable 상자, clickable을 준 ListItem, onClick을 준 Card. */
    @Composable
    private fun HostTargets() {
        // 시스템 바 아래를 피해야 누름이 요소에 간다.
        Column(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
            Box(Modifier.size(320.dp, 200.dp).background(Color.White).clickable { }.testTag("plain"))
            Spacer(Modifier.height(16.dp))
            ListItem(
                headlineContent = { },
                modifier = Modifier.width(320.dp).height(200.dp).clickable { }.testTag("listItem"),
                colors = ListItemDefaults.colors(containerColor = Color.White),
            )
            Spacer(Modifier.height(16.dp))
            Card(
                onClick = { },
                modifier = Modifier.size(320.dp, 200.dp).testTag("card"),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) { Text("") }
        }
    }

    private enum class Look { Ripple, Uniform, None }

    /**
     * [tag] 요소의 왼쪽 위 가까이를 누른 채 250ms 동안 화면을 찍어, 누른 곳과 오른쪽 아래 모서리의 밝기를 비교한다.
     * 누른 곳만 먼저 어두워진 프레임이 있으면 리플, 두 곳이 늘 같이 어두워졌으면 GlassIndication 같은 고른 오버레이다.
     */
    private fun pressLook(tag: String): Look {
        val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
        val origin = node.positionOnScreen
        val size = node.size
        val near = PointF(origin.x + size.width * 0.1f, origin.y + size.height * 0.15f)
        val far = PointF(origin.x + size.width * 0.92f, origin.y + size.height * 0.88f)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val down = SystemClock.uptimeMillis()
        fun send(action: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, near.x, near.y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            automation.injectInputEvent(event, true)
            event.recycle()
        }
        var rippleFrames = 0
        var darkened = false
        var uneven = 0
        send(MotionEvent.ACTION_DOWN)
        try {
            val end = SystemClock.uptimeMillis() + 300
            while (SystemClock.uptimeMillis() < end) {
                // 시험 규칙의 Compose 시계는 직접 넘겨야 GlassIndication 애니메이션이 진행된다. 리플은 RenderThread에서 돌아 상관없다.
                rule.mainClock.advanceTimeBy(16)
                val shot: Bitmap = automation.takeScreenshot() ?: continue
                val a = luma(shot, near)
                val b = luma(shot, far)
                shot.recycle()
                if (a < 245 || b < 245) darkened = true
                if (a < b - 8 && b > 248) rippleFrames++
                if (abs(a - b) > 6) uneven++
            }
        } finally {
            send(MotionEvent.ACTION_UP)
        }
        rule.waitForIdle()
        Thread.sleep(400)
        return when {
            rippleFrames > 0 -> Look.Ripple
            darkened && uneven == 0 -> Look.Uniform
            darkened -> Look.Ripple
            else -> Look.None
        }
    }

    private fun luma(bitmap: Bitmap, point: PointF): Int {
        val c = bitmap.getPixel(point.x.toInt().coerceIn(0, bitmap.width - 1), point.y.toInt().coerceIn(0, bitmap.height - 1))
        return (android.graphics.Color.red(c) * 299 + android.graphics.Color.green(c) * 587 + android.graphics.Color.blue(c) * 114) / 1000
    }

    @Test
    fun withoutGlassThemeHostSeesRipple() {
        // 측정 방법이 리플을 알아보는지 먼저 확인한다(대조군).
        rule.setContent { MaterialTheme(lightColorScheme()) { HostTargets() } }
        rule.waitForIdle()
        assertTrue("GlassTheme이 없으면 일반 clickable은 리플이어야 한다", pressLook("plain") == Look.Ripple)
        assertTrue("GlassTheme이 없으면 Card(onClick)는 리플이어야 한다", pressLook("card") == Look.Ripple)
    }

    @Test
    fun glassThemeReplacesRippleOnHostClickables() {
        rule.setContent { MaterialTheme(lightColorScheme()) { GlassTheme(darkTheme = false) { HostTargets() } } }
        rule.waitForIdle()
        val plain = pressLook("plain")
        assertTrue("GlassTheme 안 일반 clickable은 고르게 덮여야 한다: $plain", plain == Look.Uniform)
        val listItem = pressLook("listItem")
        assertTrue("GlassTheme 안 ListItem(clickable)은 고르게 덮여야 한다: $listItem", listItem == Look.Uniform)
        // Material3 Card(onClick)는 LocalIndication이 아니라 자기 ripple()을 직접 쓰므로 GlassTheme과 상관없이 리플이다.
        val card = pressLook("card")
        assertTrue("Card(onClick)는 Material 리플을 그대로 쓴다: $card", card == Look.Ripple)
    }

    @Test
    fun glassThemeWithNullIndicationKeepsRipple() {
        rule.setContent { MaterialTheme(lightColorScheme()) { GlassTheme(darkTheme = false, indication = null) { HostTargets() } } }
        rule.waitForIdle()
        assertTrue("indication = null이면 호스트 리플이 남아야 한다", pressLook("plain") == Look.Ripple)
    }
}
