package com.naury.kinetica.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Bidi

/**
 * 한 줄 공지 막대. 글이 막대보다 길면 옆으로 흘러가며 끝까지 보여 준다. Ant Design Mobile의 NoticeBar와 같은 역할로, 이벤트 안내나
 * 점검 예고처럼 눈에 띄되 흐름을 막지 않는 공지에 쓴다. 글이 짧으면 흐르지 않는다. 흐르는 방향은 글의 방향(아랍어는 오른쪽에서 왼쪽)을 따르고,
 * 끝쪽 가장자리는 흐리게 지워 글이 잘려 보이지 않게 한다. 화면 밖으로 스크롤되면 흐름을 멈춘다.
 *
 * '애니메이션 삭제' 설정이 켜져 있거나 [scroll]이 false면 흐르지 않고 줄을 바꿔 글 전체를 보여 준다. TalkBack은 언제나 글 전체를 읽는다.
 *
 * ```kotlin
 * KNoticeBar("10월 20일 새벽 2시부터 4시까지 서버 점검이 있어요. 그동안 주문과 결제를 쓸 수 없어요.")
 * ```
 *
 * @param status 막대 색. 기본값은 [KStatus.Warning]이다. [KStatus.Neutral]이면 표면 색 막대가 된다.
 * @param icon 앞쪽 아이콘. null이면 상태 아이콘을 쓴다.
 * @param scroll 긴 글을 흘려 보낼지. false면 줄을 바꿔 모두 보여 준다.
 * @param velocity 흐르는 속도(1초에 움직이는 거리)
 * @param onClick 막대를 누르면 부른다. null이면 누를 수 없다. 자세한 공지 화면을 열 때 쓴다.
 * @param onDismiss 닫기(×) 버튼을 누르면 부른다. null이면 닫기 버튼이 없다.
 */
@Composable
public fun KNoticeBar(
    text: String,
    modifier: Modifier = Modifier,
    status: KStatus = KStatus.Warning,
    icon: ImageVector? = null,
    scroll: Boolean = true,
    velocity: Dp = 36.dp,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val shape = RoundedCornerShape(NOTICE_CORNER)
    val tint = status.feedbackColor(surface)
    val neutral = status == KStatus.Neutral
    val back = if (neutral) surface.inkBackground() else tint.copy(alpha = NOTICE_TINT).compositeOver(surface.inkBackground())
    val ink = if (neutral) surface.contentColor else readableOn(tint, back, NOTICE_CONTRAST)
    val flowing = scroll && !reducedMotion()
    var onScreen by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = NOTICE_HEIGHT)
            .then(if (neutral) Modifier.kSurface(surface, shape) else Modifier.background(back, shape))
            .then(if (onClick != null) Modifier.kClickable(shape, role = Role.Button, onClick = onClick) else Modifier)
            .onVisibilityChanged(minFractionVisible = 0f) { onScreen = it }
            .padding(start = 12.dp, end = if (onDismiss != null) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = statusLabel(status)
        if (icon != null) {
            Image(rememberVectorPainter(icon), label, Modifier.size(NOTICE_ICON), colorFilter = ColorFilter.tint(ink))
        } else {
            KStatusIcon(
                status,
                contentDescription = label,
                size = NOTICE_ICON,
                color = if (neutral) Color.Unspecified else ink,
                surface = surface,
            )
        }
        val style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
        if (flowing) {
            FlowingText(text, style, velocity, onScreen, Modifier.weight(1f).padding(start = 10.dp))
        } else {
            ThemedText(text, Modifier.weight(1f).padding(start = 10.dp, top = 10.dp, bottom = 10.dp), style = style)
        }
        onDismiss?.let { NoticeClose(it, ink) }
    }
}

/**
 * 막대보다 긴 글을 옆으로 흘린다. 흐르는 글은 글 자체의 방향으로 놓아야 첫머리부터 보인다.
 * RTL 화면에서 한국어 공지를 레이아웃 방향대로 두면 끝부분이 먼저 보이기 때문이다.
 */
@Composable
private fun FlowingText(text: String, style: TextStyle, velocity: Dp, onScreen: Boolean, modifier: Modifier) {
    val direction = if (Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()) LayoutDirection.Ltr else LayoutDirection.Rtl
    val marquee = if (onScreen) {
        Modifier.basicMarquee(
            Int.MAX_VALUE,
            spacing = MarqueeSpacing(MARQUEE_GAP),
            velocity = velocity,
        )
    } else {
        Modifier
    }
    Box(modifier) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            ThemedText(
                text,
                Modifier.fillMaxWidth().edgeFade().then(marquee),
                style = style,
                maxLines = 1,
                overflow = if (onScreen) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 흐르는 글의 끝쪽 가장자리를 투명하게 지워, 막대보다 긴 글이 칼로 자른 듯 끊겨 보이지 않게 한다. 시작쪽은 지우지 않는다.
 * 글이 멈춰 있을 때 첫 글자가 흐려지기 때문이다. 화면 밖 레이어에서 지워야 막대 바탕까지 지워지지 않는다.
 */
private fun Modifier.edgeFade(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = FADE_WIDTH.toPx().coerceAtMost(size.width / 2)
        val rtl = layoutDirection == LayoutDirection.Rtl
        val from = if (rtl) fade else size.width - fade
        val to = if (rtl) 0f else size.width
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), from, to),
            topLeft = Offset(if (rtl) 0f else size.width - fade, 0f),
            size = Size(fade, size.height),
            blendMode = BlendMode.DstOut,
        )
    }

@Composable
private fun NoticeClose(onClick: () -> Unit, ink: Color) {
    val label = stringResource(R.string.kinetica_ui_notify_dismiss)
    Box(
        Modifier
            .size(KMetrics.MinTouch)
            .kClickable(CircleShape, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            rememberVectorPainter(KGlyphs.Close),
            null,
            Modifier.size(CLOSE_ICON),
            colorFilter = ColorFilter.tint(ink.copy(alpha = CLOSE_ALPHA)),
        )
    }
}

private val NOTICE_HEIGHT = 44.dp
private val NOTICE_CORNER = 12.dp
private val NOTICE_ICON = 18.dp
private val CLOSE_ICON = 14.dp
private val FADE_WIDTH = 14.dp
private val MARQUEE_GAP = 48.dp
private const val NOTICE_TINT = 0.14f
private const val NOTICE_CONTRAST = 4.5f
private const val CLOSE_ALPHA = 0.7f
