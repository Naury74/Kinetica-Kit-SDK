package com.naury.kinetica.showcase

import com.google.common.truth.Truth.assertThat
import com.naury.kinetica.board.KineticaBoardState
import com.naury.kinetica.core.scene.Layouts
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneGraph
import com.naury.kinetica.render.HOST_VIEW_UNITS_PER_DP
import org.junit.Test

/** 예제 화면의 카드가 같은 격자선과 같은 모서리에 맞는지 본다. 화면 폭이 바뀌어도 그래야 한다. */
class ShowcaseLayoutTest {
    @Test
    fun `대시보드 장면의 카드는 같은 줄에서 높이가 같고 끝선과 모서리가 맞는다`() {
        listOf(1080.0, 1600.0, 2400.0).forEach { width ->
            val document = Layouts.resizeArtboard(DashboardScene.create(), width, 1740.0)
            val graph = SceneGraph(document)
            fun bounds(id: String) = requireNotNull(graph.worldBounds(NodeId(id)))

            assertThat(bounds("sales-card").height).isWithin(EPS).of(bounds("orders-card").height)
            assertThat(bounds("clock-card").height).isWithin(EPS).of(bounds("weather-card").height)
            assertThat(bounds("sales-card").width).isWithin(EPS).of(bounds("orders-card").width)
            assertThat(bounds("clock-card").width).isWithin(EPS).of(bounds("weather-card").width)
            // 왼쪽 끝과 오른쪽 끝이 모든 줄에서 같다.
            val left = bounds("sales-card").left
            val right = bounds("orders-card").right
            listOf("chart-card", "clock-card", "counter-card", "refresh").forEach {
                assertThat(bounds(it).left).isWithin(EPS).of(left)
            }
            listOf("chart-card", "weather-card", "counter-card", "refresh").forEach {
                assertThat(bounds(it).right).isWithin(EPS).of(right)
            }
            // 카드 사이 간격은 가로, 세로 모두 같다.
            val gap = bounds("orders-card").left - bounds("sales-card").right
            assertThat(bounds("chart-card").top - bounds("sales-card").bottom).isWithin(EPS).of(gap)
            assertThat(bounds("clock-card").top - bounds("chart-card").bottom).isWithin(EPS).of(gap)
            assertThat(bounds("counter-card").top - bounds("clock-card").bottom).isWithin(EPS).of(gap)
        }
        val document = DashboardScene.create()
        val radii = listOf("sales-card", "orders-card", "chart-card", "clock-card", "weather-card", "counter-card")
            .map { (document.nodes.getValue(NodeId(it)).content as NodeContent.Frame).cornerRadius }
        assertThat(radii.toSet()).hasSize(1)
    }

    @Test
    fun `보드 위젯의 처음 너비는 두 칸과 한 줄 전체가 기준 폭에 꼭 맞는다`() {
        val reference = KineticaBoardState.REFERENCE_WIDTH / HOST_VIEW_UNITS_PER_DP
        assertThat(BOARD_HALF_DP * 2 + 12.0 * 3).isWithin(0.05).of(reference)
        assertThat(BOARD_FULL_DP + 12.0 * 2).isWithin(0.05).of(reference)
        assertThat(BOARD_HALF_DP * 2 + 12.0).isWithin(0.05).of(BOARD_FULL_DP.toDouble())
    }

    private companion object {
        const val EPS = 0.5
    }
}
