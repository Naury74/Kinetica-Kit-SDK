package com.naury.kinetica.core.command

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.Artboard
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneValidator
import com.naury.kinetica.core.scene.ViolationCode
import com.naury.kinetica.core.testing.TestScenes
import com.naury.kinetica.core.testing.TestScenes.group
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import org.junit.Test

class SceneReducerTest {
    private val reducer = SceneReducer()
    private val sample = TestScenes.sample()

    private fun SceneDocument.after(command: SceneCommand): SceneDocument {
        val result = reducer.apply(this, command)
        check(result is CommandResult.Applied) { "적용되어야 합니다: $result" }
        return result.document
    }

    private fun rejection(document: SceneDocument, command: SceneCommand): CommandError {
        val result = reducer.apply(document, command)
        check(result is CommandResult.Rejected) { "거부되어야 합니다: $result" }
        return result.error
    }

    @Test
    fun `노드를 지정한 위치에 넣고 나머지 순서를 유지한다`() {
        val inserted = sample.after(SceneCommand.AddNode(rect("n"), index = 1))
        val appended = sample.after(SceneCommand.AddNode(rect("m", parent = "g")))

        assertThat(inserted.rootOrder.map { it.value }).containsExactly("a", "n", "b", "g").inOrder()
        assertThat(appended.childrenOf(id("g")).map { it.value }).containsExactly("c", "d", "m").inOrder()
    }

    @Test
    fun `속성을 바꿔도 순서는 그대로다`() {
        val updated = sample.after(SceneCommand.UpdateProperties(rect("b").copy(name = "renamed", style = NodeStyle(opacity = 0.5))))

        assertThat(updated.rootOrder).isEqualTo(sample.rootOrder)
        assertThat(updated.node(id("b"))?.name).isEqualTo("renamed")
    }

    @Test
    fun `가운데 노드를 지워도 나머지 순서는 그대로다`() {
        val removed = sample.after(SceneCommand.RemoveNodes(setOf(id("b"), id("c"))))

        assertThat(removed.rootOrder.map { it.value }).containsExactly("a", "g").inOrder()
        assertThat(removed.childrenOf(id("g")).map { it.value }).containsExactly("d")
    }

    @Test
    fun `그룹을 지우면 자손과 그 track도 지운다`() {
        val track = PropertyTrack(id("c"), AnimatableProperty.Opacity, listOf(Keyframe(0, KeyValue.Scalar(1.0))))
        val keep = PropertyTrack(id("a"), AnimatableProperty.Opacity, listOf(Keyframe(0, KeyValue.Scalar(1.0))))
        val animated = sample.copy(timelines = listOf(Timeline("t", 100, listOf(track, keep))))

        val removed = animated.after(SceneCommand.RemoveNodes(setOf(id("g"))))

        assertThat(removed.nodes.keys.map { it.value }).containsExactly("a", "b")
        assertThat(removed.timelines.single().tracks).containsExactly(keep)
    }

    @Test
    fun `거부한 명령은 문서를 바꾸지 않는다`() {
        val before = TestScenes.sample()
        val commands = listOf(
            SceneCommand.AddNode(rect("a")),
            SceneCommand.AddNode(rect("x", parent = "ghost")),
            SceneCommand.AddNode(rect("x"), index = 9),
            SceneCommand.RemoveNodes(setOf(id("a"), id("ghost"))),
            SceneCommand.TransformNodes(mapOf(id("a") to Transform2D(translationX = 1.0), id("ghost") to Transform2D())),
            SceneCommand.TransformNodes(mapOf(id("a") to Transform2D(scaleX = 0.0))),
            SceneCommand.ReorderNode(id("a"), toIndex = 3),
            SceneCommand.SetArtboard(Artboard(-1.0, 10.0)),
        )

        commands.forEach { command ->
            assertThat(reducer.apply(sample, command)).isInstanceOf(CommandResult.Rejected::class.java)
            assertThat(sample).isEqualTo(before)
        }
    }

    @Test
    fun `여러 노드 변환 중 하나라도 잘못되면 아무것도 바꾸지 않는다`() {
        val error = rejection(
            sample,
            SceneCommand.TransformNodes(mapOf(id("a") to Transform2D(translationX = 5.0), id("b") to Transform2D(scaleY = 1e-9))),
        )

        assertThat(error).isInstanceOf(CommandError.InvalidResult::class.java)
        assertThat((error as CommandError.InvalidResult).violations.map { it.code }).containsExactly(ViolationCode.InvalidTransform)
    }

    @Test
    fun `구조를 바꾸는 속성 변경은 거부한다`() {
        assertThat(rejection(sample, SceneCommand.UpdateProperties(rect("c")))).isEqualTo(CommandError.StructureChange(id("c")))
        assertThat(rejection(sample, SceneCommand.UpdateProperties(group("g", listOf("c")))))
            .isEqualTo(CommandError.StructureChange(id("g")))
        val asEllipse = sample.node(id("a"))!!.copy(content = NodeContent.Ellipse(com.naury.kinetica.core.geometry.Size(1.0, 1.0)))
        assertThat(rejection(sample, SceneCommand.UpdateProperties(asEllipse))).isEqualTo(CommandError.StructureChange(id("a")))
    }

    @Test
    fun `자식이 있는 그룹은 한 번에 넣을 수 없다`() {
        assertThat(rejection(sample, SceneCommand.AddNode(group("g2", listOf("a"))))).isEqualTo(CommandError.StructureChange(id("g2")))
        assertThat(rejection(sample, SceneCommand.AddNode(rect("x", parent = "a")))).isEqualTo(CommandError.StructureChange(id("a")))
    }

    @Test
    fun `같은 부모 안에서 순서를 옮긴다`() {
        val moved = sample.after(SceneCommand.ReorderNode(id("a"), toIndex = 2))
        val inGroup = sample.after(SceneCommand.ReorderNode(id("d"), toIndex = 0))

        assertThat(moved.rootOrder.map { it.value }).containsExactly("b", "g", "a").inOrder()
        assertThat(inGroup.childrenOf(id("g")).map { it.value }).containsExactly("d", "c").inOrder()
    }

    @Test
    fun `임의 명령 1000개를 적용해도 문서는 항상 올바르다`() {
        val random = kotlin.random.Random(seed = 3)
        var document = sample
        var counter = 0
        repeat(1_000) {
            val ids = document.nodes.keys.toList()
            val command = when (random.nextInt(4)) {
                0 -> SceneCommand.AddNode(rect("r${counter++}"), index = random.nextInt(document.rootOrder.size + 2))

                1 -> SceneCommand.RemoveNodes(setOf(ids.randomOrNull(random) ?: id("none")))

                2 -> SceneCommand.TransformNodes(
                    mapOf((ids.randomOrNull(random) ?: id("none")) to Transform2D(random.nextDouble(-10.0, 10.0))),
                )

                else -> SceneCommand.ReorderNode(ids.randomOrNull(random) ?: id("none"), random.nextInt(4))
            }
            val result = reducer.apply(document, command)
            if (result is CommandResult.Applied) document = result.document
            assertThat(SceneValidator().validate(document)).isEmpty()
        }
    }
}
