package com.naury.kinetica.render

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.MotionEvent
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
 * - 누름 효과는 View의 터치 리스너로 관찰만 하고 소비하지 않으므로 클릭은 그대로 동작한다. 단, View에 이미 있던 터치 리스너는 교체된다.
 * - 리플·어둡게·반짝임은 View의 foreground로 그린다(View 모양의 사각형 범위). 빛 번짐은 그림자 높이로 표현한다.
 * - View가 화면에서 떨어지면 반복을 멈추고 다시 붙으면 이어 간다. 시스템 애니메이션이 꺼져 있으면 나타나기·반복은 하지 않는다.
 *
 * 메인 스레드에서 부른다.
 */
public object ViewEffects {
    /** [view]에 [effects]를 붙인다. 이미 붙인 효과가 있으면 먼저 떼고 붙인다. */
    @JvmStatic
    public fun apply(view: View, effects: NodeEffects) {
        clear(view)
        if (effects.isEmpty) return
        val attached = AttachedEffects(view, effects)
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

private class AttachedEffects(private val view: View, private val effects: NodeEffects) : View.OnAttachStateChangeListener {
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
            ColorDrawable(android.graphics.Color.WHITE),
        )
    }
    private val dim = kinds[PressKind.Dim]?.let { effect ->
        ColorDrawable(effect.color?.argb ?: android.graphics.Color.BLACK).apply {
            alpha =
                0
        }
    }
    private val shimmer = effects.loop?.takeIf { it.kind == LoopKind.Shimmer && motion }?.let(::ShimmerDrawable)
    private var loop: ValueAnimator? = null

    fun start() {
        val layers = listOfNotNull(originalForeground, ripple, dim, shimmer)
        if (layers.size > (if (originalForeground == null) 0 else 1)) view.foreground = LayerDrawable(layers.toTypedArray())
        if (kinds.isNotEmpty()) watchPress()
        effects.appear?.takeIf { motion }?.let(::appear)
        view.addOnAttachStateChangeListener(this)
        if (view.isAttachedToWindow) startLoop()
    }

    fun stop() {
        view.removeOnAttachStateChangeListener(this)
        loop?.cancel()
        loop = null
        view.animate().cancel()
        if (kinds.isNotEmpty()) view.setOnTouchListener(null)
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

    // 관찰만 하므로 접근성 클릭 처리(performClick)는 View 자신에게 맡긴다.
    @SuppressLint("ClickableViewAccessibility")
    private fun watchPress() {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> pressed(true, event.x, event.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressed(false, event.x, event.y)
            }
            false
        }
    }

    private fun pressed(down: Boolean, x: Float, y: Float) {
        ripple?.setHotspot(x, y)
        ripple?.state = if (down) intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled) else intArrayOf()
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

/** View foreground로 지나가는 빛 띠. */
private class ShimmerDrawable(private val loop: LoopEffect) : Drawable() {
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
        canvas.drawRect(bounds, paint)
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
