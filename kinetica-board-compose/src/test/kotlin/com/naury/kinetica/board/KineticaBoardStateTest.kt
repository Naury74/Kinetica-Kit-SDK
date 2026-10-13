package com.naury.kinetica.board

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Rect
import org.junit.Test

class KineticaBoardStateTest {
    private val catalog = listOf(
        BoardWidget("sales", "매출", widthDp = 200f, heightDp = 100f),
        BoardWidget("orders", "주문", widthDp = 120f, heightDp = 80f),
        BoardWidget("weather", "날씨", placedByDefault = false),
    )

    @Test
    fun `저장된 배치가 없으면 기본 위젯을 위에서부터 쌓는다`() {
        val state = KineticaBoardState.create(catalog)

        assertThat(state.placedKeys).containsExactly("sales", "orders").inOrder()
        assertThat(state.rectOf("sales")).isEqualTo(Rect(33.0, 33.0, 583.0, 308.0))
        assertThat(state.rectOf("orders")!!.top).isEqualTo(341.0)
        assertThat(state.availableWidgets.map { it.key }).containsExactly("weather")
        assertThat(state.history.state.value.canUndo).isFalse()
    }

    @Test
    fun `추가·빼기·옮기기는 한 단계씩 되돌린다`() {
        val state = KineticaBoardState.create(catalog)

        assertThat(state.add("weather")).isTrue()
        assertThat(state.place("sales", Rect(500.0, 600.0, 700.0, 700.0))).isTrue()
        assertThat(state.remove("orders")).isTrue()
        assertThat(state.add("unknown")).isFalse()

        state.history.undo()
        assertThat(state.placedKeys).contains("orders")
        state.history.undo()
        assertThat(state.rectOf("sales")!!.left).isEqualTo(33.0)
        state.history.undo()
        assertThat(state.placedKeys).doesNotContain("weather")
    }

    @Test
    fun `저장본을 열면 사용자가 뺀 위젯은 빠진 채이고 새로 생긴 위젯만 들어오며 모르는 위젯은 지운다`() {
        val first = KineticaBoardState.create(catalog)
        first.remove("orders")
        first.place("sales", Rect(100.0, 100.0, 400.0, 300.0))
        val saved = first.toJson()

        val updatedCatalog = catalog.filter { it.key != "weather" } + BoardWidget("news", "소식")
        val reopened = KineticaBoardState.create(updatedCatalog, saved = saved)

        assertThat(reopened.placedKeys).containsExactly("sales", "news").inOrder()
        assertThat(reopened.rectOf("sales")).isEqualTo(Rect(100.0, 100.0, 400.0, 300.0))
        assertThat(reopened.history.state.value.canUndo).isFalse()
    }

    @Test
    fun `읽을 수 없는 저장본이면 기본 배치로 시작한다`() {
        val state = KineticaBoardState.create(catalog, saved = "{broken")

        assertThat(state.placedKeys).containsExactly("sales", "orders")
    }

    @Test
    fun `격자 배치에서는 칸에 맞추고 겹친 위젯을 아래로 민다`() {
        val grid = BoardArrangement.Grid(columns = 2, gapDp = 0f)
        val state = KineticaBoardState.create(listOf(BoardWidget("a", "A"), BoardWidget("b", "B")), grid)
        // 기준 폭 1080을 두 칸으로 나누면 칸 하나가 540×540이다. b는 a 옆에 놓인다.
        assertThat(state.rectOf("b")).isEqualTo(Rect(540.0, 0.0, 1080.0, 540.0))

        state.place("a", Rect(0.0, 10.0, 1060.0, 530.0))

        assertThat(state.rectOf("a")).isEqualTo(Rect(0.0, 0.0, 1080.0, 540.0))
        assertThat(state.rectOf("b")).isEqualTo(Rect(540.0, 540.0, 1080.0, 1080.0))
        state.history.undo()
        assertThat(state.rectOf("b")).isEqualTo(Rect(540.0, 0.0, 1080.0, 540.0))
    }

    @Test
    fun `필수 위젯은 뺄 수 없다`() {
        val state = KineticaBoardState.create(listOf(BoardWidget("core", "필수", removable = false)))

        assertThat(state.remove("core")).isFalse()
        assertThat(state.placedKeys).containsExactly("core")
    }
}
