package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KKanbanBoardTest {
    @get:Rule
    val compose = createComposeRule()

    private val board = listOf(
        KKanbanColumn("todo", "To do", listOf("A", "B", "C")),
        KKanbanColumn("doing", "Doing", listOf("D")),
        KKanbanColumn("done", "Done", emptyList<String>()),
    )

    private class Move(val key: Any, val column: Any, val index: Int)

    private fun setBoard(moves: MutableList<Move>, modifier: Modifier = Modifier.height(500.dp)) {
        compose.setContent {
            KKanbanBoard(board, { it }, { key, column, index -> moves += Move(key, column, index) }, modifier.testTag("board")) { card, _ ->
                Box(Modifier.fillMaxWidth().height(60.dp)) { ThemedText(card) }
            }
        }
    }

    @Test
    fun `놓을 자리는 가운데가 손가락보다 아래인 첫 카드 앞이다`() {
        val slots = listOf(KanbanSlot(0, 0, 60), KanbanSlot(2, 140, 60))
        assertThat(kanbanDropIndex(slots, 3, 10f)).isEqualTo(0)
        assertThat(kanbanDropIndex(slots, 3, 130f)).isEqualTo(2)
        assertThat(kanbanDropIndex(slots, 3, 500f)).isEqualTo(3)
        assertThat(kanbanDropIndex(emptyList(), 0, 50f)).isEqualTo(0)
        assertThat(finalIndex(before = 2, from = 0, sameColumn = true)).isEqualTo(1)
        assertThat(finalIndex(before = 2, from = 0, sameColumn = false)).isEqualTo(2)
    }

    @Test
    fun `넓은 화면에서는 남는 폭을 나눠 열을 나란히 다 보인다`() {
        assertThat(kanbanColumnWidth(1200.dp, 3, 280.dp).value).isGreaterThan(280f)
        assertThat(kanbanColumnWidth(360.dp, 3, 280.dp).value).isAtMost(360f * 0.86f + 0.01f)
        assertThat(kanbanColumnWidth(360.dp, 0, 280.dp)).isEqualTo(280.dp)
    }

    @Test
    fun `길게 눌러 끌어 놓으면 옮긴 뒤 번호로 한 번 알린다`() {
        val moves = mutableListOf<Move>()
        setBoard(moves)
        val step = with(compose.density) { 50.dp.toPx() }
        compose.onNodeWithText("A").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS)
            moveBy(Offset(0f, step))
            moveBy(Offset(0f, step))
            up()
        }
        compose.waitForIdle()
        assertThat(moves).hasSize(1)
        assertThat(moves[0].key).isEqualTo("A")
        assertThat(moves[0].column).isEqualTo("todo")
        assertThat(moves[0].index).isEqualTo(1)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `터치가 취소되면 옮기지 않고 원래 자리로 돌아간다`() {
        val moves = mutableListOf<Move>()
        setBoard(moves)
        compose.onNodeWithText("A").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS)
            moveBy(Offset(0f, 300f))
            cancel()
        }
        compose.waitForIdle()
        assertThat(moves).isEmpty()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `TalkBack 동작으로 옆 열과 위아래로 옮긴다`() {
        val moves = mutableListOf<Move>()
        setBoard(moves)
        compose.onNodeWithText("B").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "To do, 2 of 3"))
        compose.onNodeWithText("B").performCustomAccessibilityActionWithLabel("Move to right column")
        compose.onNodeWithText("B").performCustomAccessibilityActionWithLabel("Move up")
        assertThat(
            moves.map {
                Triple(it.key, it.column, it.index)
            },
        ).containsExactly(Triple("B", "doing", 1), Triple("B", "todo", 0)).inOrder()
        // 첫 열의 카드에는 왼쪽 열로 옮기는 동작이 없다.
        val labels = compose.onNodeWithText("A").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
            .map { it.label }
        assertThat(labels).containsExactly("Move down", "Move to right column")
    }

    @Test
    fun `높이가 무한한 부모와 빈 열에서도 그린다`() {
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                KKanbanBoard(board, { it }, { _, _, _ -> }, Modifier.testTag("board")) { card, _ -> ThemedText(card) }
            }
        }
        compose.onNodeWithTag("board").assertExists()
        compose.onNodeWithText("No cards").assertExists()
    }

    @Test
    @Config(qualifiers = "w1200dp-h800dp")
    fun `같은 SnapshotStateList에 더한 열로도 끌어 놓는다`() {
        val moves = mutableListOf<Move>()
        val columns = mutableStateListOf(board[0], board[1])
        compose.setContent {
            KKanbanBoard(columns, { it }, { key, column, index -> moves += Move(key, column, index) }, Modifier.height(500.dp)) { card, _ ->
                Box(Modifier.fillMaxWidth().height(60.dp)) { ThemedText(card) }
            }
        }
        columns.add(KKanbanColumn("done", "Done", emptyList()))
        compose.waitForIdle()
        val target = compose.onNodeWithText("No cards").fetchSemanticsNode().boundsInRoot.center
        val card = compose.onNodeWithText("A").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithText("A").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS)
            val step = (target - card) / 4f
            repeat(4) { moveBy(step) }
            up()
        }
        compose.waitForIdle()
        assertThat(moves.map { Triple(it.key, it.column, it.index) }).containsExactly(Triple("A", "done", 0))
    }

    private class PlainCard(val id: String)

    @Test
    fun `Bundle에 담을 수 없는 카드 키를 써도 그리고 끌어 옮긴다`() {
        val moves = mutableListOf<Move>()
        val a = PlainCard("A")
        val columns = listOf(KKanbanColumn("todo", "To do", listOf(a, PlainCard("B"), PlainCard("C"))))
        compose.setContent {
            KKanbanBoard(columns, { it }, { key, column, index -> moves += Move(key, column, index) }, Modifier.height(500.dp)) { card, _ ->
                Box(Modifier.fillMaxWidth().height(60.dp)) { ThemedText(card.id) }
            }
        }
        val step = with(compose.density) { 50.dp.toPx() }
        compose.onNodeWithText("A").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS)
            moveBy(Offset(0f, step))
            moveBy(Offset(0f, step))
            up()
        }
        compose.waitForIdle()
        assertThat(moves.map { Triple(it.key, it.column, it.index) }).containsExactly(Triple(a, "todo", 1))
    }

    @Test
    fun `끄는 도중 같은 열 위에 카드가 더해져도 지금 자리로 놓을 번호를 계산한다`() {
        val moves = mutableListOf<Move>()
        val columns = mutableStateListOf(board[0])
        compose.setContent {
            KKanbanBoard(columns, { it }, { key, column, index -> moves += Move(key, column, index) }, Modifier.height(500.dp)) { card, _ ->
                Box(Modifier.fillMaxWidth().height(60.dp)) { ThemedText(card) }
            }
        }
        val dp = with(compose.density) { 1.dp.toPx() }
        // 끄는 동안은 자동 스크롤이 프레임마다 돌아 유휴 상태가 오지 않으므로 시계를 손으로 돌린다.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("B").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(LONG_PRESS)
        compose.onNodeWithText("B").performTouchInput { moveBy(Offset(0f, -5 * dp)) }
        compose.mainClock.advanceTimeBy(FRAMES)
        columns[0] = board[0].copy(cards = listOf("Z") + board[0].cards)
        compose.mainClock.advanceTimeBy(FRAMES)
        // B는 이제 2번이고, Z와 A 사이(1번 앞)에 놓는다.
        compose.onNodeWithText("Z").performTouchInput {
            moveBy(Offset(0f, -25 * dp))
            up()
        }
        compose.mainClock.advanceTimeBy(SETTLE)
        assertThat(moves.map { Triple(it.key, it.column, it.index) }).containsExactly(Triple("B", "todo", 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `열 폭이 0이면 거부한다`() {
        compose.setContent { KKanbanBoard(board, { it }, { _, _, _ -> }, columnWidth = 0.dp) { card, _ -> ThemedText(card) } }
    }

    private companion object {
        const val LONG_PRESS = 800L
        const val FRAMES = 100L
        const val SETTLE = 2_000L
    }
}
