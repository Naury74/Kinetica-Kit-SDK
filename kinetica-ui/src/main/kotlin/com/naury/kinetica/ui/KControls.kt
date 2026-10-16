// 세그먼트 컨트롤의 칸 모델과 오버로드를 한 파일에 둔다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [KSegmentedControl]의 칸 하나. 글자만, 아이콘만, 또는 아이콘과 글자를 함께 쓸 수 있다.
 * [label]과 [icon]이 둘 다 null이면 [IllegalArgumentException]을 던진다.
 *
 * @property contentDescription 아이콘만 있는 칸을 TalkBack이 읽을 이름. [label]이 있으면 무시한다.
 */
@Immutable
public data class KSegment(val label: String? = null, val icon: ImageVector? = null, val contentDescription: String? = null) {
    init {
        require(label != null || icon != null) { "label과 icon 중 하나는 있어야 합니다." }
    }
}

/**
 * 글래스 세그먼트 버튼. 보기 방식 전환 같은 곳에 쓴다. 선택 인디케이터가 [KTabBar]처럼 늘어났다 줄어들며 이동하고,
 * TalkBack은 항목마다 선택 상태를 읽는다. 아이콘을 넣으려면 [KSegment]를 받는 오버로드를 쓴다.
 *
 * @param options 항목 이름. 두 개 미만이면 [IllegalArgumentException]을 던진다.
 * @param selectedIndex 선택된 항목의 위치(0부터)
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 */
@Composable
public fun KSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(options.size >= 2) { "options는 두 개 이상이어야 합니다." }
    KSegmentedControl(options.map { KSegment(it) }, selectedIndex, onSelect, modifier, enabled, surface)
}

/**
 * 아이콘을 넣을 수 있는 세그먼트 버튼. 하나만 고른다. 동작은 글자만 받는 [KSegmentedControl]과 같다.
 *
 * ```kotlin
 * val segments = listOf(KSegment(icon = ListIcon, contentDescription = "목록"), KSegment(icon = GridIcon, contentDescription = "격자"))
 * KSegmentedControl(segments, view, { view = it })
 * ```
 *
 * @param segments 칸 목록. 두 개 미만이면 [IllegalArgumentException]을 던진다.
 */
@JvmName("KSegmentedControlOfSegments")
@Composable
public fun KSegmentedControl(
    segments: List<KSegment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(segments.size >= 2) { "segments는 두 개 이상이어야 합니다." }
    var width by remember { mutableIntStateOf(0) }
    val indicator = remember { LiquidIndicator() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(selectedIndex, width, segments.size, rtl) { indicator.moveTo(selectedIndex, segments.size, width.toFloat(), rtl) }
    Box(modifier.heightIn(min = SEGMENT_HEIGHT).kSurface(surface, CircleShape).padding(SEGMENT_INSET)) {
        Box(
            Modifier
                .matchParentSize()
                .onSizeChanged { width = it.width }
                .drawBehind {
                    val left = indicator.left.value
                    val right = indicator.right.value
                    if (right > left) {
                        drawRoundRect(
                            surface.contentColor.copy(alpha = SEGMENT_ALPHA),
                            Offset(left, 0f),
                            Size(right - left, size.height),
                            CornerRadius(size.height / 2),
                        )
                    }
                },
        )
        Row(Modifier.matchParentSize().selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            segments.forEachIndexed { index, segment ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected, null, KIndication(CircleShape), enabled, Role.Tab) { onSelect(index) }
                        .describe(segment),
                    contentAlignment = Alignment.Center,
                ) {
                    SegmentContent(segment, surface.contentColor.copy(alpha = segmentAlpha(selected, enabled)), selected)
                }
            }
        }
    }
}

/**
 * 여러 칸을 함께 고르는 세그먼트 버튼. 칸마다 따로 켜고 끄며, 켜진 칸은 옅은 캡슐이 깔리고 이름 앞에 체크 표시가 밀려 들어온다.
 * 필터나 요일 고르기처럼 몇 개를 동시에 켤 때 쓴다. TalkBack은 칸마다 체크 상태를 읽는다.
 *
 * ```kotlin
 * KSegmentedControl(days.map { KSegment(it) }, selectedDays, { selectedDays = it })
 * ```
 *
 * @param segments 칸 목록. 두 개 미만이면 [IllegalArgumentException]을 던진다.
 * @param selected 켜진 칸 위치(0부터) 집합. 범위 밖 값은 무시한다.
 * @param onSelectedChange 바뀐 집합을 받는다.
 * @param requireSelection true면 마지막 남은 칸은 끌 수 없다.
 */
@Composable
public fun KSegmentedControl(
    segments: List<KSegment>,
    selected: Set<Int>,
    onSelectedChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    requireSelection: Boolean = false,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(segments.size >= 2) { "segments는 두 개 이상이어야 합니다." }
    val still = reducedMotion()
    Row(
        modifier.heightIn(min = SEGMENT_HEIGHT).kSurface(surface, CircleShape).padding(SEGMENT_INSET),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, segment ->
            val on = index in selected
            val locked = requireSelection && on && selected.size == 1
            MultiSegment(segment, on, enabled && !locked, enabled, surface.contentColor, still) {
                onSelectedChange(if (on) selected - index else selected + index)
            }
        }
    }
}

@Composable
private fun RowScope.MultiSegment(
    segment: KSegment,
    on: Boolean,
    clickable: Boolean,
    enabled: Boolean,
    ink: Color,
    still: Boolean,
    onToggle: () -> Unit,
) {
    val fill by animateFloatAsState(if (on) 1f else 0f, if (still) tween(0) else tween(FILL_MS), label = "segment-fill")
    Row(
        Modifier
            .weight(1f)
            .heightIn(min = SEGMENT_HEIGHT - SEGMENT_INSET * 2)
            .drawBehind {
                if (fill >
                    0f
                ) {
                    drawRoundRect(ink.copy(alpha = SEGMENT_ALPHA * fill), cornerRadius = CornerRadius(size.height / 2))
                }
            }
            .toggleable(on, null, KIndication(CircleShape), clickable, Role.Checkbox) { onToggle() }
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED }
            .describe(segment)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            on,
            enter = if (still) fadeIn(tween(0)) else expandHorizontally() + fadeIn(),
            exit = if (still) fadeOut(tween(0)) else shrinkHorizontally() + fadeOut(),
        ) {
            Row {
                Glyph(KGlyphs.Check, ink, size = CHECK_SIZE)
                Spacer(Modifier.width(4.dp))
            }
        }
        SegmentContent(segment, ink.copy(alpha = if (on) 1f else UNSELECTED), on)
    }
}

/** 아이콘만 있는 칸에 TalkBack 이름을 붙인다. */
private fun Modifier.describe(segment: KSegment): Modifier {
    val description = segment.contentDescription
    return if (segment.label == null && description != null) semantics { contentDescription = description } else this
}

@Composable
private fun SegmentContent(segment: KSegment, color: Color, selected: Boolean) {
    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        segment.icon?.let {
            Glyph(it, color, size = SEGMENT_ICON)
            if (segment.label != null) Spacer(Modifier.width(6.dp))
        }
        segment.label?.let {
            ThemedText(
                it,
                style = TextStyle(color = color, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun segmentAlpha(selected: Boolean, enabled: Boolean): Float = (if (selected) 1f else UNSELECTED) * (if (enabled) 1f else DISABLED)

private val SEGMENT_HEIGHT = 44.dp
private val SEGMENT_INSET = 4.dp
private val SEGMENT_ICON = 18.dp
private val CHECK_SIZE = 16.dp
private const val SEGMENT_ALPHA = 0.14f
private const val DISABLED = 0.4f
private const val UNSELECTED = 0.6f
private const val FILL_MS = 160
