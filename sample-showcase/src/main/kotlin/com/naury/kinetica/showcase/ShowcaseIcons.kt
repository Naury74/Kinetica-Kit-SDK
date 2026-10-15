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

    /** 뒤로 가기 꺾쇠. RTL에서는 좌우가 뒤집힌다. */
    val Back = line(mirrored = true) {
        moveTo(15f, 5f)
        lineTo(8f, 12f)
        lineTo(15f, 19f)
    }

    /** 목록 끝 꺾쇠. RTL에서는 좌우가 뒤집힌다. */
    val Chevron = line(mirrored = true) {
        moveTo(9f, 6f)
        lineTo(15f, 12f)
        lineTo(9f, 18f)
    }
    val Close = line {
        moveTo(6f, 6f)
        lineTo(18f, 18f)
        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }

    /** 물결. 모션 데모 아이콘으로 쓴다. */
    val Wave = line {
        moveTo(3f, 12f)
        curveTo(5.5f, 7f, 8.5f, 7f, 10.5f, 12f)
        curveTo(12.5f, 17f, 15.5f, 17f, 18f, 12f)
        lineTo(21f, 12f)
    }

    /** 겹친 카드. 컴포넌트 데모 아이콘으로 쓴다. */
    val Layers = line {
        moveTo(12f, 4f)
        lineTo(21f, 9f)
        lineTo(12f, 14f)
        lineTo(3f, 9f)
        close()
        moveTo(3f, 14f)
        lineTo(12f, 19f)
        lineTo(21f, 14f)
    }

    /** 앞으로 나가는 화살표. 화면 이동 데모 아이콘으로 쓴다. */
    val Forward = line(mirrored = true) {
        moveTo(4f, 12f)
        lineTo(19f, 12f)
        moveTo(13f, 6f)
        lineTo(19f, 12f)
        lineTo(13f, 18f)
    }

    /** 두 장이 겹쳐 커지는 모양. 장면 전환 데모 아이콘으로 쓴다. */
    val Expand = line {
        moveTo(4f, 9f)
        lineTo(4f, 4f)
        lineTo(9f, 4f)
        moveTo(15f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 9f)
        moveTo(20f, 15f)
        lineTo(20f, 20f)
        lineTo(15f, 20f)
        moveTo(9f, 20f)
        lineTo(4f, 20f)
        lineTo(4f, 15f)
    }

    /** 휴대폰 화면. 앱 화면 예 데모 아이콘으로 쓴다. */
    val Phone = line {
        moveTo(8f, 3f)
        lineTo(16f, 3f)
        curveTo(17.1f, 3f, 18f, 3.9f, 18f, 5f)
        lineTo(18f, 19f)
        curveTo(18f, 20.1f, 17.1f, 21f, 16f, 21f)
        lineTo(8f, 21f)
        curveTo(6.9f, 21f, 6f, 20.1f, 6f, 19f)
        lineTo(6f, 5f)
        curveTo(6f, 3.9f, 6.9f, 3f, 8f, 3f)
        close()
        moveTo(11f, 18f)
        lineTo(13f, 18f)
    }
    val Message = line {
        moveTo(4f, 6f)
        curveTo(4f, 4.9f, 4.9f, 4f, 6f, 4f)
        lineTo(18f, 4f)
        curveTo(19.1f, 4f, 20f, 4.9f, 20f, 6f)
        lineTo(20f, 15f)
        curveTo(20f, 16.1f, 19.1f, 17f, 18f, 17f)
        lineTo(9f, 17f)
        lineTo(5f, 20.5f)
        lineTo(5f, 17f)
        curveTo(4.4f, 16.7f, 4f, 16f, 4f, 15f)
        close()
    }
    val Mail = line {
        moveTo(3.5f, 6f)
        lineTo(20.5f, 6f)
        lineTo(20.5f, 18f)
        lineTo(3.5f, 18f)
        close()
        moveTo(4f, 7f)
        lineTo(12f, 13f)
        lineTo(20f, 7f)
    }
    val Link = line {
        moveTo(10f, 14f)
        lineTo(14f, 10f)
        moveTo(11f, 7f)
        lineTo(13f, 5f)
        curveTo(15f, 3f, 19f, 5f, 19f, 5f)
        curveTo(19f, 5f, 21f, 9f, 19f, 11f)
        lineTo(17f, 13f)
        moveTo(13f, 17f)
        lineTo(11f, 19f)
        curveTo(9f, 21f, 5f, 19f, 5f, 19f)
        curveTo(5f, 19f, 3f, 15f, 5f, 13f)
        lineTo(7f, 11f)
    }
    val Download = line {
        moveTo(12f, 4f)
        lineTo(12f, 15f)
        moveTo(7f, 10f)
        lineTo(12f, 15f)
        lineTo(17f, 10f)
        moveTo(5f, 20f)
        lineTo(19f, 20f)
    }
    val Inbox = line {
        moveTo(4f, 13f)
        lineTo(4f, 19f)
        lineTo(20f, 19f)
        lineTo(20f, 13f)
        lineTo(17f, 5f)
        lineTo(7f, 5f)
        close()
        moveTo(4f, 13f)
        lineTo(9f, 13f)
        lineTo(10f, 15.5f)
        lineTo(14f, 15.5f)
        lineTo(15f, 13f)
        lineTo(20f, 13f)
    }
    val Send = line(mirrored = true) {
        moveTo(4f, 12f)
        lineTo(20f, 4f)
        lineTo(14f, 20f)
        lineTo(11f, 13f)
        close()
        moveTo(11f, 13f)
        lineTo(20f, 4f)
    }
    val Archive = line {
        moveTo(4f, 5f)
        lineTo(20f, 5f)
        lineTo(20f, 9f)
        lineTo(4f, 9f)
        close()
        moveTo(5.5f, 9f)
        lineTo(5.5f, 19f)
        lineTo(18.5f, 19f)
        lineTo(18.5f, 9f)
        moveTo(10f, 13f)
        lineTo(14f, 13f)
    }
    val Trash = line {
        moveTo(4f, 7f)
        lineTo(20f, 7f)
        moveTo(9f, 7f)
        lineTo(9f, 4.5f)
        lineTo(15f, 4.5f)
        lineTo(15f, 7f)
        moveTo(6f, 7f)
        lineTo(7f, 20f)
        lineTo(17f, 20f)
        lineTo(18f, 7f)
    }
    val Location = line {
        moveTo(12f, 21f)
        curveTo(12f, 21f, 5f, 14.5f, 5f, 9.5f)
        curveTo(5f, 5.5f, 8f, 3f, 12f, 3f)
        curveTo(16f, 3f, 19f, 5.5f, 19f, 9.5f)
        curveTo(19f, 14.5f, 12f, 21f, 12f, 21f)
        close()
        moveTo(12f, 7.5f)
        curveTo(13.1f, 7.5f, 14f, 8.4f, 14f, 9.5f)
        curveTo(14f, 10.6f, 13.1f, 11.5f, 12f, 11.5f)
        curveTo(10.9f, 11.5f, 10f, 10.6f, 10f, 9.5f)
        curveTo(10f, 8.4f, 10.9f, 7.5f, 12f, 7.5f)
        close()
    }

    private fun line(mirrored: Boolean = false, block: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = mirrored,
    ).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    ).build()
}
