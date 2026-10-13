package com.naury.kinetica.guides

import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneGraph
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 옮기는 동안 선택 범위를 안내선에 붙인다(docs 07). 제스처 하나에 하나를 만들고 프레임마다 [snap]을 부른다.
 *
 * - 후보: artboard 가장자리·가운데, 보이고 잠기지 않았고 옮기지 않는 최상위 노드의 가장자리·가운데, 격자.
 * - 축마다 따로 고른다. 들어갈 거리 안의 후보 중 종류 순서([SnapKind]) → 화면 거리 → 노드 id 순으로 하나를 고른다.
 * - 한 번 붙으면 나갈 거리를 벗어날 때까지 같은 선에 머문다(떨림 방지).
 *
 * 범위는 축 정렬 범위로 계산하므로 회전한 노드는 감싸는 사각형의 가장자리를 쓴다.
 */
public class MoveSnapper(graph: SceneGraph, moving: Collection<NodeId>, private val config: SnapConfig) {
    private val movingBounds: Rect? = moving.mapNotNull(graph::worldBounds).reduceOrNull(Rect::union)
    private val vertical = ArrayList<Candidate>()
    private val horizontal = ArrayList<Candidate>()
    private var stickyX: Sticky? = null
    private var stickyY: Sticky? = null

    init {
        val artboard = graph.document.artboard
        if (config.snapToArtboard) {
            vertical += Candidate(0.0, SnapKind.ArtboardEdge, null, 0.0, artboard.height)
            vertical += Candidate(artboard.width / 2.0, SnapKind.ArtboardCenter, null, 0.0, artboard.height)
            vertical += Candidate(artboard.width, SnapKind.ArtboardEdge, null, 0.0, artboard.height)
            horizontal += Candidate(0.0, SnapKind.ArtboardEdge, null, 0.0, artboard.width)
            horizontal += Candidate(artboard.height / 2.0, SnapKind.ArtboardCenter, null, 0.0, artboard.width)
            horizontal += Candidate(artboard.height, SnapKind.ArtboardEdge, null, 0.0, artboard.width)
        }
        if (config.snapToNodes) addNodeCandidates(graph, moving.toSet())
    }

    private fun addNodeCandidates(graph: SceneGraph, moving: Set<NodeId>) {
        graph.document.rootOrder
            .filter { it !in moving && graph.isEffectivelyVisible(it) && !graph.isEffectivelyLocked(it) }
            .filterNot { graph.document.nodes[it]?.content.let { content -> content is NodeContent.Group && content.childIds.isEmpty() } }
            .forEach { id ->
                val bounds = graph.worldBounds(id) ?: return@forEach
                listOf(bounds.left to SnapKind.NodeEdge, bounds.center.x to SnapKind.NodeCenter, bounds.right to SnapKind.NodeEdge)
                    .forEach { (x, kind) -> vertical += Candidate(x, kind, id, bounds.top, bounds.bottom) }
                listOf(bounds.top to SnapKind.NodeEdge, bounds.center.y to SnapKind.NodeCenter, bounds.bottom to SnapKind.NodeEdge)
                    .forEach { (y, kind) -> horizontal += Candidate(y, kind, id, bounds.left, bounds.right) }
            }
    }

    /**
     * @param delta 제스처 시작 뒤 손가락이 움직인 문서 거리
     * @param zoom 지금 viewport 배율. 문서 거리를 화면 거리로 바꾸는 데 쓴다.
     */
    public fun snap(delta: Vec2, zoom: Double): SnapResult {
        val bounds = movingBounds ?: return SnapResult(delta, emptyList())
        val moved = Rect(bounds.left + delta.x, bounds.top + delta.y, bounds.right + delta.x, bounds.bottom + delta.y)
        val x = pick(listOf(moved.left, moved.center.x, moved.right), vertical, stickyX, zoom).also { stickyX = it }
        val y = pick(listOf(moved.top, moved.center.y, moved.bottom), horizontal, stickyY, zoom).also { stickyY = it }
        val snappedDelta = Vec2(delta.x + (x?.offset ?: 0.0), delta.y + (y?.offset ?: 0.0))
        val snapped = Rect(
            bounds.left + snappedDelta.x,
            bounds.top + snappedDelta.y,
            bounds.right + snappedDelta.x,
            bounds.bottom + snappedDelta.y,
        )
        val guides = listOfNotNull(
            x?.let { guide(GuideAxis.Vertical, it.candidate, snapped.top, snapped.bottom) },
            y?.let { guide(GuideAxis.Horizontal, it.candidate, snapped.left, snapped.right) },
        )
        return SnapResult(snappedDelta, guides)
    }

    private fun guide(axis: GuideAxis, candidate: Candidate, start: Double, end: Double) = SnapGuide(
        axis = axis,
        position = candidate.position,
        kind = candidate.kind,
        spanStart = if (candidate.kind == SnapKind.Grid) start else minOf(start, candidate.spanStart),
        spanEnd = if (candidate.kind == SnapKind.Grid) end else maxOf(end, candidate.spanEnd),
        sourceId = candidate.sourceId,
    )

    private fun pick(anchors: List<Double>, candidates: List<Candidate>, sticky: Sticky?, zoom: Double): Sticky? {
        if (sticky != null) {
            val anchor = anchors[sticky.anchorIndex]
            if (abs(sticky.candidate.position - anchor) * zoom <= config.exitThresholdPx) {
                return sticky.copy(offset = sticky.candidate.position - anchor)
            }
        }
        val options = candidates + gridCandidates(anchors)
        return anchors.withIndex().flatMap { (index, anchor) ->
            options.map { candidate -> Sticky(candidate, index, candidate.position - anchor) }
        }
            .filter { abs(it.offset) * zoom <= config.enterThresholdPx }
            .minWithOrNull(compareBy<Sticky>({ it.candidate.kind.ordinal }, { abs(it.offset) }, { it.candidate.sourceId?.value ?: "" }))
    }

    private fun gridCandidates(anchors: List<Double>): List<Candidate> {
        val size = config.gridSize ?: return emptyList()
        return anchors.map { Candidate((it / size).roundToLong() * size, SnapKind.Grid, null, 0.0, 0.0) }
    }

    private data class Candidate(
        val position: Double,
        val kind: SnapKind,
        val sourceId: NodeId?,
        val spanStart: Double,
        val spanEnd: Double,
    )

    private data class Sticky(val candidate: Candidate, val anchorIndex: Int, val offset: Double)
}
