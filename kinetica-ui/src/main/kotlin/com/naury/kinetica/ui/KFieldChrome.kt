package com.naury.kinetica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * 입력란 둘레의 포커스와 오류 표시. 포커스를 받으면 안쪽이 살짝 밝아지고, 강조색 테두리와 그 바깥의 옅은 링이 부드럽게 나타난다.
 * 값은 그리기 단계에서 읽어 애니메이션 중에 재구성하지 않는다.
 */
internal fun Modifier.fieldFocusFrame(corner: Dp, levels: FieldLevels, accent: Color, errorColor: Color): Modifier = drawWithCache {
    val width = FOCUS_BORDER.toPx()
    val ring = FOCUS_RING.toPx()
    val radius = corner.toPx()
    val stroke = Stroke(width)
    val ringStroke = Stroke(ring)
    onDrawWithContent {
        val f = levels.focus.value.coerceIn(0f, 1f)
        val e = levels.error.value.coerceIn(0f, 1f)
        if (f > 0f) drawRoundRect(Color.White, cornerRadius = CornerRadius(radius), alpha = FOCUS_FILL * f)
        drawContent()
        val level = maxOf(f, e)
        if (level <= 0f) return@onDrawWithContent
        val color = lerp(accent, errorColor, e)
        // 바깥 링은 입력란 밖으로 번지게 그려 테두리가 단단한 선 하나로만 보이지 않게 한다.
        drawRoundRect(
            color.copy(alpha = RING_ALPHA * level),
            Offset(-ring / 2, -ring / 2),
            Size(size.width + ring, size.height + ring),
            CornerRadius(radius + ring / 2),
            style = ringStroke,
        )
        drawRoundRect(
            color.copy(alpha = BORDER_ALPHA * level),
            Offset(width / 2, width / 2),
            Size(size.width - width, size.height - width),
            CornerRadius(radius - width / 2),
            style = stroke,
        )
    }
}

/** 커서 손잡이와 선택 영역을 강조색으로 칠한다. 시스템 기본 파랑이 글래스 색과 어긋나지 않게 한다. */
@Composable
internal fun ProvideFieldSelection(accent: Color, content: @Composable () -> Unit) {
    val colors = remember(accent) { TextSelectionColors(handleColor = accent, backgroundColor = accent.copy(alpha = SELECTION_ALPHA)) }
    CompositionLocalProvider(LocalTextSelectionColors provides colors, content = content)
}

/**
 * 포커스를 받은 동안 키보드가 올라오거나 높이가 바뀌면 이 요소 전체(입력란과 그 아래 오류, 도움말 문구)를 스크롤해 보이게 한다.
 * 입력란 자체는 BasicTextField가 보이게 하지만 아래 문구까지는 챙기지 않아서 따로 둔다.
 */
@Composable
internal fun Modifier.keepVisibleWhileFocused(focused: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        requester.bringIntoView()
        snapshotFlow { ime.getBottom(density) }.distinctUntilChanged().drop(1).collect { requester.bringIntoView() }
    }
    return bringIntoViewRequester(requester)
}

/** 글자가 있을 때만 커지며 나타나는 원형 지우기 버튼. 터치 영역은 [CLEAR_TOUCH]이고 눌림 표시는 원 모양이다. */
@Composable
internal fun FieldClearButton(visible: Boolean, color: Color, onClear: () -> Unit) {
    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(CLEAR_IN_MS)) + scaleIn(spring(dampingRatio = CLEAR_DAMPING, stiffness = Spring.StiffnessMedium), CLEAR_FROM),
        exit = fadeOut(tween(CLEAR_OUT_MS, easing = FastOutSlowInEasing)) + scaleOut(tween(CLEAR_OUT_MS), CLEAR_FROM),
    ) {
        val label = stringResource(R.string.kinetica_ui_clear_text)
        Box(
            Modifier
                .size(CLEAR_TOUCH)
                .clickable(null, KIndication(CircleShape), role = Role.Button, onClick = onClear)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(CLEAR_SIZE).drawWithCache {
                    val radius = size.minDimension / 2
                    val arm = radius * CROSS_ARM
                    val width = CROSS_WIDTH.toPx()
                    val center = Offset(size.width / 2, size.height / 2)
                    onDrawBehind {
                        drawCircle(color.copy(alpha = CLEAR_FILL), radius, center)
                        val ink = color.copy(alpha = CROSS_ALPHA)
                        drawLine(ink, center + Offset(-arm, -arm), center + Offset(arm, arm), width, StrokeCap.Round)
                        drawLine(ink, center + Offset(-arm, arm), center + Offset(arm, -arm), width, StrokeCap.Round)
                    }
                },
            )
        }
    }
}

/** 포커스, 오류 표시 정도(0..1). 180ms 동안 부드럽게 바뀐다. */
internal class FieldLevels(focused: Boolean, error: Boolean) {
    val focus = Animatable(if (focused) 1f else 0f)
    val error = Animatable(if (error) 1f else 0f)

    suspend fun focusTo(focused: Boolean) = focus.animateTo(if (focused) 1f else 0f, tween(FOCUS_MS, easing = FastOutSlowInEasing))

    suspend fun errorTo(error: Boolean) = this.error.animateTo(if (error) 1f else 0f, tween(FOCUS_MS, easing = FastOutSlowInEasing))
}

/** 보조 문구(placeholder, 비어 있을 때의 라벨) 불투명도. 글래스 위에서도 읽히면서 입력한 글자보다 한 단계 옅다. */
internal const val FIELD_HINT_ALPHA = 0.5f

private val FOCUS_BORDER = 1.5.dp
private val FOCUS_RING = 4.dp
private const val FOCUS_FILL = 0.10f
private const val RING_ALPHA = 0.18f
private const val BORDER_ALPHA = 0.85f
private const val SELECTION_ALPHA = 0.3f
private const val FOCUS_MS = 180

private val CLEAR_TOUCH = 40.dp
private val CLEAR_SIZE = 18.dp
private val CROSS_WIDTH = 1.6.dp
private const val CROSS_ARM = 0.38f
private const val CLEAR_FILL = 0.22f
private const val CROSS_ALPHA = 0.85f
private const val CLEAR_IN_MS = 120
private const val CLEAR_OUT_MS = 140
private const val CLEAR_DAMPING = 0.6f
private const val CLEAR_FROM = 0.4f
