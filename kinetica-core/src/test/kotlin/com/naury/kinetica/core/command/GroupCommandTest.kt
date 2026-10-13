package com.naury.kinetica.core.command

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Mat3
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.geometry.fromMatrix
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.testing.TestScenes
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.group
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import kotlin.random.Random
import org.junit.Test

class GroupCommandTest {
    private val reducer = SceneReducer()

    private fun SceneDocument.after(command: SceneCommand): SceneDocument {
        val result = reducer.apply(this, command)
        check(result is CommandResult.Applied) { "적용되어야 합니다: $result" }
        return result.document
    }

    private fun worldCorners(document: SceneDocument, id: NodeId): List<Vec2> {
        val graph = SceneGraph(document)
        val matrix = checkNotNull(graph.worldMatrix(id))
        return checkNotNull(document.node(id)?.localBounds).corners().map(matrix::map)
    }

    @Test
    fun `묶으면 맨 위 자리에 그룹이 들어가고 화면 위치는 그대로다`() {
        val doc = document(listOf(rect("a"), rect("b"), rect("c"), rect("d")))

        val grouped = doc.after(SceneCommand.Group(listOf(id("c"), id("a")), id("g")))

        assertThat(grouped.rootOrder.map { it.value }).containsExactly("b", "g", "d").inOrder()
        assertThat(grouped.childrenOf(id("g")).map { it.value }).containsExactly("a", "c").inOrder()
        assertThat(worldCorners(grouped, id("a"))).isEqualTo(worldCorners(doc, id("a")))
    }

    @Test
    fun `풀면 그룹 변환을 자식에 합쳐 화면 위치를 유지하고 그룹 자리에 놓는다`() {
        val random = Random(seed = 107)
        repeat(200) {
            val groupTransform = Transform2D(
                translationX = random.nextDouble(-300.0, 300.0),
                translationY = random.nextDouble(-300.0, 300.0),
                scaleX = random.nextDouble(0.5, 2.0).let { s -> s },
                scaleY = 0.0,
                rotationDegrees = random.nextDouble(-180.0, 180.0),
                pivotX = random.nextDouble(-50.0, 50.0),
                pivotY = random.nextDouble(-50.0, 50.0),
            ).let { it.copy(scaleY = it.scaleX) }
            val child = Transform2D(
                translationX = random.nextDouble(-100.0, 100.0),
                translationY = random.nextDouble(-100.0, 100.0),
                scaleX = random.nextDouble(0.5, 2.0),
                scaleY = random.nextDouble(0.5, 2.0),
                rotationDegrees = random.nextDouble(-180.0, 180.0),
                pivotX = 50.0,
                pivotY = 25.0,
            )
            val doc = document(
                listOf(
                    rect("x"),
                    group("g", listOf("a", "b"), transform = groupTransform),
                    rect("a", parent = "g", transform = child),
                    rect("b", parent = "g"),
                    rect("y"),
                ),
                rootOrder = listOf("x", "g", "y"),
            )

            val ungrouped = doc.after(SceneCommand.Ungroup(id("g")))

            assertThat(ungrouped.rootOrder.map { it.value }).containsExactly("x", "a", "b", "y").inOrder()
            assertThat(ungrouped.node(id("a"))?.parentId).isNull()
            assertThat(ungrouped.node(id("a"))?.transform?.pivotX).isEqualTo(50.0)
            worldCorners(ungrouped, id("a")).zip(worldCorners(doc, id("a"))).forEach { (after, before) ->
                assertThat(after.distanceTo(before)).isLessThan(1e-6)
            }
        }
    }

    @Test
    fun `풀면 그룹 opacity·숨김·잠금을 자식에 합친다`() {
        val doc = document(
            listOf(
                group("g", listOf("a")).copy(style = NodeStyle(opacity = 0.5), visible = false, locked = true),
                rect("a", parent = "g").copy(style = NodeStyle(opacity = 0.5)),
            ),
            rootOrder = listOf("g"),
        )

        val child = doc.after(SceneCommand.Ungroup(id("g"))).node(id("a"))!!

        assertThat(child.style.opacity).isEqualTo(0.25)
        assertThat(child.visible).isFalse()
        assertThat(child.locked).isTrue()
    }

    @Test
    fun `기울임이 생기는 풀기와 부모가 다른 묶기는 거부한다`() {
        val skewed = document(
            listOf(
                group("g", listOf("a"), transform = Transform2D(scaleX = 2.0, scaleY = 1.0)),
                rect("a", parent = "g", transform = Transform2D(rotationDegrees = 30.0)),
            ),
            rootOrder = listOf("g"),
        )

        assertThat(
            reducer.apply(skewed, SceneCommand.Ungroup(id("g"))),
        ).isEqualTo(CommandResult.Rejected(CommandError.UnrepresentableTransform(id("a"))))
        assertThat(reducer.apply(TestScenes.sample(), SceneCommand.Group(listOf(id("a"), id("c")), id("n"))))
            .isEqualTo(CommandResult.Rejected(CommandError.MixedParents(listOf(id("a"), id("c")))))
        assertThat(reducer.apply(TestScenes.sample(), SceneCommand.Group(emptyList(), id("n"))))
            .isEqualTo(CommandResult.Rejected(CommandError.MixedParents(emptyList())))
    }

    @Test
    fun `묶고 풀면 원래 문서와 같다`() {
        val doc = document(listOf(rect("a", transform = Transform2D(translationX = 5.0)), rect("b"), rect("c")))

        val roundTrip = doc.after(SceneCommand.Group(listOf(id("a"), id("b")), id("g"))).after(SceneCommand.Ungroup(id("g")))

        assertThat(roundTrip).isEqualTo(doc)
    }

    @Test
    fun `행렬을 변환으로 나누면 다시 같은 행렬이 된다`() {
        val random = Random(seed = 5)
        repeat(1_000) {
            val original = Transform2D(
                translationX = random.nextDouble(-500.0, 500.0),
                translationY = random.nextDouble(-500.0, 500.0),
                scaleX = random.nextDouble(0.1, 5.0),
                scaleY = random.nextDouble(-5.0, 5.0).let { if (kotlin.math.abs(it) < 0.1) 1.0 else it },
                rotationDegrees = random.nextDouble(-180.0, 180.0),
                pivotX = random.nextDouble(-50.0, 50.0),
                pivotY = random.nextDouble(-50.0, 50.0),
            )
            val rebuilt = checkNotNull(Transform2D.fromMatrix(original.matrix(), original.pivotX, original.pivotY)).matrix()
            val point = Vec2(37.0, -11.0)
            assertThat(rebuilt.map(point).distanceTo(original.matrix().map(point))).isLessThan(1e-6)
        }
        assertThat(Transform2D.fromMatrix(Mat3(1.0, 0.0, 0.5, 1.0, 0.0, 0.0))).isNull()
        assertThat(Transform2D.fromMatrix(Mat3.scale(0.0, 1.0))).isNull()
    }
}
