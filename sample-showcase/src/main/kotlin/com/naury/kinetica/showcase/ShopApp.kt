// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
// 예시 데이터 클래스는 필드를 생성자에 나란히 두는 편이 읽기 쉬워 인자가 많다.
@file:Suppress("MagicNumber", "LongParameterList")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KButton
import com.naury.kinetica.ui.KButtonSize
import com.naury.kinetica.ui.KCard
import com.naury.kinetica.ui.KCarousel
import com.naury.kinetica.ui.KChipGroup
import com.naury.kinetica.ui.KEmptyIcon
import com.naury.kinetica.ui.KEmptyIconKind
import com.naury.kinetica.ui.KEmptyState
import com.naury.kinetica.ui.KExpandable
import com.naury.kinetica.ui.KFlyToHost
import com.naury.kinetica.ui.KFlyToState
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KPageIndicator
import com.naury.kinetica.ui.KPriceSize
import com.naury.kinetica.ui.KPriceTag
import com.naury.kinetica.ui.KQuantityStepper
import com.naury.kinetica.ui.KRadioRow
import com.naury.kinetica.ui.KRating
import com.naury.kinetica.ui.KResult
import com.naury.kinetica.ui.KResultDialog
import com.naury.kinetica.ui.KSearchBar
import com.naury.kinetica.ui.KSelectableCard
import com.naury.kinetica.ui.KShapeToken
import com.naury.kinetica.ui.KSlideToConfirm
import com.naury.kinetica.ui.KStatus
import com.naury.kinetica.ui.KStepIndicator
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KToastAction
import com.naury.kinetica.ui.KToaster
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KTopAppBar
import com.naury.kinetica.ui.KValueRow
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LikeButton
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.PushNavigation
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.SwipeAction
import com.naury.kinetica.ui.SwipeActionsRow
import com.naury.kinetica.ui.flyToSource
import com.naury.kinetica.ui.flyToTarget
import com.naury.kinetica.ui.kBadge
import com.naury.kinetica.ui.kClickable
import com.naury.kinetica.ui.rememberKFlyToState
import com.naury.kinetica.ui.rememberKToasterState
import com.naury.kinetica.ui.rememberSwipeActionsGroup
import com.naury.kinetica.ui.rememberSwipeActionsState

/**
 * 쇼핑 앱: 상품 목록, 상세, 장바구니, 결제 네 화면. 흰 카드에 머리카락 두께 테두리를 두른 단색 표면으로, 사진이 주인공인 쇼핑몰 느낌을 낸다.
 * 상세에서 담으면 상품 그림이 위쪽 장바구니 아이콘으로 날아가고, 결제를 마치면 장바구니가 비워진다.
 */
@Composable
internal fun ShopApp(onBack: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf("home")) }
    val cart = rememberIntMap()
    val liked = rememberIntMap()
    val pop = { stack = stack.dropLast(1) }
    val open = { screen: String -> stack = stack.filterNot { it == screen } + screen }
    AppTheme(SHOP_LOOK) {
        val fly = rememberKFlyToState()
        val toaster = rememberKToasterState()
        KFlyToHost(fly, Modifier.fillMaxSize()) {
            PushNavigation(stack, onBack = pop) { screen ->
                val cartButton: @Composable () -> Unit = {
                    KIconButton(
                        KIcons.Bag,
                        "장바구니, ${cart.values.sum()}개",
                        { open("cart") },
                        Modifier.flyToTarget(fly).kBadge(cart.values.sum().takeIf { it > 0 }),
                        variant = KVariant.Text,
                        tone = KTone.Neutral,
                    )
                }
                when {
                    screen == "home" -> ShopHome(onBack, cartButton, liked) { open("product/$it") }

                    screen.startsWith("product/") -> {
                        val product = PRODUCTS.first { it.id == screen.substringAfter('/').toInt() }
                        ProductDetail(product, liked, pop, cartButton, fly) { count ->
                            cart[product.id] = (cart[product.id] ?: 0) + count
                            toaster.show(
                                "장바구니에 담았어요",
                                KStatus.Success,
                                description = "${product.name} ${count}개",
                                action = KToastAction("보기") { open("cart") },
                            )
                        }
                    }

                    screen == "cart" -> CartScreen(cart, pop, onCheckout = { open("checkout") }) { open("product/$it") }

                    else -> CheckoutScreen(cart, pop) {
                        cart.clear()
                        stack = listOf("home")
                    }
                }
            }
            KToaster(toaster, Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun ShopHome(onBack: () -> Unit, cartButton: @Composable () -> Unit, liked: SnapshotStateMap<Int, Int>, onOpen: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableIntStateOf(0) }
    val shown = PRODUCTS.filter {
        (category == 0 || it.category == category) && (query.isBlank() || query.trim() in it.name || query.trim() in it.brand)
    }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { DemoBackdrop() },
        topBar = { KTopAppBar("오늘의 발견", onBack = onBack, actions = { cartButton() }) },
    ) { padding ->
        LazyVerticalGrid(
            GridCells.Adaptive(164.dp),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = appPadding(16.dp, SHOP_WIDTH),
                end = appPadding(16.dp, SHOP_WIDTH),
                top = padding.calculateTopPadding() + 4.dp,
                bottom = navigationBarBottom() + 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KSearchBar(query, { query = it }, {}, placeholder = "상품, 브랜드 검색")
                    KChipGroup(
                        CATEGORIES,
                        setOf(category),
                        { category = it.firstOrNull() ?: 0 },
                        wrap = false,
                        variant = KVariant.Outlined,
                    )
                    if (category == 0 && query.isBlank()) SaleBanner()
                }
            }
            items(shown, key = { it.id }) { product ->
                ProductTile(product, liked[product.id] == 1, { liked[product.id] = if (it) 1 else 0 }) { onOpen(product.id) }
            }
            if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    KEmptyState(
                        "찾는 상품이 없어요",
                        Modifier.padding(top = 32.dp),
                        message = "다른 검색어나 카테고리로 찾아보세요.",
                        art = { KEmptyIcon(KEmptyIconKind.NoResults, Modifier.size(72.dp)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SaleBanner() {
    ArtBox(
        listOf(Color(0xFF1F1F1F), Color(0xFF4A4A4A)),
        seed = 3,
        modifier = Modifier.fillMaxWidth().height(132.dp),
        shape = kitShape { KShapeToken.Large },
    ) {
        Column(Modifier.align(Alignment.CenterStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("가을 신상 최대 40%", 20, FontWeight.Bold, color = Color.White)
            Label("이번 주 일요일까지, 3만 원 이상 무료 배송", 13, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun ProductTile(product: Product, liked: Boolean, onLike: (Boolean) -> Unit, onClick: () -> Unit) {
    val ink = LocalKineticaContentColor.current
    Column(
        Modifier.clip(kitShape { KShapeToken.Large }).kClickable(kitShape { KShapeToken.Large }, onClickLabel = "상세 보기", onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            ArtBox(product.palette, product.id, Modifier.fillMaxWidth().aspectRatio(0.9f), kitShape { KShapeToken.Large })
            LikeButton(liked, onLike, Modifier.align(Alignment.TopEnd).padding(4.dp), iconSize = 22.dp)
        }
        Label(product.brand, 12, FontWeight.SemiBold, Modifier.padding(top = 4.dp, start = 2.dp), color = ink.copy(alpha = 0.6f))
        Label(product.name, 14, modifier = Modifier.padding(horizontal = 2.dp))
        KPriceTag(
            won(product.price),
            Modifier.padding(horizontal = 2.dp),
            discountPercent = product.discount.takeIf { it > 0 },
            size = KPriceSize.Small,
        )
        Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            ShowcaseIcon(KIcons.Star, 13.dp, Color(0xFFFFB800))
            Label("${product.rating} (${product.reviews})", 12, modifier = Modifier.padding(start = 3.dp), color = ink.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun ProductDetail(
    product: Product,
    liked: SnapshotStateMap<Int, Int>,
    onBack: () -> Unit,
    cartButton: @Composable () -> Unit,
    fly: KFlyToState,
    onAdd: (Int) -> Unit,
) {
    val choice = rememberSaveable(saver = Choice.Saver) { Choice() }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { DemoBackdrop() },
        topBar = { KTopAppBar(product.brand, onBack = onBack, actions = { cartButton() }) },
        bottomBar = {
            BuyBar(product.price * choice.count) {
                fly.launch(product.id, onArrive = { onAdd(choice.count) }) {
                    ArtBox(product.palette, product.id, Modifier.size(64.dp), kitShape { KShapeToken.Medium })
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val scroll = rememberScrollState()
            val top = padding.calculateTopPadding() + 8.dp
            val bottom = padding.calculateBottomPadding() + 24.dp
            // 넓은 화면에서는 사진과 정보를 나란히, 좁은 화면에서는 위아래로 놓는다.
            if (maxWidth >= 720.dp) {
                Row(
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 24.dp, end = 24.dp, top = top, bottom = bottom),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    Gallery(product, choice.color, fly, peek = 0.dp, Modifier.weight(1f))
                    ProductInfo(product, liked, choice, Modifier.weight(1f))
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = bottom),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Gallery(product, choice.color, fly, peek = 24.dp, Modifier.fillMaxWidth())
                    ProductInfo(product, liked, choice, Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

/** 상세 화면에서 고른 색, 사이즈, 수량. 회전하거나 접었다 펴도 남는다. */
@Stable
private class Choice(size: Int = 1, color: Int = 0, count: Int = 1) {
    var size by mutableIntStateOf(size)
    var color by mutableIntStateOf(color)
    var count by mutableIntStateOf(count)

    companion object {
        val Saver = listSaver<Choice, Int>({ listOf(it.size, it.color, it.count) }, { Choice(it[0], it[1], it[2]) })
    }
}

/** 상품 사진 넘기기. 첫 장은 담을 때 장바구니로 날아가는 그림의 출발점이다. */
@Composable
private fun Gallery(product: Product, color: Int, fly: KFlyToState, peek: Dp, modifier: Modifier) {
    val pages = 4
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val pager = rememberPagerState { pages }
        KCarousel(pages, Modifier.fillMaxWidth(), peek = peek, state = pager) { page ->
            ArtBox(
                if (page == 0) product.palette else artPalette(product.id + page + color),
                product.id + page,
                Modifier.fillMaxWidth().aspectRatio(1f).let { if (page == 0) it.flyToSource(fly, product.id) else it },
                kitShape { card },
            )
        }
        KPageIndicator(pager, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ProductInfo(product: Product, liked: SnapshotStateMap<Int, Int>, choice: Choice, modifier: Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(product.name, 22, FontWeight.Bold)
                KPriceTag(
                    won(product.price),
                    originalPrice = product.originalPrice?.let(::won),
                    discountPercent = product.discount.takeIf { it > 0 },
                    size = KPriceSize.Large,
                )
            }
            LikeButton(liked[product.id] == 1, { liked[product.id] = if (it) 1 else 0 })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KRating(product.rating, null, starSize = 18.dp)
            Label("${product.rating} · 리뷰 ${product.reviews}개", 13, color = LocalKineticaContentColor.current.copy(alpha = 0.6f))
        }
        Label("색상", 15, FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SWATCHES.forEachIndexed { index, (name, color) -> Swatch(name, color, index == choice.color) { choice.color = index } }
        }
        Label("사이즈", 15, FontWeight.SemiBold)
        KChipGroup(SIZES, setOf(choice.size), { choice.size = it.firstOrNull() ?: choice.size }, variant = KVariant.Outlined)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("수량", 15, FontWeight.SemiBold, Modifier.weight(1f))
            KQuantityStepper(choice.count, { choice.count = it }, range = 1..9)
        }
        KExpandable(open, { open = it }, header = { Label("상품 정보", 15, FontWeight.SemiBold) }) {
            Label(
                "부드러운 기모 안감을 덧대 가을부터 초겨울까지 입기 좋습니다. 어깨선이 살짝 내려온 여유 있는 핏이라 " +
                    "평소 사이즈로 고르시면 됩니다. 찬물 단독 손세탁을 권합니다.",
                14,
            )
        }
        KValueRow("배송", "내일(목) 도착 예정")
    }
}

@Composable
private fun Swatch(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val ink = LocalKineticaContentColor.current
    Box(
        Modifier
            .size(40.dp)
            .border(2.dp, if (selected) ink else Color.Transparent, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, ink.copy(alpha = 0.12f), CircleShape)
            .kClickable(CircleShape, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name + if (selected) ", 선택됨" else "" },
    )
}

@Composable
private fun BuyBar(total: Int, onAdd: () -> Unit) {
    KSurface(Modifier.fillMaxWidth(), shape = kitTopShape()) {
        // 큰 글자에서는 금액과 버튼이 한 줄에 들어가지 않으므로 버튼이 다음 줄로 내려간다.
        FlowRow(
            Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.padding(end = 16.dp)) {
                Label("총 금액", 12, color = LocalKineticaContentColor.current.copy(alpha = 0.6f))
                Label(won(total), 19, FontWeight.Bold)
            }
            KButton("장바구니 담기", onAdd, icon = KIcons.Bag, size = KButtonSize.Large)
        }
    }
}

@Composable
private fun CartScreen(cart: SnapshotStateMap<Int, Int>, onBack: () -> Unit, onCheckout: () -> Unit, onOpen: (Int) -> Unit) {
    val lines = PRODUCTS.filter { (cart[it.id] ?: 0) > 0 }
    val total = lines.sumOf { it.price * (cart[it.id] ?: 0) }
    val shipping = shippingFor(total)
    val group = rememberSwipeActionsGroup()
    val destructive = KineticaTheme.colors.destructive
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { DemoBackdrop() },
        topBar = { KTopAppBar("장바구니", onBack = onBack) },
        bottomBar = {
            if (lines.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp), Alignment.Center) {
                    KButton(
                        "${won(total + shipping)} 주문하기",
                        onCheckout,
                        Modifier.widthIn(max = SHOP_WIDTH),
                        size = KButtonSize.Large,
                        fullWidth = true,
                    )
                }
            }
        },
    ) { padding ->
        if (lines.isEmpty()) {
            EmptyCart(onBack)
            return@KineticaScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = appPadding(16.dp, SHOP_WIDTH),
                end = appPadding(16.dp, SHOP_WIDTH),
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(lines, key = { it.id }) { product ->
                SwipeActionsRow(
                    listOf(SwipeAction("삭제", destructive, removesRow = true) { cart.remove(product.id) }),
                    Modifier.animateItem(),
                    state = rememberSwipeActionsState(group),
                    shape = kitShape { KShapeToken.Large },
                ) { CartLine(product, cart, onOpen) }
            }
            item("summary") { CartSummary(total, shipping) }
            item("hint") {
                Label(
                    "왼쪽으로 밀어 상품을 뺄 수 있어요.",
                    12,
                    modifier = Modifier.padding(start = 4.dp),
                    color = LocalKineticaContentColor.current.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun EmptyCart(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        KEmptyState(
            "장바구니가 비어 있어요",
            Modifier.padding(24.dp),
            message = "마음에 드는 상품을 담아 보세요.",
            art = { KEmptyIcon(KEmptyIconKind.EmptyInbox, Modifier.size(72.dp)) },
            action = { KButton("둘러보기", onBack, variant = KVariant.Tonal) },
        )
    }
}

@Composable
private fun CartLine(product: Product, cart: SnapshotStateMap<Int, Int>, onOpen: (Int) -> Unit) {
    KCard(Modifier.fillMaxWidth(), onClick = { onOpen(product.id) }, shape = kitShape { KShapeToken.Large }, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtBox(product.palette, product.id, Modifier.size(72.dp), kitInnerShape(12.dp) { KShapeToken.Large })
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(product.brand, 12, color = LocalKineticaContentColor.current.copy(alpha = 0.6f))
                Label(product.name, 14, FontWeight.SemiBold)
                Label(won(product.price * (cart[product.id] ?: 0)), 15, FontWeight.Bold)
            }
            KQuantityStepper(cart[product.id] ?: 1, { cart[product.id] = it }, range = 1..9)
        }
    }
}

@Composable
private fun CartSummary(total: Int, shipping: Int) {
    KSurface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = kitShape { KShapeToken.Large }) {
        Column(Modifier.padding(vertical = 6.dp)) {
            KValueRow("상품 금액", won(total))
            KValueRow(
                "배송비",
                if (shipping == 0) "무료" else won(shipping),
                subtitle = if (shipping > 0) "${won(FREE_SHIPPING - total)} 더 담으면 무료" else null,
            )
        }
    }
}

@Composable
private fun CheckoutScreen(cart: SnapshotStateMap<Int, Int>, onBack: () -> Unit, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var delivery by rememberSaveable { mutableIntStateOf(0) }
    var payment by rememberSaveable { mutableIntStateOf(0) }
    var paid by rememberSaveable { mutableStateOf(false) }
    val total = PRODUCTS.sumOf { it.price * (cart[it.id] ?: 0) }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        systemBarIcons = false,
        background = { DemoBackdrop() },
        topBar = { KTopAppBar("주문하기", onBack = { if (step > 0) step-- else onBack() }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                start = appPadding(20.dp, SHOP_WIDTH),
                end = appPadding(20.dp, SHOP_WIDTH),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = navigationBarBottom() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KStepIndicator(listOf("배송", "결제", "확인"), step, Modifier.fillMaxWidth(), onStepClick = { if (it < step) step = it })
            when (step) {
                0 -> DeliveryStep(delivery, { delivery = it }) { step = 1 }

                1 -> PaymentStep(payment, { payment = it }) { step = 2 }

                else -> {
                    KSurface(Modifier.fillMaxWidth(), shape = kitShape { KShapeToken.Large }) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            KValueRow("배송", DELIVERY[delivery].first)
                            KValueRow("결제", PAYMENTS[payment].first)
                            KValueRow("결제 금액", won(total + shippingFor(total)))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    KSlideToConfirm(if (paid) "결제 완료" else "밀어서 결제하기", { paid = true }, Modifier.fillMaxWidth(), confirmed = paid)
                }
            }
        }
    }
    KResultDialog(
        paid,
        KResult.Success,
        "주문이 완료되었어요",
        onDismissRequest = onDone,
        message = "내일 도착하도록 준비할게요. 배송이 시작되면 알려 드릴게요.",
        confirmText = "확인",
    )
}

@Composable
private fun DeliveryStep(selected: Int, onSelect: (Int) -> Unit, onNext: () -> Unit) {
    KSurface(Modifier.fillMaxWidth(), shape = kitShape { KShapeToken.Large }) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("집", 15, FontWeight.SemiBold)
            Label("서울시 마포구 하늘길 24, 302호", 14)
            Label("김하늘 · 010-1234-5678", 13, color = LocalKineticaContentColor.current.copy(alpha = 0.6f))
        }
    }
    Label("배송 방법", 15, FontWeight.SemiBold)
    KSurface(Modifier.fillMaxWidth(), shape = kitShape { KShapeToken.Large }) {
        Column {
            DELIVERY.forEachIndexed { index, (title, sub) -> KRadioRow(title, selected == index, { onSelect(index) }, subtitle = sub) }
        }
    }
    KButton("다음", onNext, size = KButtonSize.Large, fullWidth = true)
}

@Composable
private fun PaymentStep(selected: Int, onSelect: (Int) -> Unit, onNext: () -> Unit) {
    Label("결제 수단", 15, FontWeight.SemiBold)
    PAYMENTS.forEachIndexed { index, (title, sub) ->
        KSelectableCard(selected == index, {
            onSelect(index)
        }, Modifier.fillMaxWidth(), role = Role.RadioButton, variant = KVariant.Outlined) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShowcaseIcon(if (index == 0) KIcons.Card else AppIcons.Gift, 22.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Label(title, 15, FontWeight.SemiBold)
                    Label(sub, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.6f))
                }
            }
        }
    }
    KButton("다음", onNext, size = KButtonSize.Large, fullWidth = true)
}

/** 3만 원 이상이면 배송비가 없다. 비어 있어도 0이다. */
private fun shippingFor(total: Int) = if (total >= FREE_SHIPPING || total == 0) 0 else 3_000

private const val FREE_SHIPPING = 30_000

private class Product(
    val id: Int,
    val brand: String,
    val name: String,
    val price: Int,
    val discount: Int,
    val rating: Float,
    val reviews: Int,
    val category: Int,
) {
    val palette get() = artPalette(id)

    /** 할인 전 가격. 천 원 단위로 반올림한다. 할인이 없으면 null. */
    val originalPrice: Int? get() = if (discount > 0) (price * 100 / (100 - discount) + 500) / 1000 * 1000 else null
}

private val CATEGORIES = listOf("전체", "아우터", "상의", "신발", "가방", "생활")
private val PRODUCTS = listOf(
    Product(1, "모노웨어", "울 블렌드 싱글 코트", 189_000, 30, 4.8f, 1240, 1),
    Product(2, "하루공방", "기모 오버핏 맨투맨", 39_000, 0, 4.6f, 856, 2),
    Product(3, "스텝앤런", "데일리 러닝화", 89_000, 15, 4.7f, 2310, 3),
    Product(4, "모노웨어", "코듀로이 셔츠 재킷", 79_000, 20, 4.5f, 412, 1),
    Product(5, "포켓랩", "나일론 미니 숄더백", 54_000, 0, 4.9f, 978, 4),
    Product(6, "하루공방", "니트 머플러", 25_000, 10, 4.4f, 233, 2),
    Product(7, "결무늬", "세라믹 머그 2개 세트", 22_000, 0, 4.8f, 1530, 5),
    Product(8, "스텝앤런", "캔버스 스니커즈", 59_000, 25, 4.3f, 645, 3),
    Product(9, "포켓랩", "데일리 백팩", 98_000, 0, 4.7f, 1102, 4),
    Product(10, "결무늬", "린넨 쿠션 커버", 18_000, 0, 4.6f, 389, 5),
)
private val SIZES = listOf("S", "M", "L", "XL")
private val SWATCHES = listOf(
    "차콜" to Color(0xFF3A3A3C),
    "오트밀" to Color(0xFFE6DCCB),
    "올리브" to Color(0xFF7A7F4E),
    "버건디" to Color(0xFF7D2638),
)
private val DELIVERY = listOf("일반 배송" to "내일(목) 도착 · 무료", "새벽 배송" to "내일 오전 7시 전 도착 · 2,500원", "편의점 픽업" to "모레부터 찾을 수 있어요")
private val PAYMENTS = listOf("신용카드" to "하늘카드 ····4821 · 일시불", "적립금" to "사용 가능 12,400원")

private val SHOP_WIDTH = 960.dp

internal val SHOP_LOOK = AppLook(
    SurfaceStyles.CleanLight,
    SurfaceStyles.CleanDark,
    defaultDark = false,
    accent = Color(0xFF111111),
    accentDark = Color(0xFFF2F2F2),
    onAccentDark = Color(0xFF111111),
)
