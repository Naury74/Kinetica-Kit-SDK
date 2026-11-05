package com.naury.kinetica.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 손가락이나 마우스가 가까운 아이콘이 커지는 독. 손가락을 댄 채 훑으면 지나가는 곳의 아이콘이 이어서 커지고, 손가락을 뗀 곳의 아이콘이 실행된다.
 * 손가락을 독 위아래로 멀리 벗어나 떼면 아무것도 실행하지 않는다. 마우스는 올리기만 해도 커지고 누르면 실행된다. 태블릿이나 데스크톱 모드의
 * 앱 실행 막대에 쓴다. 휴대전화처럼 좁은 곳에서는 아이콘이 다 들어가도록 [iconSize]를 줄인다.
 *
 * 크기는 배치 단계에서만 바뀌므로 훑는 동안 다시 구성하지 않는다. 아이콘은 키보드 Tab과 방향키로 옮겨 가고 Enter나 Space로 실행한다.
 * TalkBack에서는 버튼 목록으로 읽히고 실행 중인 아이콘은 그 상태를 함께 읽는다.
 *
 * ```kotlin
 * KDock(apps.map { app -> KDockItem(app.name, app.running, { open(app) }) { AppIcon(app) } })
 * ```
 *
 * @param iconSize 쉬고 있을 때의 아이콘 크기
 * @param magnification 가장 크게 커질 때의 배율(1..3). 범위를 벗어나면 가까운 끝 값으로 맞춘다. 1이면 커지지 않는다.
 * @param reach 손가락에서 이 거리까지 떨어진 아이콘이 커진다.
 * @param showLabels 가리키는 아이콘 위에 이름을 띄울지
 */
@Composable
public fun KDock(
    items: List<KDockItem>,
    modifier: Modifier = Modifier,
    iconSize: Dp = 48.dp,
    magnification: Float = 1.8f,
    reach: Dp = 150.dp,
    showLabels: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val model = remember { DockModel() }
    val mag = magnification.coerceIn(1f, MAX_MAGNIFICATION)
    val still = reducedMotion()
    val latest by rememberUpdatedState(items)
    val haptic = kHapticFeedback()
    LaunchedEffect(model.active, still) {
        model.presence.animateTo(
            if (model.active) 1f else 0f,
            if (still) snap() else spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
        )
    }
    LaunchedEffect(model.hovered) { if (model.hovered >= 0 && model.active) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    val shape = shapeFor { card }
    Layout(
        contents = listOf(
            { Box(Modifier.kSurface(surface, shape)) },
            { items.forEach { DockIcon(it, surface) } },
            { if (showLabels) latest.getOrNull(model.hovered)?.let { DockLabel(it.label, surface) } },
        ),
        modifier = modifier.dockPointer(model) { index -> latest.getOrNull(index)?.onClick?.invoke() },
    ) { (panel, icons, label), constraints ->
        measureDock(DockLook(model, iconSize, mag, reach), panel, icons, label.firstOrNull(), constraints)
    }
}

private class DockLook(val model: DockModel, val iconSize: Dp, val magnification: Float, val reach: Dp)

/** 독을 잰다. 손가락 위치를 여기서 읽으므로 훑는 동안 다시 구성하지 않고 다시 재고 배치만 한다. */
private fun MeasureScope.measureDock(
    look: DockLook,
    panel: List<Measurable>,
    icons: List<Measurable>,
    label: Measurable?,
    constraints: Constraints,
): MeasureResult {
    val model = look.model
    val gap = GAP.toPx()
    val pad = PAD.toPx()
    val dot = DOT_ROOM.toPx()
    val count = icons.size
    val wanted = look.iconSize.toPx()
    val growth = dockGrowth(count, wanted, gap, look.magnification, look.reach.toPx())
    val needed = count * wanted + (count - 1).coerceAtLeast(0) * gap + growth + pad * 2
    // 좁은 화면에서는 커질 자리까지 다 들어가도록 아이콘을 줄인다.
    val fit = if (constraints.hasBoundedWidth && needed > constraints.maxWidth) constraints.maxWidth / needed else 1f
    val base = wanted * fit
    val restWidth = count * base + (count - 1).coerceAtLeast(0) * gap
    val width = constraints.constrainWidth(ceil(restWidth + growth * fit + pad * 2).toInt())
    val height = constraints.constrainHeight(ceil(base * look.magnification + pad * 2 + dot).toInt())
    model.gap = gap
    model.magnification = look.magnification
    model.reach = look.reach.toPx()
    model.measure(count, (width - restWidth) / 2f, base)
    val rowWidth = model.arrange(0f)
    val rowLeft = (width - rowWidth) / 2f
    model.arrange(rowLeft)
    val iconPlaceables = icons.mapIndexed { i, m -> m.measure(Constraints.fixed(model.sizes[i].roundToInt(), model.sizes[i].roundToInt())) }
    val panelPlaceable = panel.first().measure(Constraints.fixed((rowWidth + pad * 2).roundToInt(), (base + pad * 2 + dot).roundToInt()))
    val labelPlaceable = label?.measure(Constraints())
    return layout(width, height) {
        val bottom = height - pad - dot
        panelPlaceable.place((rowLeft - pad).roundToInt(), height - panelPlaceable.height)
        iconPlaceables.forEachIndexed { i, p -> p.place((model.centers[i] - p.width / 2f).roundToInt(), (bottom - p.height).roundToInt()) }
        val hovered = model.hovered
        if (labelPlaceable != null && hovered in iconPlaceables.indices) {
            val top = bottom - iconPlaceables[hovered].height - LABEL_GAP.toPx() - labelPlaceable.height
            val x = (model.centers[hovered] - labelPlaceable.width / 2f).coerceIn(
                0f,
                (width - labelPlaceable.width).toFloat().coerceAtLeast(0f),
            )
            labelPlaceable.place(x.roundToInt(), top.roundToInt())
        }
    }
}

/**
 * 손가락과 마우스를 따라 [DockModel]을 바꾸고, 손가락을 떼거나 마우스를 누른 곳의 아이콘을 [onLaunch]로 알린다.
 * 독 위아래로 손가락 하나 높이보다 멀리 벗어나 떼면 취소로 본다.
 */
private fun Modifier.dockPointer(model: DockModel, onLaunch: (Int) -> Unit): Modifier = pointerInput(model) {
    val slop = CANCEL_SLOP.toPx()
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            // 부모가 터치를 가로채면 Compose는 이미 소비된 놓기 사건을 보낸다. 이것은 손가락을 뗀 것이 아니라 취소이므로 실행하지 않는다.
            val cancelled = event.type == PointerEventType.Release && change.isConsumed
            val inside = !cancelled && change.position.y >= -slop && change.position.y <= size.height + slop
            val launch = model.follow(event.type, change.position.x, inside, change.type == PointerType.Mouse)
            if (launch >= 0) onLaunch(launch)
            if (model.pressed) change.consume()
        }
    }
}

@Composable
private fun DockIcon(item: KDockItem, surface: SurfaceStyle) {
    val source = remember { MutableInteractionSource() }
    val running = stringResource(R.string.kinetica_ui_dock_running)
    val latest by rememberUpdatedState(item)
    val dotColor = surface.contentColor.copy(alpha = DOT_ALPHA)
    Box(
        Modifier
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = item.label
                if (item.running) stateDescription = running
                onClick {
                    latest.onClick()
                    true
                }
            }
            .drawBehind {
                if (latest.running) drawCircle(dotColor, DOT_RADIUS.toPx(), Offset(size.width / 2f, size.height + DOT_ROOM.toPx() / 2f))
            }
            .onKeyEvent { event ->
                val launch =
                    event.type == KeyEventType.KeyUp &&
                        (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar)
                if (launch) latest.onClick()
                launch
            }
            .indication(source, KIndication(shapeFor { card }))
            .focusable(interactionSource = source),
    ) { item.icon() }
}

@Composable
private fun DockLabel(text: String, surface: SurfaceStyle) {
    Box(Modifier.kSurface(surface, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
        ThemedText(text, style = TextStyle(color = surface.contentColor, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

private val GAP = 8.dp
private val PAD = 8.dp
private val DOT_ROOM = 8.dp
private val DOT_RADIUS = 2.dp
private val LABEL_GAP = 6.dp
private val CANCEL_SLOP = 48.dp
private const val MAX_MAGNIFICATION = 3f
private const val DOT_ALPHA = 0.7f
