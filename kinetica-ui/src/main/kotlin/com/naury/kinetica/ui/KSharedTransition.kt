package com.naury.kinetica.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CancellationException

private val LocalKSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
private val LocalKAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 목록 사진이 상세 화면 머리 사진으로 커지는 식의 공유 요소 전환을 한 번에 꾸린다. Compose의 `SharedTransitionLayout`과 `AnimatedContent`를
 * 감싼 것으로, [target]이 바뀌면 이전 화면은 흐려지고 새 화면이 나타나며 양쪽에서 같은 키로 [kSharedElement]나 [kSharedBounds]를 붙인
 * 요소는 자리와 크기가 이어지며 옮겨 간다.
 *
 * [onBack]을 주면 예측형 뒤로 가기 제스처를 하는 동안 전환이 손가락을 따라 [backTarget] 화면 쪽으로 감기고, 놓으면 [onBack]을 부른다.
 * 호출한 쪽은 [onBack]에서 [target]을 [backTarget]으로 바꾼다. 바꾸지 않으면 원래 화면으로 돌아온다. 제스처를 취소해도 돌아온다.
 * '애니메이션 삭제' 설정에서는 전환 없이 바로 바뀐다.
 *
 * ```kotlin
 * var selected by remember { mutableStateOf<Photo?>(null) }
 * KSharedTransitionHost(selected, onBack = if (selected != null) ({ selected = null }) else null, backTarget = null) { photo ->
 *     if (photo == null) Grid { p -> Image(p, Modifier.kSharedElement("photo-${'$'}{p.id}")) } else Detail(photo)
 * }
 * ```
 *
 * @param target 지금 보여 줄 화면을 정하는 값. `equals`로 비교하고 [content]가 화면을 고르는 데 쓴다.
 * @param onBack 뒤로 가기를 마쳤을 때. null이면 뒤로 가기를 받지 않고 호스트(액티비티)가 처리한다.
 * @param backTarget 뒤로 가면 돌아갈 화면 값. [target]과 같으면 뒤로 가기를 받지 않는다.
 */
@Composable
public fun <T> KSharedTransitionHost(
    target: T,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backTarget: T = target,
    content: @Composable (T) -> Unit,
) {
    val seekable = remember { SeekableTransitionState(target) }
    val latestTarget = rememberUpdatedState(target)
    val latestBack = rememberUpdatedState(onBack)
    val still = reducedMotion()
    LaunchedEffect(target) { if (seekable.targetState != target || seekable.currentState != target) seekable.animateTo(target) }
    val settled = seekable.currentState == target && seekable.targetState == target
    PredictiveBackHandler(enabled = onBack != null && backTarget != target && settled) { events ->
        val destination = backTarget
        val before = latestTarget.value
        try {
            events.collect { seekable.seekTo(it.progress.coerceIn(0f, 1f) * BACK_SEEK, destination) }
            latestBack.value?.invoke()
            withFrameNanos { }
            withFrameNanos { }
            if (latestTarget.value == before) seekable.animateTo(before)
        } catch (cancelled: CancellationException) {
            seekable.animateTo(before)
            throw cancelled
        }
    }
    val transition = rememberTransition(seekable, label = "k-shared")
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalKSharedScope provides this, LocalKReducedShared provides still) {
            transition.AnimatedContent(
                transitionSpec = {
                    if (still) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(FADE_IN_MS, delayMillis = FADE_DELAY_MS)) togetherWith fadeOut(tween(FADE_OUT_MS))
                    }
                },
            ) { state ->
                CompositionLocalProvider(LocalKAnimatedScope provides this) { content(state) }
            }
        }
    }
}

private val LocalKReducedShared = staticCompositionLocalOf { false }

/**
 * [KSharedTransitionHost] 안에서 이전 화면과 새 화면의 같은 [key] 요소를 하나로 이어 옮긴다. 사진처럼 두 화면에서 내용이 같은 요소에 쓴다.
 * 호스트 밖에서는 아무 일도 하지 않는다.
 */
@Composable
public fun Modifier.kSharedElement(key: Any): Modifier {
    val shared = LocalKSharedScope.current ?: return this
    val animated = LocalKAnimatedScope.current ?: return this
    val bounds = sharedBoundsTransform()
    return with(shared) { this@kSharedElement.sharedElement(rememberSharedContentState(key), animated, boundsTransform = bounds) }
}

/**
 * [kSharedElement]와 같지만 두 화면의 내용이 다를 때 쓴다. 목록 카드가 상세 화면 전체로 커지는 것처럼 테두리만 이어지고 안쪽 내용은 서로 흐려지며 바뀐다.
 * 호스트 밖에서는 아무 일도 하지 않는다.
 */
@Composable
public fun Modifier.kSharedBounds(key: Any): Modifier {
    val shared = LocalKSharedScope.current ?: return this
    val animated = LocalKAnimatedScope.current ?: return this
    val bounds = sharedBoundsTransform()
    return with(shared) { this@kSharedBounds.sharedBounds(rememberSharedContentState(key), animated, boundsTransform = bounds) }
}

@Composable
private fun sharedBoundsTransform(): BoundsTransform {
    val still = LocalKReducedShared.current
    return remember(still) {
        BoundsTransform { _, _ ->
            if (still) {
                snap()
            } else {
                spring(
                    dampingRatio = BOUNDS_DAMPING,
                    stiffness = BOUNDS_STIFFNESS,
                    visibilityThreshold = Rect.VisibilityThreshold,
                )
            }
        }
    }
}

private const val BACK_SEEK = 0.9f
private const val FADE_IN_MS = 220
private const val FADE_DELAY_MS = 60
private const val FADE_OUT_MS = 160
private const val BOUNDS_DAMPING = 0.86f
private const val BOUNDS_STIFFNESS = 380f
