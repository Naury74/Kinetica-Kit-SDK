// 파일 이름은 대표 API인 배경 컴포저블을(를) 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** [AuroraBackground]와 [StarfieldBackground]의 기본 색 */
public object KSkyDefaults {
    /** 초록에서 청록, 보라로 이어지는 오로라 띠 색 */
    public val AuroraColors: List<Color> = listOf(Color(0xFF3DF5A7), Color(0xFF2BD9E8), Color(0xFF9B6BFF))

    /** 오로라 뒤 밤하늘 */
    public val AuroraSky: Color = Color(0xFF050B1C)

    /** 별빛 뒤 밤하늘 */
    public val NightSky: Color = Color(0xFF04050D)
}

/**
 * 밤하늘에 커튼처럼 드리운 오로라 띠가 천천히 일렁이는 배경. 띠마다 세로 빛줄기가 아래쪽 가장자리에서 가장 밝고 위로 옅어진다.
 * 화면 밖으로 스크롤되거나 앱이 백그라운드로 가면 멈추고, '애니메이션 삭제' 설정이나 [speed]가 0이면 멈춘 그림으로 그린다.
 * 글래스 [KineticaScaffold]의 `background`에 두면 다이얼로그나 시트가 열려 있는 동안도 멈춘다.
 * 장식이라 TalkBack에 나타나지 않는다. 부모를 가득 채운다.
 *
 * ```kotlin
 * KineticaScaffold(background = { AuroraBackground() }) { padding -> … }
 * ```
 *
 * @param colors 띠 색. 띠 하나에 색 하나를 쓴다. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param speed 일렁이는 속도 배율. 음수면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun AuroraBackground(
    modifier: Modifier = Modifier,
    colors: List<Color> = KSkyDefaults.AuroraColors,
    sky: Color = KSkyDefaults.AuroraSky,
    speed: Float = 1f,
) {
    require(colors.isNotEmpty()) { "colors가 비어 있으면 안 됩니다." }
    require(speed >= 0f) { "speed는 0 이상이어야 합니다." }
    val clock = rememberLiveClock(speed > 0f)
    Spacer(
        modifier
            .fillMaxSize()
            .loopVisibility(clock)
            .drawWithCache {
                val bands = colors.mapIndexed { i, color -> AuroraBand(color, i) }
                val glow = Brush.verticalGradient(listOf(sky, lerpColor(sky, Color.Black, SKY_DARKEN)))
                onDrawBehind {
                    drawRect(glow)
                    val t = clock.seconds * speed + STILL_TIME
                    bands.forEach { it.draw(this, t) }
                }
            },
    )
}

/** 오로라 띠 하나. 세로 빛줄기 [RAYS]개를 같은 붓으로 높이만 바꿔 그린다. 붓은 띠마다 한 번 만든다. */
private class AuroraBand(color: Color, private val index: Int) {
    private val brush = Brush.verticalGradient(
        0f to Color.Transparent,
        RAY_GLOW_STOP to color.copy(alpha = RAY_SOFT),
        RAY_BRIGHT_STOP to color.copy(alpha = RAY_BRIGHT),
        1f to Color.Transparent,
        startY = 0f,
        endY = 1f,
    )

    fun draw(scope: DrawScope, t: Float) = with(scope) {
        val w = size.width
        val h = size.height
        val strip = w / RAYS
        val base = h * (BAND_TOP + index * BAND_STEP)
        val phase = index * BAND_PHASE
        for (i in 0..RAYS) {
            val x = i * strip
            val u = x / w
            val wave = sin(u * WAVE_K1 + t * WAVE_S1 + phase) * WAVE_A1 + sin(u * WAVE_K2 - t * WAVE_S2 + phase * 2) * WAVE_A2
            val top = base + wave * h
            val height = h * (RAY_HEIGHT + RAY_VARY * sin(u * RAY_K + t * RAY_S + phase))
            withTransform({
                translate(x, top)
                scale(1f, height, pivot = Offset.Zero)
            }) {
                drawRect(brush, Offset.Zero, Size(strip * STRIP_OVERLAP, 1f), blendMode = BlendMode.Plus)
            }
        }
    }
}

/**
 * 밤하늘에 별이 반짝이고 가끔 별똥별이 지나가는 배경. 큰 별일수록 조금 빨리 흘러 깊이감이 난다.
 * 화면 밖으로 스크롤되거나 앱이 백그라운드로 가면 멈추고, '애니메이션 삭제' 설정이나 [speed]가 0이면 별똥별 없이 멈춘 그림으로 그린다.
 * 글래스 [KineticaScaffold]의 `background`에 두면 다이얼로그나 시트가 열려 있는 동안도 멈춘다.
 * 장식이라 TalkBack에 나타나지 않는다. 부모를 가득 채운다.
 *
 * @param starCount 별 개수. 0보다 작으면 [IllegalArgumentException]을 던진다.
 * @param shootingStars 별똥별을 띄울지
 * @param speed 반짝임과 흐름의 속도 배율. 음수면 [IllegalArgumentException]을 던진다.
 */
@Composable
public fun StarfieldBackground(
    modifier: Modifier = Modifier,
    starCount: Int = 140,
    color: Color = Color.White,
    sky: Color = KSkyDefaults.NightSky,
    shootingStars: Boolean = true,
    speed: Float = 1f,
) {
    require(starCount >= 0) { "starCount는 0 이상이어야 합니다." }
    require(speed >= 0f) { "speed는 0 이상이어야 합니다." }
    val clock = rememberLiveClock(speed > 0f)
    val stars = remember(starCount) { Stars(starCount) }
    Spacer(
        modifier
            .fillMaxSize()
            .loopVisibility(clock)
            .drawWithCache {
                val shade = Brush.verticalGradient(listOf(lerpColor(sky, Color.Black, SKY_DARKEN), sky))
                val tail = Brush.horizontalGradient(listOf(color, Color.Transparent), startX = 0f, endX = 1f)
                onDrawBehind {
                    drawRect(shade)
                    val t = clock.seconds * speed
                    stars.draw(this, t, color)
                    if (shootingStars && clock.seconds > 0f) drawShootingStar(t, tail)
                }
            },
    )
}

/** 별 위치와 크기, 반짝임 위상. 같은 개수면 늘 같은 하늘이 나오도록 씨앗을 고정한다. */
private class Stars(count: Int) {
    private val x = FloatArray(count)
    private val y = FloatArray(count)
    private val r = FloatArray(count)
    private val phase = FloatArray(count)
    private val rate = FloatArray(count)

    init {
        val random = Random(SEED)
        for (i in 0 until count) {
            x[i] = random.nextFloat()
            y[i] = random.nextFloat()
            // 작은 별이 훨씬 많도록 제곱으로 치우친다.
            r[i] = STAR_MIN + (STAR_MAX - STAR_MIN) * random.nextFloat().let { it * it * it }
            phase[i] = random.nextFloat() * TWO_PI
            rate[i] = TWINKLE_MIN + random.nextFloat() * TWINKLE_RANGE
        }
    }

    fun draw(scope: DrawScope, t: Float, color: Color) = with(scope) {
        val unit = size.minDimension / UNIT
        for (i in x.indices) {
            val drift = (y[i] + t * DRIFT * r[i]) % 1f
            val twinkle = TWINKLE_BASE + (1f - TWINKLE_BASE) * (sin(t * rate[i] + phase[i]) * HALF + HALF)
            drawCircle(color.copy(alpha = color.alpha * twinkle), r[i] * unit, Offset(x[i] * size.width, drift * size.height))
        }
    }
}

/** [SHOOT_PERIOD]초마다 한 번, 앞의 [SHOOT_SHARE] 동안 오른쪽 위에서 왼쪽 아래로 별똥별이 긋고 지나간다. */
private fun DrawScope.drawShootingStar(t: Float, tail: Brush) {
    val cycle = t / SHOOT_PERIOD
    val k = cycle - cycle.toInt()
    if (k > SHOOT_SHARE) return
    val p = k / SHOOT_SHARE
    val lane = Random(cycle.toInt()).nextFloat()
    val start = Offset(size.width * (SHOOT_X0 + lane * SHOOT_X_SPREAD), size.height * lane * SHOOT_Y_SPREAD)
    val travel = size.minDimension * SHOOT_TRAVEL
    val head = Offset(start.x - travel * p, start.y + travel * p * SHOOT_SLOPE)
    val length = size.minDimension * SHOOT_LENGTH
    val fade = sin(p * PI.toFloat())
    withTransform({
        translate(head.x, head.y)
        rotate(SHOOT_ANGLE, Offset.Zero)
        scale(length, 1f, Offset.Zero)
    }) {
        drawLine(tail, Offset(0f, 0f), Offset(1f, 0f), strokeWidth = SHOOT_WIDTH, cap = StrokeCap.Round, alpha = fade)
    }
}

/**
 * 보이는 동안, 그리고 앱이 시작 상태(STARTED) 이상인 동안만 도는 시계. '애니메이션 삭제' 설정이면 돌지 않는다.
 * 글래스 스캐폴드 배경에서 모달이 열려 있는 동안도 멈춘다([shouldPauseBackground]).
 */
@Composable
internal fun rememberLiveClock(running: Boolean): LoopClock {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return rememberLoopClock(running && !reducedMotion() && !shouldPauseBackground() && state.isAtLeast(Lifecycle.State.STARTED))
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t)

private const val STILL_TIME = 7f
private const val SKY_DARKEN = 0.6f
private const val RAYS = 96
private const val STRIP_OVERLAP = 1.02f
private const val RAY_GLOW_STOP = 0.5f
private const val RAY_BRIGHT_STOP = 0.8f
private const val RAY_SOFT = 0.22f
private const val RAY_BRIGHT = 0.6f
private const val BAND_TOP = 0.12f
private const val BAND_STEP = 0.1f
private const val BAND_PHASE = 1.7f
private const val WAVE_K1 = 5.2f
private const val WAVE_K2 = 11f
private const val WAVE_S1 = 0.23f
private const val WAVE_S2 = 0.17f
private const val WAVE_A1 = 0.06f
private const val WAVE_A2 = 0.025f
private const val RAY_HEIGHT = 0.3f
private const val RAY_VARY = 0.1f
private const val RAY_K = 17f
private const val RAY_S = 0.6f
private const val SEED = 20_261_016
private const val STAR_MIN = 0.35f
private const val STAR_MAX = 1.9f
private const val UNIT = 360f
private const val TWO_PI = (2 * PI).toFloat()
private const val TWINKLE_MIN = 0.8f
private const val TWINKLE_RANGE = 2.4f
private const val TWINKLE_BASE = 0.35f
private const val HALF = 0.5f
private const val DRIFT = 0.004f
private const val SHOOT_PERIOD = 7f
private const val SHOOT_SHARE = 0.12f
private const val SHOOT_X0 = 0.55f
private const val SHOOT_X_SPREAD = 0.45f
private const val SHOOT_Y_SPREAD = 0.4f
private const val SHOOT_TRAVEL = 0.7f
private const val SHOOT_SLOPE = 0.55f
private const val SHOOT_LENGTH = 0.22f

// 머리는 왼쪽 아래로 움직이므로 꼬리는 그 반대인 오른쪽 위로 뻗는다.
private const val SHOOT_ANGLE = -28.8f
private const val SHOOT_WIDTH = 2.2f
