package com.naury.kinetica.history

import com.naury.kinetica.core.scene.SceneDocument

/**
 * 실행 취소 기록. 화면 프레임 단위가 아니라 제스처 하나나 명령 하나 같은 트랜잭션 단위로, 바뀐 부분만 한 단계씩 쌓는다.
 *
 * 스레드 안전하지 않다. [KineticaSession]이 쓰기를 직렬화한다.
 *
 * @param capacity 보관할 최대 단계 수. 넘으면 가장 오래된 단계부터 버린다.
 * @param maxChangedNodes 모든 단계에 걸쳐 보관하는 바뀐 노드 수의 합계 한도. 메모리 예산 역할을 한다.
 * @throws IllegalArgumentException 한도가 0 이하인 경우
 */
public class SceneHistory(private val capacity: Int = DEFAULT_CAPACITY, private val maxChangedNodes: Int = DEFAULT_MAX_CHANGED_NODES) {
    init {
        require(capacity > 0 && maxChangedNodes > 0) { "history 한도는 0보다 커야 합니다." }
    }

    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()

    public val canUndo: Boolean get() = undoStack.isNotEmpty()
    public val canRedo: Boolean get() = redoStack.isNotEmpty()
    public val undoLabel: String? get() = undoStack.lastOrNull()?.label
    public val redoLabel: String? get() = redoStack.lastOrNull()?.label
    public val undoDepth: Int get() = undoStack.size

    /** [before]에서 [after]로 바뀐 내용을 한 단계로 쌓는다. 바뀐 것이 없으면 쌓지 않으며, 새 단계를 쌓으면 redo 기록은 지워진다. */
    public fun record(label: String, before: SceneDocument, after: SceneDocument): Boolean {
        val diff = SceneDiff.between(before, after)
        if (diff.isEmpty) return false
        undoStack.addLast(Entry(label, diff))
        redoStack.clear()
        trim()
        return true
    }

    /** [current]에서 한 단계 되돌린 문서. 되돌릴 것이 없으면 `null`이다. [current]는 마지막으로 기록하거나 적용한 문서여야 한다. */
    public fun undo(current: SceneDocument): SceneDocument? {
        val entry = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(entry)
        return entry.diff.undo(current)
    }

    /** [current]에서 한 단계 다시 적용한 문서. 다시 적용할 것이 없으면 `null`이다. */
    public fun redo(current: SceneDocument): SceneDocument? {
        val entry = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(entry)
        return entry.diff.redo(current)
    }

    public fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    private fun trim() {
        while (undoStack.size > capacity) undoStack.removeFirst()
        var retained = undoStack.sumOf { it.diff.changedNodeCount }
        while (retained > maxChangedNodes && undoStack.size > 1) {
            retained -= undoStack.removeFirst().diff.changedNodeCount
        }
    }

    private class Entry(val label: String, val diff: SceneDiff)

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 200
        public const val DEFAULT_MAX_CHANGED_NODES: Int = 200_000
    }
}
