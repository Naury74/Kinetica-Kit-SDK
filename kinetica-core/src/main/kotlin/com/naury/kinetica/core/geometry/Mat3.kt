package com.naury.kinetica.core.geometry

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 열 벡터 기준의 2차원 아핀 변환 행렬.
 *
 * ```
 * | a  c  tx |
 * | b  d  ty |
 * | 0  0  1  |
 * ```
 *
 * 마지막 행은 항상 `0 0 1`이므로 저장하지 않는다. `A × B`는 B를 먼저 적용한 뒤 A를 적용한다.
 *
 * @throws IllegalArgumentException 성분 중 하나라도 유한하지 않은 경우
 */
public data class Mat3(val a: Double, val b: Double, val c: Double, val d: Double, val tx: Double, val ty: Double) {
    init {
        require(a.isFinite() && b.isFinite() && c.isFinite() && d.isFinite() && tx.isFinite() && ty.isFinite()) {
            "Mat3 성분은 유한해야 합니다."
        }
    }

    public val determinant: Double get() = a * d - b * c

    /** `this × other`를 돌려준다. 점에는 [other]가 먼저 적용된다. */
    public operator fun times(other: Mat3): Mat3 = Mat3(
        a = a * other.a + c * other.b,
        b = b * other.a + d * other.b,
        c = a * other.c + c * other.d,
        d = b * other.c + d * other.d,
        tx = a * other.tx + c * other.ty + tx,
        ty = b * other.tx + d * other.ty + ty,
    )

    /** 점을 변환한다. 이동 성분도 적용한다. */
    public fun map(point: Vec2): Vec2 = Vec2(a * point.x + c * point.y + tx, b * point.x + d * point.y + ty)

    /** 방향 벡터를 변환한다. 이동 성분은 적용하지 않는다. */
    public fun mapVector(vector: Vec2): Vec2 = Vec2(a * vector.x + c * vector.y, b * vector.x + d * vector.y)

    /** 축 정렬 사각형의 네 꼭짓점을 변환한 뒤 감싸는 축 정렬 사각형을 돌려준다. */
    public fun mapBounds(rect: Rect): Rect = Rect.enclosing(rect.corners().map(::map))

    /**
     * 역행렬을 돌려준다. 행렬식의 절댓값이 [SINGULAR_DETERMINANT] 이하이면 역변환이 수치적으로 의미가 없으므로 `null`이다.
     */
    public fun inverseOrNull(): Mat3? {
        val det = determinant
        if (abs(det) <= SINGULAR_DETERMINANT) return null
        val inverseDet = 1.0 / det
        val ia = d * inverseDet
        val ib = -b * inverseDet
        val ic = -c * inverseDet
        val id = a * inverseDet
        return Mat3(
            a = ia,
            b = ib,
            c = ic,
            d = id,
            tx = -(ia * tx + ic * ty),
            ty = -(ib * tx + id * ty),
        )
    }

    public companion object {
        /** 이 값 이하의 행렬식은 특이 행렬로 본다. */
        public const val SINGULAR_DETERMINANT: Double = 1e-12

        public val Identity: Mat3 = Mat3(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)

        public fun translation(x: Double, y: Double): Mat3 = Mat3(1.0, 0.0, 0.0, 1.0, x, y)

        public fun scale(x: Double, y: Double): Mat3 = Mat3(x, 0.0, 0.0, y, 0.0, 0.0)

        /** 양의 각도는 y축이 아래로 향하는 화면 좌표에서 시계 방향 회전이다. */
        public fun rotationDegrees(degrees: Double): Mat3 {
            val radians = Math.toRadians(degrees)
            val cosine = cos(radians)
            val sine = sin(radians)
            return Mat3(cosine, sine, -sine, cosine, 0.0, 0.0)
        }
    }
}
