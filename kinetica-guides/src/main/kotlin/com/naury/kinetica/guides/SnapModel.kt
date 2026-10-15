package com.naury.kinetica.guides

import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.scene.NodeId

/**
 * 스냅 설정. 거리는 화면에서 느끼는 크기 기준이어야 하므로 viewport 픽셀(dp × density)로 정한다.
 *
 * @property enterThresholdPx 이 거리 안으로 들어오면 스냅된다. docs 07의 기본값은 8dp다.
 * @property exitThresholdPx 스냅된 뒤 이 거리를 벗어나야 풀린다. 기본값은 12dp이며 [enterThresholdPx] 이상이어야 한다.
 * @property gridSize 문서 단위 격자 간격. `null`이면 격자에 스냅하지 않는다.
 * @throws IllegalArgumentException 거리가 음수이거나, exit 거리가 enter 거리보다 작거나, 격자 간격이 0 이하인 경우
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

/** 가이드선 방향. [Vertical]은 x가 일정한 세로선, [Horizontal]은 y가 일정한 가로선이다. */
public enum class GuideAxis { Vertical, Horizontal }

/** 스냅 대상의 종류. 앞에 있을수록 우선한다. */
public enum class SnapKind { NodeEdge, NodeCenter, ArtboardEdge, ArtboardCenter, Grid }

/**
 * 스냅된 가이드선 하나. 화면에는 [position] 위치의 선을 [spanStart]..[spanEnd] 구간만 그린다. 값은 모두 문서 좌표다.
 *
 * @property sourceId 노드에 스냅됐으면 그 노드의 id
 */
public data class SnapGuide(
    val axis: GuideAxis,
    val position: Double,
    val kind: SnapKind,
    val spanStart: Double,
    val spanEnd: Double,
    val sourceId: NodeId? = null,
)

/** @property delta 스냅을 반영한 문서 좌표 이동량 */
public data class SnapResult(val delta: Vec2, val guides: List<SnapGuide>)
