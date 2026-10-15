// 장면 배치 값은 시연용 데이터라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber", "LongParameterList")

package com.naury.kinetica.showcase

import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.AutoLayout
import com.naury.kinetica.core.scene.BoundProperty
import com.naury.kinetica.core.scene.CrossAlign
import com.naury.kinetica.core.scene.Insets
import com.naury.kinetica.core.scene.LayoutConstraints
import com.naury.kinetica.core.scene.LayoutDirection
import com.naury.kinetica.core.scene.Layouts
import com.naury.kinetica.core.scene.MainAlign
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.Pin
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.Shadow
import com.naury.kinetica.core.scene.TextAlignment
import com.naury.kinetica.core.scene.TextNumberFormat

/**
 * 호스트 위젯, 데이터 바인딩, 동작을 함께 쓰는 대시보드 예제. 카드(Frame) 안의 텍스트는 데이터 키에 바인딩되어 있고,
 * 위젯은 호스트가 키로 넘겨주는 실제 화면이다. 아래 버튼을 누르면 호스트에 "refresh" 동작이 전달된다.
 */
object DashboardScene {
    const val SALES_TODAY = "sales.today"
    const val ORDER_COUNT = "orders.count"
    const val STORE_OPEN = "store.open"
    const val GREETING = "user.greeting"

    fun create(): SceneDocument {
        val nodes = header() + stats() + badges() + widgets() + refreshButton()
        return Layouts.apply(
            SceneDocument(
                documentId = "studio",
                artboard = Artboard(1080.0, 1740.0, ArgbColor(0xFF15181E.toInt())),
                nodes = nodes.associateBy { it.id },
                rootOrder = nodes.filter { it.parentId == null }.map { it.id },
            ),
        )
    }

    private fun header() = listOf(
        text("greeting", "안녕하세요", 60.0, 60.0, 960.0, 90.0, 56.0, 700, 0xFFF2F4F8, bind = GREETING).stretchX(),
        text("date", "오늘의 매장 현황", 60.0, 150.0, 960.0, 60.0, 34.0, 400, 0xFF9AA3B2).stretchX(),
    )

    /** 카드 두 장은 가로 자동 레이아웃 안에서 남는 공간을 반씩 채우므로, 화면이 넓어지면 함께 넓어진다. */
    private fun stats() = listOf(
        row("stats", 60.0, 240.0, listOf("sales-card", "orders-card")),
        card("sales-card", "stats", listOf("sales-label", "sales-value")),
        text("sales-label", "오늘 매출", 0.0, 0.0, 400.0, 50.0, 30.0, 500, 0xFF5B6270, parent = "sales-card"),
        text("sales-value", "-", 0.0, 0.0, 400.0, 100.0, 60.0, 800, 0xFF1A1D24, parent = "sales-card", bind = SALES_TODAY)
            .withNumberFormat(),
        card("orders-card", "stats", listOf("orders-label", "orders-value", "badges")),
        text("orders-label", "주문", 0.0, 0.0, 200.0, 50.0, 30.0, 500, 0xFF5B6270, parent = "orders-card"),
        text("orders-value", "-", 0.0, 0.0, 300.0, 100.0, 72.0, 800, 0xFF1A1D24, parent = "orders-card", bind = ORDER_COUNT),
    )

    /** 가로 자동 레이아웃. 데이터에 따라 "영업 중"이 숨으면 "배달 가능"이 그 위치로 당겨진다. */
    private fun badges() = listOf(
        SceneNode(
            NodeId("badges"),
            NodeContent.Frame(
                Size(400.0, 52.0),
                listOf(NodeId("open-badge"), NodeId("delivery-badge")),
                clip = false,
                layout = AutoLayout(LayoutDirection.Horizontal, gap = 12.0, hugWidth = true, hugHeight = true),
            ),
            parentId = NodeId("orders-card"),
            name = "상태 표시",
        ),
        badge("open-badge", "영업 중", 0xFF5ED6A0).copy(bindings = mapOf(BoundProperty.Visible to STORE_OPEN)),
        badgeText("open-badge", "영업 중", 0xFF0B3D2C),
        badge("delivery-badge", "배달 가능", 0xFFFFE3A3),
        badgeText("delivery-badge", "배달 가능", 0xFF5A3B00),
    )

    private fun widgets() = listOf(
        host("chart", HostWidgets.SALES_CHART, 60.0, 570.0, 960.0, 380.0).stretchX(),
        row("widgets", 60.0, 980.0, listOf("left-column", "counter")),
        SceneNode(
            NodeId("left-column"),
            NodeContent.Frame(
                Size(470.0, 520.0),
                listOf(NodeId("clock"), NodeId("weather")),
                clip = false,
                layout = AutoLayout(LayoutDirection.Vertical, gap = 30.0, crossAlign = CrossAlign.Stretch, hugHeight = true),
            ),
            parentId = NodeId("widgets"),
            name = "왼쪽 열",
            layoutFill = true,
        ),
        host("clock", HostWidgets.CLOCK, 0.0, 0.0, 470.0, 200.0).copy(parentId = NodeId("left-column")),
        host("weather", HostWidgets.WEATHER, 0.0, 0.0, 470.0, 290.0).copy(parentId = NodeId("left-column")),
        host("counter", HostWidgets.COUNTER, 0.0, 0.0, 470.0, 260.0).copy(parentId = NodeId("widgets"), layoutFill = true),
    )

    /** 좌우로 늘어나는 가로 자동 레이아웃. 높이는 가장 큰 자식에 맞춘다. */
    private fun row(id: String, x: Double, y: Double, children: List<String>) = SceneNode(
        id = NodeId(id),
        content = NodeContent.Frame(
            Size(960.0, 100.0),
            children.map(::NodeId),
            clip = false,
            layout = AutoLayout(LayoutDirection.Horizontal, gap = 20.0, hugHeight = true),
        ),
        name = id,
        transform = Transform2D(translationX = x, translationY = y),
        constraints = LayoutConstraints(Pin.Both),
    )

    private fun refreshButton() = listOf(
        SceneNode(
            NodeId("refresh"),
            NodeContent.Frame(
                Size(960.0, 120.0),
                listOf(NodeId("refresh-text")),
                cornerRadius = 60.0,
                layout = AutoLayout(LayoutDirection.Horizontal, mainAlign = MainAlign.Center, crossAlign = CrossAlign.Center),
            ),
            name = "새로 고침 버튼",
            transform = Transform2D(translationX = 60.0, translationY = 1560.0),
            style = NodeStyle(
                fill = ArgbColor(0xFF7C8CFF.toInt()),
                shadows = listOf(Shadow(ArgbColor(0x557C8CFF), offsetY = 10.0, blur = 30.0)),
            ),
            action = "refresh",
            // 화면이 길어져도 아래에 붙고, 넓어지면 좌우로 늘어난다.
            constraints = LayoutConstraints(Pin.Both, Pin.End),
        ),
        text("refresh-text", "새로 고침", 0.0, 0.0, 300.0, 56.0, 40.0, 700, 0xFFFFFFFF, parent = "refresh", align = TextAlignment.Center),
    )

    private fun badgeText(badge: String, label: String, color: Long) =
        text("$badge-text", label, 0.0, 0.0, 120.0, 36.0, 24.0, 700, color, parent = badge, align = TextAlignment.Center)

    private fun SceneNode.stretchX() = copy(constraints = LayoutConstraints(Pin.Both))

    /** 숫자 카드. 안쪽은 세로 자동 레이아웃이라 데이터 때문에 텍스트가 바뀌거나 요소가 숨어도 간격이 다시 맞춰진다. */
    private fun card(id: String, parent: String, children: List<String>) = SceneNode(
        id = NodeId(id),
        content = NodeContent.Frame(
            Size(470.0, 300.0),
            children.map(::NodeId),
            cornerRadius = 36.0,
            layout = AutoLayout(
                LayoutDirection.Vertical,
                gap = 16.0,
                padding = Insets.all(32.0),
                crossAlign = CrossAlign.Stretch,
                hugHeight = true,
            ),
        ),
        parentId = NodeId(parent),
        name = id,
        style = NodeStyle(
            fill = ArgbColor(0xFFFFFFFF.toInt()),
            shadows = listOf(Shadow(ArgbColor(0x33000000), offsetY = 10.0, blur = 28.0)),
        ),
        layoutFill = true,
    )

    private fun badge(id: String, label: String, fill: Long) = SceneNode(
        id = NodeId(id),
        content = NodeContent.Frame(
            Size(150.0, 52.0),
            listOf(NodeId("$id-text")),
            cornerRadius = 26.0,
            layout = AutoLayout(LayoutDirection.Horizontal, padding = Insets(16.0, 8.0, 16.0, 8.0), hugWidth = true, hugHeight = true),
        ),
        parentId = NodeId("badges"),
        name = label,
        style = NodeStyle(fill = ArgbColor(fill.toInt())),
    )

    private fun host(id: String, key: String, x: Double, y: Double, w: Double, h: Double) = SceneNode(
        id = NodeId(id),
        content = NodeContent.HostSlot(Size(w, h), key),
        name = HostWidgets.options.first { it.key == key }.label,
        transform = Transform2D(translationX = x, translationY = y, pivotX = w / 2, pivotY = h / 2),
    )

    /** 매출처럼 "1,284,000원"으로 보이게 한다. */
    private fun SceneNode.withNumberFormat() =
        copy(content = (content as NodeContent.Text).copy(numberFormat = TextNumberFormat(suffix = "원")))

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
