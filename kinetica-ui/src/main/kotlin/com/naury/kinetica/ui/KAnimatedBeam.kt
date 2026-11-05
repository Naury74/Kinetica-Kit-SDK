package com.naury.kinetica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * [KAnimatedBeam] 안의 두 요소를 잇는 빛줄기 하나.
 *
 * @param from 빛이 출발하는 요소의 [KAnimatedBeamScope.beamAnchor] id
 * @param to 빛이 도착하는 요소의 id
 * @param curvature 곡선이 휘는 정도. 두 요소를 잇는 직선의 가운데를 이 거리만큼 직선에 수직으로 당긴다. 양수면 출발점에서 도착점을 볼 때 왼쪽으로 휜다.
 * @param reverse true면 빛이 도착점에서 출발점 쪽으로 흐른다.
 * @param delayMs 다른 빛줄기와 엇갈리게 하려고 늦추는 시간(ms)
 */
@Immutable
public data class KBeam(val from: Any, val to: Any, val curvature: Dp = 0.dp, val reverse: Boolean = false, val delayMs: Int = 0)

/** [KAnimatedBeam]의 내용 영역. 빛줄기가 이을 요소에 [beamAnchor]를 붙인다. */
@Stable
public interface KAnimatedBeamScope : BoxScope {
    /** 이 요소를 빛줄기 끝점으로 등록한다. 빛줄기는 요소의 가운데에서 시작하고 끝난다. 같은 [id]를 두 요소에 붙이면 나중에 배치된 쪽을 쓴다. */
    public fun Modifier.beamAnchor(id: Any): Modifier
}

/**
 * 요소 사이를 곡선으로 잇고 그 곡선을 따라 빛 한 줄기가 흐르게 하는 상자. 연동 서비스나 데이터 흐름을 보여 주는 소개 화면에 쓴다.
 * 이을 요소는 [content] 안 어디에 두든 [KAnimatedBeamScope.beamAnchor]만 붙이면 되고, 곡선은 내용 뒤에 그린다.
 * 요소가 움직이면 곡선도 따라간다. 화면 밖으로 스크롤되면 빛이 멈추고, '애니메이션 삭제' 설정이 켜져 있으면 흐르지 않고 곡선만 진하게 그린다.
 * 장식이라 TalkBack은 곡선을 읽지 않는다.
 *
 * ```kotlin
 * KAnimatedBeam(listOf(KBeam("app", "cloud", curvature = 40.dp), KBeam("cloud", "phone", delayMs = 600))) {
 *     AppIcon(Modifier.align(Alignment.CenterStart).beamAnchor("app"))
 *     CloudIcon(Modifier.align(Alignment.Center).beamAnchor("cloud"))
 *     PhoneIcon(Modifier.align(Alignment.CenterEnd).beamAnchor("phone"))
 * }
 * ```
 *
 * @param beams 그릴 빛줄기. 등록되지 않은 id를 가리키는 빛줄기는 그리지 않는다.
 * @param color 빛줄기 머리 색
 * @param tailColor 빛줄기 꼬리 색. 머리에서 꼬리로 갈수록 이 색으로 바뀌며 옅어진다.
 * @param trackColor 빛이 지나는 곡선 색
 * @param width 선 두께
 * @param periodMs 빛이 곡선 하나를 다 지나가고 다음 빛이 출발할 때까지의 시간(ms). 0 이하면 [IllegalArgumentException]을 던진다.
 * @param tail 빛줄기 길이. 곡선 길이에 대한 비율(0.05..0.9)이고, 범위를 벗어나면 가까운 끝 값으로 맞춘다.
 */
@Composable
public fun KAnimatedBeam(
    beams: List<KBeam>,
    modifier: Modifier = Modifier,
    color: Color = KineticaTheme.colors.accent,
    tailColor: Color = Color(TAIL_COLOR),
    trackColor: Color = LocalKineticaContentColor.current.copy(alpha = TRACK_ALPHA),
    width: Dp = 2.dp,
    periodMs: Int = 2_400,
    tail: Float = 0.35f,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable KAnimatedBeamScope.() -> Unit,
) {
    require(periodMs > 0) { "periodMs는 0보다 커야 합니다." }
    val still = reducedMotion()
    val clock = rememberLoopClock(running = !still && beams.isNotEmpty())
    val anchors = remember { BeamAnchors() }
    val paths = remember { BeamPaths() }
    val look = BeamLook(color, tailColor, trackColor, tail.coerceIn(MIN_TAIL, MAX_TAIL), periodMs, still)
    Box(
        modifier
            .loopVisibility(clock)
            .onGloballyPositioned { anchors.container.value = it }
            .drawWithCache {
                val strokes = BeamStrokes(Stroke(width.toPx(), cap = StrokeCap.Round), Stroke(width.toPx(), cap = StrokeCap.Butt))
                onDrawBehind {
                    paths.sync(beams.size)
                    beams.forEachIndexed { i, beam ->
                        val a = anchors.centerOf(beam.from) ?: return@forEachIndexed
                        val b = anchors.centerOf(beam.to) ?: return@forEachIndexed
                        val track = paths[i].shape(a, b, beam.curvature.toPx())
                        drawBeam(track, beam, look, strokes, clock.seconds)
                    }
                }
            },
        contentAlignment = contentAlignment,
    ) {
        val scope = remember(anchors, this) { BeamScope(this, anchors) }
        scope.content()
    }
}

private class BeamScope(box: BoxScope, private val anchors: BeamAnchors) :
    KAnimatedBeamScope,
    BoxScope by box {
    override fun Modifier.beamAnchor(id: Any): Modifier = this then BeamAnchorElement(anchors, id)
}

private data class BeamAnchorElement(val anchors: BeamAnchors, val id: Any) : ModifierNodeElement<BeamAnchorNode>() {
    override fun create(): BeamAnchorNode = BeamAnchorNode(anchors, id)

    override fun update(node: BeamAnchorNode) {
        node.update(anchors, id)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "beamAnchor"
        properties["id"] = id
    }
}

/** 요소가 컴포지션에서 빠지면 등록을 지워, 사라진 요소 자리로 빛이 그려지지 않게 한다. */
private class BeamAnchorNode(private var anchors: BeamAnchors, private var id: Any) :
    Modifier.Node(),
    GlobalPositionAwareModifierNode {
    private var coordinates: LayoutCoordinates? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        anchors.place(id, this, coordinates)
    }

    fun update(newAnchors: BeamAnchors, newId: Any) {
        if (newAnchors === anchors && newId == id) return
        anchors.remove(id, this)
        anchors = newAnchors
        id = newId
        coordinates?.takeIf { it.isAttached }?.let { anchors.place(id, this, it) }
    }

    override fun onDetach() {
        anchors.remove(id, this)
        coordinates = null
    }
}

/** 등록된 끝점. 위치는 상태라 요소가 움직이면 그리기 단계만 다시 돈다. */
private class BeamAnchors {
    val container = mutableStateOf<LayoutCoordinates?>(null)
    private val rects = mutableStateMapOf<Any, Rect>()

    // 같은 id를 두 요소가 쓰면 마지막으로 배치된 요소가 주인이다. 주인이 아닌 요소가 빠질 때는 등록을 남긴다.
    private val owners = HashMap<Any, Any>()

    fun place(id: Any, owner: Any, coordinates: LayoutCoordinates) {
        val origin = coordinates.positionInRoot()
        val rect = Rect(origin, Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
        owners[id] = owner
        if (rects[id] != rect) rects[id] = rect
    }

    fun remove(id: Any, owner: Any) {
        if (owners[id] !== owner) return
        owners.remove(id)
        rects.remove(id)
    }

    fun centerOf(id: Any): Offset? {
        val box = container.value?.takeIf { it.isAttached } ?: return null
        val rect = rects[id] ?: return null
        return rect.center - box.positionInRoot()
    }
}

/** 머리는 둥근 끝, 꼬리 조각은 평평한 끝으로 그린다. 꼬리 조각끼리 둥근 끝이 겹치면 구슬처럼 진해지기 때문이다. */
private class BeamStrokes(val head: Stroke, val tail: Stroke)

private class BeamLook(val head: Color, val tail: Color, val track: Color, val length: Float, val periodMs: Int, val still: Boolean)

/** 빛줄기마다 하나씩 두는 곡선. 끝점이 그대로면 경로를 다시 만들지 않는다. */
private class BeamPaths {
    private val tracks = ArrayList<BeamTrack>()

    fun sync(count: Int) {
        while (tracks.size < count) tracks += BeamTrack()
        while (tracks.size > count) tracks.removeAt(tracks.lastIndex)
    }

    operator fun get(index: Int): BeamTrack = tracks[index]
}

private class BeamTrack {
    val path = Path()
    val segment = Path()
    val measure = PathMeasure()
    var length = 0f
        private set
    private var from = Offset.Unspecified
    private var to = Offset.Unspecified
    private var bend = Float.NaN

    fun shape(a: Offset, b: Offset, curvature: Float): BeamTrack {
        if (a == from && b == to && curvature == bend) return this
        from = a
        to = b
        bend = curvature
        val dx = b.x - a.x
        val dy = b.y - a.y
        val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        // 이차 곡선의 조절점은 곡선이 실제로 휘는 거리의 두 배만큼 떨어져야 가운데가 curvature만큼 휜다.
        val control = Offset((a.x + b.x) / 2f + dy / distance * curvature * 2f, (a.y + b.y) / 2f - dx / distance * curvature * 2f)
        path.reset()
        path.moveTo(a.x, a.y)
        path.quadraticTo(control.x, control.y, b.x, b.y)
        measure.setPath(path, false)
        length = measure.length
        return this
    }
}

/** 곡선과 그 위를 지나는 빛을 그린다. 빛은 꼬리로 갈수록 옅어지는 조각들로 그려 붓을 프레임마다 만들지 않는다. */
private fun DrawScope.drawBeam(track: BeamTrack, beam: KBeam, look: BeamLook, strokes: BeamStrokes, seconds: Float) {
    if (look.still) {
        drawPath(track.path, lerp(look.track, look.head, STILL_MIX), style = strokes.head)
        return
    }
    drawPath(track.path, look.track, style = strokes.head)
    if (track.length <= 0f) return
    val elapsed = seconds * MS_PER_SECOND - beam.delayMs
    if (elapsed < 0f) return
    val phase = elapsed % look.periodMs / look.periodMs
    val span = track.length * look.length
    // 머리가 곡선 끝을 지나 꼬리까지 빠져나가야 한 번이 끝난다.
    val travel = phase * (track.length + span)
    val head = if (beam.reverse) track.length - travel else travel
    val piece = span / BEAM_PIECES
    for (k in 0 until BEAM_PIECES) {
        val near = if (beam.reverse) head + piece * k else head - piece * (k + 1)
        val far = near + piece
        val from = near.coerceIn(0f, track.length)
        val to = far.coerceIn(0f, track.length)
        if (to <= from) continue
        val fade = 1f - k.toFloat() / BEAM_PIECES
        val tint = lerp(look.head, look.tail, k.toFloat() / BEAM_PIECES)
        track.segment.reset()
        if (track.measure.getSegment(from, to, track.segment, true)) {
            drawPath(track.segment, tint.copy(alpha = tint.alpha * fade), style = if (k == 0) strokes.head else strokes.tail)
        }
    }
}

private const val TAIL_COLOR = 0xFFB15CFF
private const val TRACK_ALPHA = 0.12f
private const val STILL_MIX = 0.45f
private const val MIN_TAIL = 0.05f
private const val MAX_TAIL = 0.9f
private const val BEAM_PIECES = 12
private const val MS_PER_SECOND = 1_000f
