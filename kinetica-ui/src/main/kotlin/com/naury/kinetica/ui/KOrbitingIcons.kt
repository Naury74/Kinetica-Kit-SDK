// 파일 이름은 대표 컴포저블인 KOrbitingIcons를 따른다.
@file:Suppress("MatchingDeclarationName")

package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * [KOrbitingIcons]의 궤도 하나.
 *
 * @param radius 가운데에서 궤도까지의 거리
 * @param periodMs 한 바퀴 도는 시간(ms). 0 이하면 [IllegalArgumentException]을 던진다.
 * @param reverse true면 시계 반대 방향으로 돈다.
 * @param showPath 궤도 선을 그릴지
 * @param icons 궤도를 도는 아이콘. 같은 간격으로 놓인다. 장식이라 TalkBack은 읽지 않는다.
 */
@Immutable
public class KOrbit(
    public val radius: Dp,
    public val periodMs: Int = 20_000,
    public val reverse: Boolean = false,
    public val showPath: Boolean = true,
    public val icons: List<@Composable () -> Unit>,
) {
    init {
        require(periodMs > 0) { "periodMs는 0보다 커야 합니다." }
    }
}

/**
 * 가운데 요소 둘레를 아이콘들이 궤도를 따라 도는 장식. 앱이 연동하는 서비스나 팀원 얼굴을 소개 화면에 보여 줄 때 쓴다.
 * 궤도마다 반지름과 빠르기, 방향을 따로 정한다. 아이콘은 돌면서도 똑바로 서 있다. 도는 위치는 배치 단계에서만 바꾸므로 도는 동안 다시 구성하지 않는다.
 * 화면 밖으로 스크롤되면 멈추고, '애니메이션 삭제' 설정이 켜져 있으면 처음 자리에 멈춰 있다. TalkBack은 궤도 아이콘을 읽지 않고 [center]만 읽는다.
 *
 * 크기를 정하지 않으면 가장 큰 궤도와 아이콘이 들어갈 만큼 차지한다.
 *
 * ```kotlin
 * KOrbitingIcons(
 *     listOf(KOrbit(64.dp, icons = small), KOrbit(120.dp, periodMs = 30_000, reverse = true, icons = large)),
 * ) { Logo() }
 * ```
 *
 * @param pathColor 궤도 선 색
 */
@Composable
public fun KOrbitingIcons(
    orbits: List<KOrbit>,
    modifier: Modifier = Modifier,
    pathColor: Color = LocalKineticaContentColor.current.copy(alpha = PATH_ALPHA),
    center: @Composable () -> Unit,
) {
    val still = reducedMotion()
    val clock = rememberLoopClock(running = !still && orbits.any { it.icons.isNotEmpty() })
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)) }
    Layout(
        contents = listOf(
            { Box { center() } },
            { orbits.forEach { orbit -> orbit.icons.forEach { icon -> Box(Modifier.clearAndSetSemantics { }) { icon() } } } },
        ),
        modifier = modifier
            .loopVisibility(clock)
            .drawWithCache {
                val stroke = Stroke(1.dp.toPx(), pathEffect = dash)
                onDrawBehind {
                    orbits.forEach {
                        if (it.showPath) drawCircle(pathColor, it.radius.toPx(), Offset(size.width / 2f, size.height / 2f), style = stroke)
                    }
                }
            },
    ) { (middle, icons), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val centerPlaceable = middle.first().measure(loose)
        val iconPlaceables = icons.map { it.measure(loose) }
        val largest = iconPlaceables.maxOfOrNull { maxOf(it.width, it.height) } ?: 0
        val reach = (orbits.maxOfOrNull { it.radius.toPx() } ?: 0f) * 2f + largest
        val side = ceil(maxOf(reach, centerPlaceable.width.toFloat(), centerPlaceable.height.toFloat())).toInt()
        val width = constraints.constrainWidth(side)
        val height = constraints.constrainHeight(side)
        layout(width, height) {
            val cx = width / 2f
            val cy = height / 2f
            centerPlaceable.place((cx - centerPlaceable.width / 2f).roundToInt(), (cy - centerPlaceable.height / 2f).roundToInt())
            // 시계는 여기서만 읽으므로 도는 동안 다시 구성하거나 다시 재지 않고 배치만 다시 한다.
            val seconds = if (still) 0f else clock.seconds
            var index = 0
            orbits.forEach { orbit ->
                val turn = seconds * MS_PER_SECOND / orbit.periodMs * TAU * if (orbit.reverse) -1f else 1f
                val radius = orbit.radius.toPx()
                orbit.icons.indices.forEach { k ->
                    val placeable = iconPlaceables[index++]
                    // 위에서 시작해야 궤도마다 첫 아이콘이 같은 줄에 서지 않고 고르게 흩어져 보인다.
                    val angle = turn + k * TAU / orbit.icons.size - QUARTER_TURN + orbit.radius.value * PHASE_PER_DP
                    placeable.place(
                        (cx + cos(angle) * radius - placeable.width / 2f).roundToInt(),
                        (cy + sin(angle) * radius - placeable.height / 2f).roundToInt(),
                    )
                }
            }
        }
    }
}

private const val TAU = (2 * PI).toFloat()
private const val QUARTER_TURN = (PI / 2).toFloat()
private const val PATH_ALPHA = 0.14f
private const val DASH = 6f
private const val PHASE_PER_DP = 0.013f
private const val MS_PER_SECOND = 1_000f
