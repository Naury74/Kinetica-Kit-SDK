package com.naury.kinetica.gestures

import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument

/**
 * 제스처가 읽고 바꾸는 편집 상태. 편집기가 세션과 보기 상태를 이어 구현한다.
 * 제스처는 이 인터페이스로만 문서를 바꾸므로 저장소나 실행 취소 구현을 모른다.
 */
public interface GestureHost {
    public val document: SceneDocument
    public val selection: List<NodeId>
    public var viewport: Viewport

    public fun select(ids: List<NodeId>)

    /** 이미 트랜잭션이 열려 있으면 `false`. */
    public fun beginTransaction(label: String): Boolean

    /** 적용했으면 `true`. 거부되면 문서는 그대로다. */
    public fun execute(command: SceneCommand): Boolean

    public fun commitTransaction()

    public fun rollbackTransaction()
}

/**
 * 제스처 판정 기준. 모두 viewport 픽셀 단위이므로 dp 값에 density를 곱해 넘긴다.
 *
 * @property touchSlopPx 이만큼 움직여야 끌기로 본다.
 * @property handleTouchRadiusPx 손잡이를 잡는 반경. 48dp 터치 영역이면 24dp다.
 * @property hitSlopPx 노드를 맞힐 때 더하는 여유 거리
 * @property rotateHandleOffsetPx 회전 손잡이가 위 변에서 떨어진 거리
 * @property rotationSnapDegrees 0보다 크면 회전 각도를 이 배수로 맞춘다.
 */
public data class GestureConfig(
    val touchSlopPx: Double,
    val handleTouchRadiusPx: Double,
    val hitSlopPx: Double,
    val rotateHandleOffsetPx: Double,
    val rotationSnapDegrees: Double = 0.0,
) {
    public companion object {
        /** docs 24의 기본 크기(끌기 8dp, 손잡이 48dp 영역, 회전 손잡이 20dp)를 [density]로 바꾼다. */
        public fun forDensity(density: Float): GestureConfig = GestureConfig(
            touchSlopPx = TOUCH_SLOP_DP * density,
            handleTouchRadiusPx = HANDLE_RADIUS_DP * density,
            hitSlopPx = HIT_SLOP_DP * density,
            rotateHandleOffsetPx = ROTATE_OFFSET_DP * density,
        )

        private const val TOUCH_SLOP_DP = 8.0
        private const val HANDLE_RADIUS_DP = 24.0
        private const val HIT_SLOP_DP = 8.0
        private const val ROTATE_OFFSET_DP = 20.0
    }
}

/** 지금 처리 중인 상호작용. */
public enum class InteractionMode {
    Idle,
    PressPending,
    SelectMarquee,
    DragNode,
    ResizeNode,
    RotateNode,
    TransformCanvas,
}
