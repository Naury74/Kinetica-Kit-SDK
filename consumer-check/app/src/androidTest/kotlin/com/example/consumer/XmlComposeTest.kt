package com.example.consumer

import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.widget.TextView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.xml.XmlComposeActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 시나리오 9: XML NestedScrollView 안 ComposeView에 넣은 GlassColorPicker와 GlassSignaturePad의 터치와 스크롤. */
@RunWith(AndroidJUnit4::class)
class XmlComposeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<XmlComposeActivity>

    @Before
    fun launch() {
        scenario = ActivityScenario.launch(XmlComposeActivity::class.java)
        settle()
    }

    @After
    fun close() {
        scenario.close()
    }

    private fun settle() {
        compose.waitForIdle()
        Thread.sleep(TestSupport.SETTLE_MS)
        compose.waitForIdle()
    }

    private fun scrollY(): Int {
        var y = 0
        scenario.onActivity { y = it.findViewById<NestedScrollView>(R.id.scroll).scrollY }
        return y
    }

    private fun text(id: Int): String {
        var t = ""
        scenario.onActivity { t = it.findViewById<TextView>(id).text.toString() }
        return t
    }

    /** [id] 뷰가 화면 위쪽 근처에 오도록 바깥 NestedScrollView를 옮긴다. */
    private fun scrollViewToTop(id: Int) {
        scenario.onActivity {
            val target = it.findViewById<View>(id)
            val scroll = it.findViewById<NestedScrollView>(R.id.scroll)
            scroll.scrollTo(0, (target.top - 40 * it.resources.displayMetrics.density).toInt().coerceAtLeast(0))
        }
        settle()
    }

    private fun screenRect(id: Int): Rect {
        val rect = Rect()
        scenario.onActivity {
            val view = it.findViewById<View>(id)
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            rect.set(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)
        }
        return rect
    }

    @Test
    fun signatureDrawingDoesNotScrollParent() {
        scrollViewToTop(R.id.signatureState)
        val pad = compose.onNode(hasTestTag("xmlSignature")).fetchSemanticsNode()
        val left = pad.positionOnScreen.x
        val top = pad.positionOnScreen.y
        val w = pad.size.width.toFloat()
        val h = pad.size.height.toFloat()
        val y0 = scrollY()
        // 세로 움직임이 큰 획. 서명란이 부모 가로채기를 막지 못하면 NestedScrollView가 스크롤한다.
        injectDrag(
            listOf(
                PointF(left + w * 0.15f, top + h * 0.2f),
                PointF(left + w * 0.25f, top + h * 0.85f),
                PointF(left + w * 0.45f, top + h * 0.15f),
                PointF(left + w * 0.6f, top + h * 0.8f),
            ),
        )
        settle()
        assertEquals("서명하는 동안 바깥 NestedScrollView가 스크롤됐다", y0, scrollY())
        assertEquals("서명: 있음", text(R.id.signatureState))

        onView(withId(R.id.signatureUndo)).perform(scrollTo(), click())
        settle()
        onView(withId(R.id.signatureState)).check(matches(withText("서명: 없음")))

        // 서명란 밖에서는 바깥 스크롤이 그대로 동작해야 한다.
        val y1 = scrollY()
        val undo = screenRect(R.id.signatureUndo)
        val height = TestSupport.device.displayHeight
        injectDrag(listOf(PointF(undo.exactCenterX(), height * 0.75f), PointF(undo.exactCenterX(), height * 0.35f)))
        settle()
        assertTrue("서명란 밖 끌기가 바깥을 스크롤하지 않았다: $y1 → ${scrollY()}", scrollY() > y1)
    }

    /** 색상 바퀴(가운데 사각형)를 세로로 끌어도 바깥이 스크롤되지 않고 색이 바뀌어야 한다. */
    @Test
    fun colorPickerDragDoesNotScrollParent() {
        scrollViewToTop(R.id.colorPicker)
        val wheel = largestNodeWithActions()
        val cx = wheel.positionOnScreen.x + wheel.size.width / 2f
        val cy = wheel.positionOnScreen.y + wheel.size.height / 2f
        val r = wheel.size.width * 0.12f
        val y0 = scrollY()
        val color0 = text(R.id.colorValue)
        injectDrag(listOf(PointF(cx, cy - r), PointF(cx + r * 0.3f, cy + r), PointF(cx - r * 0.3f, cy - r * 0.5f)))
        settle()
        assertEquals("색상 사각형을 끄는 동안 바깥 NestedScrollView가 스크롤됐다", y0, scrollY())
        assertNotEquals("색상 사각형을 끌었는데 색이 바뀌지 않았다", color0, text(R.id.colorValue))

        // 바깥 고리(색조)도 같은지 본다. 고리 위쪽에서 오른쪽 아래로 크게 돈다.
        val y1 = scrollY()
        val color1 = text(R.id.colorValue)
        val ring = wheel.size.width * 0.44f
        injectDrag(listOf(PointF(cx, cy - ring), PointF(cx + ring * 0.7f, cy - ring * 0.7f), PointF(cx + ring, cy), PointF(cx + ring * 0.7f, cy + ring * 0.7f)))
        settle()
        assertEquals("색조 고리를 끄는 동안 바깥 NestedScrollView가 스크롤됐다", y1, scrollY())
        assertNotEquals("색조 고리를 끌었는데 색이 바뀌지 않았다", color1, text(R.id.colorValue))
    }

    /** 색상 선택기 안에서 사용자 지정 동작(색조·밝기 단계)을 가진 가장 큰 노드가 색상 바퀴다. */
    private fun largestNodeWithActions(): SemanticsNode = compose
        .onAllNodes(hasAnyAncestor(hasTestTag("xmlColorPicker")) and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        .fetchSemanticsNodes()
        .filter { it.config.getOrNull(SemanticsProperties.StateDescription) != null }
        .maxBy { it.size.width * it.size.height }
}
