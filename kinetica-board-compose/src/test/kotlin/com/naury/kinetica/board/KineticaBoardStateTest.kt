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
    fun `저장된 배치가 없으면 기본 위젯을 위에서부터 빈자리에 채운다`() {
        val state = KineticaBoardState.create(catalog)

        assertThat(state.placedKeys).containsExactly("sales", "orders").inOrder()
        assertThat(state.rectOf("sales")).isEqualTo(Rect(33.0, 33.0, 583.0, 308.0))
        assertThat(state.rectOf("orders")).isEqualTo(Rect(616.0, 33.0, 946.0, 253.0))
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
        assertThat(state.restoreFailed).isTrue()
        assertThat(KineticaBoardState.create(catalog).restoreFailed).isFalse()
    }

    @Test
    fun `읽지 못한 저장본은 사용자가 바꾸기 전까지 덮어쓰지 않는다`() {
        val saves = mutableListOf<String>()
        val storage = object : BoardStorage {
            override fun load() = "{broken"

            override fun save(json: String) {
                saves += json
            }
        }
        val holder = BoardStateHolder(KineticaBoardState.create(catalog, saved = storage.load()), storage)

        holder.save()
        assertThat(saves).isEmpty()
        holder.state.add("weather")
        holder.save()

        assertThat(saves).hasSize(1)
        assertThat(KineticaBoardState.create(catalog, saved = saves.single()).placedKeys).contains("weather")
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

    @Test
    fun `여러 개 놓는 위젯은 자리마다 id가 다르고 저장 뒤에도 유지된다`() {
        val withMemo = catalog + BoardWidget("memo", "메모", multiple = true, placedByDefault = false)
        val state = KineticaBoardState.create(withMemo)

        val first = state.addItem("memo")
        val second = state.addItem("memo")
        val single = state.addItem("weather")

        assertThat(first).isEqualTo("memo")
        assertThat(second).isEqualTo("memo.2")
        assertThat(single).isEqualTo("weather")
        assertThat(state.addItem("weather")).isNull()
        assertThat(state.availableWidgets.map { it.key }).containsExactly("memo")
        assertThat(state.widgetKeyOf("memo.2")).isEqualTo("memo")

        val reopened = KineticaBoardState.create(withMemo, saved = state.toJson())
        assertThat(reopened.placedKeys).containsAtLeast("memo", "memo.2")
        assertThat(reopened.widgetKeyOf("memo.2")).isEqualTo("memo")
        assertThat(reopened.remove("memo.2")).isTrue()
        assertThat(reopened.placedKeys).doesNotContain("memo.2")
        // 목록에서 위젯이 사라지면 그 위젯의 모든 자리가 빠진다.
        assertThat(KineticaBoardState.create(catalog, saved = state.toJson()).placedKeys).containsNoneOf("memo", "memo.2")
    }
}
