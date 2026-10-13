package com.naury.kinetica.guides

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.core.testing.TestScenes.document
import com.naury.kinetica.core.testing.TestScenes.id
import com.naury.kinetica.core.testing.TestScenes.rect
import kotlin.system.measureNanoTime
import org.junit.Test

class MoveSnapperTest {
    private val config = SnapConfig(enterThresholdPx = 8.0, exitThresholdPx = 12.0)

    // 옮기는 노드 m: x 100..200, y 1000..1050. 다른 노드 o: x 400..500, y 300..350. artboard 1080x1920.
    private val scene = document(
        listOf(
            rect("m", size = Size(100.0, 50.0), transform = Transform2D(translationX = 100.0, translationY = 1000.0)),
            rect("o", size = Size(100.0, 50.0), transform = Transform2D(translationX = 400.0, translationY = 300.0)),
        ),
    )

    private fun snapper(config: SnapConfig = this.config) = MoveSnapper(SceneGraph(scene), listOf(id("m")), config)

    @Test
    fun `가까운 노드 가장자리에 붙고 안내선을 돌려준다`() {
        val result = snapper().snap(Vec2(195.0, 0.0), zoom = 1.0)

        assertThat(result.delta).isEqualTo(Vec2(200.0, 0.0))
        val guide = result.guides.single()
        assertThat(guide.axis).isEqualTo(GuideAxis.Vertical)
        assertThat(guide.position).isEqualTo(400.0)
        assertThat(guide.sourceId).isEqualTo(id("o"))
        assertThat(guide.spanStart).isEqualTo(300.0)
        assertThat(guide.spanEnd).isEqualTo(1050.0)
    }

    @Test
    fun `노드 정렬이 artboard보다 먼저다`() {
        val result = snapper().snap(Vec2(302.0, 0.0), zoom = 1.0)

        assertThat(result.delta.x).isEqualTo(300.0)
        assertThat(result.guides.single().kind).isEqualTo(SnapKind.NodeEdge)
        assertThat(result.guides.single().sourceId).isEqualTo(id("o"))
    }

    @Test
    fun `거리는 화면 픽셀로 잰다`() {
        assertThat(snapper().snap(Vec2(190.0, 0.0), zoom = 1.0).delta.x).isEqualTo(190.0)
        assertThat(snapper().snap(Vec2(190.0, 0.0), zoom = 0.5).delta.x).isEqualTo(200.0)
    }

    @Test
    fun `붙은 뒤에는 나갈 거리까지 머물고 그 뒤에 떨어진다`() {
        val snapper = snapper()
        assertThat(snapper.snap(Vec2(193.0, 0.0), zoom = 1.0).delta.x).isEqualTo(200.0)

        assertThat(snapper.snap(Vec2(189.0, 0.0), zoom = 1.0).delta.x).isEqualTo(200.0)
        assertThat(snapper.snap(Vec2(187.0, 0.0), zoom = 1.0).delta.x).isEqualTo(187.0)
        assertThat(snapper.snap(Vec2(189.0, 0.0), zoom = 1.0).delta.x).isEqualTo(189.0)
    }

    @Test
    fun `두 축은 따로 붙는다`() {
        val result = snapper().snap(Vec2(296.0, -697.0), zoom = 1.0)

        assertThat(result.delta).isEqualTo(Vec2(300.0, -700.0))
        assertThat(result.guides.map { it.axis }).containsExactly(GuideAxis.Vertical, GuideAxis.Horizontal)
    }

    @Test
    fun `artboard 가운데와 격자에 붙는다`() {
        val center = snapper().snap(Vec2(386.0, 0.0), zoom = 1.0)
        val grid = MoveSnapper(
            SceneGraph(scene),
            listOf(id("m")),
            config.copy(gridSize = 64.0, snapToNodes = false, snapToArtboard = false),
        )
            .snap(Vec2(26.0, 0.0), zoom = 1.0)

        assertThat(center.delta.x).isEqualTo(390.0)
        assertThat(center.guides.single().kind).isEqualTo(SnapKind.ArtboardCenter)
        assertThat(grid.delta).isEqualTo(Vec2(28.0, -1.0))
        assertThat(grid.guides.map { it.kind }).containsExactly(SnapKind.Grid, SnapKind.Grid)
    }

    @Test
    fun `같은 입력이면 같은 결과다`() {
        val first = snapper().snap(Vec2(251.0, 3.0), zoom = 1.0)
        val second = snapper().snap(Vec2(251.0, 3.0), zoom = 1.0)

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `후보 2000개에서도 한 프레임 안에 계산한다`() {
        val nodes = (0 until 2_000).map {
            rect(
                "n$it",
                transform = Transform2D(
                    translationX = (it % 50) * 20.0,
                    translationY =
                        (it / 50) * 40.0,
                ),
            )
        }
        val graph = SceneGraph(document(nodes + rect("m")))
        val snapper = MoveSnapper(graph, listOf(id("m")), config)
        repeat(20) { snapper.snap(Vec2(it.toDouble(), it.toDouble()), 1.0) }

        val nanos = measureNanoTime { repeat(60) { snapper.snap(Vec2(it * 3.0, it * 2.0), 1.0) } } / 60

        println("MoveSnapper 2000 candidates: ${nanos / 1_000} µs/frame")
        assertThat(nanos).isLessThan(16_000_000L)
    }
}
