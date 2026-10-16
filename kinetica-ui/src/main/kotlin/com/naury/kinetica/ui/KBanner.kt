// 파일 이름은 대표 컴포저블인 KBanner를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [KBanner]의 글자 버튼.
 *
 * @property label 버튼 글자
 * @property onClick 누르면 부른다. 배너를 닫으려면 여기서 `visible`을 false로 바꾼다.
 */
@Immutable
public class KBannerAction(public val label: String, public val onClick: () -> Unit)

/**
 * 화면 위쪽에 머무는 알림 배너. 오프라인, 약관 변경, 결제 수단 만료처럼 사용자가 확인하거나 처리할 때까지 남겨 둘 안내에 쓴다.
 * 토스트와 달리 저절로 사라지지 않고 레이아웃 안에 자리를 차지하므로, 보통 상단 바 아래 목록 위에 둔다.
 * [visible]이 바뀌면 높이가 펼쳐지고 접히며 아래 내용이 밀리고 당겨진다('애니메이션 삭제'면 바로 바뀐다).
 *
 * 나타날 때 TalkBack이 내용을 읽어 준다. [KStatus.Error]는 읽던 것을 끊고 바로 읽는다.
 *
 * ```kotlin
 * KBanner(offline, "인터넷에 연결되어 있지 않아요", status = KStatus.Warning, actions = listOf(KBannerAction("다시 시도", ::retry)))
 * ```
 *
 * @param title 문구 위 굵은 제목. TalkBack에서 제목으로 읽힌다.
 * @param actions 문구 아래 끝에 놓는 글자 버튼. 두 개까지 권장하며, 많으면 줄을 바꿔 놓는다.
 * @param onDismiss 닫기(×) 버튼을 누르면 부른다. null이면 닫기 버튼이 없다.
 * @param icon 상태 아이콘 대신 쓸 아이콘. 상태 색으로 칠한다.
 */
@Composable
public fun KBanner(
    visible: Boolean,
    message: String,
    modifier: Modifier = Modifier,
    status: KStatus = KStatus.Info,
    title: String? = null,
    actions: List<KBannerAction> = emptyList(),
    onDismiss: (() -> Unit)? = null,
    icon: ImageVector? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val still = reducedMotion()
    AnimatedVisibility(
        visible,
        modifier,
        enter = if (still) {
            EnterTransition.None
        } else {
            expandVertically(spring(BANNER_DAMPING, Spring.StiffnessMediumLow)) +
                fadeIn(tween(FADE_MS))
        },
        exit = if (still) ExitTransition.None else shrinkVertically(tween(BANNER_OUT_MS)) + fadeOut(tween(FADE_MS)),
    ) {
        val shape = RoundedCornerShape(BANNER_CORNER)
        val tint = status.feedbackColor(surface)
        val ink = feedbackInk(FeedbackBox.Banner, status, KineticaTheme.colors, surface)
        Column(
            Modifier
                .fillMaxWidth()
                .kSurface(surface, shape)
                .background(tint.copy(alpha = BANNER_TINT), shape)
                .semantics { liveRegion = if (status == KStatus.Error) LiveRegionMode.Assertive else LiveRegionMode.Polite }
                .padding(
                    start = 16.dp,
                    end = if (onDismiss !=
                        null
                    ) {
                        6.dp
                    } else {
                        16.dp
                    },
                    top = 14.dp,
                    bottom = if (actions.isEmpty()) 14.dp else 6.dp,
                ),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                FeedbackLeading(status, icon, ink.icon, Modifier.padding(top = 1.dp, end = 12.dp))
                FeedbackTexts(title, message, ink, Modifier.weight(1f).padding(top = 1.dp))
                onDismiss?.let { CloseButton(it, surface, ink.title) }
            }
            if (actions.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                ) {
                    actions.forEach { KButton(it.label, it.onClick, variant = KVariant.Text, size = KButtonSize.Small, surface = surface) }
                }
            }
        }
    }
}

/**
 * 본문 안에 놓는 알림 상자. 폼 위의 오류 요약, 설정 화면의 주의 사항, 결제 화면의 안내처럼 흐름 속에서 읽고 지나갈 정보를 담는다.
 * shadcn의 Alert, Ant Design의 Alert와 같은 역할이다. 움직이지 않으며 TalkBack은 자기 차례에 상태 이름을 앞에 붙여 읽는다.
 *
 * ```kotlin
 * KAlert("카드 유효 기간이 이번 달에 끝나요", status = KStatus.Warning, title = "결제 수단 확인")
 * ```
 *
 * @param variant 모양. [KVariant.Tonal](기본값, 상태 색을 옅게 깐 상자), [KVariant.Outlined](상태 색 테두리), [KVariant.Filled](상태 색으로 채움),
 *   [KVariant.Glass](표면 스타일 상자에 아이콘만 상태 색)를 받는다. 나머지는 [KVariant.Tonal]처럼 그린다.
 * @param icon 상태 아이콘 대신 쓸 아이콘
 * @param onDismiss 닫기(×) 버튼을 누르면 부른다. null이면 닫기 버튼이 없다.
 * @param action 문구 아래에 놓을 버튼 등
 */
@Composable
public fun KAlert(
    message: String,
    modifier: Modifier = Modifier,
    status: KStatus = KStatus.Info,
    title: String? = null,
    variant: KVariant = KVariant.Tonal,
    icon: ImageVector? = null,
    onDismiss: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val shape = RoundedCornerShape(ALERT_CORNER)
    val tint = status.feedbackColor(surface)
    val look = feedbackInk(FeedbackBox.alert(variant), status, KineticaTheme.colors, surface)
    Row(
        modifier
            .fillMaxWidth()
            .alertBackground(variant, look.back, tint, surface, shape)
            .padding(start = 14.dp, end = if (onDismiss != null) 4.dp else 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        FeedbackLeading(
            status,
            icon,
            look.icon,
            Modifier.padding(top = 1.dp, end = 12.dp),
        )
        Column(Modifier.weight(1f).padding(top = 1.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FeedbackTexts(title, message, look, Modifier)
            action?.invoke()
        }
        onDismiss?.let { CloseButton(it, surface, look.title) }
    }
}

/** 배너와 알림 상자가 어떤 바탕 위에 글자를 쓰는지 */
internal enum class FeedbackBox {
    /** 표면 위에 상태 색을 [BANNER_TINT]만큼 깐 배너 */
    Banner,

    /** 상태 색을 [ALERT_TINT]만큼 깐 알림 상자([KVariant.Tonal]과 받지 않는 변형) */
    Tonal,

    /** 표면 색 그대로인 알림 상자([KVariant.Outlined], [KVariant.Glass], [KVariant.Elevated]) */
    Plain,

    /** 상태 색으로 채운 알림 상자([KVariant.Filled]) */
    Filled,
    ;

    companion object {
        fun alert(variant: KVariant): FeedbackBox = when (variant) {
            KVariant.Filled -> Filled
            KVariant.Outlined, KVariant.Glass, KVariant.Elevated -> Plain
            else -> Tonal
        }
    }
}

/** 상자 바탕과 그 위 아이콘, 제목, 본문 색 */
internal class FeedbackInk(val back: Color, val icon: StatusIconColors, val title: Color, val body: Color)

/**
 * 배너와 알림 상자의 색을 정한다. 톤 어두운 표면처럼 바탕에 강조색이 섞여 있으면 상태 색이 바탕에 묻히므로
 * 글자는 4.5:1, 아이콘은 3:1(어두운 바탕은 4.5:1) 이상이 되도록 밝히거나 어둡게 고친다. 채운 상자는 글자 대신 채우는 색을
 * 어둡게 해서 흰 글자를 그대로 쓴다. 글래스 표면은 블러를 못 쓸 때의 색을 바탕으로 보고 계산한다.
 */
internal fun feedbackInk(box: FeedbackBox, status: KStatus, colors: KineticaColors, surface: SurfaceStyle): FeedbackInk {
    val tint = status.feedbackColor(colors, surface)
    if (box == FeedbackBox.Filled) {
        val fill = readableOn(tint, nominalOnStatus(status, colors, surface), TEXT_CONTRAST)
        val ink = readableOn(nominalOnStatus(status, colors, surface), fill, TEXT_CONTRAST)
        // 아이콘 원은 글자 색으로 칠하고 기호는 상자 색으로 뚫어 보이게 한다.
        return FeedbackInk(fill, StatusIconColors(ink, fill), ink, ink)
    }
    val base = surface.inkBackground()
    val back = when (box) {
        FeedbackBox.Banner -> tint.copy(alpha = BANNER_TINT).compositeOver(base)
        FeedbackBox.Tonal -> tint.copy(alpha = ALERT_TINT).compositeOver(base)
        else -> base
    }
    val title = readableOn(surface.contentColor, back, TEXT_CONTRAST)
    val soft = title.copy(alpha = BODY_ALPHA)
    val body = if (contrast(soft.compositeOver(back), back) >= TEXT_CONTRAST) soft else title
    return FeedbackInk(back, statusIconColors(status, colors, surface, back), title, body)
}

@Composable
private fun Modifier.alertBackground(variant: KVariant, back: Color, tint: Color, surface: SurfaceStyle, shape: Shape): Modifier =
    when (variant) {
        KVariant.Filled -> background(back, shape)
        KVariant.Outlined -> border(OUTLINE, tint.copy(alpha = OUTLINE_ALPHA), shape)
        KVariant.Glass -> kSurface(surface, shape)
        KVariant.Elevated -> kSurface(surface.elevated(), shape)
        else -> background(tint.copy(alpha = ALERT_TINT), shape).border(OUTLINE, tint.copy(alpha = TONAL_BORDER_ALPHA), shape)
    }

/** 앞쪽 아이콘. [icon]이 있으면 그 아이콘을 [colors]의 채우는 색으로, 없으면 상태 아이콘을 그린다. 상태 이름을 TalkBack에 알린다. */
@Composable
private fun FeedbackLeading(status: KStatus, icon: ImageVector?, colors: StatusIconColors, modifier: Modifier) {
    val label = statusLabel(status)
    if (icon != null) {
        Image(
            rememberVectorPainter(icon),
            contentDescription = label,
            modifier.size(LEADING_ICON),
            colorFilter = ColorFilter.tint(colors.fill),
        )
    } else {
        StatusIconImage(status, colors, modifier, label, LEADING_ICON)
    }
}

@Composable
private fun FeedbackTexts(title: String?, message: String, ink: FeedbackInk, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        title?.let {
            ThemedText(
                it,
                Modifier.semantics { heading() },
                style = TextStyle(color = ink.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
            )
        }
        ThemedText(
            message,
            style = TextStyle(
                color = if (title != null) ink.body else ink.title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = if (title == null) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}

/** 오른쪽 위 닫기 버튼. 36dp 원에 그리고 터치 영역은 44dp를 지킨다. */
@Composable
private fun RowScope.CloseButton(onClick: () -> Unit, surface: SurfaceStyle, ink: Color = surface.contentColor) {
    val label = stringResource(R.string.kinetica_ui_notify_dismiss)
    Box(
        Modifier
            .align(Alignment.Top)
            .size(KMetrics.MinTouch)
            .kClickable(CircleShape, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            rememberVectorPainter(KGlyphs.Close),
            contentDescription = null,
            Modifier.size(CLOSE_ICON),
            colorFilter = ColorFilter.tint(ink.copy(alpha = CLOSE_ALPHA)),
        )
    }
}

private val BANNER_CORNER = 20.dp
private val ALERT_CORNER = 16.dp
private val LEADING_ICON = 20.dp
private val CLOSE_ICON = 16.dp
private val OUTLINE = 1.dp
private const val BANNER_TINT = 0.1f
private const val ALERT_TINT = 0.12f
private const val OUTLINE_ALPHA = 0.6f
private const val TONAL_BORDER_ALPHA = 0.22f
private const val BODY_ALPHA = 0.82f
private const val CLOSE_ALPHA = 0.7f
private const val TEXT_CONTRAST = 4.5f
private const val BANNER_DAMPING = 0.85f
private const val BANNER_OUT_MS = 220
private const val FADE_MS = 180
