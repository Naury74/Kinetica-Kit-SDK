package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 표면 위 텍스트와 아이콘 색. 글래스 요소 안에서는 [SurfaceStyle.contentColor] 값이 들어간다. */
public val LocalKineticaContentColor: androidx.compose.runtime.ProvidableCompositionLocal<Color> =
    staticCompositionLocalOf { Color(0xFF15161A) }

/**
 * 글래스 표면. [backdrop]을 비춰 보이고, 기본값은 감싸고 있는 화면의 배경이다. 안쪽 콘텐츠는 [LocalKineticaContentColor]로 색을 받는다.
 * 기본 모양은 [KineticaShapes.card]다.
 */
@Composable
public fun KSurface(
    modifier: Modifier = Modifier,
    shape: Shape = shapeFor { card },
    surface: SurfaceStyle = KineticaTheme.surface,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.kSurface(surface, shape, backdrop)) {
        CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor) { content() }
    }
}

/**
 * 다이얼로그. 열리면 배경이 서서히 흐려지고 어두워지며, 패널이 [origin]에서 튀어나오듯 커진다.
 * 바깥을 누르거나 뒤로 가기를 하면 [onDismissRequest]를 호출한다. [KineticaScaffold] 안에서 쓰면 배경이 블러 처리되어 비친다.
 * [KineticaScaffold] 밖에서 부르면 전체 화면 대화 상자 창에 띄우므로 호출한 자리의 배치에는 끼어들지 않는다.
 *
 * 패널은 [contentWindowInsets]로 시스템 바, 화면 컷아웃, 키보드를 피하고, 그 안쪽 영역보다 커지지 않는다.
 * 내용이 그보다 길 수 있으면 [content]에 `verticalScroll`이나 `LazyColumn`을 넣는다. 짧으면 내용 높이에 맞춘다.
 * 너비는 280~420dp이고, 화면이 더 좁으면 화면 너비(여백 28dp 제외)에 맞춘다.
 * 반쯤 접은 폴더블처럼 접힌 선이 화면을 가르면 [origin]이 있는 쪽 칸(없으면 위 칸이나 시작 쪽 칸) 가운데에 띄워 접힌 선에 걸치지 않게 한다.
 * 열리면 키보드 초점과 TalkBack 초점이 패널 안으로 들어가고, 뒤 화면은 TalkBack에서 숨는다. 닫히면 열기 전에 초점이 있던 요소로 돌아간다.
 *
 * @param origin 패널이 커지기 시작하는 위치(화면 좌표, px). [Modifier.kOrigin]으로 누른 버튼의 중심을 얻을 수 있다.
 *   `null`이면 화면 가운데에서 커진다.
 * @param title 다이얼로그 이름. TalkBack이 창 제목으로 읽는다.
 * @param contentWindowInsets 패널이 피할 시스템 영역. 기본값은 safeDrawing 전체(시스템 바, 컷아웃, 키보드)다.
 *   패널을 그리는 창 안에서 부르므로 [KineticaScaffold] 밖에서 대화 상자 창에 띄울 때도 그 창의 인셋을 읽는다.
 */
@Composable
public fun KDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    surface: SurfaceStyle = KineticaTheme.surface,
    shape: Shape = shapeFor { dialog },
    origin: Offset? = null,
    title: String? = null,
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets.safeDrawing },
    content: @Composable ColumnScope.() -> Unit,
) {
    val design = KineticaTheme.design
    val still = reducedMotion()
    KOverlay(visible) { transition ->
        val progress by transition.animateFloat(
            transitionSpec = {
                when {
                    still -> tween(0)
                    targetState -> design.motion.tuned(DIALOG_DAMPING, DIALOG_STIFFNESS)
                    else -> tween(CLOSE_MS)
                }
            },
            label = "glass-dialog",
        ) { if (it) 1f else 0f }
        BackHandler(enabled = visible, onBack = onDismissRequest)
        val level = progress.coerceIn(0f, 1f)
        var panelCenter by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize()) {
            Scrim(level, onDismissRequest)
            // 반쯤 접은 폴더블에서는 접힌 선에 걸치지 않도록 누른 버튼이 있는 쪽 칸 가운데에 띄운다.
            Box(Modifier.fillMaxSize().avoidHinge(origin)) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .windowInsetsPadding(contentWindowInsets())
                        .padding(28.dp)
                        .then(modifier)
                        .widthIn(min = DIALOG_MIN_WIDTH, max = DIALOG_MAX_WIDTH)
                        .onGloballyPositioned { panelCenter = it.boundsOnScreen().center }
                        .graphicsLayer {
                            val scale = DIALOG_START_SCALE + (1f - DIALOG_START_SCALE) * progress
                            scaleX = scale
                            scaleY = scale
                            alpha = level
                            // 누른 위치에서 가운데로 이동하면서 커진다.
                            val from = origin ?: panelCenter
                            translationX = (from.x - panelCenter.x) * (1f - level)
                            translationY = (from.y - panelCenter.y) * (1f - level)
                            transformOrigin = TransformOrigin.Center
                        }
                        .kSurface(surface.forPopup(), shape)
                        .semantics {
                            title?.let { paneTitle = it }
                            dismiss {
                                onDismissRequest()
                                true
                            }
                        }
                        .blockTouches()
                        .padding(design.density.padding(24.dp)),
                ) {
                    CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor) {
                        KGlassLayer { content() }
                    }
                }
            }
        }
    }
}

/** [KSheet]와 [KDialog]의 기본값. */
public object KSheetDefaults {
    /**
     * 시트 내용이 피할 시스템 영역. 내비게이션 바, 화면 컷아웃, 키보드의 아래쪽과 양옆이다.
     * edge-to-edge를 쓰지 않는 화면에서는 이 값이 0이 되므로 여백이 두 번 들어가지 않는다.
     */
    public val windowInsets: WindowInsets
        @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)

    /** 시트 위 끝과 상태 표시줄(또는 위쪽 안전 영역) 사이에 늘 남기는 간격. 시트는 이보다 높아지지 않는다. */
    public val TopGap: Dp = 48.dp

    /** 넓은 화면에서 바텀 시트가 넘지 않는 폭. 화면이 더 넓으면 가운데에 놓인다. */
    public val MaxWidth: Dp = 640.dp
}

/**
 * 아래에서 올라오는 바텀 시트. [KineticaScaffold] 안에서 열면 뒤쪽 콘텐츠가 살짝 물러나며 모서리가 둥글어진다.
 * 핸들이나 시트 내용을 아래로 끌면 닫힌다. 높이의 30% 넘게 끌거나 빠르게 플링하면 [onDismissRequest]를 호출한다.
 * 안쪽 목록이 맨 위에 있을 때 더 끌어 내려도 시트가 따라 내려간다.
 * [KineticaScaffold] 밖에서 부르면 전체 화면 대화 상자 창에 띄우므로 호출한 자리의 배치에는 끼어들지 않는다.
 *
 * 높이는 내용에 맞추되 위쪽 안전 영역 아래 [KSheetDefaults.TopGap]까지만 커진다. 그보다 길 수 있는 내용은 `LazyColumn`이나
 * `verticalScroll`로 넣는다. `fillMaxSize`나 `Modifier.weight`를 쓴 요소는 이 최대 높이를 채운다.
 * 내용은 [contentWindowInsets]만큼 안쪽으로 들어가므로, 시트 안 입력란에 초점이 가면 키보드 위로 올라온다.
 * 넓은 화면에서는 [KSheetDefaults.MaxWidth] 폭으로 가운데에 놓고, 세로로 반쯤 접은 폴더블에서는 접힌 선 시작 쪽 칸에 놓는다.
 * 열리면 키보드 초점과 TalkBack 초점이 시트 안으로 들어가고, 뒤 화면은 TalkBack에서 숨는다.
 *
 * @param title 시트 이름. TalkBack이 창 제목으로 읽는다.
 * @param contentWindowInsets 내용이 피할 시스템 영역. 직접 처리하려면 `{ WindowInsets(0) }`을 준다.
 *   시트를 그리는 창 안에서 부르므로 [KineticaScaffold] 밖에서 대화 상자 창에 띄울 때도 그 창의 인셋을 읽는다.
 */
@Composable
public fun KSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    surface: SurfaceStyle = KineticaTheme.surface,
    title: String? = null,
    contentWindowInsets: @Composable () -> WindowInsets = { KSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    val still = reducedMotion()
    KOverlay(visible) { transition ->
        val progress by transition.animateFloat(
            transitionSpec = {
                when {
                    still -> tween(0)
                    targetState -> spring(dampingRatio = SHEET_DAMPING, stiffness = Spring.StiffnessMediumLow)
                    else -> tween(CLOSE_MS)
                }
            },
            label = "glass-sheet",
        ) { if (it) 1f else 0f }
        BackHandler(enabled = visible, onBack = onDismissRequest)
        val drag = remember { SettleOffset() }
        var height by remember { mutableFloatStateOf(1f) }
        val level = progress.coerceIn(0f, 1f)
        val dragged = (drag.value / height).coerceIn(0f, 1f)
        ReportPushBack(level * (1f - dragged))
        Box(Modifier.fillMaxSize()) {
            Scrim(level * (1f - dragged), onDismissRequest)
            Box(
                // 책 모드(세로로 반쯤 접음)에서는 접힌 선에 걸치지 않도록 한쪽 칸에 띄운다.
                Modifier.fillMaxSize().avoidHinge(horizontal = false).windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                ).padding(top = KSheetDefaults.TopGap),
                contentAlignment = Alignment.BottomCenter,
            ) {
                SheetPanel(
                    SheetFrame(surface, title, contentWindowInsets(), onDismissRequest),
                    drag,
                    { height },
                    modifier.onSizeChanged {
                        height = it.height.toFloat().coerceAtLeast(1f)
                    }.graphicsLayer { translationY = height * (1f - progress) + drag.value },
                    content,
                )
            }
        }
    }
}

/** 시트 패널의 고정 설정. 파라미터 수를 줄이려고 묶었다. */
private class SheetFrame(val surface: SurfaceStyle, val title: String?, val insets: WindowInsets, val onDismiss: () -> Unit)

@Composable
private fun SheetPanel(
    frame: SheetFrame,
    drag: SettleOffset,
    height: () -> Float,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = frame.surface
    val scope = rememberCoroutineScope()
    val design = KineticaTheme.design
    val connection = remember(drag) { SheetDismissConnection(drag, height, { frame.onDismiss() }) }
    Column(
        modifier
            // 태블릿이나 펼친 폴더블에서는 화면 끝까지 늘이지 않고 가운데에 읽기 좋은 폭으로 둔다.
            .widthIn(max = KSheetDefaults.MaxWidth)
            .fillMaxWidth()
            .kSurface(surface.forPopup(), design.shapes.topShape(design.shapes.sheet))
            .semantics {
                frame.title?.let { paneTitle = it }
                dismiss {
                    frame.onDismiss()
                    true
                }
            }
            .nestedScroll(connection)
            .dragToDismiss(drag, height, frame.onDismiss)
            .settleAfterRelease({ drag.value > 0f && !drag.isSettling }) {
                scope.launch { settleSheet(drag, height(), 0f, frame.onDismiss) }
            }
            .blockTouches()
            .windowInsetsPadding(frame.insets)
            .padding(horizontal = design.density.padding(24.dp)),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 12.dp)
                .size(40.dp, 5.dp)
                .background(surface.contentColor.copy(alpha = 0.28f), RoundedCornerShape(3.dp)),
        )
        CompositionLocalProvider(LocalKineticaContentColor provides surface.contentColor) { KGlassLayer { content() } }
        Box(Modifier.padding(bottom = 16.dp))
    }
}

/**
 * 아래로 끌어 닫는 동작. 높이의 30% 넘게 끌거나 빠르게 플링하면 닫고, 그렇지 않으면 던진 속도를 이어받아 제자리로 돌아온다.
 * 끌기는 상태에 바로 써서, 마지막 움직임과 손 떼기가 같은 프레임에 와도 정착 애니메이션이 취소되지 않게 한다.
 */
@Composable
private fun Modifier.dragToDismiss(drag: SettleOffset, height: () -> Float, onDismiss: () -> Unit): Modifier = draggable(
    rememberDraggableState { delta -> drag.drag(delta, min = 0f) },
    Orientation.Vertical,
    onDragStopped = { velocity -> settleSheet(drag, height(), velocity, onDismiss) },
)

private suspend fun settleSheet(drag: SettleOffset, height: Float, velocity: Float, onDismiss: () -> Unit) {
    if (drag.value > height * SHEET_DISMISS_FRACTION || velocity > SHEET_FLING) {
        onDismiss()
        drag.animateTo(0f, tween(CLOSE_MS))
    } else {
        // 아래로 던졌던 속도는 위로 돌아오는 데 방해가 되므로 위로 던진 속도만 이어받는다.
        drag.animateTo(0f, spring(dampingRatio = SHEET_DAMPING, stiffness = Spring.StiffnessMediumLow), velocity.coerceAtMost(0f))
    }
}

/**
 * 시트 안 스크롤 목록과 끌어 닫기를 잇는다. 목록이 맨 위에 닿은 뒤 남은 아래쪽 스크롤로 시트를 끌어 내리고,
 * 다시 위로 밀면 끌어 내린 거리부터 되돌린다. 손을 떼면 [settleSheet]로 닫을지 정한다.
 */
private class SheetDismissConnection(private val drag: SettleOffset, private val height: () -> Float, private val onDismiss: () -> Unit) :
    NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y >= 0f || drag.value <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
        return Offset(0f, drag.drag(available.y, min = 0f))
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
        return Offset(0f, drag.drag(available.y, min = 0f))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (drag.value <= 0f) return Velocity.Zero
        settleSheet(drag, height(), available.y, onDismiss)
        return available
    }
}

/** 패널 안의 빈 곳을 눌러도 뒤 스크림으로 넘어가 닫히지 않게 막는다. clickable과 달리 접근성 노드나 키보드 초점을 만들지 않는다. */
internal fun Modifier.blockTouches(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/** 이 요소의 화면 좌표 경계(px). 다른 창(대화 상자, 팝업)에 그린 오버레이와 같은 좌표로 비교할 수 있다. */
internal fun LayoutCoordinates.boundsOnScreen(): Rect = Rect(positionOnScreen(), size.toSize())

/** 다이얼로그 뒤에 까는 스크림. 서서히 흐려지고 어두워지며, 누르면 닫는다. TalkBack에서는 "닫기" 버튼으로 읽힌다. */
@Composable
internal fun BoxScope.Scrim(level: Float, onDismiss: () -> Unit) {
    // 별도 창이면 창 뒤를 시스템 블러로 흐린다(열리는 정도만큼).
    val windowBlur = LocalWindowBlur.current
    val blurPx = with(LocalDensity.current) { WINDOW_BLUR.roundToPx() }
    SideEffect { windowBlur?.set((blurPx * level).roundToInt()) }
    val closeLabel = stringResource(R.string.kinetica_ui_close)
    // 단색과 톤 표면에서는 배경 기록이 없으므로 블러 없이 조금 더 어둡게 덮는다.
    val glassy = KineticaTheme.surface is SurfaceStyle.Glass
    Box(
        Modifier
            .matchParentSize()
            .then(if (glassy) Modifier.glass(GlassStyles.Clear, intensity = level) else Modifier)
            .background(Color.Black.copy(alpha = (if (glassy) SCRIM_ALPHA else SOLID_SCRIM_ALPHA) * level))
            // 키보드 사용자는 뒤로 가기(Esc)로 닫으므로 스크림은 Tab 순서에 넣지 않는다. TalkBack에는 "닫기" 버튼으로 남는다.
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = closeLabel,
                role = Role.Button,
                onClick = onDismiss,
            )
            .semantics { contentDescription = closeLabel },
    )
}

/** 이 요소의 중심(화면 좌표, px)을 [onOrigin]으로 알린다. 이 값을 [KDialog]의 `origin`에 넘기면 누른 버튼에서 다이얼로그가 커져 나온다. */
public fun Modifier.kOrigin(onOrigin: (Offset) -> Unit): Modifier = onGloballyPositioned { onOrigin(it.boundsOnScreen().center) }

private val WINDOW_BLUR = 28.dp
private const val DIALOG_DAMPING = 0.72f
private const val DIALOG_STIFFNESS = 420f
private const val DIALOG_START_SCALE = 0.6f

// 목록처럼 너비를 스스로 정하지 않는 내용도 너무 좁아지지 않게 한다. 화면이 더 좁으면 화면에 맞춘다.
private val DIALOG_MIN_WIDTH = 280.dp
private val DIALOG_MAX_WIDTH = 420.dp
private const val SHEET_DAMPING = 0.85f
private const val SHEET_DISMISS_FRACTION = 0.3f
private const val SHEET_FLING = 1800f
private const val SCRIM_ALPHA = 0.22f
private const val SOLID_SCRIM_ALPHA = 0.36f
private const val CLOSE_MS = 180
