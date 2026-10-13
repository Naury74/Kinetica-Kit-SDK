package com.naury.kinetica.core.geometry

/**
 * 문서 좌표를 화면(viewport 픽셀) 좌표로 옮기는 보기 상태. 문서에 저장하지 않는다.
 *
 * ```
 * p_viewport = T(panX, panY) × S(zoom, zoom) × p_document
 * ```
 *
 * @property panX viewport 픽셀 단위 이동량
 * @property panY viewport 픽셀 단위 이동량
 * @property zoom 문서 단위 하나가 차지하는 viewport 픽셀 수. `[MIN_ZOOM, MAX_ZOOM]` 범위다.
 * @throws IllegalArgumentException 값이 유한하지 않거나 zoom이 범위를 벗어날 때
 */
public data class Viewport(val panX: Double = 0.0, val panY: Double = 0.0, val zoom: Double = 1.0) {
    init {
        require(panX.isFinite() && panY.isFinite()) { "pan 값은 유한해야 합니다." }
        require(zoom in MIN_ZOOM..MAX_ZOOM) { "zoom은 $MIN_ZOOM..$MAX_ZOOM 범위여야 합니다." }
    }

    public fun documentToViewport(): Mat3 = Mat3(zoom, 0.0, 0.0, zoom, panX, panY)

    public fun viewportToDocument(): Mat3 = Mat3(1.0 / zoom, 0.0, 0.0, 1.0 / zoom, -panX / zoom, -panY / zoom)

    public fun toViewport(documentPoint: Vec2): Vec2 = Vec2(documentPoint.x * zoom + panX, documentPoint.y * zoom + panY)

    public fun toDocument(viewportPoint: Vec2): Vec2 = Vec2((viewportPoint.x - panX) / zoom, (viewportPoint.y - panY) / zoom)

    public fun panBy(viewportDelta: Vec2): Viewport = copy(panX = panX + viewportDelta.x, panY = panY + viewportDelta.y)

    /**
     * [viewportAnchor] 아래에 있던 문서 점이 그대로 남도록 확대 배율을 바꾼다. [requestedZoom]은 범위 안으로 맞춘다.
     *
     * `p' = q - (z' / z) × (q - p)`
     */
    public fun zoomedAround(viewportAnchor: Vec2, requestedZoom: Double): Viewport {
        val newZoom = clampZoom(requestedZoom)
        val ratio = newZoom / zoom
        return Viewport(
            panX = viewportAnchor.x - ratio * (viewportAnchor.x - panX),
            panY = viewportAnchor.y - ratio * (viewportAnchor.y - panY),
            zoom = newZoom,
        )
    }

    public companion object {
        public const val MIN_ZOOM: Double = 0.1
        public const val MAX_ZOOM: Double = 16.0

        /** NaN은 1로, 무한대와 범위 밖 값은 가까운 경계로 맞춘다. */
        public fun clampZoom(zoom: Double): Double = if (zoom.isNaN()) 1.0 else zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

        /**
         * [content]가 viewport 가운데에 꽉 차게 보이는 보기 상태. 사방에 [paddingPx] 픽셀 여백을 둔다.
         * 크기가 0인 content나 viewport는 zoom 1로 가운데에 둔다.
         */
        public fun fitting(content: Rect, viewportWidth: Double, viewportHeight: Double, paddingPx: Double = 0.0): Viewport {
            val availableWidth = viewportWidth - 2.0 * paddingPx
            val availableHeight = viewportHeight - 2.0 * paddingPx
            val zoom = clampZoom(minOf(availableWidth / content.width, availableHeight / content.height))
                .takeIf { content.width > 0.0 && content.height > 0.0 && minOf(availableWidth, availableHeight) > 0.0 }
                ?: 1.0
            return Viewport(
                panX = viewportWidth / 2.0 - content.center.x * zoom,
                panY = viewportHeight / 2.0 - content.center.y * zoom,
                zoom = zoom,
            )
        }
    }
}
