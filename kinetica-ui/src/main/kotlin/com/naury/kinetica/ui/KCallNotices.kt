package com.naury.kinetica.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 통화 화면 위쪽에 띄우는 알림 줄. 화면 공유 경고, 마이크와 카메라 오류, 다른 통화의 보류, 재연결처럼 사용자가 알아야 할 상태를 보여 준다.
 * 앞에 [status] 색 점이나 [icon]을 두고, [actionLabel]이 있으면 끝에 버튼을 둔다. 나타나면 TalkBack이 [liveRegion] 방식으로 읽는다.
 *
 * ```kotlin
 * KCallBanner("화면을 공유하고 있어요.", status = KStatus.Error, actionLabel = "중지", onAction = ::stopShare)
 * ```
 *
 * @param supporting 제목 아래 보조 문구
 * @param status 색. [KStatus.Warning]은 주황, [KStatus.Error]는 빨강이다.
 * @param icon 색 점 대신 놓을 아이콘
 * @param liveRegion 나타날 때 TalkBack이 읽는 방식. 재연결처럼 급한 것은 [LiveRegionMode.Assertive]를 쓴다.
 */
@Composable
public fun KCallBanner(
    text: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    status: KStatus = KStatus.Warning,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    liveRegion: LiveRegionMode = LiveRegionMode.Polite,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val ink = surface.contentColor
    val color = when (status) {
        KStatus.Warning -> Color(CALL_WARNING)
        KStatus.Error -> KineticaTheme.colors.destructive
        KStatus.Success -> KineticaTheme.colors.positive
        KStatus.Info -> KineticaTheme.colors.accent
        KStatus.Neutral -> ink
    }
    val readable = readableOn(color, surface.inkBackground())
    Row(
        modifier
            .fillMaxWidth()
            .kSurface(surface, roundedShapeFor(BANNER_RADIUS) { card })
            .heightIn(min = KMetrics.MinTouch + 8.dp)
            .padding(start = 16.dp, end = if (actionLabel != null) 8.dp else 16.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(rememberVectorPainter(icon), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(readable))
        } else {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { this.liveRegion = liveRegion }) {
            ThemedText(text, style = TextStyle(color = readable, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            supporting?.let { ThemedText(it, style = TextStyle(color = ink.copy(alpha = SUPPORT_ALPHA), fontSize = 13.sp)) }
        }
        if (actionLabel != null && onAction != null) {
            KButton(
                actionLabel,
                onAction,
                size = KButtonSize.Small,
                tone = if (status ==
                    KStatus.Error
                ) {
                    KTone.Destructive
                } else {
                    KTone.Accent
                },
                surface = surface,
            )
        }
    }
}

/** [KCallBubble]의 기본 표시 시간 */
public object KCallBubbleDefaults {
    /** 되돌리기 같은 버튼이 있는 말풍선의 표시 시간(ms) */
    public const val ACTION_MILLIS: Long = 8_000L

    /** 알리기만 하는 말풍선의 표시 시간(ms) */
    public const val INFO_MILLIS: Long = 6_000L
}

/**
 * [anchor] 위쪽에 꼬리를 단 말풍선. 이어폰이 연결되어 소리가 옮겨 갔을 때 오디오 버튼 위에 알리는 것처럼, 특정 버튼을 가리키는 짧은 알림에 쓴다.
 * 꼬리 끝에서 커지며 나타나고 [durationMillis]가 지나면 [onDismiss]를 부른다. TalkBack 사용자가 접근성 설정에서 '조치를 취할 시간'을 늘렸으면
 * 그 값을 따른다. 화면 전체를 덮는 상자 안에 두며, 말풍선 밖의 터치는 막지 않는다.
 *
 * ```kotlin
 * var audioButton by remember { mutableStateOf<Rect?>(null) }
 * KCallDock(items, onItemPositioned = { key, bounds -> if (key == "audio") audioButton = bounds })
 * KCallBubble(routeNotice != null, "Galaxy Buds로 듣는 중", audioButton, onDismiss = { routeNotice = null },
 *     actionLabel = "되돌리기", onAction = ::undoRoute)
 * ```
 *
 * @param anchor 가리킬 요소의 위치(루트 기준 px). [KCallDock]의 `onItemPositioned`가 주는 값을 그대로 넘긴다. null이면 아래 가운데를 가리킨다.
 * @param durationMillis 보이는 시간(ms). null이면 [onDismiss]를 부르지 않는다.
 */
@Composable
public fun KCallBubble(
    visible: Boolean,
    text: String,
    anchor: Rect?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    durationMillis: Long? = if (actionLabel != null) KCallBubbleDefaults.ACTION_MILLIS else KCallBubbleDefaults.INFO_MILLIS,
    surface: SurfaceStyle = KineticaTheme.surface.forPopup(),
) {
    val adjust = rememberAccessibleTimeout(withControls = actionLabel != null)
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(visible, text, durationMillis) {
        if (visible && durationMillis != null) {
            delay(adjust(durationMillis))
            dismiss()
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        BubbleLayout(anchor?.translate(-origin)) { tailX ->
            AnimatedVisibility(
                visible,
                enter =
                    scaleIn(spring(dampingRatio = BUBBLE_DAMPING, stiffness = Spring.StiffnessMedium), transformOrigin = tailX) + fadeIn(),
                exit = scaleOut(tween(EXIT_MS), targetScale = EXIT_SCALE, transformOrigin = tailX) + fadeOut(tween(EXIT_MS)),
            ) {
                BubbleBody(text, actionLabel, onAction, onDismiss, surface, tailX.pivotFractionX)
            }
        }
    }
}

@Composable
private fun BubbleBody(
    text: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    onDismiss: () -> Unit,
    surface: SurfaceStyle,
    tail: Float,
) {
    val shape = roundedShapeFor(BUBBLE_RADIUS) { menu }
    val tailColor = surface.fillColor
    Row(
        Modifier
            .widthIn(max = BUBBLE_MAX)
            .drawBehind {
                // 꼬리는 말풍선 아래로 삐져나오게 그린다. 판보다 먼저 그려 판 가장자리와 이어 보이게 한다.
                val half = TAIL.toPx()
                val x = (size.width * tail).coerceIn(half * 2, size.width - half * 2)
                val path = Path().apply {
                    moveTo(x - half, size.height - 1f)
                    lineTo(x, size.height + half)
                    lineTo(x + half, size.height - 1f)
                    close()
                }
                drawPath(path, tailColor)
            }
            .kSurface(surface, shape)
            .padding(start = 14.dp, end = if (actionLabel != null) 6.dp else 14.dp, top = 8.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemedText(
            text,
            Modifier.weight(1f, fill = false),
            style = TextStyle(color = surface.contentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
        if (actionLabel != null && onAction != null) {
            KButton(
                actionLabel,
                {
                    onAction()
                    onDismiss()
                },
                size = KButtonSize.Small,
                variant = KVariant.Text,
                surface = surface,
            )
        }
    }
}

/**
 * 말풍선을 [anchor] 위에 놓고 꼬리를 그린다. 말풍선이 화면 밖으로 나가지 않게 옆으로 밀고, 꼬리는 [anchor] 가운데를 가리키게 둔다.
 * [content]에는 꼬리 위치를 비율로 담은 변환 기준점을 넘겨, 나타날 때 꼬리 끝에서 커지게 한다.
 */
@Composable
private fun BubbleLayout(anchor: Rect?, content: @Composable (TransformOrigin) -> Unit) {
    var tail by remember { mutableStateOf(TransformOrigin(HALF, 1f)) }
    Layout(content = { content(tail) }) { measurables, constraints ->
        val margin = SCREEN_MARGIN.roundToPx()
        val gap = TAIL.roundToPx()
        val placeable = measurables.firstOrNull()?.measure(Constraints(maxWidth = (constraints.maxWidth - margin * 2).coerceAtLeast(0)))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable ?: return@layout
            val target =
                anchor
                    ?: Rect(
                        constraints.maxWidth / 2f,
                        constraints.maxHeight.toFloat(),
                        constraints.maxWidth / 2f,
                        constraints.maxHeight.toFloat(),
                    )
            val x = (target.center.x - placeable.width / 2f).roundToInt().coerceIn(
                margin,
                (constraints.maxWidth - margin - placeable.width).coerceAtLeast(margin),
            )
            val y = (target.top - gap - placeable.height).roundToInt().coerceAtLeast(margin)
            tail = TransformOrigin(((target.center.x - x) / placeable.width).coerceIn(0f, 1f), 1f)
            placeable.place(x, y)
        }
    }
}

/**
 * 내가 손을 든 동안 화면 위쪽에 머무는 캡슐. '손을 들었어요'와 '손 내리기' 버튼이 있다. [raised]가 바뀌면 튀어나오거나 줄어들며 사라진다.
 */
@Composable
public fun KRaisedHandPill(
    raised: Boolean,
    onLower: () -> Unit,
    modifier: Modifier = Modifier,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    AnimatedVisibility(
        raised,
        modifier,
        enter = scaleIn(spring(dampingRatio = BUBBLE_DAMPING, stiffness = Spring.StiffnessMedium), initialScale = ENTER_SCALE) + fadeIn(),
        exit = scaleOut(targetScale = ENTER_SCALE) + fadeOut(),
    ) {
        Row(
            Modifier.kSurface(surface, CircleShape).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(rememberVectorPainter(KCallIcons.Hand), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Color(HAND_TINT)))
            ThemedText(
                stringResource(R.string.kinetica_ui_call_your_hand),
                style = TextStyle(color = surface.contentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            KButton(
                stringResource(R.string.kinetica_ui_call_hand_lower),
                onLower,
                size = KButtonSize.Small,
                variant = KVariant.Tonal,
                surface = surface,
            )
        }
    }
}

/**
 * 통화 화면을 떠나 있는 동안 다른 화면 위에 떠 있는 작은 통화 캡슐. 초록 점과 통화 시간을 보여 주고, 누르면 통화로 돌아간다.
 * 상대가 말하는 동안 점 둘레가 소리 크기만큼 퍼진다. [onEnd]를 주면 끝에 끊기 버튼을 둔다.
 *
 * ```kotlin
 * if (minimized) KOngoingCallPill(call.connectedAt, onClick = { minimized = false }, onEnd = ::hangUp)
 * ```
 *
 * @param startedAtMillis 통화가 연결된 시각(`SystemClock.elapsedRealtime()` 기준 ms)
 * @param label 시간 앞에 쓸 글(상대 이름 등). null이면 시간만 쓴다.
 * @param audioLevel 상대 소리 크기(0..1). 그리기 단계에서 읽는다.
 */
@Composable
public fun KOngoingCallPill(
    startedAtMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    muted: Boolean = false,
    audioLevel: () -> Float = { 0f },
    onEnd: (() -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
    now: () -> Long = SystemClock::elapsedRealtime,
) {
    val green = KineticaTheme.colors.positive
    val elapsed = rememberKCallElapsed(startedAtMillis, now)
    val description = stringResource(R.string.kinetica_ui_call_ongoing, listOfNotNull(label, elapsed).joinToString(" "))
    Row(
        modifier
            .heightIn(min = KMetrics.MinTouch)
            .kSurface(surface, CircleShape)
            .clip(CircleShape)
            .kClickable(CircleShape, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.padding(
                start = 14.dp,
                end = if (onEnd !=
                    null
                ) {
                    6.dp
                } else {
                    16.dp
                },
            ).clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).speakingRipple(true, audioLevel, green).clip(CircleShape).background(green))
            if (muted) {
                Image(
                    rememberVectorPainter(KIcons.MicOff),
                    null,
                    Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(Color(CALL_WARNING)),
                )
            }
            ThemedText(
                listOfNotNull(label, elapsed).joinToString("  "),
                style = TextStyle(color = surface.contentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
        }
        onEnd?.let {
            KIconButton(
                KIcons.CallEnd,
                stringResource(R.string.kinetica_ui_call_end),
                it,
                Modifier.padding(4.dp),
                variant = KVariant.Filled,
                tone = KTone.Destructive,
                size = 36.dp,
            )
        }
    }
}

private val BANNER_RADIUS = 18.dp
private const val SUPPORT_ALPHA = 0.7f
private val BUBBLE_RADIUS = 16.dp
private val BUBBLE_MAX = 340.dp
private val SCREEN_MARGIN = 12.dp
private val TAIL = 10.dp
private const val HALF = 0.5f
private const val BUBBLE_DAMPING = 0.62f
private const val EXIT_MS = 140
private const val EXIT_SCALE = 0.85f
private const val ENTER_SCALE = 0.6f
private const val HAND_TINT = 0xFFFFCC00
