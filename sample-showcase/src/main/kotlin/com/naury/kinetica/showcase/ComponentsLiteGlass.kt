// 화면 모양 값(간격·크기·색)은 그 자리에서 읽는 편이 낫다.
@file:Suppress("MagicNumber")

package com.naury.kinetica.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.naury.kinetica.ui.KIconButton
import com.naury.kinetica.ui.KIconButtonSizes
import com.naury.kinetica.ui.KIcons
import com.naury.kinetica.ui.KLabeledIconButton
import com.naury.kinetica.ui.KTone
import com.naury.kinetica.ui.KVariant
import com.naury.kinetica.ui.KineticaTheme
import com.naury.kinetica.ui.LocalKineticaContentColor
import com.naury.kinetica.ui.SurfaceStyle
import com.naury.kinetica.ui.SurfaceStyles
import com.naury.kinetica.ui.kSurface

/** 블러 없는 가벼운 유리와 세로 동작 버튼. 편집 화면의 캔버스 위 버튼과 하단 도구 줄을 흉내 낸다. */
@Composable
internal fun LiteGlassCard() {
    var tool by rememberSaveable { mutableIntStateOf(0) }
    DemoCard(
        "가벼운 유리와 도구 버튼",
        "LiteGlass는 블러 없이 채움, 광택, 테두리, 그림자로만 그려 API 26에서도 같은 모양입니다. 아래 줄은 아이콘 위, 이름 아래인 도구 버튼입니다.",
    ) {
        CanvasPreview(Brush.linearGradient(listOf(Color(0xFF2B5876), Color(0xFF4E4376))), SurfaceStyles.LiteGlassDark)
        CanvasPreview(Brush.linearGradient(listOf(Color(0xFFFDFBFB), Color(0xFFE2D1C3))), SurfaceStyles.LiteGlass)
        val accent = KineticaTheme.colors.accent
        val ink = LocalKineticaContentColor.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(KIcons.Crop to "자르기", KIcons.Rotate to "회전", KIcons.Flip to "뒤집기").forEachIndexed { index, (icon, label) ->
                KLabeledIconButton(icon, label, { tool = index }, tint = if (tool == index) accent else ink)
            }
            KLabeledIconButton(KIcons.Compare, "비교", {}, enabled = false)
            KLabeledIconButton(KIcons.Trash, "삭제", {}, tint = KineticaTheme.colors.destructive)
        }
    }
}

@Composable
private fun CanvasPreview(background: Brush, surface: SurfaceStyle) {
    Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp)).background(background).padding(12.dp)) {
        Row(
            Modifier.align(Alignment.TopStart).kSurface(surface, RoundedCornerShape(50)).padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val small = KIconButtonSizes.Small
            KIconButton(KIcons.Undo, "되돌리기", {}, size = small, surface = surface, variant = KVariant.Text, tone = KTone.Neutral)
            KIconButton(KIcons.Redo, "다시 하기", {}, size = small, surface = surface, variant = KVariant.Text, tone = KTone.Neutral)
        }
        KIconButton(
            KIcons.Close,
            "닫기",
            {},
            Modifier.align(Alignment.TopEnd),
            tone = KTone.Neutral,
            size = KIconButtonSizes.Small,
            surface = surface,
        )
    }
}
