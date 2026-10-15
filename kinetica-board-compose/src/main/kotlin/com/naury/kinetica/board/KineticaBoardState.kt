package com.naury.kinetica.board

import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.LayoutConstraints
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.Pin
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.history.HistoryController
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import com.naury.kinetica.render.HOST_VIEW_UNITS_PER_DP
import com.naury.kinetica.serialization.DecodeResult
import com.naury.kinetica.serialization.SceneCodec
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 보드 하나의 배치 상태. 위젯을 추가, 삭제, 이동하거나 크기를 바꿀 때마다 실행 취소 한 단계가 쌓인다. [toJson]으로 저장하고 [create]로 복원한다.
 *
 * 배치는 기준 폭([REFERENCE_WIDTH] 문서 단위, 약 393dp)으로 저장한다. 화면 폭이 다르면 가로 위치와 너비를 비율대로 늘려 보여 주므로
 * 폴더블을 접고 펴도 배치가 유지된다. 격자 배치는 칸 위치로 저장한다. 화면을 회전해도 실행 취소 기록을 유지하려면 상태를
 * ViewModel처럼 화면보다 오래 사는 곳에 둔다. [rememberKineticaBoardState]가 이 일과 저장을 함께 맡는다.
 *
 * 메인 스레드에서만 사용한다. 다 쓰면 [close]를 호출한다.
 */
public class KineticaBoardState private constructor(
    public val catalog: List<BoardWidget>,
    public val arrangement: BoardArrangement,
    private val session: KineticaSession,
) {
    private val widgets = catalog.associateBy { it.key }

    // 위젯 키마다 지금까지 발급한 가장 큰 슬롯 번호. 삭제한 슬롯 id를 재사용하지 않아야 호스트가 슬롯 id로 저장한 데이터가 새 슬롯에 섞이지 않는다.
    private val issued = ItemNumbers()

    /** 기준 폭으로 저장된 배치 문서. 놓인 슬롯마다 호스트 뷰 노드가 하나 있고, 슬롯 id를 노드 id와 호스트 뷰 키로 쓴다. */
    public val document: StateFlow<SceneDocument> get() = session.document

    public val history: HistoryController get() = session.history

    /**
     * [create]에 넘긴 저장본을 읽지 못해 기본 배치로 시작했으면 `true`. 이때는 저장본을 바로 덮어쓰지 말고 따로 보관하거나 사용자에게 알린다.
     * 저장본이 없었으면 `false`다.
     */
    public var restoreFailed: Boolean = false
        private set

    internal val commands get() = session.commands

    /** 놓인 위젯 키. 스택 안에 있는 위젯도 포함한다. */
    private val placedWidgetKeys: Set<String>
        get() = session.document.value.let { document ->
            document.rootOrder.flatMap { id ->
                val node = document.node(id) ?: return@flatMap emptyList()
                node.stackMembers().map { it.key }.ifEmpty { listOf(widgetKeyOf(node)) }
            }.toSet()
        }

    /**
     * 보드에 놓인 슬롯 id 목록이며 그리기 순서를 따른다. 한 번만 놓는 위젯은 위젯 키와 같고, [BoardWidget.multiple] 위젯은 `memo`, `memo.2`처럼
     * 슬롯마다 다르다. 슬롯 id는 이동하거나 저장해도 바뀌지 않으므로 호스트가 슬롯별 데이터를 저장할 때 키로 쓴다. 위젯 키는 [widgetKeyOf]로 얻는다.
     * 스택 안의 슬롯은 [itemIds]에 있다.
     */
    public val placedKeys: List<String> get() = session.document.value.rootOrder.map { it.value }

    /**
     * 지금 보드에 있는 모든 슬롯 id. 스택 안의 슬롯도 포함한다. 삭제한 슬롯 id는 재사용하지 않으므로, 호스트가 슬롯별로 저장한 데이터는
     * 편집을 마칠 때 여기에 없는 것만 지우면 된다. 삭제하자마자 지우면 실행 취소로 복원한 슬롯의 데이터가 사라진다.
     */
    public val itemIds: Set<String>
        get() = session.document.value.let { document ->
            document.rootOrder.flatMap { id ->
                val node = document.node(id) ?: return@flatMap emptyList()
                node.stackMembers().map { it.id }.ifEmpty { listOf(id.value) }
            }.toSet()
        }

    /** 아직 놓이지 않았거나 여러 개 놓을 수 있어서 더 추가할 수 있는 위젯. 위젯 추가 메뉴에 쓴다. */
    public val availableWidgets: List<BoardWidget>
        get() = placedWidgetKeys.let { placed -> catalog.filter { it.multiple || it.key !in placed } }

    /** 슬롯 [id]에 놓인 위젯의 키. 스택 안의 슬롯도 찾는다. 놓여 있지 않거나 스택 자체의 id면 `null`. */
    public fun widgetKeyOf(id: String): String? {
        val document = session.document.value
        val node = NodeId.parseOrNull(id)?.let(document::node)
        if (node != null) return widgetKeyOf(node).takeIf { node.stackMembers().isEmpty() }
        return document.rootOrder.firstNotNullOfOrNull { stackId ->
            document.node(stackId)?.stackMembers()?.firstOrNull { it.id == id }?.key
        }
    }

    /** [key] 위젯을 위에서부터, 같은 줄이면 왼쪽부터 찾은 첫 빈 위치에 놓는다. 목록에 없거나 이미 놓인 한 개짜리 위젯이면 `false`. */
    public fun add(key: String): Boolean = addItem(key) != null

    /** [add]와 같지만 놓은 슬롯 id를 돌려준다. 놓지 못하면 `null`. */
    public fun addItem(key: String): String? {
        val widget = widgets[key] ?: return null
        if (widget !in availableWidgets) return null
        val document = session.document.value
        val taken = document.nodes.values.flatMap { node -> node.stackMembers().map { it.id } }.toSet()
        // 한 번만 놓는 위젯은 슬롯 id가 늘 위젯 키다. 여러 개 놓는 위젯만 번호를 이어서 붙인다.
        val id = newItemId(key, document, widgets.keys, taken, if (widget.multiple) issued[key] else 0)
        issued.record(key, id)
        val node = placementFor(widget, document, id)
        return id.takeIf { session.commands.execute(SceneCommand.AddNode(node)) is CommandResult.Applied }
    }

    /** 슬롯 [id]를 보드에서 삭제한다. 놓여 있지 않거나 [BoardWidget.removable]이 `false`면 `false`. */
    public fun remove(id: String): Boolean {
        val node = NodeId.parseOrNull(id)?.let(session.document.value::node) ?: return false
        val keys = node.stackMembers().map { it.key }.ifEmpty { listOf(widgetKeyOf(node)) }
        if (keys.any { widgets[it]?.removable == false }) return false
        return session.commands.execute(SceneCommand.RemoveNodes(setOf(NodeId(id)))) is CommandResult.Applied
    }

    /**
     * 슬롯 [id]의 위젯을 기준 폭 좌표 [rect]로 옮기거나 크기를 바꾼다. 분할 배치 뒤에 가려지는 위젯이 없도록 겹치는 다른 위젯은 아래로 밀린다.
     * 격자 배치면 가장 가까운 칸에 맞춘다. 변경 전체가 실행 취소 한 단계다.
     */
    public fun place(id: String, rect: Rect): Boolean {
        val document = session.document.value
        if (NodeId.parseOrNull(id)?.let(document::node) == null || rect.width <= 0.0 || rect.height <= 0.0) return false
        val grid = arrangement as? BoardArrangement.Grid
        val updates: Map<String, Rect> = if (grid == null) {
            val rects =
                document.rootOrder.mapNotNull { other -> rectOf(document.node(other))?.let { other.value to it } }.toMap() + (id to rect)
            BoardGeometry.pushOverlaps(rects, id, arrangement.gapUnits()).filter { (other, target) ->
                other == id ||
                    rectOf(document.node(NodeId(other))) != target
            }
        } else {
            val cells = gridCells(document) + (id to BoardGeometry.nearestCell(rect, REFERENCE_WIDTH, grid, arrangement.gapUnits()))
            BoardGeometry.resolveOverlaps(cells, id)
                .mapValues { (_, cell) -> BoardGeometry.cellRect(cell, REFERENCE_WIDTH, grid, arrangement.gapUnits()) }
                .filter { (other, target) -> rectOf(document.node(NodeId(other))) != target }
        }
        if (!session.commands.beginTransaction("place")) return false
        val ok = updates.all { (other, target) ->
            val node = document.node(NodeId(other)) ?: return@all true
            session.commands.execute(SceneCommand.UpdateProperties(withRect(node, target))) is CommandResult.Applied
        }
        if (ok) session.commands.commitTransaction() else session.commands.rollbackTransaction()
        return ok
    }

    /**
     * 슬롯 [id]의 위젯을 [dxDp], [dyDp]만큼 옮긴다. 접근성 동작과 키보드 편집에 쓴다. 보드 밖으로는 나가지 않고, 격자 배치면 가장 가까운 칸에 맞춘다.
     */
    public fun moveBy(id: String, dxDp: Float, dyDp: Float): Boolean {
        val rect = rectOf(id) ?: return false
        val (stepX, stepY) = step(dxDp, dyDp)
        val dx = stepX.coerceIn(-rect.left, REFERENCE_WIDTH - rect.right)
        val dy = stepY.coerceAtLeast(-rect.top)
        return place(id, Rect(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy))
    }

    /** 슬롯 [id]의 위젯 크기를 [dwDp], [dhDp]만큼 바꾼다. 왼쪽 위 모서리는 그대로 두고, 48dp보다 작아지지 않는다. */
    public fun resizeBy(id: String, dwDp: Float, dhDp: Float): Boolean {
        val rect = rectOf(id) ?: return false
        val min = MIN_SIZE_DP * UNITS
        val (stepW, stepH) = step(dwDp, dhDp)
        val right = (rect.right + stepW).coerceIn(rect.left + min, REFERENCE_WIDTH)
        val bottom = (rect.bottom + stepH).coerceAtLeast(rect.top + min)
        return place(id, Rect(rect.left, rect.top, right, bottom))
    }

    // 격자 배치는 이동량의 크기는 무시하고 방향만 보고 한 칸씩 움직인다.
    private fun step(dxDp: Float, dyDp: Float): Pair<Double, Double> {
        val grid = arrangement as? BoardArrangement.Grid ?: return dxDp * UNITS to dyDp * UNITS
        val width = BoardGeometry.cellWidth(REFERENCE_WIDTH, grid.columns, arrangement.gapUnits())
        return Math.signum(dxDp) * (width + arrangement.gapUnits()) to
            Math.signum(dyDp) * (width * grid.rowHeightRatio + arrangement.gapUnits())
    }

    /** 슬롯 [id]의 위젯을 맨 앞으로 가져온다. 다른 위젯과 겹치면 위에 그려진다. */
    public fun bringToFront(id: String): Boolean {
        val last = placedKeys.lastIndex
        if (id !in placedKeys || placedKeys.last() == id) return false
        return session.commands.execute(SceneCommand.ReorderNode(NodeId(id), last)) is CommandResult.Applied
    }

    /** 슬롯 [id]의 기준 폭 좌표 사각형. 없으면 `null`. */
    public fun rectOf(id: String): Rect? = NodeId.parseOrNull(id)?.let { rectOf(session.document.value.node(it)) }

    /** 저장용 JSON. 지금 목록의 키를 함께 남겨 두어, 다음에 열 때 새로 생긴 위젯과 사용자가 삭제한 위젯을 구분한다. */
    public fun toJson(): String {
        val known = JsonArray(catalog.map { JsonPrimitive(it.key) })
        val document = session.document.value
        val numbers = JsonObject(issued.all.mapValues { JsonPrimitive(it.value) })
        return codec.encode(
            document.copy(
                extensions =
                    document.extensions + (EXTENSION_KEY to JsonObject(mapOf(KNOWN to known, ISSUED to numbers)).toString()),
            ),
        )
    }

    public fun close(): Unit = session.close()

    private fun gridCells(document: SceneDocument): Map<String, GridCell> {
        val grid = arrangement as? BoardArrangement.Grid ?: return emptyMap()
        return document.rootOrder.mapNotNull(document::node).associate { node ->
            node.id.value to
                BoardGeometry.nearestCell(rectOf(node) ?: Rect(0.0, 0.0, 1.0, 1.0), REFERENCE_WIDTH, grid, arrangement.gapUnits())
        }
    }

    internal fun placementFor(widget: BoardWidget, document: SceneDocument, id: String): SceneNode {
        val gap = arrangement.gapUnits()
        val placed = document.rootOrder.mapNotNull { rectOf(document.node(it)) }
        val rect = when (val arrangement = arrangement) {
            is BoardArrangement.Grid -> {
                val occupied = gridCells(document).values
                val cell = BoardGeometry.firstFreeCell(occupied, widget.columnSpan, widget.rowSpan, arrangement.columns)
                BoardGeometry.cellRect(cell, REFERENCE_WIDTH, arrangement, gap)
            }

            is BoardArrangement.Free -> {
                val width = (widget.widthDp * UNITS).coerceAtMost(REFERENCE_WIDTH - 2 * gap)
                BoardGeometry.firstFreeSpot(width, widget.heightDp * UNITS, placed, REFERENCE_WIDTH, gap)
            }
        }
        return withRect(
            SceneNode(
                id = NodeId(id),
                content = NodeContent.HostSlot(Size(rect.width, rect.height), id),
                name = widget.label,
                constraints = LayoutConstraints(horizontal = Pin.Scale),
                metadata = widgetMetadata(id, widget.key),
            ),
            rect,
        )
    }

    public companion object {
        /** 저장 기준 폭(문서 단위). [HOST_VIEW_UNITS_PER_DP]와 함께 약 393dp 화면에 해당한다. */
        public const val REFERENCE_WIDTH: Double = 1080.0

        internal const val UNITS: Double = HOST_VIEW_UNITS_PER_DP.toDouble()
        private const val MIN_SIZE_DP = 48.0
        private const val EXTENSION_KEY = "kinetica.board"
        private const val WIDGET_KEY = "kinetica.widget"
        private const val KNOWN = "known"
        private const val ISSUED = "issued"
        private val codec = SceneCodec()

        /**
         * 보드 상태를 만든다. [saved]가 있으면 그 배치를 열고 목록과 맞춘다. 목록에 없는 위젯은 지우고, 지난번 저장 뒤 목록에 새로 생긴
         * [BoardWidget.placedByDefault] 위젯은 빈 위치에 놓는다. 사용자가 삭제한 위젯은 다시 넣지 않는다.
         * 저장본을 읽을 수 없으면 기본 배치로 시작한다.
         *
         * @throws IllegalArgumentException 목록에 같은 키가 두 번 있을 때
         */
        @JvmStatic
        @JvmOverloads
        public fun create(
            catalog: List<BoardWidget>,
            arrangement: BoardArrangement = BoardArrangement.Free(),
            saved: String? = null,
        ): KineticaBoardState {
            require(catalog.map { it.key }.toSet().size == catalog.size) { "위젯 키가 겹칩니다." }
            val restored = saved?.let { (codec.decode(it) as? DecodeResult.Success)?.document }
            val keys = catalog.map { it.key }.toSet()
            val known = restored?.let(::knownKeys)
            val base = restored?.let { document ->
                // 스택에는 목록에 남은 위젯만 두고, 하나만 남으면 일반 위젯으로 되돌린다.
                val kept = document.rootOrder.mapNotNull { id ->
                    val node = document.node(id)?.takeIf { it.content is NodeContent.HostSlot } ?: return@mapNotNull null
                    if (node.stackMembers().isEmpty()) node.takeIf { widgetKeyOf(it) in keys } else reconcileStack(node, keys)
                }
                document.copy(
                    nodes = kept.associate { it.id to it.copy(parentId = null) },
                    rootOrder = kept.map { it.id },
                    timelines = emptyList(),
                )
            } ?: emptyBoard()
            val opened = KineticaSession.create(base) as? SessionOpenResult.Opened
                ?: (KineticaSession.create(emptyBoard()) as SessionOpenResult.Opened)
            // 기본 배치와 목록 맞춤은 사용자가 실행 취소할 대상이 아니다. 임시 세션에서 배치한 결과로 기록이 빈 세션을 연다.
            val draft = KineticaBoardState(catalog, arrangement, opened.session)
            val placed = base.rootOrder.flatMap { id ->
                val node = base.nodes.getValue(id)
                node.stackMembers().map { it.key }.ifEmpty { listOf(widgetKeyOf(node)) }
            }.toSet()
            catalog.filter { widget -> widget.placedByDefault && widget.key !in placed && (known == null || widget.key !in known) }
                .forEach { draft.add(it.key) }
            val initial = draft.document.value
            draft.close()
            val state = KineticaBoardState(catalog, arrangement, (KineticaSession.create(initial) as SessionOpenResult.Opened).session)
            state.issued.restore(restored?.let(::issuedNumbers).orEmpty(), draft.issued.all, state.document.value)
            state.restoreFailed = saved != null && restored == null
            return state
        }

        private fun emptyBoard() = SceneDocument(
            documentId = "board",
            artboard = Artboard(REFERENCE_WIDTH, REFERENCE_WIDTH),
            nodes = emptyMap(),
            rootOrder = emptyList(),
        )

        private fun knownKeys(document: SceneDocument): Set<String>? = runCatching {
            val raw = document.extensions[EXTENSION_KEY] ?: return null
            Json.parseToJsonElement(raw).jsonObject[KNOWN]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet()
        }.getOrNull()

        private fun issuedNumbers(document: SceneDocument): Map<String, Int> = runCatching {
            val raw = document.extensions[EXTENSION_KEY] ?: return emptyMap()
            Json.parseToJsonElement(raw).jsonObject[ISSUED]?.jsonObject?.mapNotNull { (key, value) ->
                value.jsonPrimitive.contentOrNull?.toIntOrNull()?.let { key to it }
            }?.toMap().orEmpty()
        }.getOrDefault(emptyMap())

        internal fun widgetKeyOf(node: SceneNode): String = node.metadata[WIDGET_KEY] ?: node.id.value

        /** 슬롯 id와 위젯 키로 만든 일반 위젯 노드의 메타데이터. */
        internal fun widgetMetadata(id: String, key: String): Map<String, String> = if (id == key) emptyMap() else mapOf(WIDGET_KEY to key)

        internal fun rectOf(node: SceneNode?): Rect? {
            val size = (node?.content as? NodeContent.HostSlot)?.size ?: return null
            return Rect(
                node.transform.translationX,
                node.transform.translationY,
                node.transform.translationX + size.width,
                node.transform.translationY + size.height,
            )
        }

        internal fun withRect(node: SceneNode, rect: Rect): SceneNode {
            val slot = node.content as? NodeContent.HostSlot ?: return node
            return node.copy(
                content = slot.copy(size = Size(rect.width, rect.height)),
                transform = Transform2D(translationX = rect.left, translationY = rect.top),
            )
        }
    }
}

// 첫 슬롯은 예전 저장본과 id가 같도록 위젯 키를 그대로 쓰고, 그다음부터는 `.2`, `.3`처럼 다른 위젯 키와 겹치지 않는 첫 번호를 붙인다.
private fun newItemId(key: String, document: SceneDocument, widgetKeys: Set<String>, taken: Set<String>, issued: Int): String =
    generateSequence(issued + 1) { it + 1 }
        .map { if (it == 1) key else "$key.$it" }
        .first { id -> document.node(NodeId(id)) == null && id !in taken && (id == key || id !in widgetKeys) }

/** 위젯 키마다 지금까지 발급한 가장 큰 슬롯 번호. */
internal class ItemNumbers {
    private val numbers = HashMap<String, Int>()

    val all: Map<String, Int> get() = numbers

    operator fun get(key: String): Int = numbers[key] ?: 0

    fun record(key: String, id: String) {
        numbers[key] = maxOf(this[key], itemNumber(key, id))
    }

    /** 저장된 번호와 [document]에 놓인 슬롯을 함께 보고 맞춘다. 번호를 저장하지 않던 이전 형식 저장본도 처리한다. */
    fun restore(saved: Map<String, Int>, more: Map<String, Int>, document: SceneDocument) {
        (saved.entries + more.entries).forEach { (key, number) -> numbers[key] = maxOf(this[key], number) }
        document.rootOrder.mapNotNull(document::node).flatMap { node ->
            node.stackMembers().map { it.key to it.id }.ifEmpty { listOf(KineticaBoardState.widgetKeyOf(node) to node.id.value) }
        }.forEach { (key, id) -> record(key, id) }
    }
}

/** 슬롯 id의 번호. 위젯 키와 같으면 1, `memo.3`이면 3, 알 수 없으면 0. */
private fun itemNumber(key: String, id: String): Int = when {
    id == key -> 1
    id.startsWith("$key.") -> id.removePrefix("$key.").toIntOrNull() ?: 0
    else -> 0
}

internal fun BoardArrangement.gapUnits(): Double = when (this) {
    is BoardArrangement.Free -> gapDp * KineticaBoardState.UNITS
    is BoardArrangement.Grid -> gapDp * KineticaBoardState.UNITS
}

/** 스택 안의 위젯 하나. [id]는 슬롯 id, [key]는 위젯 키다. */
internal data class StackMember(val id: String, val key: String)
