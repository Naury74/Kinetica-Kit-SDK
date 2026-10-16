// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIndication
import com.naury.kinetica.ui.KSurface
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.jellyPress

/**
 * 데모 상단 바. 뒤로 가기 버튼, 제목, 화면을 한 줄로 설명하는 부제와 표면 스타일 버튼을 보여 준다. 넓은 화면에서는 읽기 폭에 맞춰 가운데 둔다.
 * [onBack]이 `null`이면 뒤로 버튼을 두지 않는다(두 칸 배치의 상세 칸처럼 돌아갈 곳이 없을 때).
 */
@Composable
internal fun DemoTopBar(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    maxWidth: Dp = READING_WIDTH,
    scrolled: (() -> Boolean)? = null,
) {
    Box(Modifier.fillMaxWidth()) {
        StatusBarScrim(scrolled = scrolled)
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.TopCenter,
        ) {
            // 바깥 28dp 캡슐 안에 6dp 여백으로 44dp 원형 버튼을 넣어 모서리가 같은 중심으로 굽게 한다.
            KSurface(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = maxWidth).fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
            ) {
                Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        KIconButton(
                            ShowcaseIcons.Back,
                            "뒤로",
                            onBack,
                            variant = KVariant.Text,
                            tone = KTone.Neutral,
                        )
                    }
                    Column(
                        Modifier.weight(1f).padding(
                            start = if (onBack !=
                                null
                            ) {
                                6.dp
                            } else {
                                14.dp
                            },
                            top = 4.dp,
                            bottom = 4.dp,
                            end = 4.dp,
                        ),
                    ) {
                        Label(title, 17, FontWeight.Bold)
                        Label(subtitle, 12, color = LocalKineticaContentColor.current.copy(alpha = 0.65f))
                    }
                    SurfacePickerButton()
                }
            }
        }
    }
}

/**
 * 스크롤한 내용이 상태 표시줄 아래로 지나갈 때 시계, 아이콘과 겹쳐 읽기 어렵지 않도록 위에서 아래로 옅어지는 그라디언트를 깐다.
 * 띠처럼 보이지 않게 상태 표시줄보다 조금 더 내려와 끝이 0으로 사라진다. KineticaScaffold의 가장자리 블러와 함께 쓴다.
 * 표면 글자 색이 밝으면(어두운 화면) 검정, 아니면 흰색이다.
 * [scrolled]를 주면 맨 위에 있을 때는 거의 보이지 않다가 내용이 상태 표시줄 아래로 들어가면 짙어진다.
 * 없으면 스크롤 위치를 모르므로 중간 세기로 늘 깐다.
 */
@Composable
internal fun StatusBarScrim(
    color: Color = if (LocalKineticaContentColor.current.isLight()) Color.Black else Color.White,
    scrolled: (() -> Boolean)? = null,
) {
    val target = when {
        scrolled == null -> SCRIM_RESTING
        scrolled() -> 1f
        else -> SCRIM_TOP
    }
    val level by animateFloatAsState(target, tween(SCRIM_FADE_MS), label = "statusScrim")
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        Modifier
            .fillMaxWidth()
            .height(statusTop + SCRIM_EXTRA)
            .drawWithCache {
                // 상태 표시줄이 끝나는 지점까지는 거의 그대로 두고, 그 아래에서 부드럽게 0으로 떨어진다.
                val edge = (statusTop.toPx() / size.height).coerceIn(0f, 1f)
                val brush = Brush.verticalGradient(
                    0f to color.copy(alpha = SCRIM_ALPHA),
                    edge * 0.6f to color.copy(alpha = SCRIM_ALPHA * 0.8f),
                    edge to color.copy(alpha = SCRIM_ALPHA * 0.45f),
                    1f to color.copy(alpha = 0f),
                )
                onDrawBehind { drawRect(brush, alpha = level) }
            },
    )
}

private const val SCRIM_ALPHA = 0.55f
private const val SCRIM_RESTING = 0.55f
private const val SCRIM_TOP = 0.12f
private const val SCRIM_FADE_MS = 220
private val SCRIM_EXTRA = 28.dp

/** 넓은 화면에서 본문이 지나치게 늘어나지 않도록 쓰는 최대 폭 */
internal val READING_WIDTH = 640.dp

/** 큰 제목 바의 제목 줄 폭. 큰 제목은 안쪽 20dp에서 시작하므로 그만큼 넓혀야 본문 카드와 시작선이 맞는다. */
internal val TITLE_MAX_WIDTH = READING_WIDTH + 40.dp

/**
 * 목록 가로 여백. 넓은 화면에서는 본문이 [READING_WIDTH]를 넘지 않도록 양옆 여백을 늘린다.
 * 기준 폭은 [ReadingContainer]가 알려 준 칸 폭이고, 없으면 창 폭이다.
 */
@Composable
internal fun readingPadding(minimum: Dp = 20.dp): Dp {
    val width = LocalReadingContainerWidth.current ?: LocalConfiguration.current.screenWidthDp.dp
    return maxOf(minimum, (width - READING_WIDTH) / 2)
}

private val LocalReadingContainerWidth = compositionLocalOf<Dp?> { null }

/** 두 칸 화면의 한 칸처럼 창보다 좁은 곳에서 [readingPadding]이 창 폭 대신 이 칸의 폭을 기준으로 여백을 정하게 한다. */
@Composable
internal fun ReadingContainer(content: @Composable () -> Unit) {
    BoxWithConstraints {
        CompositionLocalProvider(LocalReadingContainerWidth provides maxWidth) { content() }
    }
}

/** 내비게이션 바 높이. 스크롤 목록 아래 여백에 더한다. */
@Composable
internal fun navigationBarBottom(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** 제목과 설명이 있는 데모 카드. 시연할 내용은 [content]에 넣는다. */
@Composable
internal fun DemoCard(title: String, description: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    KSurface(modifier.fillMaxWidth(), shape = CARD_SHAPE) {
        // 폭을 채워야 넓은 화면에서 폭을 묶은 내용을 align(CenterHorizontally)로 카드 가운데에 놓을 수 있다.
        Column(Modifier.fillMaxWidth().padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label(title, 18, FontWeight.Bold)
            Label(description, 13, color = LocalKineticaContentColor.current.copy(alpha = 0.7f))
            content()
        }
    }
}

/** 데모 카드 바깥 모서리 */
internal val CARD_SHAPE = RoundedCornerShape(32.dp)

/** 데모 카드 안쪽 여백 */
internal val CARD_PADDING = 20.dp

/** 데모 카드 안에 꽉 차게 넣는 이미지나 상자의 모서리. 바깥 32dp에서 여백 20dp를 뺀 값이라 카드 모서리와 같은 중심으로 굽는다. */
internal val INNER_SHAPE = RoundedCornerShape(12.dp)

/** 캡슐 모양 버튼. [filled]면 배경을 채워 그리고, 누르면 젤리처럼 눌리며 캡슐 모양으로 어두워진다. */
@Composable
internal fun Pill(text: String, modifier: Modifier = Modifier, filled: Boolean = false, onClick: () -> Unit) {
    val ink = LocalKineticaContentColor.current
    if (filled) {
        val onInk = if (ink.isLight()) Color.Black else Color.White
        Box(
            modifier
                .jellyPress()
                .defaultMinSize(minHeight = 40.dp)
                .background(ink, CircleShape)
                .clip(CircleShape)
                .clickable(null, KIndication(CircleShape, onInk), role = Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(text, style = TextStyle(color = onInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        }
    } else {
        KSurface(onClick, modifier.jellyPress().defaultMinSize(minHeight = 40.dp), shape = CircleShape) {
            Box(Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 10.dp)) { Label(text, 14, FontWeight.SemiBold) }
        }
    }
}

/** 쇼케이스 선 아이콘. [size]는 24 격자 아이콘을 그릴 크기다. */
@Composable
internal fun ShowcaseIcon(
    icon: ImageVector,
    size: Dp = 22.dp,
    color: Color = LocalKineticaContentColor.current,
    modifier: Modifier = Modifier,
) {
    Image(rememberVectorPainter(icon), null, modifier.size(size), colorFilter = ColorFilter.tint(color))
}

@Composable
internal fun Label(
    text: String,
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    color: Color = LocalKineticaContentColor.current,
) {
    // 쇼케이스 문구는 한국어라 RTL 화면에서도 문단 방향은 글 내용(왼쪽에서 오른쪽)을 따라야 끝의 마침표, 물음표가 앞으로 가지 않는다.
    // 정렬은 화면 방향의 시작 쪽(RTL이면 오른쪽)에 맞춘다.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BasicText(
        text,
        modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            lineHeight = (size * 1.4f).sp,
            textDirection = TextDirection.Content,
            textAlign = if (rtl) TextAlign.Right else TextAlign.Left,
        ),
    )
}

internal fun Color.isLight(): Boolean = red * 0.3f + green * 0.59f + blue * 0.11f > 0.5f

/**
 * 이 화면이 보이는 동안 상태 표시줄 아이콘을 [darkBackground]면 밝게, 아니면 어둡게 둔다.
 * 화면이 쌓이는 곳에서는 사라지는 화면이 늦게 정리되며 값을 덮을 수 있으므로, 맨 위 화면 기준 값 하나만 넘긴다.
 */
@Composable
internal fun StatusBarIcons(darkBackground: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkBackground
    }
}

/** 화면을 접거나 펴서 액티비티가 다시 만들어져도 고른 색이 남도록 색 값을 그대로 저장한다. */
internal val colorStateSaver = Saver<MutableState<Color>, Long>(
    save = { it.value.value.toLong() },
    restore = { mutableStateOf(Color(it.toULong())) },
)

/** 범위 슬라이더 값을 [시작, 끝] 두 값으로 저장한다. */
internal val rangeStateSaver = Saver<MutableState<ClosedFloatingPointRange<Float>>, FloatArray>(
    save = { floatArrayOf(it.value.start, it.value.endInclusive) },
    restore = { mutableStateOf(it[0]..it[1]) },
)

/**
 * 크기가 정해진 위젯 안에서 글자 크기 배율을 [max]까지만 따른다. 홈 화면 위젯처럼 칸 크기가 고정된 곳에서
 * 큰 글자 설정이 숫자와 문구를 잘라 먹지 않게 한다.
 */
@Composable
internal fun CappedFontScale(max: Float = 1.15f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(max)), content = content)
}
