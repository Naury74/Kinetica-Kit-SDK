package com.naury.kinetica.core.scene

import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Vec2
import kotlin.math.abs

private const val MAX_METADATA_KEY_LENGTH = 64

/** 노드 하나의 값 범위를 확인한다. 부모·자식 관계는 [checkStructure]가 본다. */
internal fun checkNode(node: SceneNode, limits: SceneLimits, sink: ViolationSink) {
    val id = node.id
    if (node.name.length > limits.maxNameLength) sink.add(ViolationCode.InvalidName, "이름이 너무 깁니다.", id)
    if (!isWellFormedUtf16(node.name)) sink.add(ViolationCode.InvalidName, "이름에 잘못된 유니코드가 있습니다.", id)
    checkMetadata(node, limits, sink)
    checkTransform(node, limits, sink)
    checkStyle(node, limits, sink)
    checkContent(node, limits, sink)
}

private fun checkMetadata(node: SceneNode, limits: SceneLimits, sink: ViolationSink) {
    val metadata = node.metadata
    val invalid = metadata.size > limits.maxMetadataEntries ||
        metadata.any { (key, value) ->
            key.isEmpty() || key.length > MAX_METADATA_KEY_LENGTH || value.length > limits.maxMetadataValueLength ||
                !isWellFormedUtf16(key) || !isWellFormedUtf16(value)
        }
    if (invalid) sink.add(ViolationCode.InvalidMetadata, "metadata 개수나 길이가 상한을 넘거나 형식이 잘못되었습니다.", node.id)
}

private fun checkTransform(node: SceneNode, limits: SceneLimits, sink: ViolationSink) {
    val transform = node.transform
    val max = limits.maxCoordinateMagnitude
    val magnitudes = listOf(
        transform.translationX,
        transform.translationY,
        transform.pivotX,
        transform.pivotY,
        transform.scaleX,
        transform.scaleY,
        transform.rotationDegrees,
    )
    when {
        magnitudes.any { abs(it) > max } -> sink.add(ViolationCode.InvalidTransform, "변환 값의 크기가 $max 를 넘습니다.", node.id)
        transform.isDegenerate -> sink.add(ViolationCode.InvalidTransform, "배율이 0에 너무 가깝습니다.", node.id)
        transform.isFlipped && !limits.allowFlip -> sink.add(ViolationCode.InvalidTransform, "반전(음수 배율)이 허용되지 않습니다.", node.id)
    }
}

private fun checkStyle(node: SceneNode, limits: SceneLimits, sink: ViolationSink) {
    val style = node.style
    if (!style.opacity.isFinite() || style.opacity !in 0.0..1.0) {
        sink.add(ViolationCode.InvalidStyle, "opacity는 0..1 범위여야 합니다.", node.id)
    }
    if (!style.strokeWidth.isFinite() || style.strokeWidth !in 0.0..limits.maxCoordinateMagnitude) {
        sink.add(ViolationCode.InvalidStyle, "선 두께가 범위를 벗어났습니다.", node.id)
    }
}

private fun checkContent(node: SceneNode, limits: SceneLimits, sink: ViolationSink) {
    val id = node.id
    fun requireSize(size: Size) {
        if (size.width > limits.maxCoordinateMagnitude || size.height > limits.maxCoordinateMagnitude) {
            sink.add(ViolationCode.InvalidGeometry, "크기가 ${limits.maxCoordinateMagnitude}를 넘습니다.", id)
        }
    }
    when (val content = node.content) {
        is NodeContent.Group -> Unit

        is NodeContent.Rectangle -> {
            requireSize(content.size)
            if (!content.cornerRadius.isFinite() || content.cornerRadius < 0.0) {
                sink.add(ViolationCode.InvalidGeometry, "모서리 반지름은 0 이상이어야 합니다.", id)
            }
        }

        is NodeContent.Ellipse -> requireSize(content.size)

        is NodeContent.Path -> checkPath(content, id, limits, sink)

        is NodeContent.Text -> {
            requireSize(content.size)
            checkText(content, id, limits, sink)
        }

        is NodeContent.Image -> {
            requireSize(content.size)
            if (!isAllowedImageUri(content.uri, limits)) sink.add(ViolationCode.InvalidUri, "이미지 URI가 허용되지 않습니다.", id)
        }
    }
}

private fun checkPath(path: NodeContent.Path, id: NodeId, limits: SceneLimits, sink: ViolationSink) {
    val commands = path.commands
    when {
        commands.isEmpty() || commands.size > limits.maxPathCommands ->
            sink.add(ViolationCode.InvalidGeometry, "경로 명령 수는 1..${limits.maxPathCommands}개여야 합니다.", id)

        commands.first() !is PathCommand.MoveTo ->
            sink.add(ViolationCode.InvalidGeometry, "경로는 MoveTo로 시작해야 합니다.", id)

        commands.any { command -> command.points.any { !it.isWithin(limits.maxCoordinateMagnitude) } } ->
            sink.add(ViolationCode.InvalidGeometry, "경로 좌표가 범위를 벗어났습니다.", id)
    }
}

private fun checkText(text: NodeContent.Text, id: NodeId, limits: SceneLimits, sink: ViolationSink) {
    if (!isWellFormedUtf16(text.text)) {
        sink.add(ViolationCode.InvalidText, "글자에 잘못된 유니코드가 있습니다.", id)
    } else if (utf8Length(text.text) > limits.maxTextBytes) {
        sink.add(ViolationCode.InvalidText, "글자가 ${limits.maxTextBytes}바이트를 넘습니다.", id)
    }
    if (!text.fontSize.isFinite() || text.fontSize <= 0.0 || text.fontSize > limits.maxFontSize) {
        sink.add(ViolationCode.InvalidText, "글자 크기가 범위를 벗어났습니다.", id)
    }
}

private fun Vec2.isWithin(max: Double): Boolean = abs(x) <= max && abs(y) <= max
