package com.naury.kinetica.core.scene

import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2

/** 맞출 기준선. 문서 좌표의 축 정렬 범위 기준이다. */
public enum class AlignMode { Left, CenterHorizontal, Right, Top, CenterVertical, Bottom }

/** 나눌 방향. */
public enum class DistributeAxis { Horizontal, Vertical }

/**
 * 정렬·분배 계산(docs 07). 결과는 노드별 새 local 변환이며 [com.naury.kinetica.core.command.SceneCommand.TransformNodes]로 적용한다.
 * 범위는 축 정렬 범위를 쓰므로 회전한 노드는 감싸는 사각형 기준으로 맞춘다.
 */
public object ArrangeOps {
    /** 노드가 둘 이상이면 선택 범위에, 하나면 artboard에 맞춘다. 범위가 없는 노드는 빼고, 이미 맞은 노드는 결과에 넣지 않는다. */
    public fun align(graph: SceneGraph, ids: List<NodeId>, mode: AlignMode): Map<NodeId, Transform2D> {
        val bounds = ids.associateWith(graph::worldBounds).filterValues { it != null }.mapValues { it.value!! }
        if (bounds.isEmpty()) return emptyMap()
        val artboard = graph.document.artboard
        val target = if (bounds.size == 1) Rect(0.0, 0.0, artboard.width, artboard.height) else bounds.values.reduce(Rect::union)
        return bounds.mapNotNull { (id, rect) ->
            val delta = when (mode) {
                AlignMode.Left -> Vec2(target.left - rect.left, 0.0)
                AlignMode.CenterHorizontal -> Vec2(target.center.x - rect.center.x, 0.0)
                AlignMode.Right -> Vec2(target.right - rect.right, 0.0)
                AlignMode.Top -> Vec2(0.0, target.top - rect.top)
                AlignMode.CenterVertical -> Vec2(0.0, target.center.y - rect.center.y)
                AlignMode.Bottom -> Vec2(0.0, target.bottom - rect.bottom)
            }
            if (delta == Vec2.Zero) null else TransformOps.move(graph, listOf(id), delta)[id]?.let { id to it }
        }.toMap()
    }

    /**
     * 셋 이상인 노드 사이 간격을 같게 한다. 가운데 순서로 정렬해 양 끝 노드는 그대로 두고 사이 노드를 옮긴다.
     * 노드가 셋보다 적으면 빈 결과다.
     */
    public fun distribute(graph: SceneGraph, ids: List<NodeId>, axis: DistributeAxis): Map<NodeId, Transform2D> {
        val horizontal = axis == DistributeAxis.Horizontal
        fun Rect.start() = if (horizontal) left else top
        fun Rect.end() = if (horizontal) right else bottom
        fun Rect.middle() = if (horizontal) center.x else center.y
        val items = ids.mapNotNull { id -> graph.worldBounds(id)?.let { id to it } }.sortedBy { it.second.middle() }
        if (items.size < MIN_DISTRIBUTE) return emptyMap()
        val span = items.last().second.end() - items.first().second.start()
        val gap = (span - items.sumOf { it.second.end() - it.second.start() }) / (items.size - 1)
        var cursor = items.first().second.end() + gap
        return items.drop(1).dropLast(1).mapNotNull { (id, rect) ->
            val shift = cursor - rect.start()
            cursor += rect.end() - rect.start() + gap
            val delta = if (horizontal) Vec2(shift, 0.0) else Vec2(0.0, shift)
            if (shift == 0.0) null else TransformOps.move(graph, listOf(id), delta)[id]?.let { id to it }
        }.toMap()
    }

    private const val MIN_DISTRIBUTE = 3
}
