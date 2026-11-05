package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 거품 선택기, 빛줄기, 데이터 표, 쌓인 카드의 결함 회귀 시험 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BubbleBeamTableStackFixTest {
    @get:Rule
    val compose = createComposeRule()

    // 거품 선택기

    private fun bubbleRects(labels: List<String>): List<Rect> = labels.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }

    private fun assertSettledInside(labels: List<String>) {
        val box = compose.onNodeWithTag("picker").fetchSemanticsNode().boundsInRoot
        val rects = bubbleRects(labels)
        for (i in rects.indices) {
            val r = rects[i]
            assertThat(r.center.x).isIn(com.google.common.collect.Range.closed(box.left, box.right))
            assertThat(r.center.y).isIn(com.google.common.collect.Range.closed(box.top, box.bottom))
            for (j in i + 1 until rects.size) {
                val distance = (r.center - rects[j].center).getDistance()
                assertThat(distance).isAtLeast((r.width + rects[j].width) / 2f * 0.9f)
            }
        }
    }

    private val genres = listOf("팝", "재즈", "록", "클래식")
    private val moreGenres = genres + listOf("힙합", "R&B", "인디", "메탈", "포크")

    private fun pickerAfterAdding(reduced: Boolean) {
        var labels by mutableStateOf(genres)
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides reduced) {
                KBubblePicker(labels, setOf("록"), {}, Modifier.size(360.dp, 420.dp).testTag("picker"), bubbleSize = 72.dp)
            }
        }
        compose.waitForIdle()
        labels = moreGenres
        compose.waitForIdle()
        assertSettledInside(moreGenres)
    }

    @Test
    fun `정착해 멈춘 고정 높이 선택기에 항목을 더하면 새 거품도 겹치지 않게 자리 잡는다`() {
        pickerAfterAdding(reduced = false)
    }

    @Test
    fun `애니메이션 삭제에서도 항목을 더하면 다시 정착한다`() {
        pickerAfterAdding(reduced = true)
    }

    @Test
    fun `가운데 거품을 지워도 남은 거품은 제자리에 있다`() {
        var labels by mutableStateOf(genres)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KBubblePicker(labels, setOf("재즈"), {}, Modifier.size(360.dp, 420.dp).testTag("picker"), bubbleSize = 72.dp)
        }
        compose.mainClock.advanceTimeBy(10_000)
        val before = bubbleRects(listOf("록", "클래식"))
        labels = listOf("팝", "록", "클래식")
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        val after = bubbleRects(listOf("록", "클래식"))
        for (i in before.indices) {
            assertThat((after[i].center - before[i].center).getDistance()).isLessThan(8f)
            assertThat(after[i].width).isWithin(2f).of(before[i].width)
        }
    }

    @Test
    fun `아주 짧은 걸음 뒤에도 거품 속도가 터지지 않는다`() {
        fun physics() = BubblePhysics().apply {
            sync(List(8) { "거품 $it" }, 40f)
            bounds(400f, 300f)
            repeat(20) { step(STEP_SECONDS) }
        }
        val normal = physics()
        val tiny = physics()
        normal.step(STEP_SECONDS)
        tiny.step(1e-4f)
        val normalMax = (0 until normal.count).maxOf { normal.speedOf(it) }
        val tinyMax = (0 until tiny.count).maxOf { tiny.speedOf(it) }
        assertThat(tinyMax.isFinite()).isTrue()
        assertThat(tinyMax).isAtMost(normalMax * 2f + 1f)
    }

    // 빛줄기

    @Test
    fun `끝점 요소가 빠지면 그 빛줄기를 그리지 않는다`() {
        var showEnd by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                Box(Modifier.size(300.dp, 100.dp).background(Color.White).testTag("box")) {
                    KAnimatedBeam(listOf(KBeam("a", "b")), Modifier.fillMaxWidth().height(100.dp), color = Color.Red, width = 4.dp) {
                        Box(Modifier.align(Alignment.CenterStart).size(20.dp).beamAnchor("a"))
                        if (showEnd) Box(Modifier.align(Alignment.CenterEnd).size(20.dp).beamAnchor("b"))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        fun middle(): Color = compose.onNodeWithTag("box").captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }
        assertThat(middle()).isNotEqualTo(Color.White)
        showEnd = false
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertThat(middle()).isEqualTo(Color.White)
    }

    // 데이터 표

    private data class Order(val id: String, val amount: Int)

    /** 번들에 담을 수 없는 선택 키 */
    private class OpaqueKey(val id: String)

    private val orders = listOf(Order("A-1", 1), Order("A-2", 2), Order("A-3", 3))
    private val columns = listOf(
        KTableColumn.text<Order>("주문", { it.id }),
        KTableColumn.text<Order>("금액", { it.amount.toString() }, numeric = true, comparator = compareBy { it.amount }),
    )

    private fun SemanticsNode.collectionAncestor(): SemanticsNode? {
        var node = parent
        while (node != null && node.config.getOrNull(SemanticsProperties.CollectionInfo) == null) node = node.parent
        return node
    }

    @Test
    fun `몸통 칸은 TalkBack에 표 크기의 목록 정보 아래에 있다`() {
        compose.setContent {
            KDataTable(orders, columns, key = { it.id }, Modifier.height(400.dp), selectable = true)
        }
        val cell = compose.onNodeWithText("A-2").fetchSemanticsNode()
        assertThat(cell.config.getOrNull(SemanticsProperties.CollectionItemInfo)?.rowIndex).isEqualTo(2)
        val info = cell.collectionAncestor()?.config?.getOrNull(SemanticsProperties.CollectionInfo)
        assertThat(info?.rowCount).isEqualTo(4)
        assertThat(info?.columnCount).isEqualTo(3)
    }

    @Test
    fun `목록에서 빠진 행은 선택에서도 빠진다`() {
        val state = KDataTableState()
        var rows by mutableStateOf(orders)
        compose.setContent {
            KDataTable(rows, columns, key = { it.id }, Modifier.height(400.dp), state = state, selectable = true)
        }
        compose.onNodeWithContentDescription("Select all rows").performClick()
        compose.waitForIdle()
        assertThat(state.selection).containsExactly("A-1", "A-2", "A-3")
        rows = orders.filter { it.id != "A-2" }
        compose.waitForIdle()
        assertThat(state.selection).containsExactly("A-1", "A-3")
    }

    @Test
    fun `회전 뒤에도 정렬과 선택이 남는다`() {
        val restore = StateRestorationTester(compose)
        var state: KDataTableState? = null
        restore.setContent {
            state = rememberKDataTableState()
            KDataTable(orders, columns, key = { it.id }, Modifier.height(400.dp), state = state!!, selectable = true)
        }
        compose.runOnIdle {
            state!!.toggleSort(1)
            state!!.toggleSort(1)
            state!!.setSelected("A-3", true)
        }
        state = null
        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertThat(state?.sort).isEqualTo(KTableSort(1, ascending = false))
            assertThat(state?.selection).containsExactly("A-3")
        }
    }

    @Test
    fun `저장할 수 없는 선택 키가 있으면 회전 뒤 정렬만 남고 죽지 않는다`() {
        val restore = StateRestorationTester(compose)
        var state: KDataTableState? = null
        restore.setContent { state = rememberKDataTableState() }
        compose.runOnIdle {
            state!!.toggleSort(1)
            state!!.selection = setOf(OpaqueKey("A-1"), "A-2")
        }
        state = null
        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertThat(state?.sort).isEqualTo(KTableSort(1, ascending = true))
            assertThat(state?.selection).containsExactly("A-2")
        }
    }

    // 쌓인 카드

    private val clickableCards = SemanticsMatcher("누를 수 있는 카드") {
        hasClickAction().matches(it) && it.config.getOrNull(SemanticsProperties.CollectionItemInfo) != null
    }

    @Test
    fun `쌓인 상태에서 뒤에 숨은 카드는 TalkBack이 읽지 않고 누를 수도 없다`() {
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                KStackedCards(5, Modifier.width(300.dp), maxStacked = 3) { i -> BasicText("카드 $i", Modifier.fillMaxSize()) }
            }
        }
        // 0, 1번과 맨 앞 4번만 보이고 2, 3번은 4번 뒤에 숨는다.
        assertThat(compose.onAllNodesWithText("카드 2").fetchSemanticsNodes()).isEmpty()
        assertThat(compose.onAllNodesWithText("카드 3").fetchSemanticsNodes()).isEmpty()
        assertThat(compose.onAllNodesWithText("카드 4").fetchSemanticsNodes()).hasSize(1)
        assertThat(compose.onAllNodes(clickableCards).fetchSemanticsNodes()).hasSize(3)
    }

    @Test
    fun `카드를 고르면 아래 묶음에서 숨은 카드는 TalkBack이 읽지 않는다`() {
        lateinit var state: KStackedCardsState
        compose.setContent {
            CompositionLocalProvider(LocalKineticaReducedMotion provides true) {
                state = rememberKStackedCardsState(initiallyExpanded = true)
                KStackedCards(5, Modifier.width(300.dp), state = state, maxStacked = 3) { i -> BasicText("카드 $i", Modifier.fillMaxSize()) }
            }
        }
        assertThat(compose.onAllNodes(clickableCards).fetchSemanticsNodes()).hasSize(5)
        compose.runOnIdle { state.select(0) }
        compose.waitForIdle()
        // 아래 묶음 1, 2, 3, 4 중 1, 2와 맨 앞 4만 보인다.
        assertThat(compose.onAllNodesWithText("카드 3").fetchSemanticsNodes()).isEmpty()
        assertThat(compose.onAllNodes(clickableCards).fetchSemanticsNodes()).hasSize(4)
    }
}
