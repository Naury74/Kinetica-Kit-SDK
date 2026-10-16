package com.naury.kinetica.render

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.NodeContent
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.NodeRole
import com.naury.kinetica.core.scene.NodeStyle
import com.naury.kinetica.core.scene.PathCommand
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode
import com.naury.kinetica.core.scene.Shadow
import com.naury.kinetica.core.scene.TextAlignment

/**
 * 호스트가 [com.naury.kinetica.core.scene.NodeContent.HostSlot]의 키마다 제공하는 Composable. 키가 없으면 플레이스홀더를 그린다.
 * 내용은 노드 상자 크기에 맞춰 배치되고, 호스트 데이터가 바뀌면 바로 다시 그려진다. 안쪽의 dp와 sp는 기기 화면 밀도가 아니라
 * [HOST_VIEW_UNITS_PER_DP]로 문서 단위에 맞춰지므로, 기기나 확대 배율이 달라도 상자에 대한 비율은 같다.
 */
public typealias SceneSlots = Map<String, @Composable () -> Unit>

/** 호스트 뷰 안에서 1dp가 차지하는 문서 단위. 폭 1080 artboard가 폭 약 393dp 휴대폰 화면에 대응한다. */
public const val HOST_VIEW_UNITS_PER_DP: Float = 2.75f

/**
 * 문서의 노드마다 실제 Compose 요소를 만들어 그린다(docs 30). 호스트 뷰가 실제로 동작하고, 그룹 불투명도와 Frame 클리핑은 레이어로 합성된다.
 *
 * - 노드 배치는 문서 변환과 같다: `graphicsLayer(translation, rotation, scale, transformOrigin = pivot)`.
 * - 문서 단위 1은 픽셀 1(배율 1)이다. [viewport]가 전체를 이동하고 확대한다.
 * - [clipToArtboard]가 `true`면 artboard 밖은 그리지 않는다. 재생기에서 쓴다. 편집기는 artboard 밖에 둔 컴포넌트 원본도 보여야 하므로 `false`를 쓴다.
 * - [interactive]가 `true`면 동작([SceneNode.action])이 있는 노드를 눌렀을 때 [onAction]을 호출하고, 노드의 모션 효과([SceneNode.effects])를
 *   재생한다. 편집 모드처럼 `false`면 누르기를 처리하지 않고 효과도 재생하지 않는다.
 * - [onPressChange]는 동작이 있는 컴포넌트 인스턴스를 누르기 시작할 때 `true`로, 손을 떼거나 취소될 때 `false`로 그 노드 id와 함께 호출된다.
 *   재생기는 이 콜백으로 누르는 동안 pressed 변형을 보여 준다.
 * - [hiddenSlots]는 숨긴 노드(`visible = false`이거나 불투명도 0, 또는 그런 조상 아래) 안의 호스트 뷰를 어떻게 다룰지 정한다.
 *   기본값 [HiddenSlotPolicy.Keep]이면 다시 보일 때 스크롤 위치나 입력 중인 글자가 그대로 남는다.
 *
 * 바인딩과 애니메이션은 이 함수 밖에서 적용한 뒤 그 문서를 넘긴다.
 */
@Composable
public fun SceneLayout(
    document: SceneDocument,
    viewport: Viewport,
    modifier: Modifier = Modifier,
    images: SceneImageProvider = SceneImageProvider.None,
    slots: SceneSlots = emptyMap(),
    interactive: Boolean = false,
    onAction: (String) -> Unit = {},
    clipToArtboard: Boolean = false,
    onPressChange: (NodeId, Boolean) -> Unit = { _, _ -> },
    hiddenSlots: HiddenSlotPolicy = HiddenSlotPolicy.Keep,
) {
    // 드래그와 애니메이션 중 성능을 위해 스코프는 같은 객체로 두고 문서와 동작은 상태로 읽는다. 바뀌지 않은 노드는 인자가 같아서 재구성되지 않는다.
    val documentState = rememberUpdatedState(document)
    val callbacks = rememberUpdatedState(Callbacks(if (interactive) onAction else null, onPressChange))
    val scope = remember(images, slots, interactive, hiddenSlots) {
        NodeScope(documentState, images, slots, callbacks, effects = interactive, keepHidden = hiddenSlots == HiddenSlotPolicy.Keep)
    }
    // 장면 좌표는 왼쪽 위 기준의 절대 좌표라 RTL 화면에서도 뒤집지 않는다. 뒤집으면 장면이 영역 밖으로 밀려 보이지 않는다.
    // 호스트 슬롯 안은 호스트의 레이아웃 방향을 그대로 쓴다.
    // 바깥 Box는 호스트 방향으로 배치되므로 정렬도 방향과 무관한 왼쪽 위로 둔다. artboard 배경이 없으면 장면 크기가 0이라
    // TopStart면 RTL에서 오른쪽 끝(영역 밖)에 놓인다.
    val hostDirection = LocalLayoutDirection.current
    Box(modifier.clipToBounds(), contentAlignment = AbsoluteAlignment.TopLeft) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalHostLayoutDirection provides hostDirection) {
            SceneViewport(document, viewport, scope, clipToArtboard)
        }
    }
}

/** 호스트 슬롯 안에 되돌려 줄 호스트의 레이아웃 방향. 장면 안은 항상 LTR로 그린다. */
private val LocalHostLayoutDirection = staticCompositionLocalOf { LayoutDirection.Ltr }

@Composable
private fun SceneViewport(document: SceneDocument, viewport: Viewport, scope: NodeScope, clipToArtboard: Boolean) {
    Box(
        // artboard가 영역보다 크면 Compose가 가운데 정렬로 밀어내므로 제약 없이 측정해 왼쪽 위에 둔다. 실제 위치는 viewport가 정한다.
        Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = viewport.panX.toFloat()
            translationY = viewport.panY.toFloat()
            scaleX = viewport.zoom.toFloat()
            scaleY = viewport.zoom.toFloat()
        },
    ) {
        if (clipToArtboard) {
            Box(Modifier.requiredSizePx(Size(document.artboard.width, document.artboard.height)).clipToBounds()) {
                Artboard(document)
                Children(document.rootOrder, scope)
            }
        } else {
            Artboard(document)
            Children(document.rootOrder, scope)
        }
    }
}

private class Callbacks(val onAction: ((String) -> Unit)?, val onPressChange: (NodeId, Boolean) -> Unit)

private class NodeScope(
    private val documentState: State<SceneDocument>,
    val images: SceneImageProvider,
    val slots: SceneSlots,
    private val callbacks: State<Callbacks>,
    val effects: Boolean,
    private val keepHidden: Boolean,
) {
    fun pressChanged(id: NodeId, pressed: Boolean) = callbacks.value.onPressChange(id, pressed)

    /** 사용 모드. 누르면 동작을 실행하고 효과를 재생한다. */
    val interactive: Boolean get() = effects

    val document: SceneDocument get() = documentState.value
    val onAction: ((String) -> Unit)? get() = callbacks.value.onAction

    /** 숨긴 [node]를 컴포지션에 남겨야 하는지. 자손까지 보아 실제 호스트 뷰가 있을 때만 남긴다. */
    fun keepsHidden(node: SceneNode): Boolean = keepHidden && hasHostView(node)

    private fun hasHostView(node: SceneNode): Boolean = when (val content = node.content) {
        is NodeContent.HostSlot -> content.key in slots
        is NodeContent.Container -> content.childIds.any { id -> document.nodes[id]?.let(::hasHostView) == true }
        else -> false
    }
}

/** 숨긴 노드 안의 호스트 뷰([com.naury.kinetica.core.scene.NodeContent.HostSlot])를 어떻게 다룰지. */
public enum class HiddenSlotPolicy {
    /**
     * 컴포지션에 남겨 상태를 유지한다. 측정은 계속하지만 배치하지 않으므로 그려지지 않고, 터치와 포커스를 받지 않으며,
     * 스크린 리더에도 나오지 않는다. 숨길 때 안쪽에 포커스가 있으면 포커스를 해제한다.
     */
    Keep,

    /** 컴포지션에서 뺀다. 숨긴 동안 비용이 들지 않지만 다시 보이면 호스트 뷰의 상태가 처음부터 시작된다. */
    Dispose,
}

/** 숨긴 노드 표시. [Children]은 이 표시가 있는 자식을 측정만 하고 배치하지 않는다. */
private object HiddenNode

@Composable
private fun Artboard(document: SceneDocument) {
    val background = document.artboard.background ?: return
    Box(Modifier.requiredSizePx(Size(document.artboard.width, document.artboard.height)).background(background.toColor()))
}

/** 자식을 각자의 크기로 측정해 원점에 놓는다. 위치는 자식의 graphicsLayer가 정한다. 컨테이너 크기는 [size]이고, 없으면 0이다. */
@Composable
private fun Children(ids: List<NodeId>, scope: NodeScope, size: Size? = null, hidden: Boolean = false) {
    // 노드 id로 식별해야 순서가 바뀌어도 호스트 뷰의 상태(입력 중인 텍스트, 스크롤 위치 등)가 다른 노드로 넘어가거나 사라지지 않는다.
    Layout(content = {
        ids.forEach { id -> key(id) { scope.document.nodes[id]?.let { NodeView(it, scope, hidden) } } }
    }) { measurables, _ ->
        // 숨긴 노드도 측정해 두어야 다시 보일 때 크기를 새로 잡느라 한 프레임 비는 일이 없다.
        val placeables = measurables.map { it.measure(Constraints()) to (it.layoutId === HiddenNode) }
        layout(size?.width?.toInt() ?: 0, size?.height?.toInt() ?: 0) {
            placeables.forEach { (placeable, hiddenChild) -> if (!hiddenChild) placeable.place(0, 0) }
        }
    }
}

@Composable
private fun NodeView(node: SceneNode, scope: NodeScope, parentHidden: Boolean) {
    val hidden = parentHidden || !node.visible || node.style.opacity <= 0.0
    if (hidden && !scope.keepsHidden(node)) return
    // 숨김 여부와 상관없이 같은 Composable을 같은 순서로 불러야 숨겼다 다시 보일 때 호스트 뷰의 상태가 이어진다. 바뀌는 것은 Modifier뿐이다.
    val placed = Modifier.placement(node)
        .effects(node, scope.effects && !hidden)
        .layerBlur(node.style.blur)
        .then(if (hidden) Modifier.layoutId(HiddenNode).clearAndSetSemantics {} else Modifier.actionable(node, scope).accessibility(node))
    when (val content = node.content) {
        is NodeContent.Group -> Box(placed) { Children(content.childIds, scope, hidden = hidden) }
        is NodeContent.Frame -> FrameView(node, content, placed, scope, hidden)
        is NodeContent.Rectangle, is NodeContent.Ellipse, is NodeContent.Polygon, is NodeContent.Path -> ShapeView(node, content, placed)
        is NodeContent.Text -> TextView(node, content, placed)
        is NodeContent.Image -> ImageView(content, placed, scope)
        is NodeContent.HostSlot -> HostView(node, content, placed, scope, hidden)
    }
}

/** 노드 모양을 따라 리플과 글로우를 그린다. 장면 안이므로 거리 1단위는 1px(문서 단위)이다. */
@Composable
private fun Modifier.effects(node: SceneNode, enabled: Boolean): Modifier {
    if (!enabled || node.effects.isEmpty) return this
    val shape = remember(node.content) { node.content.outline()?.let { path -> GenericShape { _, _ -> addPath(path) } } ?: RectangleShape }
    return nodeEffects(node.effects, shape, enabled = true, unitPx = 1f)
}

/** 문서 변환과 같은 배치. translation을 적용한 뒤 pivot 기준으로 회전하고 배율을 적용한다. 불투명도는 레이어로 합성한다. */
private fun Modifier.placement(node: SceneNode): Modifier {
    val bounds = node.localBounds
    val transform = node.transform
    return graphicsLayer {
        val width = bounds?.width?.takeIf { it > 0.0 } ?: 1.0
        val height = bounds?.height?.takeIf { it > 0.0 } ?: 1.0
        transformOrigin = TransformOrigin((transform.pivotX / width).toFloat(), (transform.pivotY / height).toFloat())
        translationX = transform.translationX.toFloat()
        translationY = transform.translationY.toFloat()
        rotationZ = transform.rotationDegrees.toFloat()
        scaleX = transform.scaleX.toFloat()
        scaleY = transform.scaleY.toFloat()
        alpha = node.style.opacity.toFloat()
    }
}

private fun Modifier.actionable(node: SceneNode, scope: NodeScope): Modifier {
    val action = node.action ?: return this
    // 동작 콜백은 누를 때 읽는다. 컴포지션 중에 읽으면 호스트가 넘긴 람다가 바뀔 때마다 동작이 있는 노드가 모두 재구성된다.
    if (!scope.interactive) return this
    val role = if (node.accessibility.role == null || node.accessibility.role == NodeRole.Button) Role.Button else null
    val clickable = clickable(role = role) { scope.onAction?.invoke(action) }
    if (node.instanceOf == null) return clickable
    // 누르는 상태만 알린다. 포인터 이벤트는 관찰만 하고 소비하지 않으므로 누르기와 스크롤은 평소대로 동작한다.
    return clickable.pointerInput(node.id) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            scope.pressChanged(node.id, true)
            try {
                waitForUpOrCancellation()
            } finally {
                scope.pressChanged(node.id, false)
            }
        }
    }
}

/** 스크린 리더용 접근성 정보([SceneNode.accessibility]). 장식 노드면 이 노드와 자손을 읽지 않는다. */
private fun Modifier.accessibility(node: SceneNode): Modifier {
    val info = node.accessibility
    if (info.hidden) return clearAndSetSemantics {}
    if (info.label == null && info.role == null) return this
    return semantics(mergeDescendants = info.label != null) {
        info.label?.let { contentDescription = it }
        when (info.role) {
            NodeRole.Image -> role = Role.Image
            NodeRole.Heading -> heading()
            NodeRole.Button -> role = Role.Button
            null -> Unit
        }
    }
}

@Composable
private fun FrameView(node: SceneNode, content: NodeContent.Frame, modifier: Modifier, scope: NodeScope, hidden: Boolean) {
    val outline = remember(content) { requireNotNull(content.outline()) }
    // 그림자가 잘리지 않도록 그림자와 채우기는 클리핑 전에 그리고, 선은 자식 위에 그린다.
    val clipped = if (content.clip) Modifier.clip(GenericShape { _, _ -> addPath(outline) }) else Modifier
    Box(
        modifier
            .requiredSizePx(content.size)
            .drawBehind { drawFilledOutline(outline, node.style, size, blur = node.style.fallbackBlur) }
            .then(clipped)
            .drawWithContent {
                drawContent()
                drawOutlineStroke(outline, node.style, blur = node.style.fallbackBlur)
            },
    ) {
        Children(content.childIds, scope, content.size, hidden)
    }
}

/** 사각형, 타원, 다각형, 경로. 경로 좌표는 노드 원점 기준이므로 원점부터 경계의 오른쪽 아래 끝까지를 크기로 잡는다. */
@Composable
private fun ShapeView(node: SceneNode, content: NodeContent, modifier: Modifier) {
    val bounds = content.localBounds() ?: return
    val outline = remember(content) { content.outline() } ?: return
    val size = Size(maxOf(bounds.right, 0.0), maxOf(bounds.bottom, 0.0))
    Canvas(modifier.requiredSizePx(size)) {
        drawFilledOutline(outline, node.style, this.size, blur = node.style.fallbackBlur)
        drawOutlineStroke(outline, node.style, open = (content as? NodeContent.Path)?.commands, blur = node.style.fallbackBlur)
    }
}

@Composable
private fun TextView(node: SceneNode, content: NodeContent.Text, modifier: Modifier) {
    val density = LocalDensity.current
    val fonts = LocalSceneFonts.current
    val base = content.baseStyle(node.style.fill?.toColor() ?: Color.Black, fonts) { density.unitsToSp(it) }
    val gradient = node.style.gradient
    val style = if (gradient == null) {
        base
    } else {
        base.copy(brush = gradient.toBrush(androidx.compose.ui.geometry.Size(content.size.width.toFloat(), content.size.height.toFloat())))
    }
    val text = remember(content, fonts, density) { content.annotated(fonts) { density.unitsToSp(it) } }
    BasicText(text, modifier.requiredSizePx(content.size).clipToBounds(), style = style, overflow = TextOverflow.Clip)
}

@Composable
private fun ImageView(content: NodeContent.Image, modifier: Modifier, scope: NodeScope) {
    val image = scope.images.image(content.uri)
    if (image == null) {
        Placeholder(content.size, modifier)
    } else {
        Image(image, contentDescription = null, modifier = modifier.requiredSizePx(content.size), contentScale = ContentScale.FillBounds)
    }
}

@Composable
private fun HostView(node: SceneNode, content: NodeContent.HostSlot, modifier: Modifier, scope: NodeScope, hidden: Boolean) {
    val slot = scope.slots[content.key]
    if (slot == null) {
        Placeholder(content.size, modifier)
        return
    }
    val outline = remember(content) { requireNotNull(content.outline()) }
    // 배치하지 않은 노드는 포커스 이동에서 빠지지만, 이미 포커스를 가진 입력란은 숨겨도 키 입력을 계속 받으므로 직접 해제한다.
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    if (hidden && focused) SideEffect { focusManager.clearFocus(force = true) }
    Box(
        modifier
            .requiredSizePx(content.size)
            .onFocusChanged { focused = it.hasFocus }
            .drawBehind { drawFilledOutline(outline, node.style, size, blur = node.style.fallbackBlur) }
            .clipToBounds()
            .drawWithContent {
                drawContent()
                drawOutlineStroke(outline, node.style, blur = node.style.fallbackBlur)
            },
    ) {
        // 바깥 Box는 기기 픽셀(= 문서 단위)로 측정하고 안쪽만 문서 단위 밀도로 바꿔서, 호스트의 dp 배치가 기기마다 달라지지 않게 한다.
        val fontScale = LocalDensity.current.fontScale
        CompositionLocalProvider(
            LocalDensity provides Density(HOST_VIEW_UNITS_PER_DP, fontScale),
            LocalLayoutDirection provides LocalHostLayoutDirection.current,
        ) { slot() }
    }
}

@Composable
private fun Placeholder(size: Size, modifier: Modifier) {
    Canvas(modifier.requiredSizePx(size)) {
        drawRect(PLACEHOLDER, style = Stroke(Stroke.HairlineWidth))
        drawLine(PLACEHOLDER, Offset.Zero, Offset(this.size.width, this.size.height))
        drawLine(PLACEHOLDER, Offset(this.size.width, 0f), Offset(0f, this.size.height))
    }
}

/** API 31 미만에서는 노드 전체 블러([layerBlur])가 적용되지 않으므로, 채우기와 선만이라도 직접 블러를 걸어 그린다. */
private val NodeStyle.fallbackBlur: Double
    get() = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) blur else 0.0

/** 노드 전체 블러. 문서 단위가 픽셀과 같으므로 픽셀 값을 dp로 바꿔 쓴다. API 31 미만에서는 Compose가 블러 없이 그린다. */
@Composable
private fun Modifier.layerBlur(radius: Double): Modifier {
    if (radius <= 0.0) return this
    return blur(with(LocalDensity.current) { radius.toFloat().toDp() }, BlurredEdgeTreatment.Unbounded)
}
