package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 요소의 오른쪽 위(RTL이면 왼쪽 위) 모서리에 빨간 배지를 겹쳐 그린다. 배지는 요소 바깥으로 조금 걸쳐 그려지므로,
 * 부모가 자식을 잘라 그리면 가려질 수 있다.
 *
 * [count]가 바뀌면 배지가 톡 튀며 커졌다가 돌아오고, 처음 나타날 때는 0에서 튀어나오듯 커진다. [max]보다 크면 `99+`처럼 줄여 쓴다.
 * TalkBack은 요소의 상태 설명으로 '새 항목 3개'처럼 읽는다. 이미 상태 설명이 있는 요소(스위치 등)에 붙이면 그 설명을 덮어쓴다.
 *
 * ```kotlin
 * Icon(Icons.Default.Mail, null, Modifier.kBadge(unread))
 * ```
 *
 * @param count 표시할 개수. `null`이거나 0 이하면 배지를 숨긴다. [dot]이 `true`면 무시한다.
 * @param dot `true`면 숫자 없이 작은 점만 그린다.
 * @param color 배지 색. 기본값은 [KineticaTheme]의 `destructive`다.
 * @param max 그대로 보여 줄 가장 큰 수. 1보다 작으면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun Modifier.kBadge(
    count: Int?,
    dot: Boolean = false,
    color: Color = KineticaTheme.colors.destructive,
    max: Int = DEFAULT_MAX,
): Modifier {
    require(max >= 1) { "max는 1 이상이어야 합니다." }
    val visible = dot || (count != null && count > 0)
    val label = if (dot || count == null) "" else badgeLabel(count, max)
    val badge = rememberBadgeMotion(visible, label)
    val measurer = rememberTextMeasurer()
    val drawn = badge.drawn
    val text = remember(drawn, measurer) {
        if (drawn.isEmpty()) null else measurer.measure(drawn, BadgeText)
    }
    val description = badgeDescription(visible, if (dot) null else count)
    val scale = badge.scale
    return this
        .semantics { description?.let { stateDescription = it } }
        .drawWithContent {
            drawContent()
            val level = scale.value
            if (level > 0f) drawBadge(BadgeLook(level, text, color))
        }
}

/**
 * 줄 안에 놓는 숫자 배지. 목록 행 끝의 읽지 않은 수나 탭 이름 옆 개수처럼 글자와 나란히 놓는다.
 * 다른 요소의 모서리에 겹쳐 그리려면 [kBadge]를 쓴다. 두 배지는 같은 모양과 애니메이션(나타날 때 튀어나오고, 숫자가 바뀌면 톡 튐)을 쓴다.
 * 숨으면 폭과 높이가 0으로 줄어 옆 요소가 그 자리를 채운다. TalkBack은 '새 항목 3개'처럼 읽는다.
 *
 * ```kotlin
 * Row { Text("받은편지함"); KBadge(unread) }
 * ```
 *
 * @param count 표시할 개수. `null`이거나 0 이하면 숨긴다. [dot]이 `true`면 무시한다.
 * @param dot `true`면 숫자 없이 점만 그린다.
 * @param max 그대로 보여 줄 가장 큰 수. 1보다 작으면 [IllegalArgumentException]을 던진다.
 * @param tone 배지 색 역할. 기본값은 [KTone.Destructive]다.
 */
@Composable
public fun KBadge(
    count: Int?,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
    max: Int = DEFAULT_MAX,
    tone: KTone = KTone.Destructive,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(max >= 1) { "max는 1 이상이어야 합니다." }
    val visible = dot || (count != null && count > 0)
    val label = if (dot || count == null) "" else badgeLabel(count, max)
    val badge = rememberBadgeMotion(visible, label)
    val description = badgeDescription(visible, if (dot) null else count)
    val colors = tone.colors(surface)
    val scale = badge.scale
    Box(
        modifier
            .layout { measurable, constraints ->
                // 숨는 동안 자리도 함께 줄어 옆 글자가 부드럽게 당겨진다. 레이아웃 단계에서 읽으므로 재구성하지 않는다.
                val placeable = measurable.measure(constraints)
                val level = scale.value.coerceIn(0f, 1f)
                layout((placeable.width * level).roundToInt(), (placeable.height * level).roundToInt()) {
                    placeable.placeRelative(
                        ((placeable.width * level - placeable.width) / 2).roundToInt(),
                        ((placeable.height * level - placeable.height) / 2).roundToInt(),
                    )
                }
            }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .semantics { description?.let { contentDescription = it } }
            .then(if (badge.drawn.isEmpty()) Modifier.size(DOT_SIZE) else Modifier.heightIn(min = BADGE_HEIGHT).widthIn(min = BADGE_HEIGHT))
            .background(colors.fill, shapeFor { this.badge })
            .then(if (badge.drawn.isEmpty()) Modifier else Modifier.padding(horizontal = BADGE_PADDING)),
        contentAlignment = Alignment.Center,
    ) {
        if (badge.drawn.isNotEmpty()) ThemedText(badge.drawn, style = BadgeText.copy(color = colors.onColor), maxLines = 1)
    }
}

/** 배지 크기 애니메이션과 지금 그리는 글자. 숨는 동안에는 마지막 글자를 그대로 그린다. */
private class BadgeMotion(val scale: Animatable<Float, *>) {
    var drawn by mutableStateOf("")
    var popped = ""
}

@Composable
private fun rememberBadgeMotion(visible: Boolean, label: String): BadgeMotion {
    val motion = remember {
        BadgeMotion(Animatable(if (visible) 1f else 0f)).apply {
            drawn = label
            popped = label
        }
    }
    LaunchedEffect(visible, label) {
        val scale = motion.scale
        when {
            !visible -> scale.animateTo(0f, tween(HIDE_MS))

            scale.value < APPEARED -> {
                motion.drawn = label
                motion.popped = label
                scale.animateTo(1f, spring(dampingRatio = APPEAR_DAMPING))
            }

            label != motion.popped -> {
                motion.drawn = label
                motion.popped = label
                scale.animateTo(POP_SCALE, tween(POP_MS))
                scale.animateTo(1f, spring(dampingRatio = POP_DAMPING))
            }
        }
    }
    return motion
}

/** TalkBack이 읽을 배지 설명. 숨겨져 있으면 `null`, [count]가 `null`이면 점 배지로 본다. */
@Composable
private fun badgeDescription(visible: Boolean, count: Int?): String? = when {
    !visible -> null
    count == null -> stringResource(R.string.kinetica_ui_badge_new)
    else -> pluralStringResource(R.plurals.kinetica_ui_badge_count, count, count)
}

/** [count]가 [max]를 넘으면 `max+`로 줄인다. */
internal fun badgeLabel(count: Int, max: Int): String = if (count > max) "$max+" else count.toString()

private class BadgeLook(val scale: Float, val text: TextLayoutResult?, val color: Color)

private fun DrawScope.drawBadge(look: BadgeLook) {
    val text = look.text
    val height = if (text == null) DOT_SIZE.toPx() else maxOf(BADGE_HEIGHT.toPx(), text.size.height.toFloat())
    val width = if (text == null) height else maxOf(height, text.size.width + BADGE_PADDING.toPx() * 2)
    // 배지 높이의 일부만큼 요소 바깥으로 걸쳐 모서리에 매달린 것처럼 보이게 한다.
    val outset = height * OUTSET
    val left = if (layoutDirection == LayoutDirection.Rtl) -outset else size.width + outset - width
    val top = -outset
    val ring = RING.toPx()
    scale(look.scale, Offset(left + width / 2, top + height / 2)) {
        drawRoundRect(
            Color.White,
            Offset(left - ring, top - ring),
            Size(width + ring * 2, height + ring * 2),
            CornerRadius(height / 2 + ring),
        )
        drawRoundRect(look.color, Offset(left, top), Size(width, height), CornerRadius(height / 2))
        if (text != null) {
            drawText(
                text,
                Color.White,
                Offset(left + (width - text.size.width) / 2, top + (height - text.size.height) / 2),
            )
        }
    }
}

private val BADGE_HEIGHT = 18.dp
private val BADGE_PADDING = 5.dp
private val DOT_SIZE = 10.dp
private val RING = 1.5.dp
private const val OUTSET = 0.35f
private const val APPEARED = 0.5f
private const val APPEAR_DAMPING = 0.5f
private const val POP_SCALE = 1.3f
private const val POP_MS = 90
private const val POP_DAMPING = 0.4f
private const val HIDE_MS = 150
private const val DEFAULT_MAX = 99

// 숫자와 + 기호는 RTL 화면에서도 99+ 순서로 보여야 한다.
private val BadgeText = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr)
