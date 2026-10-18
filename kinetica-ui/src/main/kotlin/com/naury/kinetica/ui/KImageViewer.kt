package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 전체 화면 사진 뷰어. 좌우로 넘기고, 두 손가락으로 1~5배 확대하며, 확대한 채로 끌어 옮긴다. 가장자리를 넘어 끌면 고무줄처럼 늘어났다가 튕겨 돌아온다.
 * 확대한 사진이 가장자리에 닿은 뒤 다시 바깥쪽으로 끌면 옆 사진으로 넘어간다.
 * 두 번 탭하면 그 지점을 2.5배로 확대하고, 다시 두 번 탭하면 원래 크기로 돌아온다. 한 번 탭하면 위아래 글래스 바를 숨기거나 보인다.
 * 확대하지 않은 상태에서 위나 아래로 끌면 배경이 옅어지며 사진이 따라오고, 충분히 끌거나 빠르게 던지면 닫힌다. 뒤로 가기로도 닫힌다.
 *
 * [KineticaScaffold] 안에서는 오버레이 레이어에, 밖에서는 전체 화면 창에 뜬다. TalkBack에서는 사진마다 '사진 5장 중 2번째'처럼 읽히고,
 * 사용자 지정 동작으로 확대, 축소, 다음 사진, 이전 사진을 고를 수 있다.
 *
 * ```kotlin
 * val viewer = rememberKImageViewerState()
 * LazyVerticalGrid(GridCells.Fixed(3)) {
 *     itemsIndexed(photos) { i, photo ->
 *         val thumb = Modifier.aspectRatio(1f).imageViewerSource(viewer, i).clickable { viewer.open(i) }
 *         Image(photo, null, thumb, contentScale = ContentScale.Crop)
 *     }
 * }
 * KImageViewer(viewer, photos)
 * ```
 *
 * @param images 사진. `intrinsicSize`의 비율 그대로 화면에 맞춰 그리고, 그 비율로 확대 중 이동 범위와 열고 닫는 전환을 계산한다.
 *   `BrushPainter`, `ColorPainter`처럼 크기가 없는(`Size.Unspecified`이거나 가로, 세로가 0인) Painter는 화면 전체를 채워 그리고
 *   화면 전체를 사진으로 본다. 열린 채 목록이 비면 애니메이션 없이 바로 닫는다.
 * @param contentDescriptions 사진마다 TalkBack이 읽을 설명. 짧으면 나머지는 설명 없이 둔다.
 * @param surface 위아래 바의 글래스 스타일. 사진 위에 어둡게 뜨는 [GlassStyles.Vision]이 기본값이다.
 * @param caption 아래 바에 놓을 설명. `null`이면 사진이 둘 이상일 때 페이지 인디케이터를 놓는다.
 */
@Composable
public fun KImageViewer(
    state: KImageViewerState,
    images: List<Painter>,
    modifier: Modifier = Modifier,
    contentDescriptions: List<String?> = emptyList(),
    surface: SurfaceStyle = SurfaceStyles.Vision,
    caption: (@Composable (page: Int) -> Unit)? = null,
) {
    ViewerCore(state, images.size, { images.getOrNull(it)?.aspect() }, modifier, ViewerLook(surface, caption)) { page ->
        // 목록이 줄어든 뒤 뷰어가 닫히기 전에 페이지가 한 번 더 그려질 수 있다.
        val image = images.getOrNull(page)
        val description = contentDescriptions.getOrNull(page)
        when {
            image == null -> Unit
            image.aspect() != null -> Image(image, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else -> SizelessImage(image, description)
        }
    }
}

/**
 * 사진 대신 원하는 내용을 페이지로 그리는 [KImageViewer]. [content]는 화면 크기로 그린다.
 * 내용의 비율을 모르므로 열고 닫을 때 페이지 전체가 썸네일 크기로 맞춰진다. 사진 비율을 알면 [aspectRatio]로 알려 준다.
 *
 * @param pageCount 페이지 수. 0 이하이면 아무것도 그리지 않고, 열린 채 0 이하가 되면 애니메이션 없이 바로 닫는다.
 * @param aspectRatio 페이지 안 그림의 가로/세로 비율. `null`이면 화면 전체를 그림으로 본다. 확대 중 이동 범위와 열고 닫는 전환에 쓴다.
 */
@Composable
public fun KImageViewer(
    state: KImageViewerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
    aspectRatio: (page: Int) -> Float? = { null },
    surface: SurfaceStyle = SurfaceStyles.Vision,
    caption: (@Composable (page: Int) -> Unit)? = null,
    content: @Composable (page: Int) -> Unit,
) {
    ViewerCore(state, pageCount, aspectRatio, modifier, ViewerLook(surface, caption), content)
}

/**
 * 상태 없이 바로 띄우는 [KImageViewer]. 컴포지션에 들어오면 [startIndex]번 사진으로 열리고,
 * 닫힘 애니메이션이 끝나면 [onDismiss]를 부른다. 호출한 쪽은 그때 이 함수를 컴포지션에서 뺀다.
 * [images]가 빈 목록이 되어도 바로 닫히고 [onDismiss]를 부른다.
 *
 * ```kotlin
 * viewing?.let { KImageViewer(photos, startIndex = it, onDismiss = { viewing = null }) }
 * ```
 */
@Composable
public fun KImageViewer(images: List<Painter>, startIndex: Int, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberSaveable(saver = ViewerSaver) { KImageViewerState(openAt = startIndex.coerceAtLeast(0), restored = false) }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(state) {
        snapshotFlow { state.isVisible }.first { !it }
        dismiss()
    }
    KImageViewer(state, images, modifier)
}

/**
 * 크기가 없는 Painter를 영역 전체에 그린다. `Image`는 가로나 세로 한쪽만 0인 크기(세로 그라디언트의 BrushPainter 등)를
 * 크기가 있는 것으로 보고 배율을 무한대로 계산해 그리다 실패하므로, 영역 크기를 그대로 넘겨 직접 그린다.
 */
@Composable
private fun SizelessImage(painter: Painter, description: String?) {
    Spacer(
        Modifier
            .fillMaxSize()
            .semantics {
                if (description != null) {
                    contentDescription = description
                    role = Role.Image
                }
            }
            .drawBehind { with(painter) { draw(size) } },
    )
}

private class ViewerLook(val surface: SurfaceStyle, val caption: (@Composable (page: Int) -> Unit)?)

private fun Painter.aspect(): Float? {
    val size = intrinsicSize
    return if (size.isSpecifiedSize() && size.width > 0f && size.height > 0f) size.width / size.height else null
}

private fun Size.isSpecifiedSize(): Boolean = this != Size.Unspecified && width.isFinite() && height.isFinite()

@Composable
private fun ViewerCore(
    state: KImageViewerState,
    pageCount: Int,
    aspect: (Int) -> Float?,
    modifier: Modifier,
    look: ViewerLook,
    content: @Composable (page: Int) -> Unit,
) {
    if (pageCount <= 0) {
        // 보여 줄 사진이 없으니 전환 없이 닫는다. 상태 없는 KImageViewer는 isVisible을 보고 onDismiss를 부른다.
        if (state.isVisible) {
            LaunchedEffect(state) {
                state.close()
                state.isVisible = false
            }
        }
        return
    }
    if (!state.isVisible) return
    val progress = remember { Animatable(if (state.restoredOpen) 1f else 0f).also { state.restoredOpen = false } }
    val drag = remember { ViewerDrag() }
    val zoom = remember { ViewerZoom() }
    val pager = rememberPagerState(initialPage = state.page.coerceIn(0, pageCount - 1)) { pageCount }
    val scope = rememberCoroutineScope()
    val latestAspect by rememberUpdatedState(aspect)
    val height = remember { floatArrayOf(1f) }
    LaunchedEffect(state.isOpen) {
        if (state.isOpen) {
            // 닫히는 도중에 다시 열면 끌어서 닫던 이동이 남아 있으므로 제자리로 돌린다.
            if (drag.value != 0f) drag.settle(scope, 0f)
            progress.animateTo(1f, spring(dampingRatio = OPEN_DAMPING, stiffness = Spring.StiffnessMediumLow))
        } else {
            // 확대한 채 닫으면 확대된 일부만 썸네일로 줄어드니 확대도 함께 푼다.
            if (zoom.zoomed) zoom.zoomTo(scope, 1f)
            // 썸네일이 없으면 끌던 방향으로 조금 더 밀려나며 사라진다.
            if (state.sources[state.page] == null &&
                drag.value != 0f
            ) {
                drag.settle(scope, drag.value + height[0] * FLY_AWAY * sign(drag.value))
            }
            progress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            state.isVisible = false
        }
    }
    LaunchedEffect(state.openCount) { if (pager.currentPage != state.page) pager.scrollToPage(state.page.coerceIn(0, pageCount - 1)) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect {
            state.page = it
            zoom.reset()
            zoom.content = viewerFitRect(Rect(Offset.Zero, zoom.viewport), latestAspect(it)).size
        }
    }
    KOverlay(visible = true) { _ ->
        BackHandler(enabled = state.isOpen) { state.close() }
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .onGloballyPositioned { state.hostOrigin = it.positionOnScreen() },
        ) {
            val full = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            height[0] = full.height.coerceAtLeast(1f)
            if (zoom.viewport != full.size) {
                zoom.viewport = full.size
                zoom.content = viewerFitRect(full, aspect(pager.currentPage)).size
            }
            val frame = ViewerFrame(state, full, aspect, { progress.value }, { drag.value })
            ViewerBody(ViewerParts(state, pager, zoom, drag, frame), look, content)
        }
    }
}

/**
 * 검은 배경이 절반 넘게 보이는 동안만 시스템 바 아이콘을 밝게 한다. 끌어 내려 배경이 옅어지면 호스트 모양으로 돌아간다.
 * 매 프레임 바뀌는 값은 참/거짓으로 줄여 읽는다.
 */
@Composable
private fun ViewerBarIcons(frame: ViewerFrame) {
    val dark by remember(frame) { derivedStateOf { frame.level > DARK_BARS_LEVEL && frame.dragged < DARK_BARS_LEVEL } }
    SystemBarIcons(light = if (dark) true else null)
}

/** 뷰어 안쪽에서 함께 쓰는 상태 묶음. */
private class ViewerParts(
    val state: KImageViewerState,
    val pager: PagerState,
    val zoom: ViewerZoom,
    val drag: ViewerDrag,
    val frame: ViewerFrame,
)

/**
 * 열림 정도와 끌어서 닫기 이동에 따라 사진이 놓일 자리를 계산한다. 썸네일([KImageViewerState.sources])에서 사진 영역으로 커지고,
 * 끄는 동안에는 손가락을 따라 내려가며 조금 작아진다. 썸네일이 없으면 제자리에서 살짝 커지며 나타난다. 값은 그리기 단계에서 읽는다.
 */
private class ViewerFrame(
    private val state: KImageViewerState,
    private val full: Rect,
    private val aspect: (Int) -> Float?,
    private val progress: () -> Float,
    private val drag: () -> Float,
) {
    val level: Float get() = progress().coerceIn(0f, 1f)

    /** 끈 정도(0..1). 화면 높이의 [DRAG_FADE_FRACTION]만큼 끌면 1이다. */
    val dragged: Float get() = min(abs(drag()) / (full.height * DRAG_FADE_FRACTION), 1f)

    val backgroundAlpha: Float get() = level * (1f - dragged)

    val hasSource: Boolean get() = state.sources[state.page] != null

    /** 지금 프레임의 자르기 영역, 배율, 화면 가운데 기준 이동을 [out]에 채운다. */
    fun compute(out: FrameValues) {
        val p = progress().coerceAtLeast(0f)
        val d = drag()
        val image = viewerFitRect(full, aspect(state.page))
        val dragScale = 1f - min(abs(d) / full.height, 1f) * DRAG_SHRINK
        val center = full.center + Offset(0f, d)
        val source = state.sources[state.page]?.translate(-state.hostOrigin) ?: image.viewerScaled(FALLBACK_SCALE, center)
        val startScale = viewerCoverScale(image, source)
        out.scale = lerp(startScale, dragScale, p)
        out.translation = lerp(source.center, center, p) - full.center
        out.clip = lerp(source, full.viewerScaled(dragScale, center), p)
        out.alpha = if (hasSource) 1f else level
    }
}

private class FrameValues {
    var scale = 1f
    var translation = Offset.Zero
    var clip = Rect.Zero
    var alpha = 1f
}

@Composable
private fun ViewerBody(parts: ViewerParts, look: ViewerLook, content: @Composable (page: Int) -> Unit) {
    ViewerBarIcons(parts.frame)
    val scope = rememberCoroutineScope()
    var barsShown by remember { mutableStateOf(true) }
    val bars by animateFloatAsState(if (barsShown) 1f else 0f, label = "viewer-bars")
    val backdrop = rememberGlassBackdrop()
    val values = remember { FrameValues() }
    val zoomed by remember { derivedStateOf { parts.zoom.zoomed } }
    val state = parts.state
    val drag = parts.drag
    val viewerLabel = stringResource(R.string.kinetica_ui_viewer)
    Box(
        Modifier
            .fillMaxSize()
            .semantics {
                paneTitle = viewerLabel
                dismiss {
                    state.close()
                    true
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .glassBackdrop(backdrop)
                .draggable(
                    rememberDraggableState { drag.drag(it) },
                    Orientation.Vertical,
                    enabled = !zoomed && state.isOpen,
                    onDragStopped = { velocity ->
                        val far = parts.frame.dragged > DISMISS_FRACTION
                        val thrown = abs(velocity) > DISMISS_VELOCITY && sign(velocity) == sign(drag.value)
                        if (far || thrown) state.close() else drag.settle(scope, 0f)
                    },
                ),
        ) {
            Spacer(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = parts.frame.backgroundAlpha)) })
            ViewerPager(parts, values, onTap = { barsShown = !barsShown }, content)
            StatusBarScrim { parts.frame.level * (1f - parts.frame.dragged * BAR_DRAG_FADE).coerceAtLeast(0f) }
        }
        val barLevel = { bars * parts.frame.level * (1f - parts.frame.dragged * BAR_DRAG_FADE).coerceAtLeast(0f) }
        TopBar(parts, look.surface, backdrop, barsShown, barLevel)
        BottomBar(parts, look, backdrop, barLevel)
    }
}

/**
 * 상태 표시줄 뒤를 위에서 아래로 옅어지는 어두운 막으로 덮는다. 뷰어는 시스템 바 아이콘을 밝게 하는데, 사진이 화면 위까지 차면
 * 흰 사진 위에서 시계와 배터리가 보이지 않는다. [level]은 막의 진하기(0..1)이며 그리기 단계에서 읽는다.
 */
@Composable
private fun BoxScope.StatusBarScrim(level: () -> Float) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    if (top <= 0.dp) return
    Spacer(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(top + STATUS_SCRIM_FADE)
            .drawBehind {
                val alpha = STATUS_SCRIM_ALPHA * level().coerceIn(0f, 1f)
                if (alpha > 0f) {
                    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = alpha), 1f to Color.Transparent))
                }
            },
    )
}

@Composable
private fun ViewerPager(parts: ViewerParts, values: FrameValues, onTap: () -> Unit, content: @Composable (page: Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val zoom = parts.zoom
    HorizontalPager(
        parts.pager,
        Modifier
            .fillMaxSize()
            .drawWithContent {
                parts.frame.compute(values)
                val c = values.clip
                clipRect(c.left, c.top, c.right, c.bottom) { this@drawWithContent.drawContent() }
            }
            .graphicsLayer {
                parts.frame.compute(values)
                scaleX = values.scale
                scaleY = values.scale
                translationX = values.translation.x
                translationY = values.translation.y
                alpha = values.alpha
            },
        pageSpacing = PAGE_SPACING,
    ) { page ->
        val current = page == parts.pager.currentPage
        Box(
            Modifier
                .fillMaxSize()
                .then(if (current) Modifier.viewerZoomGestures(zoom, scope) else Modifier)
                .pointerInput(current) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = if (current) {
                            { zoom.toggle(scope, it - Offset(size.width / 2f, size.height / 2f)) }
                        } else {
                            null
                        },
                    )
                }
                .graphicsLayer {
                    if (current) {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    }
                }
                .then(if (current) Modifier.pageAccessibility(parts, page) else Modifier),
        ) { content(page) }
    }
}

/** 지금 페이지의 TalkBack 상태와 사용자 지정 동작(확대, 축소, 다음, 이전). */
@Composable
private fun Modifier.pageAccessibility(parts: ViewerParts, page: Int): Modifier {
    val scope = rememberCoroutineScope()
    val zoom = parts.zoom
    val percent by remember { derivedStateOf { (zoom.scale * PERCENT / PERCENT_STEP).roundToInt() * PERCENT_STEP } }
    val count = parts.pager.pageCount
    val photo = stringResource(R.string.kinetica_ui_viewer_photo, page + 1, count)
    val description = if (percent > PERCENT) stringResource(R.string.kinetica_ui_viewer_zoomed, photo, percent) else photo
    val zoomIn = stringResource(R.string.kinetica_ui_viewer_zoom_in)
    val zoomOut = stringResource(R.string.kinetica_ui_viewer_zoom_out)
    val next = stringResource(R.string.kinetica_ui_viewer_next)
    val previous = stringResource(R.string.kinetica_ui_viewer_previous)
    fun go(target: Int): Boolean {
        scope.launch { parts.pager.animateScrollToPage(target) }
        return true
    }
    return semantics {
        stateDescription = description
        customActions = listOfNotNull(
            CustomAccessibilityAction(zoomIn) {
                zoom.zoomTo(scope, if (zoom.zoomed) zoom.scale * ZOOM_STEP else VIEWER_DOUBLE_TAP_ZOOM)
                true
            }.takeIf { percent < VIEWER_MAX_ZOOM * PERCENT },
            CustomAccessibilityAction(zoomOut) {
                zoom.zoomTo(scope, 1f)
                true
            }.takeIf { percent > PERCENT },
            CustomAccessibilityAction(next) { go(page + 1) }.takeIf { page < count - 1 },
            CustomAccessibilityAction(previous) { go(page - 1) }.takeIf { page > 0 },
        )
    }
}

@Composable
private fun BoxScope.TopBar(parts: ViewerParts, surface: SurfaceStyle, backdrop: GlassBackdrop, shown: Boolean, level: () -> Float) {
    val ink = surface.contentColor
    val closeLabel = stringResource(R.string.kinetica_ui_close)
    Row(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(BAR_PADDING)
            .graphicsLayer {
                alpha = level()
                translationY = -BAR_SLIDE.toPx() * (1f - level())
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val buttonShape = shapeFor { button }
        Box(
            Modifier
                .size(BUTTON_SIZE)
                .kSurface(surface, buttonShape, backdrop)
                .then(
                    if (shown) {
                        Modifier.kClickable(buttonShape, onClickLabel = closeLabel) { parts.state.close() }
                            .semantics { contentDescription = closeLabel }
                    } else {
                        Modifier.clearAndSetSemantics { }
                    },
                )
                .lightInkScrim(buttonShape, ink)
                .drawBehind {
                    val arm = size.minDimension * CLOSE_ARM
                    val stroke = CLOSE_STROKE.toPx()
                    // 밝은 ×는 흰 사진 위에서 사라지므로 어두운 테두리를 먼저 그린다.
                    if (ink.luminance() > LIGHT_INK) {
                        val halo = Color.Black.copy(alpha = HALO_ALPHA)
                        drawLine(halo, center - Offset(arm, arm), center + Offset(arm, arm), stroke + HALO.toPx(), StrokeCap.Round)
                        drawLine(halo, center + Offset(arm, -arm), center + Offset(-arm, arm), stroke + HALO.toPx(), StrokeCap.Round)
                    }
                    drawLine(ink, center - Offset(arm, arm), center + Offset(arm, arm), stroke, StrokeCap.Round)
                    drawLine(ink, center + Offset(arm, -arm), center + Offset(-arm, arm), stroke, StrokeCap.Round)
                },
        )
        Spacer(Modifier.weight(1f))
        if (parts.pager.pageCount > 1) {
            val badge = shapeFor { badge }
            val counter = Modifier.kSurface(surface, badge, backdrop).lightInkScrim(badge, ink)
            Box(counter.clearAndSetSemantics { }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                ThemedText(
                    "${parts.pager.currentPage + 1} / ${parts.pager.pageCount}",
                    style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = KineticaTheme.design.label(FontWeight.SemiBold)),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(BUTTON_SIZE))
    }
}

/**
 * 글자가 밝은 표면이면 버튼 안쪽을 옅게 어둡게 칠한다. 글래스 버튼은 뒤 사진을 블러로 비추므로, 흰 사진 위에서는 버튼과 흰 글자가 함께 묻힌다.
 */
private fun Modifier.lightInkScrim(shape: Shape, ink: Color): Modifier = if (ink.luminance() > LIGHT_INK) {
    drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), Color.Black.copy(alpha = SCRIM_ALPHA)) }
} else {
    this
}

@Composable
private fun BoxScope.BottomBar(parts: ViewerParts, look: ViewerLook, backdrop: GlassBackdrop, level: () -> Float) {
    val caption = look.caption
    if (caption == null && parts.pager.pageCount <= 1) return
    val surface = look.surface
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

private const val OPEN_DAMPING = 0.8f
private const val FLY_AWAY = 0.3f
private const val FALLBACK_SCALE = 0.86f
private const val DRAG_SHRINK = 0.3f
private const val DRAG_FADE_FRACTION = 0.5f
private const val DARK_BARS_LEVEL = 0.5f
private const val DISMISS_FRACTION = 0.3f
private const val DISMISS_VELOCITY = 1200f
private const val BAR_DRAG_FADE = 3f
private const val PERCENT = 100
private const val PERCENT_STEP = 10
private const val ZOOM_STEP = 1.6f
private val PAGE_SPACING = 16.dp
private val BAR_PADDING = 12.dp
private val BAR_SLIDE = 16.dp
private val BUTTON_SIZE = KMetrics.IconButton
private const val CLOSE_ARM = 0.17f
private const val LIGHT_INK = 0.5f
private const val STATUS_SCRIM_ALPHA = 0.45f
private val STATUS_SCRIM_FADE = 24.dp
private const val SCRIM_ALPHA = 0.32f
private const val HALO_ALPHA = 0.35f
private val HALO = 2.dp
private val CLOSE_STROKE = 2.dp
private val CAPTION_PADDING = 16.dp
