package com.naury.kinetica.core.scene

import com.naury.kinetica.core.geometry.Vec2

/** 경로 명령을 선분 목록으로 근사한다. 곡선 하나를 [SEGMENTS_PER_CURVE]개 선분으로 나눈다. */
public object PathFlattening {
    public const val SEGMENTS_PER_CURVE: Int = 16

    /** MoveTo마다 새 선이 시작된다. Close를 만나면 그 선은 닫힌 선이다. */
    public data class Polyline(val points: List<Vec2>, val closed: Boolean)

    public fun polylines(commands: List<PathCommand>): List<Polyline> {
        val result = mutableListOf<Polyline>()
        var current = mutableListOf<Vec2>()
        fun flush(closed: Boolean) {
            if (current.isNotEmpty()) result += Polyline(current, closed)
            current = mutableListOf()
        }
        commands.forEach { command ->
            val start = current.lastOrNull() ?: Vec2.Zero
            when (command) {
                is PathCommand.MoveTo -> {
                    flush(closed = false)
                    current += command.point
                }

                is PathCommand.LineTo -> current += command.point

                is PathCommand.QuadTo -> current += sample { t -> quad(start, command.control, command.point, t) }

                is PathCommand.CubicTo -> current += sample { t -> cubic(start, command.control1, command.control2, command.point, t) }

                PathCommand.Close -> flush(closed = true)
            }
        }
        flush(closed = false)
        return result
    }

    private fun sample(point: (Double) -> Vec2): List<Vec2> = (1..SEGMENTS_PER_CURVE).map { point(it.toDouble() / SEGMENTS_PER_CURVE) }

    private fun quad(p0: Vec2, p1: Vec2, p2: Vec2, t: Double): Vec2 {
        val u = 1.0 - t
        return p0 * (u * u) + p1 * (2.0 * u * t) + p2 * (t * t)
    }

    private fun cubic(p0: Vec2, p1: Vec2, p2: Vec2, p3: Vec2, t: Double): Vec2 {
        val u = 1.0 - t
        return p0 * (u * u * u) + p1 * (CUBIC * u * u * t) + p2 * (CUBIC * u * t * t) + p3 * (t * t * t)
    }

    private const val CUBIC = 3.0
}
