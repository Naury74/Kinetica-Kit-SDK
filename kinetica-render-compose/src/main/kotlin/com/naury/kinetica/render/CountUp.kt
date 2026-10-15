package com.naury.kinetica.render

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * [target]이 바뀌면 지금 보이는 값에서 [target]까지 숫자가 올라가거나 내려가는 애니메이션 값. 대시보드 매출이나 카운터에 쓴다.
 * 첫 값은 애니메이션 없이 그대로 보여 준다. Double로 계산하므로 2^53(약 9천조)까지의 정수는 정확하다.
 * NaN이나 무한대처럼 유한하지 않은 값은 무시하고 지금 값을 유지한다.
 *
 * ```kotlin
 * val sales by animateNumberAsState(data.sales)
 * Text("%,d원".format(sales.roundToLong()))
 * ```
 *
 * @param durationMs 애니메이션 시간(ms, 0 이상). 0이면 바로 바뀐다.
 */
@Composable
public fun animateNumberAsState(target: Double, durationMs: Int = DEFAULT_COUNT_MS): State<Double> {
    val start = if (target.isFinite()) target else 0.0
    var from by remember { mutableDoubleStateOf(start) }
    var to by remember { mutableDoubleStateOf(start) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to || !target.isFinite()) return@LaunchedEffect
        // 애니메이션 도중에 값이 또 바뀌면 지금 보이는 값에서 이어서 움직인다.
        from += (to - from) * progress.value
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs.coerceAtLeast(0), easing = FastOutSlowInEasing))
    }
    return remember { derivedStateOf { from + (to - from) * progress.value } }
}

private const val DEFAULT_COUNT_MS = 800
