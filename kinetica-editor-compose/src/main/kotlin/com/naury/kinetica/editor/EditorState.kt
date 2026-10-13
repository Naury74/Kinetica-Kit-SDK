package com.naury.kinetica.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.command.CommandResult
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Rect
import com.naury.kinetica.core.geometry.Vec2
import com.naury.kinetica.core.geometry.Viewport
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.editor.AutoKey.read
import com.naury.kinetica.gestures.GestureHost
import com.naury.kinetica.guides.SnapGuide
import com.naury.kinetica.history.KineticaSession
import com.naury.kinetica.motion.TimelineEvaluator
import com.naury.kinetica.presets.PresetParams
import com.naury.kinetica.presets.PresetRegistry
import com.naury.kinetica.presets.PresetResult
import kotlin.math.roundToLong

/**
 * 편집 화면의 보기 상태(viewport·캔버스 크기·모션 재생 위치)와 세션을 잇는다. 보기 상태는 문서가 아니므로 실행 취소되지 않는다.
 *
 * 모든 편집(제스처·속성 패널·정렬)은 [execute]를 지난다. 모션 편집 중에는 [document]가 재생 위치에서 평가한 문서이고,
 * [execute]는 [AutoKey]로 애니메이션 속성 변경을 keyframe으로 바꾼다.
 */
internal class EditorState(val session: KineticaSession) : GestureHost {
    var canvasWidth by mutableIntStateOf(0)
        private set
    var canvasHeight by mutableIntStateOf(0)
        private set
    private var viewportState by mutableStateOf<Viewport?>(null)
    private var created = 0

    /** 옮기는 동안 붙은 안내선. 제스처가 끝나면 비운다. */
    var guides by mutableStateOf<List<SnapGuide>>(emptyList())

    /** 상태 기계가 바뀔 때마다 늘어난다. 표시(사각 선택 등)를 다시 그리는 신호다. */
    var gestureTick by mutableIntStateOf(0)

    /** 글자 편집 창을 연 노드. 없으면 `null`. */
    var editingText by mutableStateOf<NodeId?>(null)

    /** 모션 편집 중인지. 켜져 있으면 캔버스가 [playheadMs]의 모습을 보여 준다. */
    var motionEnabled by mutableStateOf(false)

    /** 재생 위치(ms). */
    var playheadMs by mutableDoubleStateOf(0.0)

    val timeline: Timeline? get() = session.document.value.timelines.firstOrNull { it.id == MAIN_TIMELINE }

    /** 키를 넣을 시각. 재생 위치를 정수 ms로 맞추고 timeline 범위로 자른다. */
    val keyTimeMs: Long get() = playheadMs.roundToLong().coerceIn(0L, timeline?.durationMs ?: DEFAULT_DURATION_MS)

    override val document: SceneDocument get() = displayed(session.document.value)
    override val selection: List<NodeId> get() = session.selection.value.selectedIds
    override var viewport: Viewport
        get() = viewportState ?: Viewport()
        set(value) {
            viewportState = value
        }

    /** 화면에 보여 줄 문서. 모션 편집 중이면 재생 위치에서 평가한다. */
    fun displayed(base: SceneDocument): SceneDocument {
        val timeline = base.timelines.firstOrNull { it.id == MAIN_TIMELINE }
        return if (motionEnabled && timeline != null) TimelineEvaluator.evaluate(base, timeline, playheadMs) else base
    }

    /**
     * 캔버스 크기가 처음 정해지면 artboard가 꽉 차게 보이도록 맞춘다. 그 뒤로 크기가 바뀌면(패널·키보드) 가운데 문서 점이
     * 그대로 가운데 있도록 이동량만 고친다. 문서는 바꾸지 않는다.
     */
    fun onCanvasSize(width: Int, height: Int, paddingPx: Double) {
        val previous = viewportState
        val oldWidth = canvasWidth
        val oldHeight = canvasHeight
        canvasWidth = width
        canvasHeight = height
        when {
            width <= 0 || height <= 0 -> Unit
            previous == null -> fit(paddingPx)
            oldWidth > 0 && oldHeight > 0 -> viewportState = previous.panBy(Vec2((width - oldWidth) / 2.0, (height - oldHeight) / 2.0))
        }
    }

    fun fit(paddingPx: Double) {
        val artboard = session.document.value.artboard
        viewportState =
            Viewport.fitting(Rect(0.0, 0.0, artboard.width, artboard.height), canvasWidth.toDouble(), canvasHeight.toDouble(), paddingPx)
    }

    /** 지금 보이는 영역의 가운데(문서 좌표). */
    fun visibleCenter(): Vec2 = viewport.toDocument(Vec2(canvasWidth / 2.0, canvasHeight / 2.0))

    fun nextSequence(): Int = created++

    override fun select(ids: List<NodeId>) = session.select(ids)

    override fun beginTransaction(label: String): Boolean = session.commands.beginTransaction(label)

    override fun execute(command: SceneCommand): Boolean {
        val timeline = timeline
        if (!motionEnabled || timeline == null) return session.commands.execute(command) is CommandResult.Applied
        val base = session.document.value
        val commands = AutoKey.translate(base, displayed(base), timeline, keyTimeMs, command)
        return when (commands.size) {
            0 -> true
            1 -> session.commands.execute(commands.single()) is CommandResult.Applied
            else -> transaction("edit") { commands.all { session.commands.execute(it) is CommandResult.Applied } }
        }
    }

    override fun commitTransaction() {
        session.commands.commitTransaction()
    }

    override fun rollbackTransaction() {
        session.commands.rollbackTransaction()
    }

    /**
     * [block]을 트랜잭션 하나로 실행한다. 이미 트랜잭션 안이면 그 안에서 실행한다.
     * 직접 연 트랜잭션에서 [block]이 `false`를 돌려주면 되돌린다.
     */
    fun transaction(label: String, block: () -> Boolean): Boolean {
        if (session.commands.isInTransaction) return block()
        if (!session.commands.beginTransaction(label)) return false
        val ok = block()
        if (ok) session.commands.commitTransaction() else session.commands.rollbackTransaction()
        return ok
    }

    /** 지금 보이는 값으로 [property] keyframe을 재생 위치에 넣는다. timeline이 없으면 먼저 만든다. */
    fun addKey(id: NodeId, property: AnimatableProperty): Boolean {
        val shown = document.node(id) ?: return false
        val value = when (property) {
            AnimatableProperty.Opacity -> KeyValue.Scalar(shown.style.opacity)
            AnimatableProperty.StrokeWidth -> KeyValue.Scalar(shown.style.strokeWidth)
            AnimatableProperty.Fill -> KeyValue.Color(shown.style.fill ?: return false)
            else -> KeyValue.Scalar(property.read(shown.transform))
        }
        return transaction("add-key") {
            val ready = timeline != null ||
                session.commands.execute(SceneCommand.PutTimeline(Timeline(MAIN_TIMELINE, DEFAULT_DURATION_MS))) is CommandResult.Applied
            val time = keyTimeMs
            ready &&
                session.commands.execute(
                    SceneCommand.SetKeyframe(MAIN_TIMELINE, id, property, Keyframe(time, value)),
                ) is CommandResult.Applied
        }
    }

    /** 고른 노드에 프리셋을 재생 위치부터 적용한다. 명령 전체가 실행 취소 한 단계이고, 하나라도 거부되면 모두 되돌린다. */
    fun applyPreset(registry: PresetRegistry, presetId: String): Boolean {
        val result = registry.apply(presetId, session.document.value, selection, PresetParams(MAIN_TIMELINE, startMs = keyTimeMs))
        val commands = (result as? PresetResult.Commands)?.commands ?: return false
        return transaction("preset") { commands.all { session.commands.execute(it) is CommandResult.Applied } }
    }

    companion object {
        const val MAIN_TIMELINE = "main"
        const val DEFAULT_DURATION_MS = 3_000L
    }
}
