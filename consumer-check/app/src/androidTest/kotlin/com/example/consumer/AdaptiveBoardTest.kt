package com.example.consumer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.consumer.recent.AdaptiveBoardActivity
import com.example.consumer.recent.MemoryBoardStorage
import com.example.consumer.recent.XmlAdaptiveBoardActivity
import com.naury.kinetica.board.KineticaBoardState
import com.naury.kinetica.core.geometry.Rect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 시나리오 11: 칸 최소 폭(180dp)으로 열 수를 정하는 홈 화면 보드. 회전해 폭이 바뀌면 열 수가 바뀌고, 넓은 배치에서 옮긴 위젯은
 * 좁은 배치에 영향을 주지 않으며, 좁았다가 다시 넓어져도 옮긴 자리에 그대로 있어야 한다. Compose 보드와 XML 보드를 같은 순서로 본다.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveBoardTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<out Activity>? = null

    @Before
    fun clear() = MemoryBoardStorage.clear()

    @After
    fun restore() {
        scenario?.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        scenario?.close()
    }

    /** 보드 폭(dp)에서 기대하는 열 수. 칸 n개와 간격 n+1개(12dp)가 들어가는 가장 큰 n이고 2..8로 자른다. */
    private fun expectedColumns(boardWidthDp: Float): Int = ((boardWidthDp - 12f) / (180f + 12f)).toInt().coerceIn(2, 8)

    private fun <A : Activity> run(type: Class<A>, board: (A) -> KineticaBoardState, boardWidthPx: (ActivityScenario<A>) -> Int) {
        val launched = ActivityScenario.launch(type)
        scenario = launched
        rule.waitForIdle()
        Thread.sleep(TestSupport.SETTLE_MS)

        fun state(): KineticaBoardState {
            var s: KineticaBoardState? = null
            launched.onActivity { s = board(it) }
            return s!!
        }

        fun columns(): Pair<Int, Int> {
            rule.waitForIdle()
            var density = 1f
            launched.onActivity { density = it.resources.displayMetrics.density }
            val widthDp = boardWidthPx(launched) / density
            assertTrue(rule.becomes { state().activeColumns == expectedColumns(widthDp) })
            return state().activeColumns to expectedColumns(widthDp)
        }

        fun rects(): Map<String, Rect> {
            var map: Map<String, Rect> = emptyMap()
            val st = state()
            rule.runOnIdle { map = st.placedKeys.associateWith { st.rectOf(it)!! } }
            return map
        }

        fun orientation(): Int {
            var o = 0
            launched.onActivity { o = it.resources.configuration.orientation }
            return o
        }

        // 넓은 쪽(가로)부터 시작해 좁은 쪽(세로)과 오간다.
        rotateAndSettle(rule, launched, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        val (wideColumns, _) = columns()
        val wideBefore = rects()

        rotateAndSettle(rule, launched, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        assertEquals(Configuration.ORIENTATION_PORTRAIT, orientation())
        val (narrowColumns, _) = columns()
        assertTrue("세로가 가로보다 열이 적어야 한다: $narrowColumns / $wideColumns", narrowColumns < wideColumns)
        val narrowBefore = rects()

        rotateAndSettle(rule, launched, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        assertEquals(wideColumns, columns().first)
        assertEquals("다시 넓어졌을 때 넓은 배치가 바뀌었다", wideBefore, rects())
        // 넓은 배치에서 날씨 위젯을 오른쪽 아래로 옮긴다(키보드 편집과 같은 경로).
        val st = state()
        rule.runOnIdle {
            assertTrue(st.moveBy("weather", 1f, 0f))
            assertTrue(st.moveBy("weather", 0f, 1f))
        }
        val wideMoved = rects()
        assertNotEquals("옮긴 위젯이 움직이지 않았다", wideBefore["weather"], wideMoved["weather"])

        rotateAndSettle(rule, launched, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        assertEquals(narrowColumns, columns().first)
        assertEquals("넓은 배치에서 옮긴 것이 좁은 배치를 바꿨다", narrowBefore, rects())

        rotateAndSettle(rule, launched, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        assertEquals(wideColumns, columns().first)
        assertEquals("좁았다가 넓어진 뒤 옮긴 자리가 달라졌다", wideMoved, rects())
    }

    @Test
    fun composeBoardColumnsFollowWidthAndKeepWideLayout() {
        run(AdaptiveBoardActivity::class.java, { it.board }) { rule.onNodeWithTag("adaptiveBoard").fetchSemanticsNode().size.width }
    }

    @Test
    fun xmlBoardViewColumnsFollowWidthAndKeepWideLayout() {
        run(XmlAdaptiveBoardActivity::class.java, { it.board }) { launched ->
            var width = 0
            launched.onActivity { width = it.findViewById<android.view.View>(R.id.adaptiveBoard).width }
            width
        }
    }
}
