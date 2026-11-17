package com.naury.kinetica.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 시선을 끄는 주 동작 버튼. [KButton]과 같은 크기, 모양, 색 역할을 쓰고, 그 위로 빛 띠가 [period] 간격으로 비스듬히 지나가며
 * 안쪽 테두리가 은은하게 숨 쉬듯 빛난다. 누르면 [KIndication.BUTTON_PRESSED_SCALE] 배율로 살짝 눌린다.
 *
 * 빛은 버튼이 화면에 보이고 누를 수 있을 때만 돈다. 화면 밖으로 나가면 멈췄다가 돌아오면 이어 가고, [loading], [success],
 * `enabled = false`이면 멈춘다. '애니메이션 삭제'가 켜져 있으면 빛 띠를 그리지 않고 테두리 빛만 고정해 그린다.
 * 화면에 하나만 두는 시작하기, 결제하기 같은 버튼에 쓴다. 여러 개를 나란히 두면 서로 시선을 다툰다.
 *
 * ```kotlin
 * KShineButton("시작하기", onClick = ::start, size = KButtonSize.Large, fullWidth = true)
 * ```
 *
 * @param period 빛 띠가 한 번 지나가고 다음 띠가 올 때까지의 시간(ms). 400 이상이어야 하며 아니면 [IllegalArgumentException]을 던진다.
 * @param shineColor 빛 띠와 테두리 빛 색. 알파로 진하기를 정한다. 기본값은 흰색 0.45다.
 * 그 밖의 파라미터는 [KButton]과 같다.
 */
@Composable
public fun KShineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: KTone = KTone.Accent,
    size: KButtonSize = KButtonSize.Medium,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    loading: Boolean = false,
    success: Boolean = false,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
    period: Int = DEFAULT_PERIOD_MS,
    shineColor: Color = Color.White.copy(alpha = DEFAULT_SHINE_ALPHA),
    surface: SurfaceStyle = KineticaTheme.surface,
    shape: Shape = shapeFor { button },
) {
    require(period >= MIN_PERIOD_MS) { "period는 $MIN_PERIOD_MS 이상이어야 합니다." }
    val still = isReducedMotion()
    val clock = rememberLoopClock(running = enabled && !loading && !success && !still)
    val interaction = remember { MutableInteractionSource() }
    val press = remember(shape) { KIndication(shape, Color.Transparent, 0f, KIndication.BUTTON_PRESSED_SCALE) }
    val glow = Modifier.shine(ShineSpec(shape, shineColor, period / MS_PER_SECOND, still), clock)
    ButtonBody(
        ButtonSpec(text, KVariant.Filled, tone, size, loading, success, enabled, fullWidth, surface, shape, Color.Unspecified),
        onClick,
        modifier.indication(interaction, press).loopVisibility(clock),
        icon?.let { rememberVectorPainter(it) },
        trailingIcon?.let { rememberVectorPainter(it) },
        ButtonExtras(interaction, glow),
    )
}

/** 빛 띠 그리기에 쓰는 고정 값 */
private class ShineSpec(val shape: Shape, val color: Color, val periodSeconds: Float, val still: Boolean)

/**
 * 빛 띠와 테두리 빛. 붓, 외곽선, 선 굵기는 크기가 바뀔 때만 만들고, 프레임마다 바뀌는 시계 값은 그리기 단계에서만 읽는다.
 */
private fun Modifier.shine(spec: ShineSpec, clock: LoopClock): Modifier = drawWithCache {
    val band = size.height * BAND_RATIO
    val brush = Brush.horizontalGradient(
        0f to Color.Transparent,
        HALF to spec.color,
        1f to Color.Transparent,
        startX = 0f,
        endX = band,
    )
    val edge = Brush.verticalGradient(listOf(spec.color, spec.color.copy(alpha = 0f)))
    val outline = spec.shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(EDGE_WIDTH.toPx())
    // 띠를 기울이면 위아래 끝이 버튼 밖으로 나가므로 높이를 넉넉히 잡는다. 바깥은 버튼 모양 자르기가 가린다.
    val bandSize = Size(band, size.height * BAND_OVERSHOOT)
    val bandTop = -size.height * (BAND_OVERSHOOT - 1f) / 2f
    val travel = size.width + band * 2f
    onDrawBehind {
        val seconds = clock.seconds
        val phase = if (spec.still) 0f else (seconds % spec.periodSeconds) / spec.periodSeconds
        val breathe = if (spec.still) 1f else HALF + HALF * sin(seconds * BREATHE_SPEED * PI.toFloat() * 2f)
        drawOutline(outline, edge, alpha = EDGE_MIN + EDGE_RANGE * breathe, style = stroke)
        if (spec.still) return@onDrawBehind
        val sweep = phase / SWEEP_SHARE
        if (sweep >= 1f) return@onDrawBehind
        val x = -band * 2f + travel * FastOutSlowInEasing.transform(sweep)
        rotate(BAND_ANGLE, pivot = Offset(x + band / 2f, size.height / 2f)) {
            translate(left = x, top = bandTop) { drawRect(brush, size = bandSize) }
        }
    }
}

private const val DEFAULT_PERIOD_MS = 2800
private const val MIN_PERIOD_MS = 400
private const val MS_PER_SECOND = 1000f
private const val DEFAULT_SHINE_ALPHA = 0.45f
private const val BAND_RATIO = 1.1f
private const val BAND_OVERSHOOT = 2.2f
private const val BAND_ANGLE = 20f
private const val SWEEP_SHARE = 0.38f
private const val HALF = 0.5f
private const val BREATHE_SPEED = 0.35f
private const val EDGE_MIN = 0.35f
private const val EDGE_RANGE = 0.45f
private val EDGE_WIDTH = 1.5.dp
