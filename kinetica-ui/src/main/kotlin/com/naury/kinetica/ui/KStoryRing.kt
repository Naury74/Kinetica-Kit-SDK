// 파일 이름은 대표 API인 KStoryRing을(를) 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** [KStoryRing]의 기본값 */
public object KStoryRingDefaults {
    /** 안 본 스토리 링 색. 노랑에서 주황, 분홍, 보라로 이어지는 그라디언트다. */
    public val Colors: List<Color> = listOf(Color(0xFFFFC75F), Color(0xFFFF7A45), Color(0xFFF7356F), Color(0xFFB436E0), Color(0xFFFFC75F))

    /** 친한 친구처럼 따로 구분할 스토리에 쓰는 초록 그라디언트 */
    public val CloseFriends: List<Color> = listOf(Color(0xFF34C759), Color(0xFF30D5C8), Color(0xFF34C759))
}

/**
 * 스토리가 있는 사람을 표시하는 링. [content](보통 [KAvatar]) 둘레에 [gap]만큼 띄워 링을 두른다.
 * 안 본 스토리는 [colors] 그라디언트 링, 본 스토리([seen])는 가는 회색 링이다. 본 상태로 바뀌면 색이 서서히 빠진다.
 * [loading]이면 링이 점선으로 나뉘어 돈다. 스토리를 여는 동안 표시하고, 화면 밖이거나 '애니메이션 삭제' 설정에서는 돌지 않는다.
 * TalkBack은 내용 이름에 '새 스토리' 또는 '봄'을 상태로 덧붙인다.
 *
 * ```kotlin
 * KStoryRing(seen = story.seen, onClick = { open(story) }) { KAvatar(KAvatarData(story.name), size = KAvatarSize.Large) }
 * ```
 *
 * @param colors 안 본 스토리 링 색. 비어 있으면 [IllegalArgumentException]을 던진다. 하나면 단색 링이다.
 * @param ringWidth 안 본 스토리 링 두께. 본 스토리 링은 이보다 얇다.
 * @param gap 링과 내용 사이 여백. 이 여백은 표면 바탕이 비친다.
 */
@Composable
public fun KStoryRing(
    seen: Boolean,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    colors: List<Color> = KStoryRingDefaults.Colors,
    ringWidth: Dp = 2.5.dp,
    gap: Dp = 2.5.dp,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    surface: SurfaceStyle = KineticaTheme.surface,
    content: @Composable () -> Unit,
) {
    require(colors.isNotEmpty()) { "colors가 비어 있으면 안 됩니다." }
    val still = reducedMotion()
    val seenLevel = animateFloatAsState(if (seen) 1f else 0f, if (still) snap() else tween(SEEN_FADE_MS), label = "story-seen")
    val clock = rememberLoopClock(running = loading && !still)
    val state = stringResource(if (seen) R.string.kinetica_ui_story_seen else R.string.kinetica_ui_story_new)
    val faint = surface.contentColor.copy(alpha = SEEN_ALPHA)
    val gradient = remember(colors) { if (colors.size == 1) listOf(colors[0], colors[0]) else colors }
    Box(
        modifier
            .semantics(mergeDescendants = true) { stateDescription = state }
            .loopVisibility(clock)
            .drawWithCache {
                val brush = Brush.sweepGradient(gradient, size.center)
                val bold = ringWidth.toPx()
                val thin = bold * SEEN_WIDTH
                onDrawBehind {
                    val level = seenLevel.value
                    val width = bold + (thin - bold) * level
                    val inset = width / 2
                    val arcSize = Size(size.width - width, size.height - width)
                    rotate(if (loading) clock.seconds * SPIN_DEG_PER_S else 0f) {
                        val dashes = if (loading) DASHES else 1
                        val sweep = FULL_TURN / dashes
                        val gapDeg = if (loading) DASH_GAP_DEG else 0f
                        val stroke = Stroke(width, cap = if (loading) StrokeCap.Round else StrokeCap.Butt)
                        for (i in 0 until dashes) {
                            val start = -QUARTER_TURN + i * sweep + gapDeg / 2
                            if (level < 1f) {
                                drawArc(brush, start, sweep - gapDeg, false, Offset(inset, inset), arcSize, 1f - level, stroke)
                            }
                            if (level > 0f) drawArc(faint, start, sweep - gapDeg, false, Offset(inset, inset), arcSize, level, stroke)
                        }
                    }
                }
            }
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.kClickable(CircleShape, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(ringWidth + gap),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.clip(CircleShape)) { content() }
    }
}

private const val SEEN_FADE_MS = 360
private const val SEEN_ALPHA = 0.28f
private const val SEEN_WIDTH = 0.55f
private const val SPIN_DEG_PER_S = 120f
private const val DASHES = 12
private const val DASH_GAP_DEG = 9f
private const val FULL_TURN = 360f
private const val QUARTER_TURN = 90f
