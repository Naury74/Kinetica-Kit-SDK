package com.naury.kinetica.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 수량 스테퍼, 키패드, 기간 다이얼로그가 쓰는 선 아이콘. [KGlyphs]와 같은 24 격자, 같은 굵기다. */
internal object KDataGlyphs {
    val Plus: ImageVector = glyph("plus") {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }

    val Minus: ImageVector = glyph("minus") {
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }

    val Trash: ImageVector = glyph("trash") {
        moveTo(4.5f, 6.5f)
        lineTo(19.5f, 6.5f)
        moveTo(9.5f, 6.5f)
        lineTo(9.5f, 4.5f)
        lineTo(14.5f, 4.5f)
        lineTo(14.5f, 6.5f)
        moveTo(6.5f, 6.5f)
        lineTo(7.5f, 19.5f)
        lineTo(16.5f, 19.5f)
        lineTo(17.5f, 6.5f)
        moveTo(10.5f, 10.5f)
        lineTo(10.5f, 16f)
        moveTo(13.5f, 10.5f)
        lineTo(13.5f, 16f)
    }

    // 지우기 키는 글자를 지우는 방향(앞쪽)을 가리키므로 RTL에서 뒤집는다.
    val Backspace: ImageVector = glyph("backspace", mirrored = true) {
        moveTo(8.5f, 5.5f)
        lineTo(20f, 5.5f)
        lineTo(20f, 18.5f)
        lineTo(8.5f, 18.5f)
        lineTo(3f, 12f)
        close()
        moveTo(11.5f, 9.5f)
        lineTo(16.5f, 14.5f)
        moveTo(16.5f, 9.5f)
        lineTo(11.5f, 14.5f)
    }

    val Calendar: ImageVector = glyph("calendar") {
        moveTo(5f, 6.5f)
        lineTo(19f, 6.5f)
        lineTo(19f, 19.5f)
        lineTo(5f, 19.5f)
        close()
        moveTo(5f, 10.5f)
        lineTo(19f, 10.5f)
        moveTo(9f, 4f)
        lineTo(9f, 8f)
        moveTo(15f, 4f)
        lineTo(15f, 8f)
    }

    private fun glyph(name: String, mirrored: Boolean = false, block: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
        name = "kinetica-data-$name",
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
