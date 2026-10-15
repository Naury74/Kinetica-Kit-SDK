package com.naury.kinetica.export

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.naury.kinetica.core.scene.ArgbColor

/** 목업 기기 프레임. 코드로 그리는 일반적인 모양이며 특정 제조사의 기기를 본뜨지 않는다. */
public enum class DeviceFrame {
    /** 모서리가 둥글고 위쪽에 펀치홀 카메라가 있는 휴대폰 */
    Phone,

    /** 가운데에 접히는 선이 있는, 펼친 폴더블 */
    Foldable,

    /** 베젤 두께가 일정한 태블릿 */
    Tablet,

    /** 위쪽에 타이틀 바가 있는 브라우저 창 */
    Browser,
}

/**
 * 장면을 기기 프레임에 넣어 내보내는 설정. 이때 [ExportRequest.widthPx]는 배경까지 포함한 이미지 전체 너비가 된다.
 *
 * @property paddingRatio 이미지 너비에 대한 배경 여백 비율(0..0.4)
 * @property shadow 기기 아래에 그림자를 그릴지
 */
public data class MockupFrame(
    val device: DeviceFrame = DeviceFrame.Phone,
    val background: ArgbColor = ArgbColor(DEFAULT_BACKGROUND),
    val paddingRatio: Double = 0.08,
    val shadow: Boolean = true,
) {
    init {
        require(paddingRatio.isFinite() && paddingRatio in 0.0..MAX_PADDING) { "여백 비율은 0..$MAX_PADDING 범위여야 합니다." }
    }

    private companion object {
        const val DEFAULT_BACKGROUND = 0xFFEEF0F5.toInt()
        const val MAX_PADDING = 0.4
    }
}

/** 기기 프레임의 치수(픽셀). [screen]은 장면을 그릴 영역이다. */
internal class MockupLayout(val frame: MockupFrame, width: Int, artboardAspect: Double) {
    private val spec = DeviceSpec.of(frame.device)
    private val padding = (width * frame.paddingRatio).toFloat()
    private val bodyWidth = width - 2 * padding
    private val bezel = bodyWidth * spec.bezel
    private val bar = bodyWidth * spec.bar
    private val screenWidth = bodyWidth - 2 * bezel
    private val screenHeight = (screenWidth * artboardAspect).toFloat()
    val body =
        RoundRect(padding, padding, padding + bodyWidth, padding + bar + screenHeight + 2 * bezel, CornerRadius(bodyWidth * spec.radius))
    val screen = RoundRect(
        body.left + bezel,
        body.top + bezel + bar,
        body.right - bezel,
        body.top + bezel + bar + screenHeight,
        CornerRadius(bodyWidth * spec.screenRadius),
    )
    val height: Int = (body.bottom + padding).toInt()

    /** 배경, 그림자, 기기 몸체를 그린다. 장면은 그다음에 [screen] 안에 그린다. */
    fun DrawScope.drawBehindScreen() {
        drawRect(Color(frame.background.argb))
        if (frame.shadow) {
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.Black.copy(alpha = SHADOW_ALPHA).toArgb()
                    maskFilter = android.graphics.BlurMaskFilter(body.width * SHADOW_BLUR, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                val drop = body.width * SHADOW_DROP
                canvas.nativeCanvas.drawRoundRect(
                    body.left,
                    body.top + drop,
                    body.right,
                    body.bottom + drop,
                    body.topLeftCornerRadius.x,
                    body.topLeftCornerRadius.y,
                    paint,
                )
            }
        }
        drawPath(Path().apply { addRoundRect(body) }, Color(spec.bodyColor))
        drawPath(Path().apply { addRoundRect(screen) }, Color.Black)
    }

    /** 펀치홀 카메라, 접히는 선, 타이틀 바처럼 화면 위에 덧그리는 요소. */
    fun DrawScope.drawOverScreen() {
        when (frame.device) {
            DeviceFrame.Phone -> drawCircle(
                Color(spec.bodyColor),
                radius = body.width * CAMERA,
                center = Offset(
                    (screen.left + screen.right) / 2f,
                    screen.top + body.width * CAMERA * 2,
                ),
            )

            DeviceFrame.Foldable -> drawLine(
                Color.Black.copy(alpha = HINGE_ALPHA),
                Offset((screen.left + screen.right) / 2f, screen.top),
                Offset((screen.left + screen.right) / 2f, screen.bottom),
                strokeWidth =
                    body.width * HINGE,
            )

            DeviceFrame.Tablet -> Unit

            DeviceFrame.Browser -> listOf(BROWSER_RED, BROWSER_YELLOW, BROWSER_GREEN).forEachIndexed { index, color ->
                val radius = body.width * DOT
                drawCircle(
                    Color(color),
                    radius,
                    Offset(
                        body.left + bezel + radius * (DOT_START + index * DOT_STEP),
                        body.top + (bar + bezel) / 2,
                    ),
                )
            }
        }
    }

    val screenSize: Size get() = Size(screen.width, screen.height)

    private class DeviceSpec(val bezel: Float, val radius: Float, val screenRadius: Float, val bar: Float, val bodyColor: Int) {
        companion object {
            // 기기 모양별 비율 표다. 값마다 이름을 붙이면 오히려 표를 읽기 어려워진다.
            @Suppress("MagicNumber")
            fun of(device: DeviceFrame): DeviceSpec = when (device) {
                DeviceFrame.Phone -> DeviceSpec(0.035f, 0.13f, 0.1f, 0f, PHONE_BODY)
                DeviceFrame.Foldable -> DeviceSpec(0.022f, 0.06f, 0.045f, 0f, PHONE_BODY)
                DeviceFrame.Tablet -> DeviceSpec(0.04f, 0.05f, 0.02f, 0f, PHONE_BODY)
                DeviceFrame.Browser -> DeviceSpec(0.006f, 0.015f, 0.008f, 0.05f, BROWSER_BODY)
            }
        }
    }

    private companion object {
        const val SHADOW_ALPHA = 0.25f
        const val SHADOW_BLUR = 0.05f
        const val SHADOW_DROP = 0.03f
        const val CAMERA = 0.018f
        const val HINGE = 0.004f
        const val HINGE_ALPHA = 0.18f
        const val DOT = 0.008f
        const val DOT_START = 2
        const val DOT_STEP = 3
        const val PHONE_BODY = 0xFF1B1D22.toInt()
        const val BROWSER_BODY = 0xFFE4E6EB.toInt()
        const val BROWSER_RED = 0xFFFF5F57.toInt()
        const val BROWSER_YELLOW = 0xFFFEBC2E.toInt()
        const val BROWSER_GREEN = 0xFF28C840.toInt()
    }
}
