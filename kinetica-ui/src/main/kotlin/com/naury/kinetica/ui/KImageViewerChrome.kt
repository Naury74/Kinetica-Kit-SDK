package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 뷰어 시작 쪽 버튼 모양. */
public enum class KImageViewerCloseIcon {
    /** ×. 사진 한 장을 크게 보는 오버레이의 기본 닫기 모양이다. */
    Close,

    /** ←. RTL에서는 →로 뒤집힌다. 고르기 화면에서 미리 보기로 들어온 것처럼 이전 화면으로 돌아가는 느낌일 때 쓴다. */
    Back,
}

/**
 * [KImageViewer]의 `topActions`, `bottomBar` 슬롯이 받는 값. 안쪽 글자와 아이콘은 [LocalKineticaContentColor]로 [surface]의 글자 색을 받는다.
 *
 * ```kotlin
 * topActions = { page ->
 *     KIconButton(KIcons.Share, "공유", { share(page) }, Modifier.viewerChrome(CircleShape), variant = KVariant.Text, surface = surface)
 * }
 * ```
 */
@Stable
public interface KImageViewerChromeScope {
    /** 위아래 바의 표면. 슬롯 안 버튼에 넘겨 같은 글자 색을 쓰게 한다. */
    public val surface: SurfaceStyle

    /**
     * 닫기 버튼과 같은 글래스 면을 [shape]으로 깐다. [surface]의 글자가 밝으면 흰 사진 위에서도 버튼과 글자가 묻히지 않도록
     * 안쪽을 옅게 어둡게 칠한다. 크기와 클릭은 정하지 않으므로 앞뒤에 따로 붙인다.
     */
    @Composable
    public fun Modifier.viewerChrome(shape: Shape): Modifier
}

internal class ViewerChromeScopeImpl(override val surface: SurfaceStyle, private val backdrop: GlassBackdrop) : KImageViewerChromeScope {
    @Composable
    override fun Modifier.viewerChrome(shape: Shape): Modifier =
        kSurface(surface, shape, backdrop).lightInkScrim(shape, surface.contentColor)
}

/** 위아래 바가 함께 쓰는 값. [level]은 바가 보이는 정도(0..1)이며 그리기와 배치 단계에서 읽는다. */
internal class ChromeContext(val scope: ViewerChromeScopeImpl, val backdrop: GlassBackdrop, val shown: Boolean, val level: () -> Float)

@Composable
internal fun BoxScope.TopBar(parts: ViewerParts, look: ViewerLook, chrome: ChromeContext) {
    val surface = look.surface
    val level = chrome.level
    val ink = surface.contentColor
    val actions = look.topActions
    TopBarLayout(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(BAR_PADDING)
            .graphicsLayer {
                alpha = level()
                translationY = -BAR_SLIDE.toPx() * (1f - level())
            },
        start = { CloseButton(parts, look, chrome) },
        center = {
            if (parts.pager.pageCount > 1) {
                val badge = shapeFor { badge }
                val counter = Modifier.kSurface(surface, badge, chrome.backdrop).lightInkScrim(badge, ink)
                Box(counter.clearAndSetSemantics { }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    ThemedText(
                        "${parts.pager.currentPage + 1} / ${parts.pager.pageCount}",
                        style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = KineticaTheme.design.label(FontWeight.SemiBold)),
                    )
                }
            }
        },
        end = {
            if (actions != null) {
                Row(Modifier.chromeSlot(chrome.shown, level), verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalKineticaContentColor provides ink) { chrome.scope.actions(parts.pager.currentPage) }
                }
            } else {
                Spacer(Modifier.size(BUTTON_SIZE))
            }
        },
    )
}

/** 시작 쪽 닫기 버튼. 바가 숨으면 누를 수 없고 TalkBack에서 빠진다. */
@Composable
private fun CloseButton(parts: ViewerParts, look: ViewerLook, chrome: ChromeContext) {
    val surface = look.surface
    val ink = surface.contentColor
    val back = look.closeIcon == KImageViewerCloseIcon.Back
    val label = stringResource(if (back) R.string.kinetica_ui_large_title_back else R.string.kinetica_ui_close)
    val buttonShape = shapeFor { button }
    Box(
        Modifier
            .size(BUTTON_SIZE)
            .kSurface(surface, buttonShape, chrome.backdrop)
            .then(
                if (chrome.shown) {
                    Modifier.kClickable(buttonShape, onClickLabel = label) { parts.state.close() }
                        .semantics { contentDescription = label }
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            )
            .lightInkScrim(buttonShape, ink)
            .drawBehind { if (back) drawBackArrow(ink) else drawCross(ink) },
    )
}

/**
 * 시작 버튼, 가운데 쪽수, 끝 버튼을 한 줄에 놓는다. 쪽수는 양쪽 가운데 넓은 쪽만큼 비워 늘 바 가운데에 오고, 남는 폭이 모자라면 줄어든다.
 * 끝 쪽이 버튼 하나 폭이면 예전 Row 배치와 같은 자리가 된다.
 */
@Composable
private fun TopBarLayout(modifier: Modifier, start: @Composable () -> Unit, center: @Composable () -> Unit, end: @Composable () -> Unit) {
    Layout(contents = listOf(start, center, end), modifier = modifier) { (startM, centerM, endM), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val s = startM.map { it.measure(loose) }
        val e = endM.map { it.measure(loose) }
        val width = constraints.maxWidth
        val side = maxOf(s.maxOfOrNull { it.width } ?: 0, e.maxOfOrNull { it.width } ?: 0)
        val c = centerM.map { it.measure(loose.copy(maxWidth = (width - side * 2).coerceAtLeast(0))) }
        val height = maxOf(constraints.minHeight, (s + c + e).maxOfOrNull { it.height } ?: 0)
        layout(width, height) {
            s.forEach { it.placeRelative(0, (height - it.height) / 2) }
            c.forEach { it.placeRelative((width - it.width) / 2, (height - it.height) / 2) }
            e.forEach { it.placeRelative(width - it.width, (height - it.height) / 2) }
        }
    }
}

/** 닫기 ×. 밝은 ×는 흰 사진 위에서 사라지므로 어두운 테두리를 먼저 그린다. */
private fun DrawScope.drawCross(ink: Color) {
    val arm = size.minDimension * CLOSE_ARM
    val stroke = CLOSE_STROKE.toPx()
    if (ink.luminance() > LIGHT_INK) {
        val halo = Color.Black.copy(alpha = HALO_ALPHA)
        drawLine(halo, center - Offset(arm, arm), center + Offset(arm, arm), stroke + HALO.toPx(), StrokeCap.Round)
        drawLine(halo, center + Offset(arm, -arm), center + Offset(-arm, arm), stroke + HALO.toPx(), StrokeCap.Round)
    }
    drawLine(ink, center - Offset(arm, arm), center + Offset(arm, arm), stroke, StrokeCap.Round)
    drawLine(ink, center + Offset(arm, -arm), center + Offset(-arm, arm), stroke, StrokeCap.Round)
}

/** 뒤로 ←. RTL에서는 →로 뒤집는다. 밝은 화살표는 ×와 같이 어두운 테두리를 먼저 그린다. */
private fun DrawScope.drawBackArrow(ink: Color) {
    val arm = size.minDimension * BACK_ARM
    val dir = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    val tip = center - Offset(arm * dir, 0f)
    val tail = center + Offset(arm * dir, 0f)
    val head = arm * BACK_HEAD
    val up = tip + Offset(head * dir, -head)
    val down = tip + Offset(head * dir, head)
    val stroke = CLOSE_STROKE.toPx()
    fun lines(color: Color, width: Float) {
        drawLine(color, tail, tip, width, StrokeCap.Round)
        drawLine(color, tip, up, width, StrokeCap.Round)
        drawLine(color, tip, down, width, StrokeCap.Round)
    }
    if (ink.luminance() > LIGHT_INK) lines(Color.Black.copy(alpha = HALO_ALPHA), stroke + HALO.toPx())
    lines(ink, stroke)
}

/**
 * 호스트 슬롯을 바와 함께 숨긴다. 바가 다 숨으면 놓지 않아 그리기와 터치에서 빠지고, 내용과 상태(필름 줄 스크롤 등)는 그대로 둔다.
 * 숨는 중이거나 숨은 동안에는 의미를 지워 TalkBack에서 빼고 터치를 막는다. 배치 단계에서 [level]을 읽는다.
 */
private fun Modifier.chromeSlot(shown: Boolean, level: () -> Float): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { if (level() > 0f) placeable.place(0, 0) }
    }
    .then(
        if (shown) {
            Modifier
        } else {
            Modifier
                .clearAndSetSemantics { }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
        },
    )

/**
 * 글자가 밝은 표면이면 버튼 안쪽을 옅게 어둡게 칠한다. 글래스 버튼은 뒤 사진을 블러로 비추므로, 흰 사진 위에서는 버튼과 흰 글자가 함께 묻힌다.
 */
private fun Modifier.lightInkScrim(shape: Shape, ink: Color): Modifier = if (ink.luminance() > LIGHT_INK) {
    drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), Color.Black.copy(alpha = SCRIM_ALPHA)) }
} else {
    this
}

@Composable
internal fun BoxScope.BottomBar(parts: ViewerParts, look: ViewerLook, chrome: ChromeContext) {
    val bottomBar = look.bottomBar
    if (bottomBar != null) {
        SlotBottomBar(parts, look, chrome, bottomBar)
        return
    }
    val caption = look.caption
    if (caption == null && parts.pager.pageCount <= 1) return
    val surface = look.surface
    val backdrop = chrome.backdrop
    val level = chrome.level
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(BAR_PADDING)
            .graphicsLayer {
                alpha = level()
                translationY = BAR_SLIDE.toPx() * (1f - level())
            },
    ) {
        if (caption != null) {
            Box(Modifier.fillMaxWidth().kSurface(surface, shapeFor { card }, backdrop).padding(CAPTION_PADDING)) {
                CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor) { caption(parts.pager.currentPage) }
            }
        } else {
            Box(Modifier.kSurface(surface, shapeFor { tabBar }, backdrop).padding(horizontal = 6.dp)) {
                KPageIndicator(parts.pager, surface = surface)
            }
        }
    }
}

/** 호스트가 준 전폭 아래 바. 설명 카드가 있으면 그 위에 놓고, 페이지 인디케이터는 놓지 않는다. */
@Composable
private fun BoxScope.SlotBottomBar(
    parts: ViewerParts,
    look: ViewerLook,
    chrome: ChromeContext,
    bottomBar: @Composable KImageViewerChromeScope.(page: Int) -> Unit,
) {
    val surface = look.surface
    val ink = surface.contentColor
    val level = chrome.level
    val caption = look.caption
    val page = parts.pager.currentPage
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = level()
                translationY = BAR_SLIDE.toPx() * (1f - level())
            }
            .chromeSlot(chrome.shown, level)
            .then(
                if (ink.luminance() > LIGHT_INK) {
                    Modifier.drawBehind {
                        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = BOTTOM_SCRIM_ALPHA)))
                    }
                } else {
                    Modifier
                },
            )
            .padding(top = BOTTOM_SCRIM_FADE),
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides ink) {
            if (caption != null) {
                Box(
                    Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = BAR_PADDING)
                        .padding(bottom = BAR_PADDING)
                        .fillMaxWidth()
                        .kSurface(surface, shapeFor { card }, chrome.backdrop)
                        .padding(CAPTION_PADDING),
                ) { caption(page) }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) { chrome.scope.bottomBar(page) }
        }
    }
}

private val BAR_PADDING = 12.dp
private val BAR_SLIDE = 16.dp
private val BUTTON_SIZE = KMetrics.IconButton
private const val CLOSE_ARM = 0.17f
private const val LIGHT_INK = 0.5f
private const val SCRIM_ALPHA = 0.32f
private const val HALO_ALPHA = 0.35f
private val HALO = 2.dp
private val CLOSE_STROKE = 2.dp
private val CAPTION_PADDING = 16.dp
private const val BACK_ARM = 0.2f
private const val BACK_HEAD = 0.75f
private const val BOTTOM_SCRIM_ALPHA = 0.55f
private val BOTTOM_SCRIM_FADE = 32.dp
