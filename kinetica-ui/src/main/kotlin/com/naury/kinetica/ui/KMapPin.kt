package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 지도 위에 얹는 핀. 모양은 넘긴 값으로 정해진다.
 * - [count]가 2 이상: 묶음 핀. 강조색 원 안에 숫자를 쓰고 옅은 테두리를 두른다.
 * - [label]이 있음: 가격이나 이름을 쓴 캡슐 아래에 뾰족한 꼬리가 붙은 태그 핀. 고르면 강조색으로 채워진다.
 * - 그 밖: 물방울 모양 핀. [icon]이 있으면 가운데에 그리고, 없으면 흰 점을 찍는다.
 *
 * 핀의 아래 가운데(꼬리 끝, 묶음 핀은 원 아래)가 지도 좌표다. [Modifier.mapPinAnchor]를 주면 그 점이 좌표에 오도록 놓인다.
 * [dropIn]이면 처음 나타날 때 위에서 떨어지며 통통 튀고, 고르면([selected]) 아래 가운데를 축으로 커진다.
 * '애니메이션 삭제' 설정에서는 떨어지지 않고 바로 놓인다.
 *
 * 구글 지도처럼 SurfaceView로 그리는 지도 위에서는 글래스 표면이 지도를 블러로 비추지 못한다. 그런 지도 위에는 단색 표면을 쓴다.
 *
 * ```kotlin
 * KMapPin(Modifier.mapPinAnchor { place.toScreen() }, label = "₩82,000", selected = place == picked, onClick = { picked = place })
 * ```
 *
 * @param count 이 핀에 묶인 장소 수. 1보다 작으면 [IllegalArgumentException]을 던진다.
 * @param contentDescription TalkBack이 읽을 이름. null이면 [label]이나 '장소 n곳'을 읽는다.
 */
@Composable
public fun KMapPin(
    modifier: Modifier = Modifier,
    label: String? = null,
    count: Int = 1,
    selected: Boolean = false,
    icon: ImageVector? = null,
    tone: KTone = KTone.Accent,
    dropIn: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(count >= 1) { "count는 1 이상이어야 합니다." }
    val still = reducedMotion()
    val drop = remember { Animatable(if (dropIn && !still) 0f else 1f) }
    LaunchedEffect(Unit) { drop.animateTo(1f, spring(dampingRatio = DROP_DAMPING, stiffness = DROP_STIFFNESS)) }
    val grow =
        animateFloatAsState(
            if (selected) 1f else 0f,
            if (still) snap() else spring(dampingRatio = GROW_DAMPING, stiffness = GROW_STIFFNESS),
            label = "pin",
        )
    val name = contentDescription ?: label ?: if (count > 1) stringResource(R.string.kinetica_ui_map_cluster, count) else null
    val selectedLabel = stringResource(R.string.kinetica_ui_pin_selected)
    val layer = Modifier
        .graphicsLayer {
            transformOrigin = TransformOrigin(HALF, 1f)
            val d = drop.value
            translationY = -(1f - d) * DROP_HEIGHT.toPx()
            alpha = d.coerceIn(0f, 1f)
            val scale = 1f + grow.value * SELECTED_GROW
            scaleX = scale
            scaleY = scale
        }
        .semantics {
            name?.let { this.contentDescription = it }
            this.selected = selected
            if (onClick != null) role = Role.Button
            if (selected) stateDescription = selectedLabel
        }
    val colors = tone.colors(surface)
    when {
        count > 1 -> ClusterPin(count, colors, modifier.then(layer), onClick)
        label != null -> TagPin(label, selected, colors, surface, grow, modifier.then(layer), onClick)
        else -> DropPin(icon, colors, modifier.then(layer), onClick)
    }
}

/**
 * [KMapPin]의 꼬리 끝(묶음 핀은 원 아래 가운데)을 지도 위 [anchor]에 맞춘다. [anchor]는 지도 왼쪽 위를 원점으로 한 px 좌표이고,
 * 레이아웃 단계에서 읽으므로 지도를 옮기거나 확대해 좌표가 바뀌어도 핀을 다시 구성하지 않는다.
 *
 * 핀을 지도와 같은 크기의 `Box` 안에 두고 핀의 `modifier` 맨 앞에 준다. 이 수정자는 받은 최대 크기를 차지하고 그 안에서 핀만 옮기므로,
 * 부모의 정렬이나 RTL 배치와 상관없이 같은 자리에 놓인다. 지도 좌표에는 방향이 없어서 RTL에서도 좌우를 뒤집지 않는다.
 * 부모가 크기를 정하지 않으면(무한 제약) 핀 크기만 차지하고, 그때 [anchor]는 핀이 놓인 자리 기준이다.
 *
 * ```kotlin
 * Box(Modifier.fillMaxSize()) {
 *     places.forEach { place -> KMapPin(Modifier.mapPinAnchor { projection.toScreen(place) }, label = place.price) }
 * }
 * ```
 */
public fun Modifier.mapPinAnchor(anchor: () -> Offset): Modifier = layout { measurable, constraints ->
    val pin = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else pin.width
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else pin.height
    layout(width, height) {
        val point = anchor()
        pin.place((point.x - pin.width / 2f).roundToInt(), (point.y - pin.height).roundToInt())
    }
}

private fun Modifier.pinClick(shape: Shape, onClick: (() -> Unit)?): Modifier =
    if (onClick == null) this else clip(shape).kClickable(shape, role = null, onClick = onClick)

@Composable
private fun ClusterPin(count: Int, colors: ToneColors, modifier: Modifier, onClick: (() -> Unit)?) {
    Box(
        modifier
            .defaultMinSize(CLUSTER, CLUSTER)
            .drawWithCache {
                val halo = HALO.toPx()
                onDrawBehind {
                    drawCircle(colors.color.copy(alpha = HALO_ALPHA), size.minDimension / 2)
                    drawCircle(Color.White, size.minDimension / 2 - halo)
                    drawCircle(colors.color, size.minDimension / 2 - halo - BORDER.toPx())
                }
            }
            .pinClick(CircleShape, onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        ThemedText(
            if (count >
                MAX_COUNT
            ) {
                "$MAX_COUNT+"
            } else {
                "$count"
            },
            style = TextStyle(color = colors.onColor, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun TagPin(
    label: String,
    selected: Boolean,
    colors: ToneColors,
    surface: SurfaceStyle,
    grow: State<Float>,
    modifier: Modifier,
    onClick: (() -> Unit)?,
) {
    val shape = PinTagShape(TAG_POINTER)
    val ink = if (selected) colors.onColor else surface.contentColor
    Box(
        modifier
            .kSurface(surface.forPopup(), shape)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                onDrawBehind {
                    val g = grow.value
                    if (g > 0f) drawOutline(outline, colors.color.copy(alpha = g.coerceIn(0f, 1f)))
                }
            }
            .pinClick(shape, onClick)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp + TAG_POINTER),
        contentAlignment = Alignment.Center,
    ) {
        ThemedText(label, style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun DropPin(icon: ImageVector?, colors: ToneColors, modifier: Modifier, onClick: (() -> Unit)?) {
    Box(
        modifier
            .size(DROP_WIDTH, DROP_HEIGHT_PIN)
            .drawWithCache {
                val path = teardrop(size)
                val border = Stroke(BORDER.toPx())
                onDrawBehind {
                    drawPath(path, colors.color)
                    drawPath(path, Color.White, style = border)
                    if (icon == null) drawCircle(Color.White, size.width * DOT_SHARE, Offset(size.width / 2, size.width / 2))
                }
            }
            .pinClick(CircleShape, onClick),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (icon != null) {
            Image(
                rememberVectorPainter(icon),
                null,
                Modifier.padding(top = (DROP_WIDTH - PIN_ICON) / 2).size(PIN_ICON),
                colorFilter = ColorFilter.tint(colors.onColor),
            )
        }
    }
}

/** 위는 원, 아래는 원에 접하는 두 선이 만나는 꼭짓점인 물방울. */
private fun teardrop(size: Size): Path {
    val r = size.width / 2
    val cx = r
    val distance = (size.height - r).coerceAtLeast(r + 1f)
    val angle = acos(r / distance)
    val down = Math.PI.toFloat() / 2
    val left = Offset(cx + r * cos(down + angle), r + r * sin(down + angle))
    val right = Offset(cx + r * cos(down - angle), r + r * sin(down - angle))
    val circle = Path().apply { addOval(Rect(0f, 0f, size.width, size.width)) }
    val tip = Path().apply {
        moveTo(left.x, left.y)
        lineTo(cx, size.height)
        lineTo(right.x, right.y)
        close()
    }
    return Path.combine(PathOperation.Union, circle, tip)
}

/** 아래 가운데에 [pointer] 높이의 꼬리가 달린 캡슐. */
private class PinTagShape(private val pointer: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tail = with(density) { pointer.toPx() }
        val bodyHeight = size.height - tail
        val radius = bodyHeight / 2
        val body = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, bodyHeight, CornerRadius(radius)))
        }
        val cx = size.width / 2
        val tip = Path().apply {
            moveTo(cx - tail, bodyHeight - 1f)
            lineTo(cx, size.height)
            lineTo(cx + tail, bodyHeight - 1f)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, tip))
    }

    override fun equals(other: Any?): Boolean = other is PinTagShape && other.pointer == pointer

    override fun hashCode(): Int = pointer.hashCode()
}

private val DROP_HEIGHT = 56.dp
private val CLUSTER = 44.dp
private val HALO = 5.dp
private val BORDER = 2.dp
private val TAG_POINTER = 7.dp
private val DROP_WIDTH = 30.dp
private val DROP_HEIGHT_PIN = 40.dp
private val PIN_ICON = 16.dp
private const val HALF = 0.5f
private const val DROP_DAMPING = 0.45f
private const val DROP_STIFFNESS = 380f
private const val GROW_DAMPING = 0.55f
private const val GROW_STIFFNESS = 500f
private const val SELECTED_GROW = 0.18f
private const val HALO_ALPHA = 0.28f
private const val DOT_SHARE = 0.16f
private const val MAX_COUNT = 99
