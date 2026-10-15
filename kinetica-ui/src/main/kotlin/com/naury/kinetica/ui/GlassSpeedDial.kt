package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 화면 구석에 떠 있는 둥근 글래스 버튼과, 누르면 위로 펼쳐지는 동작 목록. 펼치면 + 아이콘이 돌아 ×가 되고,
 * 버튼에 가까운 항목부터 차례로 튀어 오르며 배경이 블러 처리된다. 각 항목은 시작 쪽에 라벨 캡슐을, 버튼 위쪽 열에 아이콘 원을 둔다.
 * 항목을 누르면 목록을 접은 뒤 그 항목의 동작을 실행하고, 바깥을 누르거나 뒤로 가기를 하면 접는다.
 *
 * 목록과 블러 배경은 [GlassScaffold]의 오버레이 레이어에 그리므로 [GlassScaffold] 안에서 쓴다. 버튼의 위치는 호출한 쪽이 정한다.
 * [items]의 첫 항목이 가장 위에 놓인다. [GlassMenuItem.destructive]인 항목은 빨간색으로 표시한다.
 *
 * ```kotlin
 * var open by remember { mutableStateOf(false) }
 * GlassSpeedDial(open, { open = it }, listOf(GlassMenuItem("사진", Icons.Photo) { ... }), Modifier.align(Alignment.BottomEnd).padding(24.dp))
 * ```
 *
 * @param label 버튼의 TalkBack 라벨. `null`이면 기본 문구("빠른 실행")를 쓴다.
 */
@Composable
public fun GlassSpeedDial(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<GlassMenuItem>,
    modifier: Modifier = Modifier,
    style: GlassStyle = LocalGlassStyle.current,
    label: String? = null,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var overlayShown by remember { mutableStateOf(false) }
    val latestItems by rememberUpdatedState(items)
    val latestChange by rememberUpdatedState(onExpandedChange)
    GlassOverlay(expanded) { transition ->
        val progress by transition.animateFloat(
            transitionSpec = { if (targetState) spring(dampingRatio = DIAL_DAMPING, stiffness = DIAL_STIFFNESS) else tween(CLOSE_MS) },
            label = "glass-speed-dial",
        ) { if (it) 1f else 0f }
        BackHandler(enabled = expanded) { latestChange(false) }
        DisposableEffect(Unit) {
            overlayShown = true
            onDispose { overlayShown = false }
        }
        DialLayer(DialSpec(bounds, latestItems, style, transition.targetState, label), { progress }) { latestChange(false) }
    }
    val turn by animateFloatAsState(
        if (expanded) 1f else 0f,
        spring(dampingRatio = DIAL_DAMPING, stiffness = DIAL_STIFFNESS),
        label = "dial",
    )
    // 펼친 동안에는 오버레이에 같은 버튼을 다시 그리므로 원래 버튼은 숨긴다. 뒤쪽 블러에 버튼이 두 번 비치지 않게 하려는 것이다.
    DialButton(
        style,
        expanded,
        label,
        { turn },
        modifier
            .onGloballyPositioned { bounds = it.boundsOnScreen() }
            .graphicsLayer { alpha = if (overlayShown) 0f else 1f },
        hidden = overlayShown,
    ) { latestChange(!expanded) }
}

private class DialSpec(val bounds: Rect, val items: List<GlassMenuItem>, val style: GlassStyle, val active: Boolean, val label: String?)

@Composable
private fun DialLayer(spec: DialSpec, progress: () -> Float, close: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // 오버레이는 다른 창(대화 상자)일 수 있으므로 화면 좌표를 이 상자 좌표로 옮긴다.
    var origin by remember { mutableStateOf(Offset.Zero) }
    val bounds = spec.bounds.translate(-origin)
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }) {
        Scrim(progress().coerceIn(0f, 1f), close)
        Column(
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // 아이콘 열의 가운데를 버튼 가운데에 맞춘다. RTL이면 버튼 오른쪽으로 라벨이 펼쳐진다.
                    val half = ICON_SIZE.roundToPx() / 2f
                    val x = if (rtl) bounds.center.x - half else bounds.center.x + half - placeable.width
                    val y = bounds.top - ITEM_GAP.roundToPx() - placeable.height
                    placeable.place(x.roundToInt(), y.roundToInt())
                }
            },
            verticalArrangement = Arrangement.spacedBy(ITEM_SPACING),
            horizontalAlignment = Alignment.End,
        ) {
            val count = spec.items.size
            spec.items.forEachIndexed { index, item ->
                DialItem(item, spec.style, count - 1 - index, count, progress) {
                    // 접히는 동안에는 항목이 다시 눌려 동작이 두 번 실행되지 않게 한다.
                    if (spec.active) {
                        close()
                        item.onClick()
                    }
                }
            }
        }
        DialButton(
            spec.style,
            spec.active,
            spec.label,
            progress,
            Modifier.absoluteOffset { androidx.compose.ui.unit.IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) },
            onClick = close,
        )
    }
}

@Composable
private fun DialItem(item: GlassMenuItem, style: GlassStyle, fromButton: Int, count: Int, progress: () -> Float, onClick: () -> Unit) {
    val color = if (item.destructive) Color(DESTRUCTIVE) else style.onGlass
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        Modifier
            .graphicsLayer {
                // 버튼에 가까운 항목부터 조금씩 늦게 출발한다. 스프링이 넘치는 값은 그대로 두어 튀어 오르는 느낌을 살린다.
                // 항목이 많아도 늦게 출발하는 몫의 합이 MAX_STAGGER_TOTAL을 넘지 않게 간격을 줄인다.
                val stagger = speedDialStagger(count)
                val delay = stagger * fromButton
                val local = ((progress() - delay) / (1f - stagger * (count - 1).coerceAtLeast(0))).coerceAtLeast(0f)
                alpha = local.coerceAtMost(1f)
                translationY = ITEM_RISE.toPx() * (1f - local)
                val scale = ITEM_START_SCALE + (1f - ITEM_START_SCALE) * local
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(if (rtl) 1f - ITEM_ORIGIN_X else ITEM_ORIGIN_X, 1f)
            }
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.glass(style, CircleShape).padding(horizontal = 16.dp, vertical = 9.dp)) {
            BasicText(item.label, style = TextStyle(color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }
        item.icon?.let { icon ->
            Box(Modifier.size(ICON_SIZE).glass(style, CircleShape), contentAlignment = Alignment.Center) {
                Image(rememberVectorPainter(icon), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(color))
            }
        }
    }
}

@Composable
private fun DialButton(
    style: GlassStyle,
    expanded: Boolean,
    label: String?,
    turn: () -> Float,
    modifier: Modifier,
    hidden: Boolean = false,
    onClick: () -> Unit,
) {
    val name = label ?: stringResource(R.string.kinetica_ui_speed_dial)
    val stateText = stringResource(if (expanded) R.string.kinetica_ui_speed_dial_expanded else R.string.kinetica_ui_speed_dial_collapsed)
    Box(
        modifier
            .size(BUTTON_SIZE)
            .glass(style, CircleShape)
            .then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier.dialAction(expanded, name, stateText, onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(PLUS_SIZE).graphicsLayer { rotationZ = PLUS_TURN * turn() }) {
            val stroke = PLUS_STROKE.toPx()
            val c = center
            drawLine(style.onGlass, Offset(c.x, 0f), Offset(c.x, size.height), stroke, StrokeCap.Round)
            drawLine(style.onGlass, Offset(0f, c.y), Offset(size.width, c.y), stroke, StrokeCap.Round)
        }
    }
}

private fun Modifier.dialAction(expanded: Boolean, name: String, stateText: String, onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
        .semantics {
            contentDescription = name
            stateDescription = stateText
            if (expanded) {
                collapse {
                    onClick()
                    true
                }
            } else {
                expand {
                    onClick()
                    true
                }
            }
        }

private val BUTTON_SIZE = 56.dp
private val ICON_SIZE = 44.dp
private val PLUS_SIZE = 20.dp
private val PLUS_STROKE = 2.4.dp
private const val PLUS_TURN = 135f
private val ITEM_GAP = 16.dp
private val ITEM_SPACING = 12.dp
private val ITEM_RISE = 28.dp
private const val ITEM_START_SCALE = 0.6f
private const val ITEM_ORIGIN_X = 0.9f

/** 항목 [count]개일 때 한 항목씩 늦게 출발하는 간격(진행도 비율). 모든 항목의 지연 합이 [MAX_STAGGER_TOTAL] 이하다. */
internal fun speedDialStagger(count: Int): Float = if (count <= 1) 0f else minOf(STAGGER, MAX_STAGGER_TOTAL / (count - 1))

private const val MAX_STAGGER_TOTAL = 0.5f
private const val STAGGER = 0.07f
private const val DIAL_DAMPING = 0.68f
private const val DIAL_STIFFNESS = Spring.StiffnessMediumLow
private const val CLOSE_MS = 180
private const val DESTRUCTIVE = 0xFFFF3B30
