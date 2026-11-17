package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 목록 맨 위 대표 이미지 헤더. 이미지 위에 아래쪽으로 짙어지는 그라디언트를 깔고 제목과 부제를 놓는다.
 * 맨 위에서 아래로 더 당기면 위쪽을 고정한 채 헤더가 늘어나며 이미지가 확대되고, 손을 떼면 튕기며 돌아온다.
 * 위로 스크롤하면 이미지는 콘텐츠보다 느리게 올라가며(패럴랙스) 사라지고, 헤더 아래 끝이 [KStretchyHeaderBar]에 가까워지면
 * 큰 제목이 흐려지며 바로 넘어간다.
 *
 * 헤더는 `LazyColumn`의 첫 항목이나 `Column(verticalScroll)`의 맨 위에 둔다. 늘이기, 패럴랙스, 제목 흐림은 모두 배치와 그리기 단계에서만
 * 바뀌므로 스크롤 중에 다시 구성하지 않는다. 이미지 슬롯은 처음 높이로 한 번만 측정하고 확대는 graphicsLayer로 한다.
 *
 * ```kotlin
 * val list = rememberLazyListState()
 * val header = rememberKStretchyHeaderState(list)
 * Box {
 *     LazyColumn(Modifier.nestedScroll(header.connection), state = list) {
 *         item { KStretchyHeader(header, "제주 한 달 살기", subtitle = "서귀포, 4월") { Image(painter, null, contentScale = ContentScale.Crop) } }
 *         items(places) { ... }
 *     }
 *     KStretchyHeaderBar(header, "제주 한 달 살기", onBack = ::finish)
 * }
 * ```
 *
 * @param height 당기지 않았을 때 헤더 높이. 0보다 커야 하며 아니면 [IllegalArgumentException]을 던진다.
 * @param parallax 콘텐츠가 스크롤한 거리 중 이미지가 덜 움직일 비율(0..1). 0이면 함께 움직이고 1이면 제자리에 머문다.
 *   범위를 벗어나면 [IllegalArgumentException]을 던진다.
 * @param scrim 이미지 아래쪽에 까는 그라디언트 색. 알파가 아래 끝 진하기다. 투명하면 그리지 않는다.
 * @param contentColor 제목과 부제 색
 * @param image 이미지 슬롯. 헤더 크기를 꽉 채우도록 `Modifier.fillMaxSize()`와 `ContentScale.Crop`을 권한다. 이미지 설명은 이 안에서 정한다.
 */
@Composable
public fun KStretchyHeader(
    state: KStretchyHeaderState,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    height: Dp = DEFAULT_HEIGHT,
    parallax: Float = DEFAULT_PARALLAX,
    scrim: Color = Color.Black.copy(alpha = DEFAULT_SCRIM_ALPHA),
    contentColor: Color = Color.White,
    image: @Composable BoxScope.() -> Unit,
) {
    require(height > 0.dp) { "height는 0보다 커야 합니다." }
    require(parallax in 0f..1f) { "parallax는 0..1이어야 합니다." }
    val density = LocalDensity.current
    val heightPx = with(density) { height.toPx() }
    SideEffect {
        state.headerHeight = heightPx
        state.stretchLimit = with(density) { STRETCH_LIMIT.toPx() }
    }
    val titleInBar = remember(state) { derivedStateOf { state.isTitleInBar } }
    Box(modifier.fillMaxWidth().stretchHeight(state, heightPx.roundToInt()).clipToBounds()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer {
                    val offset = state.offset
                    // 헤더가 화면 밖으로 다 나가면 offset이 무한대라 옮길 값을 높이로 묶는다.
                    translationY = minOf(offset, heightPx) * parallax
                    val zoom = 1f + state.stretch / heightPx
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = TransformOrigin(HALF, 0f)
                },
            content = image,
        )
        if (scrim.alpha > 0f) Box(Modifier.matchParentSize().scrim(scrim))
        HeaderTitle(title, subtitle, contentColor, hidden = titleInBar.value) { state.headerTitleAlpha() }
    }
}

@Composable
private fun BoxScope.HeaderTitle(title: String, subtitle: String?, color: Color, hidden: Boolean, alpha: () -> Float) {
    Column(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .padding(horizontal = TITLE_PADDING, vertical = TITLE_PADDING)
            .graphicsLayer { this.alpha = alpha() }
            // 제목이 바로 넘어가면 바 쪽만 읽히도록 여기 의미는 지운다.
            .then(if (hidden) Modifier.clearAndSetSemantics { } else Modifier.semantics(mergeDescendants = true) { heading() }),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemedText(
            title,
            style = TextStyle(color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            ThemedText(it, style = TextStyle(color = color.copy(alpha = SUBTITLE_ALPHA), fontSize = 15.sp), maxLines = 2)
        }
    }
}

/** 당긴 만큼 헤더 높이를 늘린다. 배치 단계에서 읽으므로 당기는 동안 다시 구성하지 않는다. */
private fun Modifier.stretchHeight(state: KStretchyHeaderState, base: Int): Modifier = layout { measurable, constraints ->
    val h = (base + state.stretch.roundToInt()).coerceAtLeast(0)
    val placeable = measurable.measure(
        Constraints(minWidth = constraints.minWidth, maxWidth = constraints.maxWidth, minHeight = h, maxHeight = h),
    )
    layout(placeable.width, h) { placeable.place(0, 0) }
}

/** 아래로 짙어지는 그라디언트. 붓은 크기가 바뀔 때만 만든다. */
private fun Modifier.scrim(color: Color): Modifier = drawWithCache {
    val brush = Brush.verticalGradient(
        0f to Color.Transparent,
        SCRIM_TOP_STOP to color.copy(alpha = color.alpha * SCRIM_TOP_ALPHA),
        SCRIM_MID_STOP to color.copy(alpha = color.alpha * SCRIM_MID_ALPHA),
        1f to color,
    )
    onDrawBehind { drawRect(brush) }
}

/**
 * [KStretchyHeader] 위에 겹쳐 두는 상단 바. 처음에는 배경 없이 이미지 위에 뒤로 버튼과 [actions]만 [overImageColor]로 보이고,
 * 헤더 아래 끝이 바에 닿으면 표면 배경이 차오르며 버튼 색이 표면 글자 색으로 바뀐다. 큰 제목이 흐려지면 바 가운데 쪽 제목이 대신 나타난다.
 * 배경과 제목 변화는 그리기 단계에서만 읽고, 버튼 색은 배경이 반쯤 찼을 때 한 번 바뀐다.
 *
 * [KTopAppBar]를 바탕으로 하므로 높이는 고정이다. 목록과 같은 `Box` 안에서 목록 위에 겹쳐 둔다.
 *
 * @param onBack null이 아니면 시작 쪽에 뒤로 버튼을 놓는다. RTL에서는 화살표가 뒤집힌다.
 * @param actions 끝 쪽 버튼들. 지금 써야 할 글자 색을 받는다. 보통 `KIconButton(variant = KVariant.Text, contentColor = it)`을 넣는다.
 * @param overImageColor 배경이 차오르기 전 이미지 위에서 쓸 버튼과 제목 색
 * @param windowInsets 바가 피할 시스템 영역. 기본값은 상태 표시줄과 양옆 컷아웃이다. 화면 일부에 둘 때는 `WindowInsets(0)`을 넘긴다.
 */
@Composable
public fun KStretchyHeaderBar(
    state: KStretchyHeaderState,
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.(contentColor: Color) -> Unit = {},
    overImageColor: Color = Color.White,
    surface: SurfaceStyle = KineticaTheme.surface,
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
) {
    val bar = state.barState
    val onImage = remember(state) { derivedStateOf { bar.overlappedFraction < HALF } }
    val inBar = remember(state) { derivedStateOf { state.isTitleInBar } }
    val ink = if (onImage.value) overImageColor else surface.contentColor
    val back = stringResource(R.string.kinetica_ui_large_title_back)
    KTopAppBar(
        title = {
            ThemedText(
                title,
                Modifier
                    .graphicsLayer {
                        val a = state.barTitleAlpha()
                        alpha = a
                        translationY = (1f - a) * BAR_TITLE_RISE.toPx()
                    }
                    .then(if (inBar.value) Modifier.semantics { heading() } else Modifier.clearAndSetSemantics { }),
                style = textStyle.copy(color = ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier.onSizeChanged { state.barHeight = it.height.toFloat() },
        state = bar,
        navigationIcon = onBack?.let { action ->
            {
                KIconButton(
                    KGlyphs.ArrowBack,
                    back,
                    action,
                    variant = KVariant.Text,
                    tone = KTone.Neutral,
                    surface = surface,
                    contentColor = ink,
                )
            }
        },
        actions = { actions(ink) },
        surface = surface,
        windowInsets = windowInsets,
    )
}

private const val HALF = 0.5f
private const val DEFAULT_PARALLAX = 0.5f
private const val DEFAULT_SCRIM_ALPHA = 0.62f
private const val SUBTITLE_ALPHA = 0.85f
private const val SCRIM_TOP_STOP = 0.35f
private const val SCRIM_TOP_ALPHA = 0.08f
private const val SCRIM_MID_STOP = 0.65f
private const val SCRIM_MID_ALPHA = 0.45f
private val DEFAULT_HEIGHT = 300.dp
private val STRETCH_LIMIT = 220.dp
private val TITLE_PADDING = 20.dp
private val BAR_TITLE_RISE = 8.dp
