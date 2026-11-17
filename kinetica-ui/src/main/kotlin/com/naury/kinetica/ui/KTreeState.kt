package com.naury.kinetica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.state.ToggleableState

/**
 * [KTreeView]의 펼침, 선택, 체크 상태. [rememberKTreeState]로 만든다. 모든 값은 순번이 아니라 노드 키로 기억하므로
 * 목록이 바뀌어도 같은 노드에 남고, 트리에서 사라진 키는 [KTreeView]가 다음 구성에서 지운다.
 */
@Stable
public class KTreeState(initialExpanded: Set<Any> = emptySet(), initialSelected: Any? = null, initialChecked: Set<Any> = emptySet()) {
    /** 펼친 노드의 키 */
    public var expanded: Set<Any> by mutableStateOf(initialExpanded)

    /** 고른 노드의 키. 없으면 `null`이다. */
    public var selected: Any? by mutableStateOf(initialSelected)

    /** 체크한 잎 노드(자식이 없는 노드)의 키. 부모의 체크 상태는 저장하지 않고 자식에서 계산한다. */
    public var checked: Set<Any> by mutableStateOf(initialChecked)

    /** 키보드로 옮겨 다니는 현재 행. 선택과 달리 Enter를 눌러야 선택이 된다. */
    internal var active: Any? by mutableStateOf(initialSelected)

    public fun isExpanded(key: Any): Boolean = key in expanded

    public fun expand(key: Any) {
        if (key !in expanded) expanded = expanded + key
    }

    public fun collapse(key: Any) {
        if (key in expanded) expanded = expanded - key
    }

    public fun toggle(key: Any) {
        if (key in expanded) collapse(key) else expand(key)
    }

    /** 트리에 없는 키를 펼침, 선택, 체크에서 뺀다. 남겨 두면 같은 키로 다시 나타난 노드가 엉뚱하게 펼쳐지거나 체크된 채 나온다. */
    internal fun retain(present: Set<Any>) {
        if (expanded.any { it !in present }) expanded = expanded.filterTo(HashSet()) { it in present }
        if (checked.any { it !in present }) checked = checked.filterTo(HashSet()) { it in present }
        if (selected != null && selected !in present) selected = null
        if (active != null && active !in present) active = null
    }
}

/** 번들에 담을 수 있는 키만 저장한다. 담을 수 없는 키를 쓰면 회전 뒤 그 값은 빈다. */
private val TreeStateSaver = Saver<KTreeState, ArrayList<Any?>>(
    save = { state ->
        arrayListOf(
            ArrayList(state.expanded.filter { canBeSaved(it) }),
            state.selected?.takeIf { canBeSaved(it) },
            ArrayList(state.checked.filter { canBeSaved(it) }),
        )
    },
    restore = { saved ->
        KTreeState(
            (saved[0] as List<*>).filterNotNullTo(HashSet()),
            saved[1],
            (saved[2] as List<*>).filterNotNullTo(HashSet()),
        )
    },
)

/** [KTreeState]를 만든다. 회전과 프로세스 재시작 뒤에도 남는다. [initialExpanded]는 처음 펼쳐 둘 노드 키다. */
@Composable
public fun rememberKTreeState(initialExpanded: Set<Any> = emptySet()): KTreeState =
    rememberSaveable(saver = TreeStateSaver) { KTreeState(initialExpanded) }

/** 행의 자리. [depth]는 0부터 센 수준, [position]은 형제 [siblings]개 가운데 1부터 센 순번이다. */
internal class TreePlace(val depth: Int, val position: Int, val siblings: Int)

/** 화면에 보이는 행 하나. */
internal class TreeRow<T>(val item: T, val key: Any, val hasChildren: Boolean, val parent: Any?, val place: TreePlace) {
    val depth: Int get() = place.depth
    val position: Int get() = place.position
    val siblings: Int get() = place.siblings
}

/** 노드 키와 자식을 꺼내는 방법. */
internal class TreeAccess<T>(val key: (T) -> Any, val children: (T) -> List<T>)

/**
 * 펼친 노드의 자식만 깊이 우선 순서로 늘어놓는다. 같은 키가 두 번 나오면(순환 포함) 두 번째부터는 건너뛰어 끝없이 돌지 않는다.
 */
internal fun <T> flattenTree(roots: List<T>, access: TreeAccess<T>, expanded: Set<Any>): List<TreeRow<T>> {
    val out = ArrayList<TreeRow<T>>()
    val seen = HashSet<Any>()
    // 재귀 대신 스택을 써서 아주 깊은 트리에서도 스택이 넘치지 않게 한다.
    val stack = ArrayDeque<TreeFrame<T>>()
    stack.addLast(TreeFrame(roots, 0, null))
    while (stack.isNotEmpty()) {
        val frame = stack.last()
        if (frame.next >= frame.items.size) stack.removeLast() else visitTreeItem(frame, stack, TreeVisit(access, expanded, seen, out))
    }
    return out
}

private class TreeVisit<T>(val access: TreeAccess<T>, val expanded: Set<Any>, val seen: HashSet<Any>, val out: ArrayList<TreeRow<T>>)

/** [frame]의 다음 항목을 행으로 더하고, 펼친 부모면 그 자식을 스택에 올린다. 이미 본 키는 건너뛴다. */
private fun <T> visitTreeItem(frame: TreeFrame<T>, stack: ArrayDeque<TreeFrame<T>>, visit: TreeVisit<T>) {
    val index = frame.next++
    val item = frame.items[index]
    val key = visit.access.key(item)
    if (!visit.seen.add(key)) return
    val kids = visit.access.children(item)
    visit.out += TreeRow(item, key, kids.isNotEmpty(), frame.parent, TreePlace(stack.size - 1, index + 1, frame.items.size))
    if (kids.isNotEmpty() && key in visit.expanded) stack.addLast(TreeFrame(kids, 0, key))
}

private class TreeFrame<T>(val items: List<T>, var next: Int, val parent: Any?)

/** 트리 전체의 키. 접힌 노드 아래도 모두 센다. */
internal fun <T> allTreeKeys(roots: List<T>, access: TreeAccess<T>): Set<Any> {
    val keys = HashSet<Any>()
    val pending = ArrayDeque(roots)
    while (pending.isNotEmpty()) {
        val item = pending.removeLast()
        if (keys.add(access.key(item))) pending.addAll(access.children(item))
    }
    return keys
}

/** [item] 아래의 잎 노드 키. [item]이 잎이면 자기 자신이다. */
internal fun <T> leafKeys(item: T, access: TreeAccess<T>): List<Any> {
    val out = ArrayList<Any>()
    val seen = HashSet<Any>()
    val pending = ArrayDeque(listOf(item))
    while (pending.isNotEmpty()) {
        val node = pending.removeLast()
        val key = access.key(node)
        if (!seen.add(key)) continue
        val kids = access.children(node)
        if (kids.isEmpty()) out += key else pending.addAll(kids)
    }
    return out
}

/**
 * 모든 노드의 체크 상태. 잎은 [checked]에 있으면 켜짐이고, 부모는 잎이 모두 켜지면 켜짐, 하나도 없으면 꺼짐, 그 사이면 일부다.
 */
internal fun <T> treeCheckStates(roots: List<T>, access: TreeAccess<T>, checked: Set<Any>): Map<Any, ToggleableState> {
    val out = HashMap<Any, ToggleableState>()
    // 노드마다 (켜진 잎 수, 전체 잎 수)를 자식부터 올려 계산한다.
    val counts = HashMap<Any, LongArray>()
    val order = ArrayList<T>()
    val pending = ArrayDeque(roots)
    val seen = HashSet<Any>()
    while (pending.isNotEmpty()) {
        val node = pending.removeLast()
        if (!seen.add(access.key(node))) continue
        order += node
        pending.addAll(access.children(node))
    }
    for (i in order.indices.reversed()) {
        val node = order[i]
        val key = access.key(node)
        val kids = access.children(node)
        val count = if (kids.isEmpty()) {
            longArrayOf(if (key in checked) 1L else 0L, 1L)
        } else {
            kids.fold(LongArray(2)) { acc, kid ->
                counts[access.key(kid)]?.let {
                    acc[0] += it[0]
                    acc[1] += it[1]
                }
                acc
            }
        }
        counts[key] = count
        out[key] = when {
            count[1] == 0L || count[0] == 0L -> ToggleableState.Off
            count[0] == count[1] -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
    }
    return out
}

/** [item]을 누를 때의 새 체크 집합. 잎이 모두 켜져 있으면 모두 끄고, 아니면 모두 켠다. */
internal fun <T> toggledChecks(item: T, access: TreeAccess<T>, checked: Set<Any>): Set<Any> {
    val leaves = leafKeys(item, access)
    return if (leaves.all { it in checked }) checked - leaves.toSet() else checked + leaves
}
