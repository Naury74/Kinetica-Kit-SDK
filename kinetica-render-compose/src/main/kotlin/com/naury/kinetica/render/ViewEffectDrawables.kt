// 조각 수·크기·고리 불투명도 등은 디자인 값이라 이름 붙인 상수로 빼지 않는다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.render

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import com.naury.kinetica.core.scene.LoopEffect
import com.naury.kinetica.core.scene.PressEffect
import kotlin.math.PI
import kotlin.math.sin

/** 한 주기 앞부분에서 두 번 뛰는 크기(0..1). Compose 효과와 같은 모양이다. */
internal fun heartbeatAt(t: Float): Float {
    fun beat(start: Float): Float {
        val x = (t - start) / 0.15f
        return if (x in 0f..1f) sin(PI * x).toFloat() else 0f
    }
    return maxOf(beat(0f), beat(0.22f) * 0.8f)
}

/** 부모 overlay에서 [view] 가운데를 기준으로 사방으로 터지는 조각. */
internal class BurstDrawable(private val effect: PressEffect, private val view: View) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var progress = 0f
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        val p = progress
        if (p <= 0f || p >= 1f) return
        val cx = view.left + view.translationX + view.width / 2f
        val cy = view.top + view.translationY + view.height / 2f
        val short = minOf(view.width, view.height).toFloat()
        val spread = 1f + 1.3f * p
        val rx = view.width / 2f * spread
        val ry = view.height / 2f * spread
        val dot = maxOf(3f * view.resources.displayMetrics.density, short * 0.09f) * (1f - p / 2f)
        val base = effect.color?.argb ?: ACCENT_ARGB
        val light = towardWhite(base, 0.45f)
        val fade = 1f - p * p
        val count = effect.resolvedAmount.toInt()
        repeat(count) { i ->
            val angle = 2 * PI * i / count - PI / 2
            paint.color = if (i % 2 == 0) base else light
            paint.alpha = (fade * android.graphics.Color.alpha(paint.color)).toInt()
            val reach = if (i % 2 == 0) 1f else 0.8f
            canvas.drawCircle(cx + (kotlin.math.cos(angle) * rx * reach).toFloat(), cy + (sin(angle) * ry * reach).toFloat(), dot, paint)
        }
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Drawable 계약상 구현해야 한다.", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** 부모 overlay에서 [view] 둘레 밖으로 퍼지는 고리 두 개. View 안쪽은 가리지 않는다. */
internal class PingDrawable(private val loop: LoopEffect, private val view: View, private val corner: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inner = android.graphics.Path()

    var progress = 0f
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        val left = view.left + view.translationX
        val top = view.top + view.translationY
        val rect = android.graphics.RectF(left, top, left + view.width, top + view.height)
        inner.reset()
        inner.addRoundRect(rect, corner, corner, android.graphics.Path.Direction.CW)
        val distance = loop.resolvedAmount.toFloat() * view.resources.displayMetrics.density
        val color = loop.color?.argb ?: ACCENT_ARGB
        canvas.save()
        canvas.clipOutPath(inner)
        listOf(progress, (progress + 0.5f) % 1f).forEach { phase ->
            val grow = distance * phase
            paint.color = color
            paint.alpha = (android.graphics.Color.alpha(color) * 0.45f * (1f - phase)).toInt()
            val ring = android.graphics.RectF(rect.left - grow, rect.top - grow, rect.right + grow, rect.bottom + grow)
            canvas.drawRoundRect(ring, corner + grow, corner + grow, paint)
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Drawable 계약상 구현해야 한다.", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private const val ACCENT_ARGB = 0xFF5B6CFF.toInt()

/** [color]를 흰색 쪽으로 [amount](0..1)만큼 옮긴다. 불투명도는 그대로다. */
private fun towardWhite(color: Int, amount: Float): Int {
    fun mix(channel: Int) = (channel + (255 - channel) * amount).toInt()
    return android.graphics.Color.argb(
        android.graphics.Color.alpha(color),
        mix(android.graphics.Color.red(color)),
        mix(android.graphics.Color.green(color)),
        mix(android.graphics.Color.blue(color)),
    )
}
