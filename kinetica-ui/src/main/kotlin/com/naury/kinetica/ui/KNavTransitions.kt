package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset

/**
 * Navigation Compose의 `NavHost`나 `AnimatedContent`에 넣는 전환 네 가지(들어오기, 나가기, 뒤로 와서 들어오기, 뒤로 가며 나가기) 묶음.
 * 미리 만든 묶음은 [KNavTransitions]에 있다. Compose 기본 전환으로 만들었으므로 navigation-compose의 예측형 뒤로 가기 되감기와
 * `slideIntoContainer`의 RTL 처리를 그대로 따른다.
 *
 * ```kotlin
 * val nav = rememberKNavTransition(KNavTransitions.Push)
 * NavHost(controller, "home", enterTransition = nav.enter, exitTransition = nav.exit,
 *     popEnterTransition = nav.popEnter, popExitTransition = nav.popExit) { … }
 * ```
 *
 * @property enter 새 화면이 들어올 때
 * @property exit 새 화면에 가려지는 화면이 나갈 때
 * @property popEnter 뒤로 가서 아래 화면이 다시 들어올 때. 주지 않으면 [enter]
 * @property popExit 뒤로 가서 위 화면이 나갈 때. 주지 않으면 [exit]
 */
@Immutable
public class KNavTransitionSpec(
    public val enter: AnimatedContentTransitionScope<*>.() -> EnterTransition,
    public val exit: AnimatedContentTransitionScope<*>.() -> ExitTransition,
    public val popEnter: AnimatedContentTransitionScope<*>.() -> EnterTransition = enter,
    public val popExit: AnimatedContentTransitionScope<*>.() -> ExitTransition = exit,
)

/**
 * `AnimatedContent`의 `transitionSpec` 안에서 [spec]을 쓴다. [forward]가 `false`면 뒤로 가는 전환(`popEnter`, `popExit`)을 쓰고
 * 나가는 내용을 위에 그린다.
 *
 * ```kotlin
 * AnimatedContent(step, transitionSpec = { kTransform(KNavTransitions.SharedAxisX, forward = targetState > initialState) }) { … }
 * ```
 */
public fun AnimatedContentTransitionScope<*>.kTransform(spec: KNavTransitionSpec, forward: Boolean = true): ContentTransform =
    if (forward) {
        ContentTransform(spec.enter(this), spec.exit(this))
    } else {
        ContentTransform(spec.popEnter(this), spec.popExit(this), targetContentZIndex = -1f)
    }

/**
 * '애니메이션 삭제' 설정이 켜져 있으면 전환 없이 바로 바뀌는 묶음을, 아니면 [spec]을 그대로 돌려준다.
 */
@Composable
public fun rememberKNavTransition(spec: KNavTransitionSpec): KNavTransitionSpec = if (reducedMotion()) KNavTransitions.None else spec

/**
 * [KNavTransitionSpec] 프리셋. Compose 기본 전환으로 만들 수 있는 것만 있다. 화면을 어둡게 하거나 3D로 돌리거나 모양으로 드러내는 전환은
 * [PushNavigation]과 [KTransitionContent]에 [KTransitions]로 넣는다.
 */
public object KNavTransitions {
    /** 옆으로 밀기. 새 화면이 끝 쪽에서 들어오고 이전 화면은 30% 물러난다. */
    public val Push: KNavTransitionSpec = push(DEFAULT_STIFFNESS)

    /** 두 화면이 함께 밀린다. */
    public val Slide: KNavTransitionSpec = slide(DEFAULT_STIFFNESS)

    /** 새 화면이 아래에서 올라와 덮고, 뒤로 가면 아래로 내려간다. 전체 화면 다이얼로그에 쓴다. */
    public val SlideUp: KNavTransitionSpec = slideUp(DEFAULT_STIFFNESS)

    /** 시트 스택. 새 화면이 아래에서 올라오고 이전 화면은 작아지며 흐려진다. */
    public val SheetStack: KNavTransitionSpec = KNavTransitionSpec(
        enter = { slideIntoContainer(SlideDirection.Up, slideSpec()) },
        exit = { scaleOut(tween(MATERIAL_MS), SHEET_BACK) + fadeOut(tween(MATERIAL_MS), SHEET_ALPHA) },
        popEnter = { scaleIn(tween(MATERIAL_MS), SHEET_BACK) + fadeIn(tween(MATERIAL_MS), SHEET_ALPHA) },
        popExit = { slideOutOfContainer(SlideDirection.Down, slideSpec()) },
    )

    /** 새 화면이 작은 크기에서 커지며 나타나고, 뒤로 가면 거꾸로 줄어든다. */
    public val Zoom: KNavTransitionSpec = KNavTransitionSpec(
        enter = { scaleIn(tween(ZOOM_MS, easing = FastOutSlowInEasing), ZOOM_SMALL) + fadeIn(tween(ZOOM_FADE_MS)) },
        exit = { scaleOut(tween(ZOOM_MS, easing = FastOutSlowInEasing), ZOOM_LARGE) + fadeOut(tween(ZOOM_MS)) },
        popEnter = { scaleIn(tween(ZOOM_MS, easing = FastOutSlowInEasing), ZOOM_LARGE) + fadeIn(tween(ZOOM_FADE_MS)) },
        popExit = { scaleOut(tween(ZOOM_MS, easing = FastOutSlowInEasing), ZOOM_SMALL) + fadeOut(tween(ZOOM_MS)) },
    )

    /** 크로스페이드 */
    public val Fade: KNavTransitionSpec = KNavTransitionSpec(enter = { fadeIn(tween(FADE_MS)) }, exit = { fadeOut(tween(FADE_MS)) })

    /** Material fade through */
    public val FadeThrough: KNavTransitionSpec = KNavTransitionSpec(
        enter = {
            fadeIn(tween(THROUGH_IN_MS, THROUGH_OUT_MS, LinearOutSlowInEasing)) +
                scaleIn(tween(THROUGH_IN_MS, THROUGH_OUT_MS, LinearOutSlowInEasing), THROUGH_SCALE)
        },
        exit = { fadeOut(tween(THROUGH_OUT_MS, easing = FastOutLinearInEasing)) },
    )

    /** Material shared axis X. 뒤로 가면 반대 방향으로 밀린다. */
    public val SharedAxisX: KNavTransitionSpec = KNavTransitionSpec(
        enter = { slideIntoContainer(SlideDirection.Start, axisSpec()) { it / AXIS_DIVISOR } + axisFadeIn() },
        exit = { slideOutOfContainer(SlideDirection.Start, axisSpec()) { it / AXIS_DIVISOR } + axisFadeOut() },
        popEnter = { slideIntoContainer(SlideDirection.End, axisSpec()) { it / AXIS_DIVISOR } + axisFadeIn() },
        popExit = { slideOutOfContainer(SlideDirection.End, axisSpec()) { it / AXIS_DIVISOR } + axisFadeOut() },
    )

    /** Material shared axis Y */
    public val SharedAxisY: KNavTransitionSpec = KNavTransitionSpec(
        enter = { slideIntoContainer(SlideDirection.Up, axisSpec()) { it / AXIS_DIVISOR } + axisFadeIn() },
        exit = { slideOutOfContainer(SlideDirection.Up, axisSpec()) { it / AXIS_DIVISOR } + axisFadeOut() },
        popEnter = { slideIntoContainer(SlideDirection.Down, axisSpec()) { it / AXIS_DIVISOR } + axisFadeIn() },
        popExit = { slideOutOfContainer(SlideDirection.Down, axisSpec()) { it / AXIS_DIVISOR } + axisFadeOut() },
    )

    /** Material shared axis Z */
    public val SharedAxisZ: KNavTransitionSpec = KNavTransitionSpec(
        enter = { scaleIn(tween(MATERIAL_MS), Z_SMALL) + axisFadeIn() },
        exit = { scaleOut(tween(MATERIAL_MS), Z_LARGE) + axisFadeOut() },
        popEnter = { scaleIn(tween(MATERIAL_MS), Z_LARGE) + axisFadeIn() },
        popExit = { scaleOut(tween(MATERIAL_MS), Z_SMALL) + axisFadeOut() },
    )

    /** 전환 없이 바로 바꾼다. */
    public val None: KNavTransitionSpec = KNavTransitionSpec(enter = { EnterTransition.None }, exit = { ExitTransition.None })

    /**
     * [Push]를 [motion]의 빠르기로 만든다. 디자인 언어의 모션을 따르려면 `KNavTransitions.push(KineticaTheme.design.motion)`.
     * 화면 가장자리가 넘쳐 틈이 보이지 않도록 [KineticaMotion.damping]은 쓰지 않고 넘치지 않는 스프링으로 [KineticaMotion.stiffness]만 따른다.
     */
    public fun push(motion: KineticaMotion): KNavTransitionSpec = push(motion.stiffness)

    /** [Slide]를 [motion]의 빠르기로 만든다. 감쇠는 [push]처럼 따르지 않는다. */
    public fun slide(motion: KineticaMotion): KNavTransitionSpec = slide(motion.stiffness)

    /** [SlideUp]을 [motion]의 빠르기로 만든다. 감쇠는 [push]처럼 따르지 않는다. */
    public fun slideUp(motion: KineticaMotion): KNavTransitionSpec = slideUp(motion.stiffness)

    private fun push(stiffness: Float) = KNavTransitionSpec(
        enter = { slideIntoContainer(SlideDirection.Start, slideSpec(stiffness)) },
        exit = { slideOutOfContainer(SlideDirection.Start, slideSpec(stiffness)) { it * PARALLAX_PERCENT / PERCENT } },
        popEnter = { slideIntoContainer(SlideDirection.End, slideSpec(stiffness)) { it * PARALLAX_PERCENT / PERCENT } },
        popExit = { slideOutOfContainer(SlideDirection.End, slideSpec(stiffness)) },
    )

    private fun slide(stiffness: Float) = KNavTransitionSpec(
        enter = { slideIntoContainer(SlideDirection.Start, slideSpec(stiffness)) },
        exit = { slideOutOfContainer(SlideDirection.Start, slideSpec(stiffness)) },
        popEnter = { slideIntoContainer(SlideDirection.End, slideSpec(stiffness)) },
        popExit = { slideOutOfContainer(SlideDirection.End, slideSpec(stiffness)) },
    )

    private fun slideUp(stiffness: Float) = KNavTransitionSpec(
        enter = { slideIntoContainer(SlideDirection.Up, slideSpec(stiffness)) },
        exit = { ExitTransition.KeepUntilTransitionsFinished },
        popEnter = { EnterTransition.None },
        popExit = { slideOutOfContainer(SlideDirection.Down, slideSpec(stiffness)) },
    )

    private fun slideSpec(stiffness: Float = DEFAULT_STIFFNESS): FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = 1f, stiffness = stiffness, visibilityThreshold = IntOffset.VisibilityThreshold)

    private const val DEFAULT_STIFFNESS = Spring.StiffnessMediumLow

    private fun axisSpec(): FiniteAnimationSpec<IntOffset> = tween(MATERIAL_MS, easing = FastOutSlowInEasing)

    private fun axisFadeIn(): EnterTransition = fadeIn(tween(THROUGH_IN_MS, THROUGH_OUT_MS, LinearOutSlowInEasing))

    private fun axisFadeOut(): ExitTransition = fadeOut(tween(THROUGH_OUT_MS, easing = FastOutLinearInEasing))

    private const val PARALLAX_PERCENT = 30
    private const val PERCENT = 100
    private const val MATERIAL_MS = 300
    private const val THROUGH_OUT_MS = 105
    private const val THROUGH_IN_MS = 195
    private const val THROUGH_SCALE = 0.92f
    private const val AXIS_DIVISOR = 10
    private const val Z_SMALL = 0.8f
    private const val Z_LARGE = 1.1f
    private const val ZOOM_MS = 350
    private const val ZOOM_FADE_MS = 200
    private const val ZOOM_SMALL = 0.88f
    private const val ZOOM_LARGE = 1.06f
    private const val FADE_MS = 250
    private const val SHEET_BACK = 0.92f
    private const val SHEET_ALPHA = 0.6f
}
