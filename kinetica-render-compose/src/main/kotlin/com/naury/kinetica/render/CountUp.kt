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
 * 숫자가 [target]으로 바뀔 때 지금 보이는 값에서 [target]까지 올라가거나 내려가며 바뀌는 값(대시보드 매출·카운터).
 * 처음 값은 움직이지 않고 그대로 보인다. 큰 수(Long 범위)도 자릿수를 잃지 않도록 Double로 계산한다.
 *
 * ```kotlin
 * val sales by animateNumberAsState(data.sales)
 * Text("%,d원".format(sales.roundToLong()))
 * ```
 *
 * @param durationMs 바뀌는 데 걸리는 시간(ms, 0 이상). 0이면 바로 바뀐다.
 */
@Composable
public fun animateNumberAsState(target: Double, durationMs: Int = DEFAULT_COUNT_MS): State<Double> {
    var from by remember { mutableDoubleStateOf(target) }
    var to by remember { mutableDoubleStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        // 움직이는 도중 값이 또 바뀌면 지금 보이는 값에서 이어 간다.
        from += (to - from) * progress.value
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs.coerceAtLeast(0), easing = FastOutSlowInEasing))
    }
    return remember { derivedStateOf { from + (to - from) * progress.value } }
}

private const val DEFAULT_COUNT_MS = 800
