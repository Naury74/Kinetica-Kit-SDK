package com.naury.kinetica.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.naury.kinetica.core.scene.DataValue
import com.naury.kinetica.core.scene.SceneData
import java.math.BigDecimal
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * 숫자 값([DataValue.Number])이 바뀌면 지금 보이는 값에서 새 값까지 [durationMs] 동안 올라가거나 내려가는 데이터를 돌려준다.
 * 대시보드 매출이나 카운터처럼 텍스트에 바인딩된 숫자가 자연스럽게 바뀐다. 숫자가 아닌 값과 새로 생긴 키는 바로 바뀐다.
 *
 * 애니메이션 중의 값은 두 값 중 소수 자릿수가 많은 쪽(최대 6자리)에 맞춰 반올림하므로, 정수끼리 바뀔 때는 중간값도 정수다.
 * [durationMs]가 0 이하면 [data]를 그대로 돌려준다.
 */
@Composable
public fun animateSceneNumbers(data: SceneData, durationMs: Int = DEFAULT_NUMBER_ANIMATION_MS): SceneData {
    if (durationMs <= 0) return data
    var from by remember { mutableStateOf(data) }
    var to by remember { mutableStateOf(data) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(data) {
        if (data == to) {
            // 도중에 바뀐 값의 효과가 시작되기 전에 다시 이 값으로 돌아왔으면 멈춘 진행도에 남지 않게 마저 간다.
            if (progress.value < 1f) progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
            from = data
            return@LaunchedEffect
        }
        // 애니메이션 도중에 값이 또 바뀌면 지금 보이는 값에서 이어서 움직인다.
        from = blendNumbers(from, to, progress.value)
        to = data
        if (!changesNumbers(from, to)) {
            from = data
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
        from = data
    }
    return if (progress.value >= 1f) to else blendNumbers(from, to, progress.value)
}

/** 장면 숫자 애니메이션 기본 시간(ms). */
public const val DEFAULT_NUMBER_ANIMATION_MS: Int = 600

internal fun blendNumbers(from: SceneData, to: SceneData, fraction: Float): SceneData {
    if (fraction >= 1f) return to
    return SceneData(
        to.values.mapValues { (key, target) ->
            val start = (from[key] as? DataValue.Number)?.value?.takeIf { it.isFinite() }
            val end = (target as? DataValue.Number)?.value?.takeIf { it.isFinite() }
            if (start == null || end == null) {
                target
            } else {
                DataValue.Number(rounded(start + (end - start) * fraction, maxOf(decimals(start), decimals(end))))
            }
        },
    )
}

private fun changesNumbers(from: SceneData, to: SceneData): Boolean = to.values.any { (key, target) ->
    val start = from[key]
    target is DataValue.Number && start is DataValue.Number && start.value != target.value
}

private fun decimals(value: Double): Int =
    if (!value.isFinite()) 0 else BigDecimal(value.toString()).stripTrailingZeros().scale().coerceIn(0, MAX_DECIMALS)

private fun rounded(value: Double, decimals: Int): Double {
    val scale = 10.0.pow(decimals)
    return (value * scale).roundToLong() / scale
}

private const val MAX_DECIMALS = 6
