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
 * 시나리오 11: 칸 최소 폭(180dp)으로 열 수를 정하는 홈 화면 보드. 회전해 폭이 바뀌면 열 수가 실제 폭에 맞게 바뀌고, 열 수가 다르면
 * 가로 배치에서 옮긴 위젯은 세로 배치에 영향을 주지 않으며, 세로였다가 다시 가로가 되어도 옮긴 자리에 그대로 있어야 한다.
 * 회전해도 열 수가 같은 화면(펼친 폴더블 등)에서는 두 방향이 한 배치를 함께 쓴다. Compose 보드와 XML 보드를 같은 순서로 본다.
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

        // 지금 보드 폭에서 기대하는 열 수가 되기를 기다렸다가 그 열 수를 돌려준다.
        fun columns(): Int {
            rule.waitForIdle()
            var density = 1f
            launched.onActivity { density = it.resources.displayMetrics.density }
            val widthDp = boardWidthPx(launched) / density
            val expected = expectedColumns(widthDp)
            assertTrue("보드 폭 ${widthDp}dp에서 열 수가 $expected 이(가) 되지 않았다", rule.becomes { state().activeColumns == expected })
            return expected
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

        // 가로로 시작해 세로와 오간다. 펼친 폴더블처럼 정사각형에 가까운 화면은 회전해도 폭이 거의 같아 열 수가 같을 수 있으므로,
        // 기대 열 수는 방향이 아니라 그때의 실제 보드 폭으로 계산한다.
        rotateAndSettle(rule, launched, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        val landscapeColumns = columns()
        val landscapeBefore = rects()

        rotateAndSettle(rule, launched, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        assertEquals(Configuration.ORIENTATION_PORTRAIT, orientation())
        val portraitColumns = columns()
        val portraitBefore = rects()
        // 열 수가 같으면 두 방향이 같은 배치를 쓴다. 다를 때만 배치가 서로 독립인지 볼 수 있다.
        val separate = portraitColumns != landscapeColumns
        if (!separate) assertEquals("열 수가 같은데 배치가 다르다", landscapeBefore, portraitBefore)

        rotateAndSettle(rule, launched, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        assertEquals(landscapeColumns, columns())
        assertEquals("가로로 돌아왔을 때 가로 배치가 바뀌었다", landscapeBefore, rects())
        // 가로 배치에서 날씨 위젯을 오른쪽 아래로 옮긴다(키보드 편집과 같은 경로).
        val st = state()
        rule.runOnIdle {
            assertTrue(st.moveBy("weather", 1f, 0f))
            assertTrue(st.moveBy("weather", 0f, 1f))
        }
        val landscapeMoved = rects()
        assertNotEquals("옮긴 위젯이 움직이지 않았다", landscapeBefore["weather"], landscapeMoved["weather"])

        rotateAndSettle(rule, launched, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        assertEquals(portraitColumns, columns())
        if (separate) {
            assertEquals("가로 배치에서 옮긴 것이 세로 배치를 바꿨다", portraitBefore, rects())
        } else {
            assertEquals("열 수가 같은 세로 배치에 옮긴 자리가 반영되지 않았다", landscapeMoved, rects())
        }

        rotateAndSettle(rule, launched, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        assertEquals(landscapeColumns, columns())
        assertEquals("세로였다가 가로로 돌아온 뒤 옮긴 자리가 달라졌다", landscapeMoved, rects())
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
