package com.naury.kinetica.ui.view

import android.graphics.Canvas
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.naury.kinetica.ui.GlassBackdrop
import com.naury.kinetica.ui.rememberGlassBackdrop

/**
 * View 계층에서 [target] 뒤에 그려지는 것들을 기록한 배경. [target] 안의 Compose 글래스가 이 배경을 블러 처리해 비춘다.
 *
 * 기록 원점은 [target]의 왼쪽 위다. [target]이 글래스를 담은 Compose 루트이므로 [GlassBackdrop.rootRelative]를 켜서 루트 좌표로 맞춘다.
 * 뒤에 [SurfaceView]가 있으면 [hasSurface]가 `true`가 된다. SurfaceView 내용은 창과 다른 표면에 그려져 기록되지 않으므로,
 * 이때는 블러 대신 fallback 색을 칠하도록 [usable]이 `null`을 돌려준다.
 */
@Stable
internal class ViewBackdrop(val backdrop: GlassBackdrop) {
    init {
        backdrop.rootRelative = true
    }

    var hasSurface by mutableStateOf(false)
        private set

    /** 글래스에 넘길 배경. SurfaceView가 뒤에 있으면 `null`이다. */
    val usable: GlassBackdrop? get() = if (hasSurface) null else backdrop

    private var treeChanged = true

    fun markTreeChanged() {
        treeChanged = true
    }

    /** [target] 뒤의 View들을 다시 기록한다. 메인 스레드에서, 레이아웃이 끝난 뒤(onPreDraw)에 부른다. */
    fun record(target: View, density: Density, direction: LayoutDirection) {
        if (target.width <= 0 || target.height <= 0) return
        if (treeChanged) {
            treeChanged = false
            val surface = viewsBehind(target).any { it.containsSurface() }
            if (surface != hasSurface) hasSurface = surface
        }
        backdrop.layer.record(density, direction, IntSize(target.width, target.height)) {
            drawIntoCanvas { drawViewsBehind(target, it.nativeCanvas) }
        }
    }
}

/**
 * [target] 뒤에 그려지는 View들을 매 프레임 기록하는 [ViewBackdrop]을 만든다. [target]은 이 컴포지션을 담은 ComposeView나 그 조상이어야 한다.
 */
@Composable
internal fun rememberViewBackdrop(target: View): ViewBackdrop {
    val glassBackdrop = rememberGlassBackdrop()
    val state = remember(glassBackdrop) { ViewBackdrop(glassBackdrop) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    DisposableEffect(target, state, density, direction) {
        val registration = BackdropRegistration(target, state, density, direction)
        // RecyclerView 풀에 들어간 항목처럼 창에서 떨어져도 컴포지션이 살아 있을 수 있다. 떨어진 동안에는 기록을 멈춘다.
        target.addOnAttachStateChangeListener(registration)
        if (target.isAttachedToWindow) registration.onViewAttachedToWindow(target)
        onDispose {
            target.removeOnAttachStateChangeListener(registration)
            registration.onViewDetachedFromWindow(target)
        }
    }
    return state
}

/** 창에 붙어 있는 동안만 [target]의 ViewTreeObserver에 프레임마다 기록하는 리스너를 단다. */
private class BackdropRegistration(
    private val target: View,
    private val state: ViewBackdrop,
    private val density: Density,
    private val direction: LayoutDirection,
) : View.OnAttachStateChangeListener {
    private var observer: ViewTreeObserver? = null

    // 프레임마다 그리기 직전에 다시 기록한다. 여기서 기록하면 Compose 상태를 바꾸지 않으므로 다음 프레임을 또 부르지 않는다.
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        state.record(target, density, direction)
        true
    }
    private val layout = ViewTreeObserver.OnGlobalLayoutListener { state.markTreeChanged() }

    override fun onViewAttachedToWindow(v: View) {
        if (observer != null) return
        val current = target.viewTreeObserver
        current.addOnPreDrawListener(preDraw)
        current.addOnGlobalLayoutListener(layout)
        observer = current
        state.markTreeChanged()
        state.record(target, density, direction)
    }

    override fun onViewDetachedFromWindow(v: View) {
        val current = observer?.takeIf { it.isAlive } ?: target.viewTreeObserver
        current.removeOnPreDrawListener(preDraw)
        current.removeOnGlobalLayoutListener(layout)
        observer = null
    }
}

/** [target]의 조상마다, 그 조상 안에서 [target]을 품은 자식보다 먼저 그려지는 형제 View들. */
internal fun viewsBehind(target: View): List<View> {
    val result = ArrayList<View>()
    var child: View = target
    var parent = target.parent
    while (parent is ViewGroup) {
        for (i in 0 until parent.childCount) {
            val sibling = parent.getChildAt(i)
            if (sibling === child) break
            if (sibling.visibility == View.VISIBLE) result += sibling
        }
        child = parent
        parent = parent.parent
    }
    return result
}

private fun View.containsSurface(): Boolean {
    if (visibility != View.VISIBLE) return false
    if (this is SurfaceView) return true
    if (this !is ViewGroup) return false
    for (i in 0 until childCount) {
        if (getChildAt(i).containsSurface()) return true
    }
    return false
}

/**
 * [target]의 왼쪽 위를 원점으로, [target] 뒤에 그려지는 것(조상의 배경과 앞선 형제)을 [canvas]에 그린다.
 *
 * 하드웨어 캔버스에서는 형제의 자식들이 각자의 RenderNode로 참조되므로, 목록이 스크롤되는 것처럼 자식만 바뀌어도 기록을 다시 하지 않고 반영된다.
 * 형제의 elevation에 따른 그리기 순서와 조상이 onDraw에서 직접 그리는 내용은 반영하지 않는다.
 */
internal fun drawViewsBehind(target: View, canvas: Canvas) {
    val levels = ArrayList<Pair<ViewGroup, View>>()
    var child: View = target
    var parent = target.parent
    while (parent is ViewGroup) {
        levels += parent to child
        child = parent
        parent = parent.parent
    }
    val targetLocation = IntArray(2).also(target::getLocationInWindow)
    val location = IntArray(2)
    for (index in levels.indices.reversed()) {
        val (group, inChain) = levels[index]
        group.getLocationInWindow(location)
        val saved = canvas.save()
        canvas.translate((location[0] - targetLocation[0]).toFloat(), (location[1] - targetLocation[1]).toFloat())
        canvas.clipRect(0, 0, group.width, group.height)
        group.background?.let { background ->
            // View는 처음 그릴 때 배경 크기를 정한다. 그보다 먼저 기록하면 크기가 0이라 배경이 빠지므로 여기서 같은 크기로 맞춘다.
            val bounds = background.bounds
            val sized = bounds.left == 0 && bounds.top == 0
            if (!sized || bounds.right != group.width ||
                bounds.bottom != group.height
            ) {
                background.setBounds(0, 0, group.width, group.height)
            }
            background.draw(canvas)
        }
        canvas.translate(-group.scrollX.toFloat(), -group.scrollY.toFloat())
        // 목록 항목마다 글래스가 있으면 앞선 항목을 모두 그리게 되므로, target과 겹치지 않는 형제는 건너뛴다.
        val left = targetLocation[0] - location[0] + group.scrollX
        val top = targetLocation[1] - location[1] + group.scrollY
        val area = android.graphics.Rect(left, top, left + target.width, top + target.height)
        for (i in 0 until group.childCount) {
            val sibling = group.getChildAt(i)
            if (sibling === inChain) break
            if (sibling.overlaps(area)) drawSibling(canvas, sibling)
        }
        canvas.restoreToCount(saved)
    }
}

/** 부모 좌표의 [area]와 겹치는지. 회전이나 크기 변환이 있으면 정확히 계산하지 않고 겹친다고 본다. */
private fun View.overlaps(area: android.graphics.Rect): Boolean {
    val rotated = rotation != 0f || rotationX != 0f || rotationY != 0f
    val scaled = scaleX != 1f || scaleY != 1f
    if (rotated || scaled) return true
    val l = left + translationX
    val t = top + translationY
    return l < area.right && l + width > area.left && t < area.bottom && t + height > area.top
}

private fun drawSibling(canvas: Canvas, view: View) {
    val empty = view.width <= 0 || view.height <= 0
    if (view.visibility != View.VISIBLE || view.alpha <= 0f || empty) return
    val saved = canvas.save()
    canvas.translate(view.left.toFloat(), view.top.toFloat())
    if (!view.matrix.isIdentity) canvas.concat(view.matrix)
    if (view.alpha < 1f) {
        canvas.saveLayerAlpha(0f, 0f, view.width.toFloat(), view.height.toFloat(), (view.alpha * FULL_ALPHA).toInt())
    }
    // 부모가 RenderNode를 기록할 때처럼 스크롤만큼 옮겨 그린다. 배경은 View가 스스로 스크롤을 되돌려 그린다.
    canvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
    view.draw(canvas)
    canvas.restoreToCount(saved)
}

private const val FULL_ALPHA = 255
