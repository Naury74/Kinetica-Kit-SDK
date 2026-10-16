package com.naury.kinetica.ui

import androidx.compose.ui.geometry.Rect

/** 오버레이 기준 좌표(px)로 옮긴 접힌 선과 자세 */
internal class PlayerFold(val bounds: Rect, val posture: GlassPosture)

/** 펼친 화면의 아트 자리와 내용 자리. 내용 자리는 펼친 화면([PlayerGeometry.full]) 기준 LTR 좌표(px)다. */
internal class FullPlayerLayout(val art: Rect, val content: Rect)

/** 펼친 플레이어 배치에 쓰는 치수(px). [small]은 막대 안 아트 크기, [handle]은 위쪽 손잡이 영역 높이다. */
@Suppress("LongParameterList") // 치수만 담는 값 묶음이다.
internal class PlayerMetrics(
    val small: Float,
    val handle: Float,
    val gap: Float,
    val side: Float,
    val artMax: Float,
    val contentMax: Float,
    val wideMin: Float,
    val minPane: Float,
)

/**
 * 펼친 플레이어의 아트와 내용 자리를 정한다(px, LTR). 세 가지 배치가 있다.
 * - 탁자 모드: 아트는 가로 접힌 선 위 칸 가운데, 내용은 접힌 선 아래 칸.
 * - 넓고 낮은 화면(폭 [PlayerMetrics.wideMin] 이상이고 폭이 높이의 0.9배 이상): 아트는 시작 쪽 절반, 내용은 끝 쪽 절반. 세로 접힌 선이 있으면 그 선으로 나눈다.
 * - 그 밖: 아트를 위 가운데, 내용을 그 아래에 두고 내용 폭은 [PlayerMetrics.contentMax]로 묶는다.
 * 아트가 없으면([hasArtwork]가 false) 아트 자리는 비우고 내용이 그 자리까지 쓴다.
 */
internal fun fullPlayerLayout(width: Float, full: Rect, hasArtwork: Boolean, fold: PlayerFold?, m: PlayerMetrics): FullPlayerLayout {
    val tabletop = fold?.takeIf {
        it.posture == GlassPosture.Tabletop && it.bounds.width > it.bounds.height &&
            it.bounds.top - full.top >= m.minPane && full.bottom - it.bounds.bottom >= m.minPane
    }
    val wide = hasArtwork && width >= m.wideMin && width >= full.height * WIDE_RATIO
    return when {
        tabletop != null -> tabletopPlayerLayout(width, full, hasArtwork, tabletop.bounds, m)
        wide -> widePlayerLayout(width, full, fold, m)
        else -> stackedPlayerLayout(width, full, hasArtwork, m)
    }
}

private fun tabletopPlayerLayout(width: Float, full: Rect, hasArtwork: Boolean, hinge: Rect, m: PlayerMetrics): FullPlayerLayout {
    val hingeTop = hinge.top - full.top
    val contentLeft = maxOf(0f, (width - m.contentMax) / 2f)
    if (!hasArtwork) return FullPlayerLayout(Rect.Zero, Rect(contentLeft, m.handle, width - contentLeft, full.height))
    val content = Rect(contentLeft, hinge.bottom - full.top + m.gap / 2f, width - contentLeft, full.height)
    val room = hingeTop - m.handle - m.gap / 2f
    val big = minOf(width - m.side * 2, room, m.artMax).coerceAtLeast(m.small)
    val top = full.top + m.handle + (room - big) / 2f
    return FullPlayerLayout(Rect((width - big) / 2f, top, (width + big) / 2f, top + big), content)
}

private fun widePlayerLayout(width: Float, full: Rect, fold: PlayerFold?, m: PlayerMetrics): FullPlayerLayout {
    val height = full.height
    val vertical = fold?.bounds?.takeIf { it.height > it.width && it.left >= m.minPane && width - it.right >= m.minPane }
    val splitStart = vertical?.left ?: (width / 2f)
    val splitEnd = vertical?.right ?: (width / 2f)
    val contentWidth = minOf(width - splitEnd - m.side * 2, m.contentMax)
    val contentLeft = splitEnd + (width - splitEnd - contentWidth) / 2f
    val big = minOf(splitStart - m.side * 2, height - m.handle - m.side, m.artMax).coerceAtLeast(m.small)
    val top = full.top + m.handle + (height - m.handle - big) / 2f
    val left = (splitStart - big) / 2f
    // 내용은 아트 윗선에서 시작해 두 칸의 첫 줄이 맞게 한다.
    val content = Rect(contentLeft, top - full.top, contentLeft + contentWidth, height)
    return FullPlayerLayout(Rect(left, top, left + big, top + big), content)
}

private fun stackedPlayerLayout(width: Float, full: Rect, hasArtwork: Boolean, m: PlayerMetrics): FullPlayerLayout {
    val height = full.height
    val contentLeft = maxOf(0f, (width - m.contentMax) / 2f)
    if (!hasArtwork) return FullPlayerLayout(Rect.Zero, Rect(contentLeft, m.handle, width - contentLeft, height))
    val big = minOf(width - m.side * 2, height * ART_MAX_HEIGHT, m.artMax).coerceAtLeast(m.small)
    val artTop = full.top + m.handle
    val art = Rect((width - big) / 2f, artTop, (width + big) / 2f, artTop + big)
    return FullPlayerLayout(art, Rect(contentLeft, m.handle + big + m.gap, width - contentLeft, height))
}

private const val ART_MAX_HEIGHT = 0.46f
private const val WIDE_RATIO = 0.9f
