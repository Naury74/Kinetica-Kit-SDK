package com.naury.kinetica.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 불러오는 동안 이 요소 자리에 회색 블록을 그리고, 그 위로 빛이 비스듬히 흘러간다. [visible]이 false가 되면
 * 블록이 사라지며 실제 내용이 부드럽게 나타난다. 크기는 내용이 정하므로 불러오기 전에도 자리를 잡을 만한 내용이나 크기를 준다.
 *
 * 보이는 동안에는 내용을 TalkBack에서 숨기고 "불러오는 중"으로 읽힌다. 빛은 RTL에서 오른쪽에서 왼쪽으로 흐르며,
 * '애니메이션 삭제' 설정이 켜져 있으면 흐르지 않는다.
 *
 * ```kotlin
 * Text(title, Modifier.skeleton(visible = loading))
 * ```
 *
 * @param shape 블록 모양
 * @param color 블록 색. 기본값은 글래스 텍스트 색을 옅게 쓴 것이다.
 */
@Composable
public fun Modifier.skeleton(
    visible: Boolean,
    shape: Shape = RoundedCornerShape(8.dp),
    color: Color = LocalGlassContentColor.current.copy(alpha = BLOCK_ALPHA),
): Modifier {
    val cover = animateFloatAsState(if (visible) 1f else 0f, tween(FADE_MS), label = "skeleton")
    val still = reducedMotion()
    val clock = rememberLoopClock(running = visible && !still)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val highlight = color.copy(alpha = (color.alpha * HIGHLIGHT_GAIN).coerceAtMost(1f))
    val loading = stringResource(R.string.kinetica_ui_loading)
    val hidden = if (visible) Modifier.clearAndSetSemantics { contentDescription = loading } else Modifier
    return this
        .then(hidden)
        .loopVisibility(clock)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            val band = size.width.coerceAtLeast(size.height) * BAND
            val glow = Brush.linearGradient(
                listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset.Zero,
                end = Offset(band, band * SLANT),
            )
            val fade = Paint()
            onDrawWithContent {
                val c = cover.value
                if (c < 1f) {
                    // 블록과 내용을 겹쳐 그리는 동안 내용은 블록이 걷히는 만큼만 보인다.
                    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), fade.apply { alpha = 1f - c })
                    drawContent()
                    drawContext.canvas.restore()
                }
                if (c <= 0f) return@onDrawWithContent
                drawOutline(outline, color, alpha = c)
                if (still) return@onDrawWithContent
                val travel = (size.width + band * 2) * (clock.seconds * MS_PER_SECOND % SHIMMER_MS / SHIMMER_MS) - band
                clipPath(clip) {
                    translate(left = if (rtl) size.width - travel - band else travel) {
                        drawRect(glow, topLeft = Offset(0f, -band), size = Size(band, size.height + band * 2), alpha = c)
                    }
                }
            }
        }
}

/**
 * 여러 줄 글이 들어갈 자리의 스켈레톤. 줄마다 막대를 그리고 마지막 줄은 짧게 끝난다.
 *
 * @param lines 줄 수. 1 미만이면 [IllegalArgumentException]을 던진다.
 * @param lineHeight 막대 높이
 */
@Composable
public fun GlassSkeletonText(lines: Int, modifier: Modifier = Modifier, lineHeight: Dp = 14.dp) {
    require(lines >= 1) { "lines는 1 이상이어야 합니다." }
    val loading = stringResource(R.string.kinetica_ui_loading)
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(lineHeight * LINE_GAP),
    ) {
        repeat(lines) { i ->
            val width = if (i == lines - 1 && lines > 1) LAST_LINE else 1f
            Spacer(Modifier.fillMaxWidth(width).height(lineHeight).skeleton(true, RoundedCornerShape(lineHeight / 2)))
        }
    }
}

private const val BLOCK_ALPHA = 0.1f
private const val HIGHLIGHT_GAIN = 1.9f
private const val FADE_MS = 280
private const val SHIMMER_MS = 1_300f
private const val MS_PER_SECOND = 1_000f
private const val BAND = 0.6f
private const val SLANT = 0.35f
private const val LINE_GAP = 0.7f
private const val LAST_LINE = 0.6f
