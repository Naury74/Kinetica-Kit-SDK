package com.naury.kinetica.core.testing

import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode

/** 테스트용 문서를 짧게 만드는 함수 모음. */
public object TestScenes {
    public val artboard: Artboard = Artboard(1080.0, 1920.0)

    public fun id(value: String): NodeId = NodeId(value)

    public fun rect(
        id: String,
        parent: String? = null,
        transform: Transform2D = Transform2D.Identity,
        size: Size = Size(100.0, 50.0),
        fill: ArgbColor = ArgbColor(0xFF4466FF.toInt()),
    ): SceneNode = SceneNode(
        id = NodeId(id),
        content = NodeContent.Rectangle(size),
        parentId = parent?.let(::NodeId),
        transform = transform,
        style = NodeStyle(fill = fill),
    )

    public fun group(id: String, children: List<String>, parent: String? = null, transform: Transform2D = Transform2D.Identity): SceneNode =
        SceneNode(
            id = NodeId(id),
            content = NodeContent.Group(children.map(::NodeId)),
            parentId = parent?.let(::NodeId),
            transform = transform,
        )

    public fun document(
        nodes: List<SceneNode>,
        rootOrder: List<String> = nodes.filter { it.parentId == null }.map { it.id.value },
        timelines: List<Timeline> = emptyList(),
    ): SceneDocument = SceneDocument(
        documentId = "test-doc",
        artboard = artboard,
        nodes = nodes.associateBy { it.id },
        rootOrder = rootOrder.map(::NodeId),
        timelines = timelines,
    )

    /** 최상위 사각형 둘과, 사각형 둘을 담은 그룹 하나. */
    public fun sample(): SceneDocument = document(
        listOf(
            rect("a", transform = Transform2D(translationX = 100.0, translationY = 200.0)),
            rect("b", transform = Transform2D(translationX = 400.0, translationY = 200.0, rotationDegrees = 30.0)),
            group("g", listOf("c", "d"), transform = Transform2D(translationX = 100.0, translationY = 800.0)),
            rect("c", parent = "g"),
            rect("d", parent = "g", transform = Transform2D(translationX = 200.0)),
        ),
        rootOrder = listOf("a", "b", "g"),
    )
}
