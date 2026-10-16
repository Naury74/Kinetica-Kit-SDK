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
import kotlin.math.floor

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

    /** 시계를 돌린다. [frameRate]가 0보다 크면 값을 초당 그 횟수만큼만 바꿔, 읽는 쪽이 그 횟수만큼만 다시 그리게 한다. */
    internal suspend fun run(frameRate: Int = 0) {
        val base = time.floatValue
        var start = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (start < 0) start = now
                val elapsed = (now - start) / NANOS_PER_SECOND
                val stepped = if (frameRate > 0) floor(elapsed * frameRate) / frameRate else elapsed
                val next = base + stepped
                if (next != time.floatValue) time.floatValue = next
            }
        }
    }
}

/**
 * [running]이고 화면에 보이는 동안만 도는 [LoopClock]을 만든다. 보이는지는 [loopVisibility]를 붙여야 안다.
 * [frameRate]가 0보다 크면 값을 초당 그 횟수만큼만 바꾼다. 글래스가 비추는 배경은 바뀔 때마다 블러를 모두 다시 하므로 낮춰 쓴다.
 */
@Composable
internal fun rememberLoopClock(running: Boolean, frameRate: Int = 0): LoopClock {
    val clock = remember { LoopClock() }
    val active = running && clock.onScreen
    LaunchedEffect(clock, active, frameRate) { if (active) clock.run(frameRate) }
    return clock
}

/**
 * 글래스 뒤에 까는 움직이는 배경의 초당 변경 횟수. 배경이 바뀌면 그것을 비추는 블러(카드, 바, 시트)를 모두 다시 해야 하는데,
 * 천천히 떠다니는 배경은 초당 30번이면 120Hz 화면에서도 끊겨 보이지 않는다. 그 사이 프레임은 그리지 않는다.
 */
internal const val BACKGROUND_FRAME_RATE = 30

/** 이 요소가 창 안에 보이는지 [clock]에 알린다. */
internal fun Modifier.loopVisibility(clock: LoopClock): Modifier =
    onVisibilityChanged(minFractionVisible = 0f, callback = clock.visibilityCallback)

private const val NANOS_PER_SECOND = 1_000_000_000f
