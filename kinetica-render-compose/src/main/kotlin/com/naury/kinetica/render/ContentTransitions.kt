package com.naury.kinetica.render

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

/**
 * 호스트 화면의 내용이 바뀔 때 쓰는 전환 애니메이션 모음. Compose `AnimatedContent`의 `transitionSpec`에 넣는다.
 *
 * ```kotlin
 * AnimatedContent(tab, transitionSpec = { ContentTransitions.sharedAxisX(forward = targetState > initialState) }) { page -> Page(page) }
 * ```
 *
 * 시간 값은 ms(0 이상)다. 시스템의 '애니메이션 삭제' 설정이 켜져 있으면 Compose가 애니메이션 없이 바로 바꾼다.
 */
public object ContentTransitions {
    /** 서로 관련 없는 화면 사이의 전환. 나가는 화면이 먼저 사라지고, 들어오는 화면이 살짝 커지며 나타난다(Material fade through). */
    public fun fadeThrough(durationMs: Int = DEFAULT_MS): ContentTransform {
        val out = durationMs * FADE_OUT_PART / PARTS
        val enter = fadeIn(tween(durationMs - out, delayMillis = out, easing = LinearOutSlowInEasing)) +
            scaleIn(tween(durationMs - out, delayMillis = out, easing = LinearOutSlowInEasing), initialScale = FADE_THROUGH_SCALE)
        return ContentTransform(enter, fadeOut(tween(out, easing = FastOutLinearInEasing)), sizeTransform = SizeTransform(clip = false))
    }

    /** 탭이나 단계처럼 앞뒤 순서가 있는 화면의 전환. 가로로 조금 밀리며 바뀌고, [forward]면 오른쪽에서 들어온다(Material shared axis X). */
    public fun sharedAxisX(forward: Boolean = true, durationMs: Int = DEFAULT_MS): ContentTransform {
        val sign = if (forward) 1 else -1
        return (slideInHorizontally(tween(durationMs)) { sign * it / SHIFT_DIVISOR } + fadeIn(tween(durationMs)))
            .togetherWith(slideOutHorizontally(tween(durationMs)) { -sign * it / SHIFT_DIVISOR } + fadeOut(tween(durationMs / 2)))
    }

    /** 위아래 순서가 있는 내용의 전환. 세로로 조금 밀리며 바뀌고, [forward]면 아래에서 올라온다. */
    public fun sharedAxisY(forward: Boolean = true, durationMs: Int = DEFAULT_MS): ContentTransform {
        val sign = if (forward) 1 else -1
        return (slideInVertically(tween(durationMs)) { sign * it / SHIFT_DIVISOR } + fadeIn(tween(durationMs)))
            .togetherWith(slideOutVertically(tween(durationMs)) { -sign * it / SHIFT_DIVISOR } + fadeOut(tween(durationMs / 2)))
    }

    /** 목록에서 상세로 가는 것처럼 부모와 자식 화면 사이의 전환. 들어오는 화면이 커지며 나타나고, 나가는 화면은 더 커지며 사라진다. [forward]가 아니면 반대로 움직인다. */
    public fun sharedAxisZ(forward: Boolean = true, durationMs: Int = DEFAULT_MS): ContentTransform {
        val enterFrom = if (forward) Z_SMALL else Z_LARGE
        val exitTo = if (forward) Z_LARGE else Z_SMALL
        return (scaleIn(tween(durationMs), initialScale = enterFrom) + fadeIn(tween(durationMs)))
            .togetherWith(scaleOut(tween(durationMs), targetScale = exitTo) + fadeOut(tween(durationMs / 2)))
    }

    /** 시트나 새 내용이 아래에서 올라오는 전환. 나가는 내용은 페이드 아웃된다. */
    public fun slideUp(durationMs: Int = DEFAULT_MS): ContentTransform =
        (slideInVertically(tween(durationMs, easing = LinearOutSlowInEasing)) { it } + fadeIn(tween(durationMs)))
            .togetherWith(fadeOut(tween(durationMs / 2)))

    /** 완료나 축하 화면에 어울리는, 튕기듯 커지며 나타나는 전환. 나가는 내용은 작아지며 사라진다. */
    public fun pop(): ContentTransform =
        (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), initialScale = POP_FROM) + fadeIn())
            .togetherWith(scaleOut(targetScale = POP_FROM) + fadeOut())

    private const val DEFAULT_MS = 300
    private const val FADE_OUT_PART = 35
    private const val PARTS = 100
    private const val FADE_THROUGH_SCALE = 0.92f
    private const val SHIFT_DIVISOR = 8
    private const val Z_SMALL = 0.8f
    private const val Z_LARGE = 1.1f
    private const val POP_FROM = 0.6f
}
