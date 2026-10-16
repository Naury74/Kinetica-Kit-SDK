package com.naury.kinetica.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize

/**
 * 함께 쓰는 블러 위에 여러 글래스의 굴절을 한 번에 거는 지도. 글래스마다 자리(가운데, 크기, 모서리, 세기)를 등록하면
 * 셰이더 하나가 블러 기록 전체를 한 번 훑으며 각 자리의 가장자리를 휜다. 글래스는 이 결과에서 자기 자리만 잘라 그린다.
 *
 * 글래스마다 굴절 레이어를 따로 두면 배경이 바뀌는 프레임마다 요소 수만큼 화면 밖 렌더 패스가 생긴다(Fold7 UI 키트 화면에서 약 20개,
 * 패스 135개, 15~27ms). 지도 하나면 배경이 바뀌어도 블러 한 번과 굴절 한 번뿐이다.
 *
 * 모든 좌표와 길이는 함께 쓰는 블러 기록의 px(줄인 배율)이다.
 */
internal class RefractionMap(private val layer: GraphicsLayer) {
    private val owners = arrayOfNulls<RefractionRegion>(MAX_REGIONS)
    private val boxes = FloatArray(MAX_REGIONS * VEC4) { EMPTY }
    private val shapes = FloatArray(MAX_REGIONS * VEC4)
    private var shader: RuntimeShader? = null
    private var broken = false
    private var released = false
    private var dirty = true
    private val scratch = FloatArray(VEC4 * 2)

    /** 지도를 그릴 기록 크기. 셰이더가 이 바깥을 읽지 않도록 가장자리에서 멈춘다. */
    var bounds: IntSize = IntSize.Zero
        set(value) {
            if (field != value) dirty = true
            field = value
        }

    /** 등록된 자리 수. 테스트에서 글래스가 지도를 함께 쓰는지 확인하는 데 쓴다. */
    val count: Int get() = owners.count { it != null }

    /** [region]에 빈 자리를 준다. 자리가 [MAX_REGIONS]개로 꽉 찼으면 false다. */
    fun attach(region: RefractionRegion): Boolean {
        if (released) return false
        val index = owners.indexOfFirst { it == null }
        if (index < 0) return false
        owners[index] = region
        region.index = index
        return true
    }

    fun detach(region: RefractionRegion) {
        val index = region.index
        if (index !in owners.indices || owners[index] !== region) return
        owners[index] = null
        boxes[index * VEC4 + 2] = EMPTY
        dirty = true
        flush()
    }

    /** [index] 자리의 가운데([x], [y]), 절반 크기, 모서리 반경, 굴절 세기, 굴절 띠 폭을 바꾼다. 같으면 아무것도 하지 않는다. */
    fun set(index: Int, x: Float, y: Float, region: RefractionRegion) {
        val next = scratch
        next[0] = x
        next[1] = y
        next[2] = region.width / 2f
        next[VEC4 - 1] = region.height / 2f
        next[VEC4] = region.radius
        next[VEC4 + 1] = region.strength
        next[VEC4 + 2] = region.band
        val at = index * VEC4
        for (k in 0 until VEC4) {
            if (boxes[at + k] != next[k] || shapes[at + k] != next[VEC4 + k]) dirty = true
            boxes[at + k] = next[k]
            shapes[at + k] = next[VEC4 + k]
        }
    }

    /**
     * 바뀐 자리가 있으면 굴절 효과를 새로 만들어 레이어에 건다. 셰이더 컴파일은 처음 한 번뿐이다.
     * 기기가 셰이더나 효과를 만들지 못하면 굴절 없이 블러만 비친다. 혼자 굴절을 걸 때도 같은 이유로 실패하므로 그쪽으로 돌리지 않는다.
     */
    fun flush() {
        val idle = !dirty || released || broken
        if (idle || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        dirty = false
        GlassStats.refractionBuilds++
        layer.renderEffect = runCatching {
            val shader = shader ?: RuntimeShader(MAP_SHADER).also { shader = it }
            shader.setFloatUniform("bounds", bounds.width.toFloat(), bounds.height.toFloat())
            shader.setFloatUniform("count", (owners.indexOfLast { it != null } + 1).toFloat())
            shader.setFloatUniform("boxes", boxes)
            shader.setFloatUniform("shapes", shapes)
            RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }.onFailure { broken = true }.getOrNull()
    }

    fun release() {
        released = true
        owners.forEach { it?.index = -1 }
        owners.fill(null)
    }
}

/**
 * 글래스 하나가 [RefractionMap]에 등록한 자리. 크기와 굴절 값은 그릴 때, 위치는 그릴 때와 레이어만 옮겨질 때 고친다.
 * 길이는 모두 지도 px다.
 */
internal class RefractionRegion {
    private var map: RefractionMap? = null
    var index: Int = -1
    var width: Float = 0f
        private set
    var height: Float = 0f
        private set
    var radius: Float = 0f
        private set
    var strength: Float = 0f
        private set
    var band: Float = 0f
        private set

    /** 이번 그리기에서 지도를 썼는지. 그리기가 끝났는데 쓰지 않았으면 자리를 놓는다. */
    var used: Boolean = false

    val bound: Boolean get() = map != null && index >= 0

    /** [target] 지도에 자리를 잡는다. 이미 그 지도에 있으면 그대로 쓴다. 자리가 없으면 false다. */
    fun bind(target: RefractionMap): Boolean {
        if (map === target && index >= 0) return true
        release()
        if (!target.attach(this)) return false
        map = target
        return true
    }

    /** 크기 [w]×[h], 모서리 [corner], 세기 [power]. 반경과 굴절 띠 폭은 혼자 굴절을 걸 때의 셰이더와 같은 식으로 정한다. */
    fun shape(w: Float, h: Float, corner: Float, power: Float) {
        width = w
        height = h
        radius = minOf(corner, minOf(w, h) / 2f)
        strength = power
        band = maxOf(minOf(w, h) * BAND, 1f)
    }

    /** 왼쪽 위가 지도의 ([left], [top])에 오도록 옮기고 지도에 바로 반영한다. */
    fun place(left: Float, top: Float) {
        val target = map ?: return
        if (index < 0) return
        target.set(index, left + width / 2f, top + height / 2f, this)
        target.flush()
    }

    fun release() {
        map?.detach(this)
        map = null
        index = -1
    }
}

/** 한 지도에 담는 굴절 자리 수. 넘치는 글래스는 혼자 굴절 레이어를 쓴다. */
internal const val MAX_REGIONS = 32
private const val VEC4 = 4
private const val EMPTY = -1f
private const val BAND = 0.22f

/**
 * 자리마다 둥근 사각형 안쪽 가장자리 띠에서 바깥쪽 픽셀을 끌어온다. 혼자 거는 굴절 셰이더(GlassBackdrop.kt)와 같은 식이다.
 * 자리가 겹치면 가장 작은 자리를 따른다. 카드 위 버튼처럼 겹친 글래스는 위에 놓인 작은 쪽이 보이기 때문이다.
 * 모양 바로 바깥 한 픽셀 반까지는 가장자리 값을 그대로 써서, 기록을 늘려 그릴 때 테두리에 굴절이 빠진 선이 생기지 않게 한다.
 */
internal const val MAP_SHADER = """
uniform shader content;
uniform float2 bounds;
uniform float count;
uniform float4 boxes[32];
uniform float4 shapes[32];

float roundRect(float2 p, float2 extent, float r) {
    float2 q = abs(p) - extent + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 coord) {
    float2 shift = float2(0.0);
    float best = 1e9;
    for (int i = 0; i < 32; i++) {
        if (float(i) >= count) break;
        float4 box = boxes[i];
        float2 p = coord - box.xy;
        if (abs(p.x) > box.z + 1.5 || abs(p.y) > box.w + 1.5) continue;
        float area = box.z * box.w;
        if (area >= best) continue;
        float4 s = shapes[i];
        float d = roundRect(p, box.zw, s.x);
        if (d > 1.5) continue;
        best = area;
        float edge = clamp(1.0 + d / s.z, 0.0, 1.0);
        float2 dir = normalize(p + float2(0.0001, 0.0001));
        shift = dir * s.y * edge * edge;
    }
    return content.eval(clamp(coord + shift, float2(0.5), bounds - 0.5));
}
"""
