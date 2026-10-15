// 기본 컴포넌트의 크기와 간격은 디자인 값이라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.presets

import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.AutoLayout
import com.naury.kinetica.core.scene.CrossAlign
import com.naury.kinetica.core.scene.Insets
import com.naury.kinetica.core.scene.LayoutDirection
import com.naury.kinetica.core.scene.MainAlign
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.Shadow
import com.naury.kinetica.core.scene.TextAlignment

/** 문서에 넣을 기본 컴포넌트 모음. [roots]를 최상위에 차례로 넣고 [tokens]를 문서 토큰에 추가한다. */
public data class UiKitInsert(val tokens: Map<String, ArgbColor>, val nodes: List<SceneNode>, val roots: List<NodeId>)

/**
 * 앱 화면을 빠르게 조립하기 위한 기본 컴포넌트. 버튼, 칩, 카드, 토글, 입력 필드, 목록 항목, 탭 바가 있다.
 * 모두 자동 레이아웃 Frame이고 색은 디자인 토큰에 연결되어 있어서 토큰을 바꾸면 키트 전체의 색이 바뀐다.
 * 원본은 [origin]에서 아래쪽으로 배치하므로 아트보드 밖에 두면 재생기에는 보이지 않는다.
 * 버튼은 `Button` 세트의 Primary, Secondary, Disabled 변형이므로 인스턴스에서 변형을 바꾸거나 데이터로 고를 수 있다.
 * [com.naury.kinetica.core.scene.ComponentVariants] 참고.
 */
public object UiKit {
    /** 토큰 이름. 호스트가 같은 이름의 토큰으로 브랜드 색을 정하면 키트도 그 색을 따른다. */
    public object Token {
        public const val BRAND: String = "color.brand"
        public const val ON_BRAND: String = "color.onBrand"
        public const val SURFACE: String = "color.surface"
        public const val SURFACE_ALT: String = "color.surfaceAlt"
        public const val TEXT: String = "color.text"
        public const val TEXT_MUTED: String = "color.textMuted"
        public const val BORDER: String = "color.border"
    }

    public val defaultTokens: Map<String, ArgbColor> = mapOf(
        Token.BRAND to ArgbColor(0xFF5B6CFF.toInt()),
        Token.ON_BRAND to ArgbColor(0xFFFFFFFF.toInt()),
        Token.SURFACE to ArgbColor(0xFFFFFFFF.toInt()),
        Token.SURFACE_ALT to ArgbColor(0xFFF2F4F8.toInt()),
        Token.TEXT to ArgbColor(0xFF1A1D24.toInt()),
        Token.TEXT_MUTED to ArgbColor(0xFF6B7280.toInt()),
        Token.BORDER to ArgbColor(0xFFE5E7EB.toInt()),
    )

    /**
     * 기본 컴포넌트 원본을 만든다. id가 [prefix]로 시작하므로 같은 문서에 한 번 더 넣으려면 다른 [prefix]를 써야 한다.
     *
     * @param tokens 문서에 이미 있는 토큰. 이름이 같은 토큰은 기존 색을 그대로 써서 브랜드 색을 유지한다.
     */
    public fun starter(origin: Vec2, prefix: String = "kit", tokens: Map<String, ArgbColor> = emptyMap()): UiKitInsert {
        val palette = defaultTokens + tokens.filterKeys { it in defaultTokens }
        val builder = KitBuilder(prefix, palette)
        val components = listOf(
            builder.button("button", "Button / Primary", "확인", Token.BRAND, Token.ON_BRAND),
            builder.button("button-secondary", "Button / Secondary", "취소", Token.SURFACE_ALT, Token.BRAND),
            builder.button("button-disabled", "Button / Disabled", "확인", Token.SURFACE_ALT, Token.TEXT_MUTED),
            builder.chip(),
            builder.card(),
            builder.toggle(),
            builder.input(),
            builder.listItem(),
            builder.tabBar(),
        )
        var y = origin.y
        val placed = components.map { group ->
            val root = group.first()
            val moved = root.copy(transform = Transform2D(translationX = origin.x, translationY = y))
            y += (root.content as NodeContent.Frame).size.height + GAP
            listOf(moved) + group.drop(1)
        }
        return UiKitInsert(palette, placed.flatten(), placed.map { it.first().id })
    }

    private const val GAP = 80.0
}

/** 한 번에 넣을 키트의 노드를 만든다. 각 함수는 원본 Frame을 맨 앞에 둔 노드 목록을 돌려준다. */
private class KitBuilder(private val prefix: String, private val palette: Map<String, ArgbColor>) {
    private fun id(name: String) = NodeId("$prefix-$name")

    private fun color(token: String) = palette.getValue(token)

    // 키트 Frame마다 설정이 달라 파라미터가 많다. 객체로 묶으면 오히려 컴포넌트 정의마다 코드가 길어진다.
    @Suppress("LongParameterList")
    private fun frame(
        name: String,
        component: String?,
        size: Size,
        children: List<String>,
        layout: AutoLayout?,
        style: NodeStyle,
        parent: String? = null,
        corner: Double = 0.0,
    ) = SceneNode(
        id = id(name),
        content = NodeContent.Frame(size, children.map(::id), cornerRadius = corner, clip = false, layout = layout),
        parentId = parent?.let(::id),
        name = component ?: name,
        style = style,
        component = component,
    )

    @Suppress("LongParameterList")
    private fun text(
        name: String,
        parent: String,
        value: String,
        size: Size,
        fontSize: Double,
        weight: Int,
        token: String,
        align: TextAlignment = TextAlignment.Start,
    ) = SceneNode(
        id = id(name),
        content = NodeContent.Text(size, value, fontSize = fontSize, alignment = align, fontWeight = weight),
        parentId = id(parent),
        name = name.substringAfterLast('-'),
        style = NodeStyle(fill = color(token), fillToken = token),
    )

    private fun filled(token: String, extra: NodeStyle = NodeStyle()) = extra.copy(fill = color(token), fillToken = token)

    fun button(name: String, component: String, label: String, background: String, foreground: String) = listOf(
        frame(
            name,
            component,
            Size(240.0, 88.0),
            listOf("$name-label"),
            AutoLayout(
                LayoutDirection.Horizontal,
                padding = Insets(32.0, 22.0, 32.0, 22.0),
                mainAlign = MainAlign.Center,
                crossAlign = CrossAlign.Center,
                hugWidth = true,
                hugHeight = true,
            ),
            filled(background),
            corner = 20.0,
        ),
        text("$name-label", name, label, Size(176.0, 44.0), 32.0, 600, foreground, TextAlignment.Center),
    )

    fun chip() = listOf(
        frame(
            "chip",
            "Chip",
            Size(160.0, 56.0),
            listOf("chip-label"),
            AutoLayout(LayoutDirection.Horizontal, padding = Insets(24.0, 10.0, 24.0, 10.0), hugWidth = true, hugHeight = true),
            filled(UiKit.Token.SURFACE_ALT),
            corner = 28.0,
        ),
        text("chip-label", "chip", "태그", Size(112.0, 36.0), 26.0, 500, UiKit.Token.TEXT, TextAlignment.Center),
    )

    fun card() = listOf(
        frame(
            "card",
            "Card",
            Size(600.0, 260.0),
            listOf("card-title", "card-body"),
            AutoLayout(LayoutDirection.Vertical, gap = 12.0, padding = Insets.all(36.0), crossAlign = CrossAlign.Stretch, hugHeight = true),
            filled(UiKit.Token.SURFACE, NodeStyle(shadows = listOf(Shadow(ArgbColor(0x22000000), offsetY = 12.0, blur = 32.0)))),
            corner = 28.0,
        ),
        text("card-title", "card", "카드 제목", Size(528.0, 52.0), 38.0, 700, UiKit.Token.TEXT),
        text("card-body", "card", "설명을 두 줄 정도 넣는 자리입니다.", Size(528.0, 84.0), 28.0, 400, UiKit.Token.TEXT_MUTED),
    )

    fun toggle() = listOf(
        frame("toggle", "Toggle", Size(104.0, 60.0), listOf("toggle-knob"), null, filled(UiKit.Token.BRAND), corner = 30.0),
        SceneNode(
            id = id("toggle-knob"),
            content = NodeContent.Ellipse(Size(48.0, 48.0)),
            parentId = id("toggle"),
            name = "knob",
            transform = Transform2D(translationX = 50.0, translationY = 6.0),
            style = filled(UiKit.Token.ON_BRAND),
        ),
    )

    fun input() = listOf(
        frame(
            "input",
            "Input",
            Size(560.0, 96.0),
            listOf("input-placeholder"),
            AutoLayout(LayoutDirection.Horizontal, padding = Insets(28.0, 0.0, 28.0, 0.0), crossAlign = CrossAlign.Center),
            filled(UiKit.Token.SURFACE, NodeStyle(stroke = color(UiKit.Token.BORDER), strokeToken = UiKit.Token.BORDER, strokeWidth = 3.0)),
            corner = 18.0,
        ),
        text("input-placeholder", "input", "입력하세요", Size(480.0, 42.0), 30.0, 400, UiKit.Token.TEXT_MUTED),
    )

    fun listItem() = listOf(
        frame(
            "list-item",
            "List item",
            Size(640.0, 128.0),
            listOf("list-item-avatar", "list-item-texts"),
            AutoLayout(
                LayoutDirection.Horizontal,
                gap = 24.0,
                padding = Insets(28.0, 20.0, 28.0, 20.0),
                crossAlign = CrossAlign.Center,
                hugHeight = true,
            ),
            filled(UiKit.Token.SURFACE),
        ),
        SceneNode(
            id("list-item-avatar"),
            NodeContent.Ellipse(Size(80.0, 80.0)),
            parentId = id("list-item"),
            name = "avatar",
            style = filled(UiKit.Token.SURFACE_ALT),
        ),
        frame(
            "list-item-texts",
            null,
            Size(480.0, 88.0),
            listOf("list-item-title", "list-item-subtitle"),
            AutoLayout(LayoutDirection.Vertical, gap = 6.0, hugHeight = true),
            NodeStyle(),
            parent = "list-item",
        ).copy(layoutFill = true),
        text("list-item-title", "list-item-texts", "제목", Size(480.0, 44.0), 32.0, 600, UiKit.Token.TEXT),
        text("list-item-subtitle", "list-item-texts", "보조 설명", Size(480.0, 36.0), 26.0, 400, UiKit.Token.TEXT_MUTED),
    )

    fun tabBar(): List<SceneNode> {
        val tabs = listOf("홈", "검색", "알림", "내 정보")
        return listOf(
            frame(
                "tab-bar",
                "Tab bar",
                Size(720.0, 128.0),
                tabs.indices.map { "tab-bar-$it" },
                AutoLayout(
                    LayoutDirection.Horizontal,
                    padding = Insets(24.0, 0.0, 24.0, 0.0),
                    mainAlign = MainAlign.SpaceBetween,
                    crossAlign = CrossAlign.Center,
                ),
                filled(UiKit.Token.SURFACE, NodeStyle(shadows = listOf(Shadow(ArgbColor(0x1A000000), offsetY = -4.0, blur = 24.0)))),
            ),
        ) + tabs.mapIndexed { index, label ->
            text(
                "tab-bar-$index",
                "tab-bar",
                label,
                Size(150.0, 40.0),
                28.0,
                if (index ==
                    0
                ) {
                    700
                } else {
                    500
                },
                if (index == 0) UiKit.Token.BRAND else UiKit.Token.TEXT_MUTED,
                TextAlignment.Center,
            )
        }
    }
}
