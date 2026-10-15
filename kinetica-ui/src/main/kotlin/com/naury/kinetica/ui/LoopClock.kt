package com.naury.kinetica.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onVisibilityChanged

/**
 * 반복 애니메이션의 시계. 처음 화면에 보일 때 돌기 시작하고, 요소가 창 밖으로 나가면 멈췄다가 다시 들어오면 멈춘 시각부터 이어 간다.
 * `Column(verticalScroll)`처럼 화면 밖 요소도 컴포지션에 남는 곳에서 프레임을 계속 요청하지 않게 한다.
 * 값은 그리기 단계에서 읽어야 프레임마다 재구성하지 않는다.
 */
@Stable
internal class LoopClock {
    private val time = mutableFloatStateOf(0f)

    /** 시계가 돈 시간(초). */
    val seconds: Float get() = time.floatValue

    /** 요소의 일부라도 창 안에 보이는지. 첫 배치에서 보이는 것을 확인한 뒤부터 시계가 돈다. */
    var onScreen by mutableStateOf(false)
        private set

    // 수정자를 다시 만들 때마다 같은 콜백을 넘겨야 노드가 매번 갱신되지 않는다.
    internal val visibilityCallback: (Boolean) -> Unit = { onScreen = it }

    internal suspend fun run() {
        val base = time.floatValue
        var start = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (start < 0) start = now
                time.floatValue = base + (now - start) / NANOS_PER_SECOND
            }
        }
    }
}

/** [running]이고 화면에 보이는 동안만 도는 [LoopClock]을 만든다. 보이는지는 [loopVisibility]를 붙여야 안다. */
@Composable
internal fun rememberLoopClock(running: Boolean): LoopClock {
    val clock = remember { LoopClock() }
    val active = running && clock.onScreen
    LaunchedEffect(clock, active) { if (active) clock.run() }
    return clock
}

/** 이 요소가 창 안에 보이는지 [clock]에 알린다. */
internal fun Modifier.loopVisibility(clock: LoopClock): Modifier =
    onVisibilityChanged(minFractionVisible = 0f, callback = clock.visibilityCallback)

private const val NANOS_PER_SECOND = 1_000_000_000f
