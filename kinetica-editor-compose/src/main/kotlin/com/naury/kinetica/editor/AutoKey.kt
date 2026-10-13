package com.naury.kinetica.editor

import com.naury.kinetica.core.animation.AnimatableProperty
import com.naury.kinetica.core.animation.EasingSpec
import com.naury.kinetica.core.animation.KeyValue
import com.naury.kinetica.core.animation.Keyframe
import com.naury.kinetica.core.animation.Timeline
import com.naury.kinetica.core.command.SceneCommand
import com.naury.kinetica.core.geometry.Transform2D
import com.naury.kinetica.core.scene.NodeId
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneNode

/**
 * 모션 편집 중의 변경을 명령으로 나눈다. 화면은 [timeline]을 [timeMs]에서 평가한 문서를 보여 주므로, 편집도 그 값을 기준으로 들어온다.
 *
 * - track이 있는 속성이 바뀌면 그 시각에 keyframe을 넣는다(있으면 값만 바꾸고 easing은 유지).
 * - track이 없는 속성과 애니메이션할 수 없는 값(크기·이름·pivot 등)은 원래 문서를 고친다.
 */
internal object AutoKey {
    private val transformProperties = listOf(
        AnimatableProperty.TranslationX,
        AnimatableProperty.TranslationY,
        AnimatableProperty.ScaleX,
        AnimatableProperty.ScaleY,
        AnimatableProperty.RotationDegrees,
    )

    fun translate(
        base: SceneDocument,
        evaluated: SceneDocument,
        timeline: Timeline,
        timeMs: Long,
        command: SceneCommand,
    ): List<SceneCommand> = when (command) {
        is SceneCommand.TransformNodes -> command.transforms.flatMap { (id, transform) ->
            val shown = evaluated.node(id) ?: return@flatMap listOf(command)
            split(base, shown.copy(transform = transform), shown, timeline, timeMs)
        }

        is SceneCommand.UpdateProperties -> {
            val shown = evaluated.node(command.node.id)
            if (shown == null) listOf(command) else split(base, command.node, shown, timeline, timeMs)
        }

        else -> listOf(command)
    }

    private fun split(base: SceneDocument, wanted: SceneNode, shown: SceneNode, timeline: Timeline, timeMs: Long): List<SceneCommand> {
        val original = base.node(wanted.id) ?: return emptyList()
        val keys = mutableListOf<SceneCommand>()
        var transform = original.transform.copy(pivotX = wanted.transform.pivotX, pivotY = wanted.transform.pivotY)
        transformProperties.forEach { property ->
            val value = property.read(wanted.transform)
            if (value == property.read(shown.transform)) return@forEach
            if (timeline.track(wanted.id, property) != null) {
                keys += key(timeline, wanted.id, property, KeyValue.Scalar(value), timeMs)
            } else {
                transform = property.write(transform, value)
            }
        }
        var style = wanted.style.copy(
            opacity = original.style.opacity,
            fill = original.style.fill,
            strokeWidth = original.style.strokeWidth,
        )
        fun animate(property: AnimatableProperty, changed: Boolean, value: KeyValue, applyToBase: () -> Unit) {
            if (!changed) return
            if (timeline.track(wanted.id, property) != null) keys += key(timeline, wanted.id, property, value, timeMs) else applyToBase()
        }
        animate(AnimatableProperty.Opacity, wanted.style.opacity != shown.style.opacity, KeyValue.Scalar(wanted.style.opacity)) {
            style = style.copy(opacity = wanted.style.opacity)
        }
        animate(
            AnimatableProperty.StrokeWidth,
            wanted.style.strokeWidth != shown.style.strokeWidth,
            KeyValue.Scalar(wanted.style.strokeWidth),
        ) {
            style = style.copy(strokeWidth = wanted.style.strokeWidth)
        }
        val fill = wanted.style.fill
        if (fill != null) {
            animate(AnimatableProperty.Fill, fill != shown.style.fill, KeyValue.Color(fill)) { style = style.copy(fill = fill) }
        } else if (shown.style.fill != null && timeline.track(wanted.id, AnimatableProperty.Fill) == null) {
            style = style.copy(fill = null)
        }
        val updated = wanted.copy(transform = transform, style = style)
        val baseEdit = if (updated == original) emptyList() else listOf(SceneCommand.UpdateProperties(updated))
        return baseEdit + keys
    }

    private fun key(timeline: Timeline, id: NodeId, property: AnimatableProperty, value: KeyValue, timeMs: Long): SceneCommand {
        val easing = timeline.track(id, property)?.keyframes?.firstOrNull { it.timeMs == timeMs }?.easing ?: EasingSpec.Linear
        return SceneCommand.SetKeyframe(timeline.id, id, property, Keyframe(timeMs, value, easing))
    }

    fun AnimatableProperty.read(transform: Transform2D): Double = when (this) {
        AnimatableProperty.TranslationX -> transform.translationX
        AnimatableProperty.TranslationY -> transform.translationY
        AnimatableProperty.ScaleX -> transform.scaleX
        AnimatableProperty.ScaleY -> transform.scaleY
        AnimatableProperty.RotationDegrees -> transform.rotationDegrees
        else -> error("변환 속성이 아닙니다: $this")
    }

    private fun AnimatableProperty.write(transform: Transform2D, value: Double): Transform2D = when (this) {
        AnimatableProperty.TranslationX -> transform.copy(translationX = value)
        AnimatableProperty.TranslationY -> transform.copy(translationY = value)
        AnimatableProperty.ScaleX -> transform.copy(scaleX = value)
        AnimatableProperty.ScaleY -> transform.copy(scaleY = value)
        AnimatableProperty.RotationDegrees -> transform.copy(rotationDegrees = value)
        else -> transform
    }
}
