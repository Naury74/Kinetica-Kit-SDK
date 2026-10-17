package com.naury.kinetica.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 통화 화면 상단 바. 앞쪽에 [navigation], 가운데에 상태 캡슐(제목, 연결 품질, 통화 시간, 인원, 녹화와 암호화 배지), 뒤쪽에 [actions]를 놓는다.
 *
 * 글자가 커져도 [navigation]과 [actions]는 밀려나지 않는다. 남는 폭이 모자라면 상태 캡슐이 줄어 제목을 말줄임하고,
 * [chips]는 다 들어갈 자리가 있을 때만 보인다. 인원은 숫자만 보이고 TalkBack은 '4명 참가 중'처럼 읽는다.
 *
 * ```kotlin
 * KCallTopBar(
 *     title = "주간 싱크", startedAtMillis = call.connectedAt, participantCount = people.size, quality = quality,
 *     actions = { KIconButton(KIcons.Users, "참가자", ::openPeople) },
 * )
 * ```
 *
 * @param startedAtMillis 통화가 연결된 시각([now] 기준 ms). null이면 통화 시간을 쓰지 않는다.
 * @param participantCount 인원. null이면 쓰지 않는다.
 * @param quality 내 연결 품질. null이면 쓰지 않는다.
 * @param recording 녹화 중 배지
 * @param encrypted 종단 간 암호화 배지
 * @param navigation 앞쪽 버튼(줄이기, 뒤로)
 * @param chips 상태 캡슐 뒤에 둘 부가 칩. 자리가 모자라면 통째로 숨긴다.
 * @param actions 뒤쪽 버튼. 보통 [KIconButton]을 둔다.
 * @param now 지금 시각. 기본값은 `SystemClock.elapsedRealtime()`이다.
 */
@Composable
public fun KCallTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    startedAtMillis: Long? = null,
    participantCount: Int? = null,
    quality: KNetworkQuality? = null,
    recording: Boolean = false,
    encrypted: Boolean = false,
    navigation: (@Composable () -> Unit)? = null,
    chips: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    surface: SurfaceStyle = KineticaTheme.surface,
    now: () -> Long = SystemClock::elapsedRealtime,
) {
    Layout(
        contents = listOf(
            { navigation?.invoke() },
            { StatusCapsule(StatusInfo(title, startedAtMillis, participantCount, quality, recording, encrypted), surface, now) },
            { chips?.invoke() },
            {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ACTION_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            },
        ),
        modifier = modifier,
    ) { slots, constraints ->
        val (nav, status, chip) = slots
        val action = slots.last()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = BAR_GAP.roundToPx()
        val navs = nav.map { it.measure(loose) }
        val acts = action.map { it.measure(loose) }
        val fixed = navs.sumOf { it.width + gap } + acts.sumOf { it.width + gap }
        val room = (constraints.maxWidth - fixed).coerceAtLeast(0)
        val statuses = status.map { it.measure(loose.copy(maxWidth = room)) }
        val left = room - statuses.sumOf { it.width }
        val chipsPlaced = chip.map { it.measure(loose.copy(maxWidth = Constraints.Infinity)) }.takeIf { list ->
            list.all {
                it.width + gap <=
                    left
            }
        }
        val height = (navs + statuses + acts + chipsPlaced.orEmpty()).maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            var x = 0
            (navs + statuses + chipsPlaced.orEmpty()).forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width + gap
            }
            var end = constraints.maxWidth
            acts.reversed().forEach {
                end -= it.width
                it.placeRelative(end, (height - it.height) / 2)
                end -= gap
            }
        }
    }
}

/** 상태 캡슐에 쓸 값 */
private class StatusInfo(
    val title: String?,
    val startedAt: Long?,
    val count: Int?,
    val quality: KNetworkQuality?,
    val recording: Boolean,
    val encrypted: Boolean,
)

@Composable
private fun StatusCapsule(info: StatusInfo, surface: SurfaceStyle, now: () -> Long) {
    val ink = surface.contentColor
    val shape = roundedShapeFor(CAPSULE_RADIUS) { tabBar }
    Box(Modifier.kSurface(surface, shape).padding(horizontal = CAPSULE_PADDING_H, vertical = CAPSULE_PADDING_V)) {
        CompositionLocalProvider(LocalKineticaContentColor provides ink) { StatusLines(info, ink, now) }
    }
}

@Composable
private fun StatusLines(info: StatusInfo, ink: androidx.compose.ui.graphics.Color, now: () -> Long) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        info.title?.let {
            ThemedText(
                it,
                Modifier.semantics { heading() },
                style = TextStyle(color = ink, fontSize = TITLE_SIZE, fontWeight = KineticaTheme.design.title(FontWeight.SemiBold)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(META_GAP), verticalAlignment = Alignment.CenterVertically) {
            info.quality?.let { KNetworkQualityIndicator(it, size = META_ICON) }
            info.startedAt?.let {
                KCallTimer(it, style = TextStyle(fontSize = META_SIZE, fontWeight = FontWeight.Medium, color = ink), now = now)
            }
            info.count?.let { PeopleCount(it, ink) }
            if (info.recording) RecordingBadge()
            if (info.encrypted) {
                Image(
                    rememberVectorPainter(KIcons.Lock),
                    stringResource(R.string.kinetica_ui_call_encrypted),
                    Modifier.size(META_ICON),
                    colorFilter = ColorFilter.tint(ink.copy(alpha = META_ALPHA)),
                )
            }
        }
    }
}

@Composable
private fun PeopleCount(count: Int, ink: androidx.compose.ui.graphics.Color) {
    val label = pluralStringResource(R.plurals.kinetica_ui_call_people, count, count)
    Row(
        Modifier.clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            rememberVectorPainter(KIcons.Person),
            null,
            Modifier.size(META_ICON),
            colorFilter = ColorFilter.tint(ink.copy(alpha = META_ALPHA)),
        )
        RollingText("$count", style = themed(TextStyle(color = ink.copy(alpha = META_ALPHA), fontSize = META_SIZE)))
    }
}

/** 빨간 점과 '녹화' 글자 */
@Composable
private fun RecordingBadge() {
    val red = KineticaTheme.colors.destructive
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(REC_DOT).clip(CircleShape).background(red))
        ThemedText(
            stringResource(R.string.kinetica_ui_call_recording),
            style = TextStyle(
                color = readableOn(red, KineticaTheme.surface.inkBackground()),
                fontSize = META_SIZE,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
        )
    }
}

private val BAR_GAP = 8.dp
private val ACTION_GAP = 8.dp
private val CAPSULE_RADIUS = 24.dp
private val CAPSULE_PADDING_H = 16.dp
private val CAPSULE_PADDING_V = 9.dp
private val META_GAP = 8.dp
private val META_ICON = 14.dp
private val META_SIZE = 13.sp
private val TITLE_SIZE = 15.sp
private val REC_DOT = 7.dp
private const val META_ALPHA = 0.75f
