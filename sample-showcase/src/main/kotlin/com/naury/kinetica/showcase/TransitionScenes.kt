// 장면 배치 좌표·크기·색은 시연용 데이터라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.serialization.DecodeResult
import com.naury.kinetica.serialization.RemoteSceneBundleLoader
import com.naury.kinetica.serialization.SceneBundleCodec
import com.naury.kinetica.serialization.SceneCodec
import com.naury.kinetica.serialization.SceneFetch

/**
 * 장면 전환 데모가 재생하는 서버 번들. 홈, 상품, 결제 세 화면이 한 번들에 들어 있고, 버튼의 `action`이 `go?scene=…`로 다음 화면을 가리킨다.
 * 세 화면 모두 상품 그림 노드 id가 `hero`라 morph 전환에서 그림이 이어진다.
 */
internal object TransitionScenes {
    const val HOME = "home"
    const val PRODUCT = "product"
    const val CHECKOUT = "checkout"

    /** 화면 id 순서. 진행도 슬라이더에서 고르는 두 화면 쌍도 이 순서를 따른다. */
    val order: List<String> = listOf(HOME, PRODUCT, CHECKOUT)

    /** 화면 id별 이름. 위치 표시에 쓴다. */
    val names: Map<String, String> = mapOf(HOME to "홈", PRODUCT to "상품", CHECKOUT to "결제")

    /** 화면 id별 JSON. 실제 서버라면 이 묶음을 [SceneBundleCodec] 형식 하나로 내려준다. */
    val json: Map<String, String> = mapOf(HOME to homeJson(), PRODUCT to productJson(), CHECKOUT to checkoutJson())

    /** 읽은 문서. 데모 데이터라 읽지 못하면 바로 실패하게 둔다. */
    val documents: Map<String, SceneDocument> by lazy {
        val codec = SceneCodec()
        json.mapValues { (id, text) ->
            when (val result = codec.decode(text)) {
                is DecodeResult.Success -> result.document
                is DecodeResult.Failure -> error("$id 장면을 읽지 못했습니다: ${result.error}")
            }
        }
    }

    /** 네트워크 없이 같은 번들을 돌려주는 로더. 처음부터 화면이 보이도록 같은 문서를 기본값으로도 넣는다. */
    fun loader(): RemoteSceneBundleLoader {
        val bytes = SceneBundleCodec().encode(documents).toByteArray()
        return RemoteSceneBundleLoader({ SceneFetch.Body(bytes, etag = "bundle-v1") }, fallback = documents)
    }
}

private fun homeJson(): String = scene(
    TransitionScenes.HOME,
    "#F4F5F9",
    "push",
    text("title", 72, 150, 936, 110, "오늘의 추천", 80, 800, "#15161A"),
    text("subtitle", 72, 262, 936, 64, "새로 들어온 상품을 눌러 보세요", 42, 400, "#6B7280"),
    rect("hero", 72, 370, 936, 900, 56, gradient = SHOE, action = "go?scene=product", label = "에어 러너 보기"),
    text("name", 128, 1120, 820, 100, "에어 러너", 76, 800, "#FFFFFF", hidden = true),
    text("price", 72, 1310, 936, 90, "129,000원", 64, 800, "#15161A"),
    rect("bag", 72, 1450, 450, 380, 48, gradient = BAG, label = "백팩"),
    rect("cap", 558, 1450, 450, 380, 48, gradient = CAP, label = "모자"),
)

private fun productJson(): String = scene(
    TransitionScenes.PRODUCT,
    "#FFFFFF",
    "push",
    rect("hero", 0, 0, 1080, 1100, 0, gradient = SHOE),
    rect("back", 56, 120, 128, 128, 64, fill = "#33FFFFFF", action = "back", label = "뒤로"),
    text("back-label", 56, 150, 128, 70, "←", 56, 700, "#FFFFFF", align = "center", hidden = true),
    text("name", 72, 1160, 936, 120, "에어 러너", 88, 800, "#15161A"),
    text("desc", 72, 1300, 936, 130, "가볍고 탄탄한 러닝화입니다. 발볼이 넓어도 편하게 신을 수 있어요.", 42, 400, "#6B7280"),
    text("price", 72, 1480, 936, 100, "129,000원", 72, 800, "#15161A"),
    rect("buy", 72, 1680, 936, 160, 80, fill = "#15161A", action = "go?scene=checkout", label = "구매하기"),
    text("buy-label", 72, 1724, 936, 72, "구매하기", 52, 700, "#FFFFFF", align = "center", hidden = true),
)

private fun checkoutJson(): String = scene(
    TransitionScenes.CHECKOUT,
    "#F4F5F9",
    "cover",
    rect("back", 56, 120, 128, 128, 64, fill = "#1A000000", action = "back", label = "뒤로"),
    text("back-label", 56, 150, 128, 70, "←", 56, 700, "#15161A", align = "center", hidden = true),
    text("title", 72, 300, 936, 120, "결제", 88, 800, "#15161A"),
    rect("summary", 72, 460, 936, 360, 48, fill = "#FFFFFF"),
    rect("hero", 120, 510, 260, 260, 36, gradient = SHOE),
    text("name", 420, 540, 560, 80, "에어 러너", 56, 700, "#15161A"),
    text("price", 420, 640, 560, 80, "129,000원", 56, 800, "#15161A"),
    text("ship", 72, 880, 936, 64, "배송비 무료, 내일 도착", 42, 400, "#6B7280"),
    text("total", 72, 960, 936, 80, "합계 129,000원", 56, 800, "#15161A"),
    rect("pay", 72, 1680, 936, 160, 80, fill = "#3B6CF6", action = "go?scene=home&done=1", label = "결제하기"),
    text("pay-label", 72, 1724, 936, 72, "결제하기", 52, 700, "#FFFFFF", align = "center", hidden = true),
)

private val SHOE = gradient("#FFB199", "#FF0844")
private val BAG = gradient("#84FAB0", "#8FD3F4")
private val CAP = gradient("#A18CD1", "#FBC2EB")

private fun gradient(start: String, end: String): String =
    """{ "type": "linear", "angle": 135, "stops": [{ "at": 0, "color": "$start" }, { "at": 1, "color": "$end" }] }"""

/** 노드 JSON 하나와 그 id. 노드는 모두 최상위라 [scene]이 그린 순서대로 rootOrder에 넣는다. */
private class NodeJson(val id: String, val json: String)

private fun scene(id: String, background: String, transition: String, vararg nodes: NodeJson): String = """
{
  "schemaVersion": 1,
  "documentId": "shop-$id",
  "canvas": { "width": 1080, "height": 1920, "background": "$background" },
  "transition": { "kind": "$transition" },
  "nodes": [${nodes.joinToString(",") { it.json }}],
  "rootOrder": [${nodes.joinToString(", ") { "\"${it.id}\"" }}]
}
""".trimIndent()

@Suppress("LongParameterList")
private fun rect(
    id: String,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    radius: Int,
    fill: String? = null,
    gradient: String? = null,
    action: String? = null,
    label: String? = null,
): NodeJson {
    val style = listOfNotNull(fill?.let { "\"fill\": \"$it\"" }, gradient?.let { "\"gradient\": $it" }).joinToString(", ")
    val extra = buildList {
        action?.let {
            add("\"action\": \"$it\"")
            add("\"effects\": { \"press\": [{ \"kind\": \"scale\" }] }")
        }
        label?.let { add("\"accessibility\": { \"label\": \"$it\"${if (action != null) ", \"role\": \"button\"" else ""} }") }
    }.joinToString("") { ", $it" }
    return NodeJson(
        id,
        """{ "id": "$id", "type": "rect", "transform": { "x": $x, "y": $y }, "bounds": { "width": $width, "height": $height }, """ +
            """"cornerRadius": $radius, "style": { $style }$extra }""",
    )
}

@Suppress("LongParameterList")
private fun text(
    id: String,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    value: String,
    size: Int,
    weight: Int,
    color: String,
    align: String = "start",
    hidden: Boolean = false,
): NodeJson {
    val a11y = if (hidden) ", \"accessibility\": { \"hidden\": true }" else ""
    return NodeJson(
        id,
        """{ "id": "$id", "type": "text", "transform": { "x": $x, "y": $y }, "bounds": { "width": $width, "height": $height }, """ +
            """"text": "$value", "fontSize": $size, "fontWeight": $weight, "align": "$align", "style": { "fill": "$color" }$a11y }""",
    )
}
