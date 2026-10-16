package com.naury.kinetica.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.GlassStyles
import com.naury.kinetica.ui.R
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.kSurface
import com.naury.kinetica.ui.withoutShadow

/** [KSurfaceView]의 표면 위 글자 색이 바뀌었을 때 호출된다. [color]는 ARGB다. */
public fun interface OnContentColorChangeListener {
    public fun onContentColorChanged(color: Int)
}

/**
 * 자식 View 뒤에 kinetica-ui 표면을 깔아 주는 [FrameLayout]. Compose를 쓰지 않는 XML 화면에서 카드나 바를 글래스나 단색 표면으로 만들 때 쓴다.
 *
 * ```xml
 * <com.naury.kinetica.ui.view.KSurfaceView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:kinetica_surfaceStyle="acrylic"
 *     app:kinetica_cornerRadius="20dp">
 *     <TextView ... />
 * </com.naury.kinetica.ui.view.KSurfaceView>
 * ```
 *
 * 글래스 스타일이면 이 View보다 먼저 그려지는 것, 즉 조상의 배경과 앞선 형제 View를 블러 처리해 비춘다. 단색과 톤 스타일은 뒤를 기록하지 않는다. 스크롤이나 애니메이션으로 뒤가 바뀌면 다음 프레임에 따라 바뀐다.
 * API 30 이하이거나 뒤에 SurfaceView(지도, 영상)가 있으면 블러 대신 스타일의 fallback 색을 칠한다. 조상이 onDraw에서 직접 그린 내용과
 * elevation으로 바뀐 그리기 순서는 반영하지 않는다. 글래스의 바깥 그림자는 그리지 않으며, 자식은 모서리 모양으로 잘린다.
 *
 * XML 속성: `app:kinetica_surfaceStyle`(글래스 `auto`, `liquid`, `acrylic`, `oneUi`, `vision`, `material` 등과 그 `Dark` 변형,
 * 단색 `cleanLight`, `cleanDark`, `solidOneUi`, `solidOneUiDark`, `solidMaterial`, `solidMaterialDark`, 톤 `tonal`, `tonalDark`),
 * `app:kinetica_cornerRadius`. 앱 테마의 `kinetica_surfaceViewStyle`에 스타일을 지정하면 모든 KSurfaceView의 기본값이 된다.
 * 코드로 만들면 auto 스타일과 24dp 모서리를 쓴다.
 *
 * auto 스타일은 다크 모드를 따라 바뀐다. `android:configChanges="uiMode"`로 액티비티를 다시 만들지 않는 화면에서도 바로 바뀌므로,
 * 표면 위 글자 색은 [setOnContentColorChangeListener]로 받아 맞춘다. RecyclerView 항목으로 쓸 수 있으며, 재사용되어 창에서 떨어진
 * 동안에는 뒤를 기록하지 않는다.
 * Compose를 쓰므로 Activity나 Fragment처럼 lifecycle이 있는 창에 붙어 있어야 그려진다.
 */
// 리소스 이름은 모듈 접두사 규칙(kinetica_)을 따라야 해서 styleable 이름이 클래스 이름과 다르다.
@SuppressLint("CustomViewStyleable")
public class KSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.kinetica_surfaceViewStyle,
    defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
    private var styleState by mutableStateOf<SurfaceStyle?>(null)
    private var cornerState by mutableFloatStateOf(DEFAULT_CORNER_DP * resources.displayMetrics.density)
    private val surface = ComposeView(context)
    private var backdrop: ViewBackdrop? = null
    private var contentColorListener: OnContentColorChangeListener? = null
    private var lastContentColor = 0

    /** 마지막으로 그린 스타일. 테스트에서 다크 모드 전환이 반영됐는지 확인할 때 쓴다. */
    internal var drawnStyle: SurfaceStyle? = null
        private set

    /** 뒤에 SurfaceView가 있어 블러 대신 fallback 색을 칠하는 중인지. */
    internal val usesFallback: Boolean get() = backdrop?.hasSurface == true

    /** 표면 스타일. `null`이면 시스템 다크 모드에 따라 [SurfaceStyles.Liquid]나 [SurfaceStyles.LiquidDark]를 쓴다. */
    public var surfaceStyle: SurfaceStyle?
        get() = styleState
        set(value) {
            styleState = value
            notifyContentColor()
        }

    /** 모서리 반경(px). 음수면 [IllegalArgumentException]을 던진다. */
    public var cornerRadius: Float
        get() = cornerState
        set(value) {
            require(value >= 0f) { "cornerRadius는 0 이상이어야 합니다: $value" }
            cornerState = value
            invalidateOutline()
        }

    /** 글래스 위에 올릴 글자와 아이콘에 어울리는 색(ARGB). 지금 스타일의 [SurfaceStyle.contentColor] 값이다. */
    public val contentColor: Int
        get() = resolvedStyle(isNight()).contentColor.toArgb()

    init {
        context.obtainStyledAttributes(attrs, R.styleable.KineticaSurfaceView, defStyleAttr, defStyleRes).apply {
            try {
                styleState = styleOf(getInt(R.styleable.KineticaSurfaceView_kinetica_surfaceStyle, 0))
                if (hasValue(R.styleable.KineticaSurfaceView_kinetica_cornerRadius)) {
                    cornerRadius = getDimension(R.styleable.KineticaSurfaceView_kinetica_cornerRadius, cornerState)
                }
            } finally {
                recycle()
            }
        }
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerState)
            }
        }
        clipToOutline = true
        surface.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        surface.setContent { Surface() }
        // XML의 자식들은 생성자가 끝난 뒤 붙으므로, 맨 앞에 넣은 글래스가 늘 그 뒤에 그려진다.
        addView(surface, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        lastContentColor = contentColor
    }

    /**
     * 표면 위 글자 색([contentColor])이 바뀌면 [listener]를 부른다. [surfaceStyle]을 바꿨을 때와, auto 스타일에서 다크 모드가 바뀌었을 때
     * 부른다. 등록할 때 한 번 바로 부르지는 않는다. `null`이면 해제한다.
     */
    public fun setOnContentColorChangeListener(listener: OnContentColorChangeListener?) {
        contentColorListener = listener
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        notifyContentColor()
    }

    private fun notifyContentColor() {
        val color = contentColor
        if (color == lastContentColor) return
        lastContentColor = color
        contentColorListener?.onContentColorChanged(color)
    }

    @Composable
    private fun Surface() {
        val style = resolvedStyle(isSystemInDarkTheme())
        drawnStyle = style
        // 단색과 톤 표면은 비출 뒤가 없으므로 프레임마다 뒤를 기록하지 않는다.
        val viewBackdrop = if (style is SurfaceStyle.Glass) rememberViewBackdrop(surface) else null
        backdrop = viewBackdrop
        // 바깥 그림자는 이 View 경계에 잘려 보이지 않으므로 그리지 않는다. 크기를 정하지 않은 Box는 최소 크기를 따르므로
        // wrap_content일 때는 다른 자식이 크기를 정하고, FrameLayout이 다시 잴 때 그 크기를 꽉 채운다.
        Box(Modifier.kSurface(style.withoutShadow(), RoundedCornerShape(cornerState), viewBackdrop?.usable))
    }

    private fun resolvedStyle(night: Boolean): SurfaceStyle = styleState ?: if (night) SurfaceStyles.LiquidDark else SurfaceStyles.Liquid

    private fun isNight(): Boolean = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    private companion object {
        const val DEFAULT_CORNER_DP = 24f

        /** XML의 `kinetica_surfaceStyle` 값에 맞는 스타일. 0(auto)이면 `null`이다. */
        fun styleOf(value: Int): SurfaceStyle? = STYLES.getOrNull(value - 1)

        val STYLES: List<SurfaceStyle> = listOf(
            GlassStyles.Liquid,
            GlassStyles.LiquidDark,
            GlassStyles.Acrylic,
            GlassStyles.AcrylicDark,
            GlassStyles.OneUi,
            GlassStyles.OneUiDark,
            GlassStyles.Vision,
            GlassStyles.Material,
            GlassStyles.MaterialDark,
            GlassStyles.Frosted,
            GlassStyles.Mica,
            GlassStyles.MicaDark,
            GlassStyles.HarmonyOs,
            GlassStyles.Obsidian,
            GlassStyles.Pastel,
            GlassStyles.Clear,
        ).map { SurfaceStyle.Glass(it) } + listOf(
            SurfaceStyles.CleanLight,
            SurfaceStyles.CleanDark,
            SurfaceStyles.OneUi,
            SurfaceStyles.OneUiDark,
            SurfaceStyles.Material,
            SurfaceStyles.MaterialDark,
            SurfaceStyles.Tonal,
            SurfaceStyles.TonalDark,
        )
    }
}
