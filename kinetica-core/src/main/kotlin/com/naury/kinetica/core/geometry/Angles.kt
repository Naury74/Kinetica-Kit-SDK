package com.naury.kinetica.core.geometry

private const val FULL_TURN = 360.0
private const val HALF_TURN = 180.0

/** 각도를 `(-180, 180]` 범위로 정규화한다. */
public fun normalizeDegrees(degrees: Double): Double {
    val wrapped = degrees % FULL_TURN
    return when {
        wrapped > HALF_TURN -> wrapped - FULL_TURN
        wrapped <= -HALF_TURN -> wrapped + FULL_TURN
        else -> wrapped
    }
}

/** [from]에서 [to]로 가는 가장 짧은 회전량. 결과는 `(-180, 180]`이다. */
public fun shortestDeltaDegrees(from: Double, to: Double): Double = normalizeDegrees(to - from)
