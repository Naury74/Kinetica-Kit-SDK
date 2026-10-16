package com.naury.kinetica.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 메뉴, 앱 바, 검색 화면이 쓰는 선 아이콘. 아이콘 라이브러리에 기대지 않으려고 24 격자에 직접 그린다.
 * 방향이 있는 아이콘은 autoMirror라서 RTL에서 저절로 뒤집힌다. 색은 쓰는 쪽에서 ColorFilter로 입힌다.
 */
internal object KGlyphs {
    val Check: ImageVector = glyph("check") {
        moveTo(5f, 12.5f)
        lineTo(9.5f, 17f)
        lineTo(19f, 7f)
    }

    val ChevronDown: ImageVector = glyph("chevron-down") {
        moveTo(6f, 9f)
        lineTo(12f, 15f)
        lineTo(18f, 9f)
    }

    val ChevronForward: ImageVector = glyph("chevron-forward", mirrored = true) {
        moveTo(9f, 5.5f)
        lineTo(15.5f, 12f)
        lineTo(9f, 18.5f)
    }

    val ArrowBack: ImageVector = glyph("arrow-back", mirrored = true) {
        moveTo(19.5f, 12f)
        lineTo(4.5f, 12f)
        moveTo(11f, 5.5f)
        lineTo(4.5f, 12f)
        lineTo(11f, 18.5f)
    }

    val Close: ImageVector = glyph("close") {
        moveTo(6.5f, 6.5f)
        lineTo(17.5f, 17.5f)
        moveTo(17.5f, 6.5f)
        lineTo(6.5f, 17.5f)
    }

    val Clock: ImageVector = glyph("clock") {
        moveTo(12f, 3.5f)
        arcToRelative(8.5f, 8.5f, 0f, true, true, 0f, 17f)
        arcToRelative(8.5f, 8.5f, 0f, true, true, 0f, -17f)
        moveTo(12f, 7.5f)
        lineTo(12f, 12f)
        lineTo(15f, 14f)
    }

    val Search: ImageVector = glyph("search") {
        moveTo(10.5f, 4f)
        arcToRelative(6.5f, 6.5f, 0f, true, true, 0f, 13f)
        arcToRelative(6.5f, 6.5f, 0f, true, true, 0f, -13f)
        moveTo(15.2f, 15.2f)
        lineTo(20f, 20f)
    }

    /** 제안을 검색어 칸에 채워 넣는 왼쪽 위 화살표 */
    val ArrowInsert: ImageVector = glyph("arrow-insert", mirrored = true) {
        moveTo(18f, 18f)
        lineTo(7f, 7f)
        moveTo(7f, 15f)
        lineTo(7f, 7f)
        lineTo(15f, 7f)
    }

    private fun glyph(name: String, mirrored: Boolean = false, block: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
        name = "kinetica-$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = mirrored,
    ).path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    ).build()

    private const val STROKE = 2f
}
