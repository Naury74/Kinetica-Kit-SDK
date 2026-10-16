package com.naury.kinetica.ui.view

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.naury.kinetica.ui.KDialog
import com.naury.kinetica.ui.KOverlayHost
import com.naury.kinetica.ui.KSheet
import com.naury.kinetica.ui.KToastHost
import com.naury.kinetica.ui.KToastState
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalGlassBackdrop
import com.naury.kinetica.ui.LocalKineticaHost
import com.naury.kinetica.ui.R
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.kSurface
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

private class PendingToast(val text: String, val durationMs: Long)

/** [KViewHost]에 올린 시트나 다이얼로그 하나. */
internal class PopupEntry(val kind: PopupKind, val view: View, val options: KPopupOptions, val handle: KPopupHandle) {
    var closing by mutableStateOf(false)
    var lifecycleObserver: LifecycleEventObserver? = null
}

/**
 * 액티비티 하나에 하나씩, `android.R.id.content` 맨 위에 붙는 오버레이. 열린 글래스 창이나 토스트가 있는 동안만 붙어 있고,
 * 그 아래 View들을 [ViewBackdrop]으로 기록해 글래스가 비추게 한다. 액티비티가 끝나면 모든 창을 정리하고 스스로 떨어진다.
 */
@SuppressLint("ViewConstructor")
internal class KViewHost private constructor(private val activity: ComponentActivity) : FrameLayout(activity) {
    private val entries = mutableStateListOf<PopupEntry>()
    private val toasts = KToastState()
    private val pendingToasts = mutableStateListOf<PendingToast>()
    private val hidden = HashMap<View, Int>()
    private val compose = ComposeView(activity)
    private val content: ViewGroup = activity.findViewById(android.R.id.content)
    private var backdrop: ViewBackdrop? = null
    private var destroyed = false

    /** 뒤에 SurfaceView가 있어 블러 대신 fallback 색을 칠하는 중인지. */
    internal val usesFallback: Boolean get() = backdrop?.hasSurface == true
    private val activityObserver = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroy() }

    // 앱이 android.R.id.content에 Fragment나 View를 나중에 넣으면 오버레이보다 위에 그려진다. 배치가 끝날 때마다 맨 위로 되돌린다.
    private val keepOnTop = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        if (!destroyed && content.indexOfChild(this) != content.childCount - 1) {
            bringToFront()
            updateHiddenSiblings()
        }
    }

    /** 마지막으로 그린, 스타일을 지정하지 않은 창의 기본 스타일. 테스트에서 다크 모드 전환이 반영됐는지 확인할 때 쓴다. */
    internal var drawnFallbackStyle: SurfaceStyle? = null
        private set

    init {
        id = R.id.kinetica_ui_overlay_host
        setViewTreeLifecycleOwner(activity)
        setViewTreeSavedStateRegistryOwner(activity)
        setViewTreeOnBackPressedDispatcherOwner(activity)
        compose.setContent { HostContent() }
        addView(compose, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        content.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        activity.lifecycle.addObserver(activityObserver)
    }

    fun open(entry: PopupEntry) {
        entry.handle.host = this
        entry.handle.entry = entry
        entry.options.lifecycleOwner?.let { owner ->
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) remove(entry) }
            entry.lifecycleObserver = observer
            owner.lifecycle.addObserver(observer)
        }
        entries += entry
        bringToFront()
        updateHiddenSiblings()
    }

    /** 닫힘 애니메이션을 시작한다. 애니메이션이 끝나면 [remove]가 불린다. */
    fun close(entry: PopupEntry) {
        entry.closing = true
        // 창 안 입력란이 포커스를 가진 채 닫히면 키보드가 화면에 남으므로 함께 내린다.
        entry.view.findFocus()?.let { focused ->
            focused.context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(focused.windowToken, 0)
            focused.clearFocus()
        }
        updateHiddenSiblings()
    }

    fun showToast(text: String, durationMs: Long) {
        pendingToasts += PendingToast(text, durationMs)
        bringToFront()
    }

    private fun remove(entry: PopupEntry) {
        entry.lifecycleObserver?.let { entry.options.lifecycleOwner?.lifecycle?.removeObserver(it) }
        entry.lifecycleObserver = null
        entries -= entry
        entry.handle.release()
        updateHiddenSiblings()
        detachIfIdle()
    }

    private fun detachIfIdle() {
        if (entries.isNotEmpty() || pendingToasts.isNotEmpty()) return
        destroy()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(keepOnTop)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(keepOnTop)
        super.onDetachedFromWindow()
        // 앱이 setContentView를 다시 불러 오버레이가 떨어진 경우에도 창을 정리해 핸들이 열린 채로 남지 않게 한다.
        destroy()
    }

    /** 모든 창과 토스트를 버리고 화면에서 떨어진다. 다음에 띄울 때 [obtain]이 새로 만든다. 여러 번 불러도 된다. */
    private fun destroy() {
        if (destroyed) return
        destroyed = true
        activity.lifecycle.removeObserver(activityObserver)
        entries.toList().forEach { entry ->
            entry.lifecycleObserver?.let { entry.options.lifecycleOwner?.lifecycle?.removeObserver(it) }
            entry.handle.release()
        }
        entries.clear()
        pendingToasts.clear()
        restoreHiddenSiblings()
        (parent as? ViewGroup)?.removeView(this)
        compose.disposeComposition()
    }

    /** 모달 창이 열려 있는 동안 아래 화면을 TalkBack에서 숨긴다. 시스템 Dialog 창과 같은 동작이다. */
    private fun updateHiddenSiblings() {
        if (entries.none { !it.closing }) {
            restoreHiddenSiblings()
            return
        }
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child === this || child in hidden) continue
            hidden[child] = child.importantForAccessibility
            child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    private fun restoreHiddenSiblings() {
        hidden.forEach { (view, value) -> view.importantForAccessibility = value }
        hidden.clear()
    }

    @Composable
    private fun HostContent() {
        val viewBackdrop = rememberViewBackdrop(compose)
        backdrop = viewBackdrop
        val night = isSystemInDarkTheme()
        val fallbackStyle = if (night) SurfaceStyles.LiquidDark else SurfaceStyles.Liquid
        drawnFallbackStyle = fallbackStyle
        // 시트와 다이얼로그가 새 창을 띄우지 않고 이 오버레이 안에 그려지도록 KineticaScaffold와 같은 오버레이 레이어를 준다.
        val overlay = remember { KOverlayHost() }
        CompositionLocalProvider(LocalGlassBackdrop provides viewBackdrop.usable, LocalKineticaHost provides overlay) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().focusRequester(overlay.background).focusGroup())
                entries.forEach { entry -> key(entry) { Entry(entry, entry.options.surface ?: fallbackStyle) } }
                KToastHost(toasts, surface = fallbackStyle)
                OverlayLayer(overlay)
            }
        }
        // 토스트는 컴포지션 안에서 차례로 보여 주므로, 오버레이가 떨어지면 남은 토스트도 함께 멈춘다.
        LaunchedEffect(toasts) {
            while (true) {
                val next = snapshotFlow { pendingToasts.firstOrNull() }.filterNotNull().first()
                toasts.show(next.text, next.durationMs)
                pendingToasts.remove(next)
                post { detachIfIdle() }
            }
        }
    }

    @Composable
    private fun OverlayLayer(overlay: KOverlayHost) {
        val topModal = overlay.entries.indexOfLast { it.modal }
        overlay.entries.forEachIndexed { index, item ->
            key(item.id) {
                // 위에 다른 모달이 열려 있으면 아래 창은 TalkBack에서 숨긴다.
                Box(if (index < topModal) Modifier.clearAndSetSemantics { } else Modifier) { item.content() }
            }
        }
    }

    @Composable
    private fun Entry(entry: PopupEntry, surface: SurfaceStyle) {
        val visible = !entry.closing
        val onDismissRequest = { if (entry.options.cancelable && !entry.closing) entry.handle.dismiss() }
        val title = entry.options.title?.toString()
        // 키보드, 내비게이션 바, 컷아웃 여백은 시트와 다이얼로그가 contentWindowInsets로 직접 피한다.
        when (entry.kind) {
            PopupKind.Sheet -> KSheet(visible, onDismissRequest, surface = surface, title = title) {
                HostedView(entry.view, Modifier.fillMaxWidth())
            }

            PopupKind.Dialog -> KDialog(visible, onDismissRequest, surface = surface, title = title) {
                HostedView(entry.view, Modifier)
            }
        }
        if (entry.closing) {
            LaunchedEffect(entry) {
                // 닫힘 애니메이션과 같은 프레임 시계로 기다려야 애니메이션이 끝나기 전에 View를 떼지 않는다.
                val start = withFrameMillis { it }
                while (withFrameMillis { it } - start < REMOVE_AFTER_MS) Unit
                // 컴포지션 안에서 이 ComposeView를 떼어 내지 않도록 다음 메시지로 미룬다.
                post { remove(entry) }
            }
        }
    }

    @Composable
    private fun HostedView(view: View, modifier: Modifier) {
        AndroidView(factory = { view }, modifier = modifier)
    }

    companion object {
        /** [activity]에 붙은 오버레이를 찾거나 새로 붙인다. ComponentActivity가 아니면 [IllegalArgumentException]을 던진다. */
        fun obtain(activity: Activity): KViewHost {
            require(activity is ComponentActivity) {
                "글래스 창은 androidx.activity.ComponentActivity(AppCompatActivity, FragmentActivity 포함)에서만 띄울 수 있습니다."
            }
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            return content.findViewById<KViewHost>(R.id.kinetica_ui_overlay_host)?.takeIf { it.parent === content }
                ?: KViewHost(activity)
        }

        /** 액티비티에 붙은 오버레이. 없으면 `null`이다. 테스트에서 정리 여부를 확인할 때 쓴다. */
        internal fun find(activity: Activity): KViewHost? =
            activity.findViewById<ViewGroup>(android.R.id.content)?.findViewById(R.id.kinetica_ui_overlay_host)

        private const val REMOVE_AFTER_MS = 400L
    }
}
