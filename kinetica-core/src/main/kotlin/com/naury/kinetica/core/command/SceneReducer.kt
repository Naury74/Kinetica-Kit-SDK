package com.naury.kinetica.core.command

import com.naury.kinetica.core.scene.Layouts
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneLimits
import com.naury.kinetica.core.scene.SceneValidator

/**
 * 명령을 문서에 적용하는 순수 함수. 상태를 갖지 않으며 같은 입력이면 항상 같은 결과다.
 *
 * 적용한 결과 문서 전체를 [SceneValidator]로 검사해 규칙을 어기면 거부한다. 거부해도 입력 문서는 바뀌지 않는다.
 * 통과하면 [Layouts]로 자동 배치·크기 제약을 반영한 문서를 돌려준다.
 */
public class SceneReducer(limits: SceneLimits = SceneLimits.Default) {
    private val validator = SceneValidator(limits)

    // 명령 종류마다 한 갈래인 분기다. 나누면 어느 명령이 어디서 처리되는지 오히려 흐려진다.
    @Suppress("CyclomaticComplexMethod")
    public fun apply(document: SceneDocument, command: SceneCommand): CommandResult {
        val proposed = when (command) {
            is SceneCommand.AddNode -> NodeEdits.add(document, command)
            is SceneCommand.RemoveNodes -> NodeEdits.remove(document, command)
            is SceneCommand.UpdateProperties -> NodeEdits.update(document, command)
            is SceneCommand.TransformNodes -> NodeEdits.transform(document, command)
            is SceneCommand.ReorderNode -> NodeEdits.reorder(document, command)
            is SceneCommand.Group -> GroupEdits.group(document, command)
            is SceneCommand.WrapInFrame -> GroupEdits.wrapInFrame(document, command)
            is SceneCommand.Ungroup -> GroupEdits.ungroup(document, command)
            is SceneCommand.SetArtboard -> CommandResult.Applied(document.copy(artboard = command.artboard))
            is SceneCommand.PutTimeline -> TimelineEdits.put(document, command)
            is SceneCommand.RemoveTimeline -> TimelineEdits.remove(document, command)
            is SceneCommand.SetKeyframe -> TimelineEdits.setKeyframe(document, command)
            is SceneCommand.MoveKeyframe -> TimelineEdits.moveKeyframe(document, command)
            is SceneCommand.DeleteKeyframes -> TimelineEdits.deleteKeyframes(document, command)
            is SceneCommand.SetKeyframeEasing -> TimelineEdits.setEasing(document, command)
        }
        return if (proposed is CommandResult.Applied) settle(document, proposed.document) else proposed
    }

    // 배치는 검사를 통과한 값으로만 계산하고, 배치가 만든 값(위치·크기)도 다시 검사한다.
    private fun settle(before: SceneDocument, proposed: SceneDocument): CommandResult {
        val violations = validator.validate(proposed)
        val settled = if (violations.isEmpty()) Layouts.settle(before, proposed) else proposed
        val finalViolations = if (settled === proposed) violations else validator.validate(settled)
        return if (finalViolations.isEmpty()) applied(settled) else CommandResult.Rejected(CommandError.InvalidResult(finalViolations))
    }
}

internal fun rejected(error: CommandError): CommandResult.Rejected = CommandResult.Rejected(error)

internal fun applied(document: SceneDocument): CommandResult.Applied = CommandResult.Applied(document)
