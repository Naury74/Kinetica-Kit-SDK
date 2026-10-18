// 파일 이름은 대표 컴포저블인 KProfileHeader를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [KProfileHeader]의 숫자 칸 하나.
 *
 * @property value 숫자 글자. 예: "1.2만"
 * @property label 이름. 예: "팔로워"
 * @property onClick 눌렀을 때. `null`이면 누를 수 없다.
 */
@Immutable
public data class KProfileStat(val value: String, val label: String, val onClick: (() -> Unit)? = null)

/**
 * SNS와 마이페이지의 프로필 머리. 커버 사진 위에 아바타를 반쯤 겹쳐 놓고, 이름, 설명, 숫자 칸(게시물, 팔로워 등), 버튼 줄을 차례로 놓는다.
 * 판은 [surface]로 그리고 커버는 판 모서리에 맞춰 잘린다. 아바타 둘레에는 판 색 테두리를 둘러 커버와 구분한다.
 *
 * [collapse]에 스크롤 진행(0..1)을 넘기면 커버가 절반 속도로 밀려 올라가고 아바타가 작아지며 옅어진다. 값은 그리기 단계에서 읽어
 * 스크롤 중에 다시 구성하지 않는다. 예: `collapse = { (listState.firstVisibleItemScrollOffset / 400f).coerceIn(0f, 1f) }`.
 *
 * TalkBack은 이름을 제목으로, 숫자 칸을 '팔로워 1.2만'처럼 한 덩어리로 읽는다.
 *
 * ```kotlin
 * KProfileHeader("김민지", avatar = KAvatarData("김민지"), subtitle = "@minji · 서울", stats = listOf(KProfileStat("128", "게시물")),
 *     actions = { KButton("팔로우", ::follow, Modifier.weight(1f)) })
 * ```
 *
 * @param cover 커버 내용. `null`이면 강조색 그라디언트를 깐다.
 * @param coverHeight 커버 높이
 * @param stats 숫자 칸. 비어 있으면 줄을 숨긴다.
 * @param actions 아래 버튼 줄. `Modifier.weight(1f)`로 나눠 쓴다.
 */
@Suppress("LongMethod") // 디자인 언어 토큰(모양, 밀도, 굵기)을 읽는 줄이 더해졌을 뿐 한 덩어리 배치라 나누면 오히려 읽기 어렵다.
@Composable
public fun KProfileHeader(
    name: String,
    modifier: Modifier = Modifier,
    avatar: KAvatarData? = null,
    subtitle: String? = null,
    cover: (@Composable BoxScope.() -> Unit)? = null,
    coverHeight: Dp = 132.dp,
    stats: List<KProfileStat> = emptyList(),
    actions: (@Composable RowScope.() -> Unit)? = null,
    collapse: () -> Float = { 0f },
    shape: Shape = shapeFor { card },
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val ink = surface.contentColor
    val accent = KineticaTheme.colors.accent
    val shapes = currentShapes
    // 테두리 고리가 아바타와 같은 중심으로 굽도록 반지름에 고리 두께를 더한다.
    val ringShape = shapes.radius(shapes.avatar).let { r -> shapeOf(shapes.cornerOf(shapes.avatar), if (r == Dp.Infinity) r else r + RING) }
    Column(modifier.kSurface(surface, shape).clip(shape)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(coverHeight)
                .clipToBounds()
                .graphicsLayer { translationY = -size.height * collapse().coerceIn(0f, 1f) * PARALLAX },
        ) {
            if (cover != null) {
                cover()
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent, accent.copy(alpha = COVER_FADE)))))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (avatar != null) {
                Box(
                    Modifier
                        .pullUp(AVATAR / 2 + RING)
                        .graphicsLayer {
                            val t = collapse().coerceIn(0f, 1f)
                            val scale = 1f - (1f - AVATAR_MIN_SCALE) * t
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - t * AVATAR_FADE
                            transformOrigin = TransformOrigin(CENTER, 1f)
                        }.size(AVATAR + RING * 2)
                        .background(surface.ringColor(), ringShape)
                        .padding(RING),
                ) { KAvatar(avatar, Modifier.fillMaxSize(), size = KAvatarSize.ExtraLarge, surface = surface) }
            }
            Column(
                Modifier.padding(top = if (avatar != null) 8.dp else 16.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ThemedText(
                    name,
                    Modifier.semantics { heading() },
                    style = TextStyle(
                        color = ink,
                        fontSize = 22.sp,
                        fontWeight = KineticaTheme.design.title(FontWeight.SemiBold),
                        textAlign = TextAlign.Center,
                    ),
                )
                subtitle?.let {
                    ThemedText(it, style = TextStyle(color = surface.muted(MUTED), fontSize = 14.sp, textAlign = TextAlign.Center))
                }
                if (stats.isNotEmpty()) ProfileStats(stats, surface, Modifier.padding(top = 12.dp))
                if (actions != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        content = actions,
                    )
                }
            }
        }
    }
}

/** 요소를 [by]만큼 위로 끌어 올리고 그만큼 차지하는 높이도 줄인다. 아바타가 커버에 겹치되 아래 글자가 뒤따라 올라오게 한다. */
private fun Modifier.pullUp(by: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - shift) { placeable.place(0, -shift) }
}

@Composable
private fun ProfileStats(stats: List<KProfileStat>, surface: SurfaceStyle, modifier: Modifier) {
    val ink = surface.contentColor
    val shape = roundedShapeFor(STAT_RADIUS) { button }
    val weight = KineticaTheme.design.label(FontWeight.SemiBold)
    Row(modifier.fillMaxWidth()) {
        stats.forEach { stat ->
            val description = "${stat.label} ${stat.value}"
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .then(stat.onClick?.let { Modifier.kClickable(shape, onClickLabel = stat.label, onClick = it) } ?: Modifier)
                    .heightIn(min = KMetrics.MinTouch)
                    .padding(vertical = 6.dp)
                    .clearAndSetSemantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ThemedText(stat.value, style = TextStyle(color = ink, fontSize = 18.sp, fontWeight = weight), maxLines = 1)
                ThemedText(
                    stat.label,
                    style = TextStyle(color = surface.muted(MUTED), fontSize = 12.sp, textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val MUTED = 0.64f
private const val PARALLAX = 0.5f
private const val AVATAR_MIN_SCALE = 0.6f
private const val AVATAR_FADE = 0.7f
private const val CENTER = 0.5f
private const val COVER_FADE = 0.55f
private val AVATAR = 84.dp
private val RING = 4.dp
private val STAT_RADIUS = 12.dp
