package com.naury.kinetica.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 채팅, 첨부, 페이지 이동 컴포넌트가 쓰는 선 아이콘. [KGlyphs]와 같은 24 격자 규칙을 따른다. */
internal object SocialGlyphs {
    val Plus: ImageVector = glyph("plus") {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }

    val ArrowUp: ImageVector = glyph("arrow-up") {
        moveTo(12f, 19f)
        lineTo(12f, 5.5f)
        moveTo(6f, 11f)
        lineTo(12f, 5f)
        lineTo(18f, 11f)
    }

    val Mic: ImageVector = glyph("mic") {
        moveTo(12f, 3.5f)
        curveTo(13.66f, 3.5f, 15f, 4.84f, 15f, 6.5f)
        lineTo(15f, 11.5f)
        curveTo(15f, 13.16f, 13.66f, 14.5f, 12f, 14.5f)
        curveTo(10.34f, 14.5f, 9f, 13.16f, 9f, 11.5f)
        lineTo(9f, 6.5f)
        curveTo(9f, 4.84f, 10.34f, 3.5f, 12f, 3.5f)
        close()
        moveTo(5.5f, 11f)
        curveTo(5.5f, 14.6f, 8.4f, 17.5f, 12f, 17.5f)
        curveTo(15.6f, 17.5f, 18.5f, 14.6f, 18.5f, 11f)
        moveTo(12f, 17.5f)
        lineTo(12f, 20.5f)
    }

    val File: ImageVector = glyph("file") {
        moveTo(13.5f, 3.5f)
        lineTo(7f, 3.5f)
        curveTo(6.17f, 3.5f, 5.5f, 4.17f, 5.5f, 5f)
        lineTo(5.5f, 19f)
        curveTo(5.5f, 19.83f, 6.17f, 20.5f, 7f, 20.5f)
        lineTo(17f, 20.5f)
        curveTo(17.83f, 20.5f, 18.5f, 19.83f, 18.5f, 19f)
        lineTo(18.5f, 8.5f)
        close()
        moveTo(13.5f, 3.5f)
        lineTo(13.5f, 8.5f)
        lineTo(18.5f, 8.5f)
    }

    val Upload: ImageVector = glyph("upload") {
        moveTo(12f, 15f)
        lineTo(12f, 4.5f)
        moveTo(7.5f, 9f)
        lineTo(12f, 4.5f)
        lineTo(16.5f, 9f)
        moveTo(4.5f, 14.5f)
        lineTo(4.5f, 18f)
        curveTo(4.5f, 18.83f, 5.17f, 19.5f, 6f, 19.5f)
        lineTo(18f, 19.5f)
        curveTo(18.83f, 19.5f, 19.5f, 18.83f, 19.5f, 18f)
        lineTo(19.5f, 14.5f)
    }

    val Retry: ImageVector = glyph("retry") {
        moveTo(19f, 12f)
        arcToRelative(7f, 7f, 0f, true, true, -2.05f, -4.95f)
        moveTo(17.5f, 3.5f)
        lineTo(17.5f, 7.5f)
        lineTo(13.5f, 7.5f)
    }

    val Copy: ImageVector = glyph("copy") {
        moveTo(9f, 8f)
        lineTo(17f, 8f)
        curveTo(18.1f, 8f, 19f, 8.9f, 19f, 10f)
        lineTo(19f, 18f)
        curveTo(19f, 19.1f, 18.1f, 20f, 17f, 20f)
        lineTo(9f, 20f)
        curveTo(7.9f, 20f, 7f, 19.1f, 7f, 18f)
        lineTo(7f, 10f)
        curveTo(7f, 8.9f, 7.9f, 8f, 9f, 8f)
        close()
        moveTo(16f, 5f)
        curveTo(16f, 4.45f, 15.55f, 4f, 15f, 4f)
        lineTo(6f, 4f)
        curveTo(4.9f, 4f, 4f, 4.9f, 4f, 6f)
        lineTo(4f, 15f)
        curveTo(4f, 15.55f, 4.45f, 16f, 5f, 16f)
    }

    val ChevronBack: ImageVector = glyph("chevron-back", mirrored = true) {
        moveTo(15f, 5.5f)
        lineTo(8.5f, 12f)
        lineTo(15f, 18.5f)
    }

    private fun glyph(name: String, mirrored: Boolean = false, block: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
        name = "kinetica-social-$name",
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
