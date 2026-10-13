package com.naury.kinetica.editor

import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.Shadow
import java.util.UUID

/** 도구로 새로 만드는 노드. 가운데가 [center](문서 좌표)에 오고 pivot은 가운데다. */
internal object NodeFactory {
    private val palette = listOf(0xFF7C8CFF, 0xFF64C8FF, 0xFFF0B35A, 0xFF5ED6A0, 0xFFFF7A90).map { ArgbColor(it.toInt()) }
    private val shapeSize = Size(240.0, 160.0)
    private val textSize = Size(360.0, 80.0)
    private const val TEXT_FONT_SIZE = 48.0
    private const val WHITE = 0xFFFFFFFF
    private val frameSize = Size(360.0, 240.0)
    private const val FRAME_RADIUS = 28.0
    private const val SHADOW_COLOR = 0x33000000
    private const val SHADOW_Y = 8.0
    private const val SHADOW_BLUR = 24.0

    fun newId(): NodeId = NodeId("n-" + UUID.randomUUID())

    fun rectangle(center: Vec2, sequence: Int): SceneNode = shape(NodeContent.Rectangle(shapeSize, cornerRadius = 24.0), center, sequence)

    fun ellipse(center: Vec2, sequence: Int): SceneNode = shape(NodeContent.Ellipse(shapeSize), center, sequence)

    /** 흰 배경·둥근 모서리·옅은 그림자의 카드 모양 Frame. */
    fun frame(center: Vec2): SceneNode = SceneNode(
        id = newId(),
        content = NodeContent.Frame(frameSize, cornerRadius = FRAME_RADIUS),
        transform = centered(center, frameSize),
        style = NodeStyle(
            fill = ArgbColor(WHITE.toInt()),
            shadows = listOf(Shadow(ArgbColor(SHADOW_COLOR), offsetY = SHADOW_Y, blur = SHADOW_BLUR)),
        ),
    )

    fun hostView(center: Vec2, option: HostViewOption): SceneNode {
        val size = Size(option.width, option.height)
        return SceneNode(
            id = newId(),
            content = NodeContent.HostSlot(size, option.key),
            name = option.label,
            transform = centered(center, size),
        )
    }

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
