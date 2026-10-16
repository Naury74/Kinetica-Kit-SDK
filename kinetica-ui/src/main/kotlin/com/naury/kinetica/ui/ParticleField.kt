package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color

/**
 * [ParticleField]가 그리는 입자의 종류.
 *
 * @property defaultColors [ParticleField]에 색을 넘기지 않았을 때 쓰는 색
 */
public enum class ParticlePreset(public val defaultColors: List<Color>) {
    /** 크기가 다른 눈송이가 좌우로 흔들리며 내린다. 큰 눈송이일수록 빨리 떨어져 깊이감이 생긴다. */
    Snow(SNOW_COLORS),

    /** 폭죽이 올라가 터지며 불꽃이 중력에 끌려 떨어진다. [ParticleFieldState.launch]로 원하는 위치에서 터뜨릴 수 있다. */
    Fireworks(FIREWORK_COLORS),

    /** 반투명한 거품이 흔들리며 위로 떠오르다 사라진다. */
    Bubbles(BUBBLE_COLORS),

    /** 아래에서 불꽃이 튀어 올랐다가 호를 그리며 떨어진다. [ParticleFieldState.launch]로 한 자리에서 튀게 할 수 있다. */
    Sparks(SPARK_COLORS),
}

/**
 * [ParticleField]를 코드에서 움직이는 상태. 버튼을 눌렀을 때 그 위치에서 폭죽을 터뜨리는 데 쓴다.
 * 요청은 다음 프레임에 처리하며, 한 프레임에 쌓을 수 있는 요청은 8개까지다. 넘치는 요청은 버린다.
 */
@Stable
public class ParticleFieldState internal constructor(internal val autoLaunch: Boolean) {
    internal val queue = FloatArray(MAX_QUEUE * 2)
    internal var queued = 0

    /**
     * [at]에서 한 번 터뜨린다. [ParticlePreset.Fireworks]는 폭죽 하나가 터지고 [ParticlePreset.Sparks]는 불꽃이 사방으로 튄다.
     * 나머지 프리셋과 '애니메이션 삭제' 접근성 설정이 켜져 있을 때는 무시한다.
     *
     * @param at [ParticleField] 안 좌표(px). [Offset.Unspecified]면 위쪽 절반의 임의 위치에서 터뜨린다.
     */
    public fun launch(at: Offset = Offset.Unspecified) {
        if (queued >= MAX_QUEUE) return
        queue[queued * 2] = if (at.isSpecified) at.x else Float.NaN
        queue[queued * 2 + 1] = if (at.isSpecified) at.y else Float.NaN
        queued++
    }
}

/**
 * 컴포지션에 유지되는 [ParticleFieldState]를 만든다.
 *
 * @param autoLaunch [ParticlePreset.Fireworks]가 저절로 폭죽을 쏘아 올릴지. false면 [ParticleFieldState.launch]로 부를 때만 터진다.
 */
@Composable
public fun rememberParticleFieldState(autoLaunch: Boolean = true): ParticleFieldState =
    remember(autoLaunch) { ParticleFieldState(autoLaunch) }

/**
 * 배경에 까는 입자 애니메이션. 눈, 불꽃놀이, 거품, 튀는 불꽃 가운데 [preset]으로 고른다.
 * 입자는 처음에 고정 크기 배열로 만들어 두고 재사용하므로 프레임마다 객체를 만들지 않는다.
 * 요소가 창 밖에 있는 동안에는 멈추고, '애니메이션 삭제' 접근성 설정이 켜져 있으면 몇 초 진행된 모습을 멈춘 그림으로 보여 준다.
 * 글래스 [KineticaScaffold]의 `background`에 두면 다이얼로그나 시트가 열려 있는 동안도 멈춘다.
 *
 * 부모를 가득 채우며 터치는 받지 않는다. 장식이므로 접근성 트리에 나타나지 않는다.
 *
 * ```kotlin
 * Box { MeshGradientBackground(palette = MeshGradients.Midnight); ParticleField(ParticlePreset.Snow) }
 * ```
 *
 * @param density 입자 수 배율(0.1..4). 1이면 기본 수다. [ParticlePreset.Fireworks]에서는 폭죽을 쏘는 빈도가 바뀐다.
 *   범위를 벗어나면 [IllegalArgumentException]을 던진다.
 * @param colors 입자 색. 입자마다 이 가운데 하나를 쓴다. 비어 있으면 [IllegalArgumentException]을 던진다.
 * @param state 코드에서 터뜨릴 때 쓰는 상태
 */
@Composable
public fun ParticleField(
    preset: ParticlePreset,
    modifier: Modifier = Modifier,
    density: Float = 1f,
    colors: List<Color> = preset.defaultColors,
    state: ParticleFieldState = rememberParticleFieldState(),
) {
    require(density in MIN_DENSITY..MAX_DENSITY) { "density는 0.1..4여야 합니다." }
    require(colors.isNotEmpty()) { "colors가 비어 있습니다." }
    val still = reducedMotion()
    val clock = rememberLoopClock(running = !still && !shouldPauseBackground())
    val engine = remember(preset, density, colors) { particleSystem(preset, density, colors) }
    Spacer(
        modifier
            .fillMaxSize()
            .loopVisibility(clock)
            .drawBehind { engine.frame(this, if (still) Float.NaN else clock.seconds, state) },
    )
}

private val SNOW_COLORS = listOf(Color.White, Color(0xFFE3EEFF))
private val FIREWORK_COLORS = listOf(
    Color(0xFFFF5E7E),
    Color(0xFFFFD166),
    Color(0xFF6EE7FF),
    Color(0xFFB794FF),
    Color(0xFF7CFFB2),
    Color(0xFFFFF4E0),
)
private val BUBBLE_COLORS = listOf(Color(0xFFBDE7FF), Color.White, Color(0xFFD9C8FF))
private val SPARK_COLORS = listOf(Color(0xFFFFE08A), Color(0xFFFFA63C), Color(0xFFFF6A3D))
private const val MAX_QUEUE = 8
private const val MIN_DENSITY = 0.1f
private const val MAX_DENSITY = 4f
