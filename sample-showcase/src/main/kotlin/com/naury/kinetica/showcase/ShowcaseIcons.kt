package com.naury.kinetica.showcase

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 쇼케이스 데모 아이콘(24 격자). 일반 아이콘은 키트의 KIcons를 쓰고, 여기에는 데모 소개용 그림만 둔다. */
internal object ShowcaseIcons {
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
    val Cloud = line {
        moveTo(7f, 18f)
        lineTo(17f, 18f)
        curveTo(20f, 18f, 21f, 15f, 19.5f, 13f)
        curveTo(19f, 10f, 15f, 8.5f, 13f, 10f)
        curveTo(11.5f, 6.5f, 6f, 7f, 6f, 11.5f)
        curveTo(3f, 12f, 3f, 18f, 7f, 18f)
        close()
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

    /** 반은 비고 반은 줄무늬인 원. 표면 스타일 고르기 버튼에 쓴다. */
    val Contrast = line {
        moveTo(12f, 3f)
        arcTo(9f, 9f, 0f, false, true, 12f, 21f)
        arcTo(9f, 9f, 0f, false, true, 12f, 3f)
        close()
        moveTo(12f, 3f)
        lineTo(12f, 21f)
        moveTo(12f, 7.5f)
        lineTo(19.2f, 7.5f)
        moveTo(12f, 12f)
        lineTo(21f, 12f)
        moveTo(12f, 16.5f)
        lineTo(19.2f, 16.5f)
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
