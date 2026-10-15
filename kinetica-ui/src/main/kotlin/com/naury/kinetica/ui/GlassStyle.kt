package com.naury.kinetica.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 유리 표면의 모양. 뒤 화면을 [blur]만큼 흐리고 [saturation]·[brightness]로 색을 살린 뒤 [tint]를 덮고,
 * 가장자리에 빛 테두리([edgeLight])와 그림자([shadow])를 그린다.
 *
 * 흐림은 Android 12(API 31)부터다. 그보다 낮으면 [fallback] 색을 칠한 반투명 표면으로 보인다.
 *
 * @property blur 뒤 화면 흐림 반지름. 0이면 흐리지 않는다(맑은 유리).
 * @property saturation 채도 배율. 1은 그대로, 1보다 크면 뒤 색이 더 선명하다(유리 특유의 색 번짐).
 * @property brightness 밝기 더하기(-1..1). 밝은 유리는 양수, 어두운 유리는 음수.
 * @property tint 흐린 뒤 화면 위에 덮는 색(투명도 포함).
 * @property edgeLight 가장자리 빛 테두리 색. 위·왼쪽이 밝고 아래·오른쪽으로 옅어진다. 투명이면 그리지 않는다.
 * @property edgeWidth 빛 테두리 두께
 * @property shadow 표면 그림자 높이. 0이면 그림자 없음.
 * @property grain 고운 알갱이 무늬의 진하기(0..1). Windows Acrylic처럼 유리 질감을 준다.
 * @property refraction 가장자리 굴절(0..1). 가장자리 가까이의 뒤 화면을 안쪽으로 휘어 렌즈처럼 보인다(Liquid Glass). API 33부터.
 * @property fallback 흐림을 쓸 수 없을 때 칠할 색
 * @property onGlass 유리 위 글자·아이콘 색
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
     * 팝업·시트용으로 더 흐리고 진하게 덮은 모양. 판 뒤 내용이 비쳐도 글자가 잘 읽힌다.
     * 원래보다 덜 흐리거나 옅어지지는 않는다.
     */
    public fun forPopup(): GlassStyle = copy(
        blur = maxOf(blur, POPUP_MIN_BLUR),
        tint = tint.copy(alpha = (tint.alpha + POPUP_TINT_EXTRA).coerceAtMost(POPUP_MAX_TINT)),
        refraction = refraction * POPUP_REFRACTION,
    )

    init {
        require(blur >= 0.dp && edgeWidth >= 0.dp && shadow >= 0.dp) { "흐림·테두리·그림자는 0 이상이어야 합니다." }
        require(saturation >= 0f && brightness in -1f..1f && grain in 0f..1f && refraction in 0f..1f) {
            "채도는 0 이상, 밝기는 -1..1, 알갱이·굴절은 0..1이어야 합니다."
        }
    }
}

/**
 * 세계 여러 UI에서 가져온 유리 모양. 그대로 쓰거나 `copy`로 고쳐 쓴다. `dark`가 붙은 것은 어두운 화면용이다.
 */
public object GlassStyles {
    /** iOS 26 Liquid Glass 느낌: 맑고 선명하며 가장자리가 렌즈처럼 휘고 빛 테두리가 또렷하다. */
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

    /** Windows 11 Acrylic 느낌: 많이 흐리고 옅은 색을 덮으며 고운 알갱이가 있다. */
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

    /** One UI 느낌: 부드럽게 흐리고 밝은 바탕을 넉넉히 덮으며 테두리 없이 둥글다. */
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

    /** visionOS 느낌: 어둡고 깊은 유리에 은은한 빛 테두리. 사진·영상 위 조작판에 어울린다. */
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

    /** 흐림만 있는 맑은 유리(팝업 뒤 배경 처리용). */
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
