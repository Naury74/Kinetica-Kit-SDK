package com.naury.kinetica.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 글래스 표면의 스타일. 배경을 [blur]만큼 블러 처리하고 [saturation]과 [brightness]로 색을 살린 뒤 [tint]를 덮는다.
 * 가장자리에는 글로우 테두리([edgeLight])와 그림자([shadow])를 그린다.
 *
 * 블러는 Android 12(API 31)부터 동작한다. 그보다 낮은 버전에서는 [fallback] 색을 칠한 반투명 표면으로 보인다.
 *
 * 생성할 때 [blur], [edgeWidth], [shadow]가 음수이거나 [saturation]이 음수, [brightness]가 -1..1 밖,
 * [grain]이나 [refraction]이 0..1 밖이면 [IllegalArgumentException]을 던진다.
 *
 * @property blur 배경 블러 반경. 0이면 블러 없이 맑은 유리가 된다.
 * @property saturation 채도 배율. 1이면 그대로이고, 1보다 크면 배경 색이 더 진하게 번져 유리 특유의 느낌이 난다.
 * @property brightness 더할 밝기(-1..1). 밝은 유리는 양수, 어두운 유리는 음수를 쓴다.
 * @property tint 블러 처리한 배경 위에 덮는 색. 알파로 덮는 정도를 정한다.
 * @property edgeLight 글로우 테두리 색. 위쪽과 왼쪽이 밝고 아래쪽과 오른쪽으로 갈수록 옅어진다. 투명하면 그리지 않는다.
 * @property edgeWidth 글로우 테두리 두께
 * @property shadow 그림자 elevation. 0이면 그림자를 그리지 않는다.
 * @property grain 노이즈 질감의 진하기(0..1). Windows Acrylic처럼 유리에 질감을 준다.
 * @property refraction 가장자리 굴절 정도(0..1). 가장자리 근처의 배경을 안쪽으로 휘어 Liquid Glass처럼 렌즈 효과를 낸다. API 33부터 동작한다.
 * @property fallback 블러를 쓸 수 없을 때 칠할 색
 * @property onGlass 글래스 위에 올리는 텍스트와 아이콘 색
 */
@Immutable
public data class GlassStyle(
    val blur: Dp = 24.dp,
    val saturation: Float = 1.6f,
    val brightness: Float = 0f,
    val tint: Color = Color.White.copy(alpha = 0.18f),
    val edgeLight: Color = Color.White.copy(alpha = 0.55f),
    val edgeWidth: Dp = 1.dp,
    val shadow: Dp = 12.dp,
    val grain: Float = 0f,
    val refraction: Float = 0f,
    val fallback: Color = Color.White.copy(alpha = 0.82f),
    val onGlass: Color = Color(INK),
) {
    /**
     * 다이얼로그와 바텀 시트에 맞게 블러를 키우고 [tint]를 진하게 한 스타일을 돌려준다. 뒤쪽 콘텐츠가 비쳐도 텍스트가 잘 읽힌다.
     * 원래 스타일보다 블러가 약해지거나 색이 옅어지지는 않는다.
     */
    public fun forPopup(): GlassStyle = copy(
        blur = maxOf(blur, POPUP_MIN_BLUR),
        tint = tint.copy(alpha = (tint.alpha + POPUP_TINT_EXTRA).coerceAtMost(POPUP_MAX_TINT)),
        refraction = refraction * POPUP_REFRACTION,
    )

    init {
        require(blur >= 0.dp && edgeWidth >= 0.dp && shadow >= 0.dp) { "blur, edgeWidth, shadow는 0 이상이어야 합니다." }
        require(saturation >= 0f && brightness in -1f..1f && grain in 0f..1f && refraction in 0f..1f) {
            "saturation은 0 이상, brightness는 -1..1, grain과 refraction은 0..1이어야 합니다."
        }
    }
}

/**
 * 여러 플랫폼의 디자인을 참고한 글래스 스타일 프리셋. 그대로 쓰거나 `copy`로 값을 바꿔 쓴다. 이름이 `Dark`로 끝나는 것은 다크 테마용이다.
 */
public object GlassStyles {
    /** iOS 26 Liquid Glass 스타일. 맑고 선명하며, 가장자리가 렌즈처럼 휘고 글로우 테두리가 또렷하다. */
    @JvmField
    public val Liquid: GlassStyle = GlassStyle(
        blur = 8.dp,
        saturation = 1.8f,
        brightness = 0.04f,
        tint = Color.White.copy(alpha = 0.10f),
        edgeLight = Color.White.copy(alpha = 0.85f),
        edgeWidth = 1.2.dp,
        shadow = 16.dp,
        refraction = 0.6f,
        fallback = Color.White.copy(alpha = 0.78f),
    )

    @JvmField
    public val LiquidDark: GlassStyle = Liquid.copy(
        brightness = -0.06f,
        tint = Color.Black.copy(alpha = 0.18f),
        edgeLight = Color.White.copy(alpha = 0.45f),
        fallback = Color(0xE01C1C22),
        onGlass = Color.White,
    )

    /** Windows 11 Acrylic 스타일. 블러가 강하고 옅은 색을 덮으며, 고운 노이즈 질감이 있다. */
    @JvmField
    public val Acrylic: GlassStyle = GlassStyle(
        blur = 30.dp,
        saturation = 1.25f,
        tint = Color(0xFFF3F3F3).copy(alpha = 0.62f),
        edgeLight = Color.White.copy(alpha = 0.35f),
        shadow = 20.dp,
        grain = 0.05f,
        fallback = Color(0xF2F3F3F3),
    )

    @JvmField
    public val AcrylicDark: GlassStyle = Acrylic.copy(
        tint = Color(0xFF202020).copy(alpha = 0.70f),
        edgeLight = Color.White.copy(alpha = 0.12f),
        fallback = Color(0xF2202020),
        onGlass = Color.White,
    )

    /** One UI 스타일. 블러가 부드럽고 밝은 색을 넉넉히 덮으며, 테두리가 없다. */
    @JvmField
    public val OneUi: GlassStyle = GlassStyle(
        blur = 24.dp,
        saturation = 1.3f,
        tint = Color(0xFFFCFCFC).copy(alpha = 0.72f),
        edgeLight = Color.Transparent,
        shadow = 8.dp,
        fallback = Color(0xF5FCFCFC),
    )

    @JvmField
    public val OneUiDark: GlassStyle = OneUi.copy(
        tint = Color(0xFF17171A).copy(alpha = 0.78f),
        fallback = Color(0xF517171A),
        onGlass = Color.White,
    )

    /** visionOS 스타일. 어둡고 깊은 유리에 은은한 글로우 테두리를 둘러, 사진이나 영상 위 컨트롤 패널에 어울린다. */
    @JvmField
    public val Vision: GlassStyle = GlassStyle(
        blur = 36.dp,
        saturation = 1.5f,
        brightness = -0.08f,
        tint = Color(0xFF808080).copy(alpha = 0.22f),
        edgeLight = Color.White.copy(alpha = 0.32f),
        shadow = 24.dp,
        refraction = 0.25f,
        fallback = Color(0xE0303036),
        onGlass = Color.White,
    )

    /** Material 3 Expressive 스타일. 은은하게 블러 처리한 색조 표면에 테두리 없이 큰 그림자를 준다. 앱 색에 맞추려면 `tint`를 바꾼다. */
    @JvmField
    public val Material: GlassStyle = GlassStyle(
        blur = 16.dp,
        saturation = 1.15f,
        tint = Color(0xFFF3EDF7).copy(alpha = 0.86f),
        edgeLight = Color.Transparent,
        shadow = 14.dp,
        fallback = Color(0xFFF3EDF7),
        onGlass = Color(0xFF1D1B20),
    )

    @JvmField
    public val MaterialDark: GlassStyle = Material.copy(
        tint = Color(0xFF211F26).copy(alpha = 0.88f),
        fallback = Color(0xFF211F26),
        onGlass = Color(0xFFE6E0E9),
    )

    /** 예전 iOS의 반투명 유리 스타일. 블러를 아주 강하게 하고 흰색을 덮어 배경이 색으로만 비친다. 알림이나 메뉴 패널에 어울린다. */
    @JvmField
    public val Frosted: GlassStyle = GlassStyle(
        blur = 44.dp,
        saturation = 1.9f,
        brightness = 0.06f,
        tint = Color.White.copy(alpha = 0.55f),
        edgeLight = Color.White.copy(alpha = 0.4f),
        shadow = 10.dp,
        fallback = Color(0xF2F7F7F7),
    )

    /** 블러만 있고 색이나 테두리가 없는 스타일. 다이얼로그 뒤 배경을 흐리게 할 때 쓴다. */
    @JvmField
    public val Clear: GlassStyle = GlassStyle(
        blur = 20.dp,
        saturation = 1.2f,
        tint = Color.Transparent,
        edgeLight = Color.Transparent,
        shadow = 0.dp,
        fallback = Color.Black.copy(alpha = 0.32f),
        onGlass = Color.White,
    )
}

private val POPUP_MIN_BLUR = 28.dp
private const val POPUP_TINT_EXTRA = 0.32f
private const val POPUP_MAX_TINT = 0.85f
private const val POPUP_REFRACTION = 0.5f
private const val INK = 0xFF15161A
