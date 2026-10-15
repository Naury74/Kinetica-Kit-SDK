package com.naury.kinetica.ui.view

import android.app.Activity
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.lifecycle.LifecycleOwner
import com.naury.kinetica.ui.GlassStyle

/**
 * XML 화면에서 띄우는 글래스 바텀 시트와 다이얼로그의 설정.
 *
 * @property style 글래스 스타일. `null`이면 시스템 다크 모드에 따라 Liquid나 LiquidDark를 쓴다.
 * @property title 창 이름. TalkBack이 창 제목으로 읽는다.
 * @property cancelable `false`면 바깥을 누르거나, 아래로 끌거나, 뒤로 가기를 해도 닫히지 않는다. 뒤로 가기는 그래도 소비한다.
 *   이때는 [GlassPopupHandle.dismiss]로만 닫는다.
 * @property lifecycleOwner 이 lifecycle이 DESTROYED가 되면 애니메이션 없이 닫는다. Fragment에서 띄울 때 `viewLifecycleOwner`를 넘기면
 *   Fragment가 교체될 때 시트가 남지 않는다. `null`이면 액티비티 lifecycle만 따른다.
 */
public class GlassPopupOptions @JvmOverloads constructor(
    public val style: GlassStyle? = null,
    public val title: CharSequence? = null,
    public val cancelable: Boolean = true,
    public val lifecycleOwner: LifecycleOwner? = null,
)

/** 글래스 시트나 다이얼로그가 닫혔을 때 호출된다. */
public fun interface OnGlassDismissListener {
    public fun onDismiss()
}

/**
 * [GlassSheets.show]나 [GlassDialogs.show]로 띄운 창을 다루는 핸들. 메인 스레드에서만 쓴다.
 *
 * 닫히는 경우는 셋이다. 사용자가 바깥을 누르거나 끌어내리거나 뒤로 가기를 한 경우와 [dismiss]를 부른 경우에는
 * 닫힘 애니메이션을 보여 주고 [OnGlassDismissListener]를 한 번 호출한다. 액티비티나 [GlassPopupOptions.lifecycleOwner]가 끝난 경우에는
 * 애니메이션과 리스너 없이 바로 정리한다. 화면 회전으로 액티비티가 다시 만들어질 때도 이렇게 닫히므로, 회전 뒤에도 열어 두려면
 * `onSaveInstanceState`에 열림 여부를 저장해 두었다가 새 액티비티의 `onCreate`에서 다시 띄운다.
 */
public class GlassPopupHandle internal constructor(contentView: View) {
    internal var host: GlassViewHost? = null
    internal var entry: PopupEntry? = null
    private var listener: OnGlassDismissListener? = null
    private var held: View? = contentView

    /** 창 안에 넣은 View. layout 리소스로 띄웠다면 inflate된 View다. 창이 정리되면 `null`이 된다. */
    public val contentView: View? get() = held

    /** 열려 있고 닫히는 중이 아니면 `true`. */
    public val isShowing: Boolean get() = entry?.let { !it.closing } ?: false

    public fun setOnDismissListener(listener: OnGlassDismissListener?) {
        this.listener = listener
    }

    /** 닫힘 애니메이션과 함께 닫는다. 이미 닫혔거나 닫히는 중이면 아무 일도 하지 않는다. */
    public fun dismiss() {
        checkMainThread()
        val current = entry ?: return
        if (current.closing) return
        host?.close(current)
        listener?.onDismiss()
    }

    /** 애니메이션 없이 정리하고 참조를 모두 놓는다. 리스너는 부르지 않는다. */
    internal fun release() {
        entry = null
        host = null
        listener = null
        held = null
    }
}

/**
 * XML(View) 화면에서 Compose 코드 없이 글래스 바텀 시트를 띄운다.
 *
 * 시트는 액티비티의 `android.R.id.content` 위에 겹친 레이어에 그려지고, 그 아래 View들을 블러 처리해 비춘다(API 31 이상).
 * API 30 이하이거나 뒤에 SurfaceView(지도, 영상)가 있으면 블러 대신 스타일의 fallback 색을 칠한다.
 * 시트 안 View의 터치, 스크롤, 키보드 입력은 평소처럼 동작하고, 입력창에 포커스가 가면 시트가 키보드 위로 올라온다.
 * 뒤로 가기는 액티비티의 OnBackPressedDispatcher로 받으며, 시트를 연 뒤에 등록한 콜백이 있으면 그 콜백이 먼저 받는다.
 *
 * ```kotlin
 * val sheet = GlassSheets.show(this, R.layout.sheet_share, GlassPopupOptions(title = "공유"))
 * sheet.contentView?.findViewById<Button>(R.id.close)?.setOnClickListener { sheet.dismiss() }
 * ```
 * ```java
 * GlassPopupHandle sheet = GlassSheets.show(this, R.layout.sheet_share);
 * ```
 *
 * [activity]는 `androidx.activity.ComponentActivity`(AppCompatActivity, FragmentActivity 포함)여야 하며, 아니면 [IllegalArgumentException]을 던진다.
 * Fragment에서는 `requireActivity()`를 넘기고 [GlassPopupOptions.lifecycleOwner]에 `viewLifecycleOwner`를 준다.
 * 메인 스레드가 아니면 [IllegalStateException]을 던진다. 이미 끝난 액티비티에 띄우면 아무것도 보이지 않고, 돌려받은 핸들은 닫힌 상태다.
 */
public object GlassSheets {
    /** [contentView]를 담은 시트를 띄운다. [contentView]에 이미 부모가 있으면 [IllegalArgumentException]을 던진다. */
    @JvmStatic
    @JvmOverloads
    public fun show(activity: Activity, contentView: View, options: GlassPopupOptions = GlassPopupOptions()): GlassPopupHandle =
        showPopup(activity, contentView, options, PopupKind.Sheet)

    /** [layoutRes]를 inflate해 시트를 띄운다. inflate된 View는 [GlassPopupHandle.contentView]로 얻는다. */
    @JvmStatic
    @JvmOverloads
    public fun show(activity: Activity, @LayoutRes layoutRes: Int, options: GlassPopupOptions = GlassPopupOptions()): GlassPopupHandle =
        showPopup(activity, inflate(activity, layoutRes), options, PopupKind.Sheet)
}

/**
 * XML(View) 화면에서 Compose 코드 없이 글래스 다이얼로그를 띄운다. 화면 가운데에서 튀어나오듯 커지고, 그 아래 화면은 서서히 흐려진다.
 * 블러, 뒤로 가기, 회전, 예외 조건은 [GlassSheets]와 같다.
 *
 * ```kotlin
 * val dialog = GlassDialogs.show(this, R.layout.dialog_confirm, GlassPopupOptions(title = "삭제할까요?"))
 * ```
 */
public object GlassDialogs {
    /** [contentView]를 담은 다이얼로그를 띄운다. [contentView]에 이미 부모가 있으면 [IllegalArgumentException]을 던진다. */
    @JvmStatic
    @JvmOverloads
    public fun show(activity: Activity, contentView: View, options: GlassPopupOptions = GlassPopupOptions()): GlassPopupHandle =
        showPopup(activity, contentView, options, PopupKind.Dialog)

    /** [layoutRes]를 inflate해 다이얼로그를 띄운다. inflate된 View는 [GlassPopupHandle.contentView]로 얻는다. */
    @JvmStatic
    @JvmOverloads
    public fun show(activity: Activity, @LayoutRes layoutRes: Int, options: GlassPopupOptions = GlassPopupOptions()): GlassPopupHandle =
        showPopup(activity, inflate(activity, layoutRes), options, PopupKind.Dialog)
}

/**
 * XML(View) 화면 위쪽에 글래스 토스트를 띄운다. 여러 번 부르면 앞선 토스트가 사라진 뒤 차례로 보여 준다.
 * 토스트는 터치를 가로채지 않고, TalkBack이 내용을 읽어 준다. 액티비티가 끝나면 남은 토스트는 버린다.
 *
 * ```kotlin
 * GlassToasts.show(this, "저장했습니다")
 * ```
 */
public object GlassToasts {
    /**
     * [text]를 [durationMs](ms) 동안 보여 준다. [durationMs]가 0 이하이면 [IllegalArgumentException]을,
     * 메인 스레드가 아니면 [IllegalStateException]을 던진다. 이미 끝난 액티비티에서는 아무 일도 하지 않는다.
     * 스타일은 시스템 다크 모드에 따라 Liquid나 LiquidDark를 쓴다.
     */
    @JvmStatic
    @JvmOverloads
    public fun show(activity: Activity, text: CharSequence, durationMs: Long = DEFAULT_TOAST_MS) {
        checkMainThread()
        require(durationMs > 0) { "durationMs는 0보다 커야 합니다: $durationMs" }
        if (activity.isFinishing || activity.isDestroyed) return
        GlassViewHost.obtain(activity).showToast(text.toString(), durationMs)
    }
}

internal enum class PopupKind { Sheet, Dialog }

private fun showPopup(activity: Activity, contentView: View, options: GlassPopupOptions, kind: PopupKind): GlassPopupHandle {
    checkMainThread()
    require(contentView.parent == null) { "contentView에 이미 부모가 있습니다. 부모에서 떼어 낸 뒤 넘겨 주세요." }
    val handle = GlassPopupHandle(contentView)
    if (activity.isFinishing || activity.isDestroyed) {
        handle.release()
        return handle
    }
    GlassViewHost.obtain(activity).open(PopupEntry(kind, contentView, options, handle))
    return handle
}

/** XML의 layout_width, layout_height를 살리도록 임시 부모를 주고 inflate한다. */
private fun inflate(activity: Activity, @LayoutRes layoutRes: Int): View =
    LayoutInflater.from(activity).inflate(layoutRes, FrameLayout(activity), false)

internal fun checkMainThread() {
    check(Looper.myLooper() == Looper.getMainLooper()) { "글래스 창은 메인 스레드에서만 다룰 수 있습니다." }
}

private const val DEFAULT_TOAST_MS = 2_200L
