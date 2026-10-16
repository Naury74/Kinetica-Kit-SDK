// 아이콘 경로 좌표는 24 격자 위의 점이라 상수로 빼면 오히려 읽기 어렵다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// 편집기 도구 모음과 레이어 패널의 선 아이콘. 24 격자에 1.8 두께로 그리고, 색은 Icon의 tint로 바꾼다.
// 아이콘 의존성을 늘리지 않으려고 직접 그린다.
internal object StudioIcons {
    val Rectangle: ImageVector by lazy { icon("rectangle") { roundRect(4f, 5f, 20f, 19f, 2.5f) } }

    val Ellipse: ImageVector by lazy { icon("ellipse") { circle(12f, 12f, 8f) } }

    val Star: ImageVector by lazy { icon("star") { star() } }

    val Text: ImageVector by lazy {
        icon("text") {
            moveTo(5f, 8f)
            verticalLineTo(5.5f)
            horizontalLineTo(19f)
            verticalLineTo(8f)
            moveTo(12f, 5.5f)
            verticalLineTo(19f)
            moveTo(9.5f, 19f)
            horizontalLineTo(14.5f)
        }
    }

    val Frame: ImageVector by lazy {
        icon("frame") {
            moveTo(8f, 3.5f)
            verticalLineTo(20.5f)
            moveTo(16f, 3.5f)
            verticalLineTo(20.5f)
            moveTo(3.5f, 8f)
            horizontalLineTo(20.5f)
            moveTo(3.5f, 16f)
            horizontalLineTo(20.5f)
        }
    }

    val HostView: ImageVector by lazy {
        icon("host") {
            roundRect(4f, 4f, 10.5f, 10.5f, 1.8f)
            roundRect(13.5f, 4f, 20f, 10.5f, 1.8f)
            roundRect(4f, 13.5f, 10.5f, 20f, 1.8f)
            circle(16.75f, 16.75f, 3.25f)
        }
    }

    val Undo: ImageVector by lazy {
        icon("undo", autoMirror = true) {
            moveTo(9f, 14f)
            lineTo(4f, 9f)
            lineTo(9f, 4f)
            moveTo(4f, 9f)
            horizontalLineTo(14.5f)
            arcToRelative(5.5f, 5.5f, 0f, false, true, 0f, 11f)
            horizontalLineTo(10f)
        }
    }

    val Redo: ImageVector by lazy {
        icon("redo", autoMirror = true) {
            moveTo(15f, 14f)
            lineTo(20f, 9f)
            lineTo(15f, 4f)
            moveTo(20f, 9f)
            horizontalLineTo(9.5f)
            arcToRelative(5.5f, 5.5f, 0f, false, false, 0f, 11f)
            horizontalLineTo(14f)
        }
    }

    val Fit: ImageVector by lazy {
        icon("fit") {
            moveTo(4f, 9f)
            verticalLineTo(6f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            horizontalLineTo(9f)
            moveTo(15f, 4f)
            horizontalLineTo(18f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(9f)
            moveTo(20f, 15f)
            verticalLineTo(18f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(15f)
            moveTo(9f, 20f)
            horizontalLineTo(6f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(15f)
            roundRect(9f, 9f, 15f, 15f, 1.2f)
        }
    }

    val Motion: ImageVector by lazy {
        icon("motion") {
            moveTo(15f, 6.5f)
            lineTo(20.5f, 12f)
            lineTo(15f, 17.5f)
            lineTo(9.5f, 12f)
            close()
            moveTo(3.5f, 8.5f)
            horizontalLineTo(7f)
            moveTo(2.5f, 12f)
            horizontalLineTo(6.5f)
            moveTo(3.5f, 15.5f)
            horizontalLineTo(7f)
        }
    }

    val Layers: ImageVector by lazy {
        icon("layers") {
            moveTo(12f, 4f)
            lineTo(20f, 8.5f)
            lineTo(12f, 13f)
            lineTo(4f, 8.5f)
            close()
            moveTo(4f, 12.5f)
            lineTo(12f, 17f)
            lineTo(20f, 12.5f)
            moveTo(4f, 16f)
            lineTo(12f, 20.5f)
            lineTo(20f, 16f)
        }
    }

    val Properties: ImageVector by lazy {
        icon("properties") {
            moveTo(4f, 7f)
            horizontalLineTo(6.5f)
            moveTo(11.5f, 7f)
            horizontalLineTo(20f)
            circle(9f, 7f, 2.5f)
            moveTo(4f, 17f)
            horizontalLineTo(12.5f)
            moveTo(17.5f, 17f)
            horizontalLineTo(20f)
            circle(15f, 17f, 2.5f)
        }
    }

    val Exit: ImageVector by lazy {
        icon("exit", autoMirror = true) {
            moveTo(10f, 4f)
            horizontalLineTo(6.5f)
            arcToRelative(2.5f, 2.5f, 0f, false, false, -2.5f, 2.5f)
            verticalLineTo(17.5f)
            arcToRelative(2.5f, 2.5f, 0f, false, false, 2.5f, 2.5f)
            horizontalLineTo(10f)
            moveTo(15f, 8f)
            lineTo(19f, 12f)
            lineTo(15f, 16f)
            moveTo(19f, 12f)
            horizontalLineTo(9.5f)
        }
    }

    val Eye: ImageVector by lazy { icon("eye") { eye() } }

    val EyeOff: ImageVector by lazy {
        icon("eye-off") {
            eye()
            moveTo(4f, 4f)
            lineTo(20f, 20f)
        }
    }

    val Lock: ImageVector by lazy {
        icon("lock") {
            roundRect(5f, 11f, 19f, 20f, 2f)
            moveTo(8f, 11f)
            verticalLineTo(8f)
            arcToRelative(4f, 4f, 0f, false, true, 8f, 0f)
            verticalLineTo(11f)
        }
    }

    val LockOpen: ImageVector by lazy {
        icon("lock-open") {
            roundRect(5f, 11f, 19f, 20f, 2f)
            moveTo(8f, 11f)
            verticalLineTo(8f)
            arcToRelative(4f, 4f, 0f, false, true, 7.75f, -1.4f)
        }
    }
}

private fun icon(name: String, autoMirror: Boolean = false, build: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
    name = "kinetica.$name",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = VIEWPORT,
    viewportHeight = VIEWPORT,
    autoMirror = autoMirror,
).path(
    fill = null,
    stroke = SolidColor(Color.Black),
    strokeLineWidth = STROKE,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = build,
).build()

private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
    moveTo(left + radius, top)
    horizontalLineTo(right - radius)
    arcToRelative(radius, radius, 0f, false, true, radius, radius)
    verticalLineTo(bottom - radius)
    arcToRelative(radius, radius, 0f, false, true, -radius, radius)
    horizontalLineTo(left + radius)
    arcToRelative(radius, radius, 0f, false, true, -radius, -radius)
    verticalLineTo(top + radius)
    arcToRelative(radius, radius, 0f, false, true, radius, -radius)
    close()
}

private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
    moveTo(cx - radius, cy)
    arcToRelative(radius, radius, 0f, true, true, radius * 2, 0f)
    arcToRelative(radius, radius, 0f, true, true, -radius * 2, 0f)
    close()
}

private fun PathBuilder.eye() {
    moveTo(2.5f, 12f)
    curveTo(4.8f, 7.6f, 8.2f, 5.5f, 12f, 5.5f)
    curveTo(15.8f, 5.5f, 19.2f, 7.6f, 21.5f, 12f)
    curveTo(19.2f, 16.4f, 15.8f, 18.5f, 12f, 18.5f)
    curveTo(8.2f, 18.5f, 4.8f, 16.4f, 2.5f, 12f)
    close()
    circle(12f, 12f, 3f)
}

// 꼭짓점이 위를 향하는 오각별. 광학 중심을 맞추려고 중심을 조금 아래로 둔다.
private fun PathBuilder.star() {
    for (i in 0 until STAR_POINTS * 2) {
        val radius = if (i % 2 == 0) STAR_OUTER else STAR_INNER
        val angle = -PI / 2 + i * PI / STAR_POINTS
        val x = (STAR_CX + radius * cos(angle)).toFloat()
        val y = (STAR_CY + radius * sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private const val VIEWPORT = 24f
private const val STROKE = 1.8f
private const val STAR_POINTS = 5
private const val STAR_OUTER = 8.8
private const val STAR_INNER = 3.9
private const val STAR_CX = 12.0
private const val STAR_CY = 12.8
