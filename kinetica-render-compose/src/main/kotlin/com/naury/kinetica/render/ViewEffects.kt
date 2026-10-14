package com.naury.kinetica.render

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
 * Compose가 아닌 XML View에 미리 만든 움직임 효과([NodeEffects])를 붙인다. Compose의 `Modifier.kineticaEffects`와 같은 효과이며 거리 값은 dp다.
 *
 * - 누름 효과는 View의 눌림 상태(foreground drawable 상태)를 따라가므로 터치·클릭 리스너를 건드리지 않는다.
 *   눌림 상태는 클릭할 수 있는 View(`isClickable`, 클릭 리스너를 단 View)에서만 바뀐다.
 * - 리플·어둡게·반짝임은 View의 foreground로 [cornerRadiusDp] 둥근 사각형 안에 그린다. 빛 번짐은 그림자 높이로 표현하며 View 윤곽을 따른다.
 * - View가 화면에서 떨어지면 반복을 멈추고 다시 붙으면 이어 간다. 시스템 애니메이션이 꺼져 있으면 나타나기·반복은 하지 않는다.
 *
 * 메인 스레드에서 부른다.
 */
public object ViewEffects {
    /**
     * [view]에 [effects]를 붙인다. 이미 붙인 효과가 있으면 먼저 떼고 붙인다.
     *
     * @param cornerRadiusDp 리플·어둡게·반짝임을 그릴 모서리 반지름(dp, 0 이상). View 배경과 같게 준다.
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

    /** 붙인 효과를 떼고 View를 원래 모습으로 돌린다. */
    @JvmStatic
    public fun clear(view: View) {
        (view.getTag(R.id.kinetica_view_effects) as? AttachedEffects)?.stop()
        view.setTag(R.id.kinetica_view_effects, null)
    }
}

private class AttachedEffects(private val view: View, private val effects: NodeEffects, private val corner: Float) :
    View.OnAttachStateChangeListener {
    private val density = view.resources.displayMetrics.density
    private val kinds = effects.press.associateBy { it.kind }
    private val motion = ValueAnimator.areAnimatorsEnabled()
    private val originalForeground = view.foreground
    private val originalElevation = view.elevation
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
    private val watcher = if (kinds.keys.any { it != PressKind.Ripple }) PressWatcher(::pressed) else null
    private val shimmer = effects.loop?.takeIf { it.kind == LoopKind.Shimmer && motion }?.let { ShimmerDrawable(it, corner) }
    private var loop: ValueAnimator? = null

    fun start() {
        val layers = listOfNotNull(originalForeground, ripple, dim, shimmer, watcher)
        if (layers.size > (if (originalForeground == null) 0 else 1)) view.foreground = LayerDrawable(layers.toTypedArray())
        effects.appear?.takeIf { motion }?.let(::appear)
        view.addOnAttachStateChangeListener(this)
        if (view.isAttachedToWindow) startLoop()
    }

    fun stop() {
        view.removeOnAttachStateChangeListener(this)
        loop?.cancel()
        loop = null
        view.animate().cancel()
        view.foreground = originalForeground
        view.elevation = originalElevation
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.rotation = 0f
        view.rotationX = 0f
        view.rotationY = 0f
    }

    override fun onViewAttachedToWindow(v: View) = startLoop()

    override fun onViewDetachedFromWindow(v: View) {
        loop?.cancel()
        loop = null
    }

    private fun pressed(down: Boolean, x: Float, y: Float) {
        // 리플은 View 상태를 직접 따르므로 여기서는 나머지 효과만 움직인다.
        dim?.alpha = if (down) ((kinds[PressKind.Dim]?.resolvedAmount ?: 0.0) * FULL_ALPHA).toInt() else 0
        val animator = view.animate().setDuration(PRESS_MS).setInterpolator(if (down) STANDARD else OvershootInterpolator())
        kinds[PressKind.Scale]?.let {
            val s = if (down) it.resolvedAmount.toFloat() else 1f
            animator.scaleX(s).scaleY(s)
        }
        kinds[PressKind.Tilt]?.let {
            val amount = it.resolvedAmount.toFloat()
            val tiltX = if (view.width > 0) (x / view.width - HALF) * 2f else 0f
            val tiltY = if (view.height > 0) (y / view.height - HALF) * 2f else 0f
            animator.rotationX(if (down) -tiltY * amount else 0f).rotationY(if (down) tiltX * amount else 0f)
        }
        animator.start()
    }

    private fun appear(appear: AppearEffect) {
        val slide = SLIDE_DP * density
        view.alpha = 0f
        when (appear.kind) {
            AppearKind.Fade -> Unit
            AppearKind.SlideUp -> view.translationY = slide
            AppearKind.SlideDown -> view.translationY = -slide
            AppearKind.SlideLeft -> view.translationX = slide
            AppearKind.SlideRight -> view.translationX = -slide
            AppearKind.Pop -> view.scaleX = POP_FROM.also { view.scaleY = it }
            AppearKind.Zoom -> view.scaleX = ZOOM_FROM.also { view.scaleY = it }
            AppearKind.Flip -> view.rotationX = FLIP_DEGREES
        }
        view.animate()
            .alpha(1f).translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).rotationX(0f)
            .setStartDelay(appear.delayMs)
            .setDuration(appear.durationMs)
            .setInterpolator(if (appear.kind == AppearKind.Pop) OvershootInterpolator(POP_OVERSHOOT) else STANDARD)
            .start()
    }

    private fun startLoop() {
        val effect = effects.loop?.takeIf { motion } ?: return
        if (loop != null) return
        loop = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = effect.periodMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { applyLoop(effect, it.animatedValue as Float) }
            start()
        }
    }

    // 종류마다 한 갈래인 분기다.
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

            LoopKind.Glow -> view.elevation = originalElevation + amount * density * (GLOW_MIN + (1f - GLOW_MIN) * wave * wave) / 2f
        }
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = corner
    }

    private fun withAlpha(color: Int, alpha: Float): Int = (color and RGB_MASK) or ((alpha * FULL_ALPHA).toInt() shl ALPHA_SHIFT)

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
        const val RGB_MASK = 0x00FFFFFF
        const val ALPHA_SHIFT = 24
    }
}

/**
 * View의 눌림 상태를 알려 주는 보이지 않는 foreground 층. 클릭할 수 있는 View는 누르는 동안 drawable 상태에 pressed가 들어오고,
 * 누른 자리는 hotspot으로 들어온다.
 */
private class PressWatcher(private val onPress: (Boolean, Float, Float) -> Unit) : Drawable() {
    private var down = false
    private var x = 0f
    private var y = 0f

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val pressed = android.R.attr.state_pressed in state
        if (pressed != down) {
            down = pressed
            onPress(pressed, x, y)
        }
        return false
    }

    override fun setHotspot(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    override fun draw(canvas: Canvas) = Unit

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Drawable 계약상 구현해야 한다.", ReplaceWith("PixelFormat.TRANSPARENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSPARENT
}

/** View foreground로 지나가는 빛 띠. */
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
