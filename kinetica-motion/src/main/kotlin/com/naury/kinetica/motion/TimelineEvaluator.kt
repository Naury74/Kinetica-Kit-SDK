package com.naury.kinetica.motion

import com.naury.kinetica.core.animation.AnimatableProperties
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.geometry.shortestDeltaDegrees
import com.naury.kinetica.core.scene.ArgbColor
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import kotlin.math.roundToInt

/**
 * timeline의 한 시각에서 노드 속성 값을 계산한다. 상태가 없어 어느 시각으로 건너뛰어도(seek) 결과가 같다.
 * 이전 프레임 값을 누적하지 않는다.
 *
 * 구간 규칙: 시각 t가 `t0 <= t < t1`이면 `t0` keyframe의 easing으로 `u = (t - t0) / (t1 - t0)`를 바꿔 보간한다.
 * keyframe 시각에서는 그 keyframe 값을 그대로 돌려준다. 첫 keyframe 전과 마지막 keyframe 뒤는 가장 가까운 값을 유지한다.
 *
 * 값 규칙: 회전은 가장 짧은 방향, 색은 sRGB 채널별 선형 보간(반올림, 0..255로 맞춤),
 * opacity는 0..1, 선 두께는 0 이상으로 맞춘다. 용수철처럼 넘치는 easing의 결과도 이 범위를 지킨다.
 */
public object TimelineEvaluator {
    /** [track]의 [timeMs] 시각 값. keyframe이 없는 track은 만들 수 없으므로(검증) 비어 있으면 예외다. */
    public fun valueAt(track: PropertyTrack, timeMs: Double): KeyValue {
        val keys = track.keyframes
        require(keys.isNotEmpty()) { "keyframe이 없는 track입니다." }
        val time = if (timeMs.isNaN()) 0.0 else timeMs
        if (time <= keys.first().timeMs) return keys.first().value
        if (time >= keys.last().timeMs) return keys.last().value
        val index = segmentStart(track, time)
        val from = keys[index]
        val to = keys[index + 1]
        val duration = (to.timeMs - from.timeMs).toDouble()
        val eased = Easing.evaluate(from.easing, (time - from.timeMs) / duration, duration)
        return interpolate(track.property, from.value, to.value, eased)
    }

    /**
     * [timeline]을 [timeMs]에 적용한 문서. 원래 문서는 바꾸지 않으며 결과는 저장·편집용이 아닌 그리기용이다.
     * 지워진 노드를 가리키는 track은 무시한다.
     */
    public fun evaluate(document: SceneDocument, timeline: Timeline, timeMs: Double): SceneDocument {
        if (timeline.tracks.isEmpty()) return document
        val nodes = LinkedHashMap(document.nodes)
        timeline.tracks.forEach { track ->
            val node = nodes[track.nodeId] ?: return@forEach
            nodes[track.nodeId] = node.with(track.property, valueAt(track, timeMs))
        }
        return document.copy(nodes = nodes)
    }

    /** `keys[i].timeMs <= time < keys[i + 1].timeMs`인 i. */
    private fun segmentStart(track: PropertyTrack, time: Double): Int {
        val keys = track.keyframes
        var low = 0
        var high = keys.size - 1
        while (high - low > 1) {
            val middle = (low + high) ushr 1
            if (keys[middle].timeMs <= time) low = middle else high = middle
        }
        return low
    }

    private fun interpolate(property: AnimatableProperty, from: KeyValue, to: KeyValue, fraction: Double): KeyValue = when {
        from is KeyValue.Color && to is KeyValue.Color -> KeyValue.Color(lerpColor(from.color, to.color, fraction))

        from is KeyValue.Scalar && to is KeyValue.Scalar -> {
            val delta = if (property == AnimatableProperty.RotationDegrees) {
                shortestDeltaDegrees(from.value, to.value)
            } else {
                to.value - from.value
            }
            KeyValue.Scalar(from.value + delta * fraction)
        }

        else -> from
    }

    private fun lerpColor(from: ArgbColor, to: ArgbColor, fraction: Double): ArgbColor {
        fun channel(a: Int, b: Int): Int = (a + (b - a) * fraction).roundToInt().coerceIn(0, MAX_CHANNEL)
        return ArgbColor.fromChannels(
            channel(from.alpha, to.alpha),
            channel(from.red, to.red),
            channel(from.green, to.green),
            channel(from.blue, to.blue),
        )
    }

    private fun SceneNode.with(property: AnimatableProperty, value: KeyValue): SceneNode = AnimatableProperties.write(this, property, value)

    private const val MAX_CHANNEL = 255
}
