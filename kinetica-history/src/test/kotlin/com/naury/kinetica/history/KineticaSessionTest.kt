package com.naury.kinetica.history

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.command.CommandError
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.ViolationCode
import com.naury.kinetica.core.testing.TestScenes
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import kotlin.random.Random
import org.junit.Test

class KineticaSessionTest {
    private fun open(document: SceneDocument = TestScenes.sample(), config: SessionConfig = SessionConfig()): KineticaSession =
        (KineticaSession.create(document, config) as SessionOpenResult.Opened).session

    private fun move(x: Double) = SceneCommand.TransformNodes(mapOf(id("a") to Transform2D(translationX = x)))

    @Test
    fun `규칙을 어긴 문서로는 세션을 열지 않는다`() {
        val invalid = TestScenes.document(listOf(rect("a")), rootOrder = listOf("a", "a"))

        val result = KineticaSession.create(invalid)

        assertThat((result as SessionOpenResult.Rejected).violations.map { it.code }).containsExactly(ViolationCode.DuplicateChild)
    }

    @Test
    fun `제스처 하나는 프레임 수와 상관없이 실행 취소 한 단계다`() {
        val session = open()
        val before = session.document.value

        session.commands.beginTransaction("drag")
        (1..60).forEach { session.commands.execute(move(it.toDouble())) }
        assertThat(session.document.value.node(id("a"))?.transform?.translationX).isEqualTo(60.0)
        assertThat(session.history.state.value.canUndo).isFalse()
        session.commands.commitTransaction()

        assertThat(session.history.state.value).isEqualTo(HistoryState(canUndo = true, undoLabel = "drag"))
        assertThat(session.history.undo()).isTrue()
        assertThat(session.document.value).isEqualTo(before)
        assertThat(session.history.undo()).isFalse()
    }

    @Test
    fun `취소한 트랜잭션은 시작 전 문서로 돌아가고 기록을 남기지 않는다`() {
        val session = open()
        val before = session.document.value

        session.commands.beginTransaction("drag")
        session.commands.execute(move(10.0))
        session.commands.execute(SceneCommand.TransformNodes(mapOf(id("ghost") to Transform2D())))
        session.commands.rollbackTransaction()

        assertThat(session.document.value).isEqualTo(before)
        assertThat(session.history.state.value.canUndo).isFalse()
    }

    @Test
    fun `바뀐 것이 없는 트랜잭션은 단계를 남기지 않는다`() {
        val session = open()

        session.commands.beginTransaction("tap")
        session.commands.commitTransaction()

        assertThat(session.history.state.value.canUndo).isFalse()
    }

    @Test
    fun `트랜잭션은 중첩할 수 없고 열린 동안 실행 취소하지 않는다`() {
        val session = open()
        session.commands.execute(move(5.0))

        assertThat(session.commands.beginTransaction("outer")).isTrue()
        assertThat(session.commands.beginTransaction("inner")).isFalse()
        assertThat(session.history.undo()).isFalse()
        assertThat(session.commands.commitTransaction()).isTrue()
        assertThat(session.commands.commitTransaction()).isFalse()
        assertThat(session.commands.rollbackTransaction()).isFalse()
    }

    @Test
    fun `실행 취소 뒤 새 명령은 다시 실행 기록을 지운다`() {
        val session = open()
        session.commands.execute(move(1.0))
        session.commands.execute(move(2.0))
        session.history.undo()
        assertThat(session.history.state.value.canRedo).isTrue()

        session.commands.execute(move(3.0))

        assertThat(session.history.state.value.canRedo).isFalse()
        assertThat(session.history.redo()).isFalse()
    }

    @Test
    fun `그룹을 지웠다 되돌리면 자식과 형제 순서가 그대로다`() {
        val session = open()
        val before = session.document.value

        session.commands.execute(SceneCommand.RemoveNodes(setOf(id("g"))))
        session.history.undo()

        assertThat(session.document.value).isEqualTo(before)
        assertThat(session.document.value.rootOrder.map { it.value }).containsExactly("a", "b", "g").inOrder()
        assertThat(session.document.value.childrenOf(id("g")).map { it.value }).containsExactly("c", "d").inOrder()
    }

    @Test
    fun `임의 명령 1000개를 모두 되돌리면 처음 문서, 모두 다시 하면 마지막 문서와 같다`() {
        val random = Random(seed = 201)
        val session = open(config = SessionConfig(historyCapacity = 2_000))
        val initial = session.document.value
        var counter = 0
        repeat(1_000) {
            val document = session.document.value
            val ids = document.nodes.keys.toList()
            val target = ids.randomOrNull(random) ?: id("none")
            val command = when (random.nextInt(5)) {
                0 -> SceneCommand.AddNode(rect("n${counter++}", parent = if (random.nextBoolean()) "g" else null))

                1 -> SceneCommand.RemoveNodes(setOf(target))

                2 -> SceneCommand.TransformNodes(
                    mapOf(target to Transform2D(random.nextDouble(-50.0, 50.0), random.nextDouble(-50.0, 50.0))),
                )

                3 -> SceneCommand.ReorderNode(target, random.nextInt(3))

                else -> document.node(target)?.let {
                    SceneCommand.UpdateProperties(it.copy(style = NodeStyle(opacity = random.nextDouble())))
                }
                    ?: SceneCommand.RemoveNodes(setOf(target))
            }
            session.commands.execute(command)
        }
        val final = session.document.value

        while (session.history.undo()) Unit
        assertThat(session.document.value).isEqualTo(initial)
        while (session.history.redo()) Unit
        assertThat(session.document.value).isEqualTo(final)
    }

    @Test
    fun `단계 수 상한을 넘으면 오래된 단계부터 버린다`() {
        val session = open(config = SessionConfig(historyCapacity = 3))
        (1..5).forEach { session.commands.execute(move(it.toDouble())) }

        var undone = 0
        while (session.history.undo()) undone++

        assertThat(undone).isEqualTo(3)
        assertThat(session.document.value.node(id("a"))?.transform?.translationX).isEqualTo(2.0)
    }

    @Test
    fun `선택은 기록되지 않고 지운 노드는 선택에서 빠진다`() {
        val session = open()
        session.select(listOf(id("a"), id("ghost"), id("b"), id("a")))
        assertThat(session.selection.value.selectedIds).containsExactly(id("a"), id("b")).inOrder()

        session.commands.execute(SceneCommand.RemoveNodes(setOf(id("a"))))
        assertThat(session.selection.value.selectedIds).containsExactly(id("b"))

        session.history.undo()
        assertThat(session.selection.value.selectedIds).containsExactly(id("b"))
    }

    @Test
    fun `닫힌 세션은 쓰기를 거부하고 열린 트랜잭션을 되돌린다`() {
        val session = open()
        val before = session.document.value
        session.commands.beginTransaction("drag")
        session.commands.execute(move(9.0))

        session.close()

        assertThat(session.document.value).isEqualTo(before)
        assertThat(session.commands.execute(move(1.0))).isInstanceOf(CommandResult.Rejected::class.java)
        assertThat((session.commands.execute(move(1.0)) as CommandResult.Rejected).error)
            .isInstanceOf(CommandError.TransactionState::class.java)
        assertThat(session.commands.beginTransaction("x")).isFalse()
    }

    @Test
    fun `여러 스레드의 쓰기는 하나도 잃지 않는다`() {
        val session = open()
        val threads = (0 until 8).map { worker ->
            Thread { repeat(50) { session.commands.execute(SceneCommand.AddNode(rect("t$worker-$it"))) } }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertThat(session.document.value.nodes).hasSize(TestScenes.sample().nodes.size + 400)
        assertThat(session.history.state.value.canUndo).isTrue()
    }
}
