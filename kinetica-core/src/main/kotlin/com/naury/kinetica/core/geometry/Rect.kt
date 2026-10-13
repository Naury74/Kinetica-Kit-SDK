package com.naury.kinetica.core.geometry

/**
 * 축 정렬 사각형. `left <= right`, `top <= bottom`이고 y축은 아래로 향한다.
 *
 * @throws IllegalArgumentException 값이 유한하지 않거나 너비·높이가 음수일 때
 */
public data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) { "Rect 값은 유한해야 합니다." }
        require(left <= right && top <= bottom) { "Rect의 너비와 높이는 음수일 수 없습니다." }
    }

    public val width: Double get() = right - left
    public val height: Double get() = bottom - top
    public val center: Vec2 get() = Vec2((left + right) / 2.0, (top + bottom) / 2.0)

    /** 왼쪽 위부터 시계 방향(왼쪽 위, 오른쪽 위, 오른쪽 아래, 왼쪽 아래) 꼭짓점. */
    public fun corners(): List<Vec2> = listOf(Vec2(left, top), Vec2(right, top), Vec2(right, bottom), Vec2(left, bottom))

    /** 경계 위의 점도 포함한다. */
    public fun contains(point: Vec2): Boolean = point.x in left..right && point.y in top..bottom

    /** 경계가 맞닿기만 해도 겹친 것으로 본다. */
    public fun intersects(other: Rect): Boolean = left <= other.right && other.left <= right && top <= other.bottom && other.top <= bottom

    public fun union(other: Rect): Rect =
        Rect(minOf(left, other.left), minOf(top, other.top), maxOf(right, other.right), maxOf(bottom, other.bottom))

    /** 네 방향으로 [amount]만큼 넓힌다. 음수면 좁히되 중심을 넘지 않는다. */
    public fun inflate(amount: Double): Rect {
        val dx = maxOf(amount, -width / 2.0)
        val dy = maxOf(amount, -height / 2.0)
        return Rect(left - dx, top - dy, right + dx, bottom + dy)
    }

    public companion object {
        public fun fromSize(size: Size): Rect = Rect(0.0, 0.0, size.width, size.height)

        /** [points]를 모두 감싸는 가장 작은 사각형. */
        public fun enclosing(points: List<Vec2>): Rect {
            require(points.isNotEmpty()) { "점이 하나 이상 있어야 합니다." }
            return Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
        }
    }
}

/**
 * 너비와 높이. 둘 다 유한하고 0 이상이다.
 *
 * @throws IllegalArgumentException 값이 유한하지 않거나 음수일 때
 */
public data class Size(val width: Double, val height: Double) {
    init {
        require(width.isFinite() && height.isFinite()) { "Size 값은 유한해야 합니다." }
        require(width >= 0.0 && height >= 0.0) { "Size 값은 음수일 수 없습니다." }
    }
}
