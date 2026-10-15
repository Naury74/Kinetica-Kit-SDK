package com.naury.kinetica.showcase

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 쇼케이스에서 쓰는 선 아이콘(24 격자). 아이콘 라이브러리 없이 그린다. */
internal object ShowcaseIcons {
    val Home =
        line {
            moveTo(4f, 11f)
            lineTo(12f, 4f)
            lineTo(20f, 11f)
            moveTo(6f, 9.5f)
            lineTo(6f, 20f)
            lineTo(18f, 20f)
            lineTo(18f, 9.5f)
        }
    val Sparkle = line {
        moveTo(12f, 3f)
        lineTo(13.8f, 10.2f)
        lineTo(21f, 12f)
        lineTo(13.8f, 13.8f)
        lineTo(12f, 21f)
        lineTo(10.2f, 13.8f)
        lineTo(3f, 12f)
        lineTo(10.2f, 10.2f)
        close()
    }
    val Grid = line {
        moveTo(4f, 4f)
        lineTo(10f, 4f)
        lineTo(10f, 10f)
        lineTo(4f, 10f)
        close()
        moveTo(14f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 10f)
        lineTo(14f, 10f)
        close()
        moveTo(4f, 14f)
        lineTo(10f, 14f)
        lineTo(10f, 20f)
        lineTo(4f, 20f)
        close()
        moveTo(14f, 14f)
        lineTo(20f, 14f)
        lineTo(20f, 20f)
        lineTo(14f, 20f)
        close()
    }
    val Cloud = line {
        moveTo(7f, 18f)
        lineTo(17f, 18f)
        curveTo(20f, 18f, 21f, 15f, 19.5f, 13f)
        curveTo(19f, 10f, 15f, 8.5f, 13f, 10f)
        curveTo(11.5f, 6.5f, 6f, 7f, 6f, 11.5f)
        curveTo(3f, 12f, 3f, 18f, 7f, 18f)
        close()
    }
    val Pen =
        line {
            moveTo(4f, 20f)
            lineTo(5f, 15f)
            lineTo(16f, 4f)
            lineTo(20f, 8f)
            lineTo(9f, 19f)
            close()
            moveTo(13.5f, 6.5f)
            lineTo(17.5f, 10.5f)
        }
    val Bell = line {
        moveTo(6f, 16f)
        lineTo(6f, 11f)
        curveTo(6f, 7f, 9f, 5f, 12f, 5f)
        curveTo(15f, 5f, 18f, 7f, 18f, 11f)
        lineTo(18f, 16f)
        lineTo(20f, 18f)
        lineTo(4f, 18f)
        close()
        moveTo(10f, 21f)
        lineTo(14f, 21f)
    }
    val Plus = line {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }

    private fun line(block: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    ).build()
}
