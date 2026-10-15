package com.naury.kinetica.core.animation

/**
 * 회전 keyframe에서 다음 keyframe까지 돌아가는 방향. [AnimatableProperty.RotationDegrees] track에만 쓰고, 다른 속성에서는 무시한다.
 * 화면 좌표는 y가 아래로 향하므로 각도가 커지는 쪽이 시계 방향이다.
 *
 * @property wireName JSON에 저장할 때 쓰는 이름.
 */
public enum class RotationDirection(public val wireName: String) {
    /** 가장 짧은 방향. 회전량은 `(-180, 180]`도다. 기본값. */
    Shortest("shortest"),

    /** 시계 방향. 회전량은 `(0, 360]`도이며, 두 값이 같으면 돌지 않고, 360도의 배수만큼 다르면 한 바퀴 돈다. */
    Clockwise("clockwise"),

    /** 반시계 방향. 회전량은 `[-360, 0)`도이며, 두 값이 같으면 돌지 않고, 360도의 배수만큼 다르면 한 바퀴 돈다. */
    CounterClockwise("counterClockwise"),

    /** 두 값의 차이 그대로. 0에서 720이면 시계 방향으로 두 바퀴 돈다. */
    Direct("direct"),
    ;

    /** [from]에서 [to]로 갈 때의 회전량(도). 시작 값에 이 값을 더하면 끝 각도와 360도의 배수만큼만 다르다. */
    public fun deltaDegrees(from: Double, to: Double): Double {
        val raw = to - from
        val wrapped = raw.mod(FULL_TURN)
        return when (this) {
            Shortest -> if (wrapped > HALF_TURN) wrapped - FULL_TURN else wrapped

            Clockwise -> if (wrapped == 0.0 && raw != 0.0) FULL_TURN else wrapped

            CounterClockwise -> when {
                wrapped != 0.0 -> wrapped - FULL_TURN
                raw != 0.0 -> -FULL_TURN
                else -> 0.0
            }

            Direct -> raw
        }
    }

    public companion object {
        private const val FULL_TURN = 360.0
        private const val HALF_TURN = 180.0

        /** [name]에 맞는 방향. 모르는 이름이면 `null`이다. */
        public fun fromWireName(name: String): RotationDirection? = entries.firstOrNull { it.wireName == name }
    }
}
