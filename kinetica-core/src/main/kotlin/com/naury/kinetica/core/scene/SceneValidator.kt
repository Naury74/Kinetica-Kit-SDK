package com.naury.kinetica.core.scene

/**
 * 문서가 구조와 값 범위 규칙을 지키는지 확인한다. 외부 입력, 명령 적용 결과, 세션 시작 문서는 모두 이 검사를 거친다.
 *
 * 확인하는 것: id 형식·중복, 부모·자식 목록의 일치, 순환, 깊이, 노드 수, 유한한 값과 범위, 글자 길이와 유니코드,
 * 이미지 URI scheme, keyframe 순서·개수·값 종류, easing 매개변수, 확장 데이터 크기.
 */
public class SceneValidator(private val limits: SceneLimits = SceneLimits.Default) {
    /** 찾은 문제 목록. 비어 있으면 올바른 문서다. 문제는 최대 [MAX_REPORTED_VIOLATIONS]개까지 모은다. */
    public fun validate(document: SceneDocument): List<SceneViolation> {
        val sink = ViolationSink(MAX_REPORTED_VIOLATIONS)
        checkHeader(document, sink)
        if (document.nodes.size > limits.maxNodes) {
            sink.add(ViolationCode.TooManyNodes, "노드 수 ${document.nodes.size}개가 상한 ${limits.maxNodes}개를 넘습니다.")
            return sink.violations
        }
        checkStructure(document, limits, sink)
        document.nodes.values.forEach { checkNode(it, limits, sink) }
        checkTimelines(document, limits, sink)
        checkExtensions(document, sink)
        checkTokens(document, sink)
        return sink.violations
    }

    public fun isValid(document: SceneDocument): Boolean = validate(document).isEmpty()

    private fun checkHeader(document: SceneDocument, sink: ViolationSink) {
        if (!NodeId.isValid(document.documentId)) {
            sink.add(ViolationCode.InvalidDocumentId, "documentId 형식이 올바르지 않습니다.")
        }
        if (document.schemaVersion != SceneDocument.CURRENT_SCHEMA_VERSION) {
            sink.add(ViolationCode.UnsupportedSchemaVersion, "schemaVersion ${document.schemaVersion}은 지원하지 않습니다.")
        }
        val artboard = document.artboard
        val dimensionOk = { value: Double -> value.isFinite() && value > 0.0 && value <= limits.maxCoordinateMagnitude }
        if (!dimensionOk(artboard.width) || !dimensionOk(artboard.height)) {
            sink.add(ViolationCode.InvalidArtboard, "artboard 크기는 0보다 크고 ${limits.maxCoordinateMagnitude} 이하여야 합니다.")
        }
    }

    private fun checkExtensions(document: SceneDocument, sink: ViolationSink) {
        val totalBytes = document.extensions.entries.sumOf { (key, value) -> utf8Length(key) + utf8Length(value) }
        if (totalBytes > limits.maxExtensionBytes) {
            sink.add(ViolationCode.InvalidExtension, "확장 데이터가 ${limits.maxExtensionBytes}바이트를 넘습니다.")
        }
        if (document.extensions.keys.any { !NodeId.isValid(it) }) {
            sink.add(ViolationCode.InvalidExtension, "확장 데이터 키 형식이 올바르지 않습니다.")
        }
    }

    public companion object {
        public const val MAX_REPORTED_VIOLATIONS: Int = 100
    }
}

internal class ViolationSink(private val capacity: Int) {
    private val collected = mutableListOf<SceneViolation>()

    val violations: List<SceneViolation> get() = collected.toList()

    fun add(code: ViolationCode, detail: String, nodeId: NodeId? = null) {
        if (collected.size < capacity) collected += SceneViolation(code, detail, nodeId)
    }
}
