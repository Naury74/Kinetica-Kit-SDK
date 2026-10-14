package com.naury.kinetica.export

/**
 * 내보낼 그림의 조건.
 *
 * @property widthPx 출력 너비(픽셀). artboard 비율로 높이를 정한다(목업이면 틀 포함).
 * @property timeMs 첫 timeline의 이 시각 장면을 내보낸다. timeline이 없으면 무시한다.
 * @property transparentBackground `true`면 artboard 배경을 그리지 않고 투명하게 둔다. `false`인데 배경이 없으면 흰색을 칠한다.
 * @property maxPixels 너비×높이 상한(기본 16 MP). 넘으면 그리지 않는다.
 * @property mockup 있으면 장면을 기기 틀에 넣고 배경 여백을 둔 목업으로 내보낸다. 높이는 틀에 맞춰 정한다.
 */
public data class ExportRequest(
    val widthPx: Int,
    val timeMs: Double = 0.0,
    val transparentBackground: Boolean = false,
    val maxPixels: Long = DEFAULT_MAX_PIXELS,
    val mockup: MockupFrame? = null,
) {
    public companion object {
        public const val DEFAULT_MAX_PIXELS: Long = 16_000_000L
    }
}

/** 내보내기 결과. 실패해도 이미 있던 출력 파일은 그대로다. 취소는 coroutine 취소로 알린다(CancellationException). */
public sealed interface ExportResult {
    public data class Success(val widthPx: Int, val heightPx: Int) : ExportResult

    public data class Failure(val error: ExportError) : ExportResult
}

public enum class ExportError {
    /** 너비가 0 이하이거나 높이가 1픽셀보다 작아진다. */
    InvalidSize,

    /** 너비×높이가 [ExportRequest.maxPixels]를 넘는다. */
    DimensionsExceeded,

    /** 지금 남은 메모리로는 그림을 만들 수 없다. */
    OutOfMemoryPrevented,

    /** PNG로 바꾸지 못했다. */
    EncoderFailure,

    /** 출력에 쓰지 못했다(권한·공간 등). */
    WriteFailed,
}
