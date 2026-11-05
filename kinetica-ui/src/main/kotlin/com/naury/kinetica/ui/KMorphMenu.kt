package com.naury.kinetica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * 누르면 칩이 그 자리에서 늘어나며 메뉴 판으로 모양이 바뀌는 메뉴. iOS의 글래스 메뉴처럼 칩의 캡슐 모양이 메뉴 판의 둥근 사각형으로
 * 이어서 바뀌고, 항목은 판이 펴지는 동안 드러난다. 닫으면 같은 길을 되짚어 칩으로 접힌다.
 *
 * 판이 커지는 동안 화면을 다시 구성하지 않는다. 크기와 모서리는 배치 단계에서, 항목이 드러나는 범위는 그리기 단계에서만 바뀌고,
 * 글래스 블러는 이미 기록해 둔 배경을 판 모양으로 잘라 쓰므로 판이 커져도 블러를 새로 하지 않는다. '애니메이션 삭제'면 모양 변화 없이 바로 열고 닫는다.
 *
 * 메뉴는 아래에 자리가 있으면 아래로, 없으면 위로 펴지고 화면 밖으로 나가지 않는다. 항목, 키보드 이동, 하위 메뉴, TalkBack은 [KDropdownMenu]와 같다.
 *
 * ```kotlin
 * var open by remember { mutableStateOf(false) }
 * KMorphMenu("정렬: 최신순", open, { open = it }, listOf(
 *     KMenuEntry.Action("최신순") { sort(New) },
 *     KMenuEntry.Action("인기순") { sort(Popular) },
 * ))
 * ```
 *
 * @param label 칩에 쓰는 글자. TalkBack 이름도 된다.
 * @param expanded 메뉴가 열려 있는지
 * @param onExpandedChange 칩을 누르거나, 바깥을 누르거나, 뒤로 가기, 항목 실행으로 열림이 바뀔 때 부른다.
 * @param entries 메뉴 항목. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param icon 칩 글자 앞 아이콘
 * @param alignEnd true면 판의 끝 가장자리를 칩 끝에 맞춘다. 화면 끝 쪽 칩에 쓴다.
 * @param minWidth 판의 최소 폭
 * @param surface 칩과 판의 표면. 판은 이 표면의 팝업용 모양(`forPopup`)으로 그린다.
 */
@Composable
public fun KMorphMenu(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    entries: List<KMenuEntry>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    alignEnd: Boolean = false,
    minWidth: Dp = KDropdownMenuDefaults.MinWidth,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(entries.isNotEmpty()) { "entries가 비어 있으면 안 됩니다." }
    val change by rememberUpdatedState(onExpandedChange)
    val anchor = remember { MorphAnchor() }
    val latest by rememberUpdatedState(entries)
    val chip = MorphChipSpec(label, icon, surface, enabled)
    MorphChip(
        chip,
        // 판이 떠 있는 동안에는 판이 칩 자리에서 시작하고 끝나므로 원래 칩은 숨긴다.
        modifier
            .onGloballyPositioned { anchor.bounds = it.boundsOnScreen() }
            .graphicsLayer { alpha = if (anchor.showing) 0f else 1f }
            .semantics {
                role = Role.DropdownList
                if (!enabled) {
                    disabled()
                } else if (expanded) {
                    collapse {
                        change(false)
                        true
                    }
                } else {
                    expand {
                        change(true)
                        true
                    }
                }
            },
    ) { change(!expanded) }
    KOverlay(expanded, OverlayKind.Modal, onHidden = { anchor.showing = false }) { transition ->
        anchor.showing = true
        BackHandler(enabled = transition.targetState) { change(false) }
        MorphLayer(anchor, transition, chip, MenuLook(surface, minWidth, transition.targetState), alignEnd, latest) { change(false) }
    }
}

/** 칩의 화면 경계와, 판이 떠 있어 원래 칩을 숨기는 중인지 */
@Stable
private class MorphAnchor {
    var bounds by mutableStateOf(Rect.Zero)
    var showing by mutableStateOf(false)
}

private class MorphChipSpec(val label: String, val icon: ImageVector?, val surface: SurfaceStyle, val enabled: Boolean)

/** 칩 모양 버튼. 글자 뒤에 아래 꺾쇠를 둔다. */
@Composable
private fun MorphChip(spec: MorphChipSpec, modifier: Modifier, onClick: () -> Unit) {
    val shape = chipShape
    val ink = spec.surface.contentColor
    Row(
        modifier
            .graphicsLayer { alpha = if (spec.enabled) 1f else DISABLED_ALPHA }
            .heightIn(min = MORPH_CHIP_HEIGHT)
            .kSurface(spec.surface, shape)
            .clip(shape)
            .clickable(null, KIndication(shape, ink), spec.enabled, role = Role.Button, onClick = onClick)
            .padding(start = if (spec.icon != null) 10.dp else 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { MorphChipContent(spec) }
}

@Composable
private fun MorphChipContent(spec: MorphChipSpec) {
    val ink = spec.surface.contentColor
    val filter = ColorFilter.tint(ink)
    spec.icon?.let {
        Image(rememberVectorPainter(it), null, Modifier.size(18.dp), colorFilter = filter)
        Spacer(Modifier.width(6.dp))
    }
    ThemedText(
        spec.label,
        style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = KineticaTheme.design.label(FontWeight.Medium)),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.width(4.dp))
    Image(rememberVectorPainter(KIcons.ChevronDown), null, Modifier.size(16.dp), colorFilter = filter)
}

/**
 * 오버레이 전체를 차지하는 층. 바깥 누름을 받고, 판 배경, 칩 잔상, 메뉴 내용 세 겹을 한 Layout에 놓는다.
 * 진행도는 배치와 그리기 단계에서만 읽으므로 애니메이션 동안 이 층을 다시 구성하지 않는다.
 */
@Composable
private fun MorphLayer(
    anchor: MorphAnchor,
    transition: Transition<Boolean>,
    chip: MorphChipSpec,
    look: MenuLook,
    alignEnd: Boolean,
    entries: List<KMenuEntry>,
    onDismiss: () -> Unit,
) {
    val still = isReducedMotion()
    val progress = transition.animateFloat(
        transitionSpec = {
            when {
                still -> tween(0)
                targetState -> spring(OPEN_DAMPING, OPEN_STIFFNESS, MORPH_THRESHOLD)
                else -> spring(CLOSE_DAMPING, CLOSE_STIFFNESS, MORPH_THRESHOLD)
            }
        },
        label = "morph-menu",
    ) { if (it) 1f else 0f }
    val geometry = remember { MorphGeometry() }
    geometry.startShape = chipShape
    geometry.endShape = PopupPanelShape
    geometry.alignRight = (LocalLayoutDirection.current == LayoutDirection.Rtl) != alignEnd
    var origin by remember { mutableStateOf(Offset.Zero) }
    val safe = WindowInsets.safeDrawing
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }) {
        OutsideCatcher(onDismiss)
        Layout(
            content = { MorphSlots(progress::value, geometry, chip, look, entries, onDismiss) },
        ) { measurables, constraints ->
            val insets = safe.toIntRect(this)
            val margin = EDGE.roundToPx()
            val area = IntRect(
                insets.left + margin,
                insets.top + margin,
                constraints.maxWidth - insets.right - margin,
                constraints.maxHeight - insets.bottom - margin,
            )
            val chipRect = anchor.bounds.translate(-origin)
            val gap = GAP.toPx()
            val content = measurables[2].measure(
                Constraints(maxWidth = area.width.coerceAtLeast(0), maxHeight = anchoredRoom(chipRect, area, gap)),
            )
            val place = placeAnchored(chipRect, area, IntSize(content.width, content.height), gap, geometry.alignRight)
            val current = geometry.update(chipRect, IntOffset(place.x, place.y), content, progress.value, this)
            val background = measurables[0].measure(Constraints.fixed(current.width.toPx(), current.height.toPx()))
            val ghost = measurables[1].measure(Constraints.fixed(chipRect.width.roundToInt(), chipRect.height.roundToInt()))
            layout(constraints.maxWidth, constraints.maxHeight) {
                background.place(current.left.roundToInt(), current.top.roundToInt())
                // 잔상은 판과 같은 쪽 가장자리에 붙어 판과 함께 움직인다.
                val ghostX = if (geometry.alignRight) current.right - chipRect.width else current.left
                val ghostY = if (place.below) current.top else current.bottom - chipRect.height
                ghost.place(ghostX.roundToInt(), ghostY.roundToInt())
                content.place(place.x, place.y)
            }
        }
    }
}

/** [MorphLayer]의 세 겹: 0 판 배경, 1 칩 잔상, 2 메뉴 내용 */
@Composable
private fun MorphSlots(
    progress: () -> Float,
    geometry: MorphGeometry,
    chip: MorphChipSpec,
    look: MenuLook,
    entries: List<KMenuEntry>,
    onDismiss: () -> Unit,
) {
    val clip = remember { Path() }
    // 크기는 배치 단계에서 매 프레임 바뀌고, 모서리는 그 크기에서 정해진다.
    Box(Modifier.kSurface(look.surface.forPopup(), geometry.shape))
    // 열리기 시작할 때 원래 칩과 같은 모습으로 있다가 옅어진다.
    Row(
        Modifier
            .clearAndSetSemantics {}
            .graphicsLayer { alpha = (1f - progress() * CHIP_FADE).coerceIn(0f, 1f) }
            .padding(start = if (chip.icon != null) 10.dp else 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { MorphChipContent(chip) }
    // 다 펴진 크기로 한 번만 측정하고, 판이 덮은 만큼만 그린다.
    Box(
        Modifier
            .graphicsLayer { alpha = reveal(progress()) }
            .drawWithContent {
                if (reveal(progress()) <= 0f) return@drawWithContent
                clip.reset()
                clip.addRoundRect(geometry.shape.roundRect(geometry.current.translate(-geometry.target.topLeft)))
                clipPath(clip) { this@drawWithContent.drawContent() }
            }
            .padding(vertical = PopupPanelPadding),
    ) {
        CompositionLocalProvider(LocalKineticaContentColor provides look.surface.contentColor) {
            KGlassLayer { MenuPages(entries, look, onDismiss) }
        }
    }
}

/** 배치 단계가 매 프레임 채우는 판 경계(층 좌표, px)와 판 모양 */
private class MorphGeometry {
    val shape = MenuMorphShape()
    var startShape: Shape = RectangleShape
    var endShape: Shape = RectangleShape
    var alignRight = false
    var current = Rect.Zero
        private set
    var target = Rect.Zero
        private set

    /** 칩 경계에서 다 펴진 경계까지 [progress]만큼 간 경계를 정해 돌려준다. */
    fun update(chip: Rect, at: IntOffset, panel: Placeable, progress: Float, density: Density): Rect {
        target = Rect(Offset(at.x.toFloat(), at.y.toFloat()), Size(panel.width.toFloat(), panel.height.toFloat()))
        current = Rect(
            lerp(chip.left, target.left, progress),
            lerp(chip.top, target.top, progress),
            lerp(chip.right, target.right, progress),
            lerp(chip.bottom, target.bottom, progress),
        )
        shape.update(startShape, endShape, chip.size, target.size, density)
        return current
    }
}

private fun Float.toPx(): Int = roundToInt().coerceAtLeast(0)

/**
 * 판 모양. 크기가 칩에서 메뉴 판으로 커지는 동안 모서리 반지름을 칩 모양에서 메뉴 판 모양으로 이어서 바꾼다.
 * 크기만으로 진행 정도를 알므로 애니메이션 상태를 읽지 않는다. 배치 단계가 [update]로 양 끝 값을 넣는다.
 */
private class MenuMorphShape : Shape {
    private var startSize = Size.Zero
    private var endSize = Size.Zero
    private var startRadius = 0f
    private var endRadius = 0f

    fun update(start: Shape, end: Shape, startSize: Size, endSize: Size, density: Density) {
        this.startSize = startSize
        this.endSize = endSize
        startRadius = radiusOf(start, startSize, density)
        endRadius = radiusOf(end, endSize, density)
    }

    fun roundRect(rect: Rect): RoundRect {
        val r = radiusFor(rect.size)
        return RoundRect(rect, CornerRadius(r, r))
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(roundRect(Rect(Offset.Zero, size)))

    private fun radiusFor(size: Size): Float {
        val span = endSize.height - startSize.height
        val t = if (span <= 0f) 1f else ((size.height - startSize.height) / span).coerceIn(0f, 1f)
        return lerp(startRadius, endRadius, t).coerceAtMost(minOf(size.width, size.height) / 2)
    }

    private fun radiusOf(shape: Shape, size: Size, density: Density): Float {
        if (size.width <= 0f || size.height <= 0f) return 0f
        return when (val outline = shape.createOutline(size, LayoutDirection.Ltr, density)) {
            is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
            is Outline.Rectangle -> 0f
            else -> minOf(size.width, size.height) / 2
        }
    }
}

/** 판이 [CONTENT_DELAY]만큼 펴진 뒤부터 항목이 드러나는 정도(0~1) */
private fun reveal(progress: Float): Float = ((progress - CONTENT_DELAY) / (1f - CONTENT_DELAY)).coerceIn(0f, 1f)

private val MORPH_CHIP_HEIGHT = 36.dp
private val EDGE = 12.dp
private val GAP = 6.dp
private const val OPEN_DAMPING = 0.78f
private const val OPEN_STIFFNESS = 420f
private const val CLOSE_DAMPING = 1f
private const val CLOSE_STIFFNESS = 700f
private const val CHIP_FADE = 3f
private const val CONTENT_DELAY = 0.25f
private const val DISABLED_ALPHA = 0.4f
private const val MORPH_THRESHOLD = 0.001f
