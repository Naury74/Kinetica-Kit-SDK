// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KBadge
import com.naury.kinetica.ui.KBentoCell
import com.naury.kinetica.ui.KBentoGrid
import com.naury.kinetica.ui.KBentoSpan
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KCommand
import com.naury.kinetica.ui.KCommandPalette
import com.naury.kinetica.ui.KCommandSection
import com.naury.kinetica.ui.KDataTable
import com.naury.kinetica.ui.KDataTableState
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KStatus
import com.naury.kinetica.ui.KStatusBadge
import com.naury.kinetica.ui.KTableColumn
import com.naury.kinetica.ui.KTableSort
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.Sparkline
import com.naury.kinetica.ui.commandPaletteShortcut
import com.naury.kinetica.ui.rememberKCommandPaletteState
import java.text.NumberFormat
import java.util.Locale

/** 화면 구성 묶음: 크기가 다른 칸을 맞춰 놓는 소개 격자, 단축키로 여는 명령 판, 정렬과 선택이 되는 데이터 표. */
internal fun LazyListScope.layoutsDemos() {
    item { BentoCard() }
    item { PaletteCard() }
    item { TableCard() }
}

@Composable
private fun BentoCard() {
    var round by remember { mutableIntStateOf(0) }
    DemoCard(
        "소개 격자",
        "1x1, 2x1, 1x2, 2x2 칸을 앞쪽 빈 자리부터 채우고 남은 구멍은 옆 칸을 늘려 메웁니다. 폰은 2열, 펼친 폴더블과 태블릿은 3~4열입니다.",
    ) {
        key(round) {
            KBentoGrid(minCellWidth = 140.dp, rowHeight = 112.dp) {
                KBentoCell(span = KBentoSpan.Large, onClick = {}, onClickLabel = "매출 자세히 보기") {
                    Column(Modifier.fillMaxSize()) {
                        Muted("이번 달 매출")
                        Label("₩48,320,000", 26, FontWeight.Bold)
                        KBadge("지난달보다 12.4% ↑", tone = KTone.Positive, variant = KVariant.Tonal)
                        Spacer(Modifier.weight(1f))
                        Sparkline(SALES, Modifier.fillMaxWidth().height(56.dp))
                    }
                }
                KBentoCell { Stat("신규 주문", "128건") }
                KBentoCell { Stat("배송 중", "342건") }
                KBentoCell(span = KBentoSpan.Tall) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Muted("재고 부족")
                        LOW_STOCK.forEach { (name, left) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Label(name, 14, modifier = Modifier.weight(1f))
                                Label("${left}개", 14, FontWeight.SemiBold)
                            }
                        }
                    }
                }
                KBentoCell(span = KBentoSpan.Wide) {
                    Column {
                        Muted("고객 만족도")
                        Label("4.8 / 5.0", 22, FontWeight.Bold)
                        Muted("리뷰 1,204개 중 92%가 4점 이상")
                    }
                }
                KBentoCell { Stat("환불 요청", "6건") }
            }
        }
        KButton("다시 등장시키기", { round++ }, variant = KVariant.Tonal, size = KButtonSize.Small)
    }
}

@Composable
private fun Stat(title: String, value: String) {
    Column {
        Muted(title)
        Spacer(Modifier.height(4.dp))
        Label(value, 22, FontWeight.Bold)
    }
}

@Composable
private fun Muted(text: String) {
    Label(text, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.62f))
}

@Composable
private fun PaletteCard() {
    val palette = rememberKCommandPaletteState()
    var last by rememberSaveable { mutableStateOf("아직 실행한 명령이 없습니다") }
    val sections = remember { paletteSections { last = "실행: $it" } }
    DemoCard(
        "명령 판",
        "하드웨어 키보드에서 Ctrl+K로 열고 방향키로 고른 뒤 Enter로 실행합니다. 띄엄띄엄 친 글자로도 찾고(예: 새주), 실행한 명령은 최근 목록에 남습니다.",
        Modifier.commandPaletteShortcut(palette),
    ) {
        KButton("명령 판 열기", palette::open, icon = KIcons.Search)
        Label(last, 14, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
    }
    KCommandPalette(palette, sections)
}

private fun paletteSections(onRun: (String) -> Unit): List<KCommandSection> {
    fun command(
        id: String,
        title: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        shortcut: String? = null,
        subtitle: String? = null,
    ) = KCommand(id, title, { onRun(title) }, subtitle = subtitle, icon = icon, shortcut = shortcut)
    return listOf(
        KCommandSection(
            "주문",
            listOf(
                command("new-order", "새 주문 만들기", KIcons.Plus, "Ctrl+N"),
                command("find-order", "주문 번호로 찾기", KIcons.Search, "Ctrl+F"),
                command("export", "주문 목록 내보내기", KIcons.Download, "Ctrl+Shift+E", "CSV 파일로 저장"),
                command("refund", "환불 요청 보기", KIcons.Refresh, subtitle = "처리 대기 6건"),
            ),
        ),
        KCommandSection(
            "이동",
            listOf(
                command("go-dashboard", "대시보드로 가기", KIcons.Home, "G D"),
                command("go-customers", "고객 목록으로 가기", KIcons.Users, "G C"),
                command("go-stock", "재고 관리로 가기", KIcons.Archive, "G S"),
                command("go-settings", "설정 열기", KIcons.Settings, "Ctrl+,"),
            ),
        ),
        KCommandSection(
            "보기",
            listOf(
                command("theme", "어두운 테마로 바꾸기", KIcons.Moon, "Ctrl+Shift+L"),
                command("calendar", "배송 일정 달력 보기", KIcons.Calendar),
                command("chart", "매출 차트 보기", KIcons.Chart),
            ),
        ),
    )
}

/** 데모 주문 한 건. 금액 단위는 원이다. */
private data class Order(
    val id: String,
    val customer: String,
    val product: String,
    val status: KStatus,
    val statusText: String,
    val date: String,
    val amount: Long,
)

@Composable
private fun TableCard() {
    val state = remember { KDataTableState(initialSort = KTableSort(column = 4, ascending = false)) }
    var empty by remember { mutableStateOf(false) }
    val rows = if (empty) emptyList() else ORDERS
    val won = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val picked = ORDERS.filter { it.id in state.selection }
    DemoCard(
        "데이터 표",
        "열 머리를 누르면 정렬하고 화살표가 돌아갑니다. 첫 열은 고정되고 나머지는 머리와 함께 가로로 스크롤됩니다. 체크박스로 여러 주문을 고릅니다.",
    ) {
        KDataTable(
            rows,
            remember { orderColumns(won) },
            key = { it.id },
            modifier = Modifier.fillMaxWidth().height(420.dp),
            state = state,
            selectable = true,
            rowLabel = { "주문 ${it.id}" },
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val summary = if (picked.isEmpty()) "고른 주문 없음" else "${picked.size}건 선택, 합계 ₩${won.format(picked.sumOf { it.amount })}"
            Label(summary, 14, modifier = Modifier.weight(1f))
            KButton(if (empty) "주문 채우기" else "빈 표 보기", { empty = !empty }, variant = KVariant.Tonal, size = KButtonSize.Small)
        }
    }
}

private fun orderColumns(won: NumberFormat): List<KTableColumn<Order>> = listOf(
    KTableColumn.text("주문 번호", { it.id }, width = 108.dp),
    KTableColumn.text("고객", { it.customer }, width = 96.dp),
    KTableColumn.text<Order>("상품", { it.product }, width = 168.dp).copy(weight = 1f),
    KTableColumn("상태", width = 104.dp, comparator = compareBy { it.status.ordinal }) { KStatusBadge(it.statusText, it.status) },
    KTableColumn.text("결제일", { it.date }, width = 104.dp),
    KTableColumn.text("금액", { "₩" + won.format(it.amount) }, width = 120.dp, numeric = true, comparator = compareBy { it.amount }),
)

private val SALES = listOf(31f, 34f, 30f, 38f, 41f, 39f, 45f, 43f, 49f, 52f, 50f, 58f)

private val LOW_STOCK = listOf("무선 이어폰" to 3, "충전 케이블" to 5, "노트북 거치대" to 2, "블루투스 키보드" to 4)

private val ORDERS: List<Order> = run {
    val customers = listOf("김민지", "이서준", "박하윤", "최도윤", "정서연", "강지호", "조유나", "윤시우", "장예린", "임준서")
    val products = listOf(
        "무선 이어폰 프로" to 189_000L,
        "노트북 거치대" to 39_000L,
        "기계식 키보드" to 129_000L,
        "4K 모니터 27형" to 459_000L,
        "USB-C 충전기 65W" to 45_000L,
        "인체공학 마우스" to 69_000L,
        "웹캠 풀HD" to 89_000L,
    )
    val statuses = listOf(
        KStatus.Success to "배송 완료",
        KStatus.Info to "배송 중",
        KStatus.Warning to "결제 대기",
        KStatus.Neutral to "준비 중",
        KStatus.Error to "환불 요청",
    )
    List(36) { i ->
        val (product, price) = products[(i * 3 + 1) % products.size]
        val (status, label) = statuses[(i * 7 + 2) % statuses.size]
        val quantity = 1 + (i * 5) % 3
        Order(
            id = "#" + (24_118 - i),
            customer = customers[(i * 7) % customers.size],
            product = if (quantity > 1) "$product 외 ${quantity - 1}" else product,
            status = status,
            statusText = label,
            date = "10월 %02d일".format(31 - i % 28),
            amount = price * quantity,
        )
    }
}
