package com.naury.kinetica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * [content] 안의 표면을 바깥 판 위에 얹힌 한 겹으로 그린다. 호스트가 [Modifier.glass]나 [Modifier.kSurface]로 직접 만든 판 안에
 * 글래스 카드나 kinetica-ui 컴포넌트를 놓을 때 감싼다. 감싸지 않으면 안쪽 글래스가 판을 건너뛰고 판 뒤 배경을 블러 처리해 비추므로,
 * 판을 뚫고 뒤쪽 콘텐츠가 보인다.
 *
 * 이 안에서는 [Modifier.glass]와 [Modifier.kSurface]가 배경을 읽지 않고, 판 위에 옅은 색 한 겹만 덮는다. 단색과 톤 표면도 판과 같은
 * 색으로 묻히지 않게 옅은 한 겹으로 그린다. 안쪽 요소에 `backdrop`을 따로 넘기면(사진에 [glassBackdrop]을 붙인 경우 등) 그 배경은
 * 그대로 비춘다. [KDialog], [KSheet] 같은 kinetica-ui 팝업은 판 안을 이미 이렇게 그린다.
 *
 * 판 자신의 수정자는 이 바깥에 두어야 한다. 판까지 이 안에 넣으면 판도 한 겹으로 그려진다.
 *
 * ```kotlin
 * Box(Modifier.glass(shape = panelShape).padding(16.dp)) {
 *     KGlassLayer {
 *         Column { Box(Modifier.glass(shape = cardShape)) { … } }
 *     }
 * }
 * ```
 *
 * @param enabled false면 아무것도 바꾸지 않고 [content]만 그린다. 바깥에서 켠 한 겹 표시를 끄지는 않는다.
 */
@Composable
public fun KGlassLayer(enabled: Boolean = true, content: @Composable () -> Unit) {
    if (enabled) CompositionLocalProvider(LocalGlassLayered provides true, content = content) else content()
}
