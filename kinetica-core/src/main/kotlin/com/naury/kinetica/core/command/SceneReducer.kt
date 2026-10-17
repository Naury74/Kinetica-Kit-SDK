package com.naury.kinetica.core.command

import com.naury.kinetica.core.scene.Components
import com.naury.kinetica.core.scene.Layouts
import com.naury.kinetica.core.scene.SceneDocument
import com.naury.kinetica.core.scene.SceneLimits
import com.naury.kinetica.core.scene.SceneValidator
import com.naury.kinetica.core.scene.TextMeasurer
import com.naury.kinetica.core.scene.Tokens

/**
 * 명령을 문서에 적용하는 순수 함수. 상태가 없으며 입력이 같으면 항상 같은 결과를 낸다.
 *
 * 적용한 결과 문서 전체를 [SceneValidator]로 검사해 규칙을 어기면 거부한다. 거부해도 입력 문서는 바뀌지 않는다.
 * 통과하면 [Layouts]로 자동 레이아웃과 제약을 반영한 문서를 돌려준다.
 */
public class SceneReducer
@JvmOverloads
constructor(
    limits: SceneLimits = SceneLimits.Default,
    private val textMeasurer: TextMeasurer? = null,
) {
    private val validator = SceneValidator(limits)

    // 명령 종류마다 갈래가 하나씩인 분기다. 쪼개면 어느 명령을 어디서 처리하는지 오히려 알기 어려워진다.
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

            is SceneCommand.SetTransition -> CommandResult.Applied(document.copy(transition = command.transition))

            is SceneCommand.SetToken -> applied(
                document.copy(tokens = command.color?.let { document.tokens + (command.name to it) } ?: (document.tokens - command.name)),
            )

            is SceneCommand.PutTimeline -> TimelineEdits.put(document, command)

            is SceneCommand.RemoveTimeline -> TimelineEdits.remove(document, command)

            is SceneCommand.SetKeyframe -> TimelineEdits.setKeyframe(document, command)

            is SceneCommand.MoveKeyframe -> TimelineEdits.moveKeyframe(document, command)

            is SceneCommand.DeleteKeyframes -> TimelineEdits.deleteKeyframes(document, command)

            is SceneCommand.SetKeyframeEasing -> TimelineEdits.setEasing(document, command)
        }
        return if (proposed is CommandResult.Applied) settle(document, proposed.document) else proposed
    }

    // 레이아웃은 검사를 통과한 값으로만 계산하고, 레이아웃이 만든 위치와 크기도 다시 검사한다.
    private fun settle(before: SceneDocument, proposed: SceneDocument): CommandResult {
        val violations = validator.validate(proposed)
        val settled = if (violations.isEmpty()) settleDocument(before, proposed, textMeasurer) else proposed
        val finalViolations = if (settled === proposed) violations else validator.validate(settled)
        return if (finalViolations.isEmpty()) applied(settled) else CommandResult.Rejected(CommandError.InvalidResult(finalViolations))
    }
}

/**
 * 명령을 적용한 뒤 문서를 정리한다. 순서는 다음과 같다.
 * 1. 인스턴스에서 고친 값을 오버라이드로 남긴다.
 * 2. 직접 고친 색의 토큰 연결을 끊는다.
 * 3. 토큰 색을 반영한다.
 * 4. 인스턴스를 원본에 맞춘다.
 * 5. 제약과 자동 레이아웃을 계산한다.
 *
 * 순서가 바뀌면 사용자가 고친 값이 동기화 과정에서 지워진다.
 */
internal fun settleDocument(before: SceneDocument, after: SceneDocument, measurer: TextMeasurer? = null): SceneDocument {
    val captured = Components.captureOverrides(before, after)
    val unlinked = Tokens.unlinkEdited(before, captured)
    val synced = Components.sync(Tokens.resolve(unlinked))
    return Layouts.settle(before, synced, measurer)
}

internal fun rejected(error: CommandError): CommandResult.Rejected = CommandResult.Rejected(error)

internal fun applied(document: SceneDocument): CommandResult.Applied = CommandResult.Applied(document)
