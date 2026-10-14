package com.naury.kinetica.board

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BoardStacksTest {
    private val catalog = listOf(
        BoardWidget("sales", "매출", widthDp = 200f, heightDp = 100f),
        BoardWidget("orders", "주문", widthDp = 120f, heightDp = 80f),
        BoardWidget("weather", "날씨", widthDp = 120f, heightDp = 80f),
        BoardWidget("fixed", "필수", removable = false, placedByDefault = false),
    )

    @Test
    fun `위젯을 다른 위젯에 겹치면 그 자리의 묶음이 되고 더하기·풀기·되돌리기가 된다`() {
        val state = KineticaBoardState.create(catalog)
        val salesRect = state.rectOf("sales")

        val stackId = state.stack("sales", "orders")!!
        assertThat(state.placedKeys).containsExactly(stackId, "weather").inOrder()
        assertThat(state.stackMembers(stackId)).containsExactly("sales", "orders").inOrder()
        assertThat(state.rectOf(stackId)).isEqualTo(salesRect)
        assertThat(state.widgetKeyOf("orders")).isEqualTo("orders")
        assertThat(state.availableWidgets.map { it.key }).containsExactly("fixed")

        assertThat(state.stack(stackId, "weather")).isEqualTo(stackId)
        assertThat(state.stackMembers(stackId)).containsExactly("sales", "orders", "weather").inOrder()

        assertThat(state.unstack(stackId)).isTrue()
        assertThat(state.placedKeys).containsExactly("sales", "orders", "weather")
        assertThat(state.rectOf("sales")).isEqualTo(salesRect)

        state.history.undo()
        assertThat(state.stackMembers(stackId)).hasSize(3)
    }

    @Test
    fun `저장한 묶음은 다시 열어도 남고 목록에서 빠진 위젯은 묶음에서 빠진다`() {
        val state = KineticaBoardState.create(catalog)
        val stackId = state.stack("sales", "orders")!!

        val reopened = KineticaBoardState.create(catalog, saved = state.toJson())
        val shrunk = KineticaBoardState.create(catalog.filter { it.key != "orders" }, saved = state.toJson())

        assertThat(reopened.stackMembers(stackId)).containsExactly("sales", "orders").inOrder()
        assertThat(shrunk.placedKeys).contains("sales")
        assertThat(shrunk.stackMembers("sales")).isEmpty()
    }

    @Test
    fun `필수 위젯이 든 묶음은 뺄 수 없고 여러 개 위젯을 더 놓아도 묶음 안 id와 겹치지 않는다`() {
        val withMemo = catalog + BoardWidget("memo", "메모", multiple = true, placedByDefault = false)
        val state = KineticaBoardState.create(withMemo)
        state.add("fixed")
        val first = state.addItem("memo")!!
        val stackId = state.stack("fixed", first)!!

        assertThat(state.remove(stackId)).isFalse()
        assertThat(state.addItem("memo")).isNotEqualTo(first)
    }
}
