package com.naury.kinetica.core.scene

import com.naury.kinetica.core.geometry.Mat3
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Vec2
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * 문서 좌표의 점 아래에 있는 노드를 찾는다.
 *
 * 맨 위 노드부터 거꾸로 훑는다. 축 정렬 범위로 먼저 거른 뒤, 점을 node-local 좌표로 되돌려 도형별로 정확히 판정한다.
 * 손가락 여유 [slopDocument]는 문서 단위이며, 화면 dp 값을 `dp × density / zoom`으로 바꿔 넘긴다.
 * 회전·비등방 배율 노드에서는 여유가 local 축 방향으로 대략 맞춰진다.
 */
public class HitTester(private val graph: SceneGraph) {
    /**
     * @param includeLocked 잠긴 노드도 맞힐지. 화면 조작은 `false`다.
     * @return 맨 위 노드 id. 그룹은 맞히지 않고 그 안의 도형을 맞힌다.
     */
    public fun hitTest(documentPoint: Vec2, slopDocument: Double = 0.0, includeLocked: Boolean = false): NodeId? {
        val slop = max(0.0, slopDocument)
        return graph.drawOrder.asReversed().firstOrNull { id ->
            val node = graph.document.nodes.getValue(id)
            node.content !is NodeContent.Group &&
                graph.isEffectivelyVisible(id) &&
                (includeLocked || !graph.isEffectivelyLocked(id)) &&
                hits(id, node, documentPoint, slop)
        }
    }

    /** 문서 좌표 [area]와 범위가 겹치는 도형 노드. 그리기 순서대로다. 사각 선택(marquee)에 쓴다. */
    public fun nodesIntersecting(area: Rect, includeLocked: Boolean = false): List<NodeId> = graph.drawOrder.filter { id ->
        val node = graph.document.nodes.getValue(id)
        node.content !is NodeContent.Group &&
            graph.isEffectivelyVisible(id) &&
            (includeLocked || !graph.isEffectivelyLocked(id)) &&
            graph.worldBounds(id)?.intersects(area) == true
    }

    private fun hits(id: NodeId, node: SceneNode, documentPoint: Vec2, slop: Double): Boolean {
        val bounds = graph.worldBounds(id) ?: return false
        if (!bounds.inflate(slop).contains(documentPoint)) return false
        val world = graph.worldMatrix(id) ?: return false
        val inverse = world.inverseOrNull() ?: return false
        val localPoint = inverse.map(documentPoint)
        val localSlop = slop * localScale(inverse)
        return when (val content = node.content) {
            is NodeContent.Group -> false
            is NodeContent.Ellipse -> insideEllipse(Rect.fromSize(content.size), localPoint, localSlop)
            is NodeContent.Path -> nearPath(content, localPoint, localSlop + node.style.strokeWidth / 2.0)
            else -> node.localBounds?.inflate(localSlop)?.contains(localPoint) == true
        }
    }

    /** 문서 거리 1이 local 좌표에서 얼마인지의 근사값(두 축 배율의 평균). */
    private fun localScale(inverse: Mat3): Double = (hypot(inverse.a, inverse.b) + hypot(inverse.c, inverse.d)) / 2.0

    private fun insideEllipse(box: Rect, point: Vec2, slop: Double): Boolean {
        val radiusX = box.width / 2.0 + slop
        val radiusY = box.height / 2.0 + slop
        if (radiusX <= 0.0 || radiusY <= 0.0) return false
        val dx = (point.x - box.center.x) / radiusX
        val dy = (point.y - box.center.y) / radiusY
        return dx * dx + dy * dy <= 1.0
    }

    /** 닫힌 경로는 안쪽(짝홀 규칙)도 맞힌다. 곡선은 [PathFlattening]으로 선분으로 근사한다. */
    private fun nearPath(path: NodeContent.Path, point: Vec2, tolerance: Double): Boolean {
        val polylines = PathFlattening.polylines(path.commands)
        val nearSegment = polylines.any { line -> line.points.zipWithNext().any { (a, b) -> distanceToSegment(point, a, b) <= tolerance } }
        return nearSegment || polylines.any { it.closed && containsEvenOdd(it.points, point) }
    }

    private fun distanceToSegment(point: Vec2, a: Vec2, b: Vec2): Double {
        val segment = b - a
        val lengthSquared = segment.x * segment.x + segment.y * segment.y
        if (lengthSquared == 0.0) return point.distanceTo(a)
        val t = (((point.x - a.x) * segment.x + (point.y - a.y) * segment.y) / lengthSquared).coerceIn(0.0, 1.0)
        return point.distanceTo(a + segment * t)
    }

    private fun containsEvenOdd(points: List<Vec2>, point: Vec2): Boolean {
        var inside = false
        points.indices.forEach { i ->
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val crosses = (a.y > point.y) != (b.y > point.y)
            if (crosses && abs(b.y - a.y) > 0.0 && point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        }
        return inside
    }
}
