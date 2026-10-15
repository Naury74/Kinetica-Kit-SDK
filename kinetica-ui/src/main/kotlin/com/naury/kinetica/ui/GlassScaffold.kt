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
import androidx.compose.ui.unit.dp

/**
 * 유리 UI 화면의 뼈대. 화면을 뒤 화면으로 기록해, 위·아래 막대([topBar]·[bottomBar])와 이 안에서 연 팝업·시트·토스트가
 * 그 위에서 흐리게 비춰 보이게 한다. [content] 안의 유리(카드 등)는 [background]를 비춘다. 내용은 막대 아래까지 그려지며(막대 뒤로 스크롤되어 보임) 막대 높이는 [content]의 여백으로 받는다.
 *
 * 시트가 열려 있으면 내용이 살짝 뒤로 물러나고 둥글어진다(iOS 시트처럼). [style]은 이 안의 유리 요소가 쓰는 기본 모양이다.
 *
 * ```kotlin
 * GlassScaffold(style = GlassStyles.Liquid, background = { Image(...) }, bottomBar = { GlassTabBar(...) }) { padding ->
 *     LazyColumn(contentPadding = padding) { ... }
 * }
 * ```
 *
 * @param background 내용 뒤 바탕(사진·그라디언트). 유리가 비출 색이 있어야 유리답게 보인다.
 * @param backdropColor 시트가 열려 내용이 물러날 때 드러나는 바탕 색
 * @param edgeFade 스크롤된 내용이 상태 표시줄 아래에서 흐려지며 사라지게 한다(iOS 스크롤 가장자리처럼).
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
    // 기록은 세 겹이다: 바탕 ⊂ 바탕+내용 ⊂ 화면 전체(막대 포함). 유리는 자기를 담지 않은 겹을 비춘다.
    // 내용 안의 유리는 바탕을, 막대는 바탕+내용을, 팝업은 화면 전체를 비춘다.
    val backdrop = rememberGlassBackdrop()
    val contentBackdrop = rememberGlassBackdrop()
    val backgroundBackdrop = rememberGlassBackdrop()
    val host = remember { GlassOverlayHost() }
    var topHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalGlassHost provides host, LocalGlassStyle provides style) {
        Box(modifier) {
            Box(Modifier.fillMaxSize().glassBackdrop(backdrop)) {
                Box(Modifier.fillMaxSize().glassBackdrop(contentBackdrop).background(backdropColor)) {
                    // 층(graphicsLayer)을 만들지 않고 그릴 때만 줄여, 안쪽 유리가 뒤 화면 기록에서 빠지게 한다.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                // 그리기 단계에서 읽어, 시트가 움직이는 동안 내용을 다시 구성하지 않는다.
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
            host.entries.forEach { entry -> key(entry.id) { entry.content() } }
        }
    }
}

/** 상태 표시줄 아래로 들어간 내용을 흐리게 하고, 아래로 갈수록 옅어진다. */
@Composable
private fun EdgeFade(backdrop: GlassBackdrop, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            // 상태 표시줄과 그 아래 조금(위 막대가 떠 있는 틈)까지.
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

/** 지금 유리 요소가 쓰는 기본 모양([GlassScaffold]의 `style`). */
public val LocalGlassStyle: androidx.compose.runtime.ProvidableCompositionLocal<GlassStyle> =
    staticCompositionLocalOf { GlassStyles.Liquid }

internal val LocalGlassHost = staticCompositionLocalOf<GlassOverlayHost?> { null }

/** [GlassScaffold] 위층에 그릴 팝업들. 여는 곳은 화면 어디든 되고, 그리는 곳은 뒤 화면 기록 밖이다. */
@Stable
internal class GlassOverlayHost {
    val entries = mutableStateListOf<OverlayEntry>()

    /** 열린 시트들의 물러남 정도 중 가장 큰 값(0..1). */
    val pushBack: Float get() = entries.maxOfOrNull { it.pushBack } ?: 0f
}

@Stable
internal class OverlayEntry(val id: Any) {
    var content: @Composable () -> Unit by mutableStateOf({})
    var pushBack by mutableFloatStateOf(0f)
}

private const val PUSH_SCALE = 0.07f
private val EDGE_BLUR = 16.dp
private val EDGE_EXTRA = 12.dp
private val PUSH_DROP = 10.dp
private val PUSH_CORNER = 28.dp
