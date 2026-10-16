// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber", "LongParameterList")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.core.scene.DataValue
import com.naury.kinetica.core.scene.Layouts
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneAction
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.core.scene.sizeOrNull
import com.naury.kinetica.player.RemoteKineticaPlayer
import com.naury.kinetica.player.SceneFit
import com.naury.kinetica.render.HOST_VIEW_UNITS_PER_DP
import com.naury.kinetica.serialization.DecodeResult
import com.naury.kinetica.serialization.RemoteSceneLoader
import com.naury.kinetica.serialization.SceneCodec
import com.naury.kinetica.serialization.SceneFetch
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KToastHost
import com.naury.kinetica.ui.KineticaScaffold
import com.naury.kinetica.ui.rememberKToastState
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 서버 화면 데모. 화면을 앱에 넣어 두지 않고 서버가 준 JSON(docs/34)으로 그린다. "새 버전 받기"를 누르면 서버 응답이 바뀌어
 * 앱을 업데이트하지 않아도 화면이 달라진다. 매출 숫자는 앱 데이터에 실시간으로 연결되고, 버튼 동작은 앱이 받아 처리한다.
 */
@Composable
internal fun ServerDemo(onBack: () -> Unit) {
    // 가짜 서버라 버전마다 다른 JSON을 내려준다. 실제 앱에서는 OkHttp 같은 클라이언트로 받는다(docs/integration.md).
    var version by rememberSaveable { mutableIntStateOf(0) }
    val loader = remember { RemoteSceneLoader({ SceneFetch.Body(promoJson(version).toByteArray(), etag = "v$version") }) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var sales by rememberSaveable { mutableDoubleStateOf(1_284_000.0) }
    var orders by rememberSaveable { mutableIntStateOf(265) }
    var showJson by rememberSaveable { mutableStateOf(false) }
    val toast = rememberKToastState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_500)
            sales += Random.nextInt(4_000, 30_000)
            orders += 1
        }
    }
    KineticaScaffold(
        Modifier.fillMaxSize(),
        surface = demoSurface(SERVER_DEMO_LOOK),
        background = { DemoBackdrop { Box(Modifier.fillMaxSize().background(Color(0xFFF3F4F8))) } },
        topBar = { DemoTopBar("서버 화면", "서버 JSON으로 그리고, 숫자와 버튼은 앱과 연결됩니다", onBack) },
        bottomBar = {
            ServerActions(
                onNewVersion = {
                    version = (version + 1) % PROMOS.size
                    refreshKey++
                    scope.launch { toast.show("서버에서 새 화면을 받았어요") }
                },
                onShowJson = { showJson = true },
            )
        },
    ) { padding ->
        val data = SceneData.of(
            "sales.today" to DataValue.Number(sales),
            "orders.count" to DataValue.Number(orders.toDouble()),
            "orders.average" to DataValue.Number((sales / orders / 100).roundToInt() * 100.0),
        )
        PromoArea(loader, version, refreshKey, data, padding) { raw ->
            val action = SceneAction.parse(raw)
            scope.launch { toast.show("앱이 받은 동작: ${action.name}, filter=${action["filter"]}") }
        }
        JsonSheet(showJson, version) { showJson = false }
        KToastHost(toast)
    }
}

@Composable
private fun ServerActions(onNewVersion: () -> Unit, onShowJson: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(16.dp).widthIn(max = READING_WIDTH).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("새 버전 받기", Modifier.weight(1f), filled = true, onClick = onNewVersion)
            Pill("JSON 보기", Modifier.weight(1f), onClick = onShowJson)
        }
    }
}

/** 장면은 화면 폭에 맞춰 펼치고(Responsive), 내용이 화면보다 길면 스크롤한다. 넓은 화면에서는 상단 바와 같은 폭으로 가운데 둔다. */
@Composable
private fun PromoArea(
    loader: RemoteSceneLoader,
    version: Int,
    refreshKey: Int,
    data: SceneData,
    padding: PaddingValues,
    onAction: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        val top = padding.calculateTopPadding()
        val bottom = padding.calculateBottomPadding()
        val width = minOf(maxWidth, READING_WIDTH + 32.dp)
        val content = remember(version, width) { promoHeight(version, width) }
        val viewport = maxHeight - top - bottom
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(top))
            RemoteKineticaPlayer(
                loader,
                Modifier.width(width).height(maxOf(viewport, content)),
                refreshKey = refreshKey,
                data = data,
                onAction = onAction,
                fit = SceneFit.Responsive,
                autoplay = true,
            )
            Spacer(Modifier.height(bottom))
        }
    }
}

@Composable
private fun JsonSheet(show: Boolean, version: Int, onDismiss: () -> Unit) {
    KSheet(show, onDismiss, title = "서버 JSON") {
        Label("서버가 보낸 JSON입니다. 형식은 docs/34에 정리되어 있어요.", 16)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            BasicText(
                promoJson(version),
                // 코드라 RTL 화면에서도 왼쪽에서 오른쪽으로 읽는다.
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF15161A),
                    textDirection = TextDirection.Ltr,
                ),
            )
        }
    }
}

/** 폭 [width]에 맞춰 펼친 홍보 장면의 내용 높이. 재생기 영역이 이보다 낮으면 스크롤해서 끝까지 볼 수 있게 한다. */
private fun promoHeight(version: Int, width: Dp): Dp {
    val document = (SceneCodec().decode(promoJson(version)) as? DecodeResult.Success)?.document ?: return 0.dp
    val units = HOST_VIEW_UNITS_PER_DP.toDouble()
    val fitted = Layouts.resizeArtboard(document, width.value * units, document.artboard.height)
    val page = fitted.nodes[NodeId("page")]?.content?.sizeOrNull() ?: return 0.dp
    return (page.height / units).dp
}

private class Promo(
    val title: String,
    val subtitle: String,
    val accent: String,
    val card: String,
    val button: String,
    val menuTitle: String,
    val menu: List<Pair<String, Int>>,
)

private val PROMOS = listOf(
    Promo(
        "오늘 매출",
        "지금까지 들어온 주문",
        "#5B6CFF",
        "#FFFFFF",
        "주문 보기",
        "많이 팔린 메뉴",
        listOf("아메리카노" to 128, "카페 라떼" to 96, "치즈 케이크" to 41),
    ),
    Promo(
        "가을 신메뉴",
        "단호박 라떼 출시 기념 매출",
        "#FF7A59",
        "#FFF4EC",
        "신메뉴 주문 보기",
        "신메뉴 반응",
        listOf("단호박 라떼" to 87, "밤 크림 라떼" to 52, "고구마 스콘" to 33),
    ),
)

/**
 * 서버가 내려주는 홍보 화면 JSON. 버전마다 문구와 색이 다르다. 맨 바깥 Frame은 가로로 늘어나는 제약(`both`)과 자동 레이아웃을 써서
 * 재생기를 [com.naury.kinetica.player.SceneFit.Responsive]로 두면 화면 폭에 맞게 펼쳐지고, 높이는 내용에 맞춰진다.
 * [heroOnly]면 매출 카드만 담는다. XML 화면의 좁은 배치에서 쓴다.
 * [transparent]면 바탕을 칠하지 않고 카드만 그린다. XML 화면은 다크 모드에서 바탕이 어두워지기 때문이다.
 */
internal fun promoJson(version: Int, heroOnly: Boolean = false, transparent: Boolean = heroOnly): String {
    val promo = PROMOS[version % PROMOS.size]
    val canvas = if (transparent) "#00000000" else "#F3F4F8"
    val pageChildren = if (heroOnly) "\"card\"" else "\"card\", \"stats\", \"menu\""
    val nodes = buildList {
        addHero(promo, pageChildren)
        if (!heroOnly) addExtras(promo)
    }
    return """
{
  "schemaVersion": 1,
  "documentId": "promo-v$version",
  "canvas": { "width": 1080, "height": 900, "background": "$canvas" },
  "nodes": [${nodes.joinToString(",")}
  ],
  "rootOrder": ["page"]
}
    """.trimIndent()
}

// 버튼 글자는 버튼 Frame이 이름을 읽어 주므로 스크린 리더가 한 번 더 읽지 않게 숨긴다.
private const val LABEL_EXTRA = """"align": "center", "accessibility": { "hidden": true }, """

/** 맨 바깥 page Frame과 매출 카드(제목, 설명, 금액, 버튼) 노드 */
private fun MutableList<String>.addHero(promo: Promo, pageChildren: String) {
    add(
        """
{
  "id": "page", "type": "frame",
  "bounds": { "width": 1080, "height": 900 },
  "constraints": { "h": "both", "v": "start" },
  "layout": { "direction": "column", "gap": 32, "padding": [44, 24, 44, 48], "cross": "stretch", "hugHeight": true },
  "children": [$pageChildren]
}""",
    )
    add(
        """
{
  "id": "card", "type": "frame", "parentId": "page",
  "bounds": { "width": 992, "height": 600 },
  "cornerRadius": 56,
  "layout": { "direction": "column", "gap": 20, "padding": [56, 56, 56, 56], "cross": "stretch", "hugHeight": true },
  "children": ["title", "subtitle", "sales", "buy"],
  "style": { "fill": "${promo.card}", "shadows": [{ "color": "#1A000000", "x": 0, "y": 12, "blur": 40 }] },
  "effects": { "appear": { "kind": "slideUp" } }
}""",
    )
    add(text("title", "card", promo.title, 64, 800, "#15161A", 80))
    add(text("subtitle", "card", promo.subtitle, 40, 400, "#6B7280", 56))
    add(
        text(
            "sales",
            "card",
            "-",
            112,
            800,
            promo.accent,
            150,
            SALES_BINDING,
        ),
    )
    add(
        """
{
  "id": "buy", "type": "frame", "parentId": "card",
  "bounds": { "width": 880, "height": 140 }, "cornerRadius": 70,
  "layout": { "direction": "row", "main": "center", "cross": "center" },
  "children": ["buy-label"],
  "style": { "fill": "${promo.accent}" },
  "action": "open_orders?filter=today",
  "effects": { "press": [{ "kind": "scale" }, { "kind": "ripple" }] },
  "accessibility": { "label": "${promo.button}", "role": "button" }
}""",
    )
    add(
        text("buy-label", "buy", promo.button, 46, 700, "#FFFFFF", 64, LABEL_EXTRA, width = 800),
    )
}

/** 매출 카드 아래의 숫자 카드 두 개와 메뉴 순위 카드 노드 */
private fun MutableList<String>.addExtras(promo: Promo) {
    add(
        """
{
  "id": "stats", "type": "frame", "parentId": "page",
  "bounds": { "width": 992, "height": 240 },
  "layout": { "direction": "row", "gap": 32, "cross": "stretch", "hugHeight": true },
  "children": ["orders", "average"],
  "effects": { "appear": { "kind": "slideUp", "delayMs": 80 } }
}""",
    )
    add(statCard("orders"))
    add(text("orders-label", "orders", "주문 수", 38, 500, "#6B7280", 50))
    add(
        text(
            "orders-value",
            "orders",
            "-",
            72,
            800,
            "#15161A",
            96,
            """"bindings": { "text": "orders.count" }, "numberFormat": { "suffix": "건" }, """,
        ),
    )
    add(statCard("average"))
    add(text("average-label", "average", "평균 객단가", 38, 500, "#6B7280", 50))
    add(
        text(
            "average-value",
            "average",
            "-",
            72,
            800,
            "#15161A",
            96,
            """"bindings": { "text": "orders.average" }, "numberFormat": { "suffix": "원" }, """,
        ),
    )
    addMenu(promo)
}

/** 메뉴 순위 카드 노드. 줄마다 순위, 이름, 판매 수를 한 줄로 놓는다. */
private fun MutableList<String>.addMenu(promo: Promo) {
    val rows = promo.menu.indices.joinToString(", ") { "\"menu-$it\"" }
    add(
        """
{
  "id": "menu", "type": "frame", "parentId": "page",
  "bounds": { "width": 992, "height": 460 },
  "cornerRadius": 56,
  "layout": { "direction": "column", "gap": 8, "padding": [56, 48, 56, 40], "cross": "stretch", "hugHeight": true },
  "children": ["menu-title", $rows],
  "style": { "fill": "#FFFFFF", "shadows": [{ "color": "#14000000", "x": 0, "y": 8, "blur": 32 }] },
  "effects": { "appear": { "kind": "slideUp", "delayMs": 160 } }
}""",
    )
    add(text("menu-title", "menu", promo.menuTitle, 48, 700, "#15161A", 76))
    promo.menu.forEachIndexed { index, (name, count) ->
        add(
            """
{
  "id": "menu-$index", "type": "frame", "parentId": "menu",
  "bounds": { "width": 880, "height": 100 },
  "layout": { "direction": "row", "gap": 24, "cross": "center" },
  "children": ["menu-$index-rank", "menu-$index-name", "menu-$index-count"]
}""",
        )
        add(text("menu-$index-rank", "menu-$index", "${index + 1}", 42, 800, promo.accent, 56, width = 56))
        add(text("menu-$index-name", "menu-$index", name, 42, 500, "#15161A", 56, """"fill": true, """, width = 500))
        add(text("menu-$index-count", "menu-$index", "${count}잔", 40, 600, "#6B7280", 56, """"align": "end", """, width = 220))
    }
}

private const val SALES_BINDING = """"bindings": { "text": "sales.today" }, "numberFormat": { "suffix": "원" }, """

private fun statCard(id: String): String = """
    {
      "id": "$id", "type": "frame", "parentId": "stats", "fill": true,
      "bounds": { "width": 480, "height": 240 },
      "cornerRadius": 48,
      "layout": { "direction": "column", "gap": 12, "padding": [44, 44, 44, 44], "cross": "stretch", "hugHeight": true },
      "children": ["$id-label", "$id-value"],
      "style": { "fill": "#FFFFFF", "shadows": [{ "color": "#14000000", "x": 0, "y": 8, "blur": 32 }] }
    }"""

@Suppress("LongParameterList")
private fun text(
    id: String,
    parent: String,
    text: String,
    size: Int,
    weight: Int,
    color: String,
    height: Int,
    extra: String = "",
    width: Int = 880,
): String = """
    {
      "id": "$id", "type": "text", "parentId": "$parent",
      "bounds": { "width": $width, "height": $height },
      "text": "$text", "fontSize": $size, "fontWeight": $weight, $extra
      "style": { "fill": "$color" }
    }"""

private val SERVER_DEMO_LOOK = DemoLook(GlassStyles.Liquid, GlassStyles.LiquidDark, defaultDark = false)
