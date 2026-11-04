package com.naury.kinetica.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.ceil

/** [glassLens]와 [KLensThumb]의 기본값. */
public object KLensDefaults {
    /** 다 부푼 렌즈 크기. 슬라이더 손잡이(36×24dp)보다 크게 두어 트랙 위로 떠오르게 한다. */
    public val Size: DpSize = DpSize(60.dp, 40.dp)

    /** 놓았을 때의 불투명 손잡이 크기. */
    public val ThumbSize: DpSize = DpSize(36.dp, 24.dp)

    /** 렌즈 가운데의 확대 배율. */
    public const val ZOOM: Float = 1.3f

    /** 렌즈 안에 옅게 덮는 색. 아래 내용이 없는 곳에서 렌즈가 보이게 한다. */
    public val Tint: Color = Color.White.copy(alpha = 0.10f)
}

/**
 * 이 요소의 그리기 내용 위에 유리 방울 렌즈를 띄운다. [progress]가 0이면 아무것도 하지 않고, 0보다 크면 [center] 자리에
 * 캡슐 렌즈가 부풀어 올라 그 아래 내용(트랙, 글자, 인디케이터)을 [zoom]배 확대하고 가장자리를 굴절시킨다. 렌즈 자리의 원래 내용은
 * 그리지 않으므로 확대된 모습과 겹쳐 보이지 않는다. 슬라이더나 스위치 손잡이를 잡는 동안 쓰고, 놓아 [progress]가 0이 되면
 * 그 자리에 [KLensThumb] 같은 불투명 손잡이가 다시 보이게 한다.
 *
 * Android 13(API 33)부터 AGSL 셰이더로 렌즈 크기 영역만 샘플링한다. 그보다 낮으면 확대 없이 반투명 방울만 부풀린다.
 * [progress]와 [center]는 그리기 단계에서만 읽으므로 매 프레임 바뀌는 애니메이션 값이나 끌기 위치를 그대로 넘겨도 다시 구성하지 않는다.
 * 렌즈는 요소 경계 밖으로 나가도 그대로 그린다. 부모가 자르면 잘린다. TalkBack에는 아무것도 알리지 않는다.
 *
 * ```kotlin
 * val grab = remember { Animatable(0f) }
 * Box(Modifier.glassLens(progress = { grab.value }, center = { Offset(thumbX, it.height / 2) })) { Track() }
 * ```
 *
 * @param progress 렌즈가 부푼 정도(0..1). 범위를 벗어난 값은 0..1로 맞춘다.
 * @param center 이 요소의 크기(px)를 받아 렌즈 가운데 위치(px, 이 요소 왼쪽 위 기준)를 돌려준다. RTL에서도 물리 좌표다.
 * @param size 다 부푼 렌즈 크기. 부푸는 동안 이 크기의 70%에서 시작한다. 0 이하 크기면 렌즈를 그리지 않는다.
 * @param zoom 렌즈 가운데의 확대 배율로, 1 이상이다. 1보다 작으면 1로 맞춘다.
 * @param refraction 가장자리 굴절 세기(0..1). 범위를 벗어난 값은 0..1로 맞춘다.
 * @param tint 렌즈 안에 덮는 색
 */
@Composable
public fun Modifier.glassLens(
    progress: () -> Float,
    center: (Size) -> Offset,
    size: DpSize = KLensDefaults.Size,
    zoom: Float = KLensDefaults.ZOOM,
    refraction: Float = 1f,
    tint: Color = KLensDefaults.Tint,
): Modifier {
    val full = size
    return lens(progress, center, { Size(full.width.toPx(), full.height.toPx()) }, LensOptics(zoom, refraction, tint))
}

/**
 * 놓았을 때의 불투명 손잡이. [progress]가 1에 가까울수록 투명해져, 같은 진행 값을 받은 [glassLens] 렌즈가 그 자리를 이어받는다.
 * [glassLens]를 붙인 요소 안에 둬야 렌즈가 손잡이 아래 내용을 확대해 보여 준다. 크기는 기본 [KLensDefaults.ThumbSize]이고
 * [modifier]의 크기가 앞선다. 장식이라 TalkBack에서는 읽지 않는다. 값과 동작은 손잡이를 담은 컨트롤 노드에 둔다.
 *
 * @param progress 렌즈로 바뀐 정도(0..1). 그리기 단계에서만 읽는다.
 * @param color 손잡이 색
 */
@Composable
public fun KLensThumb(progress: () -> Float, modifier: Modifier = Modifier, color: Color = Color.White) {
    Box(
        modifier
            .size(KLensDefaults.ThumbSize)
            .clearAndSetSemantics { }
            .drawBehind { drawLensKnob(Offset(size.width / 2, size.height / 2), size, 1f - progress().coerceIn(0f, 1f), color) },
    )
}

/** 렌즈의 확대 배율, 굴절 세기(0..1), 덮는 색. */
internal class LensOptics(zoom: Float, refraction: Float, val tint: Color) {
    val zoom: Float = zoom.coerceAtLeast(1f)
    val refraction: Float = refraction.coerceIn(0f, 1f)
}

/** [glassLens]의 본체. 컨트롤은 렌즈 크기를 인디케이터 폭처럼 요소 크기에 맞춰 바꾸려고 [lensSize]를 람다로 넘긴다. */
@Composable
internal fun Modifier.lens(progress: () -> Float, center: (Size) -> Offset, lensSize: DpLens, optics: LensOptics): Modifier {
    val content = rememberGraphicsLayer()
    val sample = rememberGraphicsLayer()
    val lens = rememberGraphicsLayer()
    val renderer = remember(content, sample, lens) { LensRenderer(content, sample, lens) }
    return drawWithContent {
        val p = progress().coerceIn(0f, 1f)
        val full = lensSize(size)
        if (p <= 0f || full.width <= 0f || full.height <= 0f) {
            drawContent()
            return@drawWithContent
        }
        renderer.draw(this, LensFrame(center(size), full, p), optics)
    }
}

/** 요소 크기(px)를 받아 다 부푼 렌즈 크기(px)를 돌려주는 함수. 밀도를 쓰려고 [DrawScope]를 받는다. */
internal typealias DpLens = DrawScope.(Size) -> Size

/** 이번 프레임의 렌즈 가운데(px), 다 부푼 크기(px), 부푼 정도(0..1). */
internal class LensFrame(val center: Offset, val full: Size, val progress: Float)

/**
 * 렌즈를 그리는 레이어와 셰이더. 레이어 세 개를 쓴다. [content]는 요소 내용 기록, [sample]은 그것을 렌즈 자리만큼 옮겨 놓은 것,
 * [lens]는 [sample]에 렌즈 셰이더를 건 렌즈 크기의 레이어다. 렌즈가 움직이면 [sample]의 이동량만 바꾸고 다시 기록하지 않는다.
 * 셰이더 효과는 렌즈 크기나 광학 값이 바뀔 때만 새로 만든다. 부푸는 동안에는 다 부푼 크기의 렌즈를 줄여 그려 효과를 그대로 쓴다.
 */
internal class LensRenderer(private val content: GraphicsLayer, private val sample: GraphicsLayer, private val lens: GraphicsLayer) {
    private var shader: RuntimeShader? = null
    private var broken = false
    private var key: Any? = null
    private val sampleRecord = RecordGuard()
    private val lensRecord = RecordGuard()
    private val hole = Path()
    private var rim = Stroke(1f)

    private val canMagnify: Boolean get() = !broken && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun draw(scope: ContentDrawScope, frame: LensFrame, optics: LensOptics) = with(scope) {
        val grow = lerp(LENS_START, 1f, frame.progress)
        val w = frame.full.width * grow
        val h = frame.full.height * grow
        val rect = Rect(Offset(frame.center.x - w / 2, frame.center.y - h / 2), Size(w, h))
        val corner = CornerRadius(minOf(w, h) / 2)
        val alpha = (frame.progress * ALPHA_RAMP).coerceAtMost(1f)
        if (canMagnify && prepare(frame.full, optics)) {
            content.record { this@with.drawContent() }
            hole.reset()
            hole.addRoundRect(RoundRect(rect, corner))
            clipPath(hole, ClipOp.Difference) { drawLayer(content) }
            drawMagnified(frame, rect, grow, alpha)
        } else {
            drawContent()
            drawRoundRect(Color.White.copy(alpha = BUBBLE_ALPHA * alpha), rect.topLeft, rect.size, corner)
        }
        drawRim(rect, corner, alpha)
    }

    /** 렌즈 셰이더를 [full] 크기와 [optics]에 맞춘다. 셰이더를 만들지 못하면 false이고, 그 뒤로는 반투명 방울로 그린다. */
    private fun DrawScope.prepare(full: Size, optics: LensOptics): Boolean {
        val px = IntSize(ceil(full.width).toInt(), ceil(full.height).toInt())
        val next = listOf(px, optics.zoom, optics.refraction, optics.tint)
        if (key == next) return true
        key = next
        GlassFxStats.lensBuilds++
        lens.renderEffect = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
            val program = shader ?: RuntimeShader(LENS_SHADER).also { shader = it }
            program.setFloatUniform("size", px.width.toFloat(), px.height.toFloat())
            program.setFloatUniform("radius", minOf(px.width, px.height) / 2f)
            program.setFloatUniform("zoom", optics.zoom)
            program.setFloatUniform("bend", LENS_BEND.toPx() * optics.refraction)
            val t = optics.tint
            program.setFloatUniform("tint", t.red * t.alpha, t.green * t.alpha, t.blue * t.alpha, t.alpha)
            RenderEffect.createRuntimeShaderEffect(program, "content").asComposeRenderEffect()
        }.onFailure { broken = true }.getOrNull()
        return !broken
    }

    private fun DrawScope.drawMagnified(frame: LensFrame, rect: Rect, grow: Float, alpha: Float) {
        val px = IntSize(ceil(frame.full.width).toInt(), ceil(frame.full.height).toInt())
        val left = frame.center.x - px.width / 2f
        val top = frame.center.y - px.height / 2f
        // 내용 기록은 같은 레이어 객체에 다시 하므로, 옮겨 놓는 레이어는 처음 한 번만 기록하고 이동량만 바꾼다.
        sampleRecord.record(this, sample, content, content.size)
        sample.translationX = -left
        sample.translationY = -top
        lensRecord.record(this, lens, sample, px)
        lens.alpha = alpha
        translate(rect.left, rect.top) { scale(grow, grow, pivot = Offset.Zero) { drawLayer(lens) } }
    }

    private fun DrawScope.drawRim(rect: Rect, corner: CornerRadius, alpha: Float) {
        val width = RIM.toPx()
        if (rim.width != width) rim = Stroke(width)
        drawRoundRect(Color.Black.copy(alpha = RIM_SHADE * alpha), rect.topLeft, rect.size, corner, style = rim)
        val inset = width
        drawRoundRect(
            Color.White.copy(alpha = RIM_LIGHT * alpha),
            Offset(rect.left + inset, rect.top + inset),
            Size(rect.width - inset * 2, rect.height - inset * 2),
            CornerRadius((corner.x - inset).coerceAtLeast(0f)),
            style = rim,
        )
    }
}

/** 불투명 손잡이를 [alpha](0..1) 진하기로 [center]에 [size] 캡슐로 그린다. 렌즈가 부풀수록 옅게 그려 렌즈에 자리를 넘긴다. */
internal fun DrawScope.drawLensKnob(center: Offset, size: Size, alpha: Float, color: Color) {
    if (alpha <= 0f) return
    val topLeft = Offset(center.x - size.width / 2, center.y - size.height / 2)
    val corner = CornerRadius(minOf(size.width, size.height) / 2)
    drawRoundRect(Color.Black.copy(alpha = KNOB_SHADOW * alpha), topLeft + Offset(0f, KNOB_DROP.toPx()), size, corner)
    drawRoundRect(color.copy(alpha = color.alpha * alpha), topLeft, size, corner)
}

/** 렌즈, 묶음 글래스, 가장자리 블러가 효과나 레이어를 새로 만든 횟수. 움직이지 않을 때 다시 만들지 않는지 테스트에서 본다. */
internal object GlassFxStats {
    var lensBuilds: Int = 0
    var groupBuilds: Int = 0

    /** 가장자리 블러 띠를 다시 기록한 횟수. 스크롤만 할 때는 늘지 않아야 한다. */
    var edgeBuilds: Int = 0
}

/**
 * 렌즈 셰이더. 가운데는 [zoom]배 확대하고, 가장자리 띠에서는 바깥쪽 픽셀을 더 끌어와 볼록 렌즈처럼 휜다. 모양 밖은 투명하다.
 * 입력은 렌즈 크기 레이어 하나뿐이라 렌즈 바깥을 읽지 않도록 가장자리에서 멈춘다.
 */
internal const val LENS_SHADER = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float zoom;
uniform float bend;
uniform float4 tint;

float roundRect(float2 p, float2 extent, float r) {
    float2 q = abs(p) - extent + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 coord) {
    float2 c = size * 0.5;
    float2 p = coord - c;
    float d = roundRect(p, c, radius);
    float band = max(min(size.x, size.y) * 0.35, 1.0);
    float edge = clamp(1.0 + d / band, 0.0, 1.0);
    float2 dir = normalize(p + float2(0.0001, 0.0001));
    float2 src = c + p / zoom + dir * bend * edge * edge;
    half4 color = content.eval(clamp(src, float2(0.5), size - 0.5));
    color = color + half4(tint) * (1.0 - color.a);
    return color * clamp(0.5 - d, 0.0, 1.0);
}
"""

private const val LENS_START = 0.7f
private const val ALPHA_RAMP = 4f
private const val BUBBLE_ALPHA = 0.32f
private const val RIM_SHADE = 0.10f
private const val RIM_LIGHT = 0.55f
private const val KNOB_SHADOW = 0.14f
private val KNOB_DROP = 1.dp
private val RIM = 1.dp
private val LENS_BEND = 6.dp
