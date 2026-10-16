// 파일 이름은 대표 컴포저블을 따른다. 함께 쓰는 데이터 클래스/상태 클래스가 하나뿐이라 detekt가 그 이름을 요구한다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * [AvatarStack]에 넣을 사람 한 명.
 *
 * @property name 이름. TalkBack이 읽고, [image]가 없으면 앞 글자로 이니셜을 만든다.
 * @property color 이니셜 바탕색. [Color.Unspecified]면 이름에 따라 정해진 색을 고른다.
 * @property image 사진처럼 원 안에 대신 그릴 콘텐츠. 원 모양으로 잘린다.
 */
@Immutable
public data class KAvatar(val name: String, val color: Color = Color.Unspecified, val image: (@Composable () -> Unit)? = null)

/**
 * 원형 아바타를 겹쳐 늘어놓는다. [max]명을 넘으면 마지막에 "+3"처럼 남은 사람 수를 원으로 보여 준다.
 * 처음 나타날 때 앞에서부터 차례로 미끄러져 들어온다. RTL에서는 오른쪽부터 쌓인다.
 * TalkBack은 보이는 사람들의 이름과 남은 인원을 한 번에 읽는다.
 *
 * ```kotlin
 * AvatarStack(listOf(KAvatar("Ana"), KAvatar("Ben"), KAvatar("김민수")), max = 3)
 * ```
 *
 * @param max 원으로 보일 최대 인원. 1 미만이면 [IllegalArgumentException]을 던진다.
 * @param size 원 하나의 지름. 이웃한 원과 지름의 25%만큼 겹치고, 앞사람이 위에 놓인다.
 * @param ringColor 원 둘레에 둘러 이웃 원과 구분하는 테두리 색. 바탕색과 맞추면 깔끔하다.
 */
@Composable
public fun AvatarStack(
    avatars: List<KAvatar>,
    modifier: Modifier = Modifier,
    max: Int = 4,
    size: Dp = 36.dp,
    ringColor: Color = Color.White,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    require(max >= 1) { "max는 1 이상이어야 합니다." }
    val shown = avatars.take(max)
    val extra = avatars.size - shown.size
    val appear = rememberAppear(shown.size + if (extra > 0) 1 else 0)
    val names = shown.joinToString(", ") { it.name }
    val description = if (extra > 0) pluralStringResource(R.plurals.kinetica_ui_avatar_more, extra, names, extra) else names
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    val travel = with(LocalDensity.current) { size.toPx() * SLIDE }
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(-size * OVERLAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val circle = Modifier
            .size(size)
            .background(ringColor, CircleShape)
            .padding(size * RING)
            .clip(CircleShape)
        shown.forEachIndexed { i, avatar ->
            Box(
                Modifier.zIndex(
                    (shown.size - i).toFloat(),
                ).appearing(appear[i], sign * travel).then(circle).background(avatar.backgroundColor()),
                contentAlignment = Alignment.Center,
            ) {
                if (avatar.image != null) {
                    avatar.image.invoke()
                } else {
                    Initials(initialsOf(avatar.name), size, Color.White)
                }
            }
        }
        if (extra > 0) {
            Box(
                Modifier.appearing(appear[shown.size], sign * travel).then(circle).kSurface(surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // RTL에서도 "+3" 순서가 뒤집히지 않도록 LTR로 격리한다.
                Initials("$LTR_ISOLATE+$extra$POP_ISOLATE", size, surface.contentColor)
            }
        }
    }
}

@Composable
private fun Initials(text: String, size: Dp, color: Color) {
    // 원 크기가 고정이라 글꼴 배율을 따르지 않는다. 이름은 TalkBack이 따로 읽는다.
    val fontSize = with(LocalDensity.current) {
        (
            size * if (text.trim(LTR_ISOLATE, POP_ISOLATE).length >
                1
            ) {
                SMALL_TEXT
            } else {
                TEXT
            }
            ).toPx().toSp()
    }
    ThemedText(text, style = TextStyle(color = color, fontSize = fontSize, fontWeight = FontWeight.SemiBold), maxLines = 1)
}

private fun Modifier.appearing(level: Animatable<Float, *>, offset: Float): Modifier = graphicsLayer {
    val v = level.value
    alpha = v.coerceIn(0f, 1f)
    translationX = offset * (1f - v)
}

/** 아바타마다 등장 진행(0..1). 개수가 늘면 새로 들어온 것만 차례로 등장한다. */
@Composable
private fun rememberAppear(count: Int): List<Animatable<Float, *>> {
    val levels = remember { AppearLevels() }.ensure(count)
    val still = reducedMotion()
    LaunchedEffect(count, still) {
        var order = 0
        levels.take(count).forEach { level ->
            if (level.targetValue >= 1f) return@forEach
            if (still) {
                level.snapTo(1f)
                return@forEach
            }
            val wait = STAGGER_MS * order++
            launch {
                delay(wait)
                level.animateTo(1f, spring(dampingRatio = APPEAR_DAMPING, stiffness = APPEAR_STIFFNESS))
            }
        }
    }
    return levels
}

private class AppearLevels {
    private val levels = mutableListOf<Animatable<Float, *>>()

    fun ensure(count: Int): List<Animatable<Float, *>> {
        while (levels.size < count) levels += Animatable(0f)
        return levels
    }
}

internal fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> firstGlyph(words[0])
        else -> firstGlyph(words[0]) + firstGlyph(words[1])
    }.uppercase()
}

private fun firstGlyph(word: String): String = word.substring(0, word.offsetByCodePoints(0, 1))

private fun KAvatar.backgroundColor(): Color =
    if (color != Color.Unspecified) color else AVATAR_COLORS[name.hashCode().absoluteValue % AVATAR_COLORS.size]

private const val LTR_ISOLATE = '\u2066'
private const val POP_ISOLATE = '\u2069'
private const val OVERLAP = 0.25f
private const val RING = 0.06f
private const val SLIDE = 0.6f
private const val TEXT = 0.4f
private const val SMALL_TEXT = 0.35f
private const val STAGGER_MS = 60L
private const val APPEAR_DAMPING = 0.7f
private const val APPEAR_STIFFNESS = 380f
private val AVATAR_COLORS = listOf(
    Color(0xFF5E5CE6),
    Color(0xFFFF9F0A),
    Color(0xFF30B0C7),
    Color(0xFFFF375F),
    Color(0xFF34C759),
    Color(0xFFAF52DE),
)
