package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * 글래스 UI 화면의 기본 골격. 화면을 배경으로 기록해 두고, 상단·하단 바([topBar], [bottomBar])와 이 안에서 연 다이얼로그,
 * 바텀 시트, 토스트가 그 배경을 블러 처리해 비춰 보이게 한다. [content] 안의 카드 같은 글래스 요소는 [background]를 비춘다.
 * 콘텐츠는 바 아래까지 그려지므로 스크롤하면 바 뒤로 지나가는 모습이 보이고, 바 높이는 [content]가 받는 패딩으로 전달된다.
 *
 * 바텀 시트가 열려 있으면 iOS 시트처럼 콘텐츠가 살짝 뒤로 물러나며 모서리가 둥글어진다. [style]은 이 안의 글래스 요소가 쓰는 기본 스타일이다.
 *
 * ```kotlin
 * GlassScaffold(style = GlassStyles.Liquid, background = { Image(...) }, bottomBar = { GlassTabBar(...) }) { padding ->
 *     LazyColumn(contentPadding = padding) { ... }
 * }
 * ```
 *
 * @param background 콘텐츠 뒤에 깔리는 배경. 사진이나 그라디언트처럼 비칠 색이 있어야 글래스 효과가 살아난다.
 * @param backdropColor 바텀 시트가 열려 콘텐츠가 물러날 때 드러나는 배경색
 * @param edgeFade true면 스크롤된 콘텐츠가 상태 표시줄 아래에서 iOS처럼 흐려지며 사라진다.
 */
@Composable
public fun GlassScaffold(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyles.Liquid,
    background: @Composable BoxScope.() -> Unit = {},
    topBar: @Composable BoxScope.() -> Unit = {},
    bottomBar: @Composable BoxScope.() -> Unit = {},
    backdropColor: Color = Color.Black,
    edgeFade: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    // 배경 기록은 세 겹이다(배경 ⊂ 배경과 콘텐츠 ⊂ 바까지 포함한 화면 전체). 글래스는 자기를 포함하지 않는 겹을 비춰야
    // 자기 자신을 다시 그리지 않는다. 그래서 콘텐츠 안 글래스는 배경을, 바는 배경과 콘텐츠를, 오버레이는 화면 전체를 비춘다.
    val backdrop = rememberGlassBackdrop()
    val contentBackdrop = rememberGlassBackdrop()
    val backgroundBackdrop = rememberGlassBackdrop()
    val host = remember { GlassOverlayHost() }
    var topHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalGlassHost provides host, LocalGlassStyle provides style) {
        Box(modifier) {
            Box(Modifier.fillMaxSize().glassBackdrop(backdrop).modalBackground(host.background, host.modalOpen)) {
                Box(Modifier.fillMaxSize().glassBackdrop(contentBackdrop).background(backdropColor)) {
                    // graphicsLayer를 만들지 않고 그리기 단계에서만 축소해야 안쪽 글래스가 배경 기록에서 빠진다.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                // 그리기 단계에서 읽어야 시트가 움직이는 동안 콘텐츠를 다시 컴포지션하지 않는다.
                                val push = host.pushBack
                                if (push <= 0f) {
                                    drawContent()
                                    return@drawWithContent
                                }
                                val scale = 1f - PUSH_SCALE * push
                                val corner = PUSH_CORNER.toPx() * push
                                translate(top = PUSH_DROP.toPx() * push) {
                                    scale(scale) {
                                        clipPath(
                                            Path().apply {
                                                addRoundRect(RoundRect(size.toRect(), CornerRadius(corner)))
                                            },
                                        ) { this@drawWithContent.drawContent() }
                                    }
                                }
                            },
                    ) {
                        Box(Modifier.fillMaxSize().glassBackdrop(backgroundBackdrop), content = background)
                        CompositionLocalProvider(LocalGlassBackdrop provides backgroundBackdrop) {
                            with(density) { content(PaddingValues(top = topHeight.toDp(), bottom = bottomHeight.toDp())) }
                        }
                    }
                }
                if (edgeFade) EdgeFade(contentBackdrop, Modifier.align(Alignment.TopCenter))
                CompositionLocalProvider(LocalGlassBackdrop provides contentBackdrop) {
                    Box(Modifier.align(Alignment.TopCenter).onSizeChanged { topHeight = it.height }, content = topBar)
                    Box(Modifier.align(Alignment.BottomCenter).onSizeChanged { bottomHeight = it.height }, content = bottomBar)
                }
            }
            val topModal = host.entries.indexOfLast { it.modal }
            host.entries.forEachIndexed { index, entry ->
                key(entry.id) {
                    // 위에 다른 모달이 열려 있으면 아래 오버레이는 TalkBack에서 숨긴다.
                    Box(if (index < topModal) Modifier.clearAndSetSemantics { } else Modifier) { entry.content() }
                }
            }
        }
    }
}

/** 상태 표시줄 아래로 들어간 콘텐츠를 블러 처리한다. 블러는 아래로 갈수록 옅어진다. */
@Composable
private fun EdgeFade(backdrop: GlassBackdrop, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            // 상단 바가 떠 있는 틈까지 덮도록 상태 표시줄보다 조금 더 내려온다.
            .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + EDGE_EXTRA)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Black, Color.Transparent)), blendMode = BlendMode.DstIn)
            },
    ) {
        Box(Modifier.matchParentSize().glass(GlassStyles.Clear.copy(blur = EDGE_BLUR, fallback = Color.Transparent), backdrop = backdrop))
    }
}

/** 글래스 요소가 기본으로 쓰는 스타일. [GlassScaffold]의 `style`이 여기에 들어간다. */
public val LocalGlassStyle: androidx.compose.runtime.ProvidableCompositionLocal<GlassStyle> =
    staticCompositionLocalOf { GlassStyles.Liquid }

/** 오버레이가 화면에서 하는 역할. [GlassScaffold] 밖에서 어떤 창으로 띄울지가 여기에 따라 갈린다. */
internal enum class OverlayKind {
    /** 화면 전체를 덮고 입력과 접근성 초점을 가져간다. 밖에서는 전체 화면 대화 상자 창에 그린다. */
    Modal,

    /** 위쪽 가장자리에 붙는 알림. 뒤 화면의 입력을 막지 않는다. 밖에서는 내용 크기의 팝업 창에 그린다. */
    Top,

    /** 아래쪽 가장자리에 붙는 알림. 뒤 화면의 입력을 막지 않는다. 밖에서는 내용 크기의 팝업 창에 그린다. */
    Bottom,

    /** 밖에서는 호출한 자리에 그린다. 자기 창을 직접 띄우거나 호출한 자리를 화면으로 보는 요소가 쓴다. */
    InPlace,
}

internal val LocalGlassHost = staticCompositionLocalOf<GlassOverlayHost?> { null }

/** [GlassScaffold]의 오버레이 레이어에 그릴 항목들. 어디서 열든 배경 기록 바깥에 그린다. */
@Stable
internal class GlassOverlayHost {
    val entries = mutableStateListOf<OverlayEntry>()

    /** 뒤 화면 전체를 묶는 초점 그룹. 모달이 닫히면 열기 전에 초점이 있던 요소로 돌아가는 데 쓴다. */
    val background = FocusRequester()
    private var restorePending = false

    val modalOpen: Boolean get() = entries.any { it.modal }

    /** 뒤 화면에 초점이 있었으면 기억하고 true를 돌려준다. 여러 모달이 겹쳐 열리면 처음 것만 기억한다. */
    fun saveBackgroundFocus(): Boolean {
        if (restorePending) return false
        restorePending = background.saveFocusedChild()
        return restorePending
    }

    /** 마지막 모달이 닫히면 기억해 둔 요소로 초점을 돌려준다. */
    fun restoreBackgroundFocus() {
        if (!restorePending || entries.any { it.modal }) return
        restorePending = false
        background.restoreFocusedChild()
    }

    /** 열린 시트들이 콘텐츠를 밀어내는 정도 중 가장 큰 값(0..1). */
    val pushBack: Float get() = entries.maxOfOrNull { it.pushBack } ?: 0f
}

@Stable
internal class OverlayEntry(val id: Any, val modal: Boolean) {
    var content: @Composable () -> Unit by mutableStateOf({})
    var pushBack by mutableFloatStateOf(0f)
}

private const val PUSH_SCALE = 0.07f
private val EDGE_BLUR = 16.dp
private val EDGE_EXTRA = 12.dp
private val PUSH_DROP = 10.dp
private val PUSH_CORNER = 28.dp
