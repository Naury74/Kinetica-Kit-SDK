package com.naury.kinetica.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** [ConfettiHost]에 색종이를 터뜨린다. 여러 번 불러도 겹쳐서 날린다. */
@Stable
public class ConfettiState {
    internal val pieces = mutableStateListOf<Piece>()
    internal var running by mutableLongStateOf(0L)

    /**
     * [origin](호스트 안 좌표, px)에서 [count]개를 위로 퍼지게 터뜨린다.
     * @param colors 색종이 색
     */
    public fun burst(origin: Offset, count: Int = DEFAULT_COUNT, colors: List<Color> = ConfettiColors) {
        require(count in 1..MAX_COUNT) { "count는 1..${MAX_COUNT}이어야 합니다." }
        repeat(count) {
            val angle = (-PI / 2 + (Random.nextDouble() - CENTERED) * SPREAD).toFloat()
            val speed = MIN_SPEED + Random.nextFloat() * SPEED_RANGE
            pieces += Piece(
                position = origin,
                velocity = Offset(cos(angle) * speed, sin(angle) * speed),
                color = colors[it % colors.size],
                spin = (Random.nextFloat() - 0.5f) * MAX_SPIN,
                flip = Random.nextFloat() * FLIP_RANGE + FLIP_MIN,
                size = Size(Random.nextFloat() * SIZE_RANGE + SIZE_MIN, (Random.nextFloat() * SIZE_RANGE + SIZE_MIN) * STRIP_RATIO),
            )
        }
        running++
    }

    internal class Piece(var position: Offset, var velocity: Offset, val color: Color, val spin: Float, val flip: Float, val size: Size) {
        var angle: Float = 0f
        var age: Float = 0f
    }
}

@Composable
public fun rememberConfettiState(): ConfettiState = remember { ConfettiState() }

/**
 * 색종이를 그리는 층. 화면 위에 덮어 두고 [ConfettiState.burst]로 터뜨린다. 손가락 입력은 받지 않는다.
 * 색종이는 중력·공기 저항을 받으며 떨어지고 뒤집히며 돈다. 애니메이션 삭제 설정이면 그리지 않는다.
 */
@Composable
public fun ConfettiHost(state: ConfettiState, modifier: Modifier = Modifier.fillMaxSize()) {
    val still = reducedMotion()
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.running) {
        if (still) {
            state.pieces.clear()
            return@LaunchedEffect
        }
        var last = withFrameNanos { it }
        while (state.pieces.isNotEmpty()) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / NANOS).coerceAtMost(MAX_STEP)
            last = now
            state.pieces.forEach { piece ->
                piece.velocity = Offset(piece.velocity.x * (1f - DRAG * dt), piece.velocity.y * (1f - DRAG * dt) + GRAVITY * dt)
                piece.position += piece.velocity * dt
                piece.angle += piece.spin * dt
                piece.age += dt
            }
            state.pieces.removeAll { it.age > LIFE_SECONDS }
            frame = now
        }
    }
    Canvas(modifier) {
        frame // 프레임마다 다시 그린다.
        state.pieces.forEach { piece ->
            val fade = (1f - (piece.age - FADE_FROM) / (LIFE_SECONDS - FADE_FROM)).coerceIn(0f, 1f)
            rotate(piece.angle, piece.position) {
                scale(1f, cos(piece.age * piece.flip), piece.position) {
                    drawRect(
                        piece.color.copy(alpha = fade),
                        topLeft = piece.position - Offset(piece.size.width / 2, piece.size.height / 2),
                        size = piece.size,
                    )
                }
            }
        }
    }
}

/** 기본 색종이 색. */
public val ConfettiColors: List<Color> = listOf(
    Color(0xFFFF5C8A),
    Color(0xFFFFC23D),
    Color(0xFF4ADE80),
    Color(0xFF38BDF8),
    Color(0xFFA78BFA),
    Color(0xFFFB923C),
)

private const val DEFAULT_COUNT = 80
private const val MAX_COUNT = 400
private const val SPREAD = PI * 0.9
private const val MIN_SPEED = 900f
private const val SPEED_RANGE = 1300f
private const val MAX_SPIN = 720f
private const val FLIP_MIN = 4f
private const val FLIP_RANGE = 10f
private const val SIZE_MIN = 14f
private const val SIZE_RANGE = 14f
private const val GRAVITY = 2600f
private const val DRAG = 1.6f
private const val NANOS = 1_000_000_000f
private const val MAX_STEP = 0.05f
private const val LIFE_SECONDS = 3.2f
private const val FADE_FROM = 2.2f
private const val STRIP_RATIO = 0.5f
private const val CENTERED = 0.5
