// 예제 장면의 배치 값은 시연용 데이터라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber", "LongParameterList")

package com.naury.kinetica.showcase

import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.animation.TimelineMarker
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.PathCommand
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.TextAlignment

/** 기능을 한눈에 보는 예제: 노드 종류 5가지, 그룹, 이동·회전·배율·불투명도·색 애니메이션, marker. */
object DemoScene {
    fun create(): SceneDocument {
        val nodes = listOf(
            node(
                "title",
                NodeContent.Text(Size(900.0, 140.0), "Kinetica", fontSize = 110.0, alignment = TextAlignment.Center),
                90.0,
                160.0,
                0xFFF2F4F8,
            ),
            node(
                "subtitle",
                NodeContent.Text(Size(900.0, 80.0), "Spatial canvas × motion", fontSize = 48.0, alignment = TextAlignment.Center),
                90.0,
                320.0,
                0xFF9AA3B2,
            ),
            node(
                "card",
                NodeContent.Rectangle(Size(760.0, 460.0), cornerRadius = 48.0),
                160.0,
                520.0,
                0xFF7C8CFF,
                pivot = Vec2(380.0, 230.0),
            ),
            node("orb", NodeContent.Ellipse(Size(220.0, 220.0)), 430.0, 640.0, 0xFF64C8FF, pivot = Vec2(110.0, 110.0)),
            node(
                "wave",
                NodeContent.Path(
                    listOf(
                        PathCommand.MoveTo(Vec2(0.0, 60.0)),
                        PathCommand.CubicTo(Vec2(150.0, -40.0), Vec2(300.0, 160.0), Vec2(450.0, 60.0)),
                        PathCommand.CubicTo(Vec2(600.0, -40.0), Vec2(750.0, 160.0), Vec2(900.0, 60.0)),
                    ),
                ),
                90.0,
                1160.0,
                null,
                stroke = 0xFFF0B35A,
            ),
            SceneNode(NodeId("chips"), NodeContent.Group(listOf(NodeId("chip-a"), NodeId("chip-b"), NodeId("chip-c"))), name = "chips"),
            node("chip-a", NodeContent.Rectangle(Size(240.0, 120.0), cornerRadius = 60.0), 90.0, 1420.0, 0xFF5ED6A0, parent = "chips"),
            node("chip-b", NodeContent.Rectangle(Size(240.0, 120.0), cornerRadius = 60.0), 420.0, 1420.0, 0xFFF0B35A, parent = "chips"),
            node("chip-c", NodeContent.Rectangle(Size(240.0, 120.0), cornerRadius = 60.0), 750.0, 1420.0, 0xFFFF7A90, parent = "chips"),
        )
        return SceneDocument(
            documentId = "studio",
            artboard = Artboard(1080.0, 1920.0, ArgbColor(0xFF22262F.toInt())),
            nodes = nodes.associateBy { it.id },
            rootOrder = listOf("title", "subtitle", "card", "orb", "wave", "chips").map(::NodeId),
            timelines = listOf(timeline()),
        )
    }

    private fun timeline(): Timeline = Timeline(
        id = "main",
        durationMs = 2_400,
        markers = listOf(TimelineMarker("card-in", 600)),
        tracks = listOf(
            track("title", AnimatableProperty.Opacity, 0L to 0.0, 500L to 1.0),
            track("title", AnimatableProperty.TranslationY, 0L to 220.0, 500L to 160.0, easing = EasingSpec.EaseOut),
            track("card", AnimatableProperty.ScaleX, 300L to 0.6, 900L to 1.0, easing = EasingSpec.Spring(0.45, 260.0)),
            track("card", AnimatableProperty.ScaleY, 300L to 0.6, 900L to 1.0, easing = EasingSpec.Spring(0.45, 260.0)),
            track("card", AnimatableProperty.Opacity, 300L to 0.0, 600L to 1.0),
            track("orb", AnimatableProperty.RotationDegrees, 600L to 0.0, 2_400L to 180.0),
            track("orb", AnimatableProperty.TranslationX, 600L to 430.0, 1_500L to 230.0, 2_400L to 630.0, easing = EasingSpec.EaseInOut),
            PropertyTrack(
                NodeId("orb"),
                AnimatableProperty.Fill,
                listOf(
                    Keyframe(600, KeyValue.Color(ArgbColor(0xFF64C8FF.toInt()))),
                    Keyframe(2_400, KeyValue.Color(ArgbColor(0xFFFF7A90.toInt()))),
                ),
            ),
            track("chip-a", AnimatableProperty.TranslationY, 900L to 1520.0, 1_300L to 1420.0, easing = EasingSpec.EaseOut),
            track("chip-b", AnimatableProperty.TranslationY, 1_000L to 1520.0, 1_400L to 1420.0, easing = EasingSpec.EaseOut),
            track("chip-c", AnimatableProperty.TranslationY, 1_100L to 1520.0, 1_500L to 1420.0, easing = EasingSpec.EaseOut),
        ),
    )

    private fun track(id: String, property: AnimatableProperty, vararg keys: Pair<Long, Double>, easing: EasingSpec = EasingSpec.Linear) =
        PropertyTrack(NodeId(id), property, keys.map { (time, value) -> Keyframe(time, KeyValue.Scalar(value), easing) })

    private fun node(
        id: String,
        content: NodeContent,
        x: Double,
        y: Double,
        fill: Long?,
        pivot: Vec2 = Vec2.Zero,
        parent: String? = null,
        stroke: Long? = null,
    ) = SceneNode(
        id = NodeId(id),
        content = content,
        parentId = parent?.let(::NodeId),
        name = id,
        transform = Transform2D(translationX = x, translationY = y, pivotX = pivot.x, pivotY = pivot.y),
        style = NodeStyle(
            fill = fill?.let { ArgbColor(it.toInt()) },
            stroke = stroke?.let { ArgbColor(it.toInt()) },
            strokeWidth = if (stroke != null) 12.0 else 0.0,
        ),
    )
}
