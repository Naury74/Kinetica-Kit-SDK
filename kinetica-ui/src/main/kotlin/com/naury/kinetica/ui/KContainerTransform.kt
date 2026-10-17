package com.naury.kinetica.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * [KExpandHost]에서 작은 요소가 상세 화면으로 커지는 모습(Material container transform). 미리 만든 값만 쓸 수 있다.
 *
 * ```kotlin
 * KExpandHost(state, transform = KContainerTransform.fab(Color(0xFF5B6CFF))) { key -> Compose() }
 * ```
 */
@Immutable
public class KContainerTransform private constructor(
    internal val scaleContent: Boolean,
    internal val fadeStart: Float,
    internal val fadeEnd: Float,
    internal val circularSource: Boolean,
    internal val containerColor: Color,
) {
    public companion object {
        /**
         * App Store 투데이 카드. 상세 화면을 카드 너비에 맞게 줄여 그린 채로 커지므로 상세 화면 위쪽을 카드와 같게 만들면 카드가 그대로 이어진다.
         */
        public val Card: KContainerTransform = KContainerTransform(true, 0f, 0f, false, Color.Unspecified)

        /**
         * 목록 행에서 상세 화면으로. 행 자리에서 [containerColor] 상자가 화면 전체로 커지고, 상세 내용은 상자가 반쯤 커진 뒤 원래 크기로
         * 나타난다. 행과 상세 화면의 모양이 많이 다를 때 쓴다.
         */
        public fun listRow(containerColor: Color): KContainerTransform =
            KContainerTransform(false, ROW_FADE_START, ROW_FADE_END, false, containerColor)

        /**
         * 플로팅 액션 버튼에서 화면으로. 동그란 버튼이 [containerColor] 화면으로 커지며 모서리가 펴지고, 상세 내용은 뒤따라 나타난다.
         * [containerColor]는 버튼 색과 같게 준다.
         */
        public fun fab(containerColor: Color): KContainerTransform =
            KContainerTransform(false, FAB_FADE_START, FAB_FADE_END, true, containerColor)
    }
}

private const val ROW_FADE_START = 0.3f
private const val ROW_FADE_END = 0.8f
private const val FAB_FADE_START = 0.4f
private const val FAB_FADE_END = 0.9f
