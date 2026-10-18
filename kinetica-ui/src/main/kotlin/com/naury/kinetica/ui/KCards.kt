package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 내용을 담는 카드. 모양은 [variant]로 고른다.
 *
 * | [variant] | 모양 |
 * | --- | --- |
 * | [KVariant.Glass] | 표면 스타일 그대로. 글래스면 블러가 비친다(기본값) |
 * | [KVariant.Elevated] | 표면 스타일에 그림자를 키운다 |
 * | [KVariant.Filled] | [tone]이 [KTone.Neutral]이면 표면보다 한 단계 짙은(어두운 표면이면 밝은) 불투명 판, 아니면 그 색으로 채운 판 |
 * | [KVariant.Tonal] | [tone] 색을 옅게 깐 판 |
 * | [KVariant.Outlined] | 배경 없이 옅은 테두리만 두른다 |
 * | [KVariant.Text] | 배경과 테두리 없이 내용만 놓는다 |
 *
 * [onClick]을 주면 카드 전체가 눌리고 모양에 맞춘 눌림 표시가 덮인다. 안쪽 콘텐츠는 [LocalKineticaContentColor]로 글자 색을 받는다.
 * 안쪽 이미지나 버튼의 모서리는 `KMetrics`의 동심원 규칙대로 바깥 반지름에서 안쪽 여백을 뺀 값으로 깎으면 나란히 굽는다.
 *
 * ```kotlin
 * KCard(Modifier.fillMaxWidth(), onClick = { open(order) }, variant = KVariant.Outlined) { Text(order.title) }
 * ```
 *
 * @param contentPadding 카드 안쪽 여백. 기본값은 16dp다.
 */
@Composable
public fun KCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    variant: KVariant = KVariant.Glass,
    tone: KTone = KTone.Neutral,
    shape: Shape = shapeFor { card },
    enabled: Boolean = true,
    contentPadding: Dp = KineticaTheme.design.density.padding(CARD_PADDING),
    surface: SurfaceStyle = KineticaTheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ink = cardInk(variant, tone, surface)
    Column(
        modifier
            .cardBackground(variant, tone, surface, shape)
            .clip(shape)
            .then(
                if (onClick !=
                    null
                ) {
                    Modifier.clickable(null, KIndication(shape, ink), enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(CONTENT_GAP),
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides ink) { content() }
    }
}

/**
 * 고를 수 있는 카드. 요금제, 배송 방법, 결제 수단처럼 큰 선택지를 카드로 보여 줄 때 쓴다.
 * 고르면 테두리가 [tone] 색으로 굵어지고 바탕이 옅게 물들며, 위 끝 모서리에 체크 원이 튀어나와 체크 표시가 그려진다.
 * 하나만 고르는 묶음이면 [role]에 [Role.RadioButton]을 주고, 묶음 부모에 `Modifier.selectableGroup()`을 붙인다.
 *
 * ```kotlin
 * KSelectableCard(plan == Plan.Yearly, { plan = Plan.Yearly }, role = Role.RadioButton) { Text("연간 결제") }
 * ```
 *
 * @param selected 골라져 있는지
 * @param onSelectedChange 눌렀을 때 새 상태(지금의 반대)를 받는다.
 * @param variant 고르지 않은 카드의 모양. 기본값은 [KVariant.Glass]다.
 * @param role TalkBack이 읽을 역할. 기본값은 체크박스다.
 */
@Composable
public fun KSelectableCard(
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    variant: KVariant = KVariant.Glass,
    tone: KTone = KTone.Accent,
    shape: Shape = shapeFor { card },
    enabled: Boolean = true,
    role: Role = Role.Checkbox,
    contentPadding: Dp = KineticaTheme.design.density.padding(CARD_PADDING),
    surface: SurfaceStyle = KineticaTheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val level = remember { Animatable(if (selected) 1f else 0f) }
    val still = reducedMotion()
    LaunchedEffect(selected, still) {
        val target = if (selected) 1f else 0f
        if (still) level.snapTo(target) else level.animateTo(target, spring(dampingRatio = PICK_DAMPING, stiffness = PICK_STIFFNESS))
    }
    val ink = cardInk(variant, KTone.Neutral, surface)
    val accent = tone.colors(surface)
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .cardBackground(variant, KTone.Neutral, surface, shape)
                .selectionFrame(shape, accent.color) { level.value }
                .clip(shape)
                .toggleable(selected, null, KIndication(shape, ink), enabled, role, onSelectedChange)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(CONTENT_GAP),
        ) {
            CompositionLocalProvider(LocalKineticaContentColor provides ink) { content() }
        }
        SelectionBadge(accent, { level.value }, Modifier.align(Alignment.TopEnd).padding(BADGE_INSET))
    }
}

/**
 * 사진 위에 글자를 얹은 카드. [image]가 카드를 가득 채우고, 아래쪽에 어두운 그라디언트 스크림을 깔아 사진이 밝아도 흰 글자가 읽힌다.
 * [eyebrow]는 제목 위 작은 글자(분류, 날짜), [trailing]은 아래 끝에 놓을 버튼이나 가격이다. 카드 바깥에는 표면 그림자를 드리운다.
 *
 * ```kotlin
 * KMediaCard({ Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }, "제주 3박 4일", subtitle = "₩389,000부터")
 * ```
 *
 * @param image 사진 콘텐츠. 카드 크기에 맞춰 잘린다. 크기는 [aspectRatio]가 정한다.
 * @param aspectRatio 폭 대 높이 비율. 0 이하면 [IllegalArgumentException]을 던진다. 기본값은 4:3이다.
 */
@Composable
public fun KMediaCard(
    image: @Composable BoxScope.() -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    eyebrow: String? = null,
    onClick: (() -> Unit)? = null,
    aspectRatio: Float = DEFAULT_RATIO,
    shape: Shape = shapeFor { card },
    trailing: (@Composable () -> Unit)? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(aspectRatio > 0f) { "aspectRatio는 0보다 커야 합니다." }
    Box(
        modifier
            .aspectRatio(aspectRatio)
            .kSurface(surface.elevated(), shape)
            .clip(shape)
            .then(
                if (onClick !=
                    null
                ) {
                    Modifier.clickable(null, KIndication(shape, Color.White), role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(Modifier.fillMaxSize(), content = image)
        Box(Modifier.fillMaxSize().background(Scrim))
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(CARD_PADDING),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP),
        ) {
            CompositionLocalProvider(LocalKineticaContentColor provides Color.White) {
                Column(Modifier.weight(1f)) {
                    eyebrow?.let { CardText(it, EYEBROW_FONT, FontWeight.SemiBold, Color.White.copy(alpha = SOFT)) }
                    CardText(title, MEDIA_TITLE_FONT, KineticaTheme.design.titleWeight, Color.White, maxLines = 2)
                    subtitle?.let { CardText(it, BODY_FONT, FontWeight.Normal, Color.White.copy(alpha = SOFT)) }
                }
                trailing?.invoke()
            }
        }
    }
}

/**
 * 썸네일과 글자를 가로로 놓은 카드. 주문 내역, 추천 상품, 최근 본 글처럼 목록에 늘어놓는 카드에 쓴다.
 * 썸네일 모서리는 카드 모서리와 동심원이 되도록 바깥 반지름에서 안쪽 여백을 뺀 값으로 깎는다.
 *
 * ```kotlin
 * KHorizontalCard("무선 이어폰", subtitle = "₩129,000", thumbnail = { Image(photo, null) }, onClick = { open(item) })
 * ```
 *
 * @param thumbnail 시작 쪽에 놓을 정사각형 콘텐츠. null이면 글자만 놓는다.
 * @param thumbnailSize 썸네일 한 변. 기본값은 72dp다.
 * @param trailing 끝 쪽에 놓을 콘텐츠(가격, 버튼, 꺾쇠)
 */
@Composable
public fun KHorizontalCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    thumbnail: (@Composable BoxScope.() -> Unit)? = null,
    thumbnailSize: Dp = THUMBNAIL,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    variant: KVariant = KVariant.Glass,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val shapes = KineticaTheme.shapes
    val token = shapes.listSection
    val shape = shapes.shape(token)
    val ink = cardInk(variant, KTone.Neutral, surface)
    Row(
        modifier
            .cardBackground(variant, KTone.Neutral, surface, shape)
            .clip(shape)
            .then(
                if (onClick !=
                    null
                ) {
                    Modifier.clickable(null, KIndication(shape, ink), role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides ink) {
            thumbnail?.let {
                Box(
                    Modifier
                        .size(thumbnailSize)
                        .clip(shapes.inner(token, ROW_PADDING))
                        .background(surface.raisedFill(PLACEHOLDER)),
                    contentAlignment = Alignment.Center,
                    content = it,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TEXT_GAP)) {
                CardText(title, TITLE_FONT, KineticaTheme.design.labelWeight, ink, maxLines = 2)
                subtitle?.let { CardText(it, BODY_FONT, FontWeight.Normal, surface.muted(SOFT)) }
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun CardText(text: String, size: TextUnit, weight: FontWeight, color: Color, maxLines: Int = 1) {
    ThemedText(
        text,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 카드 [variant]에서 글자 색 */
@Composable
private fun cardInk(variant: KVariant, tone: KTone, surface: SurfaceStyle): Color = when {
    variant == KVariant.Filled && tone != KTone.Neutral -> tone.colors(surface).onColor
    else -> surface.contentColor
}

/** 카드 배경. 버튼과 달리 Filled+Neutral은 검은 판이 아니라 표면보다 한 단계 짙은 판이다. */
@Composable
private fun Modifier.cardBackground(variant: KVariant, tone: KTone, surface: SurfaceStyle, shape: Shape): Modifier = when (variant) {
    KVariant.Glass -> kSurface(surface, shape)

    KVariant.Elevated -> kSurface(surface.elevated(), shape)

    KVariant.Filled -> if (tone == KTone.Neutral) {
        background(surface.raisedFill(FILLED_TINT).compositeOver(surface.inkBackground()), shape)
    } else {
        background(tone.colors(surface).fill, shape)
    }

    KVariant.Tonal -> background(tone.colors(surface).color.copy(alpha = if (tone == KTone.Neutral) FILLED_TINT else TONAL_TINT), shape)

    KVariant.Outlined -> {
        val design = LocalKineticaDesign.current
        if (design.depth == KDepth.Offset) {
            border(design.borderWidth, surface.contentColor, shape)
        } else {
            border(design.borderWidth.coerceAtMost(OUTLINE * 2), surface.contentColor.copy(alpha = OUTLINE_ALPHA), shape)
        }
    }

    KVariant.Text -> this
}

/** 고른 정도만큼 [accent] 테두리를 굵게 두르고 바탕을 옅게 물들인다. 그리기 단계에서 읽는다. */
private fun Modifier.selectionFrame(shape: Shape, accent: Color, level: () -> Float): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(SELECTED_BORDER.toPx() * 2)
    onDrawWithContent {
        drawContent()
        val v = level().coerceIn(0f, 1f)
        if (v <= 0f) return@onDrawWithContent
        drawOutline(outline, accent.copy(alpha = SELECTED_TINT * v))
        // 선 두께의 절반이 모양 밖으로 나가므로 두 배로 그려 안쪽 절반만 남긴다(바깥은 clip이 자른다).
        drawOutline(outline, accent.copy(alpha = v), style = stroke)
    }
}

/** 위 끝 모서리의 체크 원. 고르면 튀어나오며 커지고 체크 표시가 그려진다. */
@Composable
private fun SelectionBadge(accent: ToneColors, level: () -> Float, modifier: Modifier) {
    Box(
        modifier.size(BADGE).drawWithCache {
            val stroke = BADGE_STROKE.toPx()
            onDrawBehind {
                val v = level()
                val r = size.minDimension / 2
                val c = Offset(size.width / 2, size.height / 2)
                if (v <
                    1f
                ) {
                    drawCircle(
                        lerp(Color.Gray, accent.color, v).copy(alpha = RING_ALPHA * (1f - v)),
                        r - stroke / 2,
                        c,
                        style = Stroke(stroke),
                    )
                }
                if (v > 0f) {
                    drawCircle(accent.color, r * v.coerceAtMost(MAX_POP), c)
                    val box = size.width * CHECK_BOX
                    // 체크 표시는 RTL에서도 뒤집지 않는다.
                    drawCheckStroke(accent.onColor, v, Offset((size.width - box) / 2, (size.height - box) / 2), box, stroke)
                }
            }
        },
    )
}

private val Scrim = Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.68f))
private val CARD_PADDING = 16.dp
private val CONTENT_GAP = 8.dp
private val ROW_PADDING = 10.dp
private val ROW_GAP = 14.dp
private val TEXT_GAP = 2.dp
private val THUMBNAIL = 72.dp
private val OUTLINE = 1.dp
private val SELECTED_BORDER = 2.dp
private val BADGE = 22.dp
private val BADGE_INSET = 12.dp
private val BADGE_STROKE = 1.8.dp
private val EYEBROW_FONT = 12.sp
private val MEDIA_TITLE_FONT = 20.sp
private val TITLE_FONT = 16.sp
private val BODY_FONT = 14.sp
private const val DEFAULT_RATIO = 4f / 3f
private const val SOFT = 0.78f
private const val PLACEHOLDER = 0.08f
private const val FILLED_TINT = 0.06f
private const val TONAL_TINT = 0.12f
private const val OUTLINE_ALPHA = 0.14f
private const val SELECTED_TINT = 0.08f
private const val RING_ALPHA = 0.5f
private const val MAX_POP = 1.08f
private const val CHECK_BOX = 0.56f
private const val PICK_DAMPING = 0.55f
private const val PICK_STIFFNESS = 500f
