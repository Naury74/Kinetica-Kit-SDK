// 파일 이름은 대표 API인 rememberImagePalette을(를) 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 이미지에서 뽑은 대표색. [imagePaletteOf]나 [rememberImagePalette]로 얻는다.
 *
 * @property dominant 가장 넓게 쓰인 색
 * @property vibrant 넓게 쓰인 색 가운데 가장 선명한 색. 강조 장식에 쓴다.
 * @property muted 채도가 낮은 차분한 색. 넓은 바탕에 쓴다.
 * @property dark [dominant]를 어둡게 한 색. 흰 글자를 올릴 바탕에 쓴다.
 */
@Immutable
public data class KImagePalette(val dominant: Color, val vibrant: Color, val muted: Color, val dark: Color) {
    /** [dominant] 위에 올릴 글자 색. 흰색이나 검정 가운데 대비가 큰 쪽이다. */
    val onDominant: Color get() = if (dominant.luminance() > ON_LIGHT_LUMINANCE) Color.Black else Color.White
}

/**
 * [image]의 대표색을 계산한다. 32×32로 줄인 뒤 색을 칸으로 나눠 세므로 큰 이미지도 몇 밀리초면 끝나지만, 메인 스레드에서는 부르지 않는다.
 * 거의 투명한 픽셀은 세지 않는다. 모두 투명하면 회색 팔레트를 돌려준다.
 */
public fun imagePaletteOf(image: ImageBitmap): KImagePalette {
    val source = image.asAndroidBitmap()
    // 하드웨어 비트맵은 픽셀을 읽을 수 없어 소프트웨어 비트맵으로 옮긴다.
    val readable = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
    val small = Bitmap.createScaledBitmap(readable, SAMPLE, SAMPLE, true)
    val pixels = IntArray(SAMPLE * SAMPLE)
    small.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
    return paletteOf(pixels)
}

/** 색 칸별 개수와 평균 색으로 팔레트를 고른다. 칸은 RGB 각 4비트다. */
internal fun paletteOf(pixels: IntArray): KImagePalette {
    val count = IntArray(BUCKETS)
    val sums = Array(BUCKETS) { FloatArray(RGB) }
    for (argb in pixels) {
        if ((argb ushr ALPHA_SHIFT) < MIN_ALPHA) continue
        val r = (argb shr RED_SHIFT) and BYTE
        val g = (argb shr GREEN_SHIFT) and BYTE
        val b = argb and BYTE
        val bucket = ((r shr BUCKET_SHIFT) shl RED_BUCKET) or ((g shr BUCKET_SHIFT) shl GREEN_BUCKET) or (b shr BUCKET_SHIFT)
        count[bucket]++
        sums[bucket][0] += r.toFloat()
        sums[bucket][1] += g.toFloat()
        sums[bucket][2] += b.toFloat()
    }
    val used = count.indices.filter { count[it] > 0 }
    if (used.isEmpty()) return KImagePalette(Color.Gray, Color.Gray, Color.Gray, Color.DarkGray)
    val colors = used.associateWith { i -> Color(sums[i][0] / count[i] / BYTE, sums[i][1] / count[i] / BYTE, sums[i][2] / count[i] / BYTE) }
    val dominant = colors.getValue(used.maxBy { count[it] })
    val vibrant = colors.getValue(
        used.maxBy { i ->
            colors.getValue(i).let { saturation(it) * vividWeight(it) } *
                kotlin.math.sqrt(count[i].toFloat())
        },
    )
    val muted = colors.getValue(
        used.maxBy { i ->
            colors.getValue(i).let { (1f - saturation(it)) * mutedWeight(it) } *
                kotlin.math.sqrt(count[i].toFloat())
        },
    )
    return KImagePalette(dominant, vibrant, muted, lerp(dominant, Color.Black, DARKEN))
}

private fun saturation(c: Color): Float {
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    return if (max <= 0f) 0f else (max - min) / max
}

/** 너무 어둡거나 너무 밝은 색은 선명해도 강조색으로 쓰기 어려워 덜 친다. */
private fun vividWeight(c: Color): Float = c.luminance().let { if (it in VIVID_MIN..VIVID_MAX) 1f else LOW_WEIGHT }

private fun mutedWeight(c: Color): Float = c.luminance().let { if (it in MUTED_MIN..MUTED_MAX) 1f else LOW_WEIGHT }

/**
 * [image]의 대표색을 백그라운드 스레드에서 계산해 돌려준다. 계산이 끝나기 전과 [image]가 null이면 null이다. [image]가 바뀌면 다시 계산한다.
 *
 * ```kotlin
 * val palette = rememberImagePalette(cover)
 * PaletteBackground(palette)
 * ```
 */
@Composable
public fun rememberImagePalette(image: ImageBitmap?): KImagePalette? {
    var palette by remember(image) { mutableStateOf<KImagePalette?>(null) }
    LaunchedEffect(image) {
        if (image != null) palette = withContext(Dispatchers.Default) { imagePaletteOf(image) }
    }
    return palette
}

/**
 * 음악 앱의 지금 재생 화면처럼 [palette] 색으로 물든 배경. 어두운 바탕 위에 선명한 색과 차분한 색이 위아래에서 번진다.
 * [palette]가 바뀌면 색이 부드럽게 넘어가고, '애니메이션 삭제' 설정에서는 바로 바뀐다. null이면 [fallback] 색으로 칠한다.
 * 반복 애니메이션이 없어 화면 밖이나 백그라운드에서 그리기 비용이 들지 않는다. 장식이라 TalkBack에 나타나지 않는다.
 *
 * @param fallback 팔레트가 아직 없을 때 쓰는 색
 */
@Composable
public fun PaletteBackground(palette: KImagePalette?, modifier: Modifier = Modifier, fallback: Color = Color(FALLBACK)) {
    val still = reducedMotion()
    val spec = if (still) snap<Color>() else tween(BLEND_MS)
    val top by animateColorAsState(palette?.vibrant ?: fallback, spec, label = "palette-top")
    val bottom by animateColorAsState(palette?.muted ?: fallback, spec, label = "palette-bottom")
    val base by animateColorAsState(palette?.dark ?: fallback, spec, label = "palette-base")
    Spacer(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(lerp(base, top, BASE_MIX), base, lerp(base, Color.Black, BOTTOM_DARK))))
                val r = size.maxDimension * GLOW
                drawRect(Brush.radialGradient(listOf(top.copy(alpha = TOP_GLOW), Color.Transparent), Offset(size.width * TOP_X, 0f), r))
                drawRect(
                    Brush.radialGradient(
                        listOf(bottom.copy(alpha = BOTTOM_GLOW), Color.Transparent),
                        Offset(size.width * BOTTOM_X, size.height),
                        r,
                    ),
                )
            },
    )
}

private const val SAMPLE = 32
private const val BUCKETS = 4096
private const val RGB = 3
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BYTE = 255
private const val BUCKET_SHIFT = 4
private const val RED_BUCKET = 8
private const val GREEN_BUCKET = 4
private const val MIN_ALPHA = 128
private const val DARKEN = 0.6f
private const val ON_LIGHT_LUMINANCE = 0.5f
private const val VIVID_MIN = 0.08f
private const val VIVID_MAX = 0.75f
private const val MUTED_MIN = 0.1f
private const val MUTED_MAX = 0.6f
private const val LOW_WEIGHT = 0.25f
private const val FALLBACK = 0xFF1C1C24
private const val BLEND_MS = 700
private const val BASE_MIX = 0.35f
private const val BOTTOM_DARK = 0.5f
private const val GLOW = 0.75f
private const val TOP_GLOW = 0.55f
private const val BOTTOM_GLOW = 0.4f
private const val TOP_X = 0.2f
private const val BOTTOM_X = 0.85f
