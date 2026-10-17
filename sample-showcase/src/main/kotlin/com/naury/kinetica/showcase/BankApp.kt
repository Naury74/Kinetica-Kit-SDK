// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.ui.KAmountDefaults
import com.naury.kinetica.ui.KAmountInput
import com.naury.kinetica.ui.KAmountPreset
import com.naury.kinetica.ui.KBarChart
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KCarousel
import com.naury.kinetica.ui.KChipGroup
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KKeypad
import com.naury.kinetica.ui.KKeypadMode
import com.naury.kinetica.ui.KListItem
import com.naury.kinetica.ui.KListLeading
import com.naury.kinetica.ui.KListSection
import com.naury.kinetica.ui.KListTrailing
import com.naury.kinetica.ui.KPageIndicator
import com.naury.kinetica.ui.KPinDots
import com.naury.kinetica.ui.KProgressBar
import com.naury.kinetica.ui.KResult
import com.naury.kinetica.ui.KResultMark
import com.naury.kinetica.ui.KSearchBar
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KStepProgressBar
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KTopAppBar
import com.naury.kinetica.ui.KValueRow
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.RollingText
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.jellyPress
import com.naury.kinetica.ui.kListSection
import com.naury.kinetica.ui.shake
import java.math.BigDecimal
import java.util.Currency
import kotlinx.coroutines.delay

/**
 * 송금 앱: 계좌 카드, 송금(받는 사람 → 금액 키패드 → 확인 시트 → 비밀번호), 거래 내역 세 화면. 초록 강조색을 섞은 톤 표면이라
 * 글래스 없이도 차분한 금융 앱 분위기가 난다. 보내면 잔액이 줄고 내역 맨 위에 거래가 생긴다.
 */
@Composable
internal fun BankApp(onBack: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf("home")) }
    var balance by rememberSaveable { mutableLongStateOf(2_486_300L) }
    // 보낸 거래는 "이름|금액"으로 담는다. 새 거래가 앞에 온다.
    val sent = rememberStringList()
    val pop = { stack = stack.dropLast(1) }
    AppTheme(BANK_LOOK) {
        PushNavigation(stack, onBack = pop) { screen ->
            when (screen) {
                "home" -> BankHome(balance, sent, onBack, onSend = { stack = stack + "send" }, onHistory = { stack = stack + "history" })

                "send" -> SendFlow(balance, pop) { name, amount ->
                    balance -= amount
                    sent.add(0, "$name|$amount")
                }

                else -> HistoryScreen(sent, pop)
            }
        }
    }
}

@Composable
private fun BankHome(balance: Long, sent: SnapshotStateList<String>, onBack: () -> Unit, onSend: () -> Unit, onHistory: () -> Unit) {
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { DemoBackdrop() },
        topBar = {
            KTopAppBar("하늘뱅크", onBack = onBack, actions = {
                KIconButton(KIcons.Bell, "알림", {}, variant = KVariant.Text, tone = KTone.Neutral)
            })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = navigationBarBottom() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item("cards") { AccountCards(balance) }
            item("actions") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = appPadding(20.dp, BANK_WIDTH)),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickAction("송금", KIcons.ArrowUp, Modifier.weight(1f), onSend)
                    QuickAction("채우기", KIcons.ArrowDown, Modifier.weight(1f)) {}
                    QuickAction("내역", AppIcons.Receipt, Modifier.weight(1f), onHistory)
                    QuickAction("카드", KIcons.Card, Modifier.weight(1f)) {}
                }
            }
            item("budget") { BudgetCard(Modifier.padding(horizontal = appPadding(20.dp, BANK_WIDTH))) }
            item("recent") {
                KListSection(
                    Modifier.padding(horizontal = appPadding(20.dp, BANK_WIDTH)),
                    header = "최근 거래",
                    footer = "전체 내역은 위 '내역'에서 볼 수 있어요.",
                ) {
                    allTransactions(sent).take(5).forEach { tx -> item(tx.key) { TransactionRow(tx) } }
                }
            }
        }
    }
}

@Composable
private fun AccountCards(balance: Long) {
    val pager = androidx.compose.foundation.pager.rememberPagerState { ACCOUNTS.size }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            KCarousel(ACCOUNTS.size, Modifier.widthIn(max = 520.dp).fillMaxWidth(), peek = 28.dp, state = pager) { page ->
                val account = ACCOUNTS[page]
                ArtBox(account.colors, page + 4, Modifier.fillMaxWidth().aspectRatio(1.7f), RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxSize().padding(20.dp)) {
                        Label(account.name, 14, FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
                        Label(account.number, 12, color = Color.White.copy(alpha = 0.7f))
                        Spacer(Modifier.weight(1f))
                        RollingText(
                            won(if (page == 0) balance else account.balance),
                            style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
        KPageIndicator(pager, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    KSurface(onClick, modifier.jellyPress(0.05f), shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(40.dp).background(KineticaTheme.colors.accent.copy(alpha = 0.14f), CircleShape), Alignment.Center) {
                ShowcaseIcon(icon, 22.dp, KineticaTheme.colors.accent)
            }
            Label(label, 13, FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BudgetCard(modifier: Modifier) {
    KSurface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("10월 예산", 15, FontWeight.SemiBold, Modifier.weight(1f))
                Label("68% 사용", 13, color = LocalKineticaContentColor.current.copy(alpha = 0.6f))
            }
            KProgressBar(0.68f, Modifier.fillMaxWidth())
            Label("남은 예산 ${won(512_000)} · 하루 ${won(34_100)}씩 쓸 수 있어요", 13, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
            KBarChart(
                listOf(42f, 58f, 31f, 77f, 49f, 64f, 28f),
                Modifier.fillMaxWidth().height(110.dp),
                labels = listOf("월", "화", "수", "목", "금", "토", "일"),
                valueFormatter = { "${it.toInt()}천 원" },
            )
        }
    }
}

/** 송금 단계. 받는 사람과 금액은 화면 안에서 바꾸고, 확인은 시트, 비밀번호와 완료는 화면 전체로 보여 준다. */
@Composable
private fun SendFlow(balance: Long, onClose: () -> Unit, onSent: (String, Long) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var to by rememberSaveable { mutableIntStateOf(-1) }
    var amount by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val value = KAmountDefaults.toBigDecimal(amount).toLong()
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { DemoBackdrop() },
        topBar = {
            Column {
                KTopAppBar(
                    when (step) {
                        0 -> "누구에게 보낼까요?"
                        1 -> "${PAYEES[to].name}님에게"
                        2 -> "비밀번호 입력"
                        else -> "송금 완료"
                    },
                    onBack = if (step == 3) null else ({ if (step > 0) step-- else onClose() }),
                )
                KStepProgressBar(minOf(step, 2) + 1, 3, Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }
        },
    ) { padding ->
        val inner = Modifier.fillMaxSize().padding(
            start = appPadding(20.dp, 520.dp),
            end = appPadding(20.dp, 520.dp),
            top = padding.calculateTopPadding() + 12.dp,
        )
        when (step) {
            0 -> PayeePicker(inner) {
                to = it
                step = 1
            }

            1 -> AmountStep(inner, amount, { amount = it }, balance) { confirm = true }

            2 -> PinPad(inner) {
                onSent(PAYEES[to].name, value)
                step = 3
            }

            else -> SendDone(inner, PAYEES[to].name, value, onClose)
        }
    }
    if (to >= 0) {
        KSheet(confirm, { confirm = false }, title = "송금 확인") {
            ConfirmSend(PAYEES[to], value) {
                confirm = false
                step = 2
            }
        }
    }
}

@Composable
private fun AmountStep(modifier: Modifier, amount: String, onAmount: (String) -> Unit, balance: Long, onNext: () -> Unit) {
    val value = KAmountDefaults.toBigDecimal(amount).toLong()
    val over = value > balance
    Column(
        modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KAmountInput(
            amount,
            onAmount,
            Currency.getInstance("KRW"),
            Modifier.weight(1f),
            label = "보낼 금액",
            hint = "잔액 ${won(balance)}",
            error = if (over) "잔액보다 많이 보낼 수 없어요" else null,
            presets = listOf(
                KAmountPreset("+1만", BigDecimal(10_000)),
                KAmountPreset("+5만", BigDecimal(50_000)),
                KAmountPreset("+10만", BigDecimal(100_000)),
                KAmountPreset("전액", BigDecimal(balance)),
            ),
        )
        KButton("다음", onNext, size = KButtonSize.Large, fullWidth = true, enabled = value > 0 && !over)
    }
}

@Composable
private fun ConfirmSend(payee: Payee, value: Long, onSend: () -> Unit) {
    Label("${payee.name}님에게\n${won(value)}을 보낼게요", 22, FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    KValueRow("받는 계좌", "${payee.bank} ${payee.account}")
    KValueRow("수수료", "면제")
    KValueRow("받는 분 통장 표시", "김하늘")
    Spacer(Modifier.height(16.dp))
    KButton("보내기", onSend, size = KButtonSize.Large, fullWidth = true)
}

@Composable
private fun PayeePicker(modifier: Modifier, onPick: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = PAYEES.withIndex().filter { query.isBlank() || query.trim() in it.value.name || query.trim() in it.value.bank }
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = navigationBarBottom() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { KSearchBar(query, { query = it }, {}, placeholder = "이름, 은행, 계좌번호") }
        item {
            KListSection(header = "최근 보낸 사람") {
                shown.forEach { (index, payee) ->
                    item(index) {
                        KListItem(
                            payee.name,
                            supporting = "${payee.bank} ${payee.account}",
                            leading = KListLeading.Avatar(payee.name.take(1), color = artPalette(index)[1]),
                            trailing = KListTrailing.Disclosure(),
                            onClick = { onPick(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinPad(modifier: Modifier, onOk: () -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(pin) {
        if (pin.length == 6) {
            delay(200)
            // 예시라 정해진 번호 대신 같은 숫자 여섯 개만 틀린 번호로 본다.
            if (pin.toSet().size == 1) {
                wrong++
                pin = ""
            } else {
                onOk()
            }
        }
    }
    Column(
        modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.weight(0.4f))
        Label(if (wrong > 0) "비밀번호가 맞지 않아요 ($wrong/5)" else "간편 비밀번호 6자리를 눌러 주세요", 15)
        KPinDots(6, pin.length, Modifier.shake(wrong), error = wrong > 0 && pin.isEmpty())
        Spacer(Modifier.weight(1f))
        KKeypad(
            onDigit = { if (pin.length < 6) pin += it },
            onBackspace = { pin = pin.dropLast(1) },
            mode = KKeypadMode.Pin,
            onClear = { pin = "" },
        )
    }
}

@Composable
private fun SendDone(modifier: Modifier, name: String, amount: Long, onClose: () -> Unit) {
    var result by rememberSaveable { mutableStateOf(KResult.Loading) }
    LaunchedEffect(Unit) {
        delay(900)
        result = KResult.Success
    }
    Column(
        modifier.verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        KResultMark(result, size = 88.dp)
        Label(if (result == KResult.Success) "${name}님에게\n${won(amount)}을 보냈어요" else "보내는 중이에요", 22, FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        KButton("확인", onClose, size = KButtonSize.Large, fullWidth = true, enabled = result == KResult.Success)
        KButton("영수증 보기", {}, variant = KVariant.Text, enabled = result == KResult.Success)
    }
}

@Composable
private fun HistoryScreen(sent: SnapshotStateList<String>, onBack: () -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val list = allTransactions(sent).filter { filter == 0 || (filter == 1) == (it.amount > 0) }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { DemoBackdrop() },
        topBar = { KTopAppBar("거래 내역", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = appPadding(20.dp, BANK_WIDTH),
                end = appPadding(20.dp, BANK_WIDTH),
                top = padding.calculateTopPadding() + 4.dp,
                bottom = navigationBarBottom() + 32.dp,
            ),
        ) {
            // kListSection은 행을 따로따로 넣으므로 목록에 간격을 주면 한 묶음이 갈라진다. 간격은 칩 줄에만 준다.
            item("filter") {
                KChipGroup(listOf("전체", "입금", "출금"), setOf(filter), {
                    filter = it.firstOrNull() ?: 0
                }, wrap = false, modifier = Modifier.padding(bottom = 8.dp))
            }
            list.groupBy { it.day }.forEach { (day, rows) ->
                kListSection(rows, key = { it.key }, header = day, sectionKey = day) { TransactionRow(it) }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: Transaction) {
    val income = tx.amount > 0
    KListItem(
        tx.title,
        supporting = "${tx.day} ${tx.time} · ${tx.kind}",
        leading = KListLeading.Icon(
            if (income) KIcons.ArrowDown else KIcons.ArrowUp,
            tint = if (income) KineticaTheme.colors.accent else LocalKineticaContentColor.current.copy(alpha = 0.7f),
            container = if (income) {
                KineticaTheme.colors.accent.copy(
                    alpha = 0.14f,
                )
            } else {
                LocalKineticaContentColor.current.copy(alpha = 0.08f)
            },
        ),
        trailing = KListTrailing.Value(if (income) "+${won(tx.amount)}" else won(tx.amount)),
    )
}

private class Transaction(val key: String, val title: String, val amount: Long, val day: String, val time: String, val kind: String)

private fun allTransactions(sent: List<String>): List<Transaction> = sent.mapIndexed { index, entry ->
    val (name, amount) = entry.split('|')
    Transaction("sent-${sent.size - index}", name, -amount.toLong(), "오늘", "방금", "송금")
} + HISTORY

private class Payee(val name: String, val bank: String, val account: String)
private class Account(val name: String, val number: String, val balance: Long, val colors: List<Color>)

private val PAYEES = listOf(
    Payee("이서연", "하늘뱅크", "1002-384-****"),
    Payee("박준호", "바다은행", "110-492-****"),
    Payee("최유나", "숲저축은행", "3333-07-****"),
    Payee("정민재", "하늘뱅크", "1002-551-****"),
    Payee("관리사무소", "들판은행", "302-0187-****"),
    Payee("한지우", "바다은행", "110-220-****"),
)
private val ACCOUNTS = listOf(
    Account("하늘 입출금 통장", "1002-123-456789", 0, listOf(Color(0xFF0F8F7E), Color(0xFF15C2A5))),
    Account("모으기 적금", "1002-987-654321", 3_200_000, listOf(Color(0xFF3B5BDB), Color(0xFF6C8CFF))),
    Account("여행 비상금", "1002-555-010203", 418_000, listOf(Color(0xFFE8590C), Color(0xFFFFA94D))),
)
private val HISTORY = listOf(
    Transaction("h1", "커피 한 잔", -4_800, "오늘", "08:42", "체크카드"),
    Transaction("h2", "10월 급여", 3_250_000, "어제", "09:00", "입금"),
    Transaction("h3", "편의점", -12_300, "어제", "21:15", "체크카드"),
    Transaction("h4", "박준호", 25_000, "어제", "19:03", "입금"),
    Transaction("h5", "관리비", -187_400, "10월 12일", "10:30", "자동이체"),
    Transaction("h6", "온라인 서점", -32_000, "10월 12일", "14:22", "체크카드"),
    Transaction("h7", "교통비", -1_450, "10월 11일", "08:10", "체크카드"),
    Transaction("h8", "이자", 2_841, "10월 11일", "00:00", "입금"),
    Transaction("h9", "점심 정산", -11_000, "10월 10일", "13:05", "송금"),
)

private val BANK_WIDTH = 640.dp
private val BANK_GREEN = Color(0xFF0F9D84)
internal val BANK_LOOK = AppLook(
    SurfaceStyle.Tonal(BANK_GREEN),
    SurfaceStyle.Tonal(BANK_GREEN, dark = true),
    defaultDark = false,
    accent = BANK_GREEN,
    accentDark = Color(0xFF3CCFB2),
    onAccentDark = Color(0xFF00201A),
)
