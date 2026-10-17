package com.naury.kinetica.ui

import android.os.Build
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 화면 이동 전환 하나. [PushNavigation]과 [KTransitionContent]가 쓰며, 미리 만든 전환은 [KTransitions]에 있다.
 *
 * 전환은 진행률 하나로 두 화면의 모습을 정한다. 진행률은 0..1이고, 1이면 들어온 화면이 제자리에 있고 0이면 아직 들어오지 않아
 * 가려질 화면만 온전히 보인다. 뒤로 가기는 같은 진행률을 1에서 0으로 되돌리므로 예측형 뒤로 가기 제스처를 하면 전환이 손가락을 따라 되감긴다.
 * 두 함수는 그리기 단계에서 프레임마다 불리므로 객체를 새로 만들지 않고 [KTransitionFrame]의 값만 정한다.
 *
 * 직접 만들 때는 이 클래스를 상속해 [enter]와 [exit]를 구현한다. 가로 방향 값은 [KTransitionFrame.endSign]을 곱해야 RTL에서 거울처럼 뒤집힌다.
 */
@Immutable
public abstract class KScreenTransition {
    /** 들어오는 화면의 모습을 [frame]에 정한다. [progress]는 0..1이다. */
    public abstract fun enter(frame: KTransitionFrame, progress: Float)

    /** 들어오는 화면에 가려지는 화면의 모습을 [frame]에 정한다. [progress]는 0..1이며 0이면 아직 아무것도 바뀌지 않은 상태다. */
    public abstract fun exit(frame: KTransitionFrame, progress: Float)

    /** `false`면 가려지는 화면을 들어오는 화면 위에 그린다. 기존 화면이 비켜나며 새 화면을 드러내는 전환에 쓴다. */
    public open val enteringOnTop: Boolean get() = true

    /** 진행률을 0이나 1로 옮길 때 쓰는 애니메이션. 손가락이나 뒤로 가기 제스처를 놓은 뒤에도 쓴다. */
    public open val animationSpec: AnimationSpec<Float> get() = DefaultTransitionSpring
}

/** 전환 한 프레임에서 한 화면에 적용할 값. [KScreenTransition]이 정하고 SDK가 그 화면 레이어에 적용한다. 값은 프레임마다 처음 상태로 돌아간다. */
@Stable
public class KTransitionFrame internal constructor() {
    /** 화면 너비(px) */
    public var width: Float = 0f
        internal set

    /** 화면 높이(px) */
    public var height: Float = 0f
        internal set

    /** dp 하나의 px 크기 */
    public var density: Float = 1f
        internal set

    /** 끝 쪽이 오른쪽이면 1, RTL이라 왼쪽이면 -1이다. 가로로 움직이는 값에 곱한다. */
    public var endSign: Float = 1f
        internal set

    /** 가로 이동(px) */
    public var translationX: Float = 0f

    /** 세로 이동(px) */
    public var translationY: Float = 0f

    /** 가로 배율. 1이면 원래 크기다. */
    public var scaleX: Float = 1f

    /** 세로 배율. 1이면 원래 크기다. */
    public var scaleY: Float = 1f

    /** 가로축 회전(도) */
    public var rotationX: Float = 0f

    /** 세로축 회전(도) */
    public var rotationY: Float = 0f

    /** 화면 평면 안의 회전(도) */
    public var rotationZ: Float = 0f

    /** 불투명도. 0..1을 벗어나면 맞춘다. */
    public var alpha: Float = 1f

    /** 배율과 회전의 기준점. 화면 크기에 대한 비율(0..1)이다. */
    public var pivotX: Float = HALF

    /** 배율과 회전의 기준점. 화면 크기에 대한 비율(0..1)이다. */
    public var pivotY: Float = HALF

    /** 3D 회전에서 카메라까지의 거리(px). 0 이하면 기본값을 쓴다. */
    public var cameraDistance: Float = 0f

    /** 화면 위에 덮는 검은색의 진하기(0..1). 가려지는 화면을 어둡게 할 때 쓴다. */
    public var dim: Float = 0f

    /** 화면 모서리를 둥글게 자르는 반경(px). 0이면 자르지 않는다. */
    public var cornerRadius: Float = 0f

    /** 화면을 흐리게 하는 블러 반경(px). Android 12 미만에서는 무시한다. */
    public var blurRadius: Float = 0f

    /** 시작 쪽 가장자리에 드리우는 그림자의 진하기(0..1). 옆에서 밀려 들어오는 화면이 겹친 깊이를 보여 준다. */
    public var edgeShadow: Float = 0f

    internal var clipLeft = 0f
    internal var clipTop = 0f
    internal var clipRight = Float.NaN
    internal var clipBottom = Float.NaN
    internal var revealShape: KRevealShape? = null
    internal var revealCenter = Offset.Zero
    internal var revealRadius = 0f
    internal var revealRotation = 0f
    internal var screenOrigin = Offset.Zero

    // 그림자를 드리울 쪽. 1이면 왼쪽, -1이면 오른쪽 가장자리다(endSign과 같은 규칙).
    internal var shadowSide = 1f

    /** 화면을 사각형([left], [top], [right], [bottom], 화면 기준 px) 안만 보이게 자른다. 와이프 전환에 쓴다. */
    public fun clipTo(left: Float, top: Float, right: Float, bottom: Float) {
        clipLeft = left
        clipTop = top
        clipRight = right
        clipBottom = bottom
    }

    /**
     * 화면을 [center](화면 기준 px)에서 퍼지는 [shape] 모양 안만 보이게 자른다. [fraction]이 1이면 모양이 화면을 모두 덮고 0이면 아무것도 보이지 않는다.
     * [rotation]은 모양을 돌리는 각도(도)다.
     */
    public fun reveal(shape: KRevealShape, center: Offset, fraction: Float, rotation: Float = 0f) {
        revealShape = shape
        revealCenter = center
        revealRotation = rotation
        val far = maxOf(
            hypot(center.x, center.y),
            hypot(width - center.x, center.y),
            hypot(center.x, height - center.y),
            hypot(width - center.x, height - center.y),
        )
        revealRadius = far * shape.coverFactor * fraction.coerceIn(0f, 1f)
    }

    /** 화면 좌표(px)를 이 화면 기준 좌표로 바꾼다. [Modifier.kOrigin]으로 얻은 위치를 [reveal]에 넘길 때 쓴다. */
    public fun fromScreen(point: Offset): Offset = point - screenOrigin

    internal val clipsRect: Boolean get() = !clipRight.isNaN()

    internal fun reset(size: Size, density: Float, endSign: Float) {
        width = size.width
        height = size.height
        this.density = density
        this.endSign = endSign
        translationX = 0f
        translationY = 0f
        scaleX = 1f
        scaleY = 1f
        rotationX = 0f
        rotationY = 0f
        rotationZ = 0f
        alpha = 1f
        pivotX = HALF
        pivotY = HALF
        cameraDistance = 0f
        dim = 0f
        cornerRadius = 0f
        blurRadius = 0f
        edgeShadow = 0f
        shadowSide = endSign
        clipLeft = 0f
        clipTop = 0f
        clipRight = Float.NaN
        clipBottom = Float.NaN
        revealShape = null
    }
}

/** [KTransitionFrame.reveal]로 드러낼 모양. */
public enum class KRevealShape(internal val coverFactor: Float) {
    Circle(1f),

    /** 마름모. 화면을 덮으려면 원보다 √2배 커져야 한다. */
    Diamond(DIAMOND_COVER),

    /** 다섯 꼭짓점 별 */
    Star(STAR_COVER),
}

/** 화면이 들어오는 쪽. [Start]와 [End]는 RTL에서 좌우가 바뀐다. */
public enum class KTransitionEdge {
    End,
    Start,
    Top,
    Bottom,
}

/** 전환에서 화면이 맡은 역할 */
internal enum class TransitionRole { Entering, Exiting }

/** 화면 컨테이너가 전환 레이어에 알려 주는 값. 컨테이너 하나가 들어오는 화면과 가려지는 화면에 함께 넘긴다. */
@Stable
internal class TransitionHost {
    var endSign = 1f
    var screenOrigin = Offset.Zero
}

/**
 * [transition]을 [role] 화면에 적용한다. 진행률([progress])은 그리기 단계에서만 읽어 전환하는 동안 다시 구성하지 않는다.
 * 블러를 쓰는 전환만 렌더 효과를 붙이고, 나머지는 레이어 속성과 자르기만 쓴다.
 */
internal fun Modifier.screenTransition(
    transition: KScreenTransition,
    role: TransitionRole,
    host: TransitionHost,
    progress: () -> Float,
): Modifier {
    // 레이어 속성과 그리기는 따로 불리므로 각자 값을 계산할 프레임을 하나씩 둔다.
    val layer = TransitionLayer(transition, role, host)
    return graphicsLayer {
        applyFrame(layer.update(size, density, progress()))
    }.drawWithCache {
        val draw = TransitionLayer(transition, role, host)
        val path = Path()
        onDrawWithContent {
            val frame = draw.update(size, density, progress())
            if (frame.edgeShadow > 0f) drawEdgeShadow(frame.edgeShadow, frame.shadowSide)
            if (frame.clipsRect) {
                clipRect(frame.clipLeft, frame.clipTop, frame.clipRight, frame.clipBottom) {
                    drawMasked(this@onDrawWithContent, frame, path)
                }
            } else {
                drawMasked(this, frame, path)
            }
        }
    }
}

/** 한 화면의 전환 값을 계산하는 곳. 프레임 객체 하나를 계속 다시 쓴다. */
private class TransitionLayer(val transition: KScreenTransition, val role: TransitionRole, val host: TransitionHost) {
    private val frame = KTransitionFrame()

    fun update(size: Size, density: Float, progress: Float): KTransitionFrame {
        frame.reset(size, density, host.endSign)
        frame.screenOrigin = host.screenOrigin
        val p = progress.coerceIn(0f, 1f)
        if (role == TransitionRole.Entering) transition.enter(frame, p) else transition.exit(frame, p)
        return frame
    }
}

/** 둥근 모서리나 드러내기 모양으로 잘라 내용을 그리고, 그 위에 어둡게 덮는다. */
private fun DrawScope.drawMasked(content: ContentDrawScope, frame: KTransitionFrame, path: Path) {
    if (frame.maskPath(path)) {
        clipPath(path) { content.drawContentWithDim(frame) }
    } else {
        content.drawContentWithDim(frame)
    }
}

private fun ContentDrawScope.drawContentWithDim(frame: KTransitionFrame) {
    drawContent()
    if (frame.dim > 0f) drawRect(Color.Black.copy(alpha = frame.dim.coerceIn(0f, 1f)))
}

private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyFrame(frame: KTransitionFrame) {
    translationX = frame.translationX
    translationY = frame.translationY
    scaleX = frame.scaleX
    scaleY = frame.scaleY
    rotationX = frame.rotationX
    rotationY = frame.rotationY
    rotationZ = frame.rotationZ
    alpha = frame.alpha.coerceIn(0f, 1f)
    transformOrigin = TransformOrigin(frame.pivotX, frame.pivotY)
    if (frame.cameraDistance > 0f) cameraDistance = frame.cameraDistance
    val blur = frame.blurRadius
    renderEffect = if (blur > BLUR_EPSILON && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        BlurEffect(blur, blur, TileMode.Decal)
    } else {
        null
    }
}

/** 둥근 모서리나 드러내기 모양이 있으면 [path]에 경로를 만들고 `true`를 돌려준다. */
private fun KTransitionFrame.maskPath(path: Path): Boolean {
    val shape = revealShape
    if (shape == null && cornerRadius <= 0f) return false
    path.reset()
    if (shape == null) {
        path.addRoundRect(RoundRect(Rect(0f, 0f, width, height), CornerRadius(cornerRadius)))
        return true
    }
    when (shape) {
        KRevealShape.Circle -> path.addOval(Rect(revealCenter, revealRadius))
        KRevealShape.Diamond -> path.polygon(revealCenter, revealRadius, revealRadius, DIAMOND_POINTS, revealRotation)
        KRevealShape.Star -> path.polygon(revealCenter, revealRadius, revealRadius * STAR_INNER, STAR_POINTS, revealRotation)
    }
    return true
}

/** 꼭짓점 [points]개짜리 별 또는 다각형. 안쪽 반경이 바깥 반경과 같으면 정다각형이다. */
private fun Path.polygon(center: Offset, outer: Float, inner: Float, points: Int, rotationDegrees: Float) {
    val star = inner != outer
    val steps = if (star) points * 2 else points
    val start = -PI / 2 + rotationDegrees * PI / HALF_TURN_DEGREES
    for (i in 0 until steps) {
        val radius = if (star && i % 2 == 1) inner else outer
        val angle = start + 2 * PI * i / steps
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEdgeShadow(level: Float, side: Float) {
    val edge = EDGE_SHADOW.toPx()
    val x = if (side > 0f) -edge else size.width
    val colors = listOf(Color.Transparent, Color.Black.copy(alpha = SHADOW_ALPHA * level.coerceIn(0f, 1f)))
    val brush = Brush.horizontalGradient(if (side > 0f) colors else colors.reversed(), x, x + edge)
    drawRect(brush, Offset(x, 0f), Size(edge, size.height))
}

internal val DefaultTransitionSpring: AnimationSpec<Float> = spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessMediumLow)

private const val HALF = 0.5f
private const val BLUR_EPSILON = 0.5f
private const val DIAMOND_COVER = 1.42f
private const val STAR_COVER = 2.1f
private const val STAR_INNER = 0.5f
private const val STAR_POINTS = 5
private const val DIAMOND_POINTS = 4
private const val HALF_TURN_DEGREES = 180.0
private const val SHADOW_ALPHA = 0.22f
private val EDGE_SHADOW = 16.dp
