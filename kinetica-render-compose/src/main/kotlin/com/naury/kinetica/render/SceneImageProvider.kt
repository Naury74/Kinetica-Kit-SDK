package com.naury.kinetica.render

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 호스트가 이미지 노드의 URI를 그릴 수 있는 이미지로 바꿔 준다. 아직 준비되지 않았거나 열 수 없으면 `null`을 돌려주고, 그 자리에는
 * 플레이스홀더를 그린다. 그리는 도중에 호출되므로 디스크나 네트워크를 읽지 말고 미리 읽어 둔 결과만 돌려준다.
 */
public fun interface SceneImageProvider {
    public fun image(uri: String): ImageBitmap?

    public companion object {
        public val None: SceneImageProvider = SceneImageProvider { null }
    }
}
