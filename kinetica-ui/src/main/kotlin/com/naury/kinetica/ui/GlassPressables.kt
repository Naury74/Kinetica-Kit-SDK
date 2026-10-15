package com.naury.kinetica.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 원형 아이콘 버튼. 지름 [size] 안에 아이콘을 지름의 절반 크기(44dp면 22dp)로 가운데 놓고, 누르면 원 모양 [GlassIndication]이 덮인다.
 * 색 규칙은 [GlassButton]과 같다. 기본값 [GlassButtonKind.Glass]는 글래스 원 위에 [GlassStyle.onGlass] 색 아이콘을 그린다.
 *
 * ```kotlin
 * GlassIconButton(Icons.Default.Share, "공유", onClick = ::share)
 * ```
 *
 * @param contentDescription TalkBack이 읽을 이름. 옆에 같은 뜻의 글자가 있어 읽을 필요가 없을 때만 null로 둔다.
 * @param size 버튼 지름. 터치 영역을 지키려면 44dp 이상을 권장한다. 32dp보다 작게 주면 32dp로 맞춘다.
 * @param enabled `false`면 누를 수 없고 흐리게 그린다.
 * @param accent [GlassButtonKind.Filled], [GlassButtonKind.Tinted], [GlassButtonKind.Plain]에 쓰는 강조색
 */
@Composable
public fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: GlassButtonKind = GlassButtonKind.Glass,
    size: Dp = GlassMetrics.IconButton,
    enabled: Boolean = true,
    accent: Color = GlassTheme.colors.accent,
    style: GlassStyle = LocalGlassStyle.current,
) {
    val diameter = size.coerceAtLeast(MIN_ICON_BUTTON)
    val ink = when (kind) {
        GlassButtonKind.Filled -> GlassTheme.colors.onAccent
        GlassButtonKind.Tinted, GlassButtonKind.Plain -> accent
        GlassButtonKind.Glass -> style.onGlass
    }
    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .size(diameter)
            .iconButtonBackground(kind, accent, style)
            .clip(CircleShape)
            .clickable(null, GlassIndication(CircleShape, ink, PRESS_ALPHA), enabled, role = Role.Button) {
                onClick()
            }
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(rememberVectorPainter(icon), null, Modifier.size(diameter / 2), colorFilter = ColorFilter.tint(ink))
    }
}

/**
 * 누를 수 있는 글래스 표면. 카드처럼 통째로 눌리는 요소에 쓴다. 눌림 표시는 [shape]으로 잘린다.
 * 안쪽 콘텐츠는 [LocalGlassContentColor]로 색을 받는다.
 *
 * 안쪽에 둥근 요소(이미지, 버튼)를 넣을 때는 모서리 반지름을 바깥 반지름에서 안쪽 여백을 뺀 값으로 주면 모서리가 나란히 굽는다.
 * 예를 들어 24dp 카드 안에 12dp 여백으로 넣은 이미지는 12dp로 깎는다.
 *
 * ```kotlin
 * GlassSurface(onClick = { open(item) }, shape = RoundedCornerShape(24.dp)) { Row(Modifier.padding(12.dp)) { ... } }
 * ```
 *
 * @param onClickLabel TalkBack이 누르기 동작을 알릴 때 쓰는 이름
 * @param enabled `false`면 누를 수 없다. 모양은 그대로 둔다.
 */
@Composable
public fun GlassSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    enabled: Boolean = true,
    onClickLabel: String? = null,
    style: GlassStyle = LocalGlassStyle.current,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .glass(style, shape, backdrop)
            .clip(shape)
            .clickable(null, GlassIndication(shape), enabled, onClickLabel, Role.Button, onClick),
    ) {
        CompositionLocalProvider(LocalGlassContentColor provides style.onGlass) { content() }
    }
}

@Composable
private fun Modifier.iconButtonBackground(kind: GlassButtonKind, accent: Color, style: GlassStyle): Modifier = when (kind) {
    GlassButtonKind.Filled -> background(accent, CircleShape)
    GlassButtonKind.Tinted -> background(accent.copy(alpha = TINT_ALPHA), CircleShape)
    GlassButtonKind.Glass -> glass(style, CircleShape)
    GlassButtonKind.Plain -> this
}

private val MIN_ICON_BUTTON = 32.dp
private const val DISABLED_ALPHA = 0.4f
private const val PRESS_ALPHA = 0.12f
private const val TINT_ALPHA = 0.16f
