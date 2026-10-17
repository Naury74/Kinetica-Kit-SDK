package com.naury.kinetica.render

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.naury.kinetica.core.scene.SceneTransitionEdge
import com.naury.kinetica.core.scene.SceneTransitionKind
import com.naury.kinetica.core.scene.SceneTransitionSpec
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

/** 장면 전환에서 레이어가 맡는 쪽. */
public enum class SceneTransitionRole {
    /** 지금 보이다가 사라지는 장면 */
    Outgoing,

    /** 새로 들어오는 장면 */
    Incoming,
}

/**
 * 두 장면을 겹쳐 그릴 때 들어오는 장면을 위에 그려야 하면 `true`다. 앞으로 갈 때는 들어오는 장면이, 되돌아갈 때는 나가는 장면이 위에 있다.
 * [SceneTransitionKind.Uncover]는 반대다.
 */
public fun SceneTransitionSpec.incomingOnTop(forward: Boolean): Boolean = forward != (kind == SceneTransitionKind.Uncover)

/**
 * 이 요소를 장면 전환의 한쪽([role]) 레이어로 그린다. 같은 크기로 겹친 두 요소에 하나씩 붙이고, 그리는 순서는 [incomingOnTop]을 따른다.
 * [progress]는 easing을 적용한 진행도로, 0이면 나가는 장면만 보이고 1이면 들어오는 장면만 보인다. 그리기 단계에서 읽으므로 애니메이션 값을
 * 넘겨도 다시 구성하지 않는다.
 *
 * [forward]가 `false`면 같은 전환을 거꾸로 재생한 모습이 된다. 들어오는 장면이 반대쪽 가장자리에서 나오고, 앞으로 갈 때 덮었던 장면이 걷히는 식이다.
 * 그래서 예측형 뒤로 가기처럼 손가락을 따라 되감는 전환에 그대로 쓸 수 있다. [rtl]이면 [SceneTransitionEdge.Start]와 [SceneTransitionEdge.End]의
 * 좌우가 바뀐다. [SceneTransitionKind.Morph]는 노드를 이어야 하므로 이 수정자로는 [SceneTransitionKind.Fade]처럼 그린다.
 * [SceneTransitionKind.ZoomBlur]의 블러는 Android 12 이상에서만 보인다.
 */
public fun Modifier.sceneTransitionLayer(
    spec: SceneTransitionSpec,
    role: SceneTransitionRole,
    forward: Boolean = true,
    rtl: Boolean = false,
    progress: () -> Float,
): Modifier {
    val layer = TransitionLayer()
    val front = (role == SceneTransitionRole.Incoming) == forward
    fun update(width: Float, height: Float) {
        val p = progress()
        layer.compute(spec, front, if (forward) p else 1f - p, Size(width, height), rtl)
    }
    return graphicsLayer {
        update(size.width, size.height)
        translationX = layer.translationX
        translationY = layer.translationY
        scaleX = layer.scale
        scaleY = layer.scale
        alpha = layer.alpha.coerceIn(0f, 1f)
        rotationX = layer.rotationX
        rotationY = layer.rotationY
        rotationZ = layer.rotationZ
        transformOrigin = TransformOrigin(layer.pivotX, layer.pivotY)
        cameraDistance = CAMERA_DISTANCE
        // 블러 반경이 바뀔 때만 효과를 새로 만든다. ZoomBlur가 아니면 늘 0이라 만들지 않는다.
        val blur = layer.blurPx
        if (blur != layer.appliedBlur) {
            layer.appliedBlur = blur
            layer.blurEffect = if (blur > MIN_BLUR_PX) BlurEffect(blur, blur, TileMode.Decal) else null
        }
        renderEffect = layer.blurEffect
        compositingStrategy = if (layer.dissolve in 0f..<1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithCache {
        val circle = Path()
        onDrawWithContent {
            update(size.width, size.height)
            when (layer.clip) {
                CLIP_RECT -> clipRect(layer.clipLeft, layer.clipTop, layer.clipRight, layer.clipBottom) {
                    this@onDrawWithContent.drawContent()
                }

                CLIP_CIRCLE -> {
                    circle.reset()
                    circle.addOval(Rect(Offset(layer.clipLeft, layer.clipTop), layer.clipRight))
                    clipPath(circle) { this@onDrawWithContent.drawContent() }
                }

                else -> drawContent()
            }
            if (layer.dissolve in 0f..<1f) {
                drawImage(
                    DissolveNoise.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(DissolveNoise.SIZE, DissolveNoise.SIZE),
                    dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
                    colorFilter = DissolveNoise.mask(layer.dissolve),
                    blendMode = BlendMode.DstIn,
                    filterQuality = FilterQuality.Low,
                )
            }
            if (layer.dim > 0f) drawRect(Color.Black.copy(alpha = layer.dim.coerceIn(0f, 1f)))
        }
    }
}

/**
 * 한 레이어의 전환 값. 앞으로 가는 전환에서 위에 그리는 쪽(front, 들어오는 장면)과 아래에 그리는 쪽(back, 나가는 장면)을 계산한다.
 * 되돌아가는 전환은 역할을 바꾸고 진행도를 뒤집어 같은 값을 쓴다. 프레임마다 객체를 만들지 않도록 값을 고쳐 쓴다.
 */
internal class TransitionLayer {
    var translationX = 0f
    var translationY = 0f
    var scale = 1f
    var alpha = 1f
    var rotationX = 0f
    var rotationY = 0f
    var rotationZ = 0f
    var pivotX = HALF
    var pivotY = HALF
    var dim = 0f
    var blurPx = 0f
    var appliedBlur = 0f
    var blurEffect: RenderEffect? = null

    /** 0..1이면 그 진행도로 노이즈 마스크를 씌운다. 범위 밖이면 씌우지 않는다. */
    var dissolve = NO_DISSOLVE
    var clip = CLIP_NONE

    // 사각형이면 left, top, right, bottom이고, 원이면 중심 x, 중심 y, 반지름이다.
    var clipLeft = 0f
    var clipTop = 0f
    var clipRight = 0f
    var clipBottom = 0f

    private fun reset() {
        translationX = 0f
        translationY = 0f
        scale = 1f
        alpha = 1f
        rotationX = 0f
        rotationY = 0f
        rotationZ = 0f
        pivotX = HALF
        pivotY = HALF
        dim = 0f
        blurPx = 0f
        dissolve = NO_DISSOLVE
        clip = CLIP_NONE
    }

    /**
     * @param front 앞으로 가는 전환에서 위에 그리는 들어오는 장면이면 `true`
     * @param p 앞으로 가는 전환 기준 진행도
     */
    // 전환 종류마다 갈래가 하나씩인 분기다.
    @Suppress("CyclomaticComplexMethod")
    fun compute(spec: SceneTransitionSpec, front: Boolean, p: Float, size: Size, rtl: Boolean) {
        reset()
        val width = size.width
        val height = size.height
        val edge = Edge(spec.from, rtl)
        pivotX = spec.originX.toFloat()
        pivotY = spec.originY.toFloat()
        when (spec.kind) {
            SceneTransitionKind.None -> alpha = if (front == p >= HALF) 1f else 0f

            SceneTransitionKind.Morph, SceneTransitionKind.Fade -> alpha = if (front) p else 1f - p

            SceneTransitionKind.Dissolve -> if (front) dissolve = p.coerceIn(0f, 1f)

            SceneTransitionKind.Slide -> offset(edge, width, height, if (front) 1f - p else -p)

            SceneTransitionKind.Push -> pushLayer(edge, front, p, width, height)

            SceneTransitionKind.Cover -> if (front) offset(edge, width, height, 1f - p) else dim = DIM * p

            SceneTransitionKind.Uncover -> uncoverLayer(edge, front, p, width, height)

            SceneTransitionKind.Wipe -> if (front) wipe(edge, p, width, height)

            SceneTransitionKind.Iris -> if (front) iris(spec, p, width, height)

            SceneTransitionKind.Zoom -> zoom(front, p, ZOOM_IN_FROM, ZOOM_OUT_TO)

            SceneTransitionKind.ZoomBlur -> {
                zoom(front, p, ZOOM_BLUR_IN_FROM, ZOOM_BLUR_OUT_TO)
                blurPx = minOf(width, height) * BLUR_FRACTION * if (front) 1f - p else p
            }

            SceneTransitionKind.Rotate -> rotate(edge, front, p)

            SceneTransitionKind.Flip -> flip(edge, front, p)

            SceneTransitionKind.Cube -> cube(edge, front, p, width, height)
        }
    }

    private fun offset(edge: Edge, width: Float, height: Float, amount: Float) {
        translationX = edge.x * width * amount
        translationY = edge.y * height * amount
    }

    private fun pushLayer(edge: Edge, front: Boolean, p: Float, width: Float, height: Float) {
        if (front) {
            offset(edge, width, height, 1f - p)
        } else {
            offset(edge, width, height, -PARALLAX * p)
            dim = DIM * p
        }
    }

    private fun uncoverLayer(edge: Edge, front: Boolean, p: Float, width: Float, height: Float) {
        if (front) {
            offset(edge, width, height, PARALLAX * (1f - p))
            dim = DIM * (1f - p)
        } else {
            offset(edge, width, height, -p)
        }
    }

    // 들어오는 장면이 나오는 가장자리 쪽부터 드러난다.
    private fun wipe(edge: Edge, p: Float, width: Float, height: Float) {
        clip = CLIP_RECT
        val q = p.coerceIn(0f, 1f)
        clipLeft = if (edge.x > 0f) width * (1f - q) else 0f
        clipRight = if (edge.x < 0f) width * q else width
        clipTop = if (edge.y > 0f) height * (1f - q) else 0f
        clipBottom = if (edge.y < 0f) height * q else height
    }

    private fun iris(spec: SceneTransitionSpec, p: Float, width: Float, height: Float) {
        clip = CLIP_CIRCLE
        val cx = width * spec.originX.toFloat()
        val cy = height * spec.originY.toFloat()
        val far = max(max(hypot(cx, cy), hypot(width - cx, cy)), max(hypot(cx, height - cy), hypot(width - cx, height - cy)))
        clipLeft = cx
        clipTop = cy
        clipRight = far * p.coerceIn(0f, 1f)
    }

    private fun zoom(front: Boolean, p: Float, inFrom: Float, outTo: Float) {
        if (front) {
            scale = inFrom + (1f - inFrom) * p
            alpha = (p / ZOOM_FADE_PART).coerceIn(0f, 1f)
        } else {
            scale = 1f + (outTo - 1f) * p
            alpha = 1f - p
        }
    }

    private fun rotate(edge: Edge, front: Boolean, p: Float) {
        val sign = if (edge.x != 0f) edge.x else edge.y
        if (front) {
            rotationZ = sign * ROTATE_DEGREES * (1f - p)
            scale = ROTATE_SCALE + (1f - ROTATE_SCALE) * p
            alpha = p
        } else {
            rotationZ = -sign * ROTATE_DEGREES * p
            scale = 1f - (1f - ROTATE_SCALE) * p
            alpha = 1f - p
        }
    }

    // 절반까지는 나가는 장면이 90도까지 돌고, 그 뒤로는 들어오는 장면이 -90도에서 0도로 돈다. 보이지 않는 면은 알파 0으로 숨긴다.
    private fun flip(edge: Edge, front: Boolean, p: Float) {
        pivotX = HALF
        pivotY = HALF
        val angle = if (front) -HALF_TURN * (1f - p) else HALF_TURN * p
        val visible = if (front) p >= HALF else p < HALF
        if (edge.x != 0f) rotationY = edge.x * angle else rotationX = -edge.y * angle
        alpha = if (visible) 1f else 0f
        scale = 1f - FLIP_SHRINK * (1f - abs(1f - 2f * p))
    }

    // 정육면체 두 면이 맞닿은 모서리를 축으로 돈다. 나가는 면은 들어오는 장면 쪽 모서리를, 들어오는 면은 반대쪽 모서리를 축으로 삼는다.
    private fun cube(edge: Edge, front: Boolean, p: Float, width: Float, height: Float) {
        val amount = if (front) 1f - p else -p
        offset(edge, width, height, amount)
        val angle = QUARTER_TURN * amount
        if (edge.x != 0f) {
            pivotX = if ((edge.x > 0f) == front) 0f else 1f
            pivotY = HALF
            rotationY = edge.x * angle
        } else {
            pivotX = HALF
            pivotY = if ((edge.y > 0f) == front) 0f else 1f
            rotationX = -edge.y * angle
        }
        dim = CUBE_DIM * abs(amount)
    }

    /** 들어오는 장면이 나오는 쪽의 단위 방향. LTR에서 End는 오른쪽(x = 1)이다. */
    private class Edge(from: SceneTransitionEdge, rtl: Boolean) {
        val x: Float = when (from) {
            SceneTransitionEdge.End -> if (rtl) -1f else 1f
            SceneTransitionEdge.Start -> if (rtl) 1f else -1f
            else -> 0f
        }
        val y: Float = when (from) {
            SceneTransitionEdge.Top -> -1f
            SceneTransitionEdge.Bottom -> 1f
            else -> 0f
        }
    }
}

/** 디졸브에 쓰는 작은 노이즈 이미지. 크게 늘려 부드럽게 보간하므로 얼룩처럼 퍼지며 드러난다. */
private object DissolveNoise {
    const val SIZE = 48
    private const val SEED = 7
    private const val STEEPNESS = 6f
    private const val CHANNEL_MAX = 255f
    private const val ALPHA_ROW = 15
    private const val ALPHA_OFFSET = 19
    private const val LEVELS = 256

    val image: ImageBitmap by lazy {
        val random = Random(SEED)
        val pixels = IntArray(SIZE * SIZE) {
            val v = random.nextInt(LEVELS)
            android.graphics.Color.rgb(v, v, v)
        }
        val bitmap = android.graphics.Bitmap.createBitmap(pixels, SIZE, SIZE, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.asImageBitmap()
    }

    // 회색 값 n(0..1)이 진행도보다 작은 곳만 보이도록 알파를 STEEPNESS × (p' - n)으로 만든다. p' = p × (1 + 1/STEEPNESS)라서 0과 1에서 끝까지 덮는다.
    fun mask(p: Float): ColorFilter {
        val reach = p * (1f + 1f / STEEPNESS)
        val values = FloatArray(MATRIX_SIZE)
        values[ALPHA_ROW] = -STEEPNESS
        values[ALPHA_OFFSET] = STEEPNESS * reach * CHANNEL_MAX
        return ColorFilter.colorMatrix(ColorMatrix(values))
    }

    private const val MATRIX_SIZE = 20
}

private const val HALF = 0.5f
private const val NO_DISSOLVE = -1f
internal const val CLIP_NONE = 0
internal const val CLIP_RECT = 1
internal const val CLIP_CIRCLE = 2
private const val PARALLAX = 0.3f
private const val DIM = 0.2f
private const val CUBE_DIM = 0.35f
private const val ZOOM_IN_FROM = 0.85f
private const val ZOOM_OUT_TO = 1.1f
private const val ZOOM_BLUR_IN_FROM = 0.7f
private const val ZOOM_BLUR_OUT_TO = 1.35f
private const val ZOOM_FADE_PART = 0.6f
private const val BLUR_FRACTION = 0.04f
private const val MIN_BLUR_PX = 0.5f
private const val ROTATE_DEGREES = 25f
private const val ROTATE_SCALE = 0.8f
private const val HALF_TURN = 180f
private const val QUARTER_TURN = 90f
private const val FLIP_SHRINK = 0.12f
private const val CAMERA_DISTANCE = 16f
