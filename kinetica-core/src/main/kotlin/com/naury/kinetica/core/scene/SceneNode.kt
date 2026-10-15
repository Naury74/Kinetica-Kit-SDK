package com.naury.kinetica.core.scene

import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2

/**
 * 장면 그래프의 노드 하나다. 도형은 [content]가 정한 노드 로컬 좌표로 정의하고, [transform]으로 부모 좌표계에 배치한다.
 *
 * 생성자는 구조를 검사하지 않는다. 문서에 넣기 전에 [SceneValidator]가 부모·자식 관계와 값 범위를 확인한다.
 *
 * @property parentId 부모 그룹 id. 최상위 노드면 `null`이며 이때는 [SceneDocument.rootOrder]에 있어야 한다.
 * @property name 레이어 이름. 화면 표시용이며 로그에 남기지 않는다.
 * @property locked 잠긴 노드는 화면에서 선택하거나 변형할 수 없다. 명령으로는 바꿀 수 있다.
 * @property metadata 호스트가 붙이는 문자열 값. SDK는 해석하지 않는다.
 * @property bindings 속성과 호스트 데이터 키의 바인딩. 데이터에 값이 있으면 저장된 값 대신 그 값을 쓴다. docs/30 참고.
 * @property action 사용 모드에서 노드를 누르면 호스트에 전달하는 액션 이름. SDK가 코드를 실행하지는 않는다.
 * @property constraints 부모 Frame이나 아트보드의 크기가 바뀔 때 노드가 따라가는 방식. 자동 레이아웃 Frame의 자식이면 무시한다.
 * @property component 이 Frame을 컴포넌트 원본으로 쓸 때의 컴포넌트 이름. 원본을 고치면 모든 인스턴스에 반영된다.
 * @property instanceOf 이 Frame이 인스턴스로서 가리키는 컴포넌트. 자식은 원본에서 만들며([Components]), 자식의 텍스트, 색, 표시 여부, 이미지를 고치면 오버라이드로 남는다.
 * @property effects 누를 때, 등장할 때, 반복해서 재생하는 프리셋 모션 효과. HostSlot에도 적용된다.
 * @property sizeLimits 최소·최대 크기. 자동 레이아웃이나 제약이 크기를 바꿀 때 이 범위를 지킨다. 크기가 없는 노드에는 적용하지 않는다.
 * @property accessibility TalkBack 같은 스크린 리더에 알리는 이름과 역할. 장식용 노드는 읽지 않도록 숨길 수 있다.
 * @property layoutFill `true`면 자동 레이아웃 Frame 안에서 배치 방향의 남는 공간을 다른 fill 자식과 똑같이 나눠 차지한다. Figma의 Fill container와 같다.
 */
public data class SceneNode(
    val id: NodeId,
    val content: NodeContent,
    val parentId: NodeId? = null,
    val name: String = "",
    val transform: Transform2D = Transform2D.Identity,
    val style: NodeStyle = NodeStyle(),
    val visible: Boolean = true,
    val locked: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
    val bindings: Map<BoundProperty, String> = emptyMap(),
    val action: String? = null,
    val constraints: LayoutConstraints = LayoutConstraints.Default,
    val layoutFill: Boolean = false,
    val component: String? = null,
    val instanceOf: InstanceOf? = null,
    val sizeLimits: SizeLimits = SizeLimits.None,
    val effects: NodeEffects = NodeEffects.None,
    val accessibility: NodeAccessibility = NodeAccessibility.Default,
) {
    public val kind: NodeKind get() = content.kind

    /** 노드 로컬 좌표의 도형 범위. 그룹은 자식에서 범위를 계산하므로 `null`이다. */
    public val localBounds: Rect? get() = content.localBounds()
}

/** 노드 종류. [wireName]은 JSON에 저장할 때 쓰는 이름이다. */
public enum class NodeKind(public val wireName: String) {
    Group("group"),
    Rectangle("rect"),
    Ellipse("ellipse"),
    Path("path"),
    Text("text"),
    Image("image"),
    Frame("frame"),
    Polygon("polygon"),
    HostSlot("host"),
    ;

    public companion object {
        public fun fromWireName(name: String): NodeKind? = entries.firstOrNull { it.wireName == name }
    }
}

/** 노드 종류별 내용. 크기와 도형 좌표는 모두 노드 로컬 좌표계의 문서 단위다. */
public sealed interface NodeContent {
    public val kind: NodeKind

    public fun localBounds(): Rect?

    /** 자식을 갖는 노드. 목록 순서가 z-order이며 뒤에 있을수록 위에 그려진다. 자식 좌표는 이 노드의 로컬 좌표다. */
    public sealed interface Container : NodeContent {
        public val childIds: List<NodeId>

        /** 자식 목록만 바꾼 같은 종류의 내용. */
        public fun withChildren(childIds: List<NodeId>): Container
    }

    /** 자체 크기 없이 자식만 묶는 그룹. 범위는 자식에서 계산한다. */
    public data class Group(override val childIds: List<NodeId> = emptyList()) : Container {
        override val kind: NodeKind get() = NodeKind.Group

        override fun localBounds(): Rect? = null

        override fun withChildren(childIds: List<NodeId>): Group = copy(childIds = childIds)
    }

    /**
     * 크기와 배경, 둥근 모서리, 테두리, 그림자를 가질 수 있는 컨테이너로 Figma의 frame에 해당한다.
     * 배경은 `style.fill`, 테두리는 `style.stroke`로 정한다.
     *
     * @property clip `true`면 둥근 모서리를 포함한 이 상자 밖으로 자식을 그리지 않는다.
     * @property corners 있으면 모서리마다 이 반지름을 쓰고 [cornerRadius]는 무시한다.
     * @property layout 자식의 자동 레이아웃. `null`이면 자식은 각자 위치에 있고 [SceneNode.constraints]에 따라 Frame 크기를 따라간다.
     */
    public data class Frame(
        val size: Size,
        override val childIds: List<NodeId> = emptyList(),
        val cornerRadius: Double = 0.0,
        val clip: Boolean = true,
        val layout: AutoLayout? = null,
        val corners: CornerRadii? = null,
    ) : Container {
        override val kind: NodeKind get() = NodeKind.Frame

        override fun localBounds(): Rect = Rect.fromSize(size)

        override fun withChildren(childIds: List<NodeId>): Frame = copy(childIds = childIds)
    }

    /**
     * 호스트 앱이 [key]로 제공하는 Composable이나 View를 [size] 상자에 그린다. 문서에는 키와 크기, 위치만 저장한다.
     * 호스트가 모르는 키면 빈 플레이스홀더를 그린다.
     */
    public data class HostSlot(val size: Size, val key: String) : NodeContent {
        override val kind: NodeKind get() = NodeKind.HostSlot

        override fun localBounds(): Rect = Rect.fromSize(size)
    }

    /**
     * [size] 상자에 맞춘 정다각형 또는 별. 꼭짓점은 위 가운데에서 시작해 시계 방향이다.
     *
     * @property sides 꼭짓점 수. 별이면 바깥쪽 뾰족한 끝의 수다. 3..64
     * @property innerRatio 바깥 반지름에 대한 안쪽 꼭짓점의 비율. 1이면 정다각형, 1보다 작으면 별이다. 0 초과 1 이하
     */
    public data class Polygon(val size: Size, val sides: Int = 5, val innerRatio: Double = 1.0) : NodeContent {
        override val kind: NodeKind get() = NodeKind.Polygon

        override fun localBounds(): Rect = Rect.fromSize(size)

        /** 노드 로컬 좌표로 계산한 꼭짓점 목록. */
        public fun vertices(): List<Vec2> {
            val count = if (innerRatio < 1.0) sides * 2 else sides
            val rx = size.width / 2.0
            val ry = size.height / 2.0
            return List(count) { index ->
                val angle = -kotlin.math.PI / 2.0 + 2.0 * kotlin.math.PI * index / count
                val ratio = if (innerRatio < 1.0 && index % 2 == 1) innerRatio else 1.0
                Vec2(rx + rx * ratio * kotlin.math.cos(angle), ry + ry * ratio * kotlin.math.sin(angle))
            }
        }

        public companion object {
            public const val MIN_SIDES: Int = 3
            public const val MAX_SIDES: Int = 64
        }
    }

    /**
     * `(0, 0)`에서 시작하는 사각형. [cornerRadius]는 문서 단위이며 짧은 변의 절반을 넘으면 절반으로 그린다.
     *
     * @property corners 있으면 모서리마다 이 반지름을 쓰고 [cornerRadius]는 쓰지 않는다.
     */
    public data class Rectangle(val size: Size, val cornerRadius: Double = 0.0, val corners: CornerRadii? = null) : NodeContent {
        override val kind: NodeKind get() = NodeKind.Rectangle

        override fun localBounds(): Rect = Rect.fromSize(size)
    }

    /** `(0, 0)`에서 시작하는 [size] 사각형에 내접하는 타원. */
    public data class Ellipse(val size: Size) : NodeContent {
        override val kind: NodeKind get() = NodeKind.Ellipse

        override fun localBounds(): Rect = Rect.fromSize(size)
    }

    /** 직선과 2차, 3차 베지어 곡선으로 이루어진 경로. 범위는 제어점까지 포함해 계산하므로 실제 곡선보다 클 수 있다. */
    public data class Path(val commands: List<PathCommand>) : NodeContent {
        override val kind: NodeKind get() = NodeKind.Path

        override fun localBounds(): Rect? {
            val points = commands.flatMap(PathCommand::points)
            return if (points.isEmpty()) null else Rect.enclosing(points)
        }
    }

    /**
     * [size] 상자 안에서 줄을 바꿔 배치하는 텍스트. 상자를 넘는 줄은 잘린다.
     *
     * @property fontSize 문서 단위 글자 크기
     * @property fontWeight 100..900. 400이 보통, 700이 굵게다.
     * @property lineHeight 문서 단위 줄 높이. 0이면 글꼴 기본값을 쓴다.
     * @property letterSpacing 문서 단위 자간
     * @property fontFamily 글꼴 이름. `sans`, `serif`, `monospace`, `cursive`는 어디서나 쓸 수 있고, 그 밖의 이름은 호스트가 등록한 글꼴이다.
     * 모르는 이름이거나 `null`이면 기본 글꼴로 그린다.
     * @property spans 텍스트 일부에 적용하는 스타일(리치 텍스트). 범위가 겹치면 뒤의 것이 앞의 것을 덮는다.
     * @property autoSize 텍스트에 맞춰 [size]를 바꾸는 방식. 측정은 [TextMeasurer]가 맡으며, 측정기가 없으면 저장된 크기를 그대로 쓴다.
     * @property numberFormat 숫자 데이터를 바인딩했을 때의 표시 형식. 예를 들어 `1,284,000원`처럼 보여 준다. `null`이면 숫자를 그대로 보여 준다.
     */
    public data class Text(
        val size: Size,
        val text: String,
        val fontSize: Double = 16.0,
        val alignment: TextAlignment = TextAlignment.Start,
        val fontWeight: Int = 400,
        val lineHeight: Double = 0.0,
        val letterSpacing: Double = 0.0,
        val fontFamily: String? = null,
        val italic: Boolean = false,
        val decoration: TextDecoration = TextDecoration.None,
        val spans: List<TextSpan> = emptyList(),
        val autoSize: TextAutoSize = TextAutoSize.Fixed,
        val numberFormat: TextNumberFormat? = null,
    ) : NodeContent {
        override val kind: NodeKind get() = NodeKind.Text

        override fun localBounds(): Rect = Rect.fromSize(size)
    }

    /**
     * 호스트가 제공하는 이미지를 [size] 상자에 그린다. [uri]는 호스트의 `MediaResolver`가 해석하며 SDK가 직접 열지 않는다.
     */
    public data class Image(val size: Size, val uri: String) : NodeContent {
        override val kind: NodeKind get() = NodeKind.Image

        override fun localBounds(): Rect = Rect.fromSize(size)
    }
}

public enum class TextAlignment { Start, Center, End }

/** 텍스트 상자 크기를 내용에 맞추는 방식. [wireName]은 JSON에 저장할 때 쓰는 이름이다. */
public enum class TextAutoSize(public val wireName: String) {
    /** 저장된 크기를 그대로 쓴다. 넘치는 줄은 잘린다. */
    Fixed("fixed"),

    /** 너비는 그대로 두고 높이를 줄 수에 맞춘다. */
    Height("height"),

    /** 자동 줄 바꿈 없이 너비와 높이를 텍스트에 맞춘다. 줄은 줄바꿈 문자에서만 바뀐다. */
    WidthAndHeight("auto"),
    ;

    public companion object {
        public fun fromWireName(name: String): TextAutoSize? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * 텍스트 노드가 차지할 크기를 측정한다. 렌더러가 화면에 그릴 때와 같은 글꼴과 규칙으로 구현하며,
 * `kinetica-render-compose`의 `SceneTextMeasurer`가 그 예다. 같은 입력에는 항상 같은 크기를 돌려줘야 한다.
 */
public fun interface TextMeasurer {
    /**
     * [text]를 너비 [maxWidth] 안에서 줄 바꿈해 배치했을 때의 크기를 돌려준다. [maxWidth]가 `null`이면 줄을 바꾸지 않는다.
     * 입력과 결과는 모두 문서 단위이며, 결과는 유한한 양수여야 한다.
     */
    public fun measure(text: NodeContent.Text, maxWidth: Double?): Size
}

/** 텍스트 장식. 밑줄과 취소선이 있다. */
public enum class TextDecoration(public val wireName: String) {
    None("none"),
    Underline("underline"),
    Strikethrough("strike"),
    ;

    public companion object {
        public fun fromWireName(name: String): TextDecoration? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * 텍스트의 [start]부터 [end] 직전까지 적용하는 스타일. 위치는 UTF-16 인덱스다. `null`인 값은 노드 값을 따른다.
 *
 * @property fontSize 문서 단위 글자 크기
 * @property fontWeight 100..900
 */
public data class TextSpan(
    val start: Int,
    val end: Int,
    val fontFamily: String? = null,
    val fontSize: Double? = null,
    val fontWeight: Int? = null,
    val italic: Boolean? = null,
    val decoration: TextDecoration? = null,
    val color: ArgbColor? = null,
)

/** 경로 명령. 점은 노드 로컬 문서 좌표다. */
public sealed interface PathCommand {
    public val points: List<Vec2>

    public data class MoveTo(val point: Vec2) : PathCommand {
        override val points: List<Vec2> get() = listOf(point)
    }

    public data class LineTo(val point: Vec2) : PathCommand {
        override val points: List<Vec2> get() = listOf(point)
    }

    public data class QuadTo(val control: Vec2, val point: Vec2) : PathCommand {
        override val points: List<Vec2> get() = listOf(control, point)
    }

    public data class CubicTo(val control1: Vec2, val control2: Vec2, val point: Vec2) : PathCommand {
        override val points: List<Vec2> get() = listOf(control1, control2, point)
    }

    public data object Close : PathCommand {
        override val points: List<Vec2> get() = emptyList()
    }
}

/**
 * 노드 스타일. 그룹은 [opacity]와 [blur]만 쓰고, 텍스트는 [fill]이나 [gradient]를 글자 색으로 쓰며, 이미지는 [fill]과 [stroke]를 쓰지 않는다.
 *
 * @property fill 단색 채우기. [gradient]가 있으면 무시한다.
 * @property gradient 그라디언트 채우기. 있으면 [fill]보다 우선한다.
 * @property strokeWidth 문서 단위 선 두께. 0이면 선을 그리지 않는다.
 * @property strokeDash 점선 패턴. 선과 빈칸의 길이를 번갈아 문서 단위로 적는다. 비어 있으면 실선이다.
 * @property opacity 0..1. 부모 그룹의 opacity와 곱해진다.
 * @property shadows 그림자 목록. 사각형, 타원, 다각형, Frame, HostSlot에만 그리며 앞의 것부터 그린다.
 * 바깥 그림자는 노드 아래에, 안쪽 그림자는 채우기 위에 그린다.
 * @property blur 노드 전체에 거는 블러 반지름. 문서 단위이며 0 이상이다. Android 12(API 31) 미만에서는 블러 없이 그린다.
 * @property startCap 열린 경로의 시작점 모양
 * @property endCap 열린 경로의 끝점 모양
 * @property fillToken 채우기 색으로 쓸 디자인 토큰 이름([SceneDocument.tokens]). 채우기 색을 직접 고치면 토큰 연결이 끊긴다.
 * @property strokeToken 선 색으로 쓸 디자인 토큰 이름. 동작은 [fillToken]과 같다.
 */
public data class NodeStyle(
    val fill: ArgbColor? = null,
    val stroke: ArgbColor? = null,
    val strokeWidth: Double = 0.0,
    val opacity: Double = 1.0,
    val shadows: List<Shadow> = emptyList(),
    val gradient: Gradient? = null,
    val strokeDash: List<Double> = emptyList(),
    val blur: Double = 0.0,
    val startCap: LineCap = LineCap.None,
    val endCap: LineCap = LineCap.None,
    val fillToken: String? = null,
    val strokeToken: String? = null,
)

/** 열린 경로의 시작·끝 모양. 화살표와 원은 선 두께에 비례한 크기로 그린다. */
public enum class LineCap(public val wireName: String) {
    None("none"),
    Arrow("arrow"),
    Circle("circle"),
    ;

    public companion object {
        public fun fromWireName(name: String): LineCap? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * 그림자. 값은 모두 문서 단위다.
 *
 * @property blur 블러 반지름. 0 이상이며 클수록 흐리다.
 * @property spread 바깥 그림자는 모양을 이만큼 넓혀서 만들고 음수면 좁힌다. 안쪽 그림자는 클수록 안쪽으로 두꺼워진다.
 * @property inner `true`면 모양 안쪽 가장자리에 그리는 안쪽 그림자다.
 */
public data class Shadow(
    val color: ArgbColor,
    val offsetX: Double = 0.0,
    val offsetY: Double = 4.0,
    val blur: Double = 12.0,
    val spread: Double = 0.0,
    val inner: Boolean = false,
)

/**
 * 그라디언트. [stops]는 위치(0..1) 순서이며 2개 이상이다.
 *
 * @property angleDegrees 선형 그라디언트의 방향(도). 0이면 왼쪽에서 오른쪽, 90이면 위에서 아래로 진행한다.
 * 원형 그라디언트는 가운데에서 바깥으로 퍼지므로 이 값을 쓰지 않는다.
 */
public data class Gradient(val type: GradientType, val stops: List<GradientStop>, val angleDegrees: Double = 90.0)

public enum class GradientType(public val wireName: String) {
    Linear("linear"),
    Radial("radial"),
    ;

    public companion object {
        public fun fromWireName(name: String): GradientType? = entries.firstOrNull { it.wireName == name }
    }
}

/** 그라디언트 색 지점 하나. [position]은 0..1이다. */
public data class GradientStop(val position: Double, val color: ArgbColor)

/** 모서리별 반지름. 문서 단위이며 0 이상이다. */
public data class CornerRadii(val topLeft: Double, val topRight: Double, val bottomRight: Double, val bottomLeft: Double) {
    internal val values: List<Double> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    public companion object {
        public fun all(radius: Double): CornerRadii = CornerRadii(radius, radius, radius, radius)
    }
}

/** 데이터에 바인딩할 수 있는 속성. [wireName]은 JSON에 저장할 때 쓰는 이름이다. */
public enum class BoundProperty(public val wireName: String) {
    /** 텍스트 노드의 내용. 데이터의 문자열이나 숫자를 쓴다. */
    Text("text"),

    /** 표시 여부. 데이터의 불리언 값을 쓴다. */
    Visible("visible"),

    /** 이미지 노드의 URI. 데이터의 문자열을 쓰며 이미지 URI 규칙을 똑같이 적용한다. */
    ImageUri("imageUri"),

    /** 채우기 색. 데이터의 색을 쓴다. */
    Fill("fill"),

    /** 불투명도 0..1. 데이터의 숫자를 쓴다. */
    Opacity("opacity"),

    /** 컴포넌트 인스턴스의 변형([ComponentVariants]). 데이터의 문자열을 변형 이름으로 쓰며, 같은 세트에 없는 이름이면 바꾸지 않는다. */
    Variant("variant"),
    ;

    public companion object {
        public fun fromWireName(name: String): BoundProperty? = entries.firstOrNull { it.wireName == name }
    }
}
