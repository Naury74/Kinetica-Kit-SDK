package com.naury.kinetica.guides

import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.NodeId

/**
 * 스냅 기준. 거리는 화면에서 느끼는 크기여야 하므로 viewport 픽셀이다(dp × density).
 *
 * @property enterThresholdPx 이 거리 안에 들어오면 붙는다(docs 07 기본 8dp).
 * @property exitThresholdPx 붙은 뒤 이 거리를 벗어나야 떨어진다(기본 12dp). [enterThresholdPx] 이상이어야 한다.
 * @property gridSize 문서 단위 격자 간격. `null`이면 격자에 붙지 않는다.
 * @throws IllegalArgumentException 거리가 음수이거나 exit가 enter보다 작거나 격자 간격이 0 이하일 때
 */
public data class SnapConfig(
    val enterThresholdPx: Double,
    val exitThresholdPx: Double,
    val gridSize: Double? = null,
    val snapToArtboard: Boolean = true,
    val snapToNodes: Boolean = true,
) {
    init {
        require(enterThresholdPx >= 0.0 && exitThresholdPx >= enterThresholdPx) { "exit 거리는 enter 거리 이상이어야 합니다." }
        require(gridSize == null || (gridSize.isFinite() && gridSize > 0.0)) { "격자 간격은 0보다 커야 합니다." }
    }

    public companion object {
        public fun forDensity(density: Float, gridSize: Double? = null): SnapConfig =
            SnapConfig(enterThresholdPx = ENTER_DP * density, exitThresholdPx = EXIT_DP * density, gridSize = gridSize)

        private const val ENTER_DP = 8.0
        private const val EXIT_DP = 12.0
    }
}

/** 안내선 방향. [Vertical]은 x가 일정한 세로선, [Horizontal]은 y가 일정한 가로선이다. */
public enum class GuideAxis { Vertical, Horizontal }

/** 무엇에 붙었는지. 앞에 있을수록 먼저 고른다. */
public enum class SnapKind { NodeEdge, NodeCenter, ArtboardEdge, ArtboardCenter, Grid }

/**
 * 붙은 안내선 하나. 화면에 그릴 때는 [position]의 선을 [spanStart]..[spanEnd] 구간만 그린다(모두 문서 좌표).
 *
 * @property sourceId 노드에 붙었으면 그 노드
 */
public data class SnapGuide(
    val axis: GuideAxis,
    val position: Double,
    val kind: SnapKind,
    val spanStart: Double,
    val spanEnd: Double,
    val sourceId: NodeId? = null,
)

/** @property delta 스냅을 반영한 문서 이동량 */
public data class SnapResult(val delta: Vec2, val guides: List<SnapGuide>)
