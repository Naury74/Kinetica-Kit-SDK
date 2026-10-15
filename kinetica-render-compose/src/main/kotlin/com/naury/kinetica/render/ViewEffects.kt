package com.naury.kinetica.render

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import com.naury.kinetica.core.scene.AppearEffect
import com.naury.kinetica.core.scene.AppearKind
import com.naury.kinetica.core.scene.LoopEffect
import com.naury.kinetica.core.scene.LoopKind
import com.naury.kinetica.core.scene.NodeEffects
import com.naury.kinetica.core.scene.PressKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Compose를 쓰지 않는 XML View에 미리 만든 모션 효과([NodeEffects])를 적용한다. Compose의 `Modifier.kineticaEffects`와 같은 효과이며 거리 값은 dp다.
 *
 * - 누름 효과는 View의 pressed 상태(foreground drawable 상태)를 따라가므로 터치 리스너나 클릭 리스너를 건드리지 않는다.
 *   pressed 상태는 `isClickable`이거나 클릭 리스너를 단 View에서만 바뀐다.
 * - 리플, 어둡게 덮기, Shimmer는 View의 foreground에 [cornerRadiusDp] 둥근 사각형으로 그린다. Glow와 Lift는 elevation 그림자로 표현하며 View 윤곽을 따른다.
 * - Burst와 Ping은 View 밖까지 그려야 하므로 부모 ViewGroup의 overlay에 그린다. 부모가 없으면 그리지 않는다.
 * - View가 창에서 분리되면 반복 애니메이션을 멈추고, 다시 붙으면 이어서 재생한다. 시스템 애니메이션이 꺼져 있으면 등장 애니메이션과 반복 애니메이션은 재생하지 않는다.
 * - 애니메이션은 효과마다 따로 만든 Animator로 돌린다. `View.animate()`를 쓰지 않으므로 RecyclerView의 ItemAnimator나 호스트가 직접 건
 *   `View.animate()` 애니메이션과 서로 취소하지 않는다.
 *
 * RecyclerView 항목처럼 재사용되는 View에는 `onBindViewHolder`마다 그 항목의 효과로 [apply]를 호출한다. 효과가 없는 항목이면
 * [clear]를 호출해야 앞서 바인딩한 항목의 효과가 남지 않는다. [apply]는 등장 애니메이션을 처음부터 다시 재생하므로,
 * `notifyItemChanged(position, payload)`로 내용만 바꾸는 바인딩에서는 다시 호출하지 않는다.
 *
 * 메인 스레드에서 호출한다.
 */
public object ViewEffects {
    /**
     * [view]에 [effects]를 적용한다. 이미 적용한 효과가 있으면 먼저 제거하고 다시 적용한다. [effects]가 비어 있으면 [clear]와 같다.
     *
     * @param cornerRadiusDp 리플, 어둡게 덮기, shimmer를 그릴 모서리 반지름(dp, 0 이상). View 배경의 모서리와 같게 준다.
     */
    @JvmStatic
    @JvmOverloads
    public fun apply(view: View, effects: NodeEffects, cornerRadiusDp: Float = 0f) {
        clear(view)
        if (effects.isEmpty) return
        val attached = AttachedEffects(view, effects, cornerRadiusDp.coerceAtLeast(0f) * view.resources.displayMetrics.density)
        view.setTag(R.id.kinetica_view_effects, attached)
        attached.start()
    }

    /**
     * 적용한 효과를 제거한다. 효과가 바꾼 속성(크기, 투명도, 위치, 회전, 높이, foreground)만 효과를 적용하기 전 값으로 되돌리고,
     * 효과가 건드리지 않은 속성과 호스트의 애니메이션은 그대로 둔다. 효과가 없으면 아무 일도 하지 않는다.
     */
    @JvmStatic
    public fun clear(view: View) {
        val attached = view.getTag(R.id.kinetica_view_effects) as? AttachedEffects ?: return
        view.setTag(R.id.kinetica_view_effects, null)
        attached.stop()
    }

    /** [view]에 지금 적용된 효과. 없으면 `null`이다. */
    @JvmStatic
    public fun effectsOf(view: View): NodeEffects? = (view.getTag(R.id.kinetica_view_effects) as? AttachedEffects)?.effects
}

/** 효과가 값을 바꾸는 View 속성. [AttachedEffects.stop]이 이 속성들만 되돌린다. */
private enum class Touched { Scale, Alpha, TranslationX, TranslationY, Rotation, RotationXY, TranslationZ, Elevation }

private fun touchedBy(effects: NodeEffects): Set<Touched> = buildSet {
    effects.press.forEach { addAll(touchedBy(it.kind)) }
    effects.appear?.let { addAll(touchedBy(it.kind)) }
    effects.loop?.let { addAll(touchedBy(it.kind)) }
}

private fun touchedBy(kind: PressKind): List<Touched> = when (kind) {
    PressKind.Scale -> listOf(Touched.Scale)
    PressKind.Lift -> listOf(Touched.TranslationZ, Touched.Scale)
    PressKind.Tilt -> listOf(Touched.RotationXY)
    else -> emptyList()
}

private fun touchedBy(kind: AppearKind): List<Touched> = listOf(Touched.Alpha) + when (kind) {
    AppearKind.SlideUp, AppearKind.SlideDown -> listOf(Touched.TranslationY)
    AppearKind.SlideLeft, AppearKind.SlideRight -> listOf(Touched.TranslationX)
    AppearKind.Pop, AppearKind.Zoom -> listOf(Touched.Scale)
    AppearKind.Flip -> listOf(Touched.RotationXY)
    AppearKind.Fade, AppearKind.Blur -> emptyList()
}

private fun touchedBy(kind: LoopKind): List<Touched> = when (kind) {
    LoopKind.Pulse, LoopKind.Heartbeat -> listOf(Touched.Scale)
    LoopKind.Breathe -> listOf(Touched.Alpha)
    LoopKind.Float, LoopKind.Bounce -> listOf(Touched.TranslationY)
    LoopKind.Shake, LoopKind.Spin, LoopKind.Wiggle -> listOf(Touched.Rotation)
    LoopKind.Glow -> listOf(Touched.Elevation)
    LoopKind.Shimmer, LoopKind.Ping -> emptyList()
}

/** [values]를 [view]에 적용하는 Animator. 효과마다 속성이 몇 개뿐이라 배열 복사 비용은 없다. */
@Suppress("SpreadOperator")
private fun animatorOf(view: View, values: List<PropertyValuesHolder>): ObjectAnimator =
    ObjectAnimator.ofPropertyValuesHolder(view, *values.toTypedArray())

internal class AttachedEffects(private val view: View, val effects: NodeEffects, private val corner: Float) :
    View.OnAttachStateChangeListener {
    private val density = view.resources.displayMetrics.density
    private val kinds = effects.press.associateBy { it.kind }
    private val motion = ValueAnimator.areAnimatorsEnabled()
    private val touched = touchedBy(effects)
    private val original = ViewSnapshot(view)
    private val ripple = kinds[PressKind.Ripple]?.let { effect ->
        val color = effect.color?.argb ?: android.graphics.Color.BLACK
        RippleDrawable(
            ColorStateList.valueOf(withAlpha(color, effect.resolvedAmount.toFloat())),
            null,
            rounded(android.graphics.Color.WHITE),
        )
    }
    private val dim = kinds[PressKind.Dim]?.let { effect ->
        rounded(effect.color?.argb ?: android.graphics.Color.BLACK).apply { alpha = 0 }
    }
    private val watcher = if (kinds.keys.any { it != PressKind.Ripple }) {
        // 부모가 스크롤로 가로채기 전까지 자식 View는 touch slop 가까이까지 움직임을 받으므로, 그 절반을 넘으면 스크롤로 본다.
        PressWatcher(android.view.ViewConfiguration.get(view.context).scaledTouchSlop / 2f, ::pressed)
    } else {
        null
    }
    private val shimmer = effects.loop?.takeIf { it.kind == LoopKind.Shimmer && motion }?.let { ShimmerDrawable(it, corner) }
    private var press: Animator? = null
    private var appear: Animator? = null
    private var blur: ValueAnimator? = null
    private var loop: ValueAnimator? = null
    private var ping: PingDrawable? = null

    /** 반복 애니메이션이 돌고 있는지. 테스트에서 누수를 확인할 때 쓴다. */
    internal val looping: Boolean get() = loop != null

    fun start() {
        val layers = listOfNotNull(original.foreground, ripple, dim, shimmer, watcher)
        if (layers.size > (if (original.foreground == null) 0 else 1)) view.foreground = LayerDrawable(layers.toTypedArray())
        effects.appear?.takeIf { motion }?.let(::appear)
        view.addOnAttachStateChangeListener(this)
        if (view.isAttachedToWindow) startLoop()
    }

    fun stop() {
        view.removeOnAttachStateChangeListener(this)
        stopLoop()
        listOfNotNull(press, appear, blur).forEach(Animator::cancel)
        press = null
        appear = null
        blur = null
        view.foreground = original.foreground
        if (effects.appear?.kind == AppearKind.Blur && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            view.setRenderEffect(null)
        }
        original.restore(view, touched)
    }

    override fun onViewAttachedToWindow(v: View) = startLoop()

    override fun onViewDetachedFromWindow(v: View) = stopLoop()

    private fun stopLoop() {
        loop?.cancel()
        loop = null
        ping?.let { drawable -> (view.parent as? android.view.ViewGroup)?.overlay?.remove(drawable) }
        ping = null
    }

    private fun pressed(down: Boolean, x: Float, y: Float, moved: Boolean) {
        // 리플은 View 상태를 직접 따르므로 여기서는 나머지 효과만 갱신한다.
        dim?.alpha = if (down) ((kinds[PressKind.Dim]?.resolvedAmount ?: 0.0) * FULL_ALPHA).toInt() else 0
        val values = pressValues(down, x, y)
        if (!down) burstIfTap(x, y, moved)
        press?.cancel()
        press = values.takeIf { it.isNotEmpty() }?.let {
            animatorOf(view, it).apply {
                duration = PRESS_MS
                interpolator = if (down) STANDARD else OvershootInterpolator()
                start()
            }
        }
    }

    /** 눌렀을 때([down])나 뗐을 때 크기, 높이, 기울기가 향할 값. */
    private fun pressValues(down: Boolean, x: Float, y: Float): List<PropertyValuesHolder> = buildList {
        fun scaleTo(scale: Float) {
            add(PropertyValuesHolder.ofFloat(View.SCALE_X, scale))
            add(PropertyValuesHolder.ofFloat(View.SCALE_Y, scale))
        }
        kinds[PressKind.Scale]?.let { scaleTo(if (down) it.resolvedAmount.toFloat() else original.scaleX) }
        kinds[PressKind.Lift]?.let { effect ->
            add(PropertyValuesHolder.ofFloat(View.TRANSLATION_Z, if (down) effect.resolvedAmount.toFloat() * density else 0f))
            if (PressKind.Scale !in kinds) scaleTo(if (down) original.scaleX * (1f + LIFT_SCALE) else original.scaleX)
        }
        kinds[PressKind.Tilt]?.let {
            val amount = it.resolvedAmount.toFloat()
            val tiltX = if (view.width > 0) (x / view.width - HALF) * 2f else 0f
            val tiltY = if (view.height > 0) (y / view.height - HALF) * 2f else 0f
            add(PropertyValuesHolder.ofFloat(View.ROTATION_X, if (down) -tiltY * amount else 0f))
            add(PropertyValuesHolder.ofFloat(View.ROTATION_Y, if (down) tiltX * amount else 0f))
        }
    }

    private fun appear(appear: AppearEffect) {
        val slide = SLIDE_DP * density
        val values = arrayListOf(PropertyValuesHolder.ofFloat(View.ALPHA, 0f, original.alpha))
        when (appear.kind) {
            AppearKind.Fade, AppearKind.Blur -> Unit

            AppearKind.SlideUp -> values += PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, slide, original.translationY)

            AppearKind.SlideDown -> values += PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -slide, original.translationY)

            AppearKind.SlideLeft -> values += PropertyValuesHolder.ofFloat(View.TRANSLATION_X, slide, original.translationX)

            AppearKind.SlideRight -> values += PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -slide, original.translationX)

            AppearKind.Pop, AppearKind.Zoom -> {
                val from = if (appear.kind == AppearKind.Pop) POP_FROM else ZOOM_FROM
                values += PropertyValuesHolder.ofFloat(View.SCALE_X, from, original.scaleX)
                values += PropertyValuesHolder.ofFloat(View.SCALE_Y, from, original.scaleY)
            }

            AppearKind.Flip -> values += PropertyValuesHolder.ofFloat(View.ROTATION_X, FLIP_DEGREES, original.rotationX)
        }
        if (appear.kind == AppearKind.Blur) blurIn(appear)
        this.appear = animatorOf(view, values).apply {
            startDelay = appear.delayMs
            duration = appear.durationMs
            interpolator = if (appear.kind == AppearKind.Pop) OvershootInterpolator(POP_OVERSHOOT) else STANDARD
            // startDelay 동안에도 처음 모습(투명 등)으로 보이도록 시작 값을 바로 적용한다.
            setCurrentFraction(0f)
            start()
        }
    }

    private fun blurIn(appear: AppearEffect) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        blur = ValueAnimator.ofFloat(BLUR_FROM_DP * density, 0f).apply {
            startDelay = appear.delayMs
            duration = appear.durationMs
            interpolator = STANDARD
            addUpdateListener {
                val radius = it.animatedValue as Float
                view.setRenderEffect(
                    if (radius > MIN_BLUR) android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL) else null,
                )
            }
            start()
        }
    }

    /**
     * 탭이면 View 가운데에서 조각이 터져 나간다. 부모 overlay에 잠깐 그렸다가 제거한다.
     * 스크롤 때문에 pressed가 풀렸거나 View 밖에서 손을 뗀 경우는 탭이 아니므로 재생하지 않는다.
     */
    private fun burstIfTap(x: Float, y: Float, moved: Boolean) {
        val effect = kinds[PressKind.Burst] ?: return
        val inside = x in 0f..view.width.toFloat() && y in 0f..view.height.toFloat()
        if (moved || !inside) return
        val overlay = (view.parent as? android.view.ViewGroup)?.overlay ?: return
        val drawable = BurstDrawable(effect, view)
        overlay.add(drawable)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BURST_MS
            interpolator = STANDARD
            addUpdateListener { drawable.progress = it.animatedValue as Float }
            addListener(
                object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = overlay.remove(drawable)
                },
            )
            start()
        }
    }

    private fun startLoop() {
        val effect = effects.loop?.takeIf { motion } ?: return
        if (loop != null) return
        if (effect.kind == LoopKind.Ping) {
            ping = (view.parent as? android.view.ViewGroup)?.let { parent ->
                PingDrawable(effect, view, corner).also { parent.overlay.add(it) }
            }
        }
        loop = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = effect.periodMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { applyLoop(effect, it.animatedValue as Float) }
            start()
        }
    }

    // 효과 종류마다 분기가 하나씩이다.
    @Suppress("CyclomaticComplexMethod")
    private fun applyLoop(effect: LoopEffect, t: Float) {
        val amount = effect.resolvedAmount.toFloat()
        val wave = sin(PI * t).toFloat()
        when (effect.kind) {
            LoopKind.Pulse -> (1f + (amount - 1f) * wave * wave).let {
                view.scaleX = it
                view.scaleY = it
            }

            LoopKind.Breathe -> view.alpha = 1f - (1f - amount) * wave * wave

            LoopKind.Float -> view.translationY = -amount * density * sin(2 * PI * t).toFloat()

            LoopKind.Bounce -> view.translationY = -amount * density * abs(wave)

            LoopKind.Shake ->
                view.rotation =
                    if (t < SHAKE_PART) amount * sin(2 * PI * SHAKES * t / SHAKE_PART).toFloat() * (1f - t / SHAKE_PART) else 0f

            LoopKind.Spin -> view.rotation = FULL_TURN * t

            LoopKind.Shimmer -> shimmer?.progress = t

            LoopKind.Glow -> view.elevation = original.elevation + amount * density * (GLOW_MIN + (1f - GLOW_MIN) * wave * wave) / 2f

            LoopKind.Heartbeat -> (1f + (amount - 1f) * heartbeatAt(t)).let {
                view.scaleX = it
                view.scaleY = it
            }

            LoopKind.Wiggle -> view.rotation = amount * sin(2 * PI * t).toFloat()

            LoopKind.Ping -> ping?.progress = t
        }
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = corner
    }

    private companion object {
        val STANDARD = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        const val PRESS_MS = 160L
        const val SLIDE_DP = 24f
        const val POP_FROM = 0.6f
        const val POP_OVERSHOOT = 2f
        const val ZOOM_FROM = 1.15f
        const val FLIP_DEGREES = 90f
        const val SHAKE_PART = 0.35f
        const val SHAKES = 3
        const val FULL_TURN = 360f
        const val GLOW_MIN = 0.4f
        const val HALF = 0.5f
        const val FULL_ALPHA = 255
        const val LIFT_SCALE = 0.02f
        const val BLUR_FROM_DP = 16f
        const val MIN_BLUR = 0.5f
        const val BURST_MS = 560L
    }
}

/** 효과를 적용하기 전의 View 속성. 효과를 제거할 때 효과가 바꾼 속성만 이 값으로 되돌린다. */
private class ViewSnapshot(view: View) {
    val foreground: Drawable? = view.foreground
    val scaleX = view.scaleX
    val scaleY = view.scaleY
    val alpha = view.alpha
    val translationX = view.translationX
    val translationY = view.translationY
    val translationZ = view.translationZ
    val rotation = view.rotation
    val rotationX = view.rotationX
    val rotationY = view.rotationY
    val elevation = view.elevation

    fun restore(view: View, touched: Set<Touched>) {
        for (property in touched) {
            when (property) {
                Touched.Scale -> {
                    view.scaleX = scaleX
                    view.scaleY = scaleY
                }

                Touched.Alpha -> view.alpha = alpha

                Touched.TranslationX -> view.translationX = translationX

                Touched.TranslationY -> view.translationY = translationY

                Touched.Rotation -> view.rotation = rotation

                Touched.RotationXY -> {
                    view.rotationX = rotationX
                    view.rotationY = rotationY
                }

                Touched.TranslationZ -> view.translationZ = translationZ

                Touched.Elevation -> view.elevation = elevation
            }
        }
    }
}

private fun withAlpha(color: Int, alpha: Float): Int = (color and RGB_MASK) or ((alpha * FULL_ALPHA_INT).toInt() shl ALPHA_SHIFT)

private const val RGB_MASK = 0x00FFFFFF
private const val ALPHA_SHIFT = 24
private const val FULL_ALPHA_INT = 255

/**
 * View의 pressed 상태를 알아내기 위한 보이지 않는 foreground 레이어. 클릭할 수 있는 View는 누르는 동안 drawable 상태에 pressed가 들어오고,
 * 누른 위치는 hotspot으로 전달된다.
 */
private class PressWatcher(private val slop: Float, private val onPress: (Boolean, Float, Float, Boolean) -> Unit) : Drawable() {
    private var down = false
    private var x = 0f
    private var y = 0f
    private var startX = 0f
    private var startY = 0f
    private var moved = false

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val pressed = android.R.attr.state_pressed in state
        if (pressed != down) {
            down = pressed
            if (pressed) {
                startX = x
                startY = y
                moved = false
            }
            onPress(pressed, x, y, moved)
        }
        return false
    }

    override fun setHotspot(x: Float, y: Float) {
        this.x = x
        this.y = y
        if (down && kotlin.math.hypot(x - startX, y - startY) > slop) moved = true
    }

    override fun draw(canvas: Canvas) = Unit

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Drawable 계약상 구현해야 한다.", ReplaceWith("PixelFormat.TRANSPARENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSPARENT
}

/** View foreground 위로 지나가는 Shimmer 빛줄기. */
private class ShimmerDrawable(private val loop: LoopEffect, private val corner: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var progress = 0f
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val band = bounds.width() * BAND
        val start = bounds.left - band + (bounds.width() + 2 * band) * progress
        val color = loop.color?.argb ?: android.graphics.Color.WHITE
        val peak = android.graphics.Color.argb(
            (loop.resolvedAmount * FULL).toInt(),
            android.graphics.Color.red(color),
            android.graphics.Color.green(color),
            android.graphics.Color.blue(color),
        )
        paint.shader = LinearGradient(
            start - band,
            bounds.top.toFloat(),
            start + band,
            bounds.bottom.toFloat(),
            intArrayOf(android.graphics.Color.TRANSPARENT, peak, android.graphics.Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(android.graphics.RectF(bounds), corner, corner, paint)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Drawable 계약상 구현해야 한다.", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val BAND = 0.35f
        const val FULL = 255
    }
}
