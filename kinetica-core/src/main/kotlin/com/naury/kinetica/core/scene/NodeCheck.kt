package com.naury.kinetica.core.scene

import com.naury.kinetica.core.geometry.Size
import com.naury.kinetica.core.geometry.Vec2
import kotlin.math.abs

private const val MAX_METADATA_KEY_LENGTH = 64
private const val MIN_FONT_WEIGHT = 100
private const val MAX_FONT_WEIGHT = 900
private const val MAX_SPANS = 256
private val dataKeyPattern = Regex("[A-Za-z0-9._-]{1,128}")

/** 데이터 키·동작 이름·HostSlot 키 형식. 영문자·숫자와 `.` `_` `-`로 된 1..128자. */
public fun isValidDataKey(key: String): Boolean = dataKeyPattern.matches(key)

/** 노드 하나의 값 범위를 확인한다. 부모·자식 관계는 [checkStructure]가 본다. */
internal fun checkNode(node: SceneNode, limits: SceneLimits, sink: ViolationSink) {
    val id = node.id
    if (node.name.length > limits.maxNameLength) sink.add(ViolationCode.InvalidName, "이름이 너무 깁니다.", id)
    if (!isWellFormedUtf16(node.name)) sink.add(ViolationCode.InvalidName, "이름에 잘못된 유니코드가 있습니다.", id)
    checkMetadata(node, limits, sink)
    checkTransform(node, limits, sink)
    checkStyle(node, limits, sink)
    checkContent(node, limits, sink)
    checkBindings(node, sink)
    checkComponent(node, limits, sink)
    checkSizeLimits(node, limits, sink)
}

private fun checkSizeLimits(node: SceneNode, limits: SceneLimits, sink: ViolationSink) {
    val size = node.sizeLimits
    if (size == SizeLimits.None) return
    val badValue = size.values.any { !it.isFinite() || it < 0.0 || it > limits.maxCoordinateMagnitude } ||
        listOfNotNull(size.maxWidth, size.maxHeight).any { it <= 0.0 }
    val crossed = listOf(size.minWidth to size.maxWidth, size.minHeight to size.maxHeight)
        .any { (min, max) -> min != null && max != null && min > max }
    if (badValue || crossed) {
        sink.add(ViolationCode.InvalidGeometry, "크기 최소·최대는 0 이상 유한하고 최소가 최대 이하여야 합니다.", node.id)
    }
}

private fun checkBindings(node: SceneNode, sink: ViolationSink) {
    if (node.bindings.values.any { !isValidDataKey(it) } || node.action?.let(::isValidDataKey) == false) {
        sink.add(ViolationCode.InvalidBinding, "데이터 키나 동작 이름 형식이 올바르지 않습니다.", node.id)
    }
    val textOnly = BoundProperty.Text in node.bindings && node.content !is NodeContent.Text
    val imageOnly = BoundProperty.ImageUri in node.bindings && node.content !is NodeContent.Image
    if (textOnly || imageOnly) sink.add(ViolationCode.InvalidBinding, "이 노드 종류에는 묶을 수 없는 속성입니다.", node.id)
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

// 노드 종류마다 한 갈래인 분기다. 나누면 어느 종류를 어디서 검사하는지 오히려 흐려진다.
@Suppress("CyclomaticComplexMethod")
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
            checkCorner(content.cornerRadius, id, sink)
            content.corners?.values?.forEach { checkCorner(it, id, sink) }
        }

        is NodeContent.Ellipse -> requireSize(content.size)

        is NodeContent.Polygon -> {
            requireSize(content.size)
            val valid = content.sides in NodeContent.Polygon.MIN_SIDES..NodeContent.Polygon.MAX_SIDES &&
                content.innerRatio.isFinite() && content.innerRatio > 0.0 && content.innerRatio <= 1.0
            if (!valid) sink.add(ViolationCode.InvalidGeometry, "다각형 꼭짓점 수나 별 비율이 범위를 벗어났습니다.", id)
        }

        is NodeContent.Path -> checkPath(content, id, limits, sink)

        is NodeContent.Text -> {
            requireSize(content.size)
            checkText(content, id, limits, sink)
        }

        is NodeContent.Image -> {
            requireSize(content.size)
            if (!isAllowedImageUri(content.uri, limits)) sink.add(ViolationCode.InvalidUri, "이미지 URI가 허용되지 않습니다.", id)
        }

        is NodeContent.Frame -> {
            requireSize(content.size)
            checkFrame(content, id, limits, sink)
        }

        is NodeContent.HostSlot -> {
            requireSize(content.size)
            if (!isValidDataKey(content.key)) sink.add(ViolationCode.InvalidBinding, "호스트 뷰 키 형식이 올바르지 않습니다.", id)
        }
    }
}

private fun checkFrame(frame: NodeContent.Frame, id: NodeId, limits: SceneLimits, sink: ViolationSink) {
    checkCorner(frame.cornerRadius, id, sink)
    frame.corners?.values?.forEach { checkCorner(it, id, sink) }
    frame.layout?.let { checkLayout(it, id, limits, sink) }
}

private fun checkLayout(layout: AutoLayout, id: NodeId, limits: SceneLimits, sink: ViolationSink) {
    val values = layout.padding.values + layout.gap + layout.lineGap
    if (values.any { !it.isFinite() || it < 0.0 || it > limits.maxCoordinateMagnitude }) {
        sink.add(ViolationCode.InvalidGeometry, "자동 배치 간격·여백은 0 이상 상한 이하여야 합니다.", id)
    }
}

private fun checkCorner(radius: Double, id: NodeId, sink: ViolationSink) {
    if (!radius.isFinite() || radius < 0.0) sink.add(ViolationCode.InvalidGeometry, "모서리 반지름은 0 이상이어야 합니다.", id)
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
    val styleOk = text.fontWeight in MIN_FONT_WEIGHT..MAX_FONT_WEIGHT &&
        text.lineHeight.isFinite() && text.lineHeight in 0.0..limits.maxFontSize * 2 &&
        text.letterSpacing.isFinite() && abs(text.letterSpacing) <= limits.maxFontSize
    if (!styleOk) sink.add(ViolationCode.InvalidText, "글자 굵기·줄 높이·자간이 범위를 벗어났습니다.", id)
    checkSpans(text, id, limits, sink)
}

private fun checkSpans(text: NodeContent.Text, id: NodeId, limits: SceneLimits, sink: ViolationSink) {
    val nameOk = listOfNotNull(text.fontFamily).plus(text.spans.mapNotNull { it.fontFamily }).all(::isValidFontName)
    val spansOk = text.spans.size <= MAX_SPANS && text.spans.all { span ->
        span.start in 0 until span.end && span.end <= text.text.length &&
            (span.fontSize?.let { it.isFinite() && it > 0.0 && it <= limits.maxFontSize } ?: true) &&
            (span.fontWeight?.let { it in MIN_FONT_WEIGHT..MAX_FONT_WEIGHT } ?: true)
    }
    if (!nameOk || !spansOk) sink.add(ViolationCode.InvalidText, "글꼴 이름이나 글자 구간이 범위를 벗어났습니다.", id)
}

private val fontNamePattern = Regex("[A-Za-z0-9 ._-]{1,64}")

/** 글꼴 이름 형식. 영문자·숫자·공백과 `.` `_` `-`로 된 1..64자. */
public fun isValidFontName(name: String): Boolean = fontNamePattern.matches(name)

private fun Vec2.isWithin(max: Double): Boolean = abs(x) <= max && abs(y) <= max
