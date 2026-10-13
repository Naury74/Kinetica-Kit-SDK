package com.naury.kinetica.core.geometry

import kotlin.math.abs

/**
 * 노드의 local 좌표를 부모 좌표로 옮기는 변환.
 *
 * ```
 * p_parent = T(translationX, translationY) × T(pivot) × R(rotationDegrees) × S(scaleX, scaleY) × T(-pivot) × p_local
 * ```
 *
 * pivot은 정규화 비율이 아니라 node-local 좌표다. 회전·확대는 pivot을 중심으로 일어난다.
 * 생성자는 값이 유한한지만 확인한다. 0에 가까운 배율과 반전(음수 배율) 허용 여부는 문서 검증이 판단한다.
 *
 * @property rotationDegrees 도 단위. 양수는 화면에서 시계 방향이다.
 * @throws IllegalArgumentException 값 중 하나라도 유한하지 않을 때
 */
public data class Transform2D(
    val translationX: Double = 0.0,
    val translationY: Double = 0.0,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val rotationDegrees: Double = 0.0,
    val pivotX: Double = 0.0,
    val pivotY: Double = 0.0,
) {
    init {
        require(
            translationX.isFinite() && translationY.isFinite() && scaleX.isFinite() && scaleY.isFinite() &&
                rotationDegrees.isFinite() && pivotX.isFinite() && pivotY.isFinite(),
        ) { "Transform2D 값은 유한해야 합니다." }
    }

    public val translation: Vec2 get() = Vec2(translationX, translationY)
    public val pivot: Vec2 get() = Vec2(pivotX, pivotY)

    /** 두 배율 중 하나라도 크기가 [MIN_SCALE_MAGNITUDE] 이하이면 역변환을 믿을 수 없어 편집할 수 없다. */
    public val isDegenerate: Boolean
        get() = abs(scaleX) <= MIN_SCALE_MAGNITUDE || abs(scaleY) <= MIN_SCALE_MAGNITUDE

    public val isFlipped: Boolean get() = scaleX < 0.0 || scaleY < 0.0

    public fun matrix(): Mat3 = Mat3.translation(translationX + pivotX, translationY + pivotY) *
        Mat3.rotationDegrees(rotationDegrees) *
        Mat3.scale(scaleX, scaleY) *
        Mat3.translation(-pivotX, -pivotY)

    public companion object {
        public const val MIN_SCALE_MAGNITUDE: Double = 1e-6

        public val Identity: Transform2D = Transform2D()
    }
}
