package com.naury.kinetica.showcase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.text.NumberFormat
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 가게 데이터. 실제 앱이라면 서버에서 온다. */
data class StoreData(val sales: Int, val orders: List<String>, val week: List<Float>, val open: Boolean)

/** 2초마다 바뀌는 예제 데이터. 위젯은 보드와 상관없이 이 값을 그대로 그린다. */
class StoreViewModel : ViewModel() {
    private val random = Random(SEED)
    private val mutableData = MutableStateFlow(next())
    val data: StateFlow<StoreData> = mutableData.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                mutableData.value = next()
            }
        }
    }

    private fun next() = StoreData(
        sales = BASE_SALES + random.nextInt(SALES_RANGE),
        orders = List(RECENT_ORDERS) {
            MENU[random.nextInt(MENU.size)] + " · " +
                PRICE.format((random.nextInt(PRICE_RANGE) + 1) * PRICE_UNIT) +
                "원"
        },
        week = List(DAYS) { random.nextFloat() },
        open = random.nextInt(OPEN_ODDS) != 0,
    )

    private companion object {
        const val SEED = 11
        const val TICK_MS = 2_000L
        const val BASE_SALES = 1_200_000
        const val SALES_RANGE = 400_000
        const val RECENT_ORDERS = 3
        const val PRICE_RANGE = 9
        const val PRICE_UNIT = 1_000
        const val DAYS = 7
        const val OPEN_ODDS = 4
        val PRICE: NumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
        val MENU = listOf("아메리카노", "라떼", "크루아상", "샌드위치", "케이크")
    }
}
