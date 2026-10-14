package com.naury.kinetica.history

import com.naury.kinetica.core.command.CommandError
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.command.SceneReducer
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneValidator
import com.naury.kinetica.core.scene.SceneViolation
import com.naury.kinetica.core.scene.TextMeasurer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 편집 중인 문서 하나의 상태. 문서·선택·실행 취소 상태를 [StateFlow]로 내보내고, 모든 쓰기를 [commands]로 받는다.
 *
 * 쓰기는 내부 lock으로 직렬화되므로 여러 스레드에서 불러도 된다. 무거운 작업을 하지 않으므로 main 스레드에서 불러도 된다.
 * 저장소에 접근하지 않는다. 저장 시점과 위치는 호스트가 정한다.
 */
public class KineticaSession private constructor(initial: SceneDocument, config: SessionConfig) {
    private val lock = Any()
    private val limits = config.limits
    private var reducer = SceneReducer(config.limits, config.textMeasurer)
    private val sceneHistory = SceneHistory(config.historyCapacity, config.historyMaxChangedNodes)
    private val documentState = MutableStateFlow(initial)
    private val selectionState = MutableStateFlow(SelectionState())
    private val historyState = MutableStateFlow(HistoryState())
    private var transaction: OpenTransaction? = null
    private var closed = false

    public val document: StateFlow<SceneDocument> = documentState.asStateFlow()
    public val selection: StateFlow<SelectionState> = selectionState.asStateFlow()
    public val commands: CommandDispatcher = Dispatcher()
    public val history: HistoryController = History()

    /**
     * 글자에 맞춰 크기가 바뀌는 글자 노드([com.naury.kinetica.core.scene.TextAutoSize])를 잴 측정기. 다음 명령부터 쓴다.
     * 편집기(`KineticaEditor`)는 비어 있으면 화면 글꼴로 재는 측정기를 넣는다.
     */
    public var textMeasurer: TextMeasurer? = config.textMeasurer
        set(value) {
            synchronized(lock) {
                field = value
                reducer = SceneReducer(limits, value)
            }
        }

    /** 선택을 바꾼다. 문서에 없는 id는 무시하고 중복은 한 번만 남긴다. */
    public fun select(ids: Collection<NodeId>) {
        synchronized(lock) {
            val nodes = documentState.value.nodes
            selectionState.value = SelectionState(ids.filter(nodes::containsKey).distinct())
        }
    }

    public fun clearSelection(): Unit = select(emptyList())

    /** 세션을 닫는다. 열린 트랜잭션은 되돌리고, 이후 모든 쓰기를 거부한다. 여러 번 불러도 된다. */
    public fun close() {
        synchronized(lock) {
            transaction?.let { publish(it.before) }
            transaction = null
            closed = true
        }
    }

    private fun publish(document: SceneDocument) {
        documentState.value = document
        val selected = selectionState.value.selectedIds
        if (selected.any { !document.nodes.containsKey(it) }) {
            selectionState.value = SelectionState(selected.filter(document.nodes::containsKey))
        }
        historyState.value = HistoryState(sceneHistory.canUndo, sceneHistory.canRedo, sceneHistory.undoLabel, sceneHistory.redoLabel)
    }

    private class OpenTransaction(val label: String, val before: SceneDocument)

    private inner class Dispatcher : CommandDispatcher {
        override val isInTransaction: Boolean get() = synchronized(lock) { transaction != null }

        override fun execute(command: SceneCommand): CommandResult = synchronized(lock) {
            if (closed) return CommandResult.Rejected(CommandError.TransactionState("세션이 닫혔습니다."))
            val before = documentState.value
            val result = reducer.apply(before, command)
            if (result is CommandResult.Applied) {
                if (transaction == null) sceneHistory.record(command::class.simpleName.orEmpty(), before, result.document)
                publish(result.document)
            }
            result
        }

        override fun beginTransaction(label: String): Boolean = synchronized(lock) {
            if (closed || transaction != null) return false
            transaction = OpenTransaction(label, documentState.value)
            true
        }

        override fun commitTransaction(): Boolean = synchronized(lock) {
            val open = transaction ?: return false
            transaction = null
            sceneHistory.record(open.label, open.before, documentState.value)
            publish(documentState.value)
            true
        }

        override fun rollbackTransaction(): Boolean = synchronized(lock) {
            val open = transaction ?: return false
            transaction = null
            publish(open.before)
            true
        }
    }

    private inner class History : HistoryController {
        override val state: StateFlow<HistoryState> = historyState.asStateFlow()

        override fun undo(): Boolean = step(sceneHistory::undo)

        override fun redo(): Boolean = step(sceneHistory::redo)

        private fun step(move: (SceneDocument) -> SceneDocument?): Boolean = synchronized(lock) {
            if (closed || transaction != null) return false
            val next = move(documentState.value) ?: return false
            publish(next)
            true
        }
    }

    public companion object {
        /** 문서가 [SessionConfig.limits] 규칙을 지키면 세션을 연다. 실행 취소 기록은 비어 있다. */
        public fun create(document: SceneDocument, config: SessionConfig = SessionConfig()): SessionOpenResult {
            val violations = SceneValidator(config.limits).validate(document)
            if (violations.isNotEmpty()) return SessionOpenResult.Rejected(violations)
            return SessionOpenResult.Opened(KineticaSession(document, config))
        }
    }
}

public sealed interface SessionOpenResult {
    public data class Opened(val session: KineticaSession) : SessionOpenResult

    public data class Rejected(val violations: List<SceneViolation>) : SessionOpenResult
}
