package com.naury.kinetica.showcase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 주문 한 건. [time]은 "14:05"처럼 시와 분이다. */
data class StoreOrder(val menu: String, val price: Int, val time: String)

/**
 * 가게 데이터. 실제 앱이라면 서버에서 온다.
 * [orders]는 최근 주문이 앞에 오고, [week]는 지난 6일과 오늘의 매출(원)이다.
 */
data class StoreData(val sales: Int, val orders: List<StoreOrder>, val week: List<Int>, val open: Boolean)

/** 2초마다 주문이 하나씩 들어오는 예제 데이터. 위젯은 보드와 상관없이 이 값을 그대로 그린다. */
class StoreViewModel : ViewModel() {
    private val random = Random(SEED)
    private val mutableData = MutableStateFlow(initial())
    val data: StateFlow<StoreData> = mutableData.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                mutableData.update { it.withOrder(order(LocalTime.now())) }
            }
        }
    }

    private fun initial(): StoreData {
        val now = LocalTime.now()
        val orders = List(ORDER_HISTORY) { order(now.minusMinutes(((it + 1) * ORDER_SPACING_MIN).toLong())) }
        val sales = BASE_SALES + random.nextInt(SALES_RANGE)
        val past = List(DAYS - 1) { PAST_MIN + random.nextInt(PAST_RANGE) }
        return StoreData(sales, orders, past + sales, open = true)
    }

    private fun StoreData.withOrder(order: StoreOrder): StoreData {
        val total = sales + order.price
        return copy(sales = total, orders = (listOf(order) + orders).take(ORDER_HISTORY), week = week.dropLast(1) + total)
    }

    private fun order(at: LocalTime) = StoreOrder(
        menu = MENU[random.nextInt(MENU.size)],
        price = (random.nextInt(PRICE_RANGE) + PRICE_MIN) * PRICE_UNIT,
        time = at.format(TIME),
    )

    private companion object {
        const val SEED = 11
        const val TICK_MS = 2_000L
        const val BASE_SALES = 1_200_000
        const val SALES_RANGE = 200_000
        const val PAST_MIN = 900_000
        const val PAST_RANGE = 800_000
        const val ORDER_HISTORY = 12
        const val ORDER_SPACING_MIN = 3
        const val PRICE_MIN = 3
        const val PRICE_RANGE = 7
        const val PRICE_UNIT = 1_000
        const val DAYS = 7
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val MENU = listOf("아메리카노", "카페 라떼", "크루아상", "샌드위치", "치즈 케이크", "바닐라 라떼", "레몬 에이드")
    }
}
