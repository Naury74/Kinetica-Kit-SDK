package com.naury.kinetica.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * [PushNavigation]과 [KTransitionContent]에 넣는 화면 이동 전환 모음. 모두 예측형 뒤로 가기 제스처를 따라 되감기고,
 * 가로 방향은 RTL에서 뒤집힌다. '애니메이션 삭제' 설정이 켜져 있으면 컨테이너가 전환 없이 바로 바꾼다.
 *
 * ```kotlin
 * PushNavigation(stack, onBack = { stack = stack.dropLast(1) }, transition = KTransitions.Zoom) { screen -> … }
 * ```
 */
public object KTransitions {
    /** iOS 내비게이션. 새 화면이 끝 쪽에서 밀려 들어오고, 아래 화면은 30% 물러나며 어두워진다. [PushNavigation]의 기본값이다. */
    public val Push: KScreenTransition = PushTransition(KTransitionEdge.End, PUSH_PARALLAX, PUSH_DIM, 1f)

    /** 아래 화면이 절반쯤 물러나며 작아지는 깊은 패럴랙스 밀기 */
    public val Parallax: KScreenTransition = PushTransition(KTransitionEdge.End, DEEP_PARALLAX, DEEP_DIM, DEEP_SCALE)

    /** 두 화면이 한 줄로 이어 붙은 듯 함께 밀린다. */
    public val Slide: KScreenTransition = SlideTransition(KTransitionEdge.End)

    /** 새 화면이 위를 덮으며 들어오고 아래 화면은 제자리에서 조금 어두워진다. */
    public val Cover: KScreenTransition = CoverTransition(KTransitionEdge.End)

    /** 기존 화면이 옆으로 비켜나며 아래에 있던 새 화면을 드러낸다. */
    public val Uncover: KScreenTransition = UncoverTransition(KTransitionEdge.End)

    /** Android 시스템 앱 전환처럼 새 화면이 조금 작은 크기에서 커지며 나타나고 기존 화면은 살짝 커지며 사라진다. */
    public val Zoom: KScreenTransition = ZoomTransition

    /** 두 화면이 서로 겹쳐 바뀌는 크로스페이드 */
    public val Fade: KScreenTransition = FadeTransition

    /** Material fade through. 기존 화면이 먼저 사라지고 새 화면이 살짝 커지며 나타난다. 서로 관련 없는 화면 사이에 쓴다. */
    public val FadeThrough: KScreenTransition = FadeThroughTransition

    /** Material shared axis X. 가로로 조금 밀리며 바뀐다. 단계별 화면이나 탭에 쓴다. */
    public val SharedAxisX: KScreenTransition = SharedAxisTransition(SharedAxis.X)

    /** Material shared axis Y. 세로로 조금 밀리며 바뀐다. */
    public val SharedAxisY: KScreenTransition = SharedAxisTransition(SharedAxis.Y)

    /** Material shared axis Z. 새 화면이 앞으로 다가오며 나타난다. 목록에서 상세처럼 위계가 있는 화면에 쓴다. */
    public val SharedAxisZ: KScreenTransition = SharedAxisTransition(SharedAxis.Z)

    /** 좌우로 넘기는 탭 전환. 너비의 4분의 1만큼 밀리며 겹쳐 바뀐다. [KTransitionContent]의 `isForward`와 함께 쓴다. */
    public val TabSlide: KScreenTransition = TabSlideTransition

    /** iOS 모달 시트 스택. 새 화면이 아래에서 올라오고 아래 화면은 작아지며 모서리가 둥글어지고 어두워진다. */
    public val SheetStack: KScreenTransition = SheetStackTransition

    /** 카드 스택. 새 화면이 둥근 카드로 옆에서 들어오고 아래 화면은 작아지며 뒤로 물러난다. */
    public val CardStack: KScreenTransition = CardStackTransition

    /** 두 화면이 정육면체의 이웃한 면처럼 돌아간다. */
    public val Cube: KScreenTransition = CubeTransition

    /** 카드를 뒤집듯 기존 화면이 돌아가고 반쯤 지나면 새 화면이 나타난다. */
    public val Flip: KScreenTransition = FlipTransition

    /** 화면 가운데에서 원이 퍼지며 새 화면이 드러난다. 누른 자리에서 퍼지게 하려면 [reveal]을 쓴다. */
    public val CircleReveal: KScreenTransition = RevealTransition(KRevealShape.Circle) { null }

    /** 기존 화면이 블러로 흐려지며 사라지고 새 화면이 선명해지며 나타난다. Android 12 미만에서는 블러 없이 페이드만 한다. */
    public val BlurDissolve: KScreenTransition = BlurDissolveTransition

    /** 끝 쪽에서 경계선이 쓸고 지나가며 새 화면을 드러낸다. */
    public val Wipe: KScreenTransition = WipeTransition(KTransitionEdge.End)

    /** 전환 없이 바로 바꾼다. 뒤로 가기 제스처를 하는 동안에는 아래 화면이 그대로 드러난다. */
    public val None: KScreenTransition = NoTransition

    /**
     * iOS식 밀기를 값을 바꿔 만든다.
     *
     * @param parallax 아래 화면이 물러나는 거리. 화면 너비에 대한 비율(0..1)이다.
     * @param dim 아래 화면을 어둡게 하는 정도(0..1)
     * @param from 새 화면이 들어오는 쪽
     */
    public fun push(
        parallax: Float = PUSH_PARALLAX,
        dim: Float = PUSH_DIM,
        from: KTransitionEdge = KTransitionEdge.End,
    ): KScreenTransition = PushTransition(from, parallax.coerceIn(0f, 1f), dim.coerceIn(0f, 1f), 1f)

    /** [from] 쪽에서 들어오는 [Slide] */
    public fun slide(from: KTransitionEdge): KScreenTransition = SlideTransition(from)

    /** [from] 쪽에서 덮는 [Cover]. `KTransitionEdge.Bottom`이면 전체 화면 다이얼로그처럼 아래에서 올라온다. */
    public fun cover(from: KTransitionEdge): KScreenTransition = CoverTransition(from)

    /** 기존 화면이 [from]의 반대쪽으로 비켜나는 [Uncover] */
    public fun uncover(from: KTransitionEdge): KScreenTransition = UncoverTransition(from)

    /** [from] 쪽에서 쓸고 지나가는 [Wipe] */
    public fun wipe(from: KTransitionEdge): KScreenTransition = WipeTransition(from)

    /**
     * [shape] 모양이 퍼지며 새 화면을 드러낸다.
     *
     * @param center 모양이 퍼지기 시작하는 점(화면 좌표, px). 그리기 단계에서 읽는다. [Modifier.kOrigin]으로 누른 버튼의 중심을 얻는다.
     *   `null`을 돌려주면 화면 가운데다.
     */
    public fun reveal(shape: KRevealShape = KRevealShape.Circle, center: () -> Offset? = { null }): KScreenTransition =
        RevealTransition(shape, center)
}

private class PushTransition(val edge: KTransitionEdge, val parallax: Float, val dim: Float, val backScale: Float) :
    KScreenTransition() {
    override fun enter(frame: KTransitionFrame, progress: Float) {
        frame.moveIn(edge, 1f - progress)
        frame.shadowFrom(edge, progress)
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.moveIn(edge, -parallax * progress)
        frame.dim = dim * progress
        val scale = 1f - (1f - backScale) * progress
        frame.scaleX = scale
        frame.scaleY = scale
    }
}

private class SlideTransition(val edge: KTransitionEdge) : KScreenTransition() {
    override fun enter(frame: KTransitionFrame, progress: Float) = frame.moveIn(edge, 1f - progress)

    override fun exit(frame: KTransitionFrame, progress: Float) = frame.moveIn(edge, -progress)
}

private class CoverTransition(val edge: KTransitionEdge) : KScreenTransition() {
    override fun enter(frame: KTransitionFrame, progress: Float) {
        frame.moveIn(edge, 1f - progress)
        frame.shadowFrom(edge, progress)
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.dim = COVER_DIM * progress
    }
}

private class UncoverTransition(val edge: KTransitionEdge) : KScreenTransition() {
    override val enteringOnTop: Boolean get() = false

    override fun enter(frame: KTransitionFrame, progress: Float) {
        frame.moveIn(edge, UNCOVER_PARALLAX * (1f - progress))
        frame.dim = COVER_DIM * (1f - progress)
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.moveIn(edge, -progress)
        // 비켜나는 화면의 뒤쪽 가장자리에 그림자를 드리운다.
        if (edge == KTransitionEdge.End || edge == KTransitionEdge.Start) {
            frame.edgeShadow = 1f - progress
            frame.shadowSide = if (edge == KTransitionEdge.End) -frame.endSign else frame.endSign
        }
    }
}

private object ZoomTransition : KScreenTransition() {
    override val animationSpec: AnimationSpec<Float> = tween(ZOOM_MS, easing = FastOutSlowInEasing)

    override fun enter(frame: KTransitionFrame, progress: Float) {
        frame.alpha = part(progress, 0f, ZOOM_FADE)
        frame.scale(ZOOM_FROM + (1f - ZOOM_FROM) * progress)
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.alpha = 1f - part(progress, 0f, ZOOM_FADE)
        frame.scale(1f + ZOOM_GROW * progress)
    }
}

private object FadeTransition : KScreenTransition() {
    override val animationSpec: AnimationSpec<Float> = tween(FADE_MS)

    override fun enter(frame: KTransitionFrame, progress: Float) {
        frame.alpha = progress
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.alpha = 1f - progress
    }
}

private object FadeThroughTransition : KScreenTransition() {
    override val animationSpec: AnimationSpec<Float> = tween(MATERIAL_MS, easing = FastOutSlowInEasing)

    override fun enter(frame: KTransitionFrame, progress: Float) {
        val q = part(progress, THROUGH_SPLIT, 1f)
        frame.alpha = q
        frame.scale(THROUGH_SCALE + (1f - THROUGH_SCALE) * q)
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.alpha = 1f - part(progress, 0f, THROUGH_SPLIT)
    }
}

private enum class SharedAxis { X, Y, Z }

private class SharedAxisTransition(val axis: SharedAxis) : KScreenTransition() {
    override val animationSpec: AnimationSpec<Float> = tween(MATERIAL_MS, easing = FastOutSlowInEasing)

    override fun enter(frame: KTransitionFrame, progress: Float) {
        frame.alpha = part(progress, THROUGH_SPLIT, 1f)
        val shift = AXIS_SHIFT_DP * frame.density * (1f - progress)
        when (axis) {
            SharedAxis.X -> frame.translationX = frame.endSign * shift
            SharedAxis.Y -> frame.translationY = shift
            SharedAxis.Z -> frame.scale(Z_ENTER_FROM + (1f - Z_ENTER_FROM) * progress)
        }
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.alpha = 1f - part(progress, 0f, THROUGH_SPLIT)
        val shift = AXIS_SHIFT_DP * frame.density * progress
        when (axis) {
            SharedAxis.X -> frame.translationX = -frame.endSign * shift
            SharedAxis.Y -> frame.translationY = -shift
            SharedAxis.Z -> frame.scale(1f + Z_EXIT_GROW * progress)
        }
    }
}

private object TabSlideTransition : KScreenTransition() {
    override val animationSpec: AnimationSpec<Float> = tween(TAB_MS, easing = FastOutSlowInEasing)

    override fun enter(frame: KTransitionFrame, progress: Float) {
        frame.translationX = frame.endSign * frame.width * TAB_SHIFT * (1f - progress)
        frame.alpha = part(progress, TAB_FADE_START, 1f)
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.translationX = -frame.endSign * frame.width * TAB_SHIFT * progress
        frame.alpha = 1f - part(progress, 0f, TAB_FADE_END)
    }
}

private object SheetStackTransition : KScreenTransition() {
    override fun enter(frame: KTransitionFrame, progress: Float) {
        frame.translationY = frame.height * (1f - progress)
        frame.cornerRadius = frame.sheetRadius() * part(1f - progress, 0f, CORNER_SETTLE)
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.scale(1f - SHEET_SHRINK * progress)
        frame.translationY = SHEET_DROP_DP * frame.density * progress
        frame.cornerRadius = frame.sheetRadius() * part(progress, 0f, CORNER_SETTLE)
        frame.dim = SHEET_DIM * progress
    }
}

private object CardStackTransition : KScreenTransition() {
    override fun enter(frame: KTransitionFrame, progress: Float) {
        frame.moveIn(KTransitionEdge.End, 1f - progress)
        frame.cornerRadius = frame.cardRadius() * part(1f - progress, 0f, CORNER_SETTLE)
        frame.shadowFrom(KTransitionEdge.End, progress)
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.scale(1f - CARD_SHRINK * progress)
        frame.translationX = -frame.endSign * frame.width * CARD_BACK * progress
        frame.cornerRadius = frame.cardRadius() * part(progress, 0f, CORNER_SETTLE)
        frame.dim = CARD_DIM * progress
    }
}

private object CubeTransition : KScreenTransition() {
    override fun enter(frame: KTransitionFrame, progress: Float) {
        val rest = 1f - progress
        frame.translationX = frame.endSign * frame.width * rest
        frame.pivotX = if (frame.endSign > 0f) 0f else 1f
        frame.rotationY = frame.endSign * QUARTER_TURN * rest
        frame.cameraDistance = CUBE_CAMERA * frame.density
        frame.dim = CUBE_DIM * rest
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.translationX = -frame.endSign * frame.width * progress
        frame.pivotX = if (frame.endSign > 0f) 1f else 0f
        frame.rotationY = -frame.endSign * QUARTER_TURN * progress
        frame.cameraDistance = CUBE_CAMERA * frame.density
        frame.dim = CUBE_DIM * progress
    }
}

private object FlipTransition : KScreenTransition() {
    override val animationSpec: AnimationSpec<Float> = tween(FLIP_MS, easing = FastOutSlowInEasing)

    override fun enter(frame: KTransitionFrame, progress: Float) {
        // 앞 절반에는 뒷면이라 보이지 않는다.
        frame.alpha = if (progress >= HALF) 1f else 0f
        frame.rotationY = -frame.endSign * HALF_TURN * (1f - progress)
        frame.flipDepth(progress)
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.alpha = if (progress < HALF) 1f else 0f
        frame.rotationY = frame.endSign * HALF_TURN * progress
        frame.flipDepth(progress)
    }

    private fun KTransitionFrame.flipDepth(progress: Float) {
        scale(1f - FLIP_SHRINK * sin(PI * progress).toFloat())
        cameraDistance = FLIP_CAMERA * density
    }
}

private class RevealTransition(val shape: KRevealShape, val center: () -> Offset?) : KScreenTransition() {
    override val animationSpec: AnimationSpec<Float> = tween(REVEAL_MS, easing = FastOutSlowInEasing)

    override fun enter(frame: KTransitionFrame, progress: Float) {
        if (progress >= 1f) return
        val point = center()?.let(frame::fromScreen) ?: Offset(frame.width / 2f, frame.height / 2f)
        val turn = if (shape == KRevealShape.Star) STAR_TURN * (1f - progress) else 0f
        frame.reveal(shape, point, progress, rotation = frame.endSign * turn)
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.dim = REVEAL_DIM * progress
        frame.scale(1f - REVEAL_SHRINK * progress)
    }
}

private object BlurDissolveTransition : KScreenTransition() {
    override val animationSpec: AnimationSpec<Float> = tween(BLUR_MS, easing = FastOutSlowInEasing)

    override fun enter(frame: KTransitionFrame, progress: Float) {
        frame.alpha = progress
        frame.blurRadius = BLUR_DP * frame.density * (1f - progress)
        frame.scale(1f + BLUR_ZOOM * (1f - progress))
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.alpha = 1f - progress
        frame.blurRadius = BLUR_DP * frame.density * progress
    }
}

private class WipeTransition(val edge: KTransitionEdge) : KScreenTransition() {
    override val animationSpec: AnimationSpec<Float> = tween(WIPE_MS, easing = FastOutSlowInEasing)

    override fun enter(frame: KTransitionFrame, progress: Float) {
        if (progress >= 1f) return
        val w = frame.width
        val h = frame.height
        val fromRight = (edge == KTransitionEdge.End) == (frame.endSign > 0f)
        when (edge) {
            KTransitionEdge.Top -> frame.clipTo(0f, 0f, w, h * progress)
            KTransitionEdge.Bottom -> frame.clipTo(0f, h * (1f - progress), w, h)
            else -> if (fromRight) frame.clipTo(w * (1f - progress), 0f, w, h) else frame.clipTo(0f, 0f, w * progress, h)
        }
    }

    override fun exit(frame: KTransitionFrame, progress: Float) {
        frame.moveIn(edge, -WIPE_PARALLAX * progress)
        frame.dim = COVER_DIM * progress
    }
}

private object NoTransition : KScreenTransition() {
    override val animationSpec: AnimationSpec<Float> = snap()

    override fun enter(frame: KTransitionFrame, progress: Float) {
        if (progress < 1f) frame.alpha = 0f
    }

    override fun exit(frame: KTransitionFrame, progress: Float) = Unit
}

/** [edge] 쪽에서 들어오는 방향으로 화면 크기의 [amount]배만큼 옮긴다. 음수면 반대쪽으로 비켜난다. */
private fun KTransitionFrame.moveIn(edge: KTransitionEdge, amount: Float) {
    when (edge) {
        KTransitionEdge.End -> translationX = endSign * width * amount
        KTransitionEdge.Start -> translationX = -endSign * width * amount
        KTransitionEdge.Top -> translationY = -height * amount
        KTransitionEdge.Bottom -> translationY = height * amount
    }
}

/** 가로로 들어오는 화면의 앞쪽 가장자리에 그림자를 드리운다. */
private fun KTransitionFrame.shadowFrom(edge: KTransitionEdge, progress: Float) {
    if (edge != KTransitionEdge.End && edge != KTransitionEdge.Start) return
    edgeShadow = progress
    shadowSide = if (edge == KTransitionEdge.End) endSign else -endSign
}

private fun KTransitionFrame.scale(value: Float) {
    scaleX = value
    scaleY = value
}

/** [progress]가 [from]..[to] 구간에서 지나간 비율(0..1) */
private fun part(progress: Float, from: Float, to: Float): Float = ((progress - from) / (to - from)).coerceIn(0f, 1f)

private const val HALF = 0.5f
private const val PUSH_PARALLAX = 0.3f
private const val PUSH_DIM = 0.18f
private const val DEEP_PARALLAX = 0.5f
private const val DEEP_DIM = 0.3f
private const val DEEP_SCALE = 0.94f
private const val COVER_DIM = 0.12f
private const val UNCOVER_PARALLAX = 0.15f
private const val ZOOM_MS = 350
private const val ZOOM_FROM = 0.88f
private const val ZOOM_GROW = 0.06f
private const val ZOOM_FADE = 0.6f
private const val FADE_MS = 250
private const val MATERIAL_MS = 300
private const val THROUGH_SPLIT = 0.35f
private const val THROUGH_SCALE = 0.92f
private const val AXIS_SHIFT_DP = 30f
private const val Z_ENTER_FROM = 0.8f
private const val Z_EXIT_GROW = 0.1f
private const val TAB_MS = 280
private const val TAB_SHIFT = 0.25f
private const val TAB_FADE_START = 0.1f
private const val TAB_FADE_END = 0.7f
private val STACK_CORNER = 20.dp
private const val CORNER_SETTLE = 0.25f
private const val SHEET_SHRINK = 0.08f
private const val SHEET_DROP_DP = 10f
private const val SHEET_DIM = 0.3f
private const val CARD_SHRINK = 0.1f
private const val CARD_BACK = 0.04f
private const val CARD_DIM = 0.25f
private const val QUARTER_TURN = 90f
private const val HALF_TURN = 180f
private const val CUBE_CAMERA = 24f
private const val CUBE_DIM = 0.35f
private const val FLIP_MS = 520
private const val FLIP_SHRINK = 0.12f
private const val FLIP_CAMERA = 24f
private const val REVEAL_MS = 480
private const val REVEAL_DIM = 0.15f
private const val REVEAL_SHRINK = 0.03f
private const val STAR_TURN = 72f
private const val BLUR_MS = 420
private const val BLUR_DP = 24f
private const val BLUR_ZOOM = 0.04f
private const val WIPE_MS = 450
private const val WIPE_PARALLAX = 0.2f

/** 디자인 언어 sheet 토큰의 반지름(px). 캡슐 토큰이면 기본 반지름을 쓴다. */
private fun KTransitionFrame.sheetRadius(): Float = stackRadius(shapes.sheet)

/** 디자인 언어 card 토큰의 반지름(px). 캡슐 토큰이면 기본 반지름을 쓴다. */
private fun KTransitionFrame.cardRadius(): Float = stackRadius(shapes.card)

/** 기본 디자인 언어에서는 디자인 언어가 생기기 전 모양(20dp)을 그대로 두어 호스트 화면이 바뀌지 않게 한다. */
private fun KTransitionFrame.stackRadius(token: KShapeToken): Float =
    (if (shapes == DEFAULT_SHAPES) STACK_CORNER else shapes.radiusOr(token, STACK_CORNER)).value * density

private val DEFAULT_SHAPES = KineticaShapes()
