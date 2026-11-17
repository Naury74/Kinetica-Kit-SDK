package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import kotlin.math.roundToInt

/** 일정 하나를 놓을 자리. 시각은 범위에 맞춰 자른 분이고, [column]은 겹침 묶음 [columns]개 열 가운데 몇 번째인지다. */
internal class DayEventSlot(val event: KDayEvent, val start: Int, val end: Int, val column: Int, val columns: Int)

/**
 * 일정을 겹침 묶음으로 나눠 열을 정한다. 시작 순(같으면 긴 것 먼저)으로 훑으며, 앞 일정과 이어서 겹치는 일정은 한 묶음이 되고
 * 묶음 안에서는 비어 있는 가장 왼쪽 열을 받는다. 묶음의 열 수는 그 묶음이 쓴 열 수다.
 * 화면에서 [minMinutes]보다 짧은 일정도 그 길이로 그리므로 겹침은 그 길이로 판단한다. 범위 밖 일정은 빠진다.
 */
internal fun arrangeDayEvents(events: List<KDayEvent>, range: DayRange, minMinutes: Float): List<DayEventSlot> {
    class Item(val event: KDayEvent, val start: Int, val end: Int, val shown: Float)
    val items = events.mapNotNull { event ->
        val start = (event.start.toSecondOfDay() / DAY_SECONDS_PER_MINUTE).coerceAtLeast(range.start)
        val rawEnd = (event.end.toSecondOfDay() + DAY_SECONDS_PER_MINUTE - 1) / DAY_SECONDS_PER_MINUTE
        val end = rawEnd.coerceAtMost(range.end)
        if (end <= start) null else Item(event, start, end, maxOf(end.toFloat(), start + minMinutes))
    }.sortedWith(compareBy<Item> { it.start }.thenByDescending { it.end })
    val out = ArrayList<DayEventSlot>(items.size)
    val group = ArrayList<Pair<Item, Int>>()
    val columnEnds = ArrayList<Float>()
    var groupEnd = Float.NEGATIVE_INFINITY
    fun close() {
        group.forEach { (item, column) -> out += DayEventSlot(item.event, item.start, item.end, column, columnEnds.size) }
        group.clear()
        columnEnds.clear()
    }
    for (item in items) {
        if (item.start >= groupEnd) {
            close()
            groupEnd = item.shown
        } else {
            groupEnd = maxOf(groupEnd, item.shown)
        }
        val free = columnEnds.indexOfFirst { it <= item.start }
        val column = if (free >= 0) free else columnEnds.size.also { columnEnds += 0f }
        columnEnds[column] = item.shown
        group += item to column
    }
    close()
    return out
}

/** 시간 눈금 글 열. 글 폭에 맞춰 열 폭을 정하므로 큰 글꼴에서도 잘리지 않는다. */
@Composable
internal fun HourGutter(range: DayRange, formats: TimeFormats, onLongPressTime: ((LocalTime) -> Unit)?, surface: SurfaceStyle) {
    val hours = (range.start / DAY_MINUTES_PER_HOUR) until (range.end / DAY_MINUTES_PER_HOUR)
    val color = surface.muted(GUTTER_ALPHA)
    val style = TextStyle(color = color, fontSize = 11.sp)
    val onLong by rememberUpdatedState(onLongPressTime)
    Layout(
        content = {
            for (hour in hours) {
                val time = LocalTime.of(hour, 0)
                val text = formats.hour.format(time)
                val label = stringResource(R.string.kinetica_ui_day_new_event, formats.time.format(time))
                ThemedText(
                    text,
                    Modifier.clearAndSetSemantics {
                        // 시간 눈금은 길게 눌러 일정을 만들 수 있을 때만 TalkBack에 남긴다. 아니면 칸마다 같은 글을 읽어 시끄럽다.
                        if (onLongPressTime != null) {
                            contentDescription = formats.time.format(time)
                            customActions = listOf(
                                CustomAccessibilityAction(label) {
                                    onLong?.invoke(time)
                                    true
                                },
                            )
                        }
                    },
                    style = style,
                    maxLines = 1,
                )
            }
        },
        modifier = Modifier.padding(horizontal = GUTTER_PADDING),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = placeables.maxOfOrNull { it.width } ?: 0
        val height = constraints.maxHeight.takeIf { it != Constraints.Infinity } ?: 0
        val hourPx = if (placeables.isEmpty()) 0f else height.toFloat() / placeables.size
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                val y = (hourPx * i - p.height / 2f).roundToInt().coerceIn(0, (height - p.height).coerceAtLeast(0))
                p.placeRelative(width - p.width, y)
            }
        }
    }
}

/** 일정 칸이 그리는 데 필요한 값. */
internal class DayGridSpec(
    val range: DayRange,
    val pxPerMinute: Float,
    val clock: MinuteClock,
    val formats: TimeFormats,
    val surface: SurfaceStyle,
)

@Composable
internal fun DayGrid(
    slots: List<DayEventSlot>,
    spec: DayGridSpec,
    onEventClick: ((KDayEvent) -> Unit)?,
    onLongPressTime: ((LocalTime) -> Unit)?,
    modifier: Modifier,
) {
    val rects = remember { ArrayList<Rect>() }
    val ink = spec.surface.contentColor
    val accent = KineticaTheme.colors.accent
    val onLong by rememberUpdatedState(onLongPressTime)
    val haptic = kHapticFeedback()
    val gap = with(LocalDensity.current) { BLOCK_GAP.roundToPx() }
    Layout(
        content = {
            slots.forEach { slot -> key(slot.event.id) { EventBlock(slot, spec, onEventClick) } }
        },
        modifier = modifier
            .onVisibilityChanged(minFractionVisible = 0f, callback = spec.clock.visibility)
            .hourLines(spec.range, spec.pxPerMinute, ink)
            .nowLine(spec, accent)
            .pointerInput(onLongPressTime != null) {
                if (onLongPressTime == null) return@pointerInput
                detectTapGestures(
                    onLongPress = { at ->
                        if (rects.any { it.contains(at) }) return@detectTapGestures
                        val minute = snapMinute(at.y, spec.range, spec.pxPerMinute)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLong?.invoke(LocalTime.of(minute / DAY_MINUTES_PER_HOUR, minute % DAY_MINUTES_PER_HOUR))
                    },
                )
            },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight.takeIf { it != Constraints.Infinity } ?: 0
        val minHeight = DAY_MIN_BLOCK_HEIGHT.roundToPx()
        val placeables = measurables.mapIndexed { i, m ->
            val slot = slots[i]
            val columnWidth = ((width - gap * (slot.columns - 1)) / slot.columns).coerceAtLeast(0)
            val blockHeight = maxOf(((slot.end - slot.start) * spec.pxPerMinute).roundToInt() - gap, minHeight)
            m.measure(Constraints.fixed(columnWidth, blockHeight))
        }
        rects.clear()
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                val slot = slots[i]
                val x = slot.column * (p.width + gap)
                val y = ((slot.start - spec.range.start) * spec.pxPerMinute).roundToInt()
                p.placeRelative(x, y)
                val left = if (layoutDirection == LayoutDirection.Rtl) width - x - p.width else x
                rects += Rect(left.toFloat(), y.toFloat(), (left + p.width).toFloat(), (y + p.height).toFloat())
            }
        }
    }
}

/** 길게 누른 y(px)의 시각(분)을 15분 단위로 내린다. 범위 끝에서는 마지막 15분 칸 시작으로 맞춘다. */
internal fun snapMinute(y: Float, range: DayRange, pxPerMinute: Float): Int {
    val raw = range.start + (y / pxPerMinute).toInt()
    val snapped = raw / SNAP_MINUTES * SNAP_MINUTES
    return snapped.coerceIn(range.start, (range.end - SNAP_MINUTES).coerceAtLeast(range.start))
}

@Composable
private fun EventBlock(slot: DayEventSlot, spec: DayGridSpec, onClick: ((KDayEvent) -> Unit)?) {
    val event = slot.event
    val color = if (event.color == Color.Unspecified) KineticaTheme.colors.accent else event.color
    val ink = spec.surface.contentColor
    val from = spec.formats.time.format(event.start)
    val to = spec.formats.time.format(event.end)
    val description = stringResource(R.string.kinetica_ui_day_event, event.title, from, to)
    val compact = (slot.end - slot.start) * spec.pxPerMinute < with(LocalDensity.current) { COMPACT_HEIGHT.toPx() }
    val shape = shapeFor { KShapeToken.Small }
    val click = onClick?.let { Modifier.kClickable(shape) { it(event) } } ?: Modifier
    Column(
        Modifier
            .clip(shape)
            .background(color.copy(alpha = BLOCK_FILL))
            .drawBehind {
                val bar = BAR_WIDTH.toPx()
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - bar else 0f
                drawRect(color, Offset(x, 0f), Size(bar, size.height))
            }
            .then(click)
            .clearAndSetSemantics {
                contentDescription = description
                if (onClick != null) {
                    role = Role.Button
                    onClick {
                        onClick(event)
                        true
                    }
                }
            }
            .padding(start = BLOCK_PADDING_START, end = BLOCK_PADDING, top = BLOCK_PADDING_TOP),
    ) {
        ThemedText(
            event.title,
            style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!compact) {
            val sub = listOfNotNull("$from – $to", event.subtitle).joinToString("  ")
            ThemedText(
                sub,
                style = TextStyle(color = ink.copy(alpha = SUB_ALPHA), fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 정시마다 가로선, 30분마다 옅은 선을 긋는다. 범위와 크기가 그대로면 다시 그리지 않는다. */
private fun Modifier.hourLines(range: DayRange, pxPerMinute: Float, ink: Color): Modifier = drawBehind {
    val strong = ink.copy(alpha = LINE_ALPHA)
    val faint = ink.copy(alpha = HALF_LINE_ALPHA)
    val stroke = LINE_WIDTH.toPx()
    var minute = range.start
    while (minute <= range.end) {
        val y = (minute - range.start) * pxPerMinute
        drawLine(if (minute % DAY_MINUTES_PER_HOUR == 0) strong else faint, Offset(0f, y), Offset(size.width, y), stroke)
        minute += HALF_HOUR
    }
}

/** 지금 시각 선. 시각은 그리기 단계에서 읽으므로 1분마다 다시 그리기만 한다. */
private fun Modifier.nowLine(spec: DayGridSpec, color: Color): Modifier = drawWithContent {
    drawContent()
    val now = spec.clock.now.toSecondOfDay() / DAY_SECONDS_PER_MINUTE
    if (now < spec.range.start || now > spec.range.end) return@drawWithContent
    val y = (now - spec.range.start) * spec.pxPerMinute
    val dot = NOW_DOT.toPx()
    val x = if (layoutDirection == LayoutDirection.Rtl) size.width else 0f
    drawLine(color, Offset(0f, y), Offset(size.width, y), NOW_WIDTH.toPx())
    drawCircle(color, dot, Offset(x, y))
}

private const val SNAP_MINUTES = 15
private const val HALF_HOUR = 30
private const val GUTTER_ALPHA = 0.6f
private const val LINE_ALPHA = 0.12f
private const val HALF_LINE_ALPHA = 0.05f
private const val BLOCK_FILL = 0.16f
private const val SUB_ALPHA = 0.65f
private val GUTTER_PADDING = 8.dp
private val BLOCK_GAP = 2.dp
private val BAR_WIDTH = 3.dp
private val BLOCK_PADDING_START = 9.dp
private val BLOCK_PADDING = 6.dp
private val BLOCK_PADDING_TOP = 3.dp
private val COMPACT_HEIGHT = 40.dp
private val LINE_WIDTH = 1.dp
private val NOW_WIDTH = 2.dp
private val NOW_DOT = 4.dp
