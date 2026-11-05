// 파일 이름은 대표 컴포저블을 따른다. 함께 쓰는 공개 범위 인터페이스가 하나뿐이라 detekt가 그 이름을 요구한다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** [KGlassGroup] 안에서 쓰는 범위. `Row`나 `Column` 안에서도 [glassId]를 쓸 수 있도록 레이아웃 범위를 따로 두지 않는다. */
@Stable
public interface KGlassGroupScope {
    /**
     * 이 요소 자리에 그룹의 글래스 판을 깐다. 같은 그룹의 다른 판과 가까워지면 둘 사이가 액체처럼 녹아 붙고, 멀어지면 갈라진다.
     * 판 위치는 배치 단계 위치를 따르므로 `Modifier.offset { }`처럼 배치를 바꾸는 방법으로 움직여야 한다. `graphicsLayer`의 이동은 반영되지 않는다.
     * 그룹이 녹여 그릴 수 있는 판은 먼저 들어온 [MAX_GLASS_PLATES]개까지이고, 넘친 판과 Android 13(API 33) 미만에서는 판마다 따로
     * [Modifier.glass]를 그린다. 판은 장식이라 TalkBack에 알리지 않는다.
     *
     * @param id 그룹 안에서 판을 가르는 값. 같은 그룹 안에서 겹치지 않아야 한다.
     * @param cornerRadius 판 모서리 반경. 짧은 변의 절반보다 크면 캡슐 모양이 된다.
     */
    @Composable
    public fun Modifier.glassId(id: Any, cornerRadius: Dp = 999.dp): Modifier
}

/** [KGlassGroup] 하나가 녹여 그리는 글래스 판의 최대 수. */
public const val MAX_GLASS_PLATES: Int = 8

/**
 * 가까운 글래스 판끼리 액체처럼 녹아 붙는 그룹. 안쪽 요소에 [KGlassGroupScope.glassId]를 붙이면 그 자리에 글래스 판이 깔리고,
 * 판 사이가 [melt]보다 가까워지면 거리 함수의 부드러운 합집합(SDF smooth union)으로 이어진다. 펼쳐지며 갈라지는 떠 있는 버튼 묶음,
 * 붙었다 떨어지는 툴바 조각에 어울린다.
 *
 * 판은 [backdrop]을 [style]의 블러로 비춘다. [KineticaScaffold] 안이면 함께 쓰는 블러 기록을 쓰므로 판이 여럿이어도 블러는 한 번이다.
 * Android 13(API 33)부터 셰이더 하나가 그룹 영역만 훑어 판 모양, 가장자리 굴절, 테두리 빛, 그림자를 함께 그린다. 판이 움직이지 않으면
 * 셰이더 값을 다시 올리지 않는다. 그보다 낮은 버전이거나 기기가 셰이더를 만들지 못하면 판마다 따로 둔 [Modifier.glass]로 그린다.
 * 따로 그릴 때는 겹친 판이 겹친 만큼 진해지므로, 다른 판 밑에 숨겨 둔 판(접힌 메뉴 등)은 그리지 않게 하는 편이 좋다.
 * 판과 그림자는 그룹 경계 밖으로 [melt]에 그림자 폭을 더한 만큼까지 그려진다.
 *
 * ```kotlin
 * KGlassGroup {
 *     Box(Modifier.offset { IntOffset(0, -spread()) }.glassId("share").size(52.dp)) { Icon(…) }
 *     Box(Modifier.glassId("main").size(60.dp)) { Icon(…) }
 * }
 * ```
 *
 * @param style 판의 글래스 스타일. 그림자와 테두리 빛은 그룹이 판 모양을 따라 직접 그린다.
 * @param melt 판이 녹아 붙기 시작하는 거리. 클수록 멀리서도 이어지고 이음매가 둥글어진다. 1dp보다 작으면 1dp로 맞춘다.
 * @param backdrop 비출 배경. 기본값은 [LocalGlassBackdrop]이다. null이면 [GlassStyle.fallback] 색으로 칠한다.
 */
@Composable
public fun KGlassGroup(
    modifier: Modifier = Modifier,
    style: GlassStyle = LocalGlassStyle.current,
    melt: Dp = 20.dp,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    content: @Composable KGlassGroupScope.() -> Unit,
) {
    val scope = remember { GlassGroupScope() }
    scope.style = style
    scope.backdrop = backdrop
    scope.merged = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !scope.broken
    val reach = melt.coerceAtLeast(1.dp)
    val pad = with(LocalDensity.current) { (reach + GROUP_SHADOW).roundToPx() }
    Box(modifier) {
        Spacer(
            Modifier
                .matchParentSize()
                .onPlaced { scope.group = it }
                .then(
                    if (scope.merged) {
                        Modifier
                            .bleed(pad)
                            .graphicsLayer { renderEffect = scope.effect(this, pad.toFloat(), reach.toPx()) }
                            .glass(style.forGroup(), RectangleShape, backdrop)
                    } else {
                        Modifier
                    },
                ),
        )
        scope.content()
    }
}

/** 그룹이 녹여 그리는 판 하나. 위치와 크기는 그룹 왼쪽 위 기준 px다. */
@Stable
internal class GlassPlate {
    var rect by mutableStateOf(Rect.Zero)
    var corner by mutableStateOf(0.dp)
}

private class GlassGroupScope : KGlassGroupScope {
    val plates = mutableStateListOf<GlassPlate>()
    var group: LayoutCoordinates? = null
    var style: GlassStyle = GlassStyles.Liquid
    var backdrop: GlassBackdrop? = null
    var merged by mutableStateOf(true)
    var broken by mutableStateOf(false)
    val uniforms = GroupUniforms()
    private var shader: RuntimeShader? = null
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null

    @Composable
    override fun Modifier.glassId(id: Any, cornerRadius: Dp): Modifier {
        val plate = remember(id) { GlassPlate() }
        DisposableEffect(plate) {
            plates.add(plate)
            onDispose { plates.remove(plate) }
        }
        plate.corner = cornerRadius
        val index = plates.indexOf(plate)
        // 아직 등록 전인 첫 프레임에는 자리가 남아 있으면 그룹이 그릴 판으로 본다. 따로 그렸다가 바로 바꾸면 한 프레임 깜박인다.
        val inGroup = merged && (index in 0 until MAX_GLASS_PLATES || (index < 0 && plates.size < MAX_GLASS_PLATES))
        val tracked = onGloballyPositioned { coordinates ->
            val parent = group?.takeIf { it.isAttached } ?: return@onGloballyPositioned
            val rect = parent.localBoundingBoxOf(coordinates, clipBounds = false)
            if (plate.rect != rect) plate.rect = rect
        }
        return if (inGroup) tracked else tracked.glass(style, RoundedCornerShape(cornerRadius), backdrop)
    }

    /**
     * 판 모양 셰이더 효과. 판 위치, 크기, 모서리, 레이어 크기 가운데 하나라도 바뀌었을 때만 셰이더 값을 다시 올린다.
     * 셰이더를 만들지 못하면 판마다 따로 그리도록 바꾼다.
     */
    fun effect(layer: GraphicsLayerScope, pad: Float, melt: Float): androidx.compose.ui.graphics.RenderEffect? {
        val shown = plates.take(MAX_GLASS_PLATES)
        if (!uniforms.update(shown.map { it.rect }, shown.map { with(layer) { it.corner.toPx() } }, pad, layer.size, melt)) return effect
        GlassFxStats.groupBuilds++
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        effect = runCatching {
            val program = shader ?: RuntimeShader(GROUP_SHADER).also { shader = it }
            uniforms.apply(program, style, layer)
            RenderEffect.createRuntimeShaderEffect(program, "content").asComposeRenderEffect()
        }.onFailure { broken = true }.getOrNull()
        return effect
    }
}

/**
 * 묶음 셰이더에 올릴 판 값. [update]가 지난번과 같은지 가려, 판이 움직이지 않는 프레임에는 효과를 다시 만들지 않게 한다.
 * 판마다 (가운데 x, 가운데 y, 절반 폭, 절반 높이)와 (모서리 반경, 0, 0, 0)을 담는다. 길이는 모두 그룹 레이어 px다.
 */
internal class GroupUniforms {
    val rects = FloatArray(MAX_GLASS_PLATES * VEC4)
    val extra = FloatArray(MAX_GLASS_PLATES * VEC4)
    var count = 0
        private set
    var bounds = Size.Zero
        private set
    var melt = 0f
        private set
    private val next = FloatArray(MAX_GLASS_PLATES * VEC4)
    private val nextExtra = FloatArray(MAX_GLASS_PLATES * VEC4)

    /**
     * [plates](그룹 기준 px)와 [corners](px)를 [pad]만큼 넓힌 레이어 좌표로 옮겨 담는다. 앞선 값과 같으면 false를 돌려준다.
     * [MAX_GLASS_PLATES]개를 넘는 판은 버린다. 크기가 0인 판은 아직 배치되지 않은 것으로 보고 뺀다.
     */
    fun update(plates: List<Rect>, corners: List<Float>, pad: Float, size: Size, melt: Float): Boolean {
        next.fill(0f)
        nextExtra.fill(0f)
        var n = 0
        for (i in plates.indices) {
            val rect = plates[i]
            if (n >= MAX_GLASS_PLATES || rect.width <= 0f || rect.height <= 0f) continue
            val at = n * VEC4
            next[at] = rect.center.x + pad
            next[at + 1] = rect.center.y + pad
            next[at + 2] = rect.width / 2f
            next[at + HALF_HEIGHT] = rect.height / 2f
            nextExtra[at] = minOf(corners.getOrElse(i) { 0f }, rect.minDimension / 2f)
            n++
        }
        val same = n == count && size == bounds && melt == this.melt && next.contentEquals(rects) && nextExtra.contentEquals(extra)
        if (same) return false
        next.copyInto(rects)
        nextExtra.copyInto(extra)
        count = n
        bounds = size
        this.melt = melt
        return true
    }

    fun apply(shader: RuntimeShader, style: GlassStyle, density: GraphicsLayerScope) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        shader.setFloatUniform("bounds", bounds.width, bounds.height)
        shader.setFloatUniform("count", count.toFloat())
        shader.setFloatUniform("melt", melt)
        shader.setFloatUniform("rects", rects)
        shader.setFloatUniform("extra", extra)
        with(density) {
            shader.setFloatUniform("bend", GROUP_BEND.toPx() * style.refraction.coerceAtLeast(MIN_BEND))
            shader.setFloatUniform("band", GROUP_BAND.toPx())
            shader.setFloatUniform("spread", GROUP_SHADOW.toPx())
        }
        val light = style.edgeLight
        shader.setFloatUniform("rim", light.red * light.alpha, light.green * light.alpha, light.blue * light.alpha, light.alpha)
        shader.setFloatUniform("shade", if (style.shadow > 0.dp) GROUP_SHADE else 0f)
    }
}

/** 그룹이 셰이더로 그릴 글래스. 그림자, 테두리 빛, 굴절은 셰이더가 판 모양을 따라 그리므로 뺀다. */
private fun GlassStyle.forGroup(): GlassStyle = copy(shadow = 0.dp, edgeLight = Color.Transparent, refraction = 0f)

/** 요소를 사방으로 [pad](px)만큼 넓혀 그리되 배치 크기는 그대로 둔다. 판 그림자와 녹아 붙은 이음매가 경계에서 잘리지 않게 한다. */
private fun Modifier.bleed(pad: Int): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed(width + pad * 2, height + pad * 2))
    layout(width, height) { placeable.place(-pad, -pad) }
}

/**
 * 판들의 둥근 사각형 거리 함수를 부드러운 최솟값으로 합쳐 하나의 모양으로 그린다. 모양 안은 글래스 기록을 가장자리에서 굴절시켜 그리고,
 * 가장자리 안쪽에 테두리 빛을, 바깥 [spread] 안에 옅은 그림자를 깐다. 판에서 [spread]보다 먼 곳은 거리 함수 한 번만 계산하고 끝낸다.
 */
internal const val GROUP_SHADER = """
uniform shader content;
uniform float2 bounds;
uniform float count;
uniform float melt;
uniform float4 rects[8];
uniform float4 extra[8];
uniform float bend;
uniform float band;
uniform float spread;
uniform float4 rim;
uniform float shade;

float roundRect(float2 p, float2 extent, float r) {
    float2 q = abs(p) - extent + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float field(float2 p) {
    float d = 1e5;
    for (int i = 0; i < 8; i++) {
        if (float(i) >= count) break;
        float4 r = rects[i];
        float e = roundRect(p - r.xy, r.zw, extra[i].x);
        float h = max(melt - abs(d - e), 0.0) / melt;
        d = min(d, e) - h * h * melt * 0.25;
    }
    return d;
}

half4 main(float2 coord) {
    float d = field(coord);
    if (d > spread) return half4(0.0);
    float fall = 1.0 - clamp(d / spread, 0.0, 1.0);
    half4 shadow = half4(0.0, 0.0, 0.0, shade * fall * fall);
    if (d > 1.0) return shadow;
    float2 grad = float2(field(coord + float2(1.0, 0.0)) - d, field(coord + float2(0.0, 1.0)) - d);
    float2 n = normalize(grad + float2(0.0001, 0.0001));
    float edge = clamp(1.0 + d / band, 0.0, 1.0);
    half4 color = content.eval(clamp(coord + n * bend * edge * edge, float2(0.5), bounds - 0.5));
    float glow = pow(edge, 6.0) * (0.55 + 0.45 * dot(n, float2(-0.6, -0.8)));
    color = color * (1.0 - rim.a * glow) + half4(rim) * glow;
    float aa = clamp(0.5 - d, 0.0, 1.0);
    return color * aa + shadow * (1.0 - aa);
}
"""

private const val VEC4 = 4
private const val HALF_HEIGHT = 3
private const val GROUP_SHADE = 0.16f
private const val MIN_BEND = 0.35f
private val GROUP_SHADOW = 12.dp
private val GROUP_BEND = 10.dp
private val GROUP_BAND = 14.dp
