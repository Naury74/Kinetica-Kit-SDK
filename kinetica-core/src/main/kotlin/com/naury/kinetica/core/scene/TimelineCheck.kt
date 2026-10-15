package com.naury.kinetica.core.scene

import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.KeyValueType
import com.naury.kinetica.core.animation.PropertyTrack
import com.naury.kinetica.core.animation.StepJump
import com.naury.kinetica.core.animation.Timeline
import kotlin.math.abs

private const val MAX_FPS_HINT = 240
private const val MAX_STEPS = 10_000
private const val MAX_BEZIER_Y = 10.0
private const val MAX_DAMPING_RATIO = 100.0
private const val MAX_STIFFNESS = 1_000_000.0

internal fun checkTimelines(document: SceneDocument, limits: SceneLimits, sink: ViolationSink) {
    val timelines = document.timelines
    if (timelines.size > limits.maxTimelines) {
        sink.add(ViolationCode.InvalidTimeline, "timeline 수가 최대 ${limits.maxTimelines}개를 넘습니다.")
        return
    }
    val totalKeyframes = timelines.sumOf { timeline -> timeline.tracks.sumOf { it.keyframes.size.toLong() } }
    if (totalKeyframes > limits.maxTotalKeyframes) {
        sink.add(ViolationCode.TooManyKeyframes, "keyframe 수가 최대 ${limits.maxTotalKeyframes}개를 넘습니다.")
        return
    }
    if (timelines.map { it.id }.toSet().size != timelines.size) {
        sink.add(ViolationCode.InvalidTimeline, "timeline id가 중복됩니다.")
    }
    timelines.forEach { checkTimeline(it, document, limits, sink) }
}

private fun checkTimeline(timeline: Timeline, document: SceneDocument, limits: SceneLimits, sink: ViolationSink) {
    if (!NodeId.isValid(timeline.id)) sink.add(ViolationCode.InvalidTimeline, "timeline id 형식이 올바르지 않습니다.")
    if (timeline.durationMs !in 0..limits.maxTimelineDurationMs) {
        sink.add(ViolationCode.InvalidTimeline, "timeline 길이는 0..${limits.maxTimelineDurationMs}ms 범위여야 합니다.")
        return
    }
    if (timeline.fpsHint !in 1..MAX_FPS_HINT) sink.add(ViolationCode.InvalidTimeline, "fpsHint는 1..$MAX_FPS_HINT 범위여야 합니다.")
    val badMarker = timeline.markers.any {
        it.timeMs !in 0..timeline.durationMs || it.name.length > limits.maxNameLength || !isWellFormedUtf16(it.name)
    }
    if (badMarker) sink.add(ViolationCode.InvalidTimeline, "marker 시간이나 이름이 범위를 벗어났습니다.")
    val seen = HashSet<Pair<NodeId, AnimatableProperty>>()
    timeline.tracks.forEach { track ->
        if (!seen.add(track.nodeId to track.property)) {
            sink.add(ViolationCode.InvalidTrack, "같은 노드와 속성에 대한 track이 둘 이상입니다.", track.nodeId)
        }
        checkTrack(track, timeline.durationMs, document, limits, sink)
    }
}

private fun checkTrack(track: PropertyTrack, durationMs: Long, document: SceneDocument, limits: SceneLimits, sink: ViolationSink) {
    val nodeId = track.nodeId
    if (!document.nodes.containsKey(nodeId)) sink.add(ViolationCode.MissingNode, "track이 존재하지 않는 노드를 가리킵니다.", nodeId)
    val keyframes = track.keyframes
    when {
        keyframes.isEmpty() -> sink.add(ViolationCode.InvalidTrack, "track에 keyframe이 없습니다.", nodeId)

        keyframes.zipWithNext().any { (a, b) -> a.timeMs >= b.timeMs } ->
            sink.add(ViolationCode.InvalidKeyframe, "keyframe 시간은 겹치지 않고 증가해야 합니다.", nodeId)

        keyframes.any { it.timeMs !in 0..durationMs } ->
            sink.add(ViolationCode.InvalidKeyframe, "keyframe 시간이 timeline 범위를 벗어났습니다.", nodeId)
    }
    keyframes.forEach { keyframe ->
        if (!isValueAllowed(track.property, keyframe.value, limits)) {
            sink.add(ViolationCode.InvalidKeyframe, "${track.property.wireName} 값의 타입이나 범위가 올바르지 않습니다.", nodeId)
        }
        if (!isEasingValid(keyframe.easing)) sink.add(ViolationCode.InvalidEasing, "easing 파라미터가 범위를 벗어났습니다.", nodeId)
    }
}

private fun isValueAllowed(property: AnimatableProperty, value: KeyValue, limits: SceneLimits): Boolean {
    if (property.valueType == KeyValueType.Color) return value is KeyValue.Color
    val scalar = (value as? KeyValue.Scalar)?.value ?: return false
    if (!scalar.isFinite() || abs(scalar) > limits.maxCoordinateMagnitude) return false
    return when (property) {
        AnimatableProperty.Opacity -> scalar in 0.0..1.0

        AnimatableProperty.StrokeWidth, AnimatableProperty.CornerRadius, AnimatableProperty.Blur, AnimatableProperty.ShadowBlur,
        AnimatableProperty.CornerTopLeft, AnimatableProperty.CornerTopRight, AnimatableProperty.CornerBottomRight,
        AnimatableProperty.CornerBottomLeft,
        -> scalar >= 0.0

        AnimatableProperty.Width, AnimatableProperty.Height -> scalar > 0.0

        AnimatableProperty.ScaleX, AnimatableProperty.ScaleY -> limits.allowFlip || scalar >= 0.0

        else -> true
    }
}

/** 시간 축이 거꾸로 가지 않도록 베지어 곡선의 x 제어점은 0..1이어야 한다. */
internal fun isEasingValid(easing: EasingSpec): Boolean = when (easing) {
    EasingSpec.Linear -> true
    is EasingSpec.CubicBezier -> isBezierValid(easing)
    is EasingSpec.Steps -> easing.count in minSteps(easing.jump)..MAX_STEPS
    is EasingSpec.Spring -> isSpringValid(easing)
}

/** jump-none은 첫 칸과 마지막 칸이 0과 1이므로 칸이 하나면 정의되지 않는다. */
private fun minSteps(jump: StepJump): Int = if (jump == StepJump.None) 2 else 1

private fun isBezierValid(bezier: EasingSpec.CubicBezier): Boolean {
    val xInRange = bezier.x1 in 0.0..1.0 && bezier.x2 in 0.0..1.0
    val yInRange = listOf(bezier.y1, bezier.y2).all { it.isFinite() && abs(it) <= MAX_BEZIER_Y }
    return xInRange && yInRange
}

private fun isSpringValid(spring: EasingSpec.Spring): Boolean {
    val dampingOk = spring.dampingRatio.isFinite() && spring.dampingRatio > 0.0 && spring.dampingRatio <= MAX_DAMPING_RATIO
    val stiffnessOk = spring.stiffness.isFinite() && spring.stiffness > 0.0 && spring.stiffness <= MAX_STIFFNESS
    return dampingOk && stiffnessOk
}
