package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 말풍선을 누가 보냈는지. 보낸 말풍선은 끝 쪽(LTR이면 오른쪽), 받은 말풍선은 시작 쪽에 붙는다. */
public enum class KChatSide {
    /** 내가 보낸 메시지 */
    Sent,

    /** 상대가 보낸 메시지 */
    Received,
}

/**
 * 같은 사람이 이어 보낸 말풍선 묶음 안에서의 위치. 묶음 안쪽 모서리는 작게 깎아 말풍선이 한 덩어리로 보이게 하고,
 * 꼬리는 [Single]과 [Last]에만 붙는다. [chatGroupPosition]으로 앞뒤 메시지에서 구한다.
 */
public enum class KChatGroupPosition {
    Single,
    First,
    Middle,
    Last,
}

/** 보낸 메시지의 전송 상태. 시간 옆에 작은 아이콘으로 그리고 TalkBack은 상태로 읽는다. */
public enum class KChatStatus {
    /** 시계 */
    Sending,

    /** 체크 하나 */
    Sent,

    /** 체크 둘 */
    Delivered,

    /** 진한 체크 둘 */
    Read,

    /** 빨간 느낌표. 다시 보내기는 호출한 쪽이 `onLongClick`이나 옆 버튼으로 제공한다. */
    Failed,
}

/** [KChatBubble]의 기본값 */
public object KChatBubbleDefaults {
    /** 말풍선 최대 폭. 받은 폭에 대한 비율이며 글자 크기를 키워도 그대로다. */
    // const로 두면 이름 규칙상 대문자가 되어야 해서 다른 Defaults와 이름 모양이 달라진다.
    @Suppress("MayBeConst")
    public val MaxWidthFraction: Float = 0.78f

    /** 묶음 안 말풍선 사이 간격 */
    public val GroupSpacing: Dp = 2.dp

    /** 묶음과 묶음 사이 간격 */
    public val Spacing: Dp = 10.dp
}

/**
 * 앞뒤 메시지가 같은 사람이 보낸 것인지로 묶음 안 위치를 정한다.
 *
 * ```kotlin
 * val position = chatGroupPosition(prev?.sender == msg.sender, next?.sender == msg.sender)
 * ```
 */
public fun chatGroupPosition(sameAsPrevious: Boolean, sameAsNext: Boolean): KChatGroupPosition = when {
    sameAsPrevious && sameAsNext -> KChatGroupPosition.Middle
    sameAsPrevious -> KChatGroupPosition.Last
    sameAsNext -> KChatGroupPosition.First
    else -> KChatGroupPosition.Single
}

/**
 * 글자 메시지 말풍선. 받은 폭 전체를 차지하는 줄에 놓이고, 말풍선은 [side]에 따라 시작이나 끝 쪽에 붙는다.
 * 나머지 규칙은 내용 슬롯을 받는 [KChatBubble]과 같다.
 *
 * ```kotlin
 * KChatBubble("내일 3시 어때요?", KChatSide.Sent, time = "오후 2:14", status = KChatStatus.Read)
 * ```
 */
@Composable
public fun KChatBubble(
    text: String,
    side: KChatSide,
    modifier: Modifier = Modifier,
    position: KChatGroupPosition = KChatGroupPosition.Single,
    time: String? = null,
    status: KChatStatus? = null,
    tail: Boolean = true,
    variant: KVariant = defaultChatVariant(side),
    tone: KTone = KTone.Accent,
    maxWidthFraction: Float = KChatBubbleDefaults.MaxWidthFraction,
    onLongClick: (() -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    KChatBubble(side, modifier, position, time, status, tail, variant, tone, maxWidthFraction, onLongClick, surface) {
        ThemedText(text, style = TextStyle(color = LocalKineticaContentColor.current, fontSize = 16.sp, lineHeight = 21.sp))
    }
}

/**
 * 채팅 말풍선. 보낸 말풍선은 기본으로 강조색으로 채우고([KVariant.Filled]), 받은 말풍선은 표면 스타일로 그린다([KVariant.Glass]).
 * [KSheet]나 [KDialog], [KGlassLayer] 같은 팝업 판 안에서는 [KVariant.Glass]를 그림자 없이 옅은 한 겹으로 그린다. 판 위에서도 떠 보이게
 * 하려면 [KVariant.Elevated]를, 더 가볍게 하려면 [KVariant.Outlined]나 `variant = KVariant.Tonal, tone = KTone.Neutral`을 넘긴다.
 * [position]에 따라 묶음 안쪽 모서리를 작게 깎고, [tail]이 `true`면 묶음 마지막 말풍선 아래 모서리에 꼬리를 붙인다.
 * 꼬리는 늘 바깥쪽(보낸 말풍선은 끝 쪽)에 붙으므로 RTL에서는 좌우가 바뀐다. 꼬리를 그리지 않아도 꼬리 자리 폭(6dp)은 비워 두므로
 * 묶음 안 말풍선의 가장자리가 나란하다.
 *
 * 폭은 받은 폭의 [maxWidthFraction]을 넘지 않는다. 비율이라 글자를 두 배로 키워도 말풍선이 화면을 다 덮지 않고 줄을 바꾼다.
 * [time]과 [status]는 내용 아래 끝 쪽에 작게 붙는다. TalkBack은 내용과 시간을 한 번에 읽고 [status]를 상태로 덧붙인다.
 * [onLongClick]을 주면 길게 눌러 메뉴를 열 수 있고, TalkBack에는 '옵션 더보기' 동작으로 노출된다.
 *
 * @param maxWidthFraction 최대 폭 비율(0..1]. 범위를 벗어나면 [IllegalArgumentException]을 던진다.
 * @param content 말풍선 안 내용. 글자 색은 [LocalKineticaContentColor]로 받는다. 사진이면 안쪽 모서리를 [KMetrics]의 동심원 규칙대로 깎는다.
 */
@Composable
public fun KChatBubble(
    side: KChatSide,
    modifier: Modifier = Modifier,
    position: KChatGroupPosition = KChatGroupPosition.Single,
    time: String? = null,
    status: KChatStatus? = null,
    tail: Boolean = true,
    variant: KVariant = defaultChatVariant(side),
    tone: KTone = KTone.Accent,
    maxWidthFraction: Float = KChatBubbleDefaults.MaxWidthFraction,
    onLongClick: (() -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    require(maxWidthFraction > 0f && maxWidthFraction <= 1f) { "maxWidthFraction은 0보다 크고 1 이하여야 합니다." }
    val sent = side == KChatSide.Sent
    val showTail = tail && (position == KChatGroupPosition.Single || position == KChatGroupPosition.Last)
    val shape = ChatBubbleShape(sent, position, showTail)
    val colors = tone.colors(surface)
    val ink = buttonInk(variant, tone, colors, surface)
    val statusLabel = status?.let { stringResource(it.label()) }
    val moreLabel = stringResource(R.string.kinetica_ui_more_options)
    Box(modifier.fillMaxWidth(), contentAlignment = if (sent) Alignment.TopEnd else Alignment.TopStart) {
        Column(
            Modifier
                .fractionOfWidth(maxWidthFraction)
                .semantics(mergeDescendants = true) {
                    statusLabel?.let { stateDescription = it }
                    if (onLongClick != null) {
                        customActions = listOf(
                            CustomAccessibilityAction(moreLabel) {
                                onLongClick()
                                true
                            },
                        )
                    }
                }
                .bubbleBackground(variant, colors, surface, shape)
                .clip(shape)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(null, KIndication(shape, ink), onLongClick = onLongClick, onClick = {})
                    } else {
                        Modifier
                    },
                )
                .padding(
                    // 꼬리는 보낸 말풍선이면 늘 끝 쪽, 받은 말풍선이면 시작 쪽이라 start와 end로 정하면 RTL에서도 맞는다.
                    start = BUBBLE_PADDING + if (sent) 0.dp else TAIL_GUTTER,
                    end = BUBBLE_PADDING + if (sent) TAIL_GUTTER else 0.dp,
                    top = BUBBLE_VERTICAL,
                    bottom = BUBBLE_VERTICAL,
                ),
        ) {
            CompositionLocalProvider(LocalKineticaContentColor provides ink) { content() }
            if (time != null || status != null) ChatMeta(time, status, ink, Modifier.align(Alignment.End))
        }
    }
}

/**
 * 말풍선 배경. 팝업 판 안([KGlassLayer], [KSheet], [KDialog])의 [KVariant.Glass]는 그림자와 가장자리 빛이 판 위에서 무거워 보이므로,
 * 표면 글자 색을 옅게 깐 한 겹으로 그린다. 단색과 톤 표면은 [kSurface]가 판 안에서 이미 그렇게 그린다.
 */
@Composable
private fun Modifier.bubbleBackground(variant: KVariant, colors: ToneColors, surface: SurfaceStyle, shape: Shape): Modifier =
    if (variant == KVariant.Glass && surface is SurfaceStyle.Glass && LocalGlassLayered.current) {
        background(surface.contentColor.copy(alpha = LAYERED_BUBBLE_ALPHA), shape)
    } else {
        variantBackground(variant, colors, surface, shape)
    }

/** 보낸 말풍선은 강조색으로 채우고 받은 말풍선은 표면 스타일 그대로 그린다. */
internal fun defaultChatVariant(side: KChatSide): KVariant = if (side == KChatSide.Sent) KVariant.Filled else KVariant.Glass

/** 받은 최대 폭의 [fraction]까지만 쓰게 한다. 최소 폭은 풀어 내용 크기에 맞춘다. */
private fun Modifier.fractionOfWidth(fraction: Float): Modifier = layout { measurable, constraints ->
    val max = if (constraints.hasBoundedWidth) (constraints.maxWidth * fraction).roundToInt() else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = max))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

@Composable
private fun ChatMeta(time: String?, status: KChatStatus?, ink: Color, modifier: Modifier) {
    val failed = KineticaTheme.colors.destructive
    Row(
        modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        time?.let { ThemedText(it, style = TextStyle(color = ink.copy(alpha = META_ALPHA), fontSize = 11.sp)) }
        if (status != null) {
            Spacer(Modifier.size(with(LocalDensity.current) { STATUS_SP.sp.toDp() }).drawBehind { drawChatStatus(status, ink, failed) })
        }
    }
}

private fun KChatStatus.label(): Int = when (this) {
    KChatStatus.Sending -> R.string.kinetica_ui_chat_sending
    KChatStatus.Sent -> R.string.kinetica_ui_chat_sent
    KChatStatus.Delivered -> R.string.kinetica_ui_chat_delivered
    KChatStatus.Read -> R.string.kinetica_ui_chat_read
    KChatStatus.Failed -> R.string.kinetica_ui_chat_failed
}

/** 전송 상태 아이콘. 16dp 칸 기준 비율로 그린다. */
@Suppress("MagicNumber") // 16 격자 좌표
private fun DrawScope.drawChatStatus(status: KChatStatus, ink: Color, failed: Color) {
    val u = size.minDimension / UNIT
    val faint = ink.copy(alpha = META_ALPHA)
    val stroke = Stroke(STATUS_STROKE * u, cap = StrokeCap.Round)
    when (status) {
        KChatStatus.Sending -> {
            drawCircle(faint, 5.5f * u, center, style = stroke)
            drawLine(faint, center, Offset(center.x, center.y - 3.2f * u), stroke.width, StrokeCap.Round)
            drawLine(faint, center, Offset(center.x + 2.4f * u, center.y + 1.2f * u), stroke.width, StrokeCap.Round)
        }

        KChatStatus.Sent -> drawCheck(faint, 0f, u, stroke.width)

        KChatStatus.Delivered, KChatStatus.Read -> {
            val color = if (status == KChatStatus.Read) ink else faint
            drawCheck(color, -2.2f * u, u, stroke.width)
            drawCheck(color, 2.2f * u, u, stroke.width)
        }

        KChatStatus.Failed -> {
            drawCircle(failed, 7f * u, center)
            drawLine(
                Color.White,
                Offset(center.x, center.y - 3.6f * u),
                Offset(center.x, center.y + 0.8f * u),
                stroke.width,
                StrokeCap.Round,
            )
            drawCircle(Color.White, stroke.width / 1.4f, Offset(center.x, center.y + 3.4f * u))
        }
    }
}

@Suppress("MagicNumber") // 16 격자 좌표
private fun DrawScope.drawCheck(color: Color, dx: Float, u: Float, width: Float) {
    val a = Offset(center.x + dx - 4.2f * u, center.y + 0.4f * u)
    val b = Offset(center.x + dx - 1.4f * u, center.y + 3.2f * u)
    val c = Offset(center.x + dx + 4.2f * u, center.y - 3.4f * u)
    drawLine(color, a, b, width, StrokeCap.Round)
    drawLine(color, b, c, width, StrokeCap.Round)
}

/**
 * 말풍선 모양. 꼬리 쪽에 [TAIL_GUTTER]만큼 자리를 비우고 나머지에 둥근 몸통을 그린다. [tail]이면 아래 모서리에 꼬리를 덧붙인다.
 * 꼬리 쪽은 보낸 말풍선이면 끝 쪽이라 레이아웃 방향을 따라 좌우가 바뀐다.
 */
internal class ChatBubbleShape(private val sent: Boolean, private val position: KChatGroupPosition, private val tail: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val tailRight = sent == (layoutDirection == LayoutDirection.Ltr)
        val gutter = TAIL_GUTTER.toPx()
        val left = if (tailRight) 0f else gutter
        val right = if (tailRight) size.width - gutter else size.width
        val big = minOf(BIG_RADIUS.toPx(), size.height / 2, (right - left) / 2)
        val small = minOf(SMALL_RADIUS.toPx(), big)
        val body = Path().apply { addRoundRect(bodyRect(Rect(left, 0f, right, size.height), big, small, tailRight)) }
        if (!tail) return Outline.Generic(body)
        val tailPath = tailPath(size, tailRight, gutter, this)
        Outline.Generic(Path.combine(PathOperation.Union, body, tailPath))
    }

    /** 꼬리 쪽 모서리는 묶음 안쪽이면 작게, 바깥 쪽 모서리는 늘 크게 깎는다. */
    private fun bodyRect(bounds: Rect, big: Float, small: Float, tailRight: Boolean): RoundRect {
        val joinTop = position == KChatGroupPosition.Middle || position == KChatGroupPosition.Last
        val joinBottom = position == KChatGroupPosition.First || position == KChatGroupPosition.Middle
        val top = CornerRadius(if (joinTop) small else big)
        val bottom = CornerRadius(if (joinBottom || tail) small else big)
        val round = CornerRadius(big)
        return RoundRect(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            topLeftCornerRadius = if (tailRight) round else top,
            topRightCornerRadius = if (tailRight) top else round,
            bottomRightCornerRadius = if (tailRight) bottom else round,
            bottomLeftCornerRadius = if (tailRight) round else bottom,
        )
    }

    /** iOS 메시지처럼 몸통 아래 모서리에서 바깥으로 휘어 나가는 꼬리. 오른쪽 꼬리로 그린 뒤 왼쪽이면 x를 뒤집는다. */
    private fun tailPath(size: Size, tailRight: Boolean, gutter: Float, density: Density): Path = with(density) {
        val h = size.height
        val bodyEdge = size.width - gutter
        val rise = minOf(TAIL_RISE.toPx(), h / 2)
        val back = TAIL_BACK.toPx()
        fun x(value: Float): Float = if (tailRight) value else size.width - value
        Path().apply {
            moveTo(x(bodyEdge - back), h - rise)
            lineTo(x(bodyEdge), h - rise)
            quadraticTo(x(bodyEdge), h - gutter * TAIL_CURVE, x(size.width), h)
            quadraticTo(x(bodyEdge - back * TAIL_INNER), h, x(bodyEdge - back), h - gutter * TAIL_LIFT)
            close()
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ChatBubbleShape && sent == other.sent && position == other.position && tail == other.tail

    override fun hashCode(): Int = (sent.hashCode() * HASH + position.hashCode()) * HASH + tail.hashCode()
}

internal val TAIL_GUTTER = 6.dp
private val BUBBLE_PADDING = 12.dp
private val BUBBLE_VERTICAL = 8.dp
private val BIG_RADIUS = 18.dp
private val SMALL_RADIUS = 5.dp
private val TAIL_RISE = 14.dp
private val TAIL_BACK = 10.dp
private const val STATUS_SP = 15
private const val TAIL_CURVE = 0.35f
private const val TAIL_INNER = 0.25f
private const val TAIL_LIFT = 0.15f
private const val META_ALPHA = 0.68f
private const val LAYERED_BUBBLE_ALPHA = 0.12f
private const val STATUS_STROKE = 1.5f
private const val UNIT = 16f
private const val HASH = 31
