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
    ;

    public companion object {
        public fun fromWireName(name: String): NodeKind? = entries.firstOrNull { it.wireName == name }
    }
}

/** 노드 종류별 내용. 크기와 도형 좌표는 모두 node-local 문서 단위다. */
public sealed interface NodeContent {
    public val kind: NodeKind

    public fun localBounds(): Rect?

    /** 자식 노드를 순서대로 가진다. 목록 순서가 z-order이며 뒤에 있을수록 위에 그려진다. */
    public data class Group(val childIds: List<NodeId> = emptyList()) : NodeContent {
        override val kind: NodeKind get() = NodeKind.Group

        override fun localBounds(): Rect? = null
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
     */
    public data class Text(
        val size: Size,
        val text: String,
        val fontSize: Double = 16.0,
        val alignment: TextAlignment = TextAlignment.Start,
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
 */
public data class NodeStyle(
    val fill: ArgbColor? = null,
    val stroke: ArgbColor? = null,
    val strokeWidth: Double = 0.0,
    val opacity: Double = 1.0,
)
