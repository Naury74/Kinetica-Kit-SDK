package com.naury.kinetica.editor

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.AlignMode
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.history.SessionOpenResult
import org.junit.Test

class EditorActionsTest {
    private val scene = document(
        listOf("a", "b", "c", "d", "e").mapIndexed { i, name ->
            rect(
                name,
                transform = Transform2D(
                    translationX = i * 150.0,
                    translationY =
                        i * 10.0,
                ),
            )
        },
    )
    private val session = (KineticaSession.create(scene) as SessionOpenResult.Opened).session
    private val actions = EditorActions(session)

    private fun order() = session.document.value.rootOrder.map { it.value }

    @Test
    fun `앞으로·뒤로는 고른 노드끼리 순서를 지키며 한 칸씩 옮긴다`() {
        session.select(listOf(id("b"), id("d")))

        actions.bringForward()
        assertThat(order()).containsExactly("a", "c", "b", "e", "d").inOrder()
        actions.bringForward()
        assertThat(order()).containsExactly("a", "c", "e", "b", "d").inOrder()
        actions.bringForward()
        assertThat(order()).containsExactly("a", "c", "e", "b", "d").inOrder()

        session.select(listOf(id("a"), id("c")))
        actions.sendBackward()
        assertThat(order()).containsExactly("a", "c", "e", "b", "d").inOrder()
    }

    @Test
    fun `여러 명령 동작도 실행 취소 한 단계다`() {
        session.select(listOf(id("a"), id("b"), id("c")))

        actions.align(AlignMode.Top)
        assertThat(SceneGraph(session.document.value).worldBounds(id("c"))!!.top).isEqualTo(0.0)
        session.history.undo()

        assertThat(session.document.value).isEqualTo(scene)
    }

    @Test
    fun `묶은 그룹을 고르고 풀면 자식을 고른다`() {
        session.select(listOf(id("b"), id("c")))

        actions.group()
        val group = session.selection.value.selectedIds.single()
        assertThat(session.document.value.node(group)?.content).isInstanceOf(NodeContent.Group::class.java)
        actions.ungroup()

        assertThat(session.selection.value.selectedIds).containsExactly(id("b"), id("c")).inOrder()
        assertThat(session.document.value).isEqualTo(scene)
    }
}
