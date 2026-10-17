// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
// 예시 데이터 클래스는 필드를 생성자에 나란히 두는 편이 읽기 쉬워 인자가 많다.
@file:Suppress("MagicNumber", "LongParameterList")

package com.naury.kinetica.showcase

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KChipGroup
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KMapPin
import com.naury.kinetica.ui.KMediaCard
import com.naury.kinetica.ui.KProgressBar
import com.naury.kinetica.ui.KQuantityStepper
import com.naury.kinetica.ui.KQuantityStyle
import com.naury.kinetica.ui.KRating
import com.naury.kinetica.ui.KSearchBar
import com.naury.kinetica.ui.KStatus
import com.naury.kinetica.ui.KStatusBadge
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KTimeline
import com.naury.kinetica.ui.KTimelineItem
import com.naury.kinetica.ui.KTimelineStatus
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KTopAppBar
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.RollingText
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.jellyPress
import kotlinx.coroutines.delay

/**
 * 음식 배달 앱: 가게 목록, 메뉴 담기, 주문 추적 세 화면. 주황 강조색을 섞은 따뜻한 톤 표면이다.
 * 메뉴의 + 를 누르면 수량 캡슐로 펼쳐지고 아래 주문 바가 올라온다. 주문하면 단계 타임라인과 지도 위 라이더 핀이 시간에 따라 움직인다.
 */
@Composable
internal fun DeliveryApp(onBack: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf("home")) }
    val cart = rememberIntMap()
    val pop = { stack = stack.dropLast(1) }
    AppTheme(DELIVERY_LOOK) {
        PushNavigation(stack, onBack = pop) { screen ->
            when {
                screen == "home" -> StoreList(onBack) { stack = stack + "store/$it" }

                screen.startsWith("store/") -> StoreMenu(STORES[screen.substringAfter('/').toInt()], cart, pop) {
                    stack = listOf("home", "track")
                }

                else -> TrackOrder(pop) {
                    cart.clear()
                    stack = listOf("home")
                }
            }
        }
    }
}

@Composable
private fun StoreList(onBack: () -> Unit, onOpen: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableIntStateOf(0) }
    val shown = STORES.withIndex().filter { (kind == 0 || it.value.kind == kind) && (query.isBlank() || query.trim() in it.value.name) }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        topBar = {
            KTopAppBar("우리 집", onBack = onBack, subtitle = "하늘길 24 · 302호", actions = {
                KIconButton(ShowcaseIcons.Bell, "알림", {}, variant = KVariant.Text, tone = KTone.Neutral)
            })
        },
    ) { padding ->
        LazyVerticalGrid(
            GridCells.Adaptive(300.dp),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = appPadding(16.dp, 1000.dp),
                end = appPadding(16.dp, 1000.dp),
                top = padding.calculateTopPadding() + 4.dp,
                bottom = navigationBarBottom() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KSearchBar(query, { query = it }, {}, placeholder = "먹고 싶은 메뉴, 가게 이름")
                    KChipGroup(KINDS, setOf(kind), { kind = it.firstOrNull() ?: 0 }, wrap = false, variant = KVariant.Tonal)
                }
            }
            items(shown, key = { it.index }) { (index, store) ->
                KMediaCard(
                    { ArtBox(store.palette, index + 11, Modifier.fillMaxSize()) },
                    store.name,
                    Modifier.jellyPress(0.03f),
                    subtitle = "★ ${store.rating} · ${store.minutes} · ${if (store.fee == 0) "배달비 무료" else "배달비 ${won(store.fee)}"}",
                    eyebrow = KINDS[store.kind],
                    onClick = { onOpen(index) },
                    aspectRatio = 16f / 9f,
                    trailing = if (store.fee ==
                        0
                    ) {
                        ({ KStatusBadge("무료 배달", KStatus.Success, variant = KVariant.Filled, dot = false) })
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun StoreMenu(store: Store, cart: SnapshotStateMap<Int, Int>, onBack: () -> Unit, onOrder: () -> Unit) {
    val count = cart.values.sum()
    val total = MENU.withIndex().sumOf { (i, m) -> m.price * (cart[i] ?: 0) }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        topBar = { KTopAppBar(store.name, onBack = onBack, subtitle = "${store.minutes} · 최소 주문 ${won(12_000)}") },
        bottomBar = {
            AnimatedVisibility(count > 0, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp), Alignment.Center) {
                    KButton(
                        "${count}개 담음 · ${won(total + store.fee)} 주문하기",
                        onOrder,
                        Modifier.widthIn(max = 560.dp),
                        size = KButtonSize.Large,
                        fullWidth = true,
                        enabled = total >= 12_000,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = appPadding(16.dp, 720.dp),
                end = appPadding(16.dp, 720.dp),
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + navigationBarBottom() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("hero") { StoreHero(store) }
            if (total in 1 until 12_000) {
                item("min") {
                    Label("${won(12_000 - total)} 더 담으면 주문할 수 있어요", 13, FontWeight.SemiBold, color = KineticaTheme.colors.accent)
                }
            }
            MENU.groupBy { it.section }.forEach { (section, items) ->
                item("s-$section") { Label(section, 18, FontWeight.Bold, Modifier.padding(top = 12.dp, start = 4.dp)) }
                items(items, key = { it.name }) { menu ->
                    val index = MENU.indexOf(menu)
                    MenuRow(menu, index, cart[index] ?: 0) { cart[index] = it }
                }
            }
        }
    }
}

@Composable
private fun StoreHero(store: Store) {
    ArtBox(store.palette, 5, Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(24.dp)) {
        Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            KRating(store.rating, null, starSize = 16.dp, color = Color.White)
            Label("${store.rating} · 리뷰 ${store.reviews}", 13, FontWeight.SemiBold, Modifier.padding(start = 8.dp), color = Color.White)
        }
    }
}

@Composable
private fun MenuRow(menu: Menu, index: Int, count: Int, onCount: (Int) -> Unit) {
    KSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label(menu.name, 16, FontWeight.SemiBold, Modifier.weight(1f, fill = false))
                    if (menu.best) KStatusBadge("인기", KStatus.Warning, dot = false)
                }
                Label(menu.desc, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.65f))
                Label(won(menu.price), 15, FontWeight.Bold)
            }
            Box(contentAlignment = Alignment.BottomEnd) {
                ArtBox(artPalette(index + 3), index, Modifier.size(92.dp), RoundedCornerShape(14.dp))
                KQuantityStepper(
                    count,
                    onCount,
                    Modifier.offset(x = 6.dp, y = 6.dp),
                    style = KQuantityStyle.Collapsible,
                    range = 0..20,
                    label = menu.name,
                )
            }
        }
    }
}

@Composable
private fun TrackOrder(onBack: () -> Unit, onDone: () -> Unit) {
    var stage by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (stage < 3) {
            delay(2_600)
            stage++
        }
    }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        topBar = { KTopAppBar("주문 현황", onBack = onBack) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val top = padding.calculateTopPadding() + 8.dp
            // 넓은 화면에서는 지도와 진행 정보를 나란히, 좁은 화면에서는 위아래로 놓는다.
            if (maxWidth >= 720.dp) {
                Row(
                    Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = top),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    RiderMap(stage, Modifier.weight(1f).fillMaxSize().padding(bottom = navigationBarBottom() + 24.dp))
                    TrackInfo(
                        stage,
                        onDone,
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(
                            bottom =
                                navigationBarBottom() + 24.dp,
                        ),
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = top,
                        bottom = navigationBarBottom() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    RiderMap(stage, Modifier.fillMaxWidth().height(220.dp))
                    TrackInfo(stage, onDone, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun TrackInfo(stage: Int, onDone: () -> Unit, modifier: Modifier) {
    val faint = LocalKineticaContentColor.current.copy(alpha = 0.65f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        KSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(if (stage == 3) "배달이 끝났어요" else "도착까지", 14, color = faint)
                RollingText(
                    if (stage == 3) "맛있게 드세요" else "약 ${listOf(32, 24, 11, 0)[stage]}분",
                    style = TextStyle(color = LocalKineticaContentColor.current, fontSize = 30.sp, fontWeight = FontWeight.Bold),
                )
                KProgressBar((stage + 1) / 4f, Modifier.fillMaxWidth())
            }
        }
        KSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            KTimeline(
                STAGES.mapIndexed { i, (title, sub) ->
                    KTimelineItem(
                        title,
                        subtitle = sub,
                        time = if (i <= stage) "오후 6:${(12 + i * 9).toString().padStart(2, '0')}" else null,
                        status = when {
                            i < stage || stage == 3 -> KTimelineStatus.Done
                            i == stage -> KTimelineStatus.Current
                            else -> KTimelineStatus.Upcoming
                        },
                    )
                },
                Modifier.padding(20.dp),
            )
        }
        RiderCard()
        if (stage == 3) RateCard(onDone)
    }
}

@Composable
private fun RiderCard() {
    val accent = KineticaTheme.colors.accent
    KSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(accent.copy(alpha = 0.16f), CircleShape), Alignment.Center) {
                ShowcaseIcon(AppIcons.Bike, 24.dp, accent)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Label("라이더 박민호", 15, FontWeight.SemiBold)
                Label("오토바이 · 12가 3456", 13, color = LocalKineticaContentColor.current.copy(alpha = 0.6f))
            }
            KIconButton(ShowcaseIcons.Message, "라이더에게 메시지", {}, variant = KVariant.Tonal)
            Spacer(Modifier.size(8.dp))
            KIconButton(ShowcaseIcons.Phone, "라이더에게 전화", {}, variant = KVariant.Tonal)
        }
    }
}

@Composable
private fun RateCard(onDone: () -> Unit) {
    var stars by rememberSaveable { mutableFloatStateOf(0f) }
    KSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Label("음식은 어떠셨나요?", 16, FontWeight.SemiBold)
            KRating(stars, { stars = it }, starSize = 36.dp)
            KButton("완료", onDone, fullWidth = true, enabled = stars > 0)
        }
    }
}

/** 가게에서 집까지 이어진 길 위로 라이더 핀이 단계마다 미끄러져 간다. */
@Composable
private fun RiderMap(stage: Int, modifier: Modifier) {
    val progress by animateFloatAsState(listOf(0f, 0.1f, 0.65f, 1f)[stage], tween(1_200), label = "rider")
    val accent = KineticaTheme.colors.accent
    val dark = LocalKineticaContentColor.current.isLight()
    BoxWithConstraints(modifier.background(if (dark) Color(0xFF2A2522) else Color(0xFFF3EDE6), RoundedCornerShape(24.dp))) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val road = if (dark) Color(0xFF3D3632) else Color.White
            for (i in 1..4) {
                drawLine(road, Offset(0f, size.height * i / 5f), Offset(size.width, size.height * i / 5f), 14f)
                drawLine(road, Offset(size.width * i / 5f, 0f), Offset(size.width * i / 5f, size.height), 14f)
            }
            drawLine(
                accent.copy(alpha = 0.8f),
                Offset(size.width * 0.15f, size.height * 0.8f),
                Offset(size.width * 0.85f, size.height * 0.2f),
                8f,
                StrokeCap.Round,
                PathEffect.dashPathEffect(floatArrayOf(18f, 14f)),
            )
            drawCircle(accent.copy(alpha = 0.18f), 34f, Offset(size.width * 0.85f, size.height * 0.2f))
            drawCircle(accent, 34f, Offset(size.width * 0.85f, size.height * 0.2f), style = Stroke(3f))
        }
        KMapPin(
            Modifier.offset {
                val x = (w * (0.15f + 0.7f * progress)).roundToPx()
                val y = (h * (0.8f - 0.6f * progress)).roundToPx()
                IntOffset(x - 22.dp.roundToPx(), y - 52.dp.roundToPx())
            },
            icon = AppIcons.Bike,
            contentDescription = "라이더 위치",
        )
        KMapPin(
            Modifier.offset {
                IntOffset((w * 0.15f).roundToPx() - 40.dp.roundToPx(), (h * 0.8f).roundToPx() - 44.dp.roundToPx())
            },
            label = "가게",
            tone = KTone.Neutral,
            dropIn = false,
        )
    }
}

private class Store(
    val name: String,
    val kind: Int,
    val rating: Float,
    val reviews: String,
    val minutes: String,
    val fee: Int,
    val palette: List<Color>,
)
private class Menu(val section: String, val name: String, val desc: String, val price: Int, val best: Boolean = false)

private val KINDS = listOf("전체", "한식", "분식", "양식", "카페·디저트", "아시안")
private val STORES = listOf(
    Store("동네 국밥집", 1, 4.9f, "2,140", "25~35분", 0, listOf(Color(0xFFFFC371), Color(0xFFFF5F6D))),
    Store("바삭 떡볶이", 2, 4.7f, "980", "20~30분", 2_000, listOf(Color(0xFFFF9A8B), Color(0xFFFF6A88))),
    Store("화덕 피자 공방", 3, 4.8f, "1,532", "35~45분", 3_000, listOf(Color(0xFFF6D365), Color(0xFFFDA085))),
    Store("오후의 케이크", 4, 4.9f, "611", "15~25분", 0, listOf(Color(0xFFFBC2EB), Color(0xFFA6C1EE))),
    Store("쌀국수 한 그릇", 5, 4.6f, "874", "25~35분", 1_500, listOf(Color(0xFF84FAB0), Color(0xFF8FD3F4))),
    Store("집밥 도시락", 1, 4.5f, "402", "30~40분", 0, listOf(Color(0xFFFFE259), Color(0xFFFFA751))),
)
private val MENU = listOf(
    Menu("대표 메뉴", "얼큰 순대국밥", "진하게 우린 사골 국물에 순대와 머리고기를 넉넉히", 10_000, best = true),
    Menu("대표 메뉴", "맑은 돼지국밥", "잡내 없이 맑게 끓인 부산식 국밥", 9_500),
    Menu("대표 메뉴", "수육 정식", "부드러운 수육과 국밥, 반찬 세 가지", 15_000, best = true),
    Menu("곁들임", "왕만두 4개", "고기와 김치 반반", 6_000),
    Menu("곁들임", "계란말이", "대파를 듬뿍 넣어 도톰하게", 5_000),
    Menu("음료", "식혜", "직접 달인 살얼음 식혜", 2_500),
)
private val STAGES = listOf(
    "주문 접수" to "가게에서 주문을 확인했어요",
    "조리 중" to "따뜻하게 준비하고 있어요",
    "배달 출발" to "라이더가 음식을 가지고 출발했어요",
    "배달 완료" to "문 앞에 두고 벨을 눌렀어요",
)

internal val DELIVERY_LOOK = AppLook(
    SurfaceStyle.Tonal(Color(0xFFFF7A1A)),
    SurfaceStyle.Tonal(Color(0xFFFF7A1A), dark = true),
    defaultDark = false,
    accent = Color(0xFFF46A00),
    accentDark = Color(0xFFFF9A4D),
    onAccentDark = Color(0xFF2B1300),
)
