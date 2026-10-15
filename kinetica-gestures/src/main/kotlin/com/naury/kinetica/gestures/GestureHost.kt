package com.naury.kinetica.gestures

import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument

/**
 * 제스처가 읽고 바꾸는 편집 상태. 편집기가 세션과 화면 상태를 연결해 구현한다.
 * 제스처는 이 인터페이스로만 문서를 바꾸므로 저장소나 실행 취소 구현에 의존하지 않는다.
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
 * @property touchSlopPx 이 거리 이상 움직여야 드래그로 본다.
 * @property handleTouchRadiusPx 핸들을 잡을 수 있는 반경. 터치 영역이 48dp면 24dp다.
 * @property hitSlopPx 노드 터치 판정에 더하는 여유 거리
 * @property rotateHandleOffsetPx 회전 핸들이 위쪽 변에서 떨어진 거리
 * @property rotationSnapDegrees 0보다 크면 회전 각도를 이 값의 배수로 맞춘다.
 */
public data class GestureConfig(
    val touchSlopPx: Double,
    val handleTouchRadiusPx: Double,
    val hitSlopPx: Double,
    val rotateHandleOffsetPx: Double,
    val rotationSnapDegrees: Double = 0.0,
) {
    public companion object {
        /** docs 24의 기본값(드래그 8dp, 핸들 터치 영역 48dp, 회전 핸들 20dp)을 [density]로 픽셀로 바꾼다. */
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

/** 현재 처리 중인 상호작용. */
public enum class InteractionMode {
    Idle,
    PressPending,
    SelectMarquee,
    DragNode,
    ResizeNode,
    RotateNode,
    TransformCanvas,
}
