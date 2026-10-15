package com.naury.kinetica.editor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.StepJump
import com.naury.kinetica.motion.Easing
import java.util.Locale

/**
 * 고른 키 구간의 easing을 고르고 값을 편집한다.
 *
 * 선형과 베지어는 그래프의 두 조절점을 끌어 베지어로 만든다. x는 0..1, y는 [MIN_Y]..[MAX_Y]로 자른다.
 * 스프링은 감쇠비와 강성, 계단은 칸 수와 점프 방식을 [EasingParams]에서 바꾼다. 어느 쪽이든 손을 뗄 때 [onChange]를 한 번 부른다.
 * 그래프는 고른 easing의 실제 곡선(스프링은 넘침 포함, 계단은 계단 모양)을 그리고,
 * 그래프 위의 점과 아래 막대의 점은 [segmentMs] 동안 그 곡선을 따라 움직이며 결과를 미리 보여 준다.
 */
@Composable
internal fun EasingEditor(
    easing: EasingSpec,
    segmentMs: Double,
    theme: StudioTheme,
    onChange: (EasingSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(easing) { mutableStateOf(easing) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EASING_PRESETS.forEach { (preset, label) ->
                ToggleChip(label, sameKind(draft, preset), theme) {
                    draft = preset
                    onChange(preset)
                }
            }
        }
        Text(describe(draft), color = theme.onPanel)
        EasingGraph(draft, segmentMs, theme, onDraft = { draft = it }, onCommit = { onChange(draft) })
        EasingParams(draft, theme, onDraft = { draft = it }, onCommit = { if (draft != easing) onChange(draft) })
    }
}

@Composable
private fun EasingGraph(easing: EasingSpec, segmentMs: Double, theme: StudioTheme, onDraft: (EasingSpec) -> Unit, onCommit: () -> Unit) {
    val current by rememberUpdatedState(easing)
    val transition = rememberInfiniteTransition(label = "easing-preview")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(previewMs(segmentMs), easing = LinearEasing), RepeatMode.Restart),
        label = "easing-progress",
    )
    val graphName = stringResource(R.string.kinetica_easing_curve)
    val previewName = stringResource(R.string.kinetica_easing_preview)
    val described = describe(easing)
    var handle by remember { mutableIntStateOf(0) }
    Canvas(
        Modifier
            .size(GRAPH_SIZE)
            .testTag(EASING_GRAPH_TAG)
            .semantics {
                contentDescription = graphName
                stateDescription = described
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start ->
                        handle = if (hasHandles(current)) {
                            GraphMapping(size.width.toFloat(), size.height.toFloat()).nearestHandle(current, start)
                        } else {
                            0
                        }
                    },
                    onDragEnd = { if (handle != 0) onCommit() },
                    onDragCancel = { if (handle != 0) onCommit() },
                ) { change, _ ->
                    if (handle == 0) return@detectDragGestures
                    change.consume()
                    val point = GraphMapping(size.width.toFloat(), size.height.toFloat()).toCurve(change.position)
                    onDraft(moveHandle(current, handle, point))
                }
            },
    ) {
        val mapping = GraphMapping(size.width, size.height)
        drawGrid(mapping, theme)
        drawCurve(mapping, easing, segmentMs, theme)
        if (hasHandles(easing)) drawHandles(mapping, asBezier(easing), theme)
        val eased = Easing.evaluate(easing, progress.toDouble(), segmentMs).toFloat()
        drawCircle(theme.selection, PREVIEW_DOT.toPx(), mapping.toPx(progress, eased))
    }
    PreviewBar(progress = { Easing.evaluate(easing, progress.toDouble(), segmentMs).toFloat() }, theme, previewName)
}

@Composable
private fun PreviewBar(progress: () -> Float, theme: StudioTheme, name: String) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(PREVIEW_BAR_HEIGHT)
            .semantics { contentDescription = name },
    ) {
        val pad = PREVIEW_DOT.toPx()
        val y = size.height / 2
        drawLine(theme.onPanel.copy(alpha = GRID_ALPHA), Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 2.dp.toPx())
        val x = pad + (size.width - pad * 2) * progress().coerceIn(MIN_Y.toFloat(), MAX_Y.toFloat())
        drawCircle(theme.accent, pad, Offset(x.coerceIn(0f, size.width), y))
    }
}

/** 그래프 좌표(x 0..1, y [MIN_Y]..[MAX_Y])와 그리기 좌표 사이의 변환. */
private class GraphMapping(val width: Float, val height: Float) {
    private val pad = minOf(width, height) * PAD_RATIO

    fun toPx(x: Float, y: Float): Offset {
        val fy = (y - MIN_Y.toFloat()) / (MAX_Y - MIN_Y).toFloat()
        return Offset(pad + x * (width - pad * 2), height - pad - fy * (height - pad * 2))
    }

    fun toCurve(position: Offset): Offset {
        val x = (position.x - pad) / (width - pad * 2)
        val fy = (height - pad - position.y) / (height - pad * 2)
        return Offset(x.coerceIn(0f, 1f), (MIN_Y + fy * (MAX_Y - MIN_Y)).toFloat().coerceIn(MIN_Y.toFloat(), MAX_Y.toFloat()))
    }

    /** [position]에 가까운 조절점. 1이면 첫째, 2면 둘째. */
    fun nearestHandle(easing: EasingSpec, position: Offset): Int {
        val bezier = asBezier(easing)
        val first = (toPx(bezier.x1.toFloat(), bezier.y1.toFloat()) - position).getDistance()
        val second = (toPx(bezier.x2.toFloat(), bezier.y2.toFloat()) - position).getDistance()
        return if (first <= second) 1 else 2
    }
}

private fun DrawScope.drawGrid(mapping: GraphMapping, theme: StudioTheme) {
    val color = theme.onPanel.copy(alpha = GRID_ALPHA)
    listOf(0f, 1f).forEach { y -> drawLine(color, mapping.toPx(0f, y), mapping.toPx(1f, y)) }
    listOf(0f, 1f).forEach { x -> drawLine(color, mapping.toPx(x, MIN_Y.toFloat()), mapping.toPx(x, MAX_Y.toFloat())) }
}

private fun DrawScope.drawCurve(mapping: GraphMapping, easing: EasingSpec, segmentMs: Double, theme: StudioTheme) {
    val path = Path()
    val points = if (easing is EasingSpec.Steps) stepPoints(easing) else curvePoints(easing, segmentMs)
    points.forEachIndexed { i, (x, y) ->
        val point = mapping.toPx(x, y.coerceIn(MIN_Y.toFloat(), MAX_Y.toFloat()))
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(path, theme.accent, style = Stroke(2.dp.toPx()))
}

// 스프링은 강성이 크면 한 구간에 여러 번 흔들리므로 더 촘촘히 잰다.
private fun curvePoints(easing: EasingSpec, segmentMs: Double): List<Pair<Float, Float>> {
    val samples = if (easing is EasingSpec.Spring) SPRING_SAMPLES else CURVE_SAMPLES
    return (0..samples).map { i ->
        val u = i.toFloat() / samples
        u to Easing.evaluate(easing, u.toDouble(), segmentMs).toFloat()
    }
}

/**
 * 계단의 꺾인 점. 각 칸의 높이는 칸 가운데에서 잰 값이라 점프 방식이 그대로 반영되고, 양 끝은 0과 1에 잇는다.
 * 칸 수가 잘못된 계단은 선형으로 계산되므로 칸을 하나로 본다.
 */
private fun stepPoints(easing: EasingSpec.Steps): List<Pair<Float, Float>> {
    val n = easing.count.coerceAtLeast(1)
    val levels = (0 until n).map { i -> Easing.evaluate(easing, (i + HALF) / n, 0.0).toFloat() }
    val inner = levels.flatMapIndexed { i, level -> listOf(i.toFloat() / n to level, (i + 1).toFloat() / n to level) }
    return listOf(0f to 0f) + inner + (1f to 1f)
}

private fun DrawScope.drawHandles(mapping: GraphMapping, bezier: EasingSpec.CubicBezier, theme: StudioTheme) {
    val first = mapping.toPx(bezier.x1.toFloat(), bezier.y1.toFloat())
    val second = mapping.toPx(bezier.x2.toFloat(), bezier.y2.toFloat())
    drawLine(theme.onPanel, mapping.toPx(0f, 0f), first)
    drawLine(theme.onPanel, mapping.toPx(1f, 1f), second)
    drawCircle(theme.onPanel, HANDLE_RADIUS.toPx(), first)
    drawCircle(theme.onPanel, HANDLE_RADIUS.toPx(), second)
}

/** 조절점을 끌어 편집하는 easing인지. 스프링과 계단은 슬라이더로 바꾼다. */
private fun hasHandles(easing: EasingSpec): Boolean = easing is EasingSpec.CubicBezier || easing == EasingSpec.Linear

/** 프리셋 칩을 켤지 정한다. 스프링과 계단은 값이 달라도 같은 종류면 켠다. */
private fun sameKind(easing: EasingSpec, preset: EasingSpec): Boolean = when (preset) {
    is EasingSpec.Spring -> easing is EasingSpec.Spring
    is EasingSpec.Steps -> easing is EasingSpec.Steps
    else -> easing == preset
}

/** 조절점을 보여 줄 베지어. 선형은 직선 위의 점, 스프링과 계단은 ease-in-out에서 시작한다. */
internal fun asBezier(easing: EasingSpec): EasingSpec.CubicBezier = when (easing) {
    is EasingSpec.CubicBezier -> easing
    EasingSpec.Linear -> EasingSpec.CubicBezier(THIRD, THIRD, 1 - THIRD, 1 - THIRD)
    else -> EasingSpec.EaseInOut
}

/** [handle]번 조절점을 [point]로 옮긴 베지어. x는 0..1, y는 [MIN_Y]..[MAX_Y]로 자른다. */
internal fun moveHandle(easing: EasingSpec, handle: Int, point: Offset): EasingSpec.CubicBezier {
    val bezier = asBezier(easing)
    val x = point.x.toDouble().coerceIn(0.0, 1.0).round()
    val y = point.y.toDouble().coerceIn(MIN_Y, MAX_Y).round()
    return if (handle == 1) bezier.copy(x1 = x, y1 = y) else bezier.copy(x2 = x, y2 = y)
}

private fun Double.round(): Double = Math.round(this * ROUNDING) / ROUNDING

internal fun describe(easing: EasingSpec): String = when (easing) {
    EasingSpec.Linear -> "linear"

    is EasingSpec.CubicBezier -> String.format(
        Locale.ROOT,
        "cubic-bezier(%.2f, %.2f, %.2f, %.2f)",
        easing.x1,
        easing.y1,
        easing.x2,
        easing.y2,
    )

    is EasingSpec.Steps -> if (easing.jump ==
        StepJump.End
    ) {
        "steps(${easing.count})"
    } else {
        "steps(${easing.count}, jump-${easing.jump.wireName})"
    }

    is EasingSpec.Spring -> String.format(Locale.ROOT, "spring(%.2f, %.0f)", easing.dampingRatio, easing.stiffness)
}

private fun previewMs(segmentMs: Double): Int = segmentMs.toInt().coerceIn(MIN_PREVIEW_MS, MAX_PREVIEW_MS)

/** 테스트에서 그래프를 찾을 때 쓰는 test tag. */
internal const val EASING_GRAPH_TAG = "kinetica-easing-graph"

internal val EASING_PRESETS = listOf(
    EasingSpec.Linear to R.string.kinetica_easing_linear,
    EasingSpec.EaseIn to R.string.kinetica_easing_in,
    EasingSpec.EaseOut to R.string.kinetica_easing_out,
    EasingSpec.EaseInOut to R.string.kinetica_easing_in_out,
    EasingSpec.Spring(dampingRatio = 0.5, stiffness = 300.0) to R.string.kinetica_easing_spring,
    EasingSpec.Steps(4) to R.string.kinetica_easing_steps,
)

internal const val MIN_Y = -0.5
internal const val MAX_Y = 1.5
private val GRAPH_SIZE = 180.dp
private val PREVIEW_BAR_HEIGHT = 20.dp
private val PREVIEW_DOT = 5.dp
private val HANDLE_RADIUS = 7.dp
private const val PAD_RATIO = 0.08f
private const val GRID_ALPHA = 0.3f
private const val CURVE_SAMPLES = 64
private const val SPRING_SAMPLES = 160
private const val THIRD = 1.0 / 3.0
private const val HALF = 0.5
private const val ROUNDING = 100.0
private const val MIN_PREVIEW_MS = 400
private const val MAX_PREVIEW_MS = 2_000
