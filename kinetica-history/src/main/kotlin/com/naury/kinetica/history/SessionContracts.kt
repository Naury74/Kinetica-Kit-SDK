package com.naury.kinetica.history

import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneLimits
import kotlinx.coroutines.flow.StateFlow

/**
 * 문서를 바꾸는 명령의 입구. 모든 쓰기는 이곳을 지나 직렬화된다.
 *
 * 트랜잭션 밖의 [execute]는 명령 하나가 실행 취소 한 단계다. 트랜잭션 안에서는 결과가 바로 [KineticaSession.document]에
 * 반영되지만(미리보기) 실행 취소 기록은 [commitTransaction] 때 한 단계로만 남는다. 트랜잭션은 중첩할 수 없다.
 */
public interface CommandDispatcher {
    public val isInTransaction: Boolean

    /** 명령을 적용한다. 거부되면 문서는 그대로다. 세션이 닫혔으면 [com.naury.kinetica.core.command.CommandError.TransactionState]로 거부한다. */
    public fun execute(command: SceneCommand): CommandResult

    /** 트랜잭션을 연다. 이미 열려 있거나 세션이 닫혔으면 `false`. [label]은 실행 취소 메뉴 등에 쓰는 이름이다. */
    public fun beginTransaction(label: String): Boolean

    /** 열린 트랜잭션의 변경을 실행 취소 한 단계로 남긴다. 바뀐 것이 없으면 단계를 남기지 않는다. 열린 트랜잭션이 없으면 `false`. */
    public fun commitTransaction(): Boolean

    /** 열린 트랜잭션을 시작 전 문서로 되돌린다. 열린 트랜잭션이 없으면 `false`. */
    public fun rollbackTransaction(): Boolean
}

/** 실행 취소·다시 실행. 트랜잭션이 열려 있으면 동작하지 않는다. */
public interface HistoryController {
    public val state: StateFlow<HistoryState>

    /** 되돌렸으면 `true`. */
    public fun undo(): Boolean

    /** 다시 적용했으면 `true`. */
    public fun redo(): Boolean
}

/** 화면의 실행 취소·다시 실행 버튼 상태. */
public data class HistoryState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String? = null,
    val redoLabel: String? = null,
)

/**
 * 선택한 노드. 선택은 문서가 아니므로 실행 취소 기록에 남지 않는다. 문서에서 사라진 노드는 선택에서 빠진다.
 *
 * @property selectedIds 선택한 순서. 마지막이 가장 최근에 고른 노드다.
 */
public data class SelectionState(val selectedIds: List<NodeId> = emptyList()) {
    public val isEmpty: Boolean get() = selectedIds.isEmpty()

    public operator fun contains(id: NodeId): Boolean = id in selectedIds
}

/**
 * @property historyCapacity 실행 취소 단계 수 상한
 * @property historyMaxChangedNodes 실행 취소 기록 전체에 보관하는 바뀐 노드 수 상한
 */
public data class SessionConfig(
    val limits: SceneLimits = SceneLimits.Default,
    val historyCapacity: Int = SceneHistory.DEFAULT_CAPACITY,
    val historyMaxChangedNodes: Int = SceneHistory.DEFAULT_MAX_CHANGED_NODES,
)
