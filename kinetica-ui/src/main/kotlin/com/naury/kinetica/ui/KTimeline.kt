package com.naury.kinetica.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/** [KTimeline] 항목의 상태 */
public enum class KTimelineStatus {
    /** 끝난 단계. 강조색으로 채운 점에 체크 표시를 그리고, 다음 항목까지 선을 실선으로 잇는다. */
    Done,

    /** 지금 단계. 강조색 테두리 점 둘레가 천천히 숨 쉬듯 퍼진다. '애니메이션 삭제' 설정에서는 멈춘다. */
    Current,

    /** 아직 오지 않은 단계. 빈 점과 점선으로 그리고 글자를 옅게 쓴다. */
    Upcoming,

    /** 문제가 생긴 단계. [KineticaColors.destructive] 점에 느낌표를 그린다. */
    Error,
}

/**
 * [KTimeline]의 항목 하나.
 *
 * @property title 항목 제목. 예: "배송 출발"
 * @property subtitle 보조 설명. 예: "서울 송파 물류센터"
 * @property time 시각이나 날짜 글자. 형식은 호출한 쪽이 정한다.
 * @property icon 점 안에 체크 대신 그릴 아이콘. [KTimelineStatus.Done]과 [KTimelineStatus.Current]에서만 쓴다.
 */
@Immutable
public data class KTimelineItem(
    val title: String,
    val subtitle: String? = null,
    val time: String? = null,
    val status: KTimelineStatus = KTimelineStatus.Upcoming,
    val icon: ImageVector? = null,
)

/** [KTimeline]의 방향 */
public enum class KTimelineOrientation {
    /** 위에서 아래로. 배송 추적, 주문 내역, 활동 기록처럼 설명이 긴 항목에 쓴다. */
    Vertical,

    /** 시작 쪽에서 끝 쪽으로. 짧은 이름의 단계 3~6개를 한 줄로 보일 때 쓴다. 칸이 모자라면 가로로 스크롤한다. */
    Horizontal,
}

/**
 * 타임라인. 점과 선으로 항목을 잇고 항목마다 상태([KTimelineStatus])를 보인다.
 * 끝난 항목 사이는 강조색 실선, 남은 구간은 옅은 점선이다. 가로 타임라인은 단계의 흐름이라 RTL에서 오른쪽부터 놓는다
 * (차트의 시간 축은 왼쪽이 처음이지만, 단계 표시는 읽는 방향을 따른다).
 *
 * TalkBack은 목록으로 읽고, 항목마다 제목, 설명, 시각과 함께 '완료', '진행 중' 같은 상태를 알린다.
 *
 * ```kotlin
 * KTimeline(
 *     listOf(
 *         KTimelineItem("주문 접수", time = "10:02", status = KTimelineStatus.Done),
 *         KTimelineItem("배송 중", status = KTimelineStatus.Current),
 *     ),
 * )
 * ```
 *
 * @param itemContent 항목 아래에 덧붙일 내용(사진, 버튼). 세로 타임라인에서만 쓴다.
 */
@Composable
public fun KTimeline(
    items: List<KTimelineItem>,
    modifier: Modifier = Modifier,
    orientation: KTimelineOrientation = KTimelineOrientation.Vertical,
    surface: SurfaceStyle = KineticaTheme.surface,
    itemContent: (@Composable (index: Int) -> Unit)? = null,
) {
    val look = TimelineLook(KineticaTheme.colors.accent, KineticaTheme.colors.destructive, surface.contentColor, surface)
    val states = items.map { timelineState(it.status) }
    if (orientation == KTimelineOrientation.Vertical) {
        Column(modifier.semantics { collectionInfo = CollectionInfo(items.size, 1) }) {
            items.forEachIndexed { i, item ->
                VerticalEntry(item, i, items.getOrNull(i + 1), look, states[i], itemContent)
            }
        }
    } else {
        BoxWithConstraints(modifier) {
            // 칸이 넉넉하면 폭을 똑같이 나누고, 모자라면 최소 폭을 지킨 채 가로로 스크롤한다.
            val width = maxOf(H_ITEM_MIN, maxWidth / items.size.coerceAtLeast(1))
            Row(Modifier.horizontalScroll(rememberScrollState()).semantics { collectionInfo = CollectionInfo(1, items.size) }) {
                items.forEachIndexed { i, item -> HorizontalEntry(item, i, items.size, look, states[i], width) }
            }
        }
    }
}

internal class TimelineLook(val accent: Color, val error: Color, val ink: Color, val surface: SurfaceStyle)

@Composable
private fun timelineState(status: KTimelineStatus): String = stringResource(
    when (status) {
        KTimelineStatus.Done -> R.string.kinetica_ui_step_done
        KTimelineStatus.Current -> R.string.kinetica_ui_step_current
        KTimelineStatus.Upcoming -> R.string.kinetica_ui_step_upcoming
        KTimelineStatus.Error -> R.string.kinetica_ui_step_error
    },
)

@Composable
private fun VerticalEntry(
    item: KTimelineItem,
    index: Int,
    next: KTimelineItem?,
    look: TimelineLook,
    state: String,
    itemContent: (@Composable (Int) -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                stateDescription = state
                collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
            },
    ) {
        Column(Modifier.width(DOT_COLUMN).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            TimelineDot(item.status, item.icon, look, Modifier.padding(top = DOT_TOP))
            if (next != null) {
                Box(
                    Modifier
                        .weight(1f)
                        .width(LINE_WIDTH)
                        .padding(vertical = LINE_GAP)
                        .drawBehind { drawConnector(item.status == KTimelineStatus.Done, look, vertical = true) },
                )
            }
        }
        Column(
            Modifier.weight(1f).padding(start = 12.dp, bottom = if (next != null) ITEM_GAP else 0.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                ThemedText(item.title, Modifier.weight(1f), style = titleStyle(item.status, look))
                item.time?.let {
                    ThemedText(
                        it,
                        Modifier.padding(start = 8.dp, top = 2.dp),
                        style = TextStyle(color = look.surface.muted(MUTED), fontSize = 12.sp),
                    )
                }
            }
            item.subtitle?.let {
                ThemedText(it, style = TextStyle(color = look.surface.muted(MUTED), fontSize = 13.sp, lineHeight = 18.sp))
            }
            itemContent?.let { Box(Modifier.padding(top = 6.dp)) { it(index) } }
        }
    }
}

@Composable
private fun HorizontalEntry(item: KTimelineItem, index: Int, count: Int, look: TimelineLook, state: String, width: Dp) {
    Column(
        Modifier
            .width(width)
            .semantics(mergeDescendants = true) {
                stateDescription = state
                collectionItemInfo = CollectionItemInfo(0, 1, index, 1)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().height(DOT_SIZE + HALO * 2), verticalAlignment = Alignment.CenterVertically) {
            HalfConnector(index > 0, item.status != KTimelineStatus.Upcoming, look, Modifier.weight(1f))
            TimelineDot(item.status, item.icon, look)
            HalfConnector(index < count - 1, item.status == KTimelineStatus.Done, look, Modifier.weight(1f))
        }
        ThemedText(
            item.title,
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            style = titleStyle(item.status, look).copy(fontSize = 13.sp, textAlign = TextAlign.Center),
        )
        item.time?.let {
            ThemedText(it, style = TextStyle(color = look.surface.muted(MUTED), fontSize = 11.sp, textAlign = TextAlign.Center))
        }
    }
}

/** 가로 타임라인에서 점 양옆의 반쪽 선. 앞쪽 반은 이 항목에 도달했으면, 뒤쪽 반은 이 항목이 끝났으면 실선이다. */
@Composable
private fun HalfConnector(visible: Boolean, solid: Boolean, look: TimelineLook, modifier: Modifier) {
    Box(
        modifier.height(LINE_WIDTH).drawBehind { if (visible) drawConnector(solid, look, vertical = false) },
    )
}

private fun DrawScope.drawConnector(solid: Boolean, look: TimelineLook, vertical: Boolean) {
    val stroke = size.minDimension
    val start = if (vertical) Offset(size.width / 2, 0f) else Offset(0f, size.height / 2)
    val end = if (vertical) Offset(size.width / 2, size.height) else Offset(size.width, size.height / 2)
    if (solid) {
        drawLine(look.accent, start, end, stroke, StrokeCap.Round)
    } else {
        val dash = DASH.toPx()
        drawLine(
            look.ink.copy(alpha = UPCOMING_ALPHA),
            start,
            end,
            stroke,
            StrokeCap.Round,
            PathEffect.dashPathEffect(
                floatArrayOf(
                    dash,
                    dash * DASH_GAP,
                ),
            ),
        )
    }
}

/** 상태에 맞는 점. [KStepIndicator]도 같은 점을 쓴다. */
@Composable
internal fun TimelineDot(
    status: KTimelineStatus,
    icon: ImageVector?,
    look: TimelineLook,
    modifier: Modifier = Modifier,
    size: Dp = DOT_SIZE,
    number: String? = null,
) {
    val clock = rememberLoopClock(status == KTimelineStatus.Current && !reducedMotion())
    val background = look.surface.inkBackground()
    Box(
        modifier
            .size(size)
            .loopVisibility(clock)
            .drawBehind {
                val wave = (sin(clock.seconds * PI.toFloat() * 2 / PULSE_SECONDS) + 1f) / 2
                drawDot(status, look, background, wave, core = number == null && icon == null)
            },
        contentAlignment = Alignment.Center,
    ) { DotContent(status, icon, number, look, size) }
}

private fun DrawScope.drawDot(status: KTimelineStatus, look: TimelineLook, background: Color, wave: Float, core: Boolean) {
    val radius = size.minDimension / 2
    when (status) {
        KTimelineStatus.Done -> drawCircle(look.accent, radius)

        KTimelineStatus.Error -> drawCircle(look.error, radius)

        KTimelineStatus.Current -> {
            drawCircle(look.accent.copy(alpha = HALO_ALPHA * (1f - wave)), radius + HALO.toPx() * (HALO_BASE + wave))
            drawCircle(background, radius)
            drawCircle(look.accent, radius, style = Stroke(RING.toPx()))
            if (core) drawCircle(look.accent, radius * CORE)
        }

        KTimelineStatus.Upcoming -> {
            drawCircle(background, radius)
            drawCircle(look.ink.copy(alpha = UPCOMING_ALPHA), radius - RING.toPx() / 2, style = Stroke(RING.toPx()))
        }
    }
}

@Composable
private fun DotContent(status: KTimelineStatus, icon: ImageVector?, number: String?, look: TimelineLook, size: Dp) {
    val onAccent = KineticaTheme.colors.onAccent
    when {
        status == KTimelineStatus.Error -> ThemedText(
            "!",
            style = TextStyle(color = KineticaTheme.colors.onDestructive, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )

        status == KTimelineStatus.Done -> Glyph(icon ?: KGlyphs.Check, onAccent, size = size * GLYPH)

        number != null -> {
            val color = if (status == KTimelineStatus.Upcoming) look.surface.muted(MUTED) else look.accent
            ThemedText(number, style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }

        status == KTimelineStatus.Current && icon != null -> Glyph(icon, look.accent, size = size * GLYPH)
    }
}

private fun titleStyle(status: KTimelineStatus, look: TimelineLook): TextStyle = TextStyle(
    color = when (status) {
        KTimelineStatus.Upcoming -> look.surface.muted(MUTED)
        KTimelineStatus.Error -> look.error
        else -> look.ink
    },
    fontSize = 15.sp,
    fontWeight = if (status == KTimelineStatus.Current) FontWeight.SemiBold else FontWeight.Medium,
)

private const val MUTED = 0.62f
private const val UPCOMING_ALPHA = 0.3f
private const val HALO_ALPHA = 0.35f
private const val PULSE_SECONDS = 2.4f
private const val CORE = 0.42f
private const val DASH_GAP = 1.6f
private const val HALO_BASE = 0.5f
private const val GLYPH = 0.62f
private val DOT_SIZE = 22.dp
private val DOT_COLUMN = 28.dp
private val DOT_TOP = 1.dp
private val HALO = 5.dp
private val RING = 2.dp
private val LINE_WIDTH = 2.dp
private val LINE_GAP = 4.dp
private val ITEM_GAP = 20.dp
private val DASH = 3.dp
private val H_ITEM_MIN = 84.dp
