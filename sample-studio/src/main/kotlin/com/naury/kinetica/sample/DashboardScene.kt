// 장면 배치 값은 시연용 데이터라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber", "LongParameterList")

package com.naury.kinetica.sample

import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.BoundProperty
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.Shadow
import com.naury.kinetica.core.scene.TextAlignment

/**
 * 호스트 위젯·데이터 바인딩·동작을 함께 쓰는 대시보드 예제. 카드(Frame) 안의 글자는 데이터 키에 묶여 있고,
 * 위젯은 호스트가 키로 주는 실제 화면이며, 아래 버튼을 누르면 호스트에 "refresh" 동작이 간다.
 */
object DashboardScene {
    const val SALES_TODAY = "sales.today"
    const val ORDER_COUNT = "orders.count"
    const val STORE_OPEN = "store.open"
    const val GREETING = "user.greeting"

    fun create(): SceneDocument {
        val nodes = listOf(
            text("greeting", "안녕하세요", 60.0, 60.0, 960.0, 90.0, 56.0, 700, 0xFFF2F4F8, bind = GREETING),
            text("date", "오늘의 매장 현황", 60.0, 150.0, 960.0, 60.0, 34.0, 400, 0xFF9AA3B2),
            card("sales-card", 60.0, 240.0, 470.0, 260.0, children = listOf("sales-label", "sales-value")),
            text("sales-label", "오늘 매출", 32.0, 32.0, 400.0, 50.0, 30.0, 500, 0xFF5B6270, parent = "sales-card"),
            text("sales-value", "-", 32.0, 110.0, 420.0, 110.0, 72.0, 800, 0xFF1A1D24, parent = "sales-card", bind = SALES_TODAY),
            card("orders-card", 550.0, 240.0, 470.0, 260.0, children = listOf("orders-label", "orders-value", "open-badge")),
            text("orders-label", "주문", 32.0, 32.0, 200.0, 50.0, 30.0, 500, 0xFF5B6270, parent = "orders-card"),
            text("orders-value", "-", 32.0, 110.0, 300.0, 110.0, 72.0, 800, 0xFF1A1D24, parent = "orders-card", bind = ORDER_COUNT),
            SceneNode(
                NodeId("open-badge"),
                NodeContent.Frame(Size(140.0, 56.0), listOf(NodeId("open-text")), cornerRadius = 28.0),
                parentId = NodeId("orders-card"),
                name = "영업 중",
                transform = Transform2D(translationX = 300.0, translationY = 32.0),
                style = NodeStyle(fill = ArgbColor(0xFF5ED6A0.toInt())),
                bindings = mapOf(BoundProperty.Visible to STORE_OPEN),
            ),
            text("open-text", "영업 중", 0.0, 10.0, 140.0, 40.0, 26.0, 700, 0xFF0B3D2C, parent = "open-badge", align = TextAlignment.Center),
            host("chart", HostWidgets.SALES_CHART, 60.0, 530.0, 960.0, 380.0),
            host("clock", HostWidgets.CLOCK, 60.0, 940.0, 470.0, 200.0),
            host("counter", HostWidgets.COUNTER, 550.0, 940.0, 470.0, 260.0),
            host("weather", HostWidgets.WEATHER, 60.0, 1170.0, 470.0, 290.0),
            SceneNode(
                NodeId("refresh"),
                NodeContent.Frame(Size(960.0, 120.0), listOf(NodeId("refresh-text")), cornerRadius = 60.0),
                name = "새로 고침 버튼",
                transform = Transform2D(translationX = 60.0, translationY = 1520.0),
                style = NodeStyle(
                    fill = ArgbColor(0xFF7C8CFF.toInt()),
                    shadow = Shadow(ArgbColor(0x557C8CFF), offsetY = 10.0, blur = 30.0),
                ),
                action = "refresh",
            ),
            text("refresh-text", "새로 고침", 0.0, 34.0, 960.0, 60.0, 40.0, 700, 0xFFFFFFFF, parent = "refresh", align = TextAlignment.Center),
        )
        return SceneDocument(
            documentId = "studio",
            artboard = Artboard(1080.0, 1700.0, ArgbColor(0xFF15181E.toInt())),
            nodes = nodes.associateBy { it.id },
            rootOrder = nodes.filter { it.parentId == null }.map { it.id },
        )
    }

    private fun card(id: String, x: Double, y: Double, w: Double, h: Double, children: List<String>) = SceneNode(
        id = NodeId(id),
        content = NodeContent.Frame(Size(w, h), children.map(::NodeId), cornerRadius = 36.0),
        name = id,
        transform = Transform2D(translationX = x, translationY = y),
        style = NodeStyle(fill = ArgbColor(0xFFFFFFFF.toInt()), shadow = Shadow(ArgbColor(0x33000000), offsetY = 10.0, blur = 28.0)),
    )

    private fun host(id: String, key: String, x: Double, y: Double, w: Double, h: Double) = SceneNode(
        id = NodeId(id),
        content = NodeContent.HostSlot(Size(w, h), key),
        name = HostWidgets.options.first { it.key == key }.label,
        transform = Transform2D(translationX = x, translationY = y, pivotX = w / 2, pivotY = h / 2),
    )

    private fun text(
        id: String,
        value: String,
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        fontSize: Double,
        weight: Int,
        color: Long,
        parent: String? = null,
        bind: String? = null,
        align: TextAlignment = TextAlignment.Start,
    ) = SceneNode(
        id = NodeId(id),
        content = NodeContent.Text(Size(w, h), value, fontSize = fontSize, alignment = align, fontWeight = weight),
        parentId = parent?.let(::NodeId),
        name = id,
        transform = Transform2D(translationX = x, translationY = y),
        style = NodeStyle(fill = ArgbColor(color.toInt())),
        bindings = bind?.let { mapOf(BoundProperty.Text to it) }.orEmpty(),
    )
}
