package com.naury.kinetica.serialization

import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.LayoutConstraints
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.PathCommand
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.SizeLimits
import com.naury.kinetica.core.scene.TextAlignment
import com.naury.kinetica.serialization.JsonKeys as K
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** 기본값과 같은 선택 필드는 쓰지 않는다. 그래야 같은 문서가 항상 같은 짧은 문자열이 된다. */
internal object SceneJsonWriter {
    private const val DEFAULT_FONT_WEIGHT = 400

    fun write(document: SceneDocument): JsonObject = buildJsonObject {
        put(K.SCHEMA_VERSION, document.schemaVersion)
        put(K.DOCUMENT_ID, document.documentId)
        putJsonObject(K.CANVAS) {
            putNumber(K.WIDTH, document.artboard.width)
            putNumber(K.HEIGHT, document.artboard.height)
            document.artboard.background?.let { put(K.BACKGROUND, it.toHex()) }
        }
        putJsonArray(K.NODES) { document.nodes.values.forEach { add(node(it)) } }
        putJsonArray(K.ROOT_ORDER) { document.rootOrder.forEach { add(JsonPrimitive(it.value)) } }
        putJsonArray(K.ANIMATIONS) { document.timelines.forEach { add(timeline(it)) } }
        if (document.extensions.isNotEmpty()) {
            putJsonObject(K.EXTENSIONS) { document.extensions.forEach { (key, raw) -> put(key, extensionValue(raw)) } }
        }
        if (document.tokens.isNotEmpty()) put(K.TOKENS, ComponentJson.tokens(document.tokens))
    }

    private fun node(node: SceneNode): JsonObject = buildJsonObject {
        put(K.ID, node.id.value)
        put(K.TYPE, node.kind.wireName)
        put(K.PARENT_ID, node.parentId?.value)
        if (node.name.isNotEmpty()) put(K.NAME, node.name)
        if (!node.visible) put(K.VISIBLE, false)
        if (node.locked) put(K.LOCKED, true)
        if (node.metadata.isNotEmpty()) putJsonObject(K.METADATA) { node.metadata.forEach { (key, value) -> put(key, value) } }
        if (node.bindings.isNotEmpty()) {
            putJsonObject(K.BINDINGS) {
                node.bindings.entries.sortedBy { it.key.ordinal }.forEach { (property, key) -> put(property.wireName, key) }
            }
        }
        node.action?.let { put(K.ACTION, it) }
        layout(node)
        node.component?.let { put(K.COMPONENT, it) }
        node.instanceOf?.let { put(K.INSTANCE_OF, ComponentJson.instance(it)) }
        put(K.TRANSFORM, transform(node.transform))
        content(node.content)
        put(K.STYLE, style(node.style))
    }

    // 노드 종류마다 한 갈래인 분기다.
    @Suppress("CyclomaticComplexMethod")
    private fun JsonObjectBuilder.content(content: NodeContent) {
        when (content) {
            is NodeContent.Group -> putJsonArray(K.CHILDREN) { content.childIds.forEach { add(JsonPrimitive(it.value)) } }

            is NodeContent.Rectangle -> {
                put(K.BOUNDS, size(content.size))
                if (content.cornerRadius != 0.0) putNumber(K.CORNER_RADIUS, content.cornerRadius)
                content.corners?.let { put(K.CORNERS, StyleJson.corners(it)) }
            }

            is NodeContent.Polygon -> {
                put(K.BOUNDS, size(content.size))
                put(K.SIDES, content.sides)
                if (content.innerRatio != 1.0) putNumber(K.INNER_RATIO, content.innerRatio)
            }

            is NodeContent.Ellipse -> put(K.BOUNDS, size(content.size))

            is NodeContent.Path -> putJsonArray(K.PATH) { content.commands.forEach { add(pathCommand(it)) } }

            is NodeContent.Text -> text(content)

            is NodeContent.Image -> {
                put(K.BOUNDS, size(content.size))
                put(K.URI, content.uri)
            }

            is NodeContent.Frame -> frame(content)

            is NodeContent.HostSlot -> {
                put(K.BOUNDS, size(content.size))
                put(K.KEY, content.key)
            }
        }
    }

    private fun JsonObjectBuilder.text(content: NodeContent.Text) {
        put(K.BOUNDS, size(content.size))
        put(K.TEXT, content.text)
        putNumber(K.FONT_SIZE, content.fontSize)
        if (content.alignment != TextAlignment.Start) put(K.ALIGN, content.alignment.name.lowercase())
        if (content.fontWeight != DEFAULT_FONT_WEIGHT) put(K.FONT_WEIGHT, content.fontWeight)
        if (content.lineHeight != 0.0) putNumber(K.LINE_HEIGHT, content.lineHeight)
        if (content.letterSpacing != 0.0) putNumber(K.LETTER_SPACING, content.letterSpacing)
        TextJson.write(this, content)
    }

    private fun JsonObjectBuilder.frame(content: NodeContent.Frame) {
        put(K.BOUNDS, size(content.size))
        if (content.cornerRadius != 0.0) putNumber(K.CORNER_RADIUS, content.cornerRadius)
        if (!content.clip) put(K.CLIP, false)
        content.corners?.let { put(K.CORNERS, StyleJson.corners(it)) }
        content.layout?.let { put(K.LAYOUT, LayoutJson.layout(it)) }
        putJsonArray(K.CHILDREN) { content.childIds.forEach { add(JsonPrimitive(it.value)) } }
    }

    private fun transform(transform: Transform2D): JsonObject = buildJsonObject {
        putNumber(K.X, transform.translationX)
        putNumber(K.Y, transform.translationY)
        putNumber(K.SCALE_X, transform.scaleX)
        putNumber(K.SCALE_Y, transform.scaleY)
        putNumber(K.ROTATION, transform.rotationDegrees)
        putNumber(K.PIVOT_X, transform.pivotX)
        putNumber(K.PIVOT_Y, transform.pivotY)
    }

    private fun size(size: Size): JsonObject = buildJsonObject {
        putNumber(K.WIDTH, size.width)
        putNumber(K.HEIGHT, size.height)
    }

    private fun style(style: NodeStyle): JsonObject = buildJsonObject {
        style.fill?.let { put(K.FILL, it.toHex()) }
        style.stroke?.let { put(K.STROKE, it.toHex()) }
        if (style.strokeWidth != 0.0) putNumber(K.STROKE_WIDTH, style.strokeWidth)
        putNumber(K.OPACITY, style.opacity)
        StyleJson.write(this, style)
    }

    private fun pathCommand(command: PathCommand): JsonObject = buildJsonObject {
        when (command) {
            is PathCommand.MoveTo -> put(K.OP, K.OP_MOVE)

            is PathCommand.LineTo -> put(K.OP, K.OP_LINE)

            is PathCommand.QuadTo -> {
                put(K.OP, K.OP_QUAD)
                putNumber(K.CX, command.control.x)
                putNumber(K.CY, command.control.y)
            }

            is PathCommand.CubicTo -> {
                put(K.OP, K.OP_CUBIC)
                putNumber(K.C1X, command.control1.x)
                putNumber(K.C1Y, command.control1.y)
                putNumber(K.C2X, command.control2.x)
                putNumber(K.C2Y, command.control2.y)
            }

            PathCommand.Close -> put(K.OP, K.OP_CLOSE)
        }
        command.points.lastOrNull()?.let {
            putNumber(K.X, it.x)
            putNumber(K.Y, it.y)
        }
    }

    private fun timeline(timeline: Timeline): JsonObject = buildJsonObject {
        put(K.ID, timeline.id)
        put(K.DURATION_MS, timeline.durationMs)
        put(K.FPS_HINT, timeline.fpsHint)
        putJsonArray(K.TRACKS) {
            timeline.tracks.forEach { track ->
                add(
                    buildJsonObject {
                        put(K.NODE_ID, track.nodeId.value)
                        put(K.PROPERTY, track.property.wireName)
                        put(K.KEYFRAMES, buildJsonArray { track.keyframes.forEach { add(keyframe(it)) } })
                    },
                )
            }
        }
        putJsonArray(K.MARKERS) {
            timeline.markers.forEach { marker ->
                add(
                    buildJsonObject {
                        put(K.NAME, marker.name)
                        put(K.TIME_MS, marker.timeMs)
                    },
                )
            }
        }
    }

    private fun keyframe(keyframe: Keyframe): JsonObject = buildJsonObject {
        put(K.TIME_MS, keyframe.timeMs)
        when (val value = keyframe.value) {
            is KeyValue.Scalar -> putNumber(K.VALUE, value.value)
            is KeyValue.Color -> put(K.VALUE, value.color.toHex())
        }
        if (keyframe.easing != EasingSpec.Linear) put(K.EASING, easing(keyframe.easing))
    }

    private fun easing(easing: EasingSpec): JsonObject = buildJsonObject {
        when (easing) {
            EasingSpec.Linear -> put(K.TYPE, K.EASING_LINEAR)

            is EasingSpec.CubicBezier -> {
                put(K.TYPE, K.EASING_BEZIER)
                putNumber(K.X1, easing.x1)
                putNumber(K.Y1, easing.y1)
                putNumber(K.X2, easing.x2)
                putNumber(K.Y2, easing.y2)
            }

            is EasingSpec.Steps -> {
                put(K.TYPE, K.EASING_STEPS)
                put(K.COUNT, easing.count)
            }

            is EasingSpec.Spring -> {
                put(K.TYPE, K.EASING_SPRING)
                putNumber(K.DAMPING_RATIO, easing.dampingRatio)
                putNumber(K.STIFFNESS, easing.stiffness)
            }
        }
    }

    private fun JsonObjectBuilder.putNumber(key: String, value: Double) {
        put(key, numberPrimitive(value))
    }

    /** 확장 값은 JSON 문자열이어야 한다. JSON이 아니면 문자열 값 하나로 쓴다. */
    private fun extensionValue(raw: String): JsonElement = try {
        Json.parseToJsonElement(raw)
    } catch (_: SerializationException) {
        JsonPrimitive(raw)
    }
}

/** 배치·크기 제한·움직임 효과. 기본값이면 쓰지 않는다. */
private fun JsonObjectBuilder.layout(node: SceneNode) {
    if (node.constraints != LayoutConstraints.Default) put(K.CONSTRAINTS, LayoutJson.constraints(node.constraints))
    if (node.layoutFill) put(K.LAYOUT_FILL, true)
    if (node.sizeLimits != SizeLimits.None) put(K.SIZE_LIMITS, LayoutJson.sizeLimits(node.sizeLimits))
    if (!node.effects.isEmpty) put(K.EFFECTS, EffectsJson.write(node.effects))
    if (!node.accessibility.isDefault) {
        putJsonObject(K.ACCESSIBILITY) {
            node.accessibility.label?.let { put(K.LABEL, it) }
            node.accessibility.role?.let { put(K.ROLE, it.wireName) }
            if (node.accessibility.hidden) put(K.HIDDEN, true)
        }
    }
}
