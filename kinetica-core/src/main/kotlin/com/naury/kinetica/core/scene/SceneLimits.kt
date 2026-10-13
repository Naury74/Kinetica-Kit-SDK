package com.naury.kinetica.core.scene

/**
 * 문서가 넘을 수 없는 상한. 외부에서 받은 문서가 메모리·CPU를 과하게 쓰지 못하게 막는다.
 *
 * @property maxDepth 그룹 중첩 깊이. 최상위 노드가 1이다. [HARD_MAX_DEPTH]를 넘을 수 없다.
 * @property maxTextBytes 글자 노드 하나의 UTF-8 바이트 수(기본 64 KiB)
 * @property maxTimelineDurationMs timeline 하나의 최대 길이(기본 1시간)
 * @property maxExtensionBytes 확장 데이터 전체의 UTF-8 바이트 수(기본 256 KiB)
 * @property maxCoordinateMagnitude 이동·pivot·크기·경로 좌표의 절댓값 상한(문서 단위)
 * @property allowFlip 음수 배율(반전)을 허용할지
 * @property allowedImageUriSchemes 이미지 노드가 쓸 수 있는 URI scheme. 기본은 `content`와 `android.resource`이며
 * `http`·`https`·`file`은 넣지 않는 것을 권장한다.
 * @throws IllegalArgumentException 상한이 0 이하이거나 [maxDepth]가 [HARD_MAX_DEPTH]를 넘을 때
 */
public data class SceneLimits(
    val maxNodes: Int = 10_000,
    val maxDepth: Int = 32,
    val maxTotalKeyframes: Int = 100_000,
    val maxTextBytes: Int = 65_536,
    val maxPathCommands: Int = 10_000,
    val maxNameLength: Int = 256,
    val maxMetadataEntries: Int = 32,
    val maxMetadataValueLength: Int = 1_024,
    val maxUriLength: Int = 2_048,
    val maxTimelines: Int = 64,
    val maxTimelineDurationMs: Long = 3_600_000L,
    val maxExtensionBytes: Int = 262_144,
    val maxCoordinateMagnitude: Double = 1_000_000.0,
    val maxFontSize: Double = 4_096.0,
    val allowFlip: Boolean = false,
    val allowedImageUriSchemes: Set<String> = setOf("content", "android.resource"),
) {
    init {
        require(maxDepth in 1..HARD_MAX_DEPTH) { "maxDepth는 1..$HARD_MAX_DEPTH 범위여야 합니다." }
        require(
            listOf(
                maxNodes,
                maxTotalKeyframes,
                maxTextBytes,
                maxPathCommands,
                maxNameLength,
                maxMetadataEntries,
                maxMetadataValueLength,
                maxUriLength,
                maxTimelines,
                maxExtensionBytes,
            ).all { it > 0 },
        ) { "상한은 0보다 커야 합니다." }
        require(maxTimelineDurationMs > 0 && maxCoordinateMagnitude > 0.0 && maxFontSize > 0.0) { "상한은 0보다 커야 합니다." }
    }

    public companion object {
        public const val HARD_MAX_DEPTH: Int = 64

        public val Default: SceneLimits = SceneLimits()
    }
}
