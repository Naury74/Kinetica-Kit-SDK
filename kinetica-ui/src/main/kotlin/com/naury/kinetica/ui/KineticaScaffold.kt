package com.naury.kinetica.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * kinetica-ui 화면의 기본 골격. 상단·하단 바([topBar], [bottomBar])와 이 안에서 연 다이얼로그, 바텀 시트, 토스트를 한 오버레이 레이어에 띄운다.
 * 콘텐츠는 바 아래까지 그려지므로 스크롤하면 바 뒤로 지나가는 모습이 보이고, 바 높이는 [content]가 받는 패딩으로 전달된다.
 *
 * [surface]가 글래스면 화면을 배경으로 기록해 두고 바와 오버레이가 그 배경을 블러 처리해 비춰 보이게 한다. [content] 안의 카드 같은
 * 글래스 요소는 [background]를 비춘다. 단색이나 톤 표면이면 배경을 기록하지 않아 그 비용이 없고, [SurfaceStyle.background]를 바탕에 칠한다.
 *
 * 바텀 시트가 열려 있으면 콘텐츠가 살짝 뒤로 물러나며 모서리가 둥글어진다. [surface]는 이 안의 컴포넌트가 쓰는 기본 표면이고,
 * 기본값은 [KineticaTheme]이 정한 표면(없으면 [SurfaceStyles.Liquid])이다.
 *
 * ```kotlin
 * KineticaScaffold(surface = SurfaceStyles.Liquid, background = { Image(...) }, bottomBar = { KTabBar(...) }) { padding ->
 *     LazyColumn(contentPadding = padding) { ... }
 * }
 * ```
 *
 * @param background 콘텐츠 뒤에 깔리는 배경. 글래스 표면이면 사진이나 그라디언트처럼 비칠 색이 있어야 효과가 살아난다.
 * @param backdropColor 바텀 시트가 열려 콘텐츠가 물러날 때 드러나는 배경색. 콘텐츠가 절반 넘게 물러나면 이 색이 상태 표시줄 뒤에 깔리므로,
 * 그동안 상태 표시줄 아이콘을 이 색의 밝기에 맞추고(어두운 색이면 밝은 아이콘) 시트가 내려가면 되돌린다. [systemBarIcons]와 상관없이 적용하며,
 * 투명에 가까운 색(알파 0.5 미만)이면 무엇이 비칠지 알 수 없어 건드리지 않는다.
 * @param edgeFade true면 스크롤된 콘텐츠가 상태 표시줄 아래에서 흐려지며 사라진다. 단색과 톤 표면에서는 바탕색으로 덮어 사라지게 한다.
 * @param indication 이 안에서 쓴 일반 `clickable`의 눌림 표시(`LocalIndication`). 기본값은 [KineticaTheme]의 `indication`이고
 * 테마가 없으면 [KIndication]이다. null이면 바깥(호스트) 값을 그대로 둔다.
 * @param systemBarIcons true면 상태 표시줄과 내비게이션 바 아이콘을 [surface] 밝기에 맞춘다(어두운 표면이면 밝은 아이콘).
 * 기본값은 단색과 톤 표면에서만 true다. 글래스는 호스트 배경이 비치므로 아이콘은 호스트가 정한다.
 */
@Composable
public fun KineticaScaffold(
    modifier: Modifier = Modifier,
    surface: SurfaceStyle = KineticaTheme.surface,
    background: @Composable BoxScope.() -> Unit = {},
    topBar: @Composable BoxScope.() -> Unit = {},
    bottomBar: @Composable BoxScope.() -> Unit = {},
    backdropColor: Color = Color.Black,
    edgeFade: Boolean = true,
    indication: Indication? = LocalKineticaIndication.current,
    systemBarIcons: Boolean = surface !is SurfaceStyle.Glass,
    content: @Composable (PaddingValues) -> Unit,
) {
    SystemBarIcons(if (systemBarIcons) surface.isDark else null)
    // 배경 기록은 세 겹이다(배경 ⊂ 배경과 콘텐츠 ⊂ 바까지 포함한 화면 전체). 글래스는 자기를 포함하지 않는 겹을 비춰야
    // 자기 자신을 다시 그리지 않는다. 그래서 콘텐츠 안 글래스는 배경을, 바는 배경과 콘텐츠를, 오버레이는 화면 전체를 비춘다.
    // 단색과 톤 표면은 비출 것이 없으므로 기록용 레이어를 만들지 않는다.
    val layers = if (surface is SurfaceStyle.Glass) rememberScaffoldBackdrops() else null
    // 콘텐츠 안 카드와 버튼은 모두 같은 배경을 비추므로 블러를 한 번만 하고 함께 쓴다.
    layers?.let { ShareBlurs(it.background) }
    val host = remember { KOverlayHost() }
    PushBackBarIcons(host, backdropColor)
    var topHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalGlassBackdrop provides layers?.screen,
        LocalKineticaHost provides host,
        LocalKineticaSurface provides surface,
        LocalGlassStyle provides surface.glassStyle(),
        LocalKineticaContentColor provides surface.contentColor,
        LocalIndication provides (indication ?: LocalIndication.current),
    ) {
        Box(modifier) {
            Box(Modifier.fillMaxSize().recordInto(layers?.screen).modalBackground(host.background, host.modalOpen)) {
                Box(Modifier.fillMaxSize().recordInto(layers?.content).background(backdropColor)) {
                    PushBackBox(host) {
                        ScaffoldBackground(host.takeIf { layers != null }, layers?.background, surface, background)
                        CompositionLocalProvider(LocalGlassBackdrop provides layers?.background) {
                            with(density) { content(PaddingValues(top = topHeight.toDp(), bottom = bottomHeight.toDp())) }
                        }
                    }
                }
                if (edgeFade) EdgeFade(host, layers?.content, surface.background, Modifier.align(Alignment.TopCenter))
                CompositionLocalProvider(LocalGlassBackdrop provides layers?.content, LocalTopBarSlot provides true) {
                    Box(
                        Modifier.align(Alignment.TopCenter).onSizeChanged {
                            topHeight = it.height
                            host.topBarPx = it.height
                        },
                        content = topBar,
                    )
                }
                CompositionLocalProvider(LocalGlassBackdrop provides layers?.content) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).onSizeChanged {
                            bottomHeight = it.height
                            host.bottomBarPx = it.height
                        },
                        content = bottomBar,
                    )
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

/**
 * 스캐폴드 배경 칸. 글래스 표면이면 모달이 열린 동안 뒤 화면이 블러로 덮이므로, 움직이는 배경이 그동안 멈춰
 * 블러를 프레임마다 다시 하지 않도록 [host]를 알린다.
 */
@Composable
private fun ScaffoldBackground(
    host: KOverlayHost?,
    backdrop: GlassBackdrop?,
    surface: SurfaceStyle,
    background: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalBackgroundHost provides host) {
        Box(Modifier.fillMaxSize().background(surface.background).recordInto(backdrop), content = background)
    }
}

/**
 * 시트가 콘텐츠를 [PUSH_ICON_THRESHOLD]보다 많이 밀어내면 상태 표시줄 뒤에 [backdropColor]가 드러나므로, 그동안 상태 표시줄 아이콘을
 * 그 색의 밝기에 맞춘다. 경계를 넘을 때만 다시 구성되도록 참/거짓으로 줄여 읽는다.
 */
@Composable
private fun PushBackBarIcons(host: KOverlayHost, backdropColor: Color) {
    val pushed by remember(host) { derivedStateOf { host.pushBack > PUSH_ICON_THRESHOLD } }
    val opaque = backdropColor.alpha >= OPAQUE_BACKDROP
    SystemBarIcons(if (pushed && opaque) backdropColor.luminance() < DARK_LUMINANCE else null, statusOnly = true)
}

/** 글래스 스캐폴드가 기록하는 세 겹의 배경. */
private class ScaffoldBackdrops(val screen: GlassBackdrop, val content: GlassBackdrop, val background: GlassBackdrop)

@Composable
private fun rememberScaffoldBackdrops(): ScaffoldBackdrops {
    val screen = rememberGlassBackdrop()
    val content = rememberGlassBackdrop()
    val background = rememberGlassBackdrop()
    return remember(screen, content, background) { ScaffoldBackdrops(screen, content, background) }
}

private fun Modifier.recordInto(backdrop: GlassBackdrop?): Modifier = if (backdrop != null) glassBackdrop(backdrop) else this

/**
 * [KineticaTheme]이 정한 눌림 표시. [KineticaScaffold]가 기본값으로 따른다. null이면 테마가 호스트 값을 그대로 두라고 한 것이다.
 * 테마 밖에서는 [KIndication]이다.
 */
public val LocalKineticaIndication: androidx.compose.runtime.ProvidableCompositionLocal<Indication?> =
    staticCompositionLocalOf { KIndication() }

/**
 * 시트가 열린 정도만큼 [content]를 뒤로 물린다. 축소는 그리기 단계에서만 해서 안쪽 글래스의 화면 좌표가 바뀌지 않게 한다.
 * 물러나는 정도가 바뀌는 동안에는 안쪽을 변환 없는 레이어에 두고 화면 밖 버퍼로 그린다. 그러면 배율이 프레임마다 바뀌어도 안쪽 글자와
 * 그림자를 새 배율로 다시 래스터화하지 않고 버퍼 하나를 늘려 그린다. Fold7에서는 이 재래스터화로 여는 프레임마다 텍스처 업로드가 15~25번 생겼다.
 * 다 열려 멈추면 버퍼를 놓는다. 영상처럼 멈출 수 없는 배경이면 버퍼를 프레임마다 다시 채우는 비용만 더해지기 때문이다
 * (Fold7 호스트 앱에서 시트를 연 채 중간값 8ms가 11~13ms로 늘었다). 배율이 그대로면 다시 래스터화할 일도 없다.
 */
@Composable
private fun PushBackBox(host: KOverlayHost, content: @Composable BoxScope.() -> Unit) {
    LaunchedEffect(host) {
        snapshotFlow { host.pushBack to host.modalOpen }.collectLatest { (push, modal) ->
            delay(PUSH_SETTLE_MS)
            host.buffer.settle(push, modal)
        }
    }
    Box(Modifier.fillMaxSize().pushBack(host)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                // 모달이 열리는 첫 프레임에 버퍼로 바꾸면 그 프레임에는 시트 판이 빈 버퍼를 비출 수 있어, 모달이 붙는 순간(아직 물러나기 전) 바꾼다.
                val buffered = host.buffer.use(host.pushBack, host.modalOpen)
                compositingStrategy = if (buffered) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            },
            content = content,
        )
    }
}

/**
 * 뒤로 물러난 콘텐츠를 화면 밖 버퍼로 그릴지 정한다. 모달이 붙었거나 물러나 있는 동안, 마지막으로 멈춘 값과 지금 값이 다르면 버퍼를 쓴다.
 * 값을 [settle]로 기록하면 같은 값인 동안은 버퍼를 쓰지 않는다.
 */
@Stable
internal class PushBuffer {
    private var restPush by mutableFloatStateOf(-1f)
    private var restModal by mutableStateOf(false)

    fun use(push: Float, modal: Boolean): Boolean = (modal || push > 0f) && (push != restPush || modal != restModal)

    fun settle(push: Float, modal: Boolean) {
        restPush = push
        restModal = modal
    }
}

/** 시트가 열린 정도만큼 콘텐츠를 살짝 줄이고 내려 둥글게 자른다. 그리기 단계에서 읽어 다시 컴포지션하지 않는다. */
private fun Modifier.pushBack(host: KOverlayHost): Modifier = drawWithCache {
    val clip = Path()
    onDrawWithContent {
        val push = host.pushBack
        if (push <= 0f) {
            drawContent()
            return@onDrawWithContent
        }
        val corner = PUSH_CORNER.toPx() * push
        clip.reset()
        clip.addRoundRect(RoundRect(size.toRect(), CornerRadius(corner)))
        translate(top = PUSH_DROP.toPx() * push) {
            scale(1f - PUSH_SCALE * push) { clipPath(clip) { this@onDrawWithContent.drawContent() } }
        }
    }
}

/**
 * 상태 표시줄 아래로 들어간 콘텐츠를 흐리게 사라지게 한다. 글래스면 블러 처리하고, 아니면 바탕색으로 덮는다. 아래로 갈수록 옅어진다.
 */
@Composable
private fun EdgeFade(host: KOverlayHost, backdrop: GlassBackdrop?, page: Color, modifier: Modifier) {
    if (backdrop == null && page.alpha == 0f) return
    if (host.statusBarCovered) return
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
        if (backdrop != null) {
            Box(
                Modifier.matchParentSize().glass(
                    GlassStyles.Clear.copy(blur = EDGE_BLUR, fallback = Color.Transparent),
                    backdrop = backdrop,
                ),
            )
        } else {
            Box(Modifier.matchParentSize().background(page.copy(alpha = page.alpha * EDGE_PAGE_ALPHA)))
        }
    }
}

/** [Modifier.glass]가 기본으로 쓰는 글래스 스타일. 글래스 표면이면 그 스타일이, 아니면 표면 밝기에 맞춘 Liquid 스타일이 들어간다. 테마가 없으면 [GlassStyles.Liquid]다. */
public val LocalGlassStyle: androidx.compose.runtime.ProvidableCompositionLocal<GlassStyle> =
    staticCompositionLocalOf { GlassStyles.Liquid }

/** 오버레이가 화면에서 하는 역할. [KineticaScaffold] 밖에서 어떤 창으로 띄울지가 여기에 따라 갈린다. */
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

internal val LocalKineticaHost = staticCompositionLocalOf<KOverlayHost?> { null }

/** 글래스 [KineticaScaffold]의 background 칸 안이면 그 스캐폴드의 오버레이 호스트. */
internal val LocalBackgroundHost = staticCompositionLocalOf<KOverlayHost?> { null }

/**
 * 글래스 [KineticaScaffold]의 `background` 안에서 부르면, 그 스캐폴드에 다이얼로그나 시트가 열려 있는 동안 true다. 그 밖에서는 늘 false다.
 *
 * 모달이 열린 동안 뒤 화면은 스크림 블러와 판 블러가 덮는다. 이때 배경이 한 프레임이라도 바뀌면 화면 크기 블러와 판 블러를 모두 다시 해야 하므로,
 * 직접 만든 움직이는 배경은 이 값이 true인 동안 애니메이션을 멈춘다. [MeshGradientBackground], [AuroraBackground] 같은 키트 배경은 알아서 멈춘다.
 *
 * ```kotlin
 * KineticaScaffold(background = { MyAnimatedBackground(paused = shouldPauseBackground()) }) { padding -> … }
 * ```
 */
@Composable
public fun shouldPauseBackground(): Boolean = LocalBackgroundHost.current?.modalOpen == true

/** [KineticaScaffold]의 topBar 칸 안이면 true. 상단 바가 상태 표시줄을 덮는다고 알릴 수 있는 자리인지 가린다. */
internal val LocalTopBarSlot = staticCompositionLocalOf { false }

/** [KineticaScaffold]의 오버레이 레이어에 그릴 항목들. 어디서 열든 배경 기록 바깥에 그린다. */
@Stable
internal class KOverlayHost {
    val entries = mutableStateListOf<OverlayEntry>()

    /**
     * 상단 바가 상태 표시줄 영역을 불투명하게 덮고 있는지. 덮고 있으면 그 아래의 가장자리 흐림은 보이지 않으므로 만들지 않는다.
     * 가장자리 흐림은 화면 폭 블러와 화면 밖 레이어를 하나씩 쓰므로 스크롤 내내 그리면 비용이 크다.
     */
    var statusBarCovered by mutableStateOf(false)

    // bottomBar 높이(px). 스낵바가 탭 바를 덮지 않고 그 위에 뜨는 데 쓴다.
    var bottomBarPx by mutableIntStateOf(0)

    // 위쪽 알림(토스트)이 상단 바를 가리지 않도록 그 아래에 띄울 때 쓴다.
    var topBarPx by mutableIntStateOf(0)

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

    /** 물러난 콘텐츠를 화면 밖 버퍼로 그릴지. */
    val buffer = PushBuffer()

    /** 열린 시트들이 콘텐츠를 밀어내는 정도 중 가장 큰 값(0..1). */
    val pushBack: Float get() = entries.maxOfOrNull { it.pushBack } ?: 0f
}

@Stable
internal class OverlayEntry(val id: Any, val modal: Boolean) {
    var content: @Composable () -> Unit by mutableStateOf({})
    var pushBack by mutableFloatStateOf(0f)
}

private const val PUSH_SCALE = 0.07f

// 물러나는 정도가 이 시간(ms) 동안 그대로면 다 열린 것으로 보고 버퍼를 놓는다. 끄는 손가락이 잠깐 멈춘 정도로는 놓지 않을 만큼 둔다.
private const val PUSH_SETTLE_MS = 150L
private const val PUSH_ICON_THRESHOLD = 0.5f
private const val OPAQUE_BACKDROP = 0.5f
private const val DARK_LUMINANCE = 0.5f
private const val EDGE_PAGE_ALPHA = 0.92f
private val EDGE_BLUR = 16.dp
private val EDGE_EXTRA = 12.dp
private val PUSH_DROP = 10.dp
private val PUSH_CORNER = 28.dp
