package com.naury.kinetica.core.scene

import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2

/**
 * scene graph의 노드 하나. 도형은 [content]가 정한 node-local 좌표에 정의되고 [transform]으로 부모 좌표에 놓인다.
 *
 * 생성자는 구조를 검사하지 않는다. 문서에 넣기 전에 [SceneValidator]가 부모·자식 관계와 값 범위를 확인한다.
 *
 * @property parentId 부모 그룹 id. 최상위 노드면 `null`이며 이때는 [SceneDocument.rootOrder]에 있어야 한다.
 * @property name 레이어 이름. 화면 표시용이며 로그에 남기지 않는다.
 * @property locked 잠긴 노드는 화면 조작으로 선택·변형되지 않는다. 명령으로는 바꿀 수 있다.
 * @property metadata 호스트가 붙이는 문자열 값. SDK는 해석하지 않는다.
 * @property bindings 속성을 호스트 데이터 키에 묶는다. 데이터에 값이 있으면 저장된 값 대신 쓴다(docs 30).
 * @property action 사용 모드에서 누르면 호스트에 넘기는 동작 이름. 코드를 실행하지 않는다.
 * @property constraints 부모(Frame·artboard) 크기가 바뀔 때 따라가는 방식. 자동 배치 Frame의 자식이면 쓰지 않는다.
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
) {
    public val kind: NodeKind get() = content.kind

    /** node-local 좌표의 도형 범위. 그룹은 자식에서 계산하므로 `null`이다. */
    public val localBounds: Rect? get() = content.localBounds()
}

/** 노드 종류. [wireName]은 저장 형식에서 쓰는 이름이다. */
public enum class NodeKind(public val wireName: String) {
    Group("group"),
    Rectangle("rect"),
    Ellipse("ellipse"),
    Path("path"),
    Text("text"),
    Image("image"),
    Frame("frame"),
    HostSlot("host"),
    ;

    public companion object {
        public fun fromWireName(name: String): NodeKind? = entries.firstOrNull { it.wireName == name }
    }
}

/** 노드 종류별 내용. 크기와 도형 좌표는 모두 node-local 문서 단위다. */
public sealed interface NodeContent {
    public val kind: NodeKind

    public fun localBounds(): Rect?

    /** 자식을 갖는 노드. 목록 순서가 z-order이며 뒤에 있을수록 위에 그려진다. 자식 좌표는 이 노드의 local 좌표다. */
    public sealed interface Container : NodeContent {
        public val childIds: List<NodeId>

        /** 자식 목록만 바꾼 같은 종류의 내용. */
        public fun withChildren(childIds: List<NodeId>): Container
    }

    /** 범위 없이 자식만 묶는다. 범위는 자식에서 계산한다. */
    public data class Group(override val childIds: List<NodeId> = emptyList()) : Container {
        override val kind: NodeKind get() = NodeKind.Group

        override fun localBounds(): Rect? = null

        override fun withChildren(childIds: List<NodeId>): Group = copy(childIds = childIds)
    }

    /**
     * 크기·배경·모서리·테두리·그림자를 갖는 컨테이너(Figma의 frame). 배경은 `style.fill`, 테두리는 `style.stroke`다.
     *
     * @property clip `true`면 자식을 이 상자(모서리 포함) 밖으로 그리지 않는다.
     * @property layout 자식 자동 배치. `null`이면 자식은 각자 위치에 있고 [SceneNode.constraints]로 Frame 크기를 따라간다.
     */
    public data class Frame(
        val size: Size,
        override val childIds: List<NodeId> = emptyList(),
        val cornerRadius: Double = 0.0,
        val clip: Boolean = true,
        val layout: AutoLayout? = null,
    ) : Container {
        override val kind: NodeKind get() = NodeKind.Frame

        override fun localBounds(): Rect = Rect.fromSize(size)

        override fun withChildren(childIds: List<NodeId>): Frame = copy(childIds = childIds)
    }

    /**
     * 호스트 앱이 [key]로 주는 Composable(또는 View)을 [size] 상자에 그린다. 문서에는 키·크기·배치만 저장한다.
     * 호스트가 키를 모르면 빈 틀로 그린다.
     */
    public data class HostSlot(val size: Size, val key: String) : NodeContent {
        override val kind: NodeKind get() = NodeKind.HostSlot

        override fun localBounds(): Rect = Rect.fromSize(size)
    }

    /** `(0, 0)`에서 시작하는 사각형. [cornerRadius]는 문서 단위이며 짧은 변의 절반을 넘으면 절반으로 그린다. */
    public data class Rectangle(val size: Size, val cornerRadius: Double = 0.0) : NodeContent {
        override val kind: NodeKind get() = NodeKind.Rectangle

        override fun localBounds(): Rect = Rect.fromSize(size)
    }

    /** `(0, 0)`에서 시작하는 [size] 사각형에 내접하는 타원. */
    public data class Ellipse(val size: Size) : NodeContent {
        override val kind: NodeKind get() = NodeKind.Ellipse

        override fun localBounds(): Rect = Rect.fromSize(size)
    }

    /** 직선·2차·3차 곡선으로 이루어진 경로. 범위는 제어점을 포함한 볼록 껍질 기준이라 실제 곡선보다 클 수 있다. */
    public data class Path(val commands: List<PathCommand>) : NodeContent {
        override val kind: NodeKind get() = NodeKind.Path

        override fun localBounds(): Rect? {
            val points = commands.flatMap(PathCommand::points)
            return if (points.isEmpty()) null else Rect.enclosing(points)
        }
    }

    /**
     * [size] 상자 안에 줄을 바꿔 배치하는 글자. 상자를 넘는 줄은 잘린다.
     *
     * @property fontSize 문서 단위 글자 크기
     * @property fontWeight 100..900(400 보통, 700 굵게)
     * @property lineHeight 문서 단위 줄 높이. 0이면 글꼴 기본값이다.
     * @property letterSpacing 문서 단위 자간
     */
    public data class Text(
        val size: Size,
        val text: String,
        val fontSize: Double = 16.0,
        val alignment: TextAlignment = TextAlignment.Start,
        val fontWeight: Int = 400,
        val lineHeight: Double = 0.0,
        val letterSpacing: Double = 0.0,
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

/** 경로 명령. 점은 node-local 문서 좌표다. */
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
 * 노드 모양. 그룹은 [opacity]만 쓰고, 글자는 [fill]을 글자 색으로 쓰며, 이미지는 [fill]·[stroke]를 쓰지 않는다.
 *
 * @property strokeWidth 문서 단위 선 두께. 0이면 선을 그리지 않는다.
 * @property opacity 0..1. 부모 그룹의 opacity와 곱해진다.
 * @property shadow 사각형·타원·Frame·HostSlot 아래에 그리는 그림자
 */
public data class NodeStyle(
    val fill: ArgbColor? = null,
    val stroke: ArgbColor? = null,
    val strokeWidth: Double = 0.0,
    val opacity: Double = 1.0,
    val shadow: Shadow? = null,
)

/**
 * 그림자. 값은 모두 문서 단위다.
 *
 * @property blur 0 이상. 클수록 흐리다.
 */
public data class Shadow(val color: ArgbColor, val offsetX: Double = 0.0, val offsetY: Double = 4.0, val blur: Double = 12.0)

/** 데이터에 묶을 수 있는 속성. [wireName]은 저장 형식 이름이다. */
public enum class BoundProperty(public val wireName: String) {
    /** 글자 노드의 내용. 데이터의 글자·숫자를 쓴다. */
    Text("text"),

    /** 보임. 데이터의 참·거짓을 쓴다. */
    Visible("visible"),

    /** 이미지 노드의 URI. 데이터의 글자를 쓴다(이미지 URI 규칙을 그대로 지킨다). */
    ImageUri("imageUri"),

    /** 채우기 색. 데이터의 색을 쓴다. */
    Fill("fill"),

    /** 불투명도 0..1. 데이터의 숫자를 쓴다. */
    Opacity("opacity"),
    ;

    public companion object {
        public fun fromWireName(name: String): BoundProperty? = entries.firstOrNull { it.wireName == name }
    }
}
