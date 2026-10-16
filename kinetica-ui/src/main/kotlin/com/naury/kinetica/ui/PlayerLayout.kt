package com.naury.kinetica.ui

import androidx.compose.ui.geometry.Rect

/** 오버레이 기준 좌표(px)로 옮긴 접힌 선과 자세 */
internal class PlayerFold(val bounds: Rect, val posture: KPosture)

/** 펼친 화면의 아트 자리와 내용 자리. 내용 자리는 펼친 화면([PlayerGeometry.full]) 기준 LTR 좌표(px)다. */
internal class FullPlayerLayout(val art: Rect, val content: Rect)

/**
 * 펼친 플레이어 배치에 쓰는 치수(px). [small]은 막대 안 아트 크기, [handle]은 위쪽 손잡이 영역 높이,
 * [pairGap]은 접힌 선 없이 아트와 내용을 나란히 둘 때 둘 사이 간격이다.
 */
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
    val pairGap: Float,
)

/**
 * 펼친 플레이어의 아트와 내용 자리를 정한다(px, LTR). 세 가지 배치가 있다.
 * - 탁자 모드: 아트는 가로 접힌 선 위 칸 가운데, 내용은 접힌 선 아래 칸.
 * - 넓고 낮은 화면(폭 [PlayerMetrics.wideMin] 이상이고 폭이 높이의 0.9배 이상): 세로 접힌 선이 있으면 아트는 선 앞 칸 가운데,
 *   내용은 선 뒤 칸에 둔다. 접힌 선이 없으면 아트와 내용을 [PlayerMetrics.pairGap] 간격으로 붙인 묶음을 가운데에 둔다.
 * - 그 밖: 아트를 위 가운데, 내용을 그 아래에 두고 내용 폭은 [PlayerMetrics.contentMax]로 묶는다.
 * 아트가 없으면([hasArtwork]가 false) 아트 자리는 비우고 내용이 그 자리까지 쓴다.
 */
internal fun fullPlayerLayout(width: Float, full: Rect, hasArtwork: Boolean, fold: PlayerFold?, m: PlayerMetrics): FullPlayerLayout {
    val tabletop = fold?.takeIf {
        it.posture == KPosture.Tabletop && it.bounds.width > it.bounds.height &&
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
        ?: return pairedPlayerLayout(width, full, m)
    val contentWidth = minOf(width - vertical.right - m.side * 2, m.contentMax)
    val contentLeft = vertical.right + (width - vertical.right - contentWidth) / 2f
    val big = minOf(vertical.left - m.side * 2, height - m.handle - m.side, m.artMax).coerceAtLeast(m.small)
    val top = full.top + m.handle + (height - m.handle - big) / 2f
    val left = (vertical.left - big) / 2f
    // 내용은 아트 윗선에서 시작해 두 칸의 첫 줄이 맞게 한다.
    val content = Rect(contentLeft, top - full.top, contentLeft + contentWidth, height)
    return FullPlayerLayout(Rect(left, top, left + big, top + big), content)
}

/** 접힌 선이 없는 넓은 화면. 절반씩 나누면 아트와 내용 사이가 바깥 여백보다 훨씬 벌어져 묶음이 치우쳐 보이므로 붙여서 가운데에 둔다. */
private fun pairedPlayerLayout(width: Float, full: Rect, m: PlayerMetrics): FullPlayerLayout {
    val height = full.height
    val room = width - m.side * 2
    val big = minOf((room - m.pairGap) / 2f, height - m.handle - m.side, m.artMax).coerceAtLeast(m.small)
    val contentWidth = minOf(room - big - m.pairGap, m.contentMax)
    val left = (width - (big + m.pairGap + contentWidth)) / 2f
    val top = full.top + m.handle + (height - m.handle - big) / 2f
    val contentLeft = left + big + m.pairGap
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
