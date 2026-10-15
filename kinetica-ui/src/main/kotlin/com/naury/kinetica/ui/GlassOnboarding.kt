package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * 첫 실행 안내 화면. 페이지를 넘기면 그림이 시차를 두고 따라오고 배경색이 다음 페이지 색으로 섞이며 바뀐다.
 * 아래에는 [GlassPageIndicator]와 '다음' 버튼이 있고, 마지막 페이지에 다가가면 버튼이 가로로 늘어나며 '시작하기'로 바뀐다.
 * 위쪽 끝의 '건너뛰기'와 마지막 페이지의 '시작하기'는 [onFinish]를 부른다. 화면을 가득 채우며 시스템 바와 컷아웃을 피한다.
 *
 * ```kotlin
 * GlassOnboarding(
 *     pages = listOf(
 *         OnboardingPage("사진을 한곳에", "기기와 클라우드의 사진을 모아 봅니다", art = { Image(painterResource(R.drawable.intro1), null) }),
 *         OnboardingPage("함께 보기", "앨범을 가족과 나눕니다", art = { Image(painterResource(R.drawable.intro2), null) }),
 *     ),
 *     onFinish = { navController.navigate("home") },
 * )
 * ```
 *
 * @param pages 페이지 목록. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param state 페이지 상태. 페이지 수는 [pages] 크기와 같아야 한다.
 * @param contentColor 글자, 인디케이터, 버튼 바탕 색. 배경색 위에서 잘 읽히는 색을 쓴다.
 */
@Composable
public fun GlassOnboarding(
    pages: List<OnboardingPage>,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    state: PagerState = rememberPagerState { pages.size },
    contentColor: Color = Color.White,
    style: GlassStyle = LocalGlassStyle.current,
) {
    require(pages.isNotEmpty()) { "pages가 비어 있습니다." }
    val colors = remember(pages) { pages.mapIndexed { i, p -> p.background.takeOrElse { DefaultColors[i % DefaultColors.size] } } }
    val position = { (state.currentPage + state.currentPageOffsetFraction).coerceIn(0f, (pages.size - 1).toFloat()) }
    val background = { blend(colors, position()) }
    // 마지막 페이지에 다가간 정도(0..1). 페이지가 하나면 처음부터 1이다.
    val finishing = { (position() - (pages.size - 2)).coerceIn(0f, 1f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier.fillMaxSize().onboardingBackground(background, position)) {
        HorizontalPager(state, Modifier.fillMaxSize()) { page ->
            PageContent(pages[page], contentColor) { state.getOffsetDistanceInPages(page) * if (rtl) -1f else 1f }
        }
        val scope = rememberCoroutineScope()
        val last = state.currentPage == pages.size - 1
        // 태블릿이나 펼친 폴드에서도 버튼이 화면 양 끝으로 벌어지거나 화면 너비만큼 늘어나지 않게 가운데 폭으로 묶는다.
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).centeredMaxWidth()) {
            SkipButton(contentColor, enabled = !last, alpha = { 1f - finishing() }, onClick = onFinish)
            GlassPageIndicator(
                state,
                Modifier.align(Alignment.BottomStart).padding(start = EDGE, bottom = EDGE + INDICATOR_LIFT).graphicsLayer {
                    alpha =
                        1f - finishing()
                },
                style = style.copy(onGlass = contentColor),
            )
            NextButton(last, contentColor, background, finishing) {
                if (last) onFinish() else scope.launch { state.animateScrollToPage(state.currentPage + 1) }
            }
        }
    }
}

/** 섞이는 배경색 위에 빛 번짐 두 개가 페이지 위치를 따라 천천히 떠다닌다. 붓은 크기가 바뀔 때만 만든다. */
private fun Modifier.onboardingBackground(color: () -> Color, position: () -> Float): Modifier = drawWithCache {
    val radius = size.maxDimension * GLOW_RADIUS
    val glow = Brush.radialGradient(listOf(Color.White.copy(alpha = GLOW_ALPHA), Color.Transparent), Offset.Zero, radius)
    val shade = Brush.radialGradient(listOf(Color.Black.copy(alpha = SHADE_ALPHA), Color.Transparent), Offset.Zero, radius)
    onDrawBehind {
        drawRect(color())
        val p = position()
        translate(size.width * (GLOW_X + GLOW_SWING * sin(p * GLOW_SPEED)), size.height * GLOW_Y) {
            drawCircle(glow, radius, Offset.Zero)
        }
        translate(size.width * (SHADE_X - GLOW_SWING * sin(p * GLOW_SPEED + 1f)), size.height * SHADE_Y) {
            drawCircle(shade, radius, Offset.Zero)
        }
    }
}

private fun Modifier.centeredMaxWidth(): Modifier = wrapContentWidth().widthIn(max = WIDE_MAX).fillMaxWidth()

/** [offset]은 이 페이지가 가운데에서 다음 페이지 쪽(화면 오른쪽)으로 떨어진 정도로, 그리기 단계에서만 읽는다. */
@Composable
private fun PageContent(page: OnboardingPage, contentColor: Color, offset: () -> Float) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .centeredMaxWidth()
            .padding(start = PAGE_PADDING, end = PAGE_PADDING, top = ART_TOP, bottom = BOTTOM_ROOM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    val o = offset()
                    // 그림은 페이지보다 느리게 움직여 이웃 페이지 그림과 잠시 겹친다.
                    translationX = -o * size.width * ART_PARALLAX
                    val scale = 1f - abs(o).coerceAtMost(1f) * ART_SHRINK
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - abs(o).coerceAtMost(1f) * ART_FADE
                },
            contentAlignment = Alignment.Center,
        ) { page.art() }
        Spacer(Modifier.height(TEXT_GAP))
        ThemedText(
            page.title,
            Modifier.semantics { heading() }.graphicsLayer { translationX = offset() * size.width * TITLE_PARALLAX },
            style = TextStyle(
                color = contentColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineBreak = PhraseBreak,
            ),
        )
        Spacer(Modifier.height(BODY_GAP))
        ThemedText(
            page.body,
            Modifier.graphicsLayer { translationX = offset() * size.width * BODY_PARALLAX },
            style = TextStyle(
                color = contentColor.copy(alpha = BODY_ALPHA),
                fontSize = 16.sp,
                lineHeight = 23.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun BoxScope.SkipButton(color: Color, enabled: Boolean, alpha: () -> Float, onClick: () -> Unit) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(SKIP_MARGIN)
            .graphicsLayer { this.alpha = alpha() }
            .clip(CircleShape)
            .clickable(null, GlassIndication(CircleShape, color), enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = GlassMetrics.MinTouch)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        ThemedText(
            stringResource(R.string.kinetica_ui_onboarding_skip),
            style = TextStyle(color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** 마지막 페이지에 다가갈수록([finishing]) 가로로 늘어나며 글자가 '다음'에서 '시작하기'로 바뀌는 버튼. 글자는 배경색으로 칠한다. */
@Composable
private fun BoxScope.NextButton(last: Boolean, color: Color, textColor: () -> Color, finishing: () -> Float, onClick: () -> Unit) {
    val next = stringResource(R.string.kinetica_ui_onboarding_next)
    val start = stringResource(R.string.kinetica_ui_onboarding_start)
    val pressInk = if (color.luminance() > HALF) Color.Black else Color.White
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(EDGE)
            .layout { measurable, constraints ->
                val narrow = BUTTON_MIN_WIDTH.roundToPx().coerceAtMost(constraints.maxWidth)
                val w = (narrow + (constraints.maxWidth - narrow) * finishing()).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(w, BUTTON_HEIGHT.roundToPx()))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .clip(CircleShape)
            .background(color)
            .clickable(null, GlassIndication(CircleShape, pressInk), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (last) start else next },
        contentAlignment = Alignment.Center,
    ) {
        val label = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        // 두 글자가 겹쳐 보이지 않도록 앞 절반에서 '다음'이 사라지고 뒤 절반에서 '시작하기'가 나타난다.
        ThemedText(
            next,
            Modifier.clearAndSetSemantics { }.graphicsLayer { alpha = (1f - finishing() * 2f).coerceIn(0f, 1f) },
            label,
            maxLines = 1,
            color = { textColor() },
        )
        ThemedText(
            start,
            Modifier.clearAndSetSemantics { }.graphicsLayer { alpha = (finishing() * 2f - 1f).coerceIn(0f, 1f) },
            label,
            maxLines = 1,
            color = { textColor() },
        )
    }
}

/** 한국어와 일본어 문장을 어절 단위로 줄 바꾼다. 가운데 정렬한 짧은 문장이 글자 하나만 다음 줄로 넘어가지 않게 한다. */
internal val PhraseBreak: LineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)

private fun blend(colors: List<Color>, position: Float): Color {
    val from = floor(position).toInt().coerceIn(0, colors.size - 1)
    val to = (from + 1).coerceAtMost(colors.size - 1)
    return lerp(colors[from], colors[to], position - from)
}

// 이웃한 색끼리 섞일 때 탁해지지 않도록 색상환에서 가까운 순서로 둔다.
private val DefaultColors = listOf(Color(0xFF4F46E5), Color(0xFF8B3FE0), Color(0xFFD23C7E), Color(0xFFE8603C), Color(0xFFE59A1F))

private const val GLOW_RADIUS = 0.6f
private const val GLOW_ALPHA = 0.28f
private const val SHADE_ALPHA = 0.18f
private const val GLOW_X = 0.25f
private const val GLOW_Y = 0.22f
private const val SHADE_X = 0.85f
private const val SHADE_Y = 0.85f
private const val GLOW_SWING = 0.18f
private const val GLOW_SPEED = 1.4f

private val PAGE_PADDING = 32.dp
private val WIDE_MAX = 600.dp
private val ART_TOP = 72.dp
private val BOTTOM_ROOM = 128.dp
private val TEXT_GAP = 32.dp
private val BODY_GAP = 12.dp
private const val BODY_ALPHA = 0.86f
private const val ART_PARALLAX = 0.5f
private const val ART_SHRINK = 0.18f
private const val ART_FADE = 0.5f
private const val TITLE_PARALLAX = 0.18f
private const val BODY_PARALLAX = 0.32f

private val EDGE = 24.dp
private val INDICATOR_LIFT = 16.dp
private val SKIP_MARGIN = 12.dp
private val BUTTON_MIN_WIDTH = 120.dp
private val BUTTON_HEIGHT = 56.dp
private const val HALF = 0.5f
