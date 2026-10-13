package com.naury.kinetica.presets

import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode

/**
 * 프리셋을 적용할 위치와 길이.
 *
 * @property startMs 첫 키를 넣을 시각
 * @property durationMs 노드 하나의 움직임 길이
 * @throws IllegalArgumentException 시각이 음수이거나 길이가 0 이하일 때
 */
public data class PresetParams(val timelineId: String = "main", val startMs: Long = 0L, val durationMs: Long = 450L) {
    init {
        require(startMs >= 0L && durationMs > 0L) { "시작 시각은 0 이상, 길이는 0보다 커야 합니다." }
    }
}

/**
 * 노드들의 지금 값(원래 문서)을 끝 상태로 두고 움직임을 만드는 레시피. 상태가 없고 같은 입력이면 같은 키를 만든다.
 * 결과는 일반 keyframe이므로 적용한 뒤에도 timeline에서 고칠 수 있다.
 */
public fun interface MotionPreset {
    /** [targets] 순서대로 노드별 키 목록. 문서에 없는 노드는 빼고 넘긴다. */
    public fun keys(nodes: List<SceneNode>, params: PresetParams): List<PresetKey>
}

/** 프리셋이 만든 키 하나. */
public data class PresetKey(
    val nodeId: NodeId,
    val property: AnimatableProperty,
    val timeMs: Long,
    val value: Double,
    val easing: EasingSpec,
)

/**
 * 이름으로 프리셋을 찾아 명령으로 바꾼다. 문서를 직접 바꾸지 않으며, 호스트가 돌려받은 명령을 트랜잭션 하나로 적용한다(docs 23).
 */
public class PresetRegistry(private val presets: Map<String, MotionPreset>) {
    public val ids: List<String> get() = presets.keys.toList()

    public fun apply(id: String, document: SceneDocument, targets: List<NodeId>, params: PresetParams = PresetParams()): PresetResult {
        val preset = presets[id] ?: return PresetResult.UnknownPreset(id)
        val nodes = targets.distinct().mapNotNull(document::node)
        if (nodes.isEmpty()) return PresetResult.NoTargets
        val keys = preset.keys(nodes, params)
        val end = keys.maxOfOrNull { it.timeMs } ?: params.startMs
        val existing = document.timelines.firstOrNull { it.id == params.timelineId }
        val timeline = when {
            existing == null -> Timeline(params.timelineId, end)
            existing.durationMs < end -> existing.copy(durationMs = end)
            else -> null
        }
        val commands = listOfNotNull(timeline?.let(SceneCommand::PutTimeline)) + keys.map { key ->
            SceneCommand.SetKeyframe(
                params.timelineId,
                key.nodeId,
                key.property,
                Keyframe(key.timeMs, KeyValue.Scalar(key.value), key.easing),
            )
        }
        return PresetResult.Commands(commands)
    }

    public companion object {
        /** 기본 프리셋. 이름은 바꾸지 않는다(저장된 설정이 가리킬 수 있다). */
        public val Default: PresetRegistry = PresetRegistry(
            linkedMapOf(
                "fade.in" to BuiltInPresets.fadeIn,
                "card.expand" to BuiltInPresets.cardExpand,
                "stagger.entrance" to BuiltInPresets.staggerEntrance,
                "elastic.pop" to BuiltInPresets.elasticPop,
                "slide.in.left" to BuiltInPresets.slideInLeft,
                "parallax" to BuiltInPresets.parallax,
            ),
        )
    }
}

public sealed interface PresetResult {
    /** 순서대로 적용할 명령. 하나라도 거부되면 모두 되돌리는 것이 좋다. */
    public data class Commands(val commands: List<SceneCommand>) : PresetResult

    public data class UnknownPreset(val id: String) : PresetResult

    public data object NoTargets : PresetResult
}
