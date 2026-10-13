package com.naury.kinetica.editor

import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneNode
import java.util.UUID

/** 도구로 새로 만드는 노드. 가운데가 [center](문서 좌표)에 오고 pivot은 가운데다. */
internal object NodeFactory {
    private val palette = listOf(0xFF7C8CFF, 0xFF64C8FF, 0xFFF0B35A, 0xFF5ED6A0, 0xFFFF7A90).map { ArgbColor(it.toInt()) }
    private val shapeSize = Size(240.0, 160.0)
    private val textSize = Size(360.0, 80.0)
    private const val TEXT_FONT_SIZE = 48.0
    private const val WHITE = 0xFFFFFFFF

    fun newId(): NodeId = NodeId("n-" + UUID.randomUUID())

    fun rectangle(center: Vec2, sequence: Int): SceneNode = shape(NodeContent.Rectangle(shapeSize, cornerRadius = 24.0), center, sequence)

    fun ellipse(center: Vec2, sequence: Int): SceneNode = shape(NodeContent.Ellipse(shapeSize), center, sequence)

    fun text(center: Vec2, text: String): SceneNode = SceneNode(
        id = newId(),
        content = NodeContent.Text(textSize, text, fontSize = TEXT_FONT_SIZE),
        transform = centered(center, textSize),
        style = NodeStyle(fill = ArgbColor(WHITE.toInt())),
    )

    private fun shape(content: NodeContent, center: Vec2, sequence: Int): SceneNode = SceneNode(
        id = newId(),
        content = content,
        transform = centered(center, shapeSize),
        style = NodeStyle(fill = palette[Math.floorMod(sequence, palette.size)]),
    )

    private fun centered(center: Vec2, size: Size): Transform2D = Transform2D(
        translationX = center.x - size.width / 2.0,
        translationY = center.y - size.height / 2.0,
        pivotX = size.width / 2.0,
        pivotY = size.height / 2.0,
    )
}
