package com.naury.kinetica.core.scene

import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Vec2

/**
 * 자동 배치([AutoLayout])와 크기 제약([LayoutConstraints])을 문서에 반영하는 순수 함수 모음.
 * [com.naury.kinetica.core.command.SceneReducer]가 명령마다 부르므로 저장된 문서는 항상 배치가 끝난 상태다.
 * 바인딩으로 숨김이 바뀌는 등 문서 밖 값이 배치에 영향을 줄 때는 그린 직전에 [apply]를 다시 부른다.
 */
public object Layouts {
    /**
     * 자동 배치 Frame의 자식 위치·늘이기와 내용 맞춤 크기를 계산하고, 크기를 [SceneNode.sizeLimits] 안으로 자른다.
     * 안쪽 Frame부터 계산하며, 바뀐 것이 없으면 같은 문서를 돌려준다.
     */
    @JvmOverloads
    public fun apply(document: SceneDocument, measurer: TextMeasurer? = null): SceneDocument {
        val limited = document.nodes.values.filter { it.sizeLimits != SizeLimits.None }
        val autoText = if (measurer ==
            null
        ) {
            emptyList()
        } else {
            document.nodes.values.filter { it.autoSize != TextAutoSize.Fixed }.map { it.id }
        }
        val anyLayout = document.nodes.values.any { (it.content as? NodeContent.Frame)?.layout != null }
        if (limited.isEmpty() && autoText.isEmpty() && !anyLayout) return document
        val nodes = LinkedHashMap(document.nodes)
        // 직접 크기를 바꾼 노드도 범위 안에 둔다. 배치 중 크기 변경은 resize가 자른다.
        limited.forEach { node -> node.content.sizeOrNull()?.let { resize(nodes, node.id, it) } }
        // 바깥 Frame이 안쪽 Frame을 늘이면 안쪽 배치를 다시 해야 한다. 깊이만큼 반복하면 충분하고, 보통 두 번 안에 멈춘다.
        repeat(MAX_PASSES) {
            // 늘이기·채움으로 너비가 바뀐 글자는 높이를 다시 재야 하므로 배치마다 먼저 잰다.
            var changed = measurer != null && autoText.fold(false) { any, id -> measureText(nodes, id, measurer) || any }
            SceneGraph(document.copy(nodes = nodes)).drawOrder.asReversed().forEach { id ->
                if (layoutFrame(nodes, id)) changed = true
            }
            if (!changed) return if (nodes == document.nodes) document else document.copy(nodes = nodes)
        }
        return document.copy(nodes = nodes)
    }

    /**
     * artboard 크기를 [width]×[height]로 바꾸고 최상위 노드를 각자의 [LayoutConstraints]대로 옮긴다.
     * 재생기가 화면 크기에 맞춰 장면을 펼칠 때 쓴다.
     *
     * @throws IllegalArgumentException 크기가 유한한 양수가 아닐 때
     */
    public fun resizeArtboard(document: SceneDocument, width: Double, height: Double): SceneDocument {
        require(width.isFinite() && height.isFinite() && width > 0.0 && height > 0.0) { "artboard 크기는 유한한 양수여야 합니다." }
        val old = Size(document.artboard.width, document.artboard.height)
        val resized = document.copy(artboard = document.artboard.copy(width = width, height = height))
        if (old == Size(width, height)) return resized
        val nodes = LinkedHashMap(resized.nodes)
        follow(nodes, resized.rootOrder, old, Size(width, height))
        return apply(resized.copy(nodes = nodes))
    }

    /**
     * 명령 전후를 비교해 크기가 바뀐 Frame(자동 배치가 아닌 것)·artboard의 자식을 제약대로 옮긴 뒤 자동 배치를 다시 한다.
     */
    internal fun settle(before: SceneDocument, after: SceneDocument, measurer: TextMeasurer? = null): SceneDocument {
        val nodes = LinkedHashMap(after.nodes)
        val oldArtboard = Size(before.artboard.width, before.artboard.height)
        val newArtboard = Size(after.artboard.width, after.artboard.height)
        if (oldArtboard != newArtboard) follow(nodes, after.rootOrder, oldArtboard, newArtboard)
        after.nodes.values.forEach { node ->
            val frame = node.content as? NodeContent.Frame ?: return@forEach
            val old = (before.nodes[node.id]?.content as? NodeContent.Frame)?.size ?: return@forEach
            // 인스턴스 자식은 컴포넌트 동기화가 원본 크기 기준으로 이미 맞췄다.
            if (frame.layout == null && node.instanceOf == null && old != frame.size) follow(nodes, frame.childIds, old, frame.size)
        }
        val constrained = if (nodes == after.nodes) after else after.copy(nodes = nodes)
        return apply(constrained, measurer)
    }

    /**
     * 자동 배치 Frame의 자식 [id]를 문서 좌표로 [documentDelta]만큼 끌었을 때 들어갈 자식 순서 자리(`ReorderNode`의 `toIndex`).
     * 끈 노드의 가운데를 형제들과 비교하며, 줄 바꿈 배치는 줄을 먼저 본다. 부모가 자동 배치 Frame이 아니면 `null`이다.
     */
    public fun dropIndex(graph: SceneGraph, id: NodeId, documentDelta: Vec2): Int? {
        val nodes = graph.document.nodes
        val parent = nodes[id]?.parentId?.let(nodes::get)?.content as? NodeContent.Frame
        val layout = parent?.layout
        val point = draggedCenter(graph, id, documentDelta)
        if (layout == null || point == null) return null
        val axis = Axis(layout.direction)
        val others = parent.childIds - id
        val firstAfter = others.indexOfFirst { other ->
            val rect = boundsInParent(nodes, other)?.takeIf { nodes[other]?.visible == true } ?: return@indexOfFirst false
            val pointCross = axis.cross(point.x, point.y)
            when {
                layout.wrap && axis.cross(rect.left, rect.top) > pointCross -> true
                layout.wrap && axis.cross(rect.right, rect.bottom) < pointCross -> false
                else -> axis.main(rect.center.x, rect.center.y) > axis.main(point.x, point.y)
            }
        }
        return if (firstAfter < 0) others.size else firstAfter
    }

    /** [id]를 [documentDelta]만큼 옮겼을 때 부모 좌표의 가운데. */
    private fun draggedCenter(graph: SceneGraph, id: NodeId, documentDelta: Vec2): Vec2? {
        val node = graph.document.nodes[id] ?: return null
        val moved = TransformOps.move(graph, listOf(id), documentDelta)[id] ?: return null
        val center = boundsInParent(graph.document.nodes, id)?.center ?: return null
        return Vec2(
            center.x + moved.translationX - node.transform.translationX,
            center.y + moved.translationY - node.transform.translationY,
        )
    }

    /** 부모 기준 축 정렬 범위. 크기가 없는 그룹은 자손 범위의 합이고, 자손이 없으면 `null`이다. */
    internal fun boundsInParent(nodes: Map<NodeId, SceneNode>, id: NodeId): Rect? {
        val node = nodes[id] ?: return null
        val local = node.localBounds ?: (node.content as? NodeContent.Container)?.childIds
            ?.mapNotNull { boundsInParent(nodes, it) }
            ?.reduceOrNull(Rect::union)
            ?: return null
        return node.transform.matrix().mapBounds(local)
    }

    private fun measureText(nodes: MutableMap<NodeId, SceneNode>, id: NodeId, measurer: TextMeasurer): Boolean {
        val text = nodes[id]?.content as? NodeContent.Text ?: return false
        val measured = when (text.autoSize) {
            TextAutoSize.Fixed -> return false
            TextAutoSize.Height -> measurer.measure(text, text.size.width).let { Size(text.size.width, it.height) }
            TextAutoSize.WidthAndHeight -> measurer.measure(text, null)
        }
        val valid = measured.width.isFinite() && measured.height.isFinite() && measured.width > 0.0 && measured.height > 0.0
        return valid && resize(nodes, id, measured)
    }

    private fun layoutFrame(nodes: MutableMap<NodeId, SceneNode>, id: NodeId): Boolean {
        val frame = nodes[id]?.content as? NodeContent.Frame ?: return false
        val layout = frame.layout ?: return false
        return FramePass(nodes, id, frame, layout).run()
    }

    /** Frame 하나의 자동 배치 한 번. 늘이기 → 내용 맞춤 크기 → 위치 순서로 계산한다. */
    private class FramePass(
        val nodes: MutableMap<NodeId, SceneNode>,
        val id: NodeId,
        val frame: NodeContent.Frame,
        val layout: AutoLayout,
    ) {
        private val axis = Axis(layout.direction)
        private val items = frame.childIds.filter { nodes[it]?.visible == true }
        private val padStart = axis.main(layout.padding.left, layout.padding.top)
        private val padEnd = axis.main(layout.padding.right, layout.padding.bottom)
        private val crossStart = axis.cross(layout.padding.left, layout.padding.top)
        private val crossEnd = axis.cross(layout.padding.right, layout.padding.bottom)
        private val hugMain = axis.main(layout.hugWidth, layout.hugHeight)
        private val hugCross = axis.cross(layout.hugWidth, layout.hugHeight)

        fun run(): Boolean {
            if (layout.wrap && !hugMain) return runWrapped()
            val innerCross = innerCross()
            val stretched = layout.crossAlign == CrossAlign.Stretch && innerCross > 0.0 && stretch(innerCross)
            val filled = !hugMain && fill()
            val bounds = items.mapNotNull { child -> boundsInParent(nodes, child)?.let { child to it } }
            val totalMain = bounds.sumOf { axis.main(it.second.width, it.second.height) } + layout.gap * (bounds.size - 1).coerceAtLeast(0)
            val frameMain = if (hugMain) padStart + totalMain + padEnd else axis.main(frame.size.width, frame.size.height)
            val resized = resizeFrame(frameMain, innerCross)
            // 크기 최소·최대로 잘렸을 수 있으므로 실제 크기 기준으로 놓는다.
            val actual = nodes.getValue(id).content.sizeOrNull() ?: frame.size
            val actualMain = axis.main(actual.width, actual.height)
            val actualCross = if (hugCross) axis.cross(actual.width, actual.height) - crossStart - crossEnd else innerCross
            val placed = place(bounds, actualMain - padStart - padEnd - totalMain, actualCross)
            return stretched || filled || resized || placed
        }

        /** 줄 바꿈 배치. 자식을 안쪽 길이에 맞춰 줄로 나누고, 줄마다 배치 방향 정렬을, 줄 안에서 교차 정렬을 한다. */
        private fun runWrapped(): Boolean {
            val innerMain = axis.main(frame.size.width, frame.size.height) - padStart - padEnd
            val bounds = items.mapNotNull { child -> boundsInParent(nodes, child)?.let { child to it } }
            val lines = breakLines(bounds, innerMain)
            val lineCross = lines.map { line -> line.maxOf { axis.cross(it.second.width, it.second.height) } }
            val contentCross = lineCross.sum() + layout.lineGap * (lines.size - 1).coerceAtLeast(0)
            val resized = resizeFrame(axis.main(frame.size.width, frame.size.height), contentCross)
            // 고정 크기 Frame이면 줄 묶음 전체도 교차 정렬을 따른다.
            val spare = if (hugCross) 0.0 else innerCross() - contentCross
            var offset = crossStart + when (layout.crossAlign) {
                CrossAlign.Start, CrossAlign.Stretch -> 0.0
                CrossAlign.Center -> spare / 2.0
                CrossAlign.End -> spare
            }
            return lines.indices.fold(resized) { changed, index ->
                val line = lines[index]
                val total = line.sumOf { axis.main(it.second.width, it.second.height) } + layout.gap * (line.size - 1)
                val placed = place(line, innerMain - total, lineCross[index], offset)
                offset += lineCross[index] + layout.lineGap
                placed || changed
            }
        }

        private fun breakLines(bounds: List<Pair<NodeId, Rect>>, innerMain: Double): List<List<Pair<NodeId, Rect>>> {
            val lines = mutableListOf<MutableList<Pair<NodeId, Rect>>>()
            var used = 0.0
            bounds.forEach { item ->
                val length = axis.main(item.second.width, item.second.height)
                val current = lines.lastOrNull()
                if (current == null || used + layout.gap + length > innerMain + EPSILON) {
                    lines += mutableListOf(item)
                    used = length
                } else {
                    current += item
                    used += layout.gap + length
                }
            }
            return lines
        }

        /** 채움 자식이 배치 방향의 남는 공간을 똑같이 나눈다. 내용에 맞추는 Frame에서는 남는 공간이 없어 쓰지 않는다. */
        private fun fill(): Boolean {
            val fillers = items.filter { child ->
                nodes.getValue(child).let {
                    it.layoutFill && it.isAxisAligned() && !it.measures(width = axis.horizontal) &&
                        it.content.sizeOrNull() != null
                }
            }
            if (fillers.isEmpty()) return false
            val fixed = (items - fillers.toSet()).mapNotNull { boundsInParent(nodes, it) }.sumOf { axis.main(it.width, it.height) }
            val placed = items.count { boundsInParent(nodes, it) != null }
            val inner = axis.main(frame.size.width, frame.size.height) - padStart - padEnd - layout.gap * (placed - 1).coerceAtLeast(0)
            val share = ((inner - fixed) / fillers.size).coerceAtLeast(MIN_LENGTH)
            return fillers.fold(false) { changed, child ->
                val size = nodes.getValue(child).content.sizeOrNull() ?: return@fold changed
                val next = if (axis.horizontal) Size(share, size.height) else Size(size.width, share)
                resize(nodes, child, next) || changed
            }
        }

        private fun innerCross(): Double {
            if (!hugCross) return axis.cross(frame.size.width, frame.size.height) - crossStart - crossEnd
            return items.mapNotNull { boundsInParent(nodes, it) }.maxOfOrNull { axis.cross(it.width, it.height) } ?: 0.0
        }

        private fun stretch(innerCross: Double): Boolean = items.fold(false) { changed, child ->
            val node = nodes.getValue(child)
            val size =
                node.content.sizeOrNull()?.takeIf { node.isAxisAligned() && !hugsCross(node) && !node.measures(width = !axis.horizontal) }
                    ?: return@fold changed
            val next = if (axis.horizontal) Size(size.width, innerCross) else Size(innerCross, size.height)
            resize(nodes, child, next) || changed
        }

        // 자식 Frame이 이 축을 내용에 맞추고 있으면 늘이지 않는다. 늘이면 두 규칙이 번갈아 크기를 바꾼다.
        private fun hugsCross(node: SceneNode): Boolean {
            val childLayout = (node.content as? NodeContent.Frame)?.layout ?: return false
            return if (axis.horizontal) childLayout.hugHeight else childLayout.hugWidth
        }

        private fun resizeFrame(frameMain: Double, innerCross: Double): Boolean {
            val frameCross = if (hugCross) crossStart + innerCross + crossEnd else axis.cross(frame.size.width, frame.size.height)
            val size = if (axis.horizontal) Size(frameMain, frameCross) else Size(frameCross, frameMain)
            return size.width > 0.0 && size.height > 0.0 && resize(nodes, id, size)
        }

        private fun place(bounds: List<Pair<NodeId, Rect>>, free: Double, innerCross: Double, crossBase: Double = crossStart): Boolean {
            val gap =
                layout.gap +
                    if (layout.mainAlign == MainAlign.SpaceBetween && bounds.size > 1) free.coerceAtLeast(0.0) / (bounds.size - 1) else 0.0
            var cursor = padStart + when (layout.mainAlign) {
                MainAlign.Start, MainAlign.SpaceBetween -> 0.0
                MainAlign.Center -> free / 2.0
                MainAlign.End -> free
            }
            return bounds.fold(false) { changed, (child, rect) ->
                val cross = axis.cross(rect.width, rect.height)
                val crossOffset = crossBase + when (layout.crossAlign) {
                    CrossAlign.Start, CrossAlign.Stretch -> 0.0
                    CrossAlign.Center -> (innerCross - cross) / 2.0
                    CrossAlign.End -> innerCross - cross
                }
                val targetX = if (axis.horizontal) cursor else crossOffset
                val targetY = if (axis.horizontal) crossOffset else cursor
                cursor += axis.main(rect.width, rect.height) + gap
                translate(nodes, child, targetX - rect.left, targetY - rect.top) || changed
            }
        }
    }

    /** 부모 크기가 [old]에서 [new]로 바뀔 때 [childIds]를 각자의 제약대로 옮기고 늘인다. */
    internal fun follow(nodes: MutableMap<NodeId, SceneNode>, childIds: List<NodeId>, old: Size, new: Size) {
        childIds.forEach { child ->
            val node = nodes[child] ?: return@forEach
            val rect = boundsInParent(nodes, child) ?: return@forEach
            val (x, width) = pinned(node.constraints.horizontal, rect.left, rect.width, old.width, new.width)
            val (y, height) = pinned(node.constraints.vertical, rect.top, rect.height, old.height, new.height)
            val resizable = node.content.sizeOrNull() != null && node.isAxisAligned()
            if (resizable && width > 0.0 && height > 0.0) resize(nodes, child, Size(width, height))
            translate(nodes, child, x - rect.left, y - rect.top)
        }
    }

    /** 한 축의 새 시작 위치와 크기. 크기는 [Pin.Both]·[Pin.Scale]일 때만 바뀐다. */
    private fun pinned(pin: Pin, start: Double, length: Double, old: Double, new: Double): Pair<Double, Double> = when (pin) {
        Pin.Start -> start to length
        Pin.End -> start + (new - old) to length
        Pin.Center -> start + (new - old) / 2.0 to length
        Pin.Both -> start to (length + new - old).coerceAtLeast(MIN_LENGTH)
        Pin.Scale -> if (old > 0.0) start * new / old to length * new / old else start to length
    }

    private fun translate(nodes: MutableMap<NodeId, SceneNode>, id: NodeId, dx: Double, dy: Double): Boolean {
        if (kotlin.math.abs(dx) < EPSILON && kotlin.math.abs(dy) < EPSILON) return false
        val node = nodes.getValue(id)
        nodes[id] = node.copy(
            transform = node.transform.copy(
                translationX = node.transform.translationX + dx,
                translationY = node.transform.translationY + dy,
            ),
        )
        return true
    }

    /**
     * 크기가 있는 노드의 크기를 바꾼다. pivot은 같은 비율 자리에 두어 회전 없는 노드의 왼쪽 위가 그대로다.
     * 자동 배치가 아닌 Frame이면 자식도 제약대로 따라간다.
     */
    private fun resize(nodes: MutableMap<NodeId, SceneNode>, id: NodeId, wanted: Size): Boolean {
        val node = nodes.getValue(id)
        val old = node.content.sizeOrNull() ?: return false
        val size = node.sizeLimits.clamp(wanted)
        if (kotlin.math.abs(old.width - size.width) < EPSILON && kotlin.math.abs(old.height - size.height) < EPSILON) return false
        val fx = if (old.width > 0.0) size.width / old.width else 1.0
        val fy = if (old.height > 0.0) size.height / old.height else 1.0
        nodes[id] = node.copy(
            content = node.content.withSize(size),
            transform = node.transform.copy(pivotX = node.transform.pivotX * fx, pivotY = node.transform.pivotY * fy),
        )
        val frame = node.content as? NodeContent.Frame
        if (frame != null && frame.layout == null) follow(nodes, frame.childIds, old, size)
        return true
    }

    /** 글자 측정이 정하는 축이면 `true`. 늘이기·채움이 그 축을 바꾸면 측정과 번갈아 바뀌므로 건드리지 않는다. */
    private fun SceneNode.measures(width: Boolean): Boolean = when (autoSize) {
        TextAutoSize.Fixed -> false
        TextAutoSize.Height -> !width
        TextAutoSize.WidthAndHeight -> true
    }

    private val SceneNode.autoSize: TextAutoSize get() = (content as? NodeContent.Text)?.autoSize ?: TextAutoSize.Fixed

    private fun SceneNode.isAxisAligned(): Boolean =
        transform.rotationDegrees % FULL_TURN == 0.0 && transform.scaleX == 1.0 && transform.scaleY == 1.0

    private class Axis(direction: LayoutDirection) {
        val horizontal = direction == LayoutDirection.Horizontal

        fun <T> main(x: T, y: T): T = if (horizontal) x else y

        fun <T> cross(x: T, y: T): T = if (horizontal) y else x
    }

    private const val MAX_PASSES = 4
    private const val EPSILON = 1e-9
    private const val MIN_LENGTH = 1.0
    private const val FULL_TURN = 360.0
}

/** 크기가 있는 내용의 크기. 경로·그룹은 `null`이다. */
public fun NodeContent.sizeOrNull(): Size? = when (this) {
    is NodeContent.Rectangle -> size
    is NodeContent.Ellipse -> size
    is NodeContent.Polygon -> size
    is NodeContent.Text -> size
    is NodeContent.Image -> size
    is NodeContent.Frame -> size
    is NodeContent.HostSlot -> size
    is NodeContent.Group, is NodeContent.Path -> null
}

/** 크기가 있는 내용은 [size]로 바꾸고, 경로·그룹은 그대로 돌려준다. */
public fun NodeContent.withSize(size: Size): NodeContent = when (this) {
    is NodeContent.Rectangle -> copy(size = size)
    is NodeContent.Ellipse -> copy(size = size)
    is NodeContent.Polygon -> copy(size = size)
    is NodeContent.Text -> copy(size = size)
    is NodeContent.Image -> copy(size = size)
    is NodeContent.Frame -> copy(size = size)
    is NodeContent.HostSlot -> copy(size = size)
    is NodeContent.Group, is NodeContent.Path -> this
}
