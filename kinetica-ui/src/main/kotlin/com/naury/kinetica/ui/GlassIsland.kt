package com.naury.kinetica.ui

import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/** [GlassIslandState.show]가 끝난 이유. */
public enum class GlassIslandResult {
    /** 사용자가 펼쳐진 아일랜드를 눌렀다. */
    Clicked,

    /** 위로 쓸어 올렸거나 [GlassIslandState.dismiss]로 닫았다. */
    Dismissed,

    /** 표시 시간이 지나 저절로 닫혔다. */
    TimedOut,
}

/**
 * [GlassIslandHost]에 띄울 알림 내용.
 *
 * [compactText]가 있으면 compact 모드로 시작한다. 왼쪽에 [icon], 오른쪽에 [compactText]만 보이는 좁은 캡슐이고,
 * 누르면 [title]과 [subtitle]이 보이는 expanded 모드로 펼쳐진다. [compactText]가 없으면 처음부터 펼쳐서 보여 준다.
 *
 * @property title 제목. TalkBack이 읽는 내용에도 들어간다.
 * @property subtitle 제목 아래 보조 문구
 * @property icon 왼쪽 아이콘. [accent] 색으로 칠한다.
 * @property accent 아이콘과 compact 문구의 색. [Color.Unspecified]면 글래스 위 글자 색을 쓴다.
 * @property compactText compact 모드 오른쪽에 보일 짧은 글(예: 타이머 `00:42`)
 * @property trailing expanded 모드 오른쪽에 둘 동작 버튼 슬롯
 */
@Stable
public data class GlassIslandContent(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    val accent: Color = Color.Unspecified,
    val compactText: String? = null,
    val trailing: (@Composable () -> Unit)? = null,
)

/**
 * [GlassIslandHost]에 다이내믹 아일랜드식 알림을 띄우는 상태. 여러 알림을 요청하면 한 번에 하나씩 순서대로 보여 주고,
 * 사이에는 캡슐이 사라지지 않고 줄었다가 다시 부푼다.
 */
@Stable
public class GlassIslandState {
    internal var content by mutableStateOf<GlassIslandContent?>(null)
        private set
    internal var expanded by mutableStateOf(false)
    internal var visible by mutableStateOf(false)
        private set
    internal var timeoutAdjust: (Long) -> Long = { it }
    private var pending: CompletableDeferred<GlassIslandResult>? = null
    private var waiting = 0
    private val queue = Mutex()

    /** 알림을 보여 주는 중이면 true. 줄어드는 애니메이션 중에는 false다. */
    public val isShowing: Boolean get() = content != null

    /**
     * [content]를 보여 주고 닫힐 때까지 일시 중단한 뒤 닫힌 이유를 돌려준다. 앞선 알림이 있으면 그 뒤에 보여 준다.
     * 호출한 코루틴이 취소되면 알림도 바로 닫는다.
     *
     * @param durationMs 표시 시간(ms). TalkBack 사용자가 접근성 설정에서 시간을 늘렸으면 그 값을 따른다.
     *   `null`이면 [dismiss]를 부르거나 사용자가 닫을 때까지 보여 준다. 0 이하이면 [IllegalArgumentException]을 던진다.
     */
    public suspend fun show(content: GlassIslandContent, durationMs: Long? = DEFAULT_ISLAND_MS): GlassIslandResult {
        require(durationMs == null || durationMs > 0) { "durationMs는 0보다 커야 합니다." }
        waiting++
        try {
            queue.lock()
        } finally {
            waiting--
        }
        try {
            val done = CompletableDeferred<GlassIslandResult>()
            pending = done
            visible = true
            this.content = content
            expanded = content.compactText == null
            val result = if (durationMs == null) {
                done.await()
            } else {
                withTimeoutOrNull(timeoutAdjust(durationMs)) { done.await() } ?: GlassIslandResult.TimedOut
            }
            clear()
            // 다음 알림이 기다리고 있으면 캡슐을 남겨 두고 바로 다시 부풀린다.
            delay(COLLAPSE_MS)
            if (waiting == 0) {
                visible = false
                delay(HIDE_MS)
            }
            return result
        } catch (e: CancellationException) {
            // 표시 중이든 줄어드는 중이든 취소되면 캡슐이 남지 않게 한다.
            clear()
            visible = waiting > 0
            throw e
        } finally {
            queue.unlock()
        }
    }

    /** 보여 주는 중인 알림의 내용을 바꾼다. 타이머처럼 계속 바뀌는 값을 갱신할 때 쓴다. 보여 주는 알림이 없으면 아무 일도 하지 않는다. */
    public fun update(content: GlassIslandContent) {
        if (this.content != null) this.content = content
    }

    /** 보여 주는 중인 알림을 닫는다. [show]는 [GlassIslandResult.Dismissed]를 돌려준다. */
    public fun dismiss() {
        pending?.complete(GlassIslandResult.Dismissed)
    }

    /** compact 모드면 펼치고, 펼쳐져 있으면 [GlassIslandResult.Clicked]로 끝낸다. */
    internal fun tap() {
        if (expanded) pending?.complete(GlassIslandResult.Clicked) else expanded = true
    }

    private fun clear() {
        pending = null
        content = null
        expanded = false
    }
}

@Composable
public fun rememberGlassIslandState(): GlassIslandState = remember { GlassIslandState() }

/** [GlassIslandHost]의 기본 스타일. */
public object GlassIslandDefaults {
    /** 블러 없이 불투명한 검은 캡슐. 화면 위쪽의 카메라 홀과 이어져 보인다. */
    @JvmField
    public val Black: GlassStyle = GlassStyle(
        blur = 0.dp,
        saturation = 1f,
        tint = Color.Black,
        edgeLight = Color.Transparent,
        shadow = 6.dp,
        fallback = Color.Black,
        onGlass = Color.White,
    )
}

/**
 * 화면 맨 위 가운데에 뜨는 다이내믹 아일랜드식 알림. 작은 캡슐에서 시작해 내용이 오면 용수철처럼 넓고 높게 부풀고,
 * 시간이 지나면 다시 캡슐로 줄어든다. 위로 쓸어 올리면 닫히고, 펼쳐진 알림을 누르면 [GlassIslandResult.Clicked]로 끝난다.
 * 상태 표시줄 바로 아래에 놓인다(상태 표시줄 영역은 시스템이 터치를 가져가 누를 수 없다). [GlassScaffold] 안에서는 오버레이 레이어에 그린다.
 * 밖에서는 내용 크기의 팝업 창에 띄우므로 호출한 자리의 배치에 끼어들지 않고 뒤 화면의 터치도 막지 않는다.
 * TalkBack은 새 알림을 바로 읽어 준다.
 *
 * @param alwaysVisible true면 알림이 없을 때도 빈 캡슐을 보여 준다.
 */
@Composable
public fun GlassIslandHost(
    state: GlassIslandState,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassIslandDefaults.Black,
    alwaysVisible: Boolean = false,
) {
    val adjust = rememberAccessibleTimeout(withControls = true)
    SideEffect { state.timeoutAdjust = adjust }
    // 상태 표시줄 영역의 터치는 시스템이 먼저 가져가므로, 눌러서 펼칠 수 있게 상태 표시줄 바로 아래에 놓는다.
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val gap = if (WindowInsets.statusBars.getTop(LocalDensity.current) > 0) ISLAND_GAP else MIN_TOP
    GlassOverlay(state.visible || alwaysVisible, OverlayKind.Top, insets) { transition ->
        val appear = transition.animateFloat(
            transitionSpec = { if (targetState) spring(ISLAND_DAMPING, Spring.StiffnessMedium) else tween(HIDE_MS.toInt()) },
            label = "glass-island",
        ) { if (it) 1f else 0f }
        Box(Modifier.padding(top = gap, start = SIDE_MARGIN, end = SIDE_MARGIN)) {
            Island(
                state,
                style,
                modifier.graphicsLayer {
                    val p = appear.value
                    val scale = APPEAR_SCALE + (1f - APPEAR_SCALE) * p
                    scaleX = scale
                    scaleY = scale
                    alpha = p.coerceIn(0f, 1f)
                    transformOrigin = TOP_CENTER
                },
            )
        }
    }
}

@Composable
private fun Island(state: GlassIslandState, style: GlassStyle, modifier: Modifier) {
    val density = LocalDensity.current
    val pill = with(density) { Size(PILL_WIDTH.toPx(), PILL_HEIGHT.toPx()) }
    val width = remember { Animatable(pill.width) }
    val height = remember { Animatable(pill.height) }
    val bodyAlpha = remember { Animatable(0f) }
    var natural by remember { mutableStateOf(IntSize.Zero) }
    var last by remember { mutableStateOf<GlassIslandContent?>(null) }
    state.content?.let { last = it }
    val showing = state.content != null
    LaunchedEffect(natural, showing, state.expanded) {
        val grow = showing && natural.width > 0
        val spec = spring<Float>(dampingRatio = ISLAND_DAMPING, stiffness = ISLAND_STIFFNESS)
        launch { width.animateTo(if (grow) natural.width.toFloat() else pill.width, spec) }
        launch { height.animateTo(if (grow) natural.height.toFloat() else pill.height, spec) }
        bodyAlpha.animateTo(if (showing) 1f else 0f, tween(if (showing) FADE_IN_MS else FADE_OUT_MS, if (showing) FADE_IN_DELAY else 0))
    }
    val shape = remember { CapsuleShape(ISLAND_MAX_CORNER) }
    val clip = remember { Path() }
    val maxWidth = with(density) { ISLAND_MAX_WIDTH.roundToPx() }
    Layout(
        content = {
            Box(Modifier.glass(style, shape))
            Box(
                Modifier
                    .onSizeChanged { natural = it }
                    .drawWithContent {
                        // 아일랜드가 아직 덜 부푼 동안 내용이 바깥으로 삐져나오지 않게 지금 크기의 캡슐로 자른다.
                        val w = width.value
                        val h = height.value
                        val left = (size.width - w) / 2
                        val topEdge = (size.height - h) / 2
                        val corner = min(h / 2, ISLAND_MAX_CORNER.toPx())
                        clip.reset()
                        clip.addRoundRect(RoundRect(left, topEdge, left + w, topEdge + h, CornerRadius(corner)))
                        clipPath(clip) { this@drawWithContent.drawContent() }
                    }
                    .graphicsLayer { alpha = bodyAlpha.value },
            ) {
                last?.let { IslandBody(it, state.expanded, style) }
            }
        },
        modifier = modifier.islandGestures(state).islandSemantics(state, last),
    ) { measurables, constraints ->
        val w = width.value.roundToInt().coerceAtLeast(0)
        val h = height.value.roundToInt().coerceAtLeast(0)
        val body = measurables[1].measure(Constraints(maxWidth = min(constraints.maxWidth, maxWidth)))
        val background = measurables[0].measure(Constraints.fixed(w, h))
        layout(w, h) {
            background.place(0, 0)
            body.place((w - body.width) / 2, (h - body.height) / 2)
        }
    }
}

@Composable
private fun Modifier.islandGestures(state: GlassIslandState): Modifier {
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { SWIPE_DISMISS.toPx() }
    return this
        .graphicsLayer { translationY = drag.value }
        .draggable(
            rememberDraggableState { delta ->
                // 아래로는 조금만 따라오게 해서 붙잡힌 느낌을 준다.
                val next = drag.value + if (drag.value + delta > 0f) delta * PULL_DOWN_RESIST else delta
                scope.launch { drag.snapTo(next) }
            },
            Orientation.Vertical,
            enabled = state.content != null,
            onDragStopped = { velocity ->
                if (drag.value < -threshold || velocity < -SWIPE_FLING) state.dismiss()
                drag.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            },
        )
        .pointerInput(state) { detectTapGestures { if (state.content != null) state.tap() } }
}

@Composable
private fun Modifier.islandSemantics(state: GlassIslandState, content: GlassIslandContent?): Modifier {
    if (state.content == null || content == null) return clearAndSetSemantics {}
    val dismissLabel = stringResource(R.string.kinetica_ui_notify_dismiss)
    val clickLabel = stringResource(if (state.expanded) R.string.kinetica_ui_notify_open else R.string.kinetica_ui_notify_expand)
    return semantics(mergeDescendants = true) {
        liveRegion = LiveRegionMode.Polite
        role = Role.Button
        // compact 모드에서는 제목이 화면에 없으므로 따로 알려 준다. 펼치면 제목과 부제 글자가 합쳐져 읽힌다.
        if (!state.expanded) contentDescription = content.title
        onClick(clickLabel) {
            state.tap()
            true
        }
        dismiss(dismissLabel) {
            state.dismiss()
            true
        }
    }
}

@Composable
private fun IslandBody(content: GlassIslandContent, expanded: Boolean, style: GlassStyle) {
    val accent = if (content.accent == Color.Unspecified) style.onGlass else content.accent
    CompositionLocalProvider(LocalGlassContentColor provides style.onGlass) {
        if (expanded) ExpandedBody(content, accent, style) else CompactBody(content, accent)
    }
}

@Composable
private fun CompactBody(content: GlassIslandContent, accent: Color) {
    Row(
        Modifier.height(PILL_HEIGHT).widthIn(min = COMPACT_MIN_WIDTH).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        content.icon?.let { IslandIcon(it, accent, COMPACT_ICON) } ?: Spacer(Modifier.size(COMPACT_ICON))
        // 가운데는 비워 카메라 홀을 피한다.
        Spacer(Modifier.widthIn(min = COMPACT_GAP))
        ThemedText(
            content.compactText.orEmpty(),
            style = TextStyle(color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
            maxLines = 1,
        )
    }
}

@Composable
private fun ExpandedBody(content: GlassIslandContent, accent: Color, style: GlassStyle) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content.icon?.let {
            Box(
                Modifier.size(EXPANDED_ICON_BOX).background(accent.copy(alpha = ICON_BACK_ALPHA), CircleShape),
                contentAlignment = Alignment.Center,
            ) { IslandIcon(it, accent, EXPANDED_ICON) }
            Spacer(Modifier.size(12.dp))
        }
        Column(Modifier.weight(1f)) {
            ThemedText(
                content.title,
                style = TextStyle(color = style.onGlass, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            content.subtitle?.let {
                ThemedText(
                    it,
                    style = TextStyle(color = style.onGlass.copy(alpha = SUBTITLE_ALPHA), fontSize = 13.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        content.trailing?.let {
            Spacer(Modifier.size(12.dp))
            it()
        }
    }
}

@Composable
private fun IslandIcon(icon: ImageVector, tint: Color, size: Dp) {
    Image(rememberVectorPainter(icon), contentDescription = null, Modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** 높이의 절반과 [maxCorner] 중 작은 값을 모서리 반경으로 쓰는 모양. 낮을 때는 캡슐, 높을 때는 둥근 사각형이 된다. */
internal class CapsuleShape(private val maxCorner: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = min(size.height / 2, with(density) { maxCorner.toPx() })
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner)))
    }

    override fun equals(other: Any?): Boolean = other is CapsuleShape && other.maxCorner == maxCorner

    override fun hashCode(): Int = maxCorner.hashCode()
}

/**
 * 접근성 설정의 '조치를 취할 시간'을 반영한 표시 시간(ms)을 돌려주는 함수. API 29 미만이거나 설정이 없으면 받은 값을 그대로 돌려준다.
 */
@Composable
internal fun rememberAccessibleTimeout(withControls: Boolean): (Long) -> Long {
    val context = LocalContext.current
    return remember(context, withControls) {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val adjust: (Long) -> Long = { ms ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && manager != null) {
                val flags = AccessibilityManager.FLAG_CONTENT_TEXT or
                    AccessibilityManager.FLAG_CONTENT_ICONS or
                    (if (withControls) AccessibilityManager.FLAG_CONTENT_CONTROLS else 0)
                maxOf(ms, manager.getRecommendedTimeoutMillis(ms.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), flags).toLong())
            } else {
                ms
            }
        }
        adjust
    }
}

private const val DEFAULT_ISLAND_MS = 4_000L
private const val COLLAPSE_MS = 420L
private const val HIDE_MS = 220L
private const val ISLAND_DAMPING = 0.68f
private const val ISLAND_STIFFNESS = 320f
private const val APPEAR_SCALE = 0.6f
private const val FADE_IN_MS = 240
private const val FADE_IN_DELAY = 90
private const val FADE_OUT_MS = 120
private const val PULL_DOWN_RESIST = 0.3f
private const val SWIPE_FLING = 900f
private const val ICON_BACK_ALPHA = 0.18f
private const val SUBTITLE_ALPHA = 0.7f
private const val TABULAR = "tnum"
private val TOP_CENTER = TransformOrigin(0.5f, 0f)
private val PILL_WIDTH = 120.dp
private val ISLAND_GAP = 6.dp
private val PILL_HEIGHT = 36.dp
private val MIN_TOP = 8.dp
private val SIDE_MARGIN = 12.dp
private val ISLAND_MAX_WIDTH = 420.dp
private val ISLAND_MAX_CORNER = 32.dp
private val COMPACT_MIN_WIDTH = 168.dp
private val COMPACT_GAP = 48.dp
private val COMPACT_ICON = 20.dp
private val EXPANDED_ICON = 24.dp
private val EXPANDED_ICON_BOX = 44.dp
private val SWIPE_DISMISS = 24.dp
