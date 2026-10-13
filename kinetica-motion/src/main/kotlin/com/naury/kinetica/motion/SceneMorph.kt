package com.naury.kinetica.motion

import com.naury.kinetica.core.geometry.Mat3
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.fromMatrix
import com.naury.kinetica.core.geometry.shortestDeltaDegrees
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.scene.SceneNode
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** 원본 장면의 노드 [sourceId]가 대상 장면의 [targetId]로 바뀐다. */
public data class TransitionMapping(val sourceId: NodeId, val targetId: NodeId)

public sealed interface MorphResult {
    public data class Ready(val morph: SceneMorph) : MorphResult

    /** 한 노드가 여러 노드와 짝지어졌다(docs 09: 일대다 금지). */
    public data class AmbiguousMapping(val nodeId: NodeId) : MorphResult

    public data class UnknownNode(val nodeId: NodeId) : MorphResult
}

/**
 * 두 장면 사이의 전환(docs 09 내부 morph). 진행률마다 그릴 장면을 만든다. 두 문서는 바꾸지 않는다.
 *
 * - 짝지은 노드가 같은 종류(사각형·타원·이미지끼리, 글자는 내용까지 같을 때)면 world 변환·크기·모서리·색·불투명도·선 두께를 보간한다.
 * - 종류가 다른 짝은 원본이 사라지며 대상이 나타난다(dissolve).
 * - 짝이 없는 원본 노드는 사라지고 대상 노드는 나타난다.
 *
 * 결과 장면은 계층이 없는 평평한 목록이다. 그룹 변환은 각 노드에 합쳐지며, 기울임은 회전·배율로 근사한다.
 * 진행률 0이면 원본, 1이면 대상과 같은 모습이다. artboard는 대상 것을 쓴다.
 */
public class SceneMorph private constructor(
    private val source: Flattened,
    private val target: Flattened,
    private val pairs: Map<NodeId, NodeId>,
    private val artboardDocument: SceneDocument,
) {
    /** [progress](0..1, 넘치면 맞춤)에서 그릴 장면. easing은 호출하는 쪽이 적용한다. */
    public fun frame(progress: Double): SceneDocument {
        val p = if (progress.isNaN()) 0.0 else progress.coerceIn(0.0, 1.0)
        val output = ArrayList<SceneNode>()
        val matchedSources = pairs.keys
        source.leaves.filter { it.node.id !in matchedSources }.forEach { output += it.faded(1.0 - p) }
        target.leaves.forEach { targetLeaf ->
            val sourceId = pairs.entries.firstOrNull { it.value == targetLeaf.node.id }?.key
            val sourceLeaf = sourceId?.let(source::leaf)
            when {
                sourceLeaf == null -> output += targetLeaf.faded(p)

                compatible(sourceLeaf.node, targetLeaf.node) -> output += blend(sourceLeaf, targetLeaf, p)

                else -> {
                    output += sourceLeaf.faded(1.0 - p)
                    output += targetLeaf.faded(p)
                }
            }
        }
        val nodes = output.mapIndexed { index, node -> node.copy(id = NodeId("f$index"), parentId = null) }
        return artboardDocument.copy(nodes = nodes.associateBy { it.id }, rootOrder = nodes.map { it.id }, timelines = emptyList())
    }

    private fun blend(from: Leaf, to: Leaf, p: Double): SceneNode {
        val a = from.node
        val b = to.node
        val transform = Transform2D(
            translationX = lerp(a.transform.translationX, b.transform.translationX, p),
            translationY = lerp(a.transform.translationY, b.transform.translationY, p),
            scaleX = lerp(a.transform.scaleX, b.transform.scaleX, p),
            scaleY = lerp(a.transform.scaleY, b.transform.scaleY, p),
            rotationDegrees =
                a.transform.rotationDegrees + shortestDeltaDegrees(a.transform.rotationDegrees, b.transform.rotationDegrees) * p,
            pivotX = lerp(a.transform.pivotX, b.transform.pivotX, p),
            pivotY = lerp(a.transform.pivotY, b.transform.pivotY, p),
        )
        val style = b.style.copy(
            fill = lerpColor(a.style.fill, b.style.fill, p),
            stroke = lerpColor(a.style.stroke, b.style.stroke, p),
            strokeWidth = lerp(a.style.strokeWidth, b.style.strokeWidth, p),
            opacity = lerp(from.alpha, to.alpha, p),
        )
        return b.copy(transform = transform, style = style, content = blendContent(a.content, b.content, p))
    }

    private fun blendContent(a: NodeContent, b: NodeContent, p: Double): NodeContent = when {
        a is NodeContent.Rectangle && b is NodeContent.Rectangle ->
            b.copy(size = lerpSize(a.size, b.size, p), cornerRadius = lerp(a.cornerRadius, b.cornerRadius, p))

        a is NodeContent.Ellipse && b is NodeContent.Ellipse -> b.copy(size = lerpSize(a.size, b.size, p))

        a is NodeContent.Image && b is NodeContent.Image -> b.copy(size = lerpSize(a.size, b.size, p))

        a is NodeContent.Frame && b is NodeContent.Frame ->
            b.copy(size = lerpSize(a.size, b.size, p), cornerRadius = lerp(a.cornerRadius, b.cornerRadius, p), childIds = emptyList())

        a is NodeContent.HostSlot && b is NodeContent.HostSlot -> b.copy(size = lerpSize(a.size, b.size, p))

        a is NodeContent.Text && b is NodeContent.Text -> b.copy(
            size = lerpSize(a.size, b.size, p),
            fontSize = lerp(a.fontSize, b.fontSize, p),
        )

        else -> b
    }

    /** 노드를 world 변환·누적 불투명도로 펼친 것. pivot은 도형 가운데라 회전이 가운데를 축으로 보간된다. */
    private class Leaf(val node: SceneNode, val alpha: Double) {
        fun faded(factor: Double): SceneNode = node.copy(style = node.style.copy(opacity = (alpha * factor).coerceIn(0.0, 1.0)))
    }

    private class Flattened(val leaves: List<Leaf>) {
        private val byId = leaves.associateBy { it.node.id }

        fun leaf(id: NodeId): Leaf? = byId[id]
    }

    public companion object {
        /** 짝짓기 기준 metadata 키. 두 장면에서 같은 값이면 짝이 된다. */
        public const val MORPH_KEY: String = "morphKey"

        /**
         * [mappings]가 없으면 `morphKey` metadata가 같은 노드, 그다음 id가 같은 노드끼리 짝짓는다. 그룹은 짝짓지 않고 자식을 짝짓는다.
         */
        public fun create(source: SceneDocument, target: SceneDocument, mappings: List<TransitionMapping>? = null): MorphResult {
            val from = flatten(source)
            val to = flatten(target)
            val pairs = mappings ?: defaultMappings(from, to)
            val unknown = pairs.firstOrNull { from.leaf(it.sourceId) == null }?.sourceId
                ?: pairs.firstOrNull { to.leaf(it.targetId) == null }?.targetId
            val repeated = pairs.groupBy { it.sourceId }.entries.firstOrNull { it.value.size > 1 }?.key
                ?: pairs.groupBy { it.targetId }.entries.firstOrNull { it.value.size > 1 }?.key
            return when {
                unknown != null -> MorphResult.UnknownNode(unknown)
                repeated != null -> MorphResult.AmbiguousMapping(repeated)
                else -> MorphResult.Ready(SceneMorph(from, to, pairs.associate { it.sourceId to it.targetId }, target))
            }
        }

        private fun defaultMappings(from: Flattened, to: Flattened): List<TransitionMapping> {
            val byKey = to.leaves.mapNotNull { leaf -> leaf.node.metadata[MORPH_KEY]?.let { it to leaf.node.id } }.toMap()
            val used = HashSet<NodeId>()
            return from.leaves.mapNotNull { leaf ->
                val targetId = leaf.node.metadata[MORPH_KEY]?.let(byKey::get) ?: leaf.node.id.takeIf { to.leaf(it) != null }
                targetId?.takeIf(used::add)?.let { TransitionMapping(leaf.node.id, it) }
            }
        }

        private fun flatten(document: SceneDocument): Flattened {
            val graph = SceneGraph(document)
            val leaves = graph.drawOrder.mapNotNull { id ->
                val node = document.nodes.getValue(id)
                val world = graph.worldMatrix(id)
                if (node.content is NodeContent.Group || world == null || !graph.isEffectivelyVisible(id)) return@mapNotNull null
                val alpha = graph.ancestry(id).fold(1.0) { acc, ancestor -> acc * ancestor.style.opacity }
                val center = node.localBounds?.center
                val flatContent = (node.content as? NodeContent.Frame)?.copy(childIds = emptyList()) ?: node.content
                Leaf(
                    node.copy(content = flatContent, transform = decompose(world, center?.x ?: 0.0, center?.y ?: 0.0), parentId = null),
                    alpha,
                )
            }
            return Flattened(leaves)
        }

        /** 기울임이 있으면 회전·배율만 남긴 근사값을 쓴다. */
        private fun decompose(world: Mat3, pivotX: Double, pivotY: Double): Transform2D =
            Transform2D.fromMatrix(world, pivotX, pivotY) ?: run {
                val scaleX = hypot(world.a, world.b)
                val scaleY = hypot(world.c, world.d).coerceAtLeast(Transform2D.MIN_SCALE_MAGNITUDE * 2)
                Transform2D(
                    translationX = world.tx,
                    translationY = world.ty,
                    scaleX = scaleX.coerceAtLeast(Transform2D.MIN_SCALE_MAGNITUDE * 2),
                    scaleY = scaleY,
                    rotationDegrees = Math.toDegrees(atan2(world.b, world.a)),
                )
            }

        private fun compatible(a: SceneNode, b: SceneNode): Boolean = when (val content = a.content) {
            is NodeContent.Rectangle, is NodeContent.Ellipse, is NodeContent.Image, is NodeContent.Frame ->
                content::class ==
                    b.content::class

            is NodeContent.HostSlot -> (b.content as? NodeContent.HostSlot)?.key == content.key

            is NodeContent.Text -> (b.content as? NodeContent.Text)?.text == content.text

            is NodeContent.Path -> a.content == b.content

            is NodeContent.Group -> false
        }

        private fun lerp(a: Double, b: Double, p: Double): Double = if (p >= 1.0) b else a + (b - a) * p

        private fun lerpSize(a: Size, b: Size, p: Double): Size = Size(lerp(a.width, b.width, p), lerp(a.height, b.height, p))

        /** 한쪽이 없으면 다른 쪽 색을 투명하게 한 것과 보간한다. */
        private fun lerpColor(a: ArgbColor?, b: ArgbColor?, p: Double): ArgbColor? {
            if (a == null && b == null) return null
            val from = a ?: ArgbColor(checkNotNull(b).argb and RGB_MASK)
            val to = b ?: ArgbColor(checkNotNull(a).argb and RGB_MASK)
            fun channel(x: Int, y: Int) = (x + (y - x) * p).roundToInt().coerceIn(0, MAX_CHANNEL)
            return ArgbColor.fromChannels(
                channel(from.alpha, to.alpha),
                channel(from.red, to.red),
                channel(from.green, to.green),
                channel(from.blue, to.blue),
            )
        }

        private const val RGB_MASK = 0x00FFFFFF
        private const val MAX_CHANNEL = 255
    }
}
