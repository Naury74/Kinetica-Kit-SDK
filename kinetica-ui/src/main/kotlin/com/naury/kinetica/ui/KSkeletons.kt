// 파일 이름은 대표 컴포저블인 KSkeletonListItem를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** [KSkeletonListItem] 앞쪽 자리 모양. */
public enum class KSkeletonLeading {
    /** 앞쪽 자리가 없다. */
    None,

    /** 원형 아바타 */
    Avatar,

    /** 둥근 사각형 아이콘이나 썸네일 */
    Thumbnail,
}

/**
 * 아바타 자리 스켈레톤. 불러오는 동안 [KAvatar] 대신 둔다.
 *
 * @param size 지름
 * @param shape 모양. 기본값은 원이다.
 * @param style 움직임. [Modifier.skeleton] 참고
 */
@Composable
public fun KSkeletonAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = CircleShape,
    style: KSkeletonStyle = KSkeletonStyle.Shimmer,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    Spacer(modifier.size(size).skeleton(true, shape, blockColor(surface), style))
}

/**
 * 목록 행 스켈레톤. [KListItem]과 같은 높이와 여백으로 앞쪽 아바타나 썸네일, 글 막대 [lines]줄을 그린다.
 * 그래서 [KListSection] 안에서 불러오기 전과 후의 행 높이가 거의 같아 내용이 튀지 않는다. TalkBack은 "불러오는 중"으로 읽는다.
 *
 * ```kotlin
 * if (loading) repeat(6) { KSkeletonListItem() } else people.forEach { KListItem(it.name, …) }
 * ```
 *
 * @param lines 글 줄 수(1..3). 범위를 벗어나면 가까운 끝 값으로 본다.
 * @param trailing `true`면 끝에 짧은 값 막대를 둔다.
 */
@Composable
public fun KSkeletonListItem(
    modifier: Modifier = Modifier,
    leading: KSkeletonLeading = KSkeletonLeading.Avatar,
    lines: Int = 2,
    trailing: Boolean = false,
    style: KSkeletonStyle = KSkeletonStyle.Shimmer,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val count = lines.coerceIn(1, MAX_LINES)
    val color = blockColor(surface)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(
                min = if (count >= MAX_LINES) {
                    ROW_THREE_LINES
                } else if (count == 2) {
                    ROW_TWO_LINES
                } else {
                    ROW_ONE_LINE
                },
            )
            .loadingSemantics()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (leading) {
            KSkeletonLeading.Avatar -> Spacer(Modifier.size(LEADING).skeleton(true, CircleShape, color, style))
            KSkeletonLeading.Thumbnail -> Spacer(Modifier.size(LEADING).skeleton(true, RoundedCornerShape(10.dp), color, style))
            KSkeletonLeading.None -> Unit
        }
        // 막대마다 실제 글 한 줄 높이의 칸을 잡아, 글자 크기를 키워도 불러온 뒤의 행 높이와 같게 한다.
        val density = LocalDensity.current
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TEXT_GAP)) {
            repeat(count) { i ->
                val height = if (i == 0) HEADLINE else SUPPORTING
                val line = with(density) { (if (i == 0) HEADLINE_TEXT else SUPPORTING_TEXT).toDp() } * LINE_BOX
                Box(Modifier.height(line), contentAlignment = Alignment.CenterStart) {
                    Spacer(Modifier.fillMaxWidth(LineWidths[i]).height(height).skeleton(true, RoundedCornerShape(height / 2), color, style))
                }
            }
        }
        if (trailing) Spacer(Modifier.size(TRAILING_WIDTH, SUPPORTING).skeleton(true, RoundedCornerShape(SUPPORTING / 2), color, style))
    }
}

/**
 * 카드 스켈레톤. 표면 스타일 카드 안에 사진 자리(16:9), 제목 막대, 글 [lines]줄, 아래쪽 아바타와 짧은 막대를 그린다.
 * 사진 자리 모서리는 카드 모서리와 동심원으로 깎는다. TalkBack은 "불러오는 중"으로 읽는다.
 *
 * @param media `false`면 사진 자리를 빼고 글만 그린다.
 * @param lines 제목 아래 글 줄 수(0..4). 범위를 벗어나면 가까운 끝 값으로 본다.
 */
@Composable
public fun KSkeletonCard(
    modifier: Modifier = Modifier,
    media: Boolean = true,
    lines: Int = 2,
    style: KSkeletonStyle = KSkeletonStyle.Shimmer,
    surface: SurfaceStyle = KineticaTheme.surface,
) {
    val color = blockColor(surface)
    Column(
        modifier
            .fillMaxWidth()
            .kSurface(surface, RoundedCornerShape(CARD_CORNER))
            .loadingSemantics()
            .padding(CARD_PADDING),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (media) {
            val inner = KMetrics.concentric(CARD_CORNER, CARD_PADDING)
            Spacer(Modifier.fillMaxWidth().aspectRatio(MEDIA_RATIO).skeleton(true, RoundedCornerShape(inner), color, style))
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.fillMaxWidth(TITLE_WIDTH).height(TITLE).skeleton(true, RoundedCornerShape(TITLE / 2), color, style))
        repeat(lines.coerceIn(0, MAX_CARD_LINES)) { i ->
            val width = if (i == lines - 1 && lines > 1) LAST_LINE else 1f
            Spacer(Modifier.fillMaxWidth(width).height(SUPPORTING).skeleton(true, RoundedCornerShape(SUPPORTING / 2), color, style))
        }
        Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.size(FOOTER_AVATAR).skeleton(true, CircleShape, color, style))
            Spacer(Modifier.size(FOOTER_WIDTH, SUPPORTING).skeleton(true, RoundedCornerShape(SUPPORTING / 2), color, style))
        }
    }
}

@Composable
private fun Modifier.loadingSemantics(): Modifier {
    val loading = stringResource(R.string.kinetica_ui_loading)
    return clearAndSetSemantics { contentDescription = loading }
}

private fun blockColor(surface: SurfaceStyle) = surface.contentColor.copy(alpha = BLOCK_ALPHA)

private val LineWidths = floatArrayOf(0.55f, 0.85f, 0.7f)
private const val MAX_LINES = 3
private const val MAX_CARD_LINES = 4
private const val BLOCK_ALPHA = 0.1f
private const val MEDIA_RATIO = 16f / 9f
private const val TITLE_WIDTH = 0.6f
private const val LAST_LINE = 0.7f
private val ROW_ONE_LINE = 48.dp
private val ROW_TWO_LINES = 64.dp
private val ROW_THREE_LINES = 84.dp
private val LEADING = 40.dp
private val HEADLINE = 14.dp
private val SUPPORTING = 11.dp
private val TRAILING_WIDTH = 36.dp
private val TITLE = 18.dp
private val TEXT_GAP = 2.dp
private val HEADLINE_TEXT = 16.sp
private val SUPPORTING_TEXT = 14.sp
private const val LINE_BOX = 1.4f
private val CARD_CORNER = 24.dp
private val CARD_PADDING = 12.dp
private val FOOTER_AVATAR = 28.dp
private val FOOTER_WIDTH = 96.dp
