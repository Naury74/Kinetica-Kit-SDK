package com.naury.kinetica.export

/**
 * 이미지 내보내기 설정.
 *
 * @property widthPx 출력 너비(픽셀). 높이는 artboard 비율로 정하며, 목업이면 기기 프레임까지 포함해 정한다.
 * @property timeMs 첫 timeline에서 이 시각의 장면을 내보낸다. timeline이 없으면 무시한다.
 * @property transparentBackground `true`면 artboard 배경을 그리지 않고 투명하게 둔다. `false`인데 배경이 없으면 흰색으로 칠한다.
 * @property maxPixels 너비×높이 상한(기본 16MP). 넘으면 그리지 않는다.
 * @property mockup 지정하면 장면을 기기 프레임에 넣고 배경 여백을 둔 목업 이미지로 내보낸다. 높이는 기기 프레임에 맞춰 정한다.
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

/** 내보내기 결과. 실패해도 기존 출력 파일은 그대로 남는다. 취소는 코루틴 취소(CancellationException)로 알린다. */
public sealed interface ExportResult {
    public data class Success(val widthPx: Int, val heightPx: Int) : ExportResult

    public data class Failure(val error: ExportError) : ExportResult
}

public enum class ExportError {
    /** 너비가 0 이하이거나, 계산한 높이가 1픽셀보다 작다. */
    InvalidSize,

    /** 너비×높이가 [ExportRequest.maxPixels]를 넘는다. */
    DimensionsExceeded,

    /** 남은 메모리가 부족해 비트맵을 만들 수 없다. */
    OutOfMemoryPrevented,

    /** PNG 인코딩에 실패했다. */
    EncoderFailure,

    /** 권한이나 저장 공간 문제 등으로 출력에 쓰지 못했다. */
    WriteFailed,
}
