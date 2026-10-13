package com.naury.kinetica.core.scene

import com.naury.kinetica.core.geometry.Mat3
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.geometry.normalizeDegrees
import kotlin.math.max

/**
 * 제스처가 쓰는 변형 계산. 모두 제스처를 시작할 때의 문서([SceneGraph])를 기준으로 절대값을 계산하므로 프레임마다 오차가 쌓이지 않는다.
 * 결과는 명령으로 적용한다.
 */
public object TransformOps {
    /** 크기 조절로 줄일 수 있는 최소 너비·높이(문서 단위). 반전은 만들지 않는다. */
    public const val MIN_EXTENT: Double = 1.0

    /** [ids]를 문서 좌표에서 [documentDelta]만큼 옮긴 local 변환. 부모가 회전·확대돼 있어도 화면에서 손가락을 따라간다. */
    public fun move(graph: SceneGraph, ids: Collection<NodeId>, documentDelta: Vec2): Map<NodeId, Transform2D> = ids.mapNotNull { id ->
        val node = graph.document.nodes[id] ?: return@mapNotNull null
        val parentInverse = parentMatrix(graph, node).inverseOrNull() ?: return@mapNotNull null
        val parentDelta = parentInverse.mapVector(documentDelta)
        id to node.transform.copy(
            translationX = node.transform.translationX + parentDelta.x,
            translationY = node.transform.translationY + parentDelta.y,
        )
    }.toMap()

    /**
     * [handle]을 문서 좌표 [documentPointer]로 끌어 크기를 바꾼 노드. 반대쪽 손잡이는 문서 좌표에서 제자리에 있다.
     *
     * 크기가 있는 노드(사각형·타원·글자·이미지)는 내용 크기를 바꾸고, 경로·그룹은 배율을 바꾼다.
     * 크기는 node-local 축 방향으로 바뀌며 [MIN_EXTENT]보다 작아지지 않는다.
     *
     * @param keepAspect 모서리 손잡이에서 가로·세로 비율을 유지한다.
     * @return 계산할 수 없으면(범위 없음, 역변환 없음, 회전 손잡이) `null`
     */
    public fun resize(
        graph: SceneGraph,
        id: NodeId,
        handle: SelectionHandle,
        documentPointer: Vec2,
        keepAspect: Boolean = false,
    ): SceneNode? {
        val node = graph.document.nodes[id] ?: return null
        val bounds = localExtent(graph, node) ?: return null
        val world = graph.worldMatrix(id) ?: return null
        val axes = HandleAxes.of(handle) ?: return null
        val pointerLocal = world.inverseOrNull()?.map(documentPointer) ?: return null
        val box = resizedBox(bounds, axes, pointerLocal, keepAspect)
        val anchorBefore = Vec2(axes.anchorX(bounds), axes.anchorY(bounds))
        val anchorAfter = Vec2(axes.anchorX(box), axes.anchorY(box))
        val fx = box.width / bounds.width
        val fy = box.height / bounds.height
        val resized = withExtent(node, Size(box.width, box.height), fx, fy)
        val sizeBased = node.content.hasSize()
        val transform = resized.transform.let {
            if (sizeBased) it.copy(pivotX = it.pivotX * fx, pivotY = it.pivotY * fy) else it
        }
        // 크기 기반 노드는 local 원점이 새 상자의 왼쪽 위, 배율 기반 노드는 원래 local 좌표 그대로다.
        val newLocalAnchor = if (sizeBased) Vec2(anchorAfter.x - box.left, anchorAfter.y - box.top) else anchorBefore
        val pinned = pinTranslation(graph, node, transform, newLocalAnchor, world.map(anchorBefore))
        return resized.copy(transform = pinned)
    }

    /**
     * local 상자 가운데를 축으로 [deltaDegrees]만큼 더 돌린 local 변환. 가운데는 문서 좌표에서 제자리에 있다.
     * [snapDegrees]가 0보다 크면 최종 각도를 그 배수로 맞춘다.
     */
    public fun rotate(graph: SceneGraph, id: NodeId, deltaDegrees: Double, snapDegrees: Double = 0.0): Transform2D? {
        val node = graph.document.nodes[id] ?: return null
        val bounds = localExtent(graph, node) ?: return null
        val world = graph.worldMatrix(id) ?: return null
        val raw = node.transform.rotationDegrees + deltaDegrees
        val angle = if (snapDegrees > 0.0) Math.round(raw / snapDegrees) * snapDegrees else raw
        val turned = node.transform.copy(rotationDegrees = normalizeDegrees(angle))
        return pinTranslation(graph, node, turned, bounds.center, world.map(bounds.center))
    }

    private fun NodeContent.hasSize(): Boolean = this !is NodeContent.Path && this !is NodeContent.Group

    /** 크기가 있는 노드는 내용 크기를 [size]로, 경로·그룹은 배율을 [fx]·[fy]배로 바꾼다. */
    private fun withExtent(node: SceneNode, size: Size, fx: Double, fy: Double): SceneNode = when (val content = node.content) {
        is NodeContent.Rectangle -> node.copy(content = content.copy(size = size))

        is NodeContent.Ellipse -> node.copy(content = content.copy(size = size))

        is NodeContent.Polygon -> node.copy(content = content.copy(size = size))

        is NodeContent.Text -> node.copy(content = content.copy(size = size))

        is NodeContent.Image -> node.copy(content = content.copy(size = size))

        is NodeContent.Frame -> node.copy(content = content.copy(size = size))

        is NodeContent.HostSlot -> node.copy(content = content.copy(size = size))

        is NodeContent.Path, is NodeContent.Group ->
            node.copy(transform = node.transform.copy(scaleX = node.transform.scaleX * fx, scaleY = node.transform.scaleY * fy))
    }

    /** [transform]의 이동만 바꿔 local 점 [localAnchor]가 문서 좌표 [documentAnchor]에 오게 한다. */
    private fun pinTranslation(
        graph: SceneGraph,
        node: SceneNode,
        transform: Transform2D,
        localAnchor: Vec2,
        documentAnchor: Vec2,
    ): Transform2D {
        val parentInverse = parentMatrix(graph, node).inverseOrNull() ?: return transform
        val target = parentInverse.map(documentAnchor)
        val placed = transform.copy(translationX = 0.0, translationY = 0.0).matrix().map(localAnchor)
        return transform.copy(translationX = target.x - placed.x, translationY = target.y - placed.y)
    }

    private fun parentMatrix(graph: SceneGraph, node: SceneNode): Mat3 = node.parentId?.let(graph::worldMatrix) ?: Mat3.Identity

    /** 크기를 잴 local 상자. 그룹은 자식 범위를 그룹 local 좌표로 되돌려 감싼다. 너비·높이가 0이면 `null`. */
    private fun localExtent(graph: SceneGraph, node: SceneNode): Rect? {
        val local = node.localBounds ?: run {
            val inverse = graph.worldMatrix(node.id)?.inverseOrNull() ?: return null
            graph.worldBounds(node.id)?.let { Rect.enclosing(it.corners().map(inverse::map)) }
        }
        return local?.takeIf { it.width > 0.0 && it.height > 0.0 }
    }

    private fun resizedBox(bounds: Rect, axes: HandleAxes, pointer: Vec2, keepAspect: Boolean): Rect {
        var left = bounds.left
        var top = bounds.top
        var right = bounds.right
        var bottom = bounds.bottom
        when (axes.x) {
            -1 -> left = minOf(pointer.x, right - MIN_EXTENT)
            1 -> right = maxOf(pointer.x, left + MIN_EXTENT)
        }
        when (axes.y) {
            -1 -> top = minOf(pointer.y, bottom - MIN_EXTENT)
            1 -> bottom = maxOf(pointer.y, top + MIN_EXTENT)
        }
        if (!keepAspect || axes.x == 0 || axes.y == 0) return Rect(left, top, right, bottom)
        val factor = max((right - left) / bounds.width, (bottom - top) / bounds.height)
        val width = bounds.width * factor
        val height = bounds.height * factor
        return Rect(
            if (axes.x < 0) bounds.right - width else bounds.left,
            if (axes.y < 0) bounds.bottom - height else bounds.top,
            if (axes.x < 0) bounds.right else bounds.left + width,
            if (axes.y < 0) bounds.bottom else bounds.top + height,
        )
    }

    /** 손잡이가 움직이는 축 방향. -1은 왼쪽·위, 1은 오른쪽·아래, 0은 그 축을 바꾸지 않는다. 고정점은 반대쪽이다. */
    private class HandleAxes(val x: Int, val y: Int) {
        fun anchorX(box: Rect): Double = when (x) {
            -1 -> box.right
            1 -> box.left
            else -> box.center.x
        }

        fun anchorY(box: Rect): Double = when (y) {
            -1 -> box.bottom
            1 -> box.top
            else -> box.center.y
        }

        companion object {
            fun of(handle: SelectionHandle): HandleAxes? = when (handle) {
                SelectionHandle.TopLeft -> HandleAxes(-1, -1)
                SelectionHandle.Top -> HandleAxes(0, -1)
                SelectionHandle.TopRight -> HandleAxes(1, -1)
                SelectionHandle.Right -> HandleAxes(1, 0)
                SelectionHandle.BottomRight -> HandleAxes(1, 1)
                SelectionHandle.Bottom -> HandleAxes(0, 1)
                SelectionHandle.BottomLeft -> HandleAxes(-1, 1)
                SelectionHandle.Left -> HandleAxes(-1, 0)
                SelectionHandle.Rotate -> null
            }
        }
    }
}
