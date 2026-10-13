package com.naury.kinetica.core.scene

import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.geometry.Viewport

/**
 * 선택 테두리. 노드 하나를 고르면 노드와 함께 돌아간 사각형이고, 여러 개나 그룹이면 축 정렬 사각형이다.
 *
 * @property corners 문서 좌표의 네 꼭짓점. 왼쪽 위, 오른쪽 위, 오른쪽 아래, 왼쪽 아래 순서(노드 local 축 기준)다.
 */
public data class SelectionFrame(val corners: List<Vec2>) {
    init {
        require(corners.size == CORNER_COUNT) { "꼭짓점은 4개여야 합니다." }
    }

    public val center: Vec2 get() = (corners[0] + corners[2]) * HALF

    /** viewport 좌표의 손잡이 위치. 회전 손잡이는 위 변 가운데에서 바깥으로 [rotateOffsetPx] 떨어져 있다. */
    public fun handles(viewport: Viewport, rotateOffsetPx: Double): Map<SelectionHandle, Vec2> {
        val points = corners.map(viewport::toViewport)
        val topLeft = points[0]
        val topRight = points[1]
        val bottomRight = points[2]
        val bottomLeft = points.last()
        val top = (topLeft + topRight) * HALF
        val bottom = (bottomLeft + bottomRight) * HALF
        val outward = (top - bottom).let { direction ->
            val length = direction.length()
            if (length == 0.0) Vec2(0.0, -1.0) else direction * (1.0 / length)
        }
        return mapOf(
            SelectionHandle.TopLeft to topLeft,
            SelectionHandle.Top to top,
            SelectionHandle.TopRight to topRight,
            SelectionHandle.Right to (topRight + bottomRight) * HALF,
            SelectionHandle.BottomRight to bottomRight,
            SelectionHandle.Bottom to bottom,
            SelectionHandle.BottomLeft to bottomLeft,
            SelectionHandle.Left to (topLeft + bottomLeft) * HALF,
            SelectionHandle.Rotate to top + outward * rotateOffsetPx,
        )
    }

    /** viewport 좌표 [viewportPoint]가 테두리 안(경계 포함)에 있는지. 테두리는 볼록 사각형이다. */
    public fun contains(viewport: Viewport, viewportPoint: Vec2): Boolean {
        val points = corners.map(viewport::toViewport)
        val signs = points.indices.map { i ->
            val a = points[i]
            val b = points[(i + 1) % points.size]
            (b.x - a.x) * (viewportPoint.y - a.y) - (b.y - a.y) * (viewportPoint.x - a.x)
        }
        return signs.all { it >= 0.0 } || signs.all { it <= 0.0 }
    }

    public companion object {
        private const val CORNER_COUNT = 4
        private const val HALF = 0.5

        /** [ids]의 선택 테두리. 그릴 범위가 없으면 `null`. */
        public fun of(graph: SceneGraph, ids: List<NodeId>): SelectionFrame? {
            val single = ids.singleOrNull()?.let(graph.document.nodes::get)
            val local = single?.localBounds
            val world = single?.let { graph.worldMatrix(it.id) }
            if (local != null && world != null) return SelectionFrame(local.corners().map(world::map))
            return ids.mapNotNull(graph::worldBounds).reduceOrNull(Rect::union)?.let { SelectionFrame(it.corners()) }
        }
    }
}

/** 선택 테두리의 손잡이. 반대쪽 손잡이가 크기 조절의 고정점이다. */
public enum class SelectionHandle {
    TopLeft,
    Top,
    TopRight,
    Right,
    BottomRight,
    Bottom,
    BottomLeft,
    Left,
    Rotate,
    ;

    /** viewport 좌표 [viewportPoint]에서 [touchRadiusPx] 안에 있는 가장 가까운 손잡이. */
    public companion object {
        public fun at(handles: Map<SelectionHandle, Vec2>, viewportPoint: Vec2, touchRadiusPx: Double): SelectionHandle? = handles.entries
            .filter { it.value.distanceTo(viewportPoint) <= touchRadiusPx }
            .minByOrNull { it.value.distanceTo(viewportPoint) }
            ?.key
    }
}
