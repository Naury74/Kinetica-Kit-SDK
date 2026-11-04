package com.naury.kinetica.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 소개 격자, 명령 판, 데이터 표 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class KLayoutsTest {
    @get:Rule
    val compose = createComposeRule()

    // 소개 격자

    @Test
    fun `격자는 큰 칸 뒤 구멍을 뒤의 작은 칸으로 채운다`() {
        val cells = packBento(listOf(KBentoSpan.Large, KBentoSpan.Wide, KBentoSpan.Small, KBentoSpan.Small), columns = 3, fillGaps = false)
        assertThat(cells[0]).isEqualTo(BentoPlacement(0, 0, 2, 2))
        // 2열짜리는 3열 격자 첫 줄 남은 1칸에 못 들어가 큰 칸 아래로 간다.
        assertThat(cells[1]).isEqualTo(BentoPlacement(0, 2, 2, 1))
        // 작은 칸은 큰 칸 오른쪽 구멍부터 메운다.
        assertThat(cells[2]).isEqualTo(BentoPlacement(2, 0, 1, 1))
        assertThat(cells[3]).isEqualTo(BentoPlacement(2, 1, 1, 1))
    }

    @Test
    fun `빈 자리 메우기를 켜면 줄 끝 구멍을 옆 칸을 넓혀 메우고 줄 수는 늘리지 않는다`() {
        val spans = listOf(KBentoSpan.Wide, KBentoSpan.Small, KBentoSpan.Tall, KBentoSpan.Small)
        val loose = packBento(spans, columns = 2, fillGaps = false)
        assertThat(loose[1]).isEqualTo(BentoPlacement(0, 1, 1, 1))
        val filled = packBento(spans, columns = 2, fillGaps = true)
        val rows = filled.maxOf { it.row + it.rows }
        assertThat(rows).isEqualTo(loose.maxOf { it.row + it.rows })
        val area = filled.sumOf { it.columns * it.rows }
        assertThat(area).isEqualTo(rows * 2)
    }

    @Test
    fun `격자보다 넓은 칸은 격자 폭으로 줄이고 열 수는 폭에 맞춰 늘어난다`() {
        assertThat(packBento(listOf(KBentoSpan(4, 1)), columns = 2, fillGaps = true).single().columns).isEqualTo(2)
        assertThat(bentoColumns(360f, 150f, 12f, 4)).isEqualTo(2)
        assertThat(bentoColumns(840f, 150f, 12f, 4)).isEqualTo(4)
        assertThat(bentoColumns(100f, 150f, 12f, 4)).isEqualTo(1)
    }

    private fun bentoBounds(direction: LayoutDirection) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.width(312.dp)) {
                    KBentoGrid(spacing = 12.dp, rowHeight = 100.dp) {
                        KBentoCell(Modifier.testTag("big"), span = KBentoSpan.Wide) { }
                        KBentoCell(Modifier.testTag("a")) { }
                        KBentoCell(Modifier.testTag("b")) { }
                    }
                }
            }
        }
    }

    @Test
    fun `격자 칸은 열 폭과 간격대로 놓이고 RTL에서는 좌우가 바뀐다`() {
        bentoBounds(LayoutDirection.Ltr)
        val big = compose.onNodeWithTag("big").getBoundsInRoot()
        val a = compose.onNodeWithTag("a").getBoundsInRoot()
        val b = compose.onNodeWithTag("b").getBoundsInRoot()
        assertThat(big.width.value).isWithin(1f).of(312f)
        assertThat(a.width.value).isWithin(1f).of(150f)
        assertThat(a.top.value).isWithin(1f).of(112f)
        assertThat(a.left.value).isWithin(1f).of(0f)
        assertThat(b.left.value).isWithin(1f).of(162f)
    }

    @Test
    fun `RTL 격자에서는 첫 작은 칸이 오른쪽에 놓인다`() {
        bentoBounds(LayoutDirection.Rtl)
        assertThat(compose.onNodeWithTag("a").getBoundsInRoot().left.value).isWithin(1f).of(162f)
        assertThat(compose.onNodeWithTag("b").getBoundsInRoot().left.value).isWithin(1f).of(0f)
    }

    @Test
    fun `큰 글꼴처럼 내용이 줄 높이보다 길면 그 줄이 늘어난다`() {
        compose.setContent {
            Box(Modifier.width(312.dp)) {
                KBentoGrid(rowHeight = 80.dp) {
                    KBentoCell(Modifier.testTag("tall")) { Box(Modifier.height(200.dp)) }
                    KBentoCell(Modifier.testTag("side")) { }
                }
            }
        }
        assertThat(compose.onNodeWithTag("side").getBoundsInRoot().height.value).isAtLeast(200f)
    }

    // 명령 판

    @Test
    fun `제목에 그대로 들어 있으면 앞에서 맞을수록 높게 치고 흩어진 글자와 키워드도 찾는다`() {
        val prefix = matchCommand("Open settings", emptyList(), "open")!!
        val middle = matchCommand("Reopen tab", emptyList(), "open")!!
        assertThat(prefix.score).isGreaterThan(middle.score)
        assertThat(prefix.ranges).containsExactly(0..3)
        val scattered = matchCommand("Open Settings", emptyList(), "opse")!!
        assertThat(scattered.ranges).containsExactly(0..1, 5..6).inOrder()
        assertThat(matchCommand("새 문서", listOf("new", "create"), "creat")!!.ranges).isEmpty()
        assertThat(matchCommand("새 문서", emptyList(), "xyz")).isNull()
    }

    @Test
    fun `검색어가 비면 최근 명령을 맨 위에 두고 검색어가 있으면 맞는 묶음만 남긴다`() {
        val sections = sampleSections {}
        val idle = paletteRows(sections, "", listOf("theme"), "최근")
        assertThat((idle[0] as PaletteRow.Header).title).isEqualTo("최근")
        assertThat((idle[1] as PaletteRow.Item).command.id).isEqualTo("theme")
        val found = paletteRows(sections, "문서", listOf("theme"), "최근")
        assertThat(found.filterIsInstance<PaletteRow.Header>().map { it.title }).containsExactly("파일")
        assertThat(found.filterIsInstance<PaletteRow.Item>().map { it.command.id }).containsExactly("open", "new").inOrder()
        assertThat(nextSelectable(found, found.size - 1, 1)).isEqualTo(1)
    }

    private fun sampleSections(ran: (String) -> Unit) = listOf(
        KCommandSection(
            "파일",
            listOf(
                KCommand("new", "새 문서", { ran("new") }, shortcut = "Ctrl+N"),
                KCommand("open", "문서 열기", { ran("open") }),
                KCommand("locked", "잠긴 명령", { ran("locked") }, enabled = false),
            ),
        ),
        KCommandSection("보기", listOf(KCommand("theme", "어두운 테마", { ran("theme") }))),
    )

    private fun setPalette(state: KCommandPaletteState, ran: MutableList<String>) {
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                Box(Modifier.fillMaxSize().commandPaletteShortcut(state).focusable().testTag("screen")) {
                    KCommandPalette(state, sampleSections { ran += it })
                }
            }
        }
    }

    @Test
    fun `명령 판은 방향키로 고르고 Enter로 실행한 뒤 닫고 최근 명령에 넣는다`() {
        val state = KCommandPaletteState(initiallyOpen = true)
        val ran = mutableListOf<String>()
        setPalette(state, ran)
        compose.mainClock.advanceTimeBy(600)
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("문서")
        compose.waitForIdle()
        compose.onNodeWithText("2 results").assertIsDisplayed()
        // 제목 맨 앞에서 맞은 '문서 열기'가 먼저이고, 처음에는 그것을 골라 둔다.
        assertThat(compose.onNodeWithText("문서 열기").getBoundsInRoot().top).isLessThan(compose.onNodeWithText("새 문서").getBoundsInRoot().top)
        field.performKeyInput { pressKey(Key.DirectionDown) }
        field.performKeyInput { pressKey(Key.Enter) }
        compose.mainClock.advanceTimeBy(600)
        assertThat(ran).containsExactly("new")
        assertThat(state.isOpen).isFalse()
        assertThat(state.recentIds).containsExactly("new")
    }

    @Test
    fun `실행할 수 없는 명령은 방향키가 건너뛰고 맞는 것이 없으면 결과 없음을 보인다`() {
        val state = KCommandPaletteState(initiallyOpen = true)
        val ran = mutableListOf<String>()
        setPalette(state, ran)
        compose.mainClock.advanceTimeBy(600)
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("명령")
        compose.waitForIdle()
        field.performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertThat(ran).isEmpty()
        assertThat(state.isOpen).isTrue()

        field.performTextInput("없는말")
        compose.waitForIdle()
        compose.onNodeWithText("No results").assertIsDisplayed()
        compose.onNodeWithText("0 results").assertIsDisplayed()
    }

    @Test
    fun `결과 수는 TalkBack 알림 영역이고 단축키는 한 번에 읽힌다`() {
        val state = KCommandPaletteState(initiallyOpen = true)
        setPalette(state, mutableListOf())
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("4 results").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        compose.onNodeWithContentDescription("Shortcut Ctrl+N").assertIsDisplayed()
    }

    @Test
    fun `Ctrl+K로 열고 Esc로 닫는다`() {
        val state = KCommandPaletteState()
        setPalette(state, mutableListOf())
        assertThat(state.handleShortcut(androidx.compose.ui.input.key.KeyEvent(ctrlK()))).isTrue()
        assertThat(state.isOpen).isTrue()
        compose.mainClock.advanceTimeBy(600)
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        assertThat(state.isOpen).isFalse()
        compose.onNodeWithTag("screen").requestFocus()
        compose.onNodeWithTag("screen").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
        compose.waitForIdle()
        assertThat(state.isOpen).isTrue()
    }

    private fun ctrlK() = android.view.KeyEvent(
        0L,
        0L,
        android.view.KeyEvent.ACTION_DOWN,
        android.view.KeyEvent.KEYCODE_K,
        0,
        android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_CTRL_LEFT_ON,
    )

    @Test
    fun `최근 명령은 겹치지 않고 최대 개수만 남으며 저장했다 되살린다`() {
        val state = KCommandPaletteState(maxRecent = 2)
        state.addRecent("a")
        state.addRecent("b")
        state.addRecent("a")
        state.addRecent("c")
        assertThat(state.recentIds).containsExactly("c", "a").inOrder()
        val saved = with(KCommandPaletteState.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(state) }!!
        val restored = KCommandPaletteState.Saver.restore(saved)!!
        assertThat(restored.recentIds).containsExactly("c", "a").inOrder()
        assertThat(restored.maxRecent).isEqualTo(2)
    }

    // 데이터 표

    private data class Order(val id: String, val customer: String, val amount: Int)

    private val orders = listOf(Order("A-3", "민지", 30_000), Order("A-1", "서준", 12_000), Order("A-2", "하윤", 54_000))

    private val orderColumns = listOf(
        KTableColumn.text<Order>("주문", { it.id }),
        KTableColumn.text<Order>("고객", { it.customer }, comparator = null),
        KTableColumn.text<Order>("금액", { "%,d".format(it.amount) }, numeric = true, comparator = compareBy { it.amount }),
    )

    @Test
    fun `정렬은 비교자 순서대로 하고 같은 값은 원래 순서를 지킨다`() {
        assertThat(sortRows(orders, orderColumns, KTableSort(2)).map { it.id }).containsExactly("A-1", "A-3", "A-2").inOrder()
        assertThat(
            sortRows(orders, orderColumns, KTableSort(2, ascending = false)).map {
                it.id
            },
        ).containsExactly("A-2", "A-3", "A-1").inOrder()
        assertThat(sortRows(orders, orderColumns, KTableSort(1))).isEqualTo(orders)
        val state = KDataTableState()
        state.toggleSort(0)
        assertThat(state.sort).isEqualTo(KTableSort(0, true))
        state.toggleSort(0)
        assertThat(state.sort).isEqualTo(KTableSort(0, false))
        state.toggleSort(2)
        assertThat(state.sort).isEqualTo(KTableSort(2, true))
    }

    @Test
    fun `남는 폭은 가중치 비율로 나누고 모자라면 열 폭 그대로 둔다`() {
        assertThat(resolveColumnWidths(listOf(100f, 100f), listOf(1f, 3f), 400f)).containsExactly(150f, 250f).inOrder()
        assertThat(resolveColumnWidths(listOf(100f, 100f), listOf(1f, 3f), 150f)).containsExactly(100f, 100f).inOrder()
        assertThat(resolveColumnWidths(listOf(100f, 100f), listOf(0f, 0f), 400f)).containsExactly(100f, 100f).inOrder()
    }

    private fun setTable(state: KDataTableState, rows: List<Order> = orders) {
        compose.setContent {
            KineticaScaffold(surface = SurfaceStyles.CleanLight) {
                KDataTable(rows, orderColumns, key = { it.id }, Modifier.height(400.dp).testTag("table"), state = state, selectable = true)
            }
        }
    }

    @Test
    fun `열 머리를 누르면 오름차순, 한 번 더 누르면 내림차순으로 바뀌고 정렬 상태를 읽는다`() {
        val state = KDataTableState()
        setTable(state)
        val header = compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not sorted").and(hasTextExactly("금액")),
        )
        header.performClick()
        compose.waitForIdle()
        assertThat(state.sort).isEqualTo(KTableSort(2, true))
        fun topOf(text: String) = compose.onNodeWithText(text).getBoundsInRoot().top.value
        assertThat(topOf("A-1")).isLessThan(topOf("A-3"))
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sorted ascending")).performClick()
        compose.waitForIdle()
        assertThat(state.sort).isEqualTo(KTableSort(2, false))
        assertThat(topOf("A-2")).isLessThan(topOf("A-3"))
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sorted descending")).assertIsDisplayed()
    }

    @Test
    fun `표는 TalkBack에 행과 열 수를 알리고 머리 체크박스로 모두 고르고 푼다`() {
        val state = KDataTableState()
        setTable(state)
        compose.onNodeWithTag("table").assert(
            SemanticsMatcher("4행 4열 표") {
                val info = it.config.getOrNull(SemanticsProperties.CollectionInfo)
                info?.rowCount == 4 && info.columnCount == 4
            },
        )
        compose.onNodeWithContentDescription("Select all rows").performClick()
        compose.waitForIdle()
        assertThat(state.selection).containsExactly("A-1", "A-2", "A-3")
        compose.onNodeWithContentDescription("Select Row 2").performClick()
        compose.waitForIdle()
        assertThat(state.selection).hasSize(2)
        compose.onNodeWithContentDescription("Select all rows").performClick()
        compose.waitForIdle()
        assertThat(state.selection).containsExactly("A-1", "A-2", "A-3")
        compose.onNodeWithContentDescription("Select all rows").performClick()
        compose.waitForIdle()
        assertThat(state.selection).isEmpty()
    }

    @Test
    fun `행이 없으면 머리 아래에 빈 상태를 보인다`() {
        setTable(KDataTableState(), rows = emptyList())
        compose.onNodeWithText("No data").assertIsDisplayed()
        assertThat(compose.onAllNodesWithText("주문").fetchSemanticsNodes()).isNotEmpty()
    }

    private fun hasTextExactly(text: String) = androidx.compose.ui.test.hasText(text)
}
