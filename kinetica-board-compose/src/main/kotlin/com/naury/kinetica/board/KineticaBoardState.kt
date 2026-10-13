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
 * 보드 하나의 배치 상태. 위젯 추가·빼기·옮기기·크기 바꾸기는 실행 취소 한 단계씩이며, [toJson]으로 저장해 [create]로 되살린다.
 *
 * 배치는 기준 폭([REFERENCE_WIDTH] 문서 단위, 약 393dp)으로 저장하고, 화면 폭이 다르면 가로 위치·너비를 비율대로 펼쳐 보여 준다.
 * 그래서 폴더블을 접고 펴거나 화면을 돌려도 저장된 배치와 실행 취소 기록이 그대로다. 격자 배치는 칸 위치로 저장한다.
 *
 * 스레드: 메인 스레드에서 쓴다. 다 쓰면 [close]한다.
 */
public class KineticaBoardState private constructor(
    public val catalog: List<BoardWidget>,
    public val arrangement: BoardArrangement,
    private val session: KineticaSession,
) {
    private val widgets = catalog.associateBy { it.key }

    /** 기준 폭 기준 배치 문서. 위젯마다 키를 id로 쓰는 호스트 뷰 노드 하나다. */
    public val document: StateFlow<SceneDocument> get() = session.document

    public val history: HistoryController get() = session.history

    /** 보드에 놓인 위젯 키(그리기 순서). */
    public val placedKeys: List<String> get() = session.document.value.rootOrder.map { it.value }

    /** 아직 놓이지 않은 위젯. 위젯 추가 목록에 쓴다. */
    public val availableWidgets: List<BoardWidget> get() = placedKeys.toSet().let { placed -> catalog.filter { it.key !in placed } }

    /** [key] 위젯을 비어 있는 첫 자리(위에서부터, 같은 줄이면 왼쪽부터)에 놓는다. 목록에 없거나 이미 놓였으면 `false`. */
    public fun add(key: String): Boolean {
        val widget = widgets[key] ?: return false
        if (key in placedKeys) return false
        val node = placementFor(widget, session.document.value)
        return session.commands.execute(SceneCommand.AddNode(node)) is CommandResult.Applied
    }

    /** [key] 위젯을 보드에서 뺀다. 놓여 있지 않거나 [BoardWidget.removable]이 `false`면 `false`. */
    public fun remove(key: String): Boolean {
        if (widgets[key]?.removable == false || key !in placedKeys) return false
        return session.commands.execute(SceneCommand.RemoveNodes(setOf(NodeId(key)))) is CommandResult.Applied
    }

    /**
     * [key] 위젯을 기준 폭 좌표 [rect]로 옮기거나 크기를 바꾼다. 겹치는 다른 위젯은 아래로 밀린다(분할 배치 뒤 가려지지 않게).
     * 격자 배치면 가장 가까운 칸으로 맞춘다.
     * 모든 변경이 실행 취소 한 단계다.
     */
    public fun place(key: String, rect: Rect): Boolean {
        val document = session.document.value
        if (document.node(NodeId(key)) == null || rect.width <= 0.0 || rect.height <= 0.0) return false
        val grid = arrangement as? BoardArrangement.Grid
        val updates: Map<String, Rect> = if (grid == null) {
            val rects = document.rootOrder.mapNotNull { id -> rectOf(document.node(id))?.let { id.value to it } }.toMap() + (key to rect)
            BoardGeometry.pushOverlaps(rects, key, gapUnits()).filter { (id, target) ->
                id == key ||
                    rectOf(document.node(NodeId(id))) != target
            }
        } else {
            val cells = gridCells(document) + (key to BoardGeometry.nearestCell(rect, REFERENCE_WIDTH, grid, gapUnits()))
            BoardGeometry.resolveOverlaps(cells, key)
                .mapValues { (_, cell) -> BoardGeometry.cellRect(cell, REFERENCE_WIDTH, grid, gapUnits()) }
                .filter { (id, target) -> rectOf(document.node(NodeId(id))) != target }
        }
        if (!session.commands.beginTransaction("place")) return false
        val ok = updates.all { (id, target) ->
            val node = document.node(NodeId(id)) ?: return@all true
            session.commands.execute(SceneCommand.UpdateProperties(withRect(node, target))) is CommandResult.Applied
        }
        if (ok) session.commands.commitTransaction() else session.commands.rollbackTransaction()
        return ok
    }

    /** 위젯 하나를 맨 위로 올린다(겹칠 때 위에 그려진다). */
    public fun bringToFront(key: String): Boolean {
        val last = placedKeys.lastIndex
        if (key !in placedKeys || placedKeys.last() == key) return false
        return session.commands.execute(SceneCommand.ReorderNode(NodeId(key), last)) is CommandResult.Applied
    }

    /** 기준 폭 좌표의 위젯 사각형. 없으면 `null`. */
    public fun rectOf(key: String): Rect? = rectOf(session.document.value.node(NodeId(key)))

    /** 저장용 JSON. 지금 목록의 키를 함께 남겨, 다음에 열 때 새로 생긴 위젯과 사용자가 뺀 위젯을 구분한다. */
    public fun toJson(): String {
        val known = JsonArray(catalog.map { JsonPrimitive(it.key) })
        val document = session.document.value
        return codec.encode(
            document.copy(extensions = document.extensions + (EXTENSION_KEY to JsonObject(mapOf(KNOWN to known)).toString())),
        )
    }

    public fun close(): Unit = session.close()

    private fun gapUnits(): Double = when (val arrangement = arrangement) {
        is BoardArrangement.Free -> arrangement.gapDp * UNITS
        is BoardArrangement.Grid -> arrangement.gapDp * UNITS
    }

    private fun gridCells(document: SceneDocument): Map<String, GridCell> {
        val grid = arrangement as? BoardArrangement.Grid ?: return emptyMap()
        return document.rootOrder.mapNotNull(document::node).associate { node ->
            node.id.value to BoardGeometry.nearestCell(rectOf(node) ?: Rect(0.0, 0.0, 1.0, 1.0), REFERENCE_WIDTH, grid, gapUnits())
        }
    }

    private fun placementFor(widget: BoardWidget, document: SceneDocument): SceneNode {
        val gap = gapUnits()
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
                id = NodeId(widget.key),
                content = NodeContent.HostSlot(Size(rect.width, rect.height), widget.key),
                name = widget.label,
                constraints = LayoutConstraints(horizontal = Pin.Scale),
            ),
            rect,
        )
    }

    public companion object {
        /** 저장 기준 폭(문서 단위). [HOST_VIEW_UNITS_PER_DP]와 함께 약 393dp 화면에 해당한다. */
        public const val REFERENCE_WIDTH: Double = 1080.0

        internal const val UNITS: Double = HOST_VIEW_UNITS_PER_DP.toDouble()
        private const val EXTENSION_KEY = "kinetica.board"
        private const val KNOWN = "known"
        private val codec = SceneCodec()

        /**
         * 보드 상태를 만든다. [saved]가 있으면 그 배치를 열고 목록과 맞춘다: 목록에 없는 위젯은 빼고, 지난번 저장 뒤 목록에 새로 생긴
         * [BoardWidget.placedByDefault] 위젯은 빈자리에 놓는다. 사용자가 뺀 위젯은 다시 넣지 않는다.
         * 저장본을 읽을 수 없으면 기본 배치로 시작한다.
         *
         * @throws IllegalArgumentException 목록에 같은 키가 두 번 있을 때
         */
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
                val kept = document.rootOrder.filter { it.value in keys && document.node(it)?.content is NodeContent.HostSlot }
                document.copy(
                    nodes = kept.associateWith {
                        document.nodes.getValue(it).copy(parentId = null)
                    },
                    rootOrder = kept,
                    timelines = emptyList(),
                )
            } ?: emptyBoard()
            val opened = KineticaSession.create(base) as? SessionOpenResult.Opened
                ?: (KineticaSession.create(emptyBoard()) as SessionOpenResult.Opened)
            // 처음 배치·목록 맞춤은 사용자가 되돌릴 일이 아니므로, 임시 세션에서 놓은 결과로 기록 없는 세션을 연다.
            val draft = KineticaBoardState(catalog, arrangement, opened.session)
            catalog.filter { widget -> widget.placedByDefault && (known == null || widget.key !in known) }.forEach { draft.add(it.key) }
            val initial = draft.document.value
            draft.close()
            val state = KineticaBoardState(catalog, arrangement, (KineticaSession.create(initial) as SessionOpenResult.Opened).session)
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
