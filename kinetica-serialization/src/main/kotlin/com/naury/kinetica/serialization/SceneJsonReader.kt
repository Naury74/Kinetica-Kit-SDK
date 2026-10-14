package com.naury.kinetica.serialization

import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.KeyValueType
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.animation.TimelineMarker
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.BoundProperty
import com.naury.kinetica.core.scene.LayoutConstraints
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeEffects
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeKind
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.PathCommand
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneLimits
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.SizeLimits
import com.naury.kinetica.core.scene.TextAlignment
import com.naury.kinetica.serialization.JsonKeys as K
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * 형식 버전 1의 JSON 트리를 문서로 바꾼다. 필드 형식만 확인하고, 문서 규칙은 이후 [com.naury.kinetica.core.scene.SceneValidator]가 본다.
 * 개수 상한은 객체를 만들기 전에 확인한다.
 */
internal class SceneJsonReader(private val limits: SceneLimits) {
    private var keyframeCount = 0L

    fun read(root: JsonObject): SceneDocument {
        val canvasPath = "$.${K.CANVAS}"
        val canvas = root.required(K.CANVAS, "$").asObject(canvasPath)
        val artboard =
            Artboard(canvas.finite(K.WIDTH, canvasPath), canvas.finite(K.HEIGHT, canvasPath), canvas.colorOrNull(K.BACKGROUND, canvasPath))
        return SceneDocument(
            documentId = root.string(K.DOCUMENT_ID, "$"),
            artboard = artboard,
            nodes = readNodes(root.required(K.NODES, "$").asArray(K.NODES)),
            rootOrder = root.required(K.ROOT_ORDER, "$").asArray(K.ROOT_ORDER).mapIndexed { i, element ->
                element.asNodeId("${K.ROOT_ORDER}[$i]")
            },
            timelines = readTimelines(root.optional(K.ANIMATIONS)?.asArray(K.ANIMATIONS)),
            extensions = root.optional(K.EXTENSIONS)?.asObject(K.EXTENSIONS)?.mapValues { (_, value) -> value.toString() }.orEmpty(),
            tokens = ComponentJson.readTokens(root),
            schemaVersion = SceneDocument.CURRENT_SCHEMA_VERSION,
        )
    }

    private fun readNodes(array: JsonArray): Map<NodeId, SceneNode> {
        if (array.size > limits.maxNodes) fail(K.NODES, "노드 수가 상한 ${limits.maxNodes}개를 넘습니다.")
        val nodes = LinkedHashMap<NodeId, SceneNode>(array.size)
        array.forEachIndexed { index, element ->
            val path = "${K.NODES}[$index]"
            val node = readNode(element.asObject(path), path)
            if (nodes.put(node.id, node) != null) fail("$path.${K.ID}", "id가 겹칩니다.")
        }
        return nodes
    }

    private fun readNode(json: JsonObject, path: String): SceneNode {
        val kind = NodeKind.fromWireName(json.string(K.TYPE, path)) ?: fail("$path.${K.TYPE}", "지원하지 않는 노드 종류입니다.")
        return SceneNode(
            id = json.required(K.ID, path).asNodeId("$path.${K.ID}"),
            content = readContent(kind, json, path),
            parentId = json.optional(K.PARENT_ID)?.asNodeId("$path.${K.PARENT_ID}"),
            name = json.optional(K.NAME)?.asString("$path.${K.NAME}").orEmpty(),
            transform = json.optional(K.TRANSFORM)?.let { readTransform(it.asObject("$path.${K.TRANSFORM}"), "$path.${K.TRANSFORM}") }
                ?: Transform2D.Identity,
            style = json.optional(K.STYLE)?.let { readStyle(it.asObject("$path.${K.STYLE}"), "$path.${K.STYLE}") } ?: NodeStyle(),
            visible = json.booleanOr(K.VISIBLE, path, default = true),
            locked = json.booleanOr(K.LOCKED, path, default = false),
            metadata = json.optional(K.METADATA)?.asObject("$path.${K.METADATA}")
                ?.mapValues { (key, value) -> value.asString("$path.${K.METADATA}.$key") }
                .orEmpty(),
            bindings = readBindings(json, path),
            action = json.optional(K.ACTION)?.asString("$path.${K.ACTION}"),
            constraints =
                json.optional(K.CONSTRAINTS)?.let {
                    LayoutJson.readConstraints(it.asObject("$path.${K.CONSTRAINTS}"), "$path.${K.CONSTRAINTS}")
                }
                    ?: LayoutConstraints.Default,
            layoutFill = json.booleanOr(K.LAYOUT_FILL, path, default = false),
            sizeLimits = json.optional(K.SIZE_LIMITS)?.let {
                LayoutJson.readSizeLimits(it.asObject("$path.${K.SIZE_LIMITS}"), "$path.${K.SIZE_LIMITS}")
            } ?: SizeLimits.None,
            effects = json.optional(K.EFFECTS)?.let { EffectsJson.read(it.asObject("$path.${K.EFFECTS}"), "$path.${K.EFFECTS}") }
                ?: NodeEffects.None,
            component = json.optional(K.COMPONENT)?.asString("$path.${K.COMPONENT}"),
            instanceOf = json.optional(K.INSTANCE_OF)?.let {
                ComponentJson.readInstance(it.asObject("$path.${K.INSTANCE_OF}"), "$path.${K.INSTANCE_OF}")
            },
        )
    }

    private fun readBindings(json: JsonObject, path: String): Map<BoundProperty, String> {
        val bindings = json.optional(K.BINDINGS)?.asObject("$path.${K.BINDINGS}") ?: return emptyMap()
        return bindings.entries.associate { (name, value) ->
            val property = BoundProperty.fromWireName(name) ?: fail("$path.${K.BINDINGS}", "지원하지 않는 바인딩 속성입니다.")
            property to value.asString("$path.${K.BINDINGS}.$name")
        }
    }

    // 노드 종류마다 한 갈래인 분기다.
    @Suppress("CyclomaticComplexMethod")
    private fun readContent(kind: NodeKind, json: JsonObject, path: String): NodeContent = when (kind) {
        NodeKind.Group -> NodeContent.Group(readChildren(json, path))

        NodeKind.Rectangle -> NodeContent.Rectangle(
            json.size(K.BOUNDS, path),
            json.finiteOr(K.CORNER_RADIUS, path, 0.0),
            StyleJson.readCorners(json, path),
        )

        NodeKind.Polygon -> NodeContent.Polygon(
            json.size(K.BOUNDS, path),
            json.optional(K.SIDES)?.asInt("$path.${K.SIDES}") ?: DEFAULT_SIDES,
            json.finiteOr(K.INNER_RATIO, path, 1.0),
        )

        NodeKind.Ellipse -> NodeContent.Ellipse(json.size(K.BOUNDS, path))

        NodeKind.Path -> NodeContent.Path(readPath(json.required(K.PATH, path).asArray("$path.${K.PATH}"), "$path.${K.PATH}"))

        NodeKind.Text -> NodeContent.Text(
            size = json.size(K.BOUNDS, path),
            text = json.string(K.TEXT, path),
            fontSize = json.finiteOr(K.FONT_SIZE, path, DEFAULT_FONT_SIZE),
            alignment = json.optional(K.ALIGN)?.let { element ->
                val name = element.asString("$path.${K.ALIGN}")
                TextAlignment.entries.firstOrNull { it.name.lowercase() == name }
                    ?: fail("$path.${K.ALIGN}", "start·center·end 중 하나여야 합니다.")
            } ?: TextAlignment.Start,
            fontWeight = json.optional(K.FONT_WEIGHT)?.asInt("$path.${K.FONT_WEIGHT}") ?: DEFAULT_FONT_WEIGHT,
            lineHeight = json.finiteOr(K.LINE_HEIGHT, path, 0.0),
            letterSpacing = json.finiteOr(K.LETTER_SPACING, path, 0.0),
            fontFamily = json.optional(K.FONT_FAMILY)?.asString("$path.${K.FONT_FAMILY}"),
            italic = json.booleanOr(K.ITALIC, path, default = false),
            decoration = TextJson.readDecoration(json, path),
            spans = TextJson.readSpans(json, path),
            autoSize = TextJson.readAutoSize(json, path),
        )

        NodeKind.Image -> NodeContent.Image(json.size(K.BOUNDS, path), json.string(K.URI, path))

        NodeKind.Frame -> NodeContent.Frame(
            size = json.size(K.BOUNDS, path),
            childIds = readChildren(json, path),
            cornerRadius = json.finiteOr(K.CORNER_RADIUS, path, 0.0),
            clip = json.booleanOr(K.CLIP, path, default = true),
            corners = StyleJson.readCorners(json, path),
            layout = json.optional(K.LAYOUT)?.let { LayoutJson.readLayout(it.asObject("$path.${K.LAYOUT}"), "$path.${K.LAYOUT}") },
        )

        NodeKind.HostSlot -> NodeContent.HostSlot(json.size(K.BOUNDS, path), json.string(K.KEY, path))
    }

    private fun readChildren(json: JsonObject, path: String): List<NodeId> = json.optional(K.CHILDREN)?.asArray("$path.${K.CHILDREN}")
        ?.mapIndexed { i, element -> element.asNodeId("$path.${K.CHILDREN}[$i]") }
        .orEmpty()

    private fun readTransform(json: JsonObject, path: String): Transform2D = Transform2D(
        translationX = json.finiteOr(K.X, path, 0.0),
        translationY = json.finiteOr(K.Y, path, 0.0),
        scaleX = json.finiteOr(K.SCALE_X, path, 1.0),
        scaleY = json.finiteOr(K.SCALE_Y, path, 1.0),
        rotationDegrees = json.finiteOr(K.ROTATION, path, 0.0),
        pivotX = json.finiteOr(K.PIVOT_X, path, 0.0),
        pivotY = json.finiteOr(K.PIVOT_Y, path, 0.0),
    )

    private fun readStyle(json: JsonObject, path: String): NodeStyle = NodeStyle(
        fill = json.colorOrNull(K.FILL, path),
        stroke = json.colorOrNull(K.STROKE, path),
        strokeWidth = json.finiteOr(K.STROKE_WIDTH, path, 0.0),
        opacity = json.finiteOr(K.OPACITY, path, 1.0),
        shadows = StyleJson.readShadows(json, path),
        gradient = StyleJson.readGradient(json, path),
        strokeDash = StyleJson.readDash(json, path),
        blur = json.finiteOr(K.LAYER_BLUR, path, 0.0),
        startCap = StyleJson.readCap(json, K.START_CAP, path),
        endCap = StyleJson.readCap(json, K.END_CAP, path),
        fillToken = json.optional(K.FILL_TOKEN)?.asString("$path.${K.FILL_TOKEN}"),
        strokeToken = json.optional(K.STROKE_TOKEN)?.asString("$path.${K.STROKE_TOKEN}"),
    )

    private fun readPath(array: JsonArray, path: String): List<PathCommand> {
        if (array.size > limits.maxPathCommands) fail(path, "경로 명령 수가 상한을 넘습니다.")
        return array.mapIndexed { index, element ->
            val itemPath = "$path[$index]"
            val json = element.asObject(itemPath)
            when (json.string(K.OP, itemPath)) {
                K.OP_MOVE -> PathCommand.MoveTo(json.point(K.X, K.Y, itemPath))

                K.OP_LINE -> PathCommand.LineTo(json.point(K.X, K.Y, itemPath))

                K.OP_QUAD -> PathCommand.QuadTo(json.point(K.CX, K.CY, itemPath), json.point(K.X, K.Y, itemPath))

                K.OP_CUBIC -> PathCommand.CubicTo(
                    json.point(K.C1X, K.C1Y, itemPath),
                    json.point(K.C2X, K.C2Y, itemPath),
                    json.point(K.X, K.Y, itemPath),
                )

                K.OP_CLOSE -> PathCommand.Close

                else -> fail("$itemPath.${K.OP}", "M·L·Q·C·Z 중 하나여야 합니다.")
            }
        }
    }

    private fun readTimelines(array: JsonArray?): List<Timeline> {
        if (array == null) return emptyList()
        if (array.size > limits.maxTimelines) fail(K.ANIMATIONS, "timeline 수가 상한을 넘습니다.")
        return array.mapIndexed { index, element ->
            val path = "${K.ANIMATIONS}[$index]"
            val json = element.asObject(path)
            Timeline(
                id = json.string(K.ID, path),
                durationMs = json.required(K.DURATION_MS, path).asLong("$path.${K.DURATION_MS}"),
                tracks = json.optional(K.TRACKS)?.asArray("$path.${K.TRACKS}")
                    ?.mapIndexed { i, track -> readTrack(track.asObject("$path.${K.TRACKS}[$i]"), "$path.${K.TRACKS}[$i]") }
                    .orEmpty(),
                markers = json.optional(K.MARKERS)?.asArray("$path.${K.MARKERS}")
                    ?.mapIndexed { i, marker ->
                        val markerPath = "$path.${K.MARKERS}[$i]"
                        val markerJson = marker.asObject(markerPath)
                        TimelineMarker(markerJson.string(K.NAME, markerPath), markerJson.required(K.TIME_MS, markerPath).asLong(markerPath))
                    }
                    .orEmpty(),
                fpsHint = json.optional(K.FPS_HINT)?.asInt("$path.${K.FPS_HINT}") ?: Timeline.DEFAULT_FPS,
            )
        }
    }

    private fun readTrack(json: JsonObject, path: String): PropertyTrack {
        val property = AnimatableProperty.fromWireName(json.string(K.PROPERTY, path))
            ?: fail("$path.${K.PROPERTY}", "지원하지 않는 속성입니다.")
        val keys = json.required(K.KEYFRAMES, path).asArray("$path.${K.KEYFRAMES}")
        keyframeCount += keys.size
        if (keyframeCount > limits.maxTotalKeyframes) fail("$path.${K.KEYFRAMES}", "keyframe 수가 상한을 넘습니다.")
        return PropertyTrack(
            nodeId = json.required(K.NODE_ID, path).asNodeId("$path.${K.NODE_ID}"),
            property = property,
            keyframes = keys.mapIndexed { i, key ->
                readKeyframe(key.asObject("$path.${K.KEYFRAMES}[$i]"), property, "$path.${K.KEYFRAMES}[$i]")
            },
        )
    }

    private fun readKeyframe(json: JsonObject, property: AnimatableProperty, path: String): Keyframe {
        val value = json.required(K.VALUE, path)
        return Keyframe(
            timeMs = json.required(K.TIME_MS, path).asLong("$path.${K.TIME_MS}"),
            value = when (property.valueType) {
                KeyValueType.Scalar -> KeyValue.Scalar(value.asFinite("$path.${K.VALUE}"))
                KeyValueType.Color -> KeyValue.Color(value.asColor("$path.${K.VALUE}"))
            },
            easing = json.optional(K.EASING)?.let {
                readEasing(it.asObject("$path.${K.EASING}"), "$path.${K.EASING}")
            } ?: EasingSpec.Linear,
        )
    }

    private fun readEasing(json: JsonObject, path: String): EasingSpec = when (json.string(K.TYPE, path)) {
        K.EASING_LINEAR -> EasingSpec.Linear

        K.EASING_BEZIER -> EasingSpec.CubicBezier(
            json.finite(K.X1, path),
            json.finite(K.Y1, path),
            json.finite(K.X2, path),
            json.finite(K.Y2, path),
        )

        K.EASING_STEPS -> EasingSpec.Steps(json.required(K.COUNT, path).asInt("$path.${K.COUNT}"))

        K.EASING_SPRING -> EasingSpec.Spring(json.finite(K.DAMPING_RATIO, path), json.finite(K.STIFFNESS, path))

        else -> fail("$path.${K.TYPE}", "지원하지 않는 easing입니다.")
    }

    private companion object {
        const val DEFAULT_FONT_SIZE = 16.0
        const val DEFAULT_FONT_WEIGHT = 400
        const val DEFAULT_SIDES = 5
    }
}
