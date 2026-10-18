// 파일 이름은 대표 컴포저블인 KSelectionBadge를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 고른 순서 번호를 보여 주는 원형 선택 표시. 사진 고르기에서 썸네일 구석에 놓아 몇 번째로 골랐는지 보인다.
 * 고르지 않았으면 옅게 어두운 빈 원, 고르면 [tone] 색으로 차오르며 가운데에 [order]를 쓴다.
 *
 * 사진 위에 놓이므로 흰 테두리와 그 바깥의 옅은 어두운 테두리를 함께 그려 밝은 사진과 어두운 사진 위 어디서나 보인다.
 * 고를 때는 원이 살짝 튀며 차오르고, '애니메이션 삭제'가 켜져 있거나 [LocalKineticaReducedMotion]이 true면 바로 바뀐다.
 * 번호 글자는 원 크기에 맞춰 그리므로 글자 크기 설정이 커져도 원 밖으로 넘치지 않는다.
 *
 * TalkBack은 상태로 '선택됨, 3번째'나 '선택 안 됨'을 읽는다. [onClick]이 null이면 누를 수 없는 표시이고, 썸네일 전체를 `clickable`로
 * 만들고 그 안에 넣으면 그 상태가 썸네일 이름 뒤에 붙어 읽힌다. [onClick]을 주면 48dp 터치 영역의 체크박스로 읽힌다.
 *
 * [KCheckbox]의 원 모양에 번호를 더하지 않고 따로 둔 까닭: 체크박스는 표면 위 행 안에서 표면 색 테두리로 그리는 입력이고,
 * 이 표시는 사진 위에서 대비를 스스로 챙겨야 하며 상태도 켜짐/꺼짐이 아니라 순서다.
 *
 * ```kotlin
 * Box(Modifier.aspectRatio(1f).clickable { toggle(photo) }) {
 *     Image(photo.thumb, photo.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
 *     KSelectionBadge(order = selection.indexOf(photo.id).takeIf { it >= 0 }?.plus(1), Modifier.align(Alignment.TopEnd).padding(6.dp))
 * }
 * ```
 *
 * @param order 고른 순서(1부터). null이나 0 이하이면 고르지 않은 상태다. 세 자리 이상이면 글자를 줄여 원 안에 맞춘다.
 * @param onClick 눌렸을 때. null이면 누를 수 없는 표시만 그린다.
 * @param size 원 지름. 터치 영역은 [onClick]이 있을 때 48dp보다 작아지지 않는다.
 * @param tone 골랐을 때 채우는 색 역할. 번호는 그 역할의 채운 면 글자 색이다.
 */
@Composable
public fun KSelectionBadge(
    order: Int?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    size: Dp = SELECTION_BADGE_SIZE,
    tone: KTone = KTone.Accent,
) {
    val selected = order != null && order > 0
    val label = if (selected) {
        stringResource(R.string.kinetica_ui_selection_on_order, order)
    } else {
        stringResource(R.string.kinetica_ui_selection_off)
    }
    SelectionBadge(selected, label, modifier, onClick, size, tone) { ink ->
        if (selected) BadgeNumber(order, size, ink)
    }
}

/**
 * 순서 없이 골랐는지만 보이는 [KSelectionBadge]. 고르면 번호 대신 체크 표시를 그린다. 한 장만 고르는 화면이나 순서가 뜻이 없는 목록에 쓴다.
 * TalkBack은 '선택됨'이나 '선택 안 됨'을 읽는다.
 */
@Composable
public fun KSelectionBadge(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    size: Dp = SELECTION_BADGE_SIZE,
    tone: KTone = KTone.Accent,
) {
    val label = stringResource(if (selected) R.string.kinetica_ui_selection_on else R.string.kinetica_ui_selection_off)
    SelectionBadge(selected, label, modifier, onClick, size, tone) { ink ->
        if (selected) {
            Box(
                Modifier.size(size).drawBehind {
                    val box = this.size.minDimension
                    val check = Path().apply {
                        moveTo(box * CHECK_A_X, box * CHECK_A_Y)
                        lineTo(box * CHECK_B_X, box * CHECK_B_Y)
                        lineTo(box * CHECK_C_X, box * CHECK_C_Y)
                    }
                    drawPath(check, ink, style = Stroke(box * CHECK_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
                },
            )
        }
    }
}

@Suppress("LongParameterList") // 두 공개 오버로드의 파라미터를 그대로 받는다.
@Composable
private fun SelectionBadge(
    selected: Boolean,
    label: String,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    size: Dp,
    tone: KTone,
    mark: @Composable (ink: Color) -> Unit,
) {
    val colors = tone.colors(KineticaTheme.surface)
    val fill = remember { Animatable(if (selected) 1f else 0f) }
    val still = reducedMotion()
    LaunchedEffect(selected, still) {
        val target = if (selected) 1f else 0f
        when {
            still -> fill.snapTo(target)
            selected -> fill.animateTo(1f, spring(dampingRatio = POP_DAMPING, stiffness = Spring.StiffnessMediumLow))
            else -> fill.animateTo(0f, tween(EMPTY_MS))
        }
    }
    val interactive = onClick != null
    val fillColor = colors.fill
    Box(
        modifier
            .size(if (interactive) maxOf(size, KMetrics.Touch) else size)
            .then(
                if (onClick != null) {
                    Modifier.toggleable(
                        value = selected,
                        interactionSource = null,
                        indication = KIndication(CircleShape, colors.color, CHOICE_HALO_ALPHA),
                        role = Role.Checkbox,
                        onValueChange = { onClick() },
                    )
                } else {
                    Modifier
                },
            )
            .semantics { stateDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .drawBehind { drawBadge(fill.value, fillColor) }
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.graphicsLayer {
                    val f = fill.value
                    alpha = f.coerceIn(0f, 1f)
                    scaleX = FILL_FROM + (1f - FILL_FROM) * f
                    scaleY = scaleX
                },
                contentAlignment = Alignment.Center,
            ) { mark(colors.onColor) }
        }
    }
}

/**
 * 원을 그린다. 바깥 어두운 테두리는 흰 사진 위에서, 흰 테두리는 어두운 사진 위에서 원의 가장자리를 보인다.
 * 빈 원 안쪽은 옅게 어둡게 칠해 흰 테두리가 밝은 사진에 묻히지 않게 한다.
 */
private fun DrawScope.drawBadge(fill: Float, color: Color) {
    val ring = RING.toPx()
    val halo = HALO.toPx()
    val radius = size.minDimension / 2f - halo
    drawCircle(Color.Black.copy(alpha = HALO_ALPHA), radius + halo / 2f, style = Stroke(halo))
    drawCircle(Color.Black.copy(alpha = EMPTY_ALPHA * (1f - fill.coerceIn(0f, 1f))), radius)
    if (fill > 0f) {
        val r = radius * (FILL_FROM + (1f - FILL_FROM) * fill)
        drawCircle(color.copy(alpha = fill.coerceIn(0f, 1f)), r.coerceAtMost(radius + halo / 2f))
    }
    drawCircle(
        Color.White.copy(alpha = RING_ALPHA),
        radius - ring / 2f,
        center = Offset(size.width / 2f, size.height / 2f),
        style = Stroke(ring),
    )
}

/** 원 안 번호. 원 크기에서 글자 크기를 정해 글자 크기 설정과 무관하게 원 안에 맞춘다. */
@Composable
private fun BadgeNumber(order: Int, size: Dp, ink: Color) {
    val text = order.toString()
    val ratio = if (text.length >= SMALL_DIGITS) NUMBER_SMALL else NUMBER
    val fontSize = with(LocalDensity.current) { (size * ratio).toSp() }
    ThemedText(
        text,
        style = TextStyle(
            color = ink,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
    )
}

private val SELECTION_BADGE_SIZE = 24.dp
private val RING = 1.5.dp
private val HALO = 1.dp
private const val RING_ALPHA = 0.95f
private const val HALO_ALPHA = 0.28f
private const val EMPTY_ALPHA = 0.2f
private const val FILL_FROM = 0.55f
private const val POP_DAMPING = 0.5f
private const val EMPTY_MS = 120
private const val NUMBER = 0.52f
private const val NUMBER_SMALL = 0.38f
private const val SMALL_DIGITS = 3
private const val CHECK_A_X = 0.29f
private const val CHECK_A_Y = 0.52f
private const val CHECK_B_X = 0.44f
private const val CHECK_B_Y = 0.66f
private const val CHECK_C_X = 0.72f
private const val CHECK_C_Y = 0.36f
private const val CHECK_STROKE = 0.09f
