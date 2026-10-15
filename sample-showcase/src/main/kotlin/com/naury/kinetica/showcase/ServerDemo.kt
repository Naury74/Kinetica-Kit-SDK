// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naury.kinetica.core.scene.DataValue
import com.naury.kinetica.core.scene.SceneAction
import com.naury.kinetica.core.scene.SceneData
import com.naury.kinetica.player.RemoteKineticaPlayer
import com.naury.kinetica.serialization.RemoteSceneLoader
import com.naury.kinetica.serialization.SceneFetch
import com.naury.kinetica.ui.GlassScaffold
import com.naury.kinetica.ui.GlassSheet
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.GlassToastHost
import com.naury.kinetica.ui.rememberGlassToastState
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
    var version by remember { mutableIntStateOf(0) }
    val loader = remember { RemoteSceneLoader({ SceneFetch.Body(promoJson(version).toByteArray(), etag = "v$version") }) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var sales by remember { mutableDoubleStateOf(1_284_000.0) }
    var showJson by remember { mutableStateOf(false) }
    val toast = rememberGlassToastState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_500)
            sales += Random.nextInt(4_000, 30_000)
        }
    }
    GlassScaffold(
        Modifier.fillMaxSize(),
        style = GlassStyles.Liquid,
        background = { Box(Modifier.fillMaxSize().background(Color(0xFFF3F4F8))) },
        topBar = { DemoTopBar("서버 화면", "서버 JSON으로 그리고, 숫자와 버튼은 앱과 연결됩니다", onBack) },
        bottomBar = {
            Row(Modifier.navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("새 버전 받기", filled = true) {
                    version = (version + 1) % PROMOS.size
                    refreshKey++
                    scope.launch { toast.show("서버에서 새 화면을 받았어요") }
                }
                Pill("JSON 보기") { showJson = true }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            RemoteKineticaPlayer(
                loader,
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                refreshKey = refreshKey,
                data = SceneData.of("sales.today" to DataValue.Number(sales)),
                onAction = { raw ->
                    val action = SceneAction.parse(raw)
                    scope.launch { toast.show("앱이 받은 동작: ${action.name}, filter=${action["filter"]}") }
                },
                autoplay = true,
            )
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
        GlassSheet(showJson, { showJson = false }, title = "서버 JSON") {
            Label("서버가 보낸 JSON입니다. 형식은 docs/34에 정리되어 있어요.", 16)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                BasicText(
                    promoJson(version),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF15161A)),
                )
            }
        }
        GlassToastHost(toast)
    }
}

private class Promo(val title: String, val subtitle: String, val accent: String, val card: String, val button: String)

private val PROMOS = listOf(
    Promo("오늘 매출", "지금까지 들어온 주문", "#5B6CFF", "#FFFFFF", "주문 보기"),
    Promo("가을 신메뉴", "단호박 라떼 출시 기념 매출", "#FF7A59", "#FFF4EC", "신메뉴 주문 보기"),
)

/** 서버가 내려주는 홍보 화면 JSON. 버전마다 문구와 색이 다르다. */
internal fun promoJson(version: Int): String {
    val promo = PROMOS[version % PROMOS.size]
    return """
{
  "schemaVersion": 1,
  "documentId": "promo-v$version",
  "canvas": { "width": 1080, "height": 900, "background": "#F3F4F8" },
  "nodes": [
    {
      "id": "card", "type": "frame",
      "transform": { "x": 40, "y": 40 },
      "bounds": { "width": 1000, "height": 760 },
      "cornerRadius": 56,
      "layout": { "direction": "column", "gap": 28, "padding": [56, 56, 56, 56], "hugHeight": true },
      "children": ["title", "subtitle", "sales", "buy"],
      "style": { "fill": "${promo.card}", "shadows": [{ "color": "#22000000", "x": 0, "y": 16, "blur": 48 }] },
      "effects": { "appear": { "kind": "slideUp" } }
    },
    {
      "id": "title", "type": "text", "parentId": "card",
      "bounds": { "width": 888, "height": 80 },
      "text": "${promo.title}", "fontSize": 64, "fontWeight": 800,
      "style": { "fill": "#15161A" }
    },
    {
      "id": "subtitle", "type": "text", "parentId": "card",
      "bounds": { "width": 888, "height": 56 },
      "text": "${promo.subtitle}", "fontSize": 40,
      "style": { "fill": "#6B7280" }
    },
    {
      "id": "sales", "type": "text", "parentId": "card",
      "bounds": { "width": 888, "height": 150 },
      "text": "-", "fontSize": 120, "fontWeight": 800,
      "bindings": { "text": "sales.today" },
      "numberFormat": { "suffix": "원" },
      "style": { "fill": "${promo.accent}" }
    },
    {
      "id": "buy", "type": "rect", "parentId": "card",
      "bounds": { "width": 888, "height": 150 }, "cornerRadius": 75,
      "style": { "fill": "${promo.accent}" },
      "action": "open_orders?filter=today",
      "effects": { "press": [{ "kind": "scale" }, { "kind": "ripple" }] },
      "accessibility": { "label": "${promo.button}", "role": "button" }
    },
    {
      "id": "buy-label", "type": "text",
      "transform": { "x": 96, "y": 506 },
      "bounds": { "width": 888, "height": 70 },
      "text": "${promo.button}", "fontSize": 48, "fontWeight": 700, "align": "center",
      "style": { "fill": "#FFFFFF" },
      "accessibility": { "hidden": true }
    }
  ],
  "rootOrder": ["card", "buy-label"]
}
    """.trimIndent()
}
