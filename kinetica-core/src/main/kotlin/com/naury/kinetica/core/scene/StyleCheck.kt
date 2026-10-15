package com.naury.kinetica.core.scene

import kotlin.math.abs

private const val MAX_SHADOWS = 8
private const val MAX_DASH = 8
private const val MIN_STOPS = 2
private const val MAX_STOPS = 16

/** 스타일 값의 범위를 확인한다. 개수 한도는 외부 문서가 그리기 비용을 지나치게 키우지 못하게 막는다. */
internal fun checkStyle(node: SceneNode, limits: SceneLimits, sink: ViolationSink) {
    val style = node.style
    val max = limits.maxCoordinateMagnitude
    if (!style.opacity.isFinite() || style.opacity !in 0.0..1.0) sink.add(ViolationCode.InvalidStyle, "opacity는 0..1 범위여야 합니다.", node.id)
    if (!style.strokeWidth.isFinite() || style.strokeWidth !in 0.0..max) sink.add(ViolationCode.InvalidStyle, "선 두께가 범위를 벗어났습니다.", node.id)
    if (!style.blur.isFinite() || style.blur !in 0.0..max) sink.add(ViolationCode.InvalidStyle, "블러 반지름이 범위를 벗어났습니다.", node.id)
    checkShadows(node, max, sink)
    checkDash(node, max, sink)
    style.gradient?.let { checkGradient(it, node.id, sink) }
}

private fun checkShadows(node: SceneNode, max: Double, sink: ViolationSink) {
    fun bounded(value: Double) = value.isFinite() && abs(value) <= max
    val shadows = node.style.shadows
    val valid = shadows.size <= MAX_SHADOWS &&
        shadows.all { listOf(it.offsetX, it.offsetY, it.spread).all(::bounded) && it.blur.isFinite() && it.blur in 0.0..max }
    if (!valid) sink.add(ViolationCode.InvalidStyle, "그림자 값이 범위를 벗어났거나 ${MAX_SHADOWS}개를 넘습니다.", node.id)
}

private fun checkDash(node: SceneNode, max: Double, sink: ViolationSink) {
    val dash = node.style.strokeDash
    val valid = dash.size <= MAX_DASH && dash.all { it.isFinite() && it > 0.0 && it <= max }
    if (!valid) sink.add(ViolationCode.InvalidStyle, "점선 길이는 양수이고 ${MAX_DASH}개 이하여야 합니다.", node.id)
}

private fun checkGradient(gradient: Gradient, id: NodeId, sink: ViolationSink) {
    val positions = gradient.stops.map { it.position }
    val valid = gradient.stops.size in MIN_STOPS..MAX_STOPS && gradient.angleDegrees.isFinite() &&
        positions.all { it.isFinite() && it in 0.0..1.0 } && positions.zipWithNext().all { (a, b) -> a <= b }
    if (!valid) sink.add(ViolationCode.InvalidStyle, "그라디언트는 0..1 순서의 색 2..${MAX_STOPS}개여야 합니다.", id)
}
