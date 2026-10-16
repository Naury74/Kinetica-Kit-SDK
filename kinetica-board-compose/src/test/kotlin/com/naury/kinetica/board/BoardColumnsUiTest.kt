package com.naury.kinetica.board

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-mdpi")
class BoardColumnsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val grid = BoardArrangement.Grid(columns = 2, columnsByWidth = GridColumns.Adaptive(160f, maxColumns = 4))
    private val catalog = listOf("a", "b", "c", "d").map { BoardWidget(it, it.uppercase()) }
    private val width = mutableStateOf(360.dp)

    private fun show(state: KineticaBoardState, editing: Boolean = false) {
        compose.setContent {
            // 다른 테스트에 화면 크기 설정이 남지 않도록 400dp 창에서 재고, 보드만 창보다 넓게 둔다.
            Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).size(width.value, 700.dp)) {
                KineticaBoard(state, Modifier.fillMaxSize(), editing = editing) { id ->
                    Box(
                        Modifier.fillMaxSize().testTag("content-$id").background(Color(0xFF3F6FD8), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(id.uppercase(), color = Color.White) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun resize(to: Dp) {
        width.value = to
        compose.waitForIdle()
    }

    private fun frame(id: String) = compose.onNodeWithTag("$BOARD_WIDGET_TAG$id").getBoundsInRoot()

    private fun left(id: String) = frame(id).left

    private fun cellWidth(id: String) = frame(id).let { it.right - it.left }

    @Test
    fun `폭이 넓어지면 열 수를 늘려 위젯이 지나치게 커지지 않고 좁아지면 원래 배치로 돌아온다`() {
        val state = KineticaBoardState.create(catalog, grid)
        // 편집 틀은 위젯과 같은 자리에 놓이므로 틀의 위치로 배치를 본다.
        show(state, editing = true)
        assertThat(state.activeColumns).isEqualTo(2)
        // 360dp에 2열: (360 - 12 × 3) / 2 = 162dp.
        assertThat(cellWidth("a").value).isWithin(1f).of(162f)
        assertThat(left("c").value).isWithin(1f).of(12f)

        resize(840.dp)
        assertThat(state.activeColumns).isEqualTo(4)
        // 840dp에 4열: (840 - 12 × 5) / 4 = 195dp. 좁은 배치의 셋째 위젯이 첫 줄 셋째 칸으로 흘러 들어간다.
        assertThat(cellWidth("a").value).isWithin(1f).of(195f)
        assertThat(left("c").value).isWithin(1f).of(12f + 2 * 207f)

        resize(360.dp)
        assertThat(state.activeColumns).isEqualTo(2)
        assertThat(left("c").value).isWithin(1f).of(12f)
        assertThat(state.history.state.value.canUndo).isFalse()
    }

    @Test
    fun `넓은 화면에서 끌어 옮긴 자리는 접었다 펴도 넓은 화면에만 남는다`() {
        val state = KineticaBoardState.create(catalog, grid)
        width.value = 840.dp
        show(state, editing = true)

        // a를 첫 줄 맨 끝 칸 아래(둘째 줄 넷째 칸)로 끈다. 칸 간격은 207dp다.
        compose.onNodeWithTag("${BOARD_WIDGET_TAG}a").performTouchInput {
            down(center)
            moveBy(Offset(30f, 0f))
            moveBy(Offset(3 * 207f - 30f, 207f))
            up()
        }
        compose.waitForIdle()
        assertThat(left("a").value).isWithin(1f).of(12f + 3 * 207f)

        resize(360.dp)
        assertThat(left("a").value).isWithin(1f).of(12f)
        assertThat(frame("a").top.value).isWithin(1f).of(12f)

        resize(840.dp)
        assertThat(left("a").value).isWithin(1f).of(12f + 3 * 207f)
        assertThat(frame("a").top.value).isWithin(1f).of(12f + 207f)
    }

    @Test
    fun `끄는 도중 열 수가 바뀌면 끌기를 취소하고 다시 끌 수 있다`() {
        val state = KineticaBoardState.create(catalog, grid)
        show(state, editing = true)
        val before = state.document.value
        val widget = compose.onNodeWithTag("${BOARD_WIDGET_TAG}a")
        widget.performTouchInput {
            down(center)
            moveBy(Offset(30f, 0f))
            moveBy(Offset(150f, 0f))
        }
        resize(840.dp)
        widget.performTouchInput {
            moveBy(Offset(30f, 200f))
            up()
        }
        compose.waitForIdle()
        assertThat(state.document.value).isEqualTo(before)

        // 취소 뒤에는 새 열 수에서 평소처럼 끌어 옮긴다.
        widget.performTouchInput {
            down(center)
            moveBy(Offset(30f, 0f))
            moveBy(Offset(207f - 30f, 207f))
            up()
        }
        compose.waitForIdle()
        assertThat(state.history.state.value.canUndo).isTrue()
        assertThat(left("a").value).isWithin(1f).of(12f + 207f)
        resize(360.dp)
        assertThat(left("a").value).isWithin(1f).of(12f)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w900dp-h800dp-mdpi")
class BoardColumnsViewTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `XML 속성으로 최소 칸 폭과 최대 열 수를 주면 넓은 화면에서 열을 늘린다`() {
        val state = KineticaBoardState.create(listOf(BoardWidget("a", "A")), BoardArrangement.Grid(columns = 2))
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val attrs = Robolectric.buildAttributeSet()
                    .addAttribute(R.attr.kinetica_minCellWidth, "160dp")
                    .addAttribute(R.attr.kinetica_maxColumns, "4")
                    .build()
                val board = KineticaBoardView(activity, attrs)
                assertThat(board.columnsByWidth).isEqualTo(GridColumns.Adaptive(160f, 4))
                activity.setContentView(board)
                board.setBoard(state) { id, context -> android.widget.TextView(context).apply { text = id } }
            }
            compose.waitForIdle()
            // 900dp 화면이면 160dp 칸이 5개 들어가지만 최대 4열이다.
            assertThat(state.activeColumns).isEqualTo(4)
        }
    }
}
